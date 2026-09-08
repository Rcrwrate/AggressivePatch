/*
 * Copyright 2021 The Netty Project
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 * https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 * Code-level port of io.netty.handler.codec.compression.ZstdEncoder from a
 * recent Netty (4.1.x) version, relocated to love.shirokasoke.aggressivepatch.netty.
 * Adapted for netty-all 4.0.10.Final (bundled with MC 1.7.10):
 * - ObjectUtil checks replaced with plain IllegalArgumentException
 * - Zstd availability check and ZstdConstants inlined against com.github.luben.zstd
 * - extends ChannelOutboundHandlerAdapter rather than MessageToByteEncoder, whose 4.0 form
 * allocates the output buffer itself (a 256 byte ioBuffer() that is heap when Unsafe is
 * missing) and always emits a message; we need a direct buffer of our own, and a write that
 * produces no output at all is normal once the stream buffers data
 * - the one-shot Zstd.compress() call per block was replaced by a single streaming
 * ZstdCompressCtx: blocks are emitted with EndDirective.FLUSH inside one long lived frame
 * instead of one self-contained frame per block, so every block keeps matching against the
 * history window of the blocks sent before it
 */
package love.shirokasoke.aggressivepatch.netty;

import java.nio.ByteBuffer;

import com.github.luben.zstd.EndDirective;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.compression.CompressionException;
import io.netty.util.ReferenceCountUtil;

/**
 * Compresses a {@link ByteBuf} using the Zstandard algorithm.
 * See <a href="https://facebook.github.io/zstd">Zstandard</a>.
 */
public final class ZstdStreamEncoder extends ChannelOutboundHandlerAdapter {

    // Inlined io.netty.handler.codec.compression.ZstdConstants.
    private static final int MIN_COMPRESSION_LEVEL;
    private static final int MAX_COMPRESSION_LEVEL;
    private static final int DEFAULT_COMPRESSION_LEVEL;
    private static final int DEFAULT_MAX_ENCODE_SIZE = Integer.MAX_VALUE;
    private static final int DEFAULT_BLOCK_SIZE = 1 << 16; // 64 KB
    /** Default size of the history window shared by every block of the stream (256 KiB). */
    private static final int DEFAULT_WINDOW_LOG = 18;
    private static final int MIN_WINDOW_LOG = 10;
    /** Above 27 a frame may not be decompressible everywhere; it also bounds decoder memory. */
    private static final int MAX_WINDOW_LOG = 27;
    /** zstd never holds back more than one block internally; 128 KiB is its hard maximum. */
    private static final int MAX_ZSTD_BLOCK_SIZE = 1 << 17;
    /** How much room is requested per compression pass; the destination grows by this step. */
    private static final int DST_STEP = 1 << 16;
    /** Never allocate less than this for a compressed block. */
    private static final int MIN_DST_SIZE = 1024;
    private static final ByteBuffer EMPTY_SRC = ByteBuffer.allocateDirect(0);

    static {
        int minLevel;
        int maxLevel;
        int defaultLevel;
        try {
            com.github.luben.zstd.util.Native.load();
            minLevel = Zstd.minCompressionLevel();
            maxLevel = Zstd.maxCompressionLevel();
            defaultLevel = Zstd.defaultCompressionLevel();
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
        MIN_COMPRESSION_LEVEL = minLevel;
        MAX_COMPRESSION_LEVEL = maxLevel;
        DEFAULT_COMPRESSION_LEVEL = defaultLevel;
    }

    private final int blockSize;
    private final int compressionLevel;
    private final int maxEncodeSize;
    private final int windowLog;
    private ByteBuf buffer;
    /**
     * Streaming context shared by every block of the connection - this is what carries the
     * history window from one block to the next. One per channel, and every callback of a
     * channel runs on a single thread, so it needs no synchronization.
     */
    private ZstdCompressCtx compressCtx;
    /**
     * Set once bytes have been handed to the stream with {@link EndDirective#CONTINUE} and cleared
     * again by the next FLUSH/END. zstd may hold back up to one internal block after a CONTINUE,
     * so a flush that finds our own buffer empty may still have to drain the stream itself.
     */
    private boolean ctxMayHoldData;
    /** Set once the frame terminator has been emitted, so a repeated close() sends no second frame. */
    private boolean frameFinished;

    /** Creates a new Zstd encoder with default settings. */
    public ZstdStreamEncoder() {
        this(DEFAULT_COMPRESSION_LEVEL, DEFAULT_BLOCK_SIZE, DEFAULT_MAX_ENCODE_SIZE);
    }

    /**
     * Creates a new Zstd encoder.
     *
     * @param compressionLevel specifies the level of the compression
     */
    public ZstdStreamEncoder(int compressionLevel) {
        this(compressionLevel, DEFAULT_BLOCK_SIZE, DEFAULT_MAX_ENCODE_SIZE);
    }

    /**
     * Creates a new Zstd encoder.
     *
     * @param blockSize     is used to calculate the compression block size
     * @param maxEncodeSize specifies the size of the largest compressed object
     */
    public ZstdStreamEncoder(int blockSize, int maxEncodeSize) {
        this(DEFAULT_COMPRESSION_LEVEL, blockSize, maxEncodeSize);
    }

    /**
     * Creates a new Zstd encoder.
     *
     * @param compressionLevel specifies the level of the compression
     * @param blockSize        is used to calculate the compression block size
     * @param maxEncodeSize    specifies the size of the largest compressed object
     */
    public ZstdStreamEncoder(int compressionLevel, int blockSize, int maxEncodeSize) {
        this(compressionLevel, blockSize, maxEncodeSize, DEFAULT_WINDOW_LOG);
    }

    /**
     * Creates a new Zstd encoder.
     *
     * @param compressionLevel specifies the level of the compression
     * @param blockSize        is used to calculate the compression block size
     * @param maxEncodeSize    specifies the size of the largest compressed object
     * @param windowLog        size of the history window shared by all blocks, as a power of two
     */
    public ZstdStreamEncoder(int compressionLevel, int blockSize, int maxEncodeSize, int windowLog) {
        if (compressionLevel < MIN_COMPRESSION_LEVEL || compressionLevel > MAX_COMPRESSION_LEVEL) {
            throw new IllegalArgumentException(
                "compressionLevel: " + compressionLevel
                    + " (expected: "
                    + MIN_COMPRESSION_LEVEL
                    + '-'
                    + MAX_COMPRESSION_LEVEL
                    + ')');
        }
        if (blockSize <= 0) {
            throw new IllegalArgumentException("blockSize: " + blockSize + " (expected: > 0)");
        }
        if (maxEncodeSize <= 0) {
            throw new IllegalArgumentException("maxEncodeSize: " + maxEncodeSize + " (expected: > 0)");
        }
        if (windowLog < MIN_WINDOW_LOG || windowLog > MAX_WINDOW_LOG) {
            throw new IllegalArgumentException(
                "windowLog: " + windowLog + " (expected: " + MIN_WINDOW_LOG + '-' + MAX_WINDOW_LOG + ')');
        }
        this.compressionLevel = compressionLevel;
        this.blockSize = blockSize;
        this.maxEncodeSize = maxEncodeSize;
        this.windowLog = windowLog;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof final ByteBuf in)) {
            ctx.write(msg, promise);
            return;
        }

        ByteBuf out = null;
        try {
            if (buffer == null || compressCtx == null) {
                throw new IllegalStateException("not added to a pipeline, or has been removed");
            }

            final ByteBuf buffer = this.buffer;
            int length;
            while ((length = in.readableBytes()) > 0) {
                final int nextChunkSize = Math.min(length, buffer.writableBytes());
                in.readBytes(buffer, nextChunkSize);

                if (!buffer.isWritable()) {
                    // Hand the block to the stream without terminating it: the bytes stay in the
                    // history window and zstd only emits output once an internal block fills up.
                    if (out == null) {
                        out = allocateOut(ctx, blockSize);
                    }
                    compressBufferedData(out, EndDirective.CONTINUE);
                }
            }
            // Leftovers are deliberately kept until flush(), so writes that are not immediately
            // followed by a flush end up in the same zstd block as the ones after them.

            if (out != null && out.isReadable()) {
                ctx.write(out, promise);
                out = null;
            } else {
                // Nothing left the stream yet, the bytes are only queued for the next flush.
                ctx.write(Unpooled.EMPTY_BUFFER, promise);
            }
        } catch (EncoderException e) {
            throw e;
        } catch (Throwable e) {
            throw new EncoderException(e);
        } finally {
            ReferenceCountUtil.release(in);
            if (out != null) {
                out.release();
            }
        }
    }

    /**
     * Feeds the buffered bytes into the compression stream.
     *
     * @param directive {@link EndDirective#CONTINUE} keeps the data inside the stream,
     *                  {@link EndDirective#FLUSH} emits it as a block while <b>keeping</b> the
     *                  history window, {@link EndDirective#END} closes the frame and drops it.
     */
    private void compressBufferedData(ByteBuf out, EndDirective directive) {
        final int flushableBytes = buffer.readableBytes();
        if (flushableBytes == 0) {
            return;
        }

        // Sizing check that 4.1 performs in the overridable allocateBuffer; Netty 4.0 has no such
        // hook, so it is enforced here where the compressed size is actually known. The stream may
        // still be holding back up to one block from an earlier call, hence MAX_ZSTD_BLOCK_SIZE.
        final long bound = Zstd.compressBound((long) flushableBytes + MAX_ZSTD_BLOCK_SIZE);
        if (bound > maxEncodeSize || bound <= 0) {
            throw new EncoderException(
                "requested encode buffer size (" + bound
                    + " bytes) exceeds "
                    + "the maximum allowable size ("
                    + maxEncodeSize
                    + " bytes)");
        }

        final ByteBuffer src = buffer.internalNioBuffer(buffer.readerIndex(), flushableBytes);
        try {
            compressInto(out, src, directive, (int) bound);
            // CONTINUE may leave up to one internal block inside the stream; FLUSH and END are
            // guaranteed to leave nothing behind, which flush() relies on to skip idle flushes.
            ctxMayHoldData = directive == EndDirective.CONTINUE;
        } catch (Exception e) {
            throw new CompressionException(e);
        } finally {
            buffer.clear();
        }
    }

    /**
     * Size to reserve up front for {@code flushableBytes} of input: the realistic bound, not the
     * worst case, which would add another 128 KiB for a block that usually is not held back.
     * {@link #compressInto} grows the destination if the stream really does produce that much.
     */
    private static int initialCapacity(int flushableBytes, int bound) {
        final long want = Math.max(Zstd.compressBound(flushableBytes), MIN_DST_SIZE);
        return (int) Math.min(bound, want);
    }

    private static ByteBuf allocateOut(ChannelHandlerContext ctx, int flushableBytes) {
        final int bound = (int) Zstd.compressBound((long) flushableBytes + MAX_ZSTD_BLOCK_SIZE);
        return ctx.alloc()
            .directBuffer(initialCapacity(flushableBytes, bound));
    }

    /**
     * Runs the compression stream into {@code out}, which must be a direct buffer - zstd-jni's
     * streaming ByteBuffer API rejects anything else, so every caller allocates one itself.
     */
    private void compressInto(ByteBuf out, ByteBuffer src, EndDirective directive, int bound) {
        while (true) {
            // Grow in bounded steps: the worst case only materialises when the stream really
            // holds a full block back, which a plain small packet never does.
            final int room = Math.min(bound, DST_STEP);
            if (out.writableBytes() < room) {
                out.ensureWritable(room);
            }
            final int idx = out.writerIndex();
            final ByteBuffer dst = out.internalNioBuffer(idx, out.writableBytes());
            final int dstPos = dst.position();
            // Advances src.position()/dst.position() and reports whether everything the directive
            // asked for has left the internal buffers.
            final boolean flushed = compressCtx.compressDirectByteBufferStream(dst, src, directive);
            out.writerIndex(idx + dst.position() - dstPos);

            if (directive == EndDirective.CONTINUE) {
                // CONTINUE reports false as long as data is held back on purpose, so the only
                // completion criterion is that all of the input has been consumed.
                if (!src.hasRemaining()) {
                    return;
                }
            } else if (flushed) {
                return;
            }
            // Otherwise the destination ran out of room: grow it and call again.
        }
    }

    @Override
    public void flush(final ChannelHandlerContext ctx) {
        if (buffer != null && compressCtx != null) {
            final int buffered = buffer.readableBytes();
            if (buffered > 0) {
                // A FLUSH emits a block but leaves the frame - and therefore the history window -
                // open, so the next packet can still match against everything sent before it.
                final ByteBuf buf = allocateOut(ctx, buffered);
                boolean written = false;
                try {
                    compressBufferedData(buf, EndDirective.FLUSH);
                    if (buf.isReadable()) {
                        ctx.write(buf);
                        written = true;
                    }
                } finally {
                    if (!written) {
                        buf.release();
                    }
                }
            } else if (ctxMayHoldData) {
                // The writes since the last flush added up to exactly one or more full blocks, so
                // nothing is left in our own buffer while zstd may still sit on up to one internal
                // block. Draining it needs a FLUSH with an empty source; skipping it would stall
                // those bytes until some later write happens to fill a block again.
                final ByteBuf buf = ctx.alloc()
                    .directBuffer(MIN_DST_SIZE);
                boolean written = false;
                try {
                    compressInto(
                        buf,
                        EMPTY_SRC.duplicate(),
                        EndDirective.FLUSH,
                        (int) Zstd.compressBound(MAX_ZSTD_BLOCK_SIZE));
                    ctxMayHoldData = false;
                    if (buf.isReadable()) {
                        ctx.write(buf);
                        written = true;
                    }
                } finally {
                    if (!written) {
                        buf.release();
                    }
                }
            }
        }
        ctx.flush();
    }

    /**
     * @apiNote It has never been invoked in the production environment
     * @since 2.9.0-beta2
     */
    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        // Terminate the frame so the peer's decompressor sees a well formed end of stream.
        // Best effort: off the event loop we must not touch the compression context.
        try {
            if (ctx.executor()
                .inEventLoop()) {
                finishFrame(ctx);
            }
        } finally {
            ctx.close(promise);
        }
    }

    /**
     * @apiNote It has never been invoked in the production environment
     * @since 2.9.0-beta2
     */
    private void finishFrame(ChannelHandlerContext handlerCtx) {
        if (compressCtx == null || frameFinished) {
            return;
        }
        frameFinished = true;
        ByteBuf out = null;
        try {
            final int buffered = buffer == null ? 0 : buffer.readableBytes();
            final int bound = (int) Math.min(Zstd.compressBound((long) buffered + MAX_ZSTD_BLOCK_SIZE), maxEncodeSize);
            out = handlerCtx.alloc()
                .directBuffer(initialCapacity(buffered, bound));
            if (buffered > 0) {
                // END drains the leftovers and terminates the frame in a single pass, which saves
                // the empty block a separate FLUSH-then-END would append.
                compressBufferedData(out, EndDirective.END);
            } else {
                compressInto(out, EMPTY_SRC.duplicate(), EndDirective.END, bound);
            }
            if (out.isReadable()) {
                handlerCtx.writeAndFlush(out);
                out = null;
            }
        } catch (Exception ignore) {
            // the connection is going away anyway
        } finally {
            if (out != null) {
                out.release();
            }
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        ZstdCompressCtx created = null;
        ByteBuf accumulation = null;
        try {
            created = new ZstdCompressCtx();
            // windowLog bounds the encoder memory as well as the window the decoder has to
            // allocate; the checksum is off because TCP already protects the stream.
            created.setLevel(compressionLevel)
                .setWindowLog(windowLog)
                .setChecksum(false)
                .setWorkers(0);
            accumulation = ctx.alloc()
                .directBuffer(blockSize);
        } catch (Throwable t) {
            // Netty 4.0 keeps the handler in the pipeline when handlerAdded fails, so whatever was
            // already acquired must be given back here or it leaks for the life of the channel.
            if (created != null) {
                created.close();
            }
            if (accumulation != null) {
                accumulation.release();
            }
            throw t;
        }
        this.compressCtx = created;
        this.buffer = accumulation;
        this.ctxMayHoldData = false;
        this.frameFinished = false;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        if (compressCtx != null) {
            compressCtx.close();
            compressCtx = null;
        }
        if (buffer != null) {
            buffer.release();
            buffer = null;
        }
    }
}

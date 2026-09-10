/*
 * spotless:off
 *
 * Copyright 2021 The Netty Project
 * 
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 * 
 * https://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 * 
 * Code-level port of io.netty.handler.codec.compression.ZstdEncoder from a
 * recent Netty (4.1.x) version, relocated to love.shirokasoke.aggressivepatch.netty.
 * Adapted for netty-all 4.0.10.Final (bundled with MC 1.7.10): extends
 * ChannelOutboundHandlerAdapter rather than MessageToByteEncoder (which lacks
 * allocateBuffer/isPreferDirect in Netty 4.0), with ObjectUtil checks inlined.
 * spotless:on
 */
package love.shirokasoke.aggressivepatch.netty;

import java.nio.ByteBuffer;

import com.github.luben.zstd.Zstd;

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
public final class ZstdRawEncoder extends ChannelOutboundHandlerAdapter {

    // Inlined io.netty.handler.codec.compression.ZstdConstants.
    private static final int MIN_COMPRESSION_LEVEL;
    private static final int MAX_COMPRESSION_LEVEL;
    private static final int DEFAULT_COMPRESSION_LEVEL;
    private static final int DEFAULT_MAX_ENCODE_SIZE = Integer.MAX_VALUE;
    private static final int DEFAULT_BLOCK_SIZE = 1 << 16; // 64 KB

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
    private ByteBuf buffer;

    /** Creates a new Zstd encoder with default settings. */
    public ZstdRawEncoder() {
        this(DEFAULT_COMPRESSION_LEVEL, DEFAULT_BLOCK_SIZE, DEFAULT_MAX_ENCODE_SIZE);
    }

    /**
     * Creates a new Zstd encoder.
     *
     * @param compressionLevel specifies the level of the compression
     */
    public ZstdRawEncoder(int compressionLevel) {
        this(compressionLevel, DEFAULT_BLOCK_SIZE, DEFAULT_MAX_ENCODE_SIZE);
    }

    /**
     * Creates a new Zstd encoder.
     *
     * @param blockSize     is used to calculate the compression block size
     * @param maxEncodeSize specifies the size of the largest compressed object
     */
    public ZstdRawEncoder(int blockSize, int maxEncodeSize) {
        this(DEFAULT_COMPRESSION_LEVEL, blockSize, maxEncodeSize);
    }

    /**
     * Creates a new Zstd encoder.
     *
     * @param compressionLevel specifies the level of the compression
     * @param blockSize        is used to calculate the compression block size
     * @param maxEncodeSize    specifies the size of the largest compressed object
     */
    public ZstdRawEncoder(int compressionLevel, int blockSize, int maxEncodeSize) {
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
        this.compressionLevel = compressionLevel;
        this.blockSize = blockSize;
        this.maxEncodeSize = maxEncodeSize;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        buffer = ctx.alloc()
            .directBuffer(blockSize);
        buffer.clear();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (buffer != null) {
            buffer.release();
            buffer = null;
        }
        super.handlerRemoved(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf out = null;
        ByteBuf in = null;
        try {
            if (msg instanceof ByteBuf) {
                in = (ByteBuf) msg;
                if (buffer == null) {
                    throw new IllegalStateException("not added to a pipeline," + "or has been removed,buffer is null");
                }

                // Data already buffered plus new input.
                int total = in.readableBytes() + buffer.readableBytes();

                // quick overflow check
                if (total < 0) {
                    throw new EncoderException("too much data to allocate a buffer for compression");
                }

                long alloc = computeAllocSize(total);

                if (alloc > maxEncodeSize || alloc < 0) {
                    throw new EncoderException(
                        "requested encode buffer size (" + alloc
                            + " bytes) exceeds the maximum allowable size ("
                            + maxEncodeSize
                            + " bytes)");
                }

                out = ctx.alloc()
                    .directBuffer((int) alloc);

                encode(in, out);

                if (out.isReadable()) {
                    ctx.write(out, promise);
                } else {
                    out.release();
                    ctx.write(Unpooled.EMPTY_BUFFER, promise);
                }
                out = null;
                ReferenceCountUtil.release(in);
                in = null;
            } else {
                ctx.write(msg, promise);
            }
        } catch (EncoderException e) {
            throw e;
        } catch (Throwable e) {
            throw new EncoderException(e);
        } finally {
            if (in != null) {
                ReferenceCountUtil.release(in);
            }
            if (out != null) {
                out.release();
            }
        }
    }

    /**
     * Worst-case compressed output size for {@code totalUncompressed} bytes given
     * {@link #blockSize} blocks and {@link #maxEncodeSize}.
     */
    private long computeAllocSize(int totalUncompressed) {
        long remaining = totalUncompressed;
        long alloc = 0;
        while (remaining > 0) {
            int curSize = (int) Math.min(blockSize, remaining);
            remaining -= curSize;
            // Zstd.compressBound returns the maximum compressed size for a given input size.
            alloc = Math.max(alloc, Zstd.compressBound(curSize));
        }
        return alloc;
    }

    private void encode(ByteBuf in, ByteBuf out) {
        if (buffer == null) {
            throw new IllegalStateException("not added to a pipeline," + "or has been removed,buffer is null");
        }

        final ByteBuf buf = this.buffer;
        int length;
        while ((length = in.readableBytes()) > 0) {
            final int nextChunkSize = Math.min(length, buf.writableBytes());
            in.readBytes(buf, nextChunkSize);

            if (!buf.isWritable()) {
                flushBufferedData(out);
            }
        }
        // return the remaining data in the buffer
        // when buffer size is smaller than the block size
        if (buf.isReadable()) {
            flushBufferedData(out);
        }
    }

    private void flushBufferedData(ByteBuf out) {
        final int flushableBytes = buffer.readableBytes();
        if (flushableBytes == 0) {
            return;
        }

        final int bufSize = (int) Zstd.compressBound(flushableBytes);
        out.ensureWritable(bufSize);
        final int idx = out.writerIndex();
        int compressedLength;
        try {
            ByteBuffer outNioBuffer = out.internalNioBuffer(idx, out.writableBytes());
            compressedLength = Zstd.compress(
                outNioBuffer,
                buffer.internalNioBuffer(buffer.readerIndex(), flushableBytes),
                compressionLevel);
        } catch (Exception e) {
            throw new CompressionException(e);
        }

        out.writerIndex(idx + compressedLength);
        buffer.clear();
    }

    @Override
    public void flush(final ChannelHandlerContext ctx) {
        if (buffer != null && buffer.isReadable()) {
            final int flushableBytes = buffer.readableBytes();
            final int bufSize = (int) Zstd.compressBound(flushableBytes);
            final ByteBuf buf = ctx.alloc()
                .directBuffer(bufSize);
            try {
                flushBufferedData(buf);
                ctx.write(buf);
            } catch (Throwable t) {
                buf.release();
                throw t;
            }
        }
        ctx.flush();
    }
}

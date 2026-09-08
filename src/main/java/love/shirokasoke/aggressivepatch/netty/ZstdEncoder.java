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
 * - MessageToByteEncoder.allocateBuffer/isPreferDirect hooks absent in 4.0; the
 * output-sizing logic (compressBound + maxEncodeSize check) lives in
 * flushBufferedData/flush instead
 * - heap-buffer fallback added for zstd-jni's direct-only ByteBuffer API (4.0's
 * ioBuffer() is heap when Unsafe is unavailable)
 */
package love.shirokasoke.aggressivepatch.netty;

import java.nio.ByteBuffer;

import com.github.luben.zstd.Zstd;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.compression.CompressionException;

/**
 * Compresses a {@link ByteBuf} using the Zstandard algorithm.
 * See <a href="https://facebook.github.io/zstd">Zstandard</a>.
 */
public final class ZstdEncoder extends MessageToByteEncoder<ByteBuf> {

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
    public ZstdEncoder() {
        this(DEFAULT_COMPRESSION_LEVEL, DEFAULT_BLOCK_SIZE, DEFAULT_MAX_ENCODE_SIZE);
    }

    /**
     * Creates a new Zstd encoder.
     *
     * @param compressionLevel specifies the level of the compression
     */
    public ZstdEncoder(int compressionLevel) {
        this(compressionLevel, DEFAULT_BLOCK_SIZE, DEFAULT_MAX_ENCODE_SIZE);
    }

    /**
     * Creates a new Zstd encoder.
     *
     * @param blockSize     is used to calculate the compression block size
     * @param maxEncodeSize specifies the size of the largest compressed object
     */
    public ZstdEncoder(int blockSize, int maxEncodeSize) {
        this(DEFAULT_COMPRESSION_LEVEL, blockSize, maxEncodeSize);
    }

    /**
     * Creates a new Zstd encoder.
     *
     * @param compressionLevel specifies the level of the compression
     * @param blockSize        is used to calculate the compression block size
     * @param maxEncodeSize    specifies the size of the largest compressed object
     */
    public ZstdEncoder(int compressionLevel, int blockSize, int maxEncodeSize) {
        super(ByteBuf.class, true);
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
    protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) {
        if (buffer == null) {
            throw new IllegalStateException("not added to a pipeline," + "or has been removed,buffer is null");
        }

        final ByteBuf buffer = this.buffer;
        int length;
        while ((length = in.readableBytes()) > 0) {
            final int nextChunkSize = Math.min(length, buffer.writableBytes());
            in.readBytes(buffer, nextChunkSize);

            if (!buffer.isWritable()) {
                flushBufferedData(out);
            }
        }
        // Return the remaining data in the buffer when its size is smaller than the block size.
        if (buffer.isReadable()) {
            flushBufferedData(out);
        }
    }

    private void flushBufferedData(ByteBuf out) {
        final int flushableBytes = buffer.readableBytes();
        if (flushableBytes == 0) {
            return;
        }

        // Sizing check that 4.1 performs in the overridable allocateBuffer; Netty 4.0 has no such
        // hook, so it is enforced here where the compressed size is actually known.
        final long bound = Zstd.compressBound(flushableBytes);
        if (bound > maxEncodeSize || bound <= 0) {
            throw new EncoderException(
                "requested encode buffer size (" + bound
                    + " bytes) exceeds "
                    + "the maximum allowable size ("
                    + maxEncodeSize
                    + " bytes)");
        }
        final int bufSize = (int) bound;

        final ByteBuffer src = buffer.internalNioBuffer(buffer.readerIndex(), flushableBytes);
        try {
            if (out.isDirect()) {
                out.ensureWritable(bufSize);
                final int idx = out.writerIndex();
                final int compressedLength = Zstd
                    .compress(out.internalNioBuffer(idx, out.writableBytes()), src, compressionLevel);
                out.writerIndex(idx + compressedLength);
            } else {
                // zstd-jni's ByteBuffer API is direct-only; Netty 4.0 hands us a heap buffer when
                // Unsafe is unavailable, so bounce through a temporary direct buffer.
                final ByteBuf tmp = out.alloc()
                    .directBuffer(bufSize);
                try {
                    final int compressedLength = Zstd
                        .compress(tmp.internalNioBuffer(0, bufSize), src, compressionLevel);
                    out.writeBytes(tmp, 0, compressedLength);
                } finally {
                    tmp.release();
                }
            }
        } catch (Exception e) {
            throw new CompressionException(e);
        }

        buffer.clear();
    }

    @Override
    public void flush(final ChannelHandlerContext ctx) {
        if (buffer != null && buffer.isReadable()) {
            // 4.1 sizes this via allocateBuffer(ctx, Unpooled.EMPTY_BUFFER, isPreferDirect());
            // a single pending block never exceeds blockSize, so one compressBound covers it.
            final ByteBuf buf = ctx.alloc()
                .directBuffer((int) Zstd.compressBound(buffer.readableBytes()));
            flushBufferedData(buf);
            ctx.write(buf);
        }
        ctx.flush();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        buffer = ctx.alloc()
            .directBuffer(blockSize);
        buffer.clear();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        if (buffer != null) {
            buffer.release();
            buffer = null;
        }
    }
}

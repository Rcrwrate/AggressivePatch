/*
 * Forked from the Netty Project (https://netty.io), io.netty.handler.codec.compression.ZstdConstants.
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
 */
package love.shirokasoke.aggressivepatch.netty;

/**
 * Constants used by the forked Zstandard codec, mirroring the modern Netty {@code ZstdConstants}.
 *
 * <p>
 * These live in our own package so the encoder/decoder forks can be used on MC 1.7.10, whose bundled
 * legacy Netty 4.0 fork lacks the {@code io.netty.handler.codec.compression} zstd classes.
 */
public final class ZstdConstants {

    /** Default compression level, as used by the modern Netty Zstandard codec. */
    public static final int DEFAULT_COMPRESSION_LEVEL = 3;

    /** Minimum compression level accepted by zstd-jni. */
    public static final int MIN_COMPRESSION_LEVEL = -131072;

    /** Maximum compression level accepted by zstd-jni. */
    public static final int MAX_COMPRESSION_LEVEL = 22;

    /** Default block size of the encoder (64 KiB). */
    public static final int DEFAULT_BLOCK_SIZE = 1 << 16;

    /** Default maximum size of the encoded output (32 MiB). */
    public static final int DEFAULT_MAX_ENCODE_SIZE = Integer.MAX_VALUE;

    private ZstdConstants() {}
}

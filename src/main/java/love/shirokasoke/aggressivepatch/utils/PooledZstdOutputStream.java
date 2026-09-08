package love.shirokasoke.aggressivepatch.utils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import com.github.luben.zstd.RecyclingBufferPool;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdOutputStream;

/**
 * Zstd output stream with adjustable compression level. Native compression buffers are recycled via
 * {@link RecyclingBufferPool} to reduce allocation overhead
 * 
 * @see {@link com.mitchej123.hodgepodge.util.PooledGzipOutputStream}
 */
public class PooledZstdOutputStream extends ZstdOutputStream {

    /** Default compression level (zstd's own default: 3). */
    public static final int DEFAULT_LEVEL = Zstd.defaultCompressionLevel();

    private PooledZstdOutputStream(ByteArrayOutputStream out, int level) throws IOException {
        super(out, RecyclingBufferPool.INSTANCE, level);
    }

    public static byte[] compressNBT(NBTTagCompound nbt) throws IOException {
        return compressNBT(nbt, DEFAULT_LEVEL);
    }

    /**
     * Serialize and compress an NBT tag with the given zstd compression level.
     *
     * @param level zstd compression level, valid range is {@link Zstd#minCompressionLevel()} to
     *              {@link Zstd#maxCompressionLevel()}; out-of-range values are clamped by zstd itself
     */
    public static byte[] compressNBT(NBTTagCompound nbt, int level) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(256);

        try (PooledZstdOutputStream zos = new PooledZstdOutputStream(baos, level);
            DataOutputStream dos = new DataOutputStream(zos)) {
            CompressedStreamTools.write(nbt, dos);
        }

        return baos.toByteArray();
    }
}

package love.shirokasoke.aggressivepatch.utils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import com.github.luben.zstd.RecyclingBufferPool;
import com.github.luben.zstd.ZstdInputStream;

/**
 * Zstd input stream that recycles native decompression buffers via {@link RecyclingBufferPool}
 * 
 * @see {@link com.mitchej123.hodgepodge.util.PooledGzipInputStream}
 */
public class PooledZstdInputStream extends ZstdInputStream {

    private PooledZstdInputStream(InputStream in) throws IOException {
        super(in, RecyclingBufferPool.INSTANCE);
    }

    public static PooledZstdInputStream create(byte[] data) throws IOException {
        return new PooledZstdInputStream(new ByteArrayInputStream(data));
    }

    public static NBTTagCompound readNBT(byte[] data, NBTSizeTracker sizeTracker) throws IOException {
        try (PooledZstdInputStream zis = create(data); DataInputStream dis = new DataInputStream(zis)) {
            return CompressedStreamTools.func_152456_a/* readTag */(dis, sizeTracker);
        }
    }
}

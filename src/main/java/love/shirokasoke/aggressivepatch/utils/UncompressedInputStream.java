package love.shirokasoke.aggressivepatch.utils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Pass-through input stream that reads NBT data without any decompression.
 */
public class UncompressedInputStream extends FilterInputStream {

    public UncompressedInputStream(InputStream in) {
        super(in);
    }

    public static UncompressedInputStream create(byte[] data) {
        return new UncompressedInputStream(new ByteArrayInputStream(data));
    }

    public static NBTTagCompound readNBT(byte[] data, NBTSizeTracker sizeTracker) throws IOException {
        try (UncompressedInputStream uis = create(data); DataInputStream dis = new DataInputStream(uis)) {
            return CompressedStreamTools.func_152456_a/* readTag */(dis, sizeTracker);
        }
    }
}

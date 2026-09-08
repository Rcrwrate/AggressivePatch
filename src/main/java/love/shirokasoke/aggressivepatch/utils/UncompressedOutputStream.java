package love.shirokasoke.aggressivepatch.utils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Pass-through output stream that writes NBT data without any compression.
 */
public class UncompressedOutputStream extends FilterOutputStream {

    public UncompressedOutputStream(OutputStream out) {
        super(out);
    }

    public static byte[] writeNBT(NBTTagCompound nbt) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(256);

        try (UncompressedOutputStream uos = new UncompressedOutputStream(baos);
            DataOutputStream dos = new DataOutputStream(uos)) {
            CompressedStreamTools.write(nbt, dos);
        }

        return baos.toByteArray();
    }

    @Override
    public void write(byte[] buf, int off, int len) throws IOException {
        // FilterOutputStream defaults to per-byte writes, pass through directly instead
        out.write(buf, off, len);
    }
}

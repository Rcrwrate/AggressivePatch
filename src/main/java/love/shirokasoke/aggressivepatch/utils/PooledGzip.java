package love.shirokasoke.aggressivepatch.utils;

import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * GZIP compressor for already-serialized NBT payloads, mirroring Hodgepodge's PooledGzipOutputStream
 * (pooled Deflater, nowrap deflate with a hand-written GZIP header/trailer) but taking raw bytes instead
 * of an NBTTagCompound, so the payload does not have to be serialized a second time.
 * 
 * @see {@link com.mitchej123.hodgepodge.util.PooledGzipOutputStream}
 */
public final class PooledGzip {

    private static final ThreadLocal<Deflater> DEFLATER_POOL = ThreadLocal
        .withInitial(() -> new Deflater(Deflater.DEFAULT_COMPRESSION, true));

    private static final ThreadLocal<CRC32> CRC_POOL = ThreadLocal.withInitial(CRC32::new);

    private static final ThreadLocal<byte[]> BUFFER_POOL = ThreadLocal.withInitial(() -> new byte[512]);

    private static final byte[] GZIP_HEADER = { 0x1f, (byte) 0x8b, // Magic number
        0x08, // Compression method (deflate)
        0x00, // Flags
        0x00, 0x00, 0x00, 0x00, // Modification time
        0x00, // Extra flags
        0x00 // OS (unknown)
    };

    private PooledGzip() {}

    /**
     * Avoid duplicate export of NBT
     * 
     * @param raw
     * @return
     */
    public static byte[] compress(byte[] raw) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
        baos.write(GZIP_HEADER, 0, GZIP_HEADER.length);

        final Deflater deflater = DEFLATER_POOL.get();
        deflater.reset();
        deflater.setInput(raw);
        deflater.finish();
        final byte[] buf = BUFFER_POOL.get();
        while (!deflater.finished()) {
            baos.write(buf, 0, deflater.deflate(buf));
        }

        final CRC32 crc = CRC_POOL.get();
        crc.reset();
        crc.update(raw);
        final int crcVal = (int) crc.getValue();
        baos.write(crcVal & 0xff);
        baos.write((crcVal >> 8) & 0xff);
        baos.write((crcVal >> 16) & 0xff);
        baos.write((crcVal >> 24) & 0xff);

        final int size = raw.length;
        baos.write(size & 0xff);
        baos.write((size >> 8) & 0xff);
        baos.write((size >> 16) & 0xff);
        baos.write((size >> 24) & 0xff);

        return baos.toByteArray();
    }
}

package love.shirokasoke.aggressivepatch.mixins.early.minecraft;

import static love.shirokasoke.aggressivepatch.mixins.NBTConfig.custom;

import java.io.IOException;

import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.luben.zstd.Zstd;
import com.mitchej123.hodgepodge.config.FixesConfig;
import com.mitchej123.hodgepodge.util.PooledGzipInputStream;
import com.mitchej123.hodgepodge.util.PooledGzipOutputStream;

import love.shirokasoke.aggressivepatch.mixins.NBTConfig;
import love.shirokasoke.aggressivepatch.utils.PooledGzip;
import love.shirokasoke.aggressivepatch.utils.PooledZstdInputStream;
import love.shirokasoke.aggressivepatch.utils.UncompressedInputStream;
import love.shirokasoke.aggressivepatch.utils.UncompressedOutputStream;

@Mixin(PacketBuffer.class)
public class MixinPacketBuffer {

    /** Magic value meaning the real length is encoded in a following int. Same encoding as Hodgepodge. */
    @Unique
    private static final short AP$MAGIC_LENGTH_IS_INT = Short.MAX_VALUE;

    @Inject(method = "readNBTTagCompoundFromBuffer", at = @At("HEAD"), cancellable = true)
    private void ap$readNBTTagCompoundFromBuffer(CallbackInfoReturnable<NBTTagCompound> cir) throws IOException {
        final PacketBuffer self = (PacketBuffer) (Object) this;
        final short shortLength = self.readShort();
        if (shortLength < 0) {
            cir.setReturnValue(null);
            return;
        }
        final int realLength = shortLength == AP$MAGIC_LENGTH_IS_INT ? self.readInt() : shortLength;
        final byte[] buffer = new byte[realLength];
        self.readBytes(buffer);
        cir.setReturnValue(ap$decodeNBT(buffer, new NBTSizeTracker(FixesConfig.maxNetworkNbtSizeLimit)));
    }

    @Inject(method = "writeNBTTagCompoundToBuffer", at = @At("HEAD"), cancellable = true)
    private void ap$writeNBTTagCompoundToBuffer(NBTTagCompound nbt, CallbackInfo ci) throws IOException {
        final PacketBuffer self = (PacketBuffer) (Object) this;
        if (nbt == null) {
            self.writeShort(-1);
        } else {
            final byte[] buffer = ap$encodeNBT(nbt);
            if (buffer.length >= AP$MAGIC_LENGTH_IS_INT) {
                self.writeShort(AP$MAGIC_LENGTH_IS_INT);
                self.writeInt(buffer.length);
            } else {
                self.writeShort(buffer.length);
            }
            self.writeBytes(buffer);
        }
        ci.cancel();
    }

    /**
     * Encoding of the level config values: 0-22 = zstd compression level, 23 = no compression,
     * 24 = pooled GZIP, 25 = pick a tier by serialized NBT size (custom mode).
     */
    @Unique
    private static byte[] ap$encodeNBT(NBTTagCompound nbt) throws IOException {
        final int level = NBTConfig.compressLevel;
        if (level == 24) {
            return PooledGzipOutputStream.compressNBT(nbt);
        }
        final byte[] raw = UncompressedOutputStream.writeNBT(nbt);
        final int effective = level == 25 ? ap$resolveCustomLevel(raw.length) : level;
        switch (effective) {
            case 23:
                return raw;
            case 24:
                return PooledGzip.compress(raw);
            case 25:
                // Custom has no meaning as a tier override (would recurse), fall back to zstd's default level
                return Zstd.compress(raw, Zstd.defaultCompressionLevel());
            default:
                return Zstd.compress(raw, effective);
        }
    }

    @Unique
    private static int ap$resolveCustomLevel(int size) {
        if (size < custom.smallLimit) return custom.smallLevel;
        if (size < custom.medianLimit) return custom.medianLevel;
        return custom.largeLevel;
    }

    /**
     * Decode by sniffing the payload magic, so every supported format can be read back regardless of which
     * mode produced it: GZIP (1f 8b), zstd frame (28 b5 2f fd), otherwise raw uncompressed NBT.
     */
    @Unique
    private static NBTTagCompound ap$decodeNBT(byte[] buffer, NBTSizeTracker tracker) throws IOException {
        if (buffer.length >= 2 && (buffer[0] & 0xff) == 0x1f && (buffer[1] & 0xff) == 0x8b) {
            return PooledGzipInputStream.readNBT(buffer, tracker);
        }
        if (buffer.length >= 4 && (buffer[0] & 0xff) == 0x28
            && (buffer[1] & 0xff) == 0xb5
            && (buffer[2] & 0xff) == 0x2f
            && (buffer[3] & 0xff) == 0xfd) {
            return PooledZstdInputStream.readNBT(buffer, tracker);
        }
        return UncompressedInputStream.readNBT(buffer, tracker);
    }
}

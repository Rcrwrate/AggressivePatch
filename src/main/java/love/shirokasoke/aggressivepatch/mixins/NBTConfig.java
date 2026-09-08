package love.shirokasoke.aggressivepatch.mixins;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import love.shirokasoke.aggressivepatch.MyMod;

@Config(modid = MyMod.MODID, category = "nbt", filename = "AggressivePatch", configSubDirectory = "shirokasoke")
public class NBTConfig {

    static {
        ConfigurationManager.registerConfig(NBTConfig.class);
    }

    @Config.Reloadable("mixin")
    @Config.Comment({ "Network NBT compression mode for PacketBuffer NBT tags.",
        "0-22: zstd with this value as the compression level (0 means zstd's default level 3)",
        "23: no compression, the NBT is sent raw",
        "24: GZIP via Hodgepodge's pooled streams (wire-compatible with vanilla)",
        "25: custom - pick the codec based on the serialized NBT size (see the custom category below)",
        "Modes other than 24 change the network format, so both sides must run this mod." })
    @Config.RangeInt(min = 0, max = 25)
    @Config.DefaultInt(25)
    public static int compressLevel;

    @Config.Comment("Compression level tiers picked from the serialized NBT size")
    public static Custom custom = new Custom();

    public static class Custom {

        @Config.Reloadable("mixin")
        @Config.Comment("If the serialized NBT size (in bytes) is smaller than this, smallLevel overrides compressLevel")
        @Config.RangeInt(min = 0)
        @Config.DefaultInt(512)
        public static int smallLimit;

        @Config.Reloadable("mixin")
        @Config.Comment({ "Same encoding as compressLevel: 0-22 = zstd level, 23 = no compression, 24 = pooled GZIP",
            "(25 has no meaning here and falls back to zstd's default level 3)",
            "Tiny NBTs usually are not worth compressing, so 23 (raw) is a sane default" })
        @Config.RangeInt(min = 0, max = 25)
        @Config.DefaultInt(23)
        public static int smallLevel;

        @Config.Reloadable("mixin")
        @Config.Comment({
            "If the serialized NBT size is >= smallLimit and smaller than this (in bytes), medianLevel overrides compressLevel",
            "If the size is >= this value, largeLevel overrides compressLevel" })
        @Config.RangeInt(min = 0)
        @Config.DefaultInt(4096)
        public static int medianLimit;

        @Config.Reloadable("mixin")
        @Config.Comment("Same encoding as smallLevel")
        @Config.RangeInt(min = 0, max = 25)
        @Config.DefaultInt(3)
        public static int medianLevel;

        @Config.Reloadable("mixin")
        @Config.Comment("Same encoding as smallLevel")
        @Config.RangeInt(min = 0, max = 25)
        @Config.DefaultInt(7)
        public static int largeLevel;
    }

}

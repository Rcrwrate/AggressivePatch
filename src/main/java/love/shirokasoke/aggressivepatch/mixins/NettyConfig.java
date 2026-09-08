package love.shirokasoke.aggressivepatch.mixins;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import love.shirokasoke.aggressivepatch.MyMod;

@Config(modid = MyMod.MODID, category = "netty", filename = "AggressivePatch", configSubDirectory = "shirokasoke")
public class NettyConfig {

    static {
        ConfigurationManager.registerConfig(NettyConfig.class);
    }

    @Config.Comment({ "Master switch for TCP stream zstd compression; both sides must enable it.",
        "Single-player local channels are always skipped." })
    @Config.DefaultBoolean(true)
    public static boolean enabled;

    @Config.Reloadable("mixin")
    @Config.Comment({ "Outbound zstd compression level (1-22); client and server may differ.",
        "Keep it low (1-3) on IO threads; with a codecThreads pool, higher levels are safe." })
    @Config.RangeInt(min = 1, max = 22)
    @Config.DefaultInt(3)
    public static int compressLevel;

    @Config.Reloadable("mixin")
    @Config.Comment({ "Outbound accumulation buffer size in bytes: when full, buffered bytes are compressed",
        "as one chunk; the remainder is flushed later. Bigger values only help payloads above one",
        "block (chunk data). Server-side only." })
    @Config.RangeInt(min = 1024)
    @Config.DefaultInt(65536)
    public static int blockSizeBytes;

    @Config.Reloadable("mixin")
    @Config.Comment({ "Shared zstd history window, as a power of two: blocks can reference earlier data",
        "within it, so repeated payloads compress better. Costs roughly 3x the window per connection",
        "on both sides; 18 (256 KiB) covers several chunk packets." })
    @Config.RangeInt(min = 10, max = 27)
    @Config.DefaultInt(18)
    public static int windowLog;

    @Config.Reloadable("mixin")
    @Config.Comment({ "Dedicated compression pool size; 0 = compress on the shared Netty IO threads.",
        "With a pool, compressLevel can go much higher without adding latency to other players.",
        "2-4 threads is plenty. Created once on the first compressed connection; changes need a restart." })
    @Config.RangeInt(min = 0, max = 16)
    @Config.DefaultInt(0)
    public static int codecThreads;

    @Config.Reloadable("mixin")
    @Config.Comment({ "Use streaming compression: better ratio and efficiency." })
    @Config.DefaultBoolean(true)
    public static boolean useStreamCompress;
}

package love.shirokasoke.aggressivepatch;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

@Config(modid = MyMod.MODID, filename = "AggressivePatch", configSubDirectory = "shirokasoke")
@Config.RequiresWorldRestart
public class MConfig {

    static {
        ConfigurationManager.registerConfig(MConfig.class);
    }

    @Config.DefaultString("Hello World")
    public static String greeting;
}

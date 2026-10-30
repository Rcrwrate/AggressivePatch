package love.shirokasoke.aggressivepatch.mixins;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import love.shirokasoke.aggressivepatch.MyMod;

@Config(modid = MyMod.MODID, category = "oc", filename = "AggressivePatch", configSubDirectory = "shirokasoke")
public class OCConfig {

    static {
        ConfigurationManager.registerConfig(OCConfig.class);
    }

    @Config.Comment("Enable the OpenComputers PrintData texture compression patch")
    @Config.DefaultBoolean(false)
    public static boolean enabled;

    @Config.Comment("Restore the final flag on PrintData$.MODULE$ via OCFinalFixer after late mixins strip it")
    @Config.DefaultBoolean(true)
    public static boolean enabled_ASMFIX;

    @Config.Comment("Recovery mode: restore already-written data back to the state before this mod was installed")
    @Config.DefaultBoolean(false)
    public static boolean recover;
}

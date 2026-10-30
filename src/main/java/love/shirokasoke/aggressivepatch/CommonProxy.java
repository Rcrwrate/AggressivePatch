package love.shirokasoke.aggressivepatch;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import love.shirokasoke.aggressivepatch.commands.Server;
import love.shirokasoke.aggressivepatch.mixins.OCConfig;
import love.shirokasoke.aggressivepatch.utils.PrintDataRecoverHandler;

public class CommonProxy {

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {

        MyMod.LOG.info(MConfig.greeting);
        MyMod.LOG.info("I am MyMod at version " + Tags.VERSION);

        // OCFinalFixer 的注册时机
        // - 必须晚于 unimixins 的后期 mixin 代理（它们在 construction 阶段注册）, 在 PrintDataMixin 注入之后
        // - 必须早于 OC 首次加载 PrintData$（modid 字典序 aggressivepatch < opencomputers, OC 的 preInit 在我们之后）
        if (OCConfig.enabled && OCConfig.enabled_ASMFIX) {
            net.minecraft.launchwrapper.Launch.classLoader
                .registerTransformer("love.shirokasoke.aggressivepatch.core.asm.OCFinalFixer");
        }

        // recover 模式：挂载存量数据修复器（区块加载时扫描原始 NBT 并还原压缩数据）
        if (OCConfig.enabled && OCConfig.recover) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new PrintDataRecoverHandler());
            MyMod.LOG.warn("PrintData recover mode is active");
        }
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {}

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {}

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new Server());
    }

    public void serverStopping(FMLServerStoppingEvent event) {
        love.shirokasoke.aggressivepatch.netty.ZstdCodecExecutors.shutdown();
    }
}

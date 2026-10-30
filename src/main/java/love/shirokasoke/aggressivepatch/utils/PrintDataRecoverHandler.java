package love.shirokasoke.aggressivepatch.utils;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.event.world.ChunkDataEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * recover 模式的存量数据修复器
 * <p>
 * 没有办法主动触发全量修改，因此把修复挂在数据路径上
 */
public class PrintDataRecoverHandler {

    private static final Logger LOG = LogManager.getLogger("AP-Recover");

    private static final String TEXTURE = "opencomputers:White";
    private static final String MAGIC = "0";

    @SubscribeEvent
    public void onChunkDataLoad(ChunkDataEvent.Load event) {
        if (event.world.isRemote) {
            return;
        }
        int healed = heal(
            event.getData()
                .getCompoundTag("Level"));
        if (healed > 0) {
            LOG.info(
                "Recovered {} compressed PrintData texture(s) in chunk [{}, {}] during load",
                healed,
                event.getChunk().xPosition,
                event.getChunk().zPosition);
        }
    }

    private static int heal(NBTTagCompound tag) {
        int count = 0;
        if (tag.getTag("bounds") instanceof NBTTagByteArray && MAGIC.equals(tag.getString("texture"))) {
            tag.setString("texture", TEXTURE);
            count++;
        }
        for (String key : tag.func_150296_c()) {
            count += heal(tag.getTag(key));
        }
        return count;
    }

    private static int heal(NBTBase nbt) {
        if (nbt instanceof NBTTagCompound) {
            return heal((NBTTagCompound) nbt);
        }
        if (nbt instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) nbt;
            int count = 0;
            for (int i = 0; i < list.tagCount(); i++) {
                // 非复合条目返回空 compound，不会匹配特征
                count += heal(list.getCompoundTagAt(i));
            }
            return count;
        }
        return 0;
    }
}

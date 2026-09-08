package love.shirokasoke.aggressivepatch.commands;

import net.minecraft.command.ICommandSender;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class Client extends Server {

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public String getCommandName() {
        return "apc";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/apc [show|reload]";
    }
}

package love.shirokasoke.aggressivepatch.commands;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import love.shirokasoke.aggressivepatch.Tags;
import love.shirokasoke.aggressivepatch.mixins.NBTConfig;
import love.shirokasoke.aggressivepatch.mixins.NBTConfig.Custom;
import love.shirokasoke.aggressivepatch.mixins.NettyConfig;

public class Server extends CommandBase {

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public String getCommandName() {
        return "aps";
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("aggressivepatch");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/aps [show|reload]";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0 || "show".equals(args[0])) {
            show(sender);
            return;
        }

        if ("reload".equals(args[0])) {
            ConfigurationManager.reloadConfig(NBTConfig.class, "mixin");
            ConfigurationManager.reloadConfig(NettyConfig.class, "mixin");
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.GREEN + "Config Reloaded" + EnumChatFormatting.RESET));
            return;
        }

        throw new WrongUsageException(getCommandUsage(sender), new Object[0]);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "show", "reload");
        }
        return null;
    }

    private void show(ICommandSender sender) {
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.AQUA + "AggressivePatch " + Tags.VERSION + EnumChatFormatting.RESET));
        sender.addChatMessage(
            new ChatComponentText(
                "Compression mode: " + EnumChatFormatting.YELLOW + describe(NBTConfig.compressLevel)));

        if (NBTConfig.compressLevel == 25) {
            sender.addChatMessage(
                new ChatComponentText(
                    "  < " + Custom.smallLimit + " B: " + EnumChatFormatting.YELLOW + describe(Custom.smallLevel)));
            sender.addChatMessage(
                new ChatComponentText(
                    "  < " + Custom.medianLimit + " B: " + EnumChatFormatting.YELLOW + describe(Custom.medianLevel)));
            sender.addChatMessage(
                new ChatComponentText(
                    "  >= " + Custom.medianLimit + " B: " + EnumChatFormatting.YELLOW + describe(Custom.largeLevel)));
        }

        if (NettyConfig.enabled) {
            sender.addChatMessage(
                new ChatComponentText(
                    "  Enabled: " + EnumChatFormatting.YELLOW + NettyConfig.enabled + EnumChatFormatting.RESET));
            sender.addChatMessage(
                new ChatComponentText(
                    "  Zstd level: " + EnumChatFormatting.YELLOW
                        + NettyConfig.compressLevel
                        + EnumChatFormatting.RESET));
            sender.addChatMessage(
                new ChatComponentText(
                    "  Block size: " + EnumChatFormatting.YELLOW
                        + NettyConfig.blockSizeBytes
                        + " B"
                        + EnumChatFormatting.RESET));
            sender.addChatMessage(
                new ChatComponentText(
                    "  Codec threads: " + EnumChatFormatting.YELLOW
                        + (NettyConfig.codecThreads == 0 ? "shared IO threads" : NettyConfig.codecThreads)
                        + EnumChatFormatting.RESET));
            sender.addChatMessage(
                new ChatComponentText(
                    "  History window: " + EnumChatFormatting.YELLOW
                        + (1 << NettyConfig.windowLog)
                        + " B"
                        + EnumChatFormatting.RESET));
        }

    }

    private static String describe(int level) {
        switch (level) {
            case 23:
                return "none (raw NBT)";
            case 24:
                return "GZIP (pooled, vanilla compatible)";
            case 25:
                return "custom (picked by NBT size)";
            default:
                return "zstd level " + level;
        }
    }
}

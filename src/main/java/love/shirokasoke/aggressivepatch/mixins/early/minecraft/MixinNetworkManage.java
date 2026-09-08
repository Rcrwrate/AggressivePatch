package love.shirokasoke.aggressivepatch.mixins.early.minecraft;

import javax.crypto.SecretKey;

import net.minecraft.network.NetworkManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import love.shirokasoke.aggressivepatch.mixins.NettyConfig;
import love.shirokasoke.aggressivepatch.netty.ZstdCodecExecutors;
import love.shirokasoke.aggressivepatch.netty.ZstdDecoder;
import love.shirokasoke.aggressivepatch.netty.ZstdEncoder;
import love.shirokasoke.aggressivepatch.netty.ZstdStreamEncoder;

@Mixin(NetworkManager.class)
public class MixinNetworkManage {

    @Unique
    private Logger log = LogManager.getLogger("Zstd-Inject");

    @Shadow
    private Channel channel;

    @Shadow
    private boolean isClientSide;

    @Inject(method = "enableEncryption", at = @At("TAIL"))
    private void ap$installZstdCodec(SecretKey key, CallbackInfo ci) {
        log.info("Injecting {}", isClientSide ? "client" : "server");
        if (isClientSide) {
            // inbound: decrypt -> zstd -> splitter
            this.channel.pipeline()
                .addBefore("splitter", "zstd", new ZstdDecoder());
            log.info("Injected decoder");
        } else {
            // outbound: prepender -> zstd -> encrypt
            final ChannelOutboundHandlerAdapter encoder = NettyConfig.useStreamCompress
                ? new ZstdStreamEncoder(
                    NettyConfig.compressLevel,
                    NettyConfig.blockSizeBytes,
                    Integer.MAX_VALUE,
                    NettyConfig.windowLog)
                : new ZstdEncoder(NettyConfig.compressLevel, NettyConfig.blockSizeBytes, Integer.MAX_VALUE);
            if (NettyConfig.codecThreads > 0) {
                this.channel.pipeline()
                    .addBefore(ZstdCodecExecutors.workers(NettyConfig.codecThreads), "prepender", "zstd", encoder);
            } else {
                this.channel.pipeline()
                    .addBefore("prepender", "zstd", encoder);
            }

            log.info(
                "Injected encoder(compressLevel: {}, blockSize: {}, codecThreads {}, useStreamCompress {})",
                NettyConfig.compressLevel,
                NettyConfig.blockSizeBytes,
                NettyConfig.codecThreads,
                NettyConfig.useStreamCompress);
        }

    }
}

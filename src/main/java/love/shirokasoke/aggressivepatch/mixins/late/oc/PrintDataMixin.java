package love.shirokasoke.aggressivepatch.mixins.late.oc;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import li.cil.oc.common.item.data.PrintData$;
import li.cil.oc.common.item.data.PrintData.Shape;
import love.shirokasoke.aggressivepatch.mixins.OCConfig;

/**
 * 目标是 Scala 伴生对象 li.cil.oc.common.item.data.PrintData$
 * <p>
 * Scala object 的标准编译模式：<clinit> → new PrintData$() → 构造器里写 static final MODULE$ = this
 * <p>
 * Mixin会重写字节码版本，高版本不支持这种操作，尊享
 * {@code IllegalAccessError: Update to static final field PrintData$.MODULE$ attempted from a different method (<init>) than the initializer method <clinit>}
 * 
 * 直接取消掉{@link PrintData$#MODULE$}的{@code final}（必要的代价），之后补回来
 */
@Mixin(PrintData$.class)
public abstract class PrintDataMixin {

    @Shadow(remap = false)
    @Mutable
    public static PrintData$ MODULE$;

    @Unique
    private static final String TEXTURE = "opencomputers:White";
    @Unique
    private static final String MAGIC = "0";
    @Unique
    private static final boolean recover = OCConfig.recover;

    /**
     * Shape -> NBT
     */
    @Inject(method = "shapeToNBT", at = @At("TAIL"), remap = false, require = 1)
    private void aggressivepatch$shapeToNBT$compressTexture(Shape shape, CallbackInfoReturnable<NBTTagCompound> cir) {
        if (recover) return;
        final NBTTagCompound nbt = cir.getReturnValue();
        final String texture = nbt.getString("texture");
        if (TEXTURE.equals(texture)) {
            nbt.setString("texture", MAGIC);
        }
    }

    /**
     * NBT -> Shape
     */
    @Inject(method = "nbtToShape", at = @At("HEAD"), remap = false, require = 1)
    private void aggressivepatch$nbtToShape$decompressTexture(NBTTagCompound nbt, CallbackInfoReturnable<Shape> cir) {
        final String texture = nbt.getString("texture");
        if (MAGIC.equals(texture)) {
            nbt.setString("texture", TEXTURE);
        }
    }
}

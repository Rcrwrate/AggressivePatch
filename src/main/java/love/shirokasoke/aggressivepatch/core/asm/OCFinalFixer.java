package love.shirokasoke.aggressivepatch.core.asm;

import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

/**
 * 在 late mixin 之后，在类被加载之前把 ACC_FINAL 补回
 * <p>
 * 必须注册在 unimixins 后期 mixin 代理之后、{@code PrintData$} 首次加载之前，否则会被 mixin 再剥掉或根本看不到该类
 * <p>
 * 因此不能走标准ASM注册路径（coremod transformer 注入发生在后期代理之前），统一在 preInit 里注册
 * <p>
 * 
 * @apiNote 全部用字符串常量匹配，避免触发 OC 类的提前加载
 */
public class OCFinalFixer implements IClassTransformer {

    private static final Logger LOG = LogManager.getLogger("AP-OCFinalFixer");

    private static final String TARGET = "li.cil.oc.common.item.data.PrintData$";
    private static final String FIELD = "MODULE$";
    private static final String DESC = "Lli/cil/oc/common/item/data/PrintData$;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !TARGET.equals(transformedName)) {
            return basicClass;
        }
        try {
            ClassReader cr = new ClassReader(basicClass);
            ClassNode node = new ClassNode();
            cr.accept(node, 0);

            boolean restored = false;
            for (FieldNode f : (List<FieldNode>) node.fields) {
                if (FIELD.equals(f.name) && DESC.equals(f.desc) && (f.access & Opcodes.ACC_FINAL) == 0) {
                    f.access |= Opcodes.ACC_FINAL;
                    restored = true;
                }
            }
            if (!restored) {
                return basicClass;
            }

            // 恢复 final 的同时必须把 class 版本压回 V1_8
            // JVM 只允许 class version <= 52 的类在 <init> 中写 static final，
            // 这正是当初 mixin （modern 字节码把版本顶高）后不得不移除 final 的原因
            if (node.version > Opcodes.V1_8) {
                LOG.info(
                    "Downgrading {} class file version {} -> {} so <init> may write the static final",
                    TARGET,
                    node.version & 0xFFFF,
                    Opcodes.V1_8 & 0xFFFF);
                node.version = Opcodes.V1_8;
            }

            ClassWriter cw = new ClassWriter(cr, 0);
            node.accept(cw);
            LOG.info("Restored ACC_FINAL on {}.MODULE$ (after late mixins)", TARGET);
            return cw.toByteArray();
        } catch (Throwable t) {
            LOG.catching(t);
            LOG.warn("OCFinalFixer failed, returning original bytes for {}", transformedName);
            return basicClass;
        }
    }
}

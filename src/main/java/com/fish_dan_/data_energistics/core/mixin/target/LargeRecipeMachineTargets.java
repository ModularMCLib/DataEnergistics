package com.fish_dan_.data_energistics.core.mixin.target;

import dev.polymixin.api.DynamicTargetProvider;
import dev.polymixin.api.TargetContext;
import io.github.classgraph.ClassInfo;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.NullMarked;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;

/** Selects native output hooks across loaded Mods using metadata only, without initializing optional classes. */
@NullMarked
public final class LargeRecipeMachineTargets implements DynamicTargetProvider {

    private static final String RECIPE_MACHINE = "mekanism.common.tile.prefab.TileEntityRecipeMachine";

    /** Runs during Mixin preparation; unreadable candidate bytecode fails with the affected class name. */
    @Override
    public Collection<ClassInfo> provideTargets(TargetContext context) {
        ObjectArrayList<ClassInfo> targets = new ObjectArrayList<>();
        for (ClassInfo candidate : context.implementersOfDeclaredTargets()) {
            if (!candidate.isInterface() && candidate.extendsSuperclass(RECIPE_MACHINE) && declaresOutputHook(candidate.getName())) {
                targets.add(candidate);
            }
        }
        targets.sort(Comparator.comparing(ClassInfo::getName));
        return targets;
    }

    @Override
    public boolean relaxInjectionRequirements() {
        return false;
    }

    private static boolean declaresOutputHook(String className) {
        ClassNode node;
        try {
            node = MixinService.getService().getBytecodeProvider().getClassNode(className);
        } catch (ClassNotFoundException | IOException exception) {
            throw new IllegalStateException("Could not inspect the native machine output hook on " + className, exception);
        }
        // PolyMixin's shared ClassGraph scan includes hierarchy information only, not method metadata.
        for (MethodNode method : node.methods) {
            if (method.name.equals("handleEject") && method.desc.equals("()V") &&
                    (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_STATIC)) == 0 &&
                    method.instructions.size() > 0) {
                return true;
            }
        }
        return false;
    }
}

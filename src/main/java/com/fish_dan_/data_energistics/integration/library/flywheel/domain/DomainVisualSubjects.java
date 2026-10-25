package com.fish_dan_.data_energistics.integration.library.flywheel.domain;

import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;
import com.fish_dan_.data_energistics.integration.MOD;
import com.fish_dan_.data_energistics.integration.technology.create.domain.CreateFrozenInstance;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import java.util.concurrent.ConcurrentHashMap;

/** Flywheel executes visual plans on worker threads; associations are published at its creation boundary. */
public final class DomainVisualSubjects {

    private static final ConcurrentHashMap<Object, Object> SUBJECTS = new ConcurrentHashMap<>();
    private static final Reference2ObjectOpenHashMap<Object, ObjectArrayList<CreateFrozenInstance>> GPU_FRAMES = new Reference2ObjectOpenHashMap<>();

    private DomainVisualSubjects() {}

    public static void add(Object visual, Object subject) {
        if (subject instanceof Entity || subject instanceof BlockEntity) SUBJECTS.put(visual, subject);
    }

    public static void remove(Object visual) {
        SUBJECTS.remove(visual);
    }

    /** Runs between Flywheel's worker barriers, before the next frame plan is dispatched. */
    public static void frame(float ticks) {
        if (!MOD.CREATE.isLoaded()) return;
        var iterator = GPU_FRAMES.reference2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!SUBJECTS.containsKey(entry.getKey())) iterator.remove();
            else if (!frozen(entry.getKey())) {
                entry.getValue().forEach(frame -> frame.restore(ticks));
                iterator.remove();
            }
        }
        SUBJECTS.forEach((visual, subject) -> {
            if (!(visual instanceof BlockEntityVisual<?> block) || GPU_FRAMES.containsKey(visual) || !frozen(visual)) return;
            var frames = new ObjectArrayList<CreateFrozenInstance>();
            var captured = new ReferenceOpenHashSet<Instance>();
            block.collectCrumblingInstances(instance -> {
                if (instance == null || !captured.add(instance)) return;
                var frame = CreateFrozenInstance.capture(instance, ticks);
                if (frame != null) frames.add(frame);
            });
            GPU_FRAMES.put(visual, frames);
        });
    }

    public static boolean frozen(Object visual) {
        Object subject = SUBJECTS.get(visual);
        return subject instanceof Entity entity ? DomainStasis.frozen(entity) : subject instanceof BlockEntity block && block.getLevel() != null && DomainStasis.frozen(block.getLevel(), block.getBlockPos());
    }

    public static void clear() {
        SUBJECTS.clear();
        GPU_FRAMES.clear();
    }
}

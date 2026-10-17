package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.WeakHashMap;

/** Client-render-thread blade-tip samples; each view follows its actual rendered blade matrix. */
final class SanctifierBladeTrail {

    private static final float LIFETIME = 2;
    private static final int MAX_SAMPLES = 16;
    private static final WeakHashMap<LivingEntity, EnumMap<View, Trail>> TRAILS = new WeakHashMap<>();

    private SanctifierBladeTrail() {}

    static void render(LivingEntity entity, View view, SanctifierAnimationStates.Input input,
                       Matrix4f sword, PoseStack poses, MultiBufferSource buffers, boolean powered) {
        boolean drawing = input.drawing() && input.draw() > 0.25F && input.draw() < 0.79F;
        boolean attacking = input.draw() >= 0.79F && input.swing() > 0;
        if (!drawing && !attacking) {
            var views = TRAILS.get(entity);
            if (views != null) {
                views.remove(view);
                if (views.isEmpty()) TRAILS.remove(entity);
            }
            return;
        }
        var views = TRAILS.computeIfAbsent(entity, ignored -> new EnumMap<>(View.class));
        var trail = views.computeIfAbsent(view, ignored -> new Trail());
        trail.sample(entity.getMainArm(), drawing, drawing ? input.draw() : input.swing(), input.age(), sword);
        if (trail.samples.size() < 2) return;
        VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
        Matrix4f pose = poses.last().pose();
        float brightness = powered ? 1 : 0.3F;
        for (int i = 1; i < trail.samples.size(); i++) {
            Sample from = trail.samples.get(i - 1), to = trail.samples.get(i);
            ribbon(vertices, pose, from, to, input.age(), 1, 100, 25, 70 * brightness);
            ribbon(vertices, pose, from, to, input.age(), 0.24F, 222, 145, 120 * brightness);
        }
    }

    private static void ribbon(VertexConsumer vertices, Matrix4f pose, Sample from, Sample to,
                               float time, float width, int green, int blue, float opacity) {
        vertex(vertices, pose, from, 0, time, green, blue, opacity);
        vertex(vertices, pose, to, 0, time, green, blue, opacity);
        vertex(vertices, pose, to, width, time, green, blue, opacity);
        vertex(vertices, pose, from, width, time, green, blue, opacity);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f pose, Sample sample, float inset,
                               float time, int green, int blue, float opacity) {
        float fade = 1 - (time - sample.time()) / LIFETIME;
        Vector3f tip = sample.tip(), inner = sample.inner();
        vertices.addVertex(pose, tip.x + (inner.x - tip.x) * inset,
                tip.y + (inner.y - tip.y) * inset, tip.z + (inner.z - tip.z) * inset)
                .setColor(255, green, blue, (int) (opacity * fade * fade));
    }

    enum View {
        FIRST_PERSON,
        THIRD_PERSON
    }

    private static final class Trail {

        private final ObjectArrayList<Sample> samples = new ObjectArrayList<>(MAX_SAMPLES);
        private HumanoidArm arm = HumanoidArm.RIGHT;
        private boolean drawing;
        private float phase;

        private void sample(HumanoidArm arm, boolean drawing, float phase, float time, Matrix4f sword) {
            if (!this.samples.isEmpty()) {
                Sample last = this.samples.getLast();
                // Restarting a swing, reversing into the sheath or changing the clock cannot join old ribbons.
                if (this.arm != arm || this.drawing != drawing || phase < this.phase || time < last.time()
                        || time - last.time() >= LIFETIME) this.samples.clear();
            }
            this.arm = arm;
            this.drawing = drawing;
            this.phase = phase;
            this.samples.removeIf(sample -> time - sample.time() >= LIFETIME);
            if (!this.samples.isEmpty() && time == this.samples.getLast().time()) return;
            if (this.samples.size() == MAX_SAMPLES) this.samples.removeFirst();
            this.samples.add(new Sample(time,
                    sword.transformPosition(new Vector3f(-0.04F, 50.11F, 0).div(16)),
                    sword.transformPosition(new Vector3f(-0.04F, 48.41F, 0).div(16))));
        }
    }

    private record Sample(float time, Vector3f tip, Vector3f inner) {}
}

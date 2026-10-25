package com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering;

import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModuleStyle;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.TacticalHudPreferences;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GrenadeTrajectory;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.model.HudModule;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** World-aligned guidance rendered inside LDLib2; collision prediction refreshes only with the server snapshot. */
public final class TacticalWeaponGuides extends UIElement {

    private final ObjectList<Guide> guides = new ObjectArrayList<>();
    private PlatformSnapshot displayed = PlatformSnapshot.EMPTY;

    public TacticalWeaponGuides() {
        style(value -> value.overflowVisible(true));
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        var client = context.mc;
        if (client.level == null || client.player == null) return;
        PlatformSnapshot snapshot = PlatformClientState.snapshot;
        if (!snapshot.connected() || snapshot.failed()) return;
        if (displayed != snapshot) {
            displayed = snapshot;
            guides.clear();
            for (var gun : snapshot.guns()) {
                if (!gun.settings().enabled() || gun.target() < 0) continue;
                var flight = client.level.getEntity(gun.flight());
                Vec3 origin = flight == null ? gun.origin() : flight.getBoundingBox().getCenter();
                var target = client.level.getEntity(gun.target());
                Vec3 destination = target == null ? gun.destination() : target.getBoundingBox().getCenter();
                var laser = client.level.clip(new ClipContext(origin, destination, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
                Vec3 laserEnd = laser.getType() == HitResult.Type.MISS ? destination : laser.getLocation();
                var points = new ObjectArrayList<Vec3>();
                Vec3 impact = null;
                if (GunForm.of(gun.item()) == GunForm.GRENADE) {
                    GrenadeTrajectory trajectory = GrenadeTrajectory.aimed(origin, destination);
                    int samples = Math.clamp((int) Math.ceil(trajectory.targetTick() + 20), 8, 128);
                    points.add(origin);
                    for (int step = 1; step <= samples; step++) {
                        double tick = (trajectory.targetTick() + 20) * step / samples;
                        Vec3 from = points.getLast(), to = trajectory.position(tick);
                        BlockPos blockPosition = BlockPos.containing(to);
                        if (!client.level.getChunkSource().hasChunk(blockPosition.getX() >> 4, blockPosition.getZ() >> 4)) break;
                        var block = client.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
                        if (target != null) {
                            var hit = target.getBoundingBox().inflate(0.15).clip(from, block.getType() == HitResult.Type.MISS ? to : block.getLocation());
                            if (hit.isPresent()) {
                                impact = hit.get();
                                points.add(impact);
                                break;
                            }
                        }
                        if (block.getType() != HitResult.Type.MISS) {
                            impact = block.getLocation();
                            points.add(impact);
                            break;
                        }
                        points.add(to);
                    }
                } else impact = laserEnd;
                guides.add(new Guide(gun.slot(), origin, laserEnd, points, impact));
            }
        }
        HudModuleStyle style = TacticalHudPreferences.get(HudModule.GUIDES);
        int width = client.getWindow().getGuiScaledWidth(), height = client.getWindow().getGuiScaledHeight();
        float scale = style.scale() * Math.min(width / 854f, height / 480f);
        float thickness = style.borderWidth() * scale;
        context.graphics.pose().pushPose();
        context.graphics.pose().translate(getPositionX(), getPositionY(), 0);
        for (Guide guide : guides) {
            if (thickness > 0 && style.shows(HudModuleStyle.LASER)) drawSegment(context, guide.origin, guide.laserEnd, width, height, thickness, style.borderColor());
            if (thickness > 0 && style.shows(HudModuleStyle.TRAJECTORY)) {
                for (int index = 1; index < guide.trajectory.size(); index++) {
                    // Dashes keep several overlapping arcs readable without filling the view with solid bands.
                    if ((index & 1) == 0) continue;
                    drawSegment(context, guide.trajectory.get(index - 1), guide.trajectory.get(index), width, height, thickness, style.borderColor());
                }
            }
            if (style.shows(HudModuleStyle.IMPACT) && guide.impact != null) {
                var point = TacticalProjection.project(guide.impact, width, height);
                if (point == null) continue;
                int x = Math.round(point.x), y = Math.round(point.y), radius = Math.max(2, Math.round(4 * scale));
                if (thickness > 0) {
                    int line = Math.max(1, Math.round(thickness));
                    context.graphics.fill(x - radius, y, x + radius + line, y + line, style.borderColor());
                    context.graphics.fill(x, y - radius, x + line, y + radius + line, style.borderColor());
                }
                context.graphics.pose().pushPose();
                context.graphics.pose().translate(x + radius + 2, y, 0);
                context.graphics.pose().scale(scale, scale, 1);
                String caption = "#" + (guide.slot + 1);
                context.graphics.fill(-1, -1, client.font.width(caption) + 1, 9, style.background());
                context.graphics.drawString(client.font, caption, 0, 0, style.textColor(), false);
                context.graphics.pose().popPose();
            }
        }
        context.graphics.pose().popPose();
    }

    private static void drawSegment(GUIContext context, Vec3 from, Vec3 to, int width, int height, float thickness, int color) {
        TacticalProjection.Line line = TacticalProjection.project(from, to, width, height);
        if (line == null) return;
        DrawerHelper.drawLines(context.graphics, ObjectList.of(line.from(), line.to()), color, color, Math.max(0.5f, thickness));
    }

    private record Guide(int slot, Vec3 origin, Vec3 laserEnd, ObjectList<Vec3> trajectory, @Nullable Vec3 impact) {}
}

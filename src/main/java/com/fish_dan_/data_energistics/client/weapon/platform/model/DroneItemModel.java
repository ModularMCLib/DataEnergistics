package com.fish_dan_.data_energistics.client.weapon.platform.model;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Static item poses assembled from the same independent models/textures as deployed drones. */
@NullMarked
public final class DroneItemModel extends BakedModelWrapper<BakedModel> {

    private final ObjectList<BakedQuad> quads;
    private final ObjectList<ObjectList<BakedQuad>> directionalQuads;
    private final ItemOverrides overrides;
    private final ObjectList<BakedModel> renderPasses = ObjectList.of(this);

    private DroneItemModel(BakedModel airframe, BakedModel mount, BakedModel barrel, ItemOverrides overrides) {
        super(airframe);
        var combined = new ObjectArrayList<BakedQuad>();
        append(combined, airframe, null);
        append(combined, mount, null);
        append(combined, barrel, null);
        quads = ObjectList.of(combined.toArray(BakedQuad[]::new));
        var sided = new ObjectArrayList<ObjectList<BakedQuad>>(Direction.values().length);
        for (Direction direction : Direction.values()) {
            combined = new ObjectArrayList<>();
            append(combined, airframe, direction);
            append(combined, mount, direction);
            append(combined, barrel, direction);
            sided.add(ObjectList.of(combined.toArray(BakedQuad[]::new)));
        }
        directionalQuads = sided;
        this.overrides = overrides;
    }

    /** Rebuilt on each resource reload, so no baked models survive their atlas lifecycle. */
    public static void install(ModelEvent.ModifyBakingResult event) {
        var forms = new ObjectArrayList<BakedModel>(GunForm.values().length);
        ItemOverrides overrides = new ItemOverrides() {

            @Override
            public BakedModel resolve(BakedModel originalModel, ItemStack stack, @Nullable ClientLevel level,
                                      @Nullable LivingEntity entity, int seed) {
                return forms.get(GunForm.of(stack).ordinal());
            }
        };
        var models = event.getModels();
        for (GunForm form : GunForm.values()) {
            DroneWeapon weapon = DroneWeapon.forForm(form);
            forms.add(new DroneItemModel(models.get(DroneAirframe.forForm(form).getAssembled()),
                    models.get(weapon.getMount()), models.get(weapon.getBarrel()), overrides));
        }
        event.getModels().put(ModelResourceLocation.inventory(Data_Energistics.id("digital_floating_gun")),
                forms.get(GunForm.NORMAL.ordinal()));
    }

    @Override
    public ObjectList<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return side == null ? quads : directionalQuads.get(side.ordinal());
    }

    @Override
    public ObjectList<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                          ModelData data, @Nullable RenderType renderType) {
        return getQuads(state, side, random);
    }

    @Override
    public ItemOverrides getOverrides() {
        return overrides;
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack poses, boolean leftHand) {
        originalModel.applyTransform(context, poses, leftHand);
        return this;
    }

    @Override
    public ObjectList<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
        return renderPasses;
    }

    private static void append(ObjectArrayList<BakedQuad> target, BakedModel model, @Nullable Direction side) {
        target.addAll(model.getQuads(null, side, RandomSource.create(42), ModelData.EMPTY, null));
    }
}

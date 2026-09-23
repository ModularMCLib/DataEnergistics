package com.fish_dan_.data_energistics.blockentity.patternprovider;

import com.fish_dan_.data_energistics.ae2.patternprovider.packaged.DigitalPackagedPatternProviderLogic;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderMatchingMetadata;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderMatchingMetadataSource;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedProviderMatchingMetadataResolver;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEMenus;

import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

/** Standalone packaged provider. Deliberately not an AdaptivePatternProviderHost or connector endpoint. */
public final class DigitalPackagedPatternProviderBlockEntity extends PatternProviderBlockEntity
                                                             implements PatternProviderMatchingMetadataSource {

    public DigitalPackagedPatternProviderBlockEntity(BlockPos position, BlockState state) {
        super(DEBlockEntities.DIGITAL_PACKAGED_PATTERN_PROVIDER.get(), position, state);
        getMainNode().setVisualRepresentation(DEBlocks.DIGITAL_PACKAGED_PATTERN_PROVIDER.get());
    }

    @Override
    protected DigitalPackagedPatternProviderLogic createLogic() {
        return new DigitalPackagedPatternProviderLogic(getMainNode(), this);
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(DEBlocks.DIGITAL_PACKAGED_PATTERN_PROVIDER.get().asItem());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return DEBlocks.DIGITAL_PACKAGED_PATTERN_PROVIDER.get().asItem().getDefaultInstance();
    }

    @Override
    public PatternProviderMatchingMetadata matchingMetadata(@Nullable ResourceLocation recipeCategoryId) {
        if (!(getLevel() instanceof ServerLevel level)) {
            return PatternProviderMatchingMetadata.empty();
        }
        ObjectArrayList<Direction> sides = new ObjectArrayList<>(Direction.values().length);
        for (Direction side : Direction.values()) {
            sides.add(side);
        }
        return PackagedProviderMatchingMetadataResolver.resolve(
                level,
                DataEnergisticsEntrypointLoader.snapshot().packagedCrafting(),
                getBlockPos(),
                sides,
                recipeCategoryId);
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(DEMenus.DIGITAL_PACKAGED_PATTERN_PROVIDER.get(), player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(DEMenus.DIGITAL_PACKAGED_PATTERN_PROVIDER.get(), player, subMenu.getLocator());
    }
}

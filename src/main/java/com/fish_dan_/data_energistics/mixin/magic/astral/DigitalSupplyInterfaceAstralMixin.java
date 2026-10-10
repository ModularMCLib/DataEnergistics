package com.fish_dan_.data_energistics.mixin.magic.astral;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.digitalsupply.DigitalSupplyInterfaceBlockEntity;
import com.fish_dan_.data_energistics.integration.magic.astral.AstralDigitalSupplyReceiver;
import com.fish_dan_.data_energistics.integration.magic.astral.AstralSorceryDigitalSupplyAdapter;
import com.fish_dan_.data_energistics.integration.magic.astral.DigitalSupplyTransmissionReceiverNode;

import appeng.api.storage.MEStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import hellfirepvp.astralsorcery.common.linking.LinkContainer;
import hellfirepvp.astralsorcery.common.linking.SimpleLineOfSightLinkable;
import hellfirepvp.astralsorcery.common.starlight.StarlightNetworkLevelHelper;
import hellfirepvp.astralsorcery.common.starlight.api.TransmissionNode;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import hellfirepvp.astralsorcery.common.tile.network.SimpleTransmissionNode;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/** Adds Astral's native tool-link contract without loading its classes into the ordinary block-entity class. */
@Mixin(value = DigitalSupplyInterfaceBlockEntity.class, remap = false)
public abstract class DigitalSupplyInterfaceAstralMixin implements AstralDigitalSupplyReceiver, SimpleLineOfSightLinkable {

    @Unique
    private static final String dataEnergistics$ASTRAL_LINKS_TAG = "astral_transmission_links";

    @Unique
    private LinkContainer dataEnergistics$astralLinks = new LinkContainer();

    @Unique
    private boolean dataEnergistics$astralNodeRegistered;

    @Inject(method = "serverTick", at = @At("HEAD"))
    private void dataEnergistics$ensureAstralNode(CallbackInfo callback) {
        DigitalSupplyInterfaceBlockEntity target = (DigitalSupplyInterfaceBlockEntity) (Object) this;
        if (!(target.getLevel() instanceof ServerLevel)) {
            return;
        }
        StarlightNetworkLevelHelper helper = StarlightNetworkLevelHelper.get(target.getLevel());
        BlockPos position = target.position();
        TransmissionNode node = helper.getNode(position).orElse(null);
        if (node instanceof DigitalSupplyTransmissionReceiverNode receiverNode) {
            AstralSorceryDigitalSupplyAdapter.registerTransmissionNode((ServerLevel) target.getLevel(), receiverNode);
            this.dataEnergistics$astralNodeRegistered = true;
            return;
        }
        if (node instanceof SimpleTransmissionNode simple) {
            simple.getLinkDataContainer().ifPresent(links -> {
                if (this.dataEnergistics$astralLinks.getLinkedToCount() == 0) {
                    this.dataEnergistics$astralLinks = links;
                }
            });
        }
        float lossMultiplier = node instanceof SimpleTransmissionNode simple ? simple.getTransmissionLossMultiplier() : 1.0F;
        DigitalSupplyTransmissionReceiverNode replacement = new DigitalSupplyTransmissionReceiverNode(
                position, new LinkContainer(), lossMultiplier);
        ((AstralStarlightNodeLifecycle) helper).dataEnergistics$replaceNode(position, replacement);
        AstralSorceryDigitalSupplyAdapter.registerTransmissionNode((ServerLevel) target.getLevel(), replacement);
        this.dataEnergistics$astralNodeRegistered = true;
    }

    @Inject(method = "detachAdapters", at = @At("HEAD"))
    private void dataEnergistics$removeAstralNode(CallbackInfo callback) {
        if (!this.dataEnergistics$astralNodeRegistered) {
            return;
        }
        DigitalSupplyInterfaceBlockEntity target = (DigitalSupplyInterfaceBlockEntity) (Object) this;
        if (target.getLevel() != null) {
            TransmissionNode node = StarlightNetworkLevelHelper.get(target.getLevel())
                    .getNode(target.position()).orElse(null);
            if (node instanceof DigitalSupplyTransmissionReceiverNode receiverNode) {
                AstralSorceryDigitalSupplyAdapter.unregisterTransmissionNode((ServerLevel) target.getLevel(), receiverNode);
            }
            ((AstralStarlightNodeLifecycle) StarlightNetworkLevelHelper.get(target.getLevel()))
                    .dataEnergistics$removeNode(target.position());
        }
        this.dataEnergistics$astralNodeRegistered = false;
    }

    @Override
    public @Nullable MEStorage data_energistics$networkStorage() {
        return ((DigitalSupplyInterfaceBlockEntity) (Object) this).networkStorage();
    }

    @Override
    public LinkContainer data_energistics$astralLinkContainer() {
        return this.dataEnergistics$astralLinks;
    }

    @Override
    public Optional<LinkContainer> getLinkDataContainer() {
        return Optional.of(this.dataEnergistics$astralLinks);
    }

    @Override
    public BlockPos getLinkablePos() {
        return ((DigitalSupplyInterfaceBlockEntity) (Object) this).position();
    }

    @Inject(method = "loadTag", at = @At("TAIL"))
    private void dataEnergistics$loadAstralLinks(CompoundTag data, HolderLookup.Provider registries, CallbackInfo callback) {
        if (data.contains(dataEnergistics$ASTRAL_LINKS_TAG, Tag.TAG_COMPOUND)) {
            LinkContainer.CODEC.parse(NbtOps.INSTANCE, data.getCompound(dataEnergistics$ASTRAL_LINKS_TAG))
                    .resultOrPartial(error -> Data_Energistics.LOGGER.error(
                            "Rejected Astral Digital Supply Interface links at {}: {}",
                            getLinkablePos(), error))
                    .ifPresent(links -> this.dataEnergistics$astralLinks = links);
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void dataEnergistics$saveAstralLinks(CompoundTag data, HolderLookup.Provider registries, CallbackInfo callback) {
        LinkContainer.CODEC.encodeStart(NbtOps.INSTANCE, this.dataEnergistics$astralLinks)
                .resultOrPartial(error -> Data_Energistics.LOGGER.error(
                        "Failed to save Astral Digital Supply Interface links at {}: {}",
                        getLinkablePos(), error))
                .ifPresent(value -> data.put(dataEnergistics$ASTRAL_LINKS_TAG, value));
    }

    @Override
    public void data_energistics$receiveAstralTransmission(ServerLevel level, StarlightTransmissionPacket packet) {
        AstralSorceryDigitalSupplyAdapter.receiveTransmission(this, packet);
    }
}

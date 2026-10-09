package com.fish_dan_.data_energistics.integration.magic.forbiddenarcanus.digitalsupply;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.BloodKey;
import com.fish_dan_.data_energistics.ae2.key.ExperienceKey;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceAdapter;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceTarget;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyResourceDefinition;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyTransferContext;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyTransferDirection;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyUnitConversion;

import appeng.api.stacks.AEKey;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.stal111.forbidden_arcanus.common.block.entity.forge.HephaestusForgeBlockEntity;
import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssenceType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.EnumSet;

/** Transfers Forbidden Arcanus forge essence through public two-phase digital-supply transactions. */
public final class ForbiddenArcanusDigitalSupplyAdapter implements DigitalSupplyInterfaceAdapter {

    private static final long RATE_LIMIT = 256L;
    private final ObjectList<DigitalSupplyResourceDefinition> resources = createResources();

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("forbidden_arcanus_digital_supply");
    }

    @Override
    public ObjectList<DigitalSupplyResourceDefinition> resources() {
        return resources;
    }

    @Override
    public boolean supports(DigitalSupplyInterfaceTarget target) {
        for (ConnectorLink link : target.links().bindings()) {
            if (forgeAt(target, link.position()) != null) return true;
        }
        return false;
    }

    @Override
    public void discover(DigitalSupplyInterfaceTarget target) {
        for (DigitalSupplyResourceDefinition resource : resources) target.setPresence(resource.key(), false);
        boolean present = false;
        for (ConnectorLink link : target.links().bindings()) {
            if (forgeAt(target, link.position()) != null) {
                present = true;
                break;
            }
        }
        for (DigitalSupplyResourceDefinition resource : resources) target.setPresence(resource.key(), present);
        if (present) target.refreshState();
    }

    @Override
    public void tick(DigitalSupplyInterfaceTarget target, DigitalSupplyTransferContext transfer) {
        for (ConnectorLink link : target.links().bindings()) {
            HephaestusForgeBlockEntity forge = forgeAt(target, link.position());
            if (forge == null || !target.links().isOnline(link)) continue;
            for (EssenceType type : EssenceType.values()) {
                AEKey key = keyFor(type);
                if (link.mode().supportsInput()) transfer.networkToTarget(key, RATE_LIMIT, (amount, simulate) -> change(forge, type, amount, simulate, true));
                if (link.mode().supportsPull()) transfer.targetToNetwork(key, RATE_LIMIT, DigitalSupplyTransferContext.NativeTransfer.reversible(
                        (amount, simulate) -> change(forge, type, amount, simulate, false),
                        amount -> change(forge, type, amount, false, true)));
            }
        }
    }

    private static long change(HephaestusForgeBlockEntity forge, EssenceType type, long amount, boolean simulate, boolean insert) {
        if (amount <= 0 || amount > RATE_LIMIT) return 0;
        if (forge.getRitualManager().isRitualActive()) return 0;
        var manager = forge.getEssenceManager();
        int current = manager.getEssence(type);
        if (insert) {
            if (manager.isEssenceFull(type)) return 0;
            if (simulate) return amount;
            manager.increaseEssence(type, Math.toIntExact(amount));
            return Math.max(0, manager.getEssence(type) - current);
        }
        if (current <= 0) return 0;
        long accepted = Math.min(amount, current);
        if (!simulate) manager.setEssence(type, current - Math.toIntExact(accepted));
        return accepted;
    }

    private static HephaestusForgeBlockEntity forgeAt(DigitalSupplyInterfaceTarget target, BlockPos position) {
        BlockEntity entity = target.level().getBlockEntity(position);
        return entity instanceof HephaestusForgeBlockEntity forge ? forge : null;
    }

    private static ObjectList<DigitalSupplyResourceDefinition> createResources() {
        var result = new ObjectArrayList<DigitalSupplyResourceDefinition>();
        for (ForbiddenArcanusEssenceKey key : ForbiddenArcanusEssenceKey.all()) {
            result.add(new DigitalSupplyResourceDefinition(key.getId(), key, key.getDisplayName(),
                    DigitalSupplyUnitConversion.IDENTITY, false,
                    EnumSet.of(DigitalSupplyTransferDirection.NETWORK_TO_TARGET, DigitalSupplyTransferDirection.TARGET_TO_NETWORK)));
        }
        return result;
    }

    private static AEKey keyFor(EssenceType type) {
        return switch (type) {
            case BLOOD -> BloodKey.INSTANCE;
            case EXPERIENCE -> ExperienceKey.INSTANCE;
            default -> ForbiddenArcanusEssenceKey.of(type);
        };
    }
}

package com.fish_dan_.data_energistics.integration.magic.goety.digitalsupply;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorMode;
import com.fish_dan_.data_energistics.api.registry.worldenergy.DigitalSupplyInterfaceAdapter;
import com.fish_dan_.data_energistics.api.registry.worldenergy.DigitalSupplyInterfaceTarget;
import com.fish_dan_.data_energistics.api.registry.worldenergy.WorldEnergyResourceDefinition;
import com.fish_dan_.data_energistics.api.registry.worldenergy.WorldEnergyTransferContext;
import com.fish_dan_.data_energistics.api.registry.worldenergy.WorldEnergyTransferDirection;
import com.fish_dan_.data_energistics.api.registry.worldenergy.WorldEnergyUnitConversion;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.Polarice3.Goety.api.items.magic.ITotem;
import com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity;
import com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity;
import com.Polarice3.Goety.config.MainConfig;
import com.Polarice3.Goety.utils.SEHelper;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.EnumSet;

/**
 * Exposes Goety souls and ritual experience through public block-entity and player APIs.
 *
 * <p>
 * Experience is deliberately represented as levels: one AE unit is one value of
 * {@link Player#experienceLevel}. No raw-point conversion is implied by this adapter.
 * </p>
 */
public final class GoetyDigitalSupplyAdapter implements DigitalSupplyInterfaceAdapter {

    private static final ResourceLocation ADAPTER_ID = Data_Energistics.id("goety_digital_supply");
    private static final long TICK_LIMIT = Integer.MAX_VALUE;
    private static final ObjectList<WorldEnergyResourceDefinition> RESOURCES = createResources();

    @Override
    public ResourceLocation id() {
        return ADAPTER_ID;
    }

    @Override
    public ObjectList<WorldEnergyResourceDefinition> resources() {
        return RESOURCES;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean supports(DigitalSupplyInterfaceTarget target) {
        if (target.level().isClientSide()) {
            return false;
        }
        for (ConnectorLink link : target.links().bindings()) {
            if (target.level().isLoaded(link.position()) && recognized(target.level(), link.position())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void discover(DigitalSupplyInterfaceTarget target) {
        boolean souls = false;
        boolean experience = false;
        for (BlockEntity entity : linkedEntities(target)) {
            if (entity instanceof CursedCageBlockEntity cage && soulEndpoint(cage)) {
                souls = true;
            }
            if (entity instanceof DarkAltarBlockEntity altar && experienceEndpoint(target.level(), altar)) {
                experience = true;
            }
        }
        target.setPresence(GoetySoulKey.INSTANCE, souls);
        target.setPresence(GoetyExperienceKey.INSTANCE, experience);
        target.refreshState();
    }

    @Override
    public void tick(DigitalSupplyInterfaceTarget target, WorldEnergyTransferContext transfer) {
        ObjectSet<BlockPos> visitedCages = new ObjectLinkedOpenHashSet<>();
        ObjectSet<BlockPos> visitedAltars = new ObjectLinkedOpenHashSet<>();
        for (ConnectorLink link : target.links().bindings()) {
            if (!target.level().isLoaded(link.position())) {
                continue;
            }
            BlockEntity entity = target.level().getBlockEntity(link.position());
            if (entity instanceof CursedCageBlockEntity cage && visitedCages.add(cage.getBlockPos())) {
                transferSouls(cage, link.mode(), transfer);
            } else if (entity instanceof DarkAltarBlockEntity altar && visitedAltars.add(altar.getBlockPos())) {
                transferExperience(target.level(), altar, link.mode(), transfer);
                BlockPos cagePosition = altar.getBlockPos().below();
                if (target.level().isLoaded(cagePosition) && target.level().getBlockEntity(cagePosition) instanceof CursedCageBlockEntity cage && visitedCages.add(cage.getBlockPos())) {
                    transferSouls(cage, link.mode(), transfer);
                }
            }
        }
    }

    private static ObjectList<WorldEnergyResourceDefinition> createResources() {
        ObjectArrayList<WorldEnergyResourceDefinition> resources = new ObjectArrayList<>();
        resources.add(new WorldEnergyResourceDefinition(
                GoetySoulKey.ID,
                GoetySoulKey.INSTANCE,
                GoetySoulKey.INSTANCE.getDisplayName(),
                WorldEnergyUnitConversion.IDENTITY,
                true,
                EnumSet.of(WorldEnergyTransferDirection.NETWORK_TO_WORLD, WorldEnergyTransferDirection.WORLD_TO_NETWORK)));
        resources.add(new WorldEnergyResourceDefinition(
                GoetyExperienceKey.ID,
                GoetyExperienceKey.INSTANCE,
                GoetyExperienceKey.INSTANCE.getDisplayName(),
                WorldEnergyUnitConversion.IDENTITY,
                true,
                EnumSet.of(WorldEnergyTransferDirection.NETWORK_TO_WORLD, WorldEnergyTransferDirection.WORLD_TO_NETWORK)));
        return resources;
    }

    private static boolean recognized(Level level, BlockPos position) {
        BlockEntity entity = level.getBlockEntity(position);
        return entity instanceof CursedCageBlockEntity || entity instanceof DarkAltarBlockEntity;
    }

    private static ObjectList<BlockEntity> linkedEntities(DigitalSupplyInterfaceTarget target) {
        ObjectArrayList<BlockEntity> entities = new ObjectArrayList<>();
        ObjectSet<BlockPos> positions = new ObjectLinkedOpenHashSet<>();
        for (ConnectorLink link : target.links().bindings()) {
            if (!target.level().isLoaded(link.position())) {
                continue;
            }
            if (positions.add(link.position())) {
                BlockEntity entity = target.level().getBlockEntity(link.position());
                if (entity instanceof CursedCageBlockEntity || entity instanceof DarkAltarBlockEntity) {
                    entities.add(entity);
                    if (entity instanceof DarkAltarBlockEntity) {
                        BlockPos cagePosition = link.position().below();
                        if (target.level().isLoaded(cagePosition) && positions.add(cagePosition) && target.level().getBlockEntity(cagePosition) instanceof CursedCageBlockEntity cage) {
                            entities.add(cage);
                        }
                    }
                }
            }
        }
        return entities;
    }

    private static boolean soulEndpoint(CursedCageBlockEntity cage) {
        return cage.getSouls() >= 0;
    }

    private static boolean experienceEndpoint(Level level, DarkAltarBlockEntity altar) {
        return altar.getCurrentRitualRecipe() != null && altar.castingPlayer != null && altar.castingPlayer.level() == level;
    }

    private static void transferSouls(CursedCageBlockEntity cage, ConnectorMode mode,
                                      WorldEnergyTransferContext transfer) {
        if (!soulEndpoint(cage)) {
            return;
        }
        if (mode.supportsInput()) {
            transfer.networkToWorld(GoetySoulKey.INSTANCE, TICK_LIMIT, (amount, simulate) -> changeSouls(cage, amount, simulate, true));
        }
        if (mode.supportsPull()) {
            transfer.worldToNetwork(GoetySoulKey.INSTANCE, TICK_LIMIT, (amount, simulate) -> changeSouls(cage, amount, simulate, false));
        }
    }

    private static long changeSouls(CursedCageBlockEntity cage, long amount, boolean simulate, boolean insert) {
        if (amount <= 0L) {
            return 0L;
        }
        long bounded = Math.min(amount, Integer.MAX_VALUE);
        Player owner = cage.getOwner();
        if (owner != null && SEHelper.getSEActive(owner)) {
            int current = Math.max(0, SEHelper.getSESouls(owner));
            if (insert) {
                int maximum = Math.max(current, MainConfig.MaxSouls.get());
                long accepted = Math.min(bounded, Math.max(0L, (long) maximum - current));
                if (simulate || accepted == 0L) {
                    return accepted;
                }
                if (!SEHelper.increaseSESouls(owner, (int) accepted)) {
                    return 0L;
                }
                return Math.max(0L, SEHelper.getSESouls(owner) - current);
            }
            long available = Math.min(bounded, current);
            if (simulate || available == 0L) {
                return available;
            }
            if (!SEHelper.decreaseSESouls(owner, (int) available)) {
                return 0L;
            }
            return Math.max(0L, current - SEHelper.getSESouls(owner));
        }
        if (cage.getItem().getItem() instanceof ITotem totem) {
            int maximum = Math.max(0, totem.getMaxSouls());
            int current = Math.max(0, cage.getSouls());
            long accepted = insert ? Math.min(bounded, Math.max(0L, (long) maximum - current)) : Math.min(bounded, current);
            if (simulate || accepted == 0L) {
                return accepted;
            }
            if (insert) {
                ITotem.increaseSouls(cage.getItem(), (int) accepted);
            } else {
                ITotem.decreaseSouls(cage.getItem(), (int) accepted);
            }
            cage.setItem(cage.getItem());
            return Math.max(0L, (long) cage.getSouls() - current) * (insert ? 1L : -1L);
        }
        if (insert) {
            return 0L;
        }
        int before = Math.max(0, cage.getSouls());
        long available = Math.min(bounded, before);
        if (simulate || available == 0L) {
            return available;
        }
        cage.decreaseSouls((int) available);
        return Math.max(0L, (long) before - cage.getSouls());
    }

    private static void transferExperience(Level level, DarkAltarBlockEntity altar, ConnectorMode mode,
                                           WorldEnergyTransferContext transfer) {
        if (!experienceEndpoint(level, altar)) {
            return;
        }
        if (mode.supportsInput()) {
            transfer.networkToWorld(GoetyExperienceKey.INSTANCE, TICK_LIMIT,
                    (amount, simulate) -> changeExperience(level, altar, amount, simulate, true));
        }
        if (mode.supportsPull()) {
            transfer.worldToNetwork(GoetyExperienceKey.INSTANCE, TICK_LIMIT,
                    (amount, simulate) -> changeExperience(level, altar, amount, simulate, false));
        }
    }

    private static long changeExperience(Level level, DarkAltarBlockEntity altar, long amount,
                                         boolean simulate, boolean insert) {
        if (amount <= 0L || !experienceEndpoint(level, altar)) {
            return 0L;
        }
        Player caster = altar.castingPlayer;
        int current = Math.max(0, caster.experienceLevel);
        long bounded = Math.min(amount, Integer.MAX_VALUE);
        long accepted = insert ? bounded : Math.min(bounded, current);
        if (simulate || accepted == 0L) {
            return accepted;
        }
        int delta = (int) accepted;
        caster.giveExperienceLevels(insert ? delta : -delta);
        int after = Math.max(0, caster.experienceLevel);
        return insert ? Math.max(0L, (long) after - current) : Math.max(0L, (long) current - after);
    }
}

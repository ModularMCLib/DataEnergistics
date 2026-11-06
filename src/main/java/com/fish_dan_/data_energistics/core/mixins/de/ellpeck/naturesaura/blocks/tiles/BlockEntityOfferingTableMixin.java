package com.fish_dan_.data_energistics.core.mixins.de.ellpeck.naturesaura.blocks.tiles;

import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.common.world.packaged.PackagedMachineClaims;
import com.fish_dan_.data_energistics.common.world.packaged.PackagedRecoveryJournal;
import com.fish_dan_.data_energistics.core.mixin.condition.RequireMods;
import com.fish_dan_.data_energistics.integration.magic.naturesaura.packaged.BlockEntityOfferingTableAccessor;

import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.ellpeck.naturesaura.blocks.tiles.BlockEntityOfferingTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;

/** Keeps the real, possibly randomized queue after every native tick, including queue removal. */
@RequireMods("naturesaura")
@Mixin(value = BlockEntityOfferingTable.class, remap = false)
abstract class BlockEntityOfferingTableMixin implements BlockEntityOfferingTableAccessor {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void stopRemovedOperation(CallbackInfo callback) {
        var table = (BlockEntityOfferingTable) (Object) this;
        if (table.getLevel() instanceof ServerLevel level) {
            var claims = PackagedMachineClaims.get(level);
            var owner = claims.owner(table.getBlockPos());
            if (owner != null && claims.structureRemoved(owner)) callback.cancel();
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void persistQueue(CallbackInfo callback) {
        var table = (BlockEntityOfferingTable) (Object) this;
        if (!(table.getLevel() instanceof ServerLevel level)) return;
        var owner = PackagedMachineClaims.get(level).owner(table.getBlockPos());
        if (owner == null || PackagedMachineClaims.get(level).structureRemoved(owner)) return;
        var ledger = PackagedRecoveryJournal.get(level);
        var data = ledger.read(owner);
        var queue = new ListTag();
        for (var stack : ((BlockEntityOfferingTableAccessor) table).dataEnergistics$queuedOutputs()) queue.add(stack.save(level.registryAccess()));
        if (!queue.equals(data.getList("offering_queue", 10))) table.setChanged();
        data.put("offering_queue", queue);
        ledger.write(owner, data);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean retainRejectedOutput(Level level, Entity entity, Operation<Boolean> original) {
        boolean added = original.call(level, entity);
        var table = (BlockEntityOfferingTable) (Object) this;
        if (level instanceof ServerLevel server && entity instanceof ItemEntity item) {
            var owner = PackagedMachineClaims.get(server).owner(table.getBlockPos());
            if (owner != null) {
                if (added) PackagedEntityCapture.claim(item, owner);
                else((BlockEntityOfferingTableAccessor) table).dataEnergistics$queuedOutputs().add(item.getItem().copy());
            }
        }
        return added;
    }

    @Accessor("itemsToSpawn")
    public abstract Queue<ItemStack> dataEnergistics$queuedOutputs();
}

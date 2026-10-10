package com.fish_dan_.data_energistics.integration.magic.astral;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

import net.minecraft.resources.ResourceLocation;

import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import hellfirepvp.astralsorcery.common.lumen.LumenLike;
import hellfirepvp.astralsorcery.common.lumen.LumenStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Astral's lumen capability view for an AE-backed interface.
 *
 * <p>
 * The marker set identifies lumen types only. It is never used as a quantity source; all amounts are
 * simulated and committed against the ordinary AE storage supplied by {@code networkStorage}.
 * </p>
 */
@NullMarked
public final class AstralLumenStorage implements ILumenHandler {

    private final Supplier<@Nullable MEStorage> storage;
    private final Supplier<ObjectSet<AEKey>> marks;

    public AstralLumenStorage(Supplier<@Nullable MEStorage> storage, Supplier<ObjectSet<AEKey>> marks) {
        this.storage = storage;
        this.marks = marks;
    }

    @Override
    public ObjectList<LumenStack> getContainedLumen() {
        ObjectArrayList<LumenStack> result = new ObjectArrayList<>();
        for (AEKey key : this.marks.get()) {
            if (key instanceof AstralSorceryKey astralKey && astralKey.getKind() == AstralSorceryKey.Kind.LUMEN) {
                result.add(LumenStack.of((Lumen) astralKey.getValue(), 1));
            }
        }
        return result;
    }

    @Override
    public Optional<LumenStack> getContainedLumen(LumenLike type) {
        Lumen lumen = type.asLumen();
        AstralSorceryKey key = keyFor(lumen);
        if (key == null || !this.marks.get().contains(key)) {
            return Optional.empty();
        }
        return Optional.of(LumenStack.of(lumen, 1));
    }

    @Override
    public int getCapacity(LumenLike type) {
        // Astral treats zero as a display hint. This endpoint has no local lumen tank.
        return 0;
    }

    @Override
    public int fill(LumenStack stack, Action action) {
        if (stack.isEmpty()) {
            return 0;
        }
        AstralSorceryKey key = keyFor(stack.getLumen());
        if (key == null) {
            return 0;
        }
        MEStorage network = this.storage.get();
        if (network == null) {
            return 0;
        }
        int requested = stack.getAmount();
        long inserted = network.insert(key, requested,
                action.isSimulate() ? Actionable.SIMULATE : Actionable.MODULATE,
                IActionSource.empty());
        return checkedAmount(inserted, requested, "insertion");
    }

    @Override
    public LumenStack drain(LumenLike lumen, int amount, Action action) {
        if (amount <= 0) {
            return LumenStack.EMPTY;
        }
        AstralSorceryKey key = keyFor(lumen.asLumen());
        if (key == null || !this.marks.get().contains(key)) {
            return LumenStack.EMPTY;
        }
        MEStorage network = this.storage.get();
        if (network == null) {
            return LumenStack.EMPTY;
        }
        long extracted = network.extract(key, amount,
                action.isSimulate() ? Actionable.SIMULATE : Actionable.MODULATE,
                IActionSource.empty());
        return LumenStack.of(lumen.asLumen(), checkedAmount(extracted, amount, "extraction"));
    }

    private static @Nullable AstralSorceryKey keyFor(Lumen lumen) {
        if (lumen == LumenAS.NONE.get()) {
            return null;
        }
        ResourceLocation registryId = RegistriesAS.REGISTRY_LUMEN.getKey(lumen);
        return registryId == null ? null : AstralSorceryKey.lumen(registryId, lumen);
    }

    private static int checkedAmount(long amount, int requested, String operation) {
        if (amount < 0L || amount > requested) {
            throw new IllegalStateException("Astral lumen network returned an invalid " + operation + " amount");
        }
        return (int) amount;
    }
}

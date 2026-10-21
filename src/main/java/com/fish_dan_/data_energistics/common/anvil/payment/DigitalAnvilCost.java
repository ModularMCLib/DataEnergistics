package com.fish_dan_.data_energistics.common.anvil.payment;

import com.fish_dan_.data_energistics.integration.ae.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.EchoKey;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.ExperienceKey;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.StellarFluxKey;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Rounded operation costs, independent of the player's current experience level. */
public record DigitalAnvilCost(long energy, long experienceLevels, long experiencePoints,
                               long dataFlow, long stellarFlux, long echo) {

    public static final DigitalAnvilCost ZERO = new DigitalAnvilCost(0, 0, 0, 0, 0, 0);

    public static DigitalAnvilCost calculate(long base, double penalty, boolean unrestrictedTypes,
                                             int boost, int copiedEnchantments, int copiedLevels) {
        BigDecimal levelMultiplier = BigDecimal.valueOf(100L + 5L * boost, 2);
        BigDecimal copyMultiplier = BigDecimal.valueOf(100L + 3L * copiedEnchantments, 2);
        BigDecimal multiplier = (unrestrictedTypes ? BigDecimal.valueOf(110, 2) : BigDecimal.ONE)
                .multiply(levelMultiplier).multiply(copyMultiplier);
        BigDecimal energy = BigDecimal.valueOf(1600).multiply(BigDecimal.ONE.add(
                BigDecimal.valueOf(penalty).multiply(BigDecimal.valueOf(75, 2)))).multiply(multiplier);
        long levels = ceil(BigDecimal.valueOf(base).add(BigDecimal.valueOf(penalty))
                .multiply(BigDecimal.valueOf(5, 1)).multiply(multiplier));
        // The final AE quote already includes every multiplier, so the 5% flow surcharge shares them once.
        BigDecimal flow = unrestrictedTypes ? energy.multiply(BigDecimal.valueOf(5, 2)) : BigDecimal.ZERO;
        return new DigitalAnvilCost(ceil(energy), levels, pointsForLevels(levels), ceil(flow),
                boost > 0 ? ceil(flow.multiply(BigDecimal.valueOf(3, 2))) : 0,
                copiedEnchantments > 0 ? ceil(flow.multiply(BigDecimal.valueOf(2L * copiedLevels, 2))) : 0);
    }

    public Object2LongMap<AEKey> resources() {
        Object2LongMap<AEKey> resources = new Object2LongLinkedOpenHashMap<>();
        if (experiencePoints > 0) resources.put(ExperienceKey.of(), experiencePoints);
        if (dataFlow > 0) resources.put(DataFlowKey.of(), dataFlow);
        if (stellarFlux > 0) resources.put(StellarFluxKey.of(), stellarFlux);
        if (echo > 0) resources.put(EchoKey.of(), echo);
        return resources;
    }

    /** The AE experience key stores raw points, so a quoted level cost is measured from level zero. */
    private static long pointsForLevels(long levels) {
        BigInteger level = BigInteger.valueOf(levels);
        BigInteger square = level.multiply(level);
        BigInteger points = levels <= 16 ? square.add(level.multiply(BigInteger.valueOf(6))) :
                levels <= 31 ? square.multiply(BigInteger.valueOf(5)).subtract(level.multiply(BigInteger.valueOf(81)))
                        .add(BigInteger.valueOf(720)).divide(BigInteger.TWO) :
                        square.multiply(BigInteger.valueOf(9)).subtract(level.multiply(BigInteger.valueOf(325)))
                                .add(BigInteger.valueOf(4440)).divide(BigInteger.TWO);
        return points.longValueExact();
    }

    private static long ceil(BigDecimal value) {
        return value.setScale(0, RoundingMode.CEILING).longValueExact();
    }
}

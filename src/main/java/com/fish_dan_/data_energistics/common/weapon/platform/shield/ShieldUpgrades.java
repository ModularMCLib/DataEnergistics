package com.fish_dan_.data_energistics.common.weapon.platform.shield;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record ShieldUpgrades(int capacity, int recovery, int energyPerPoint, int overloadTicks) {
    public static final ObjectList<ResourceLocation> CAPACITY = line("shield_capacity", 5);
    public static final ObjectList<ResourceLocation> RECOVERY = line("shield_recovery", 3);
    public static final ObjectList<ResourceLocation> EFFICIENCY = line("shield_efficiency", 3);
    public static final ObjectList<ResourceLocation> RESISTANCE = line("shield_resistance", 3);
    public static final ObjectList<ObjectList<ResourceLocation>> LINES = ObjectList.of(CAPACITY, RECOVERY, EFFICIENCY, RESISTANCE);
    public static final ShieldUpgrades BASE = new ShieldUpgrades(2000, 20, 100, 1200);
    public static final Codec<ShieldUpgrades> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(2000, 100000).fieldOf("capacity").forGetter(ShieldUpgrades::capacity),
            Codec.intRange(20, 1000).fieldOf("recovery").forGetter(ShieldUpgrades::recovery),
            Codec.intRange(25, 100).fieldOf("energy").forGetter(ShieldUpgrades::energyPerPoint),
            Codec.intRange(0, 1200).fieldOf("overload").forGetter(ShieldUpgrades::overloadTicks)).apply(instance, ShieldUpgrades::new));

    public static ShieldUpgrades of(ItemStack chip) {
        var completed = ItemResearchUpgrades.of(chip).ids();
        return new ShieldUpgrades(new int[] { 2000, 5000, 10000, 25000, 50000, 100000 }[level(CAPACITY, completed)],
                new int[] { 20, 100, 500, 1000 }[level(RECOVERY, completed)],
                new int[] { 100, 75, 50, 25 }[level(EFFICIENCY, completed)],
                new int[] { 1200, 600, 200, 0 }[level(RESISTANCE, completed)]);
    }

    private static int level(ObjectList<ResourceLocation> line, ObjectList<ResourceLocation> completed) {
        for (int index = line.size() - 1; index >= 0; index--) if (completed.contains(line.get(index))) return index + 1;
        return 0;
    }

    private static ObjectList<ResourceLocation> line(String name, int count) {
        var ids = new ObjectArrayList<ResourceLocation>(count);
        for (int level = 1; level <= count; level++) ids.add(Data_Energistics.id("biochip/specialized/weapon_platform/" + name + "_" + level));
        return new ObjectImmutableList<>(ids);
    }
}

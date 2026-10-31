package com.fish_dan_.data_energistics.common.item.carrier;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.data.DEItems;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class RadixContainmentSphereItemGameTest {

    private RadixContainmentSphereItemGameTest() {}

    @TestHolder("radix_containment_sphere_storage_capacity_is_256_bytes")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void storageCapacityIs256Bytes(GameTestHelper helper) {
        ItemStack sphere = DEItems.RADIX_CONTAINMENT_SPHERE.toStack();
        int bytes = DEItems.RADIX_CONTAINMENT_SPHERE.get().getBytes(sphere);
        if (bytes != 256) {
            throw new GameTestAssertException("Expected 256 storage bytes, got " + bytes);
        }
        helper.succeed();
    }
}

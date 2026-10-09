package com.fish_dan_.data_energistics.ae2.key;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

/**
 * Data Energistics-owned key space for biological resources shared by optional integrations.
 *
 * <p>
 * Blood and experience deliberately live here instead of in a third-party plugin. Optional integrations only
 * translate their native representation to these stable keys, so a network keeps one resource identity when more
 * than one compatible mod is installed.
 * </p>
 */
public final class DigitalBiologicalResourceKeyType extends AEKeyType {

    static final String RESOURCE_FIELD = "resource";
    private static final int BLOOD_PACKET_ID = 0;
    private static final int EXPERIENCE_PACKET_ID = 1;

    public static final ResourceLocation ID = Data_Energistics.id("digital_biological_resources");
    public static final DigitalBiologicalResourceKeyType TYPE = new DigitalBiologicalResourceKeyType();

    private static final MapCodec<DigitalBiologicalResourceKey> CODEC = ResourceLocation.CODEC
            .fieldOf(RESOURCE_FIELD)
            .flatXmap(DigitalBiologicalResourceKeyType::resolveResource,
                    key -> DataResult.success(key.getId()));

    private DigitalBiologicalResourceKeyType() {
        super(ID, DigitalBiologicalResourceKey.class,
                Component.translatable("key_type." + Data_Energistics.MODID + ".digital_biological_resources"));
    }

    @Override
    public MapCodec<? extends AEKey> codec() {
        return CODEC;
    }

    @Override
    public AEKey readFromPacket(RegistryFriendlyByteBuf buffer) {
        return switch (buffer.readVarInt()) {
            case BLOOD_PACKET_ID -> BloodKey.INSTANCE;
            case EXPERIENCE_PACKET_ID -> ExperienceKey.INSTANCE;
            default -> {
                Data_Energistics.LOGGER.error("Received unknown digital biological resource key packet id");
                yield null;
            }
        };
    }

    @Override
    public int getAmountPerByte() {
        return 8;
    }

    @Override
    public int getAmountPerOperation() {
        return 1;
    }

    static void writeToPacket(RegistryFriendlyByteBuf buffer, DigitalBiologicalResourceKey key) {
        if (key instanceof BloodKey) {
            buffer.writeVarInt(BLOOD_PACKET_ID);
        } else if (key instanceof ExperienceKey) {
            buffer.writeVarInt(EXPERIENCE_PACKET_ID);
        } else {
            throw new IllegalArgumentException("Unsupported digital biological resource key: " + key.getClass().getName());
        }
    }

    private static DataResult<DigitalBiologicalResourceKey> resolveResource(ResourceLocation resourceId) {
        if (resourceId.equals(BloodKey.ID)) {
            return DataResult.success(BloodKey.INSTANCE);
        }
        if (resourceId.equals(ExperienceKey.ID)) {
            return DataResult.success(ExperienceKey.INSTANCE);
        }
        return DataResult.error(() -> "Unknown digital biological resource id: " + resourceId);
    }
}

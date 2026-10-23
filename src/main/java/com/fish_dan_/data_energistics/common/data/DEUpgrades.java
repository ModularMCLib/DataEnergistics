package com.fish_dan_.data_energistics.common.data;

import com.fish_dan_.data_energistics.common.block.patternprovider.AdaptivePatternProviderBlock;
import com.fish_dan_.data_energistics.common.part.beam.BeamFormerPart;
import com.fish_dan_.data_energistics.common.part.dataripper.DataRipperPart;
import com.fish_dan_.data_energistics.common.part.patternprovider.AdaptivePatternProviderPart;
import com.fish_dan_.data_energistics.common.part.sanctum.DataSanctumInterfacePart;
import com.fish_dan_.data_energistics.common.part.solar.MeSolarPanelPart;
import com.fish_dan_.data_energistics.common.part.terminal.UniversalTerminalPart;
import com.fish_dan_.data_energistics.integration.ae.ae2.cell.InfiniteDataCellHandler;
import com.fish_dan_.data_energistics.integration.ae.ae2.dataflow.DataFlowBusStrategies;
import com.fish_dan_.data_energistics.integration.ae.ae2.dataflow.DigitalStorageCellHandler;

import appeng.api.parts.PartModels;
import appeng.api.storage.StorageCells;
import appeng.api.upgrades.Upgrades;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.items.parts.PartModelsHelper;

public final class DEUpgrades {

    private static final String ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP = "block.data_energistics.adaptive_pattern_provider";
    private static final String DATA_SANCTUM_INTERFACE_UPGRADE_TOOLTIP_GROUP = "block.data_energistics.data_sanctum_interface";
    private static final String DATA_SANCTUM_INTERFACE_PART_UPGRADE_TOOLTIP_GROUP = "item.data_energistics.data_sanctum_interface_part";

    private DEUpgrades() {}

    public static void init() {
        DataFlowBusStrategies.register();
        ((AdaptivePatternProviderBlock<?>) DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get()).bindBlockEntity();
        DEBlocks.DIGITAL_PACKAGED_PATTERN_PROVIDER.get().bindBlockEntity();
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.RESEARCH_STATION.get(), DEItems.RESEARCH_STATION.get());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.ME_DIGITAL_ANVIL.get(), DEItems.ME_DIGITAL_ANVIL.get());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.RESEARCH_STATION_PART.get(), DEItems.RESEARCH_STATION.get());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DIGITAL_PACKAGED_PATTERN_PROVIDER.get(), DEItems.DIGITAL_PACKAGED_PATTERN_PROVIDER.get());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_SOLAR_PANEL_BLOCK_ENTITY.get(), DEBlocks.DATA_SOLAR_PANEL.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DIGITAL_STORAGE_DEPOT_BLOCK_ENTITY.get(), DEBlocks.DIGITAL_STORAGE_DEPOT.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(), DEBlocks.DATA_EXTRACTOR.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_RIPPER_REASSEMBLER_BLOCK_ENTITY.get(), DEBlocks.DATA_RIPPER_REASSEMBLER.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_ASYNCHRONOUS_PROCESSING_FACTORY_BLOCK_ENTITY.get(), DEBlocks.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_INTEGRATED_CHARGER_BLOCK_ENTITY.get(), DEBlocks.DATA_INTEGRATED_CHARGER.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_IO_PORT.get(), DEBlocks.DATA_IO_PORT.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_ENERGY_CELL_BLOCK_ENTITY.get(), DEBlocks.DATA_ENERGY_CELL.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.TRINITY_DATA_CORE_BLOCK_ENTITY.get(), DEBlocks.TRINITY_DATA_CORE.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY.get(), DEBlocks.DATA_DISTRIBUTION_TOWER.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.ME_BEAM_FORMER.get(), DEItems.ME_BEAM_FORMER.get());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.ME_OMNI_BEAM_FORMER.get(), DEItems.ME_OMNI_BEAM_FORMER.get());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get(), DEBlocks.DATA_MIMETIC_FIELD.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_TELEPORT_ANCHOR_BLOCK_ENTITY.get(), DEBlocks.DATA_TELEPORT_ANCHOR.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_SANCTUM_BLOCK_ENTITY.get(), DEBlocks.DATA_SANCTUM.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_SANCTUM_INTERFACE_BLOCK_ENTITY.get(), DEBlocks.DATA_SANCTUM_INTERFACE.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY.get(), DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.COMPOSITE_WAREHOUSE_BLOCK_ENTITY.get(), DEBlocks.COMPOSITE_INPUT_WAREHOUSE.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.COMPOSITE_WAREHOUSE_BLOCK_ENTITY.get(), DEBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.ME_COMPOSITE_INPUT_WAREHOUSE_BLOCK_ENTITY.get(), DEBlocks.ME_COMPOSITE_INPUT_WAREHOUSE.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.ME_COMPOSITE_OUTPUT_WAREHOUSE_BLOCK_ENTITY.get(), DEBlocks.ME_COMPOSITE_OUTPUT_WAREHOUSE.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.ME_PATTERN_BUFFER_BLOCK_ENTITY.get(), DEBlocks.ME_PATTERN_BUFFER.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.TRINITY_INFORMATION_EXCHANGE_DEPOT_BLOCK_ENTITY.get(), DEBlocks.TRINITY_INFORMATION_EXCHANGE_DEPOT.get().asItem());
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.DATA_RIPPER.get(), 8, "item.data_energistics.data_ripper");
        Upgrades.add(AEItems.SPEED_CARD, DEItems.DATA_RIPPER.get(), 5, "item.data_energistics.data_ripper");
        Upgrades.add(AEItems.SPEED_CARD, DEItems.DATA_IO_PORT.get(), 4, "block.data_energistics.data_io_port");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEItems.DATA_IO_PORT.get(), 4, "block.data_energistics.data_io_port");
        Upgrades.add(AEItems.REDSTONE_CARD, DEItems.DATA_IO_PORT.get(), 1, "block.data_energistics.data_io_port");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEItems.DATA_RIPPER.get(), 5, "item.data_energistics.data_ripper");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEItems.ME_BEAM_FORMER_PART.get(), 3, "item.data_energistics.me_beam_former_part");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEItems.ME_BEAM_FORMER.get(), 3, "block.data_energistics.me_beam_former");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEItems.ME_OMNI_BEAM_FORMER.get(), 3, "block.data_energistics.me_omni_beam_former");
        Upgrades.add(AEItems.INVERTER_CARD, DEItems.DATA_RIPPER.get(), 5, "item.data_energistics.data_ripper");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DIGITAL_STORAGE_CELL_1K.get(), 3,
                "item.data_energistics.portable_digital_storage_cell_1k");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DIGITAL_STORAGE_CELL_4K.get(), 3,
                "item.data_energistics.portable_digital_storage_cell_4k");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DIGITAL_STORAGE_CELL_16K.get(), 3,
                "item.data_energistics.portable_digital_storage_cell_16k");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DIGITAL_STORAGE_CELL_64K.get(), 3,
                "item.data_energistics.portable_digital_storage_cell_64k");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DIGITAL_STORAGE_CELL_256K.get(), 3,
                "item.data_energistics.portable_digital_storage_cell_256k");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DIGITAL_STORAGE_CELL_1M.get(), 3,
                "item.data_energistics.portable_digital_storage_cell_1m");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DIGITAL_STORAGE_CELL_4M.get(), 3,
                "item.data_energistics.portable_digital_storage_cell_4m");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DIGITAL_STORAGE_CELL_16M.get(), 3,
                "item.data_energistics.portable_digital_storage_cell_16m");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DIGITAL_STORAGE_CELL_64M.get(), 3,
                "item.data_energistics.portable_digital_storage_cell_64m");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DIGITAL_STORAGE_CELL_256M.get(), 3,
                "item.data_energistics.portable_digital_storage_cell_256m");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.RADIX_CONTAINMENT_SPHERE.get(), 3,
                "item.data_energistics.radix_containment_sphere");
        Upgrades.add(AEItems.FUZZY_CARD, DEItems.RADIX_CONTAINMENT_SPHERE.get(), 1,
                "item.data_energistics.radix_containment_sphere");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.ME_VACUUM.get(), 3,
                "item.data_energistics.me_vacuum");
        Upgrades.add(AEItems.ENERGY_CARD, DEBlocks.DATA_EXTRACTOR.get(), 7, "block.data_energistics.data_extractor");
        Upgrades.add(AEItems.CAPACITY_CARD, DEBlocks.DATA_EXTRACTOR.get(), 7, "block.data_energistics.data_extractor");
        Upgrades.add(AEItems.SPEED_CARD, DEBlocks.DATA_EXTRACTOR.get(), 5, "block.data_energistics.data_extractor");
        Upgrades.add(AEItems.FUZZY_CARD, DEBlocks.DATA_EXTRACTOR.get(), 1, "block.data_energistics.data_extractor");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEBlocks.DATA_RIPPER_REASSEMBLER.get(), 2, "block.data_energistics.data_reassembler");
        Upgrades.add(AEItems.SPEED_CARD, DEBlocks.DATA_RIPPER_REASSEMBLER.get(), 4, "block.data_energistics.data_reassembler");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEBlocks.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get(), 2,
                "block.data_energistics.data_asynchronous_processing_factory");
        Upgrades.add(AEItems.SPEED_CARD, DEBlocks.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get(), 4,
                "block.data_energistics.data_asynchronous_processing_factory");
        Upgrades.add(AEItems.SPEED_CARD, DEBlocks.DATA_INTEGRATED_CHARGER.get(), 4,
                "block.data_energistics.data_integrated_charger");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEBlocks.DATA_INTEGRATED_CHARGER.get(), 2,
                "block.data_energistics.data_integrated_charger");
        Upgrades.add(AEItems.SPEED_CARD, DEBlocks.DATA_SOLAR_PANEL.get(), 3, "block.data_energistics.me_solar_panel");
        Upgrades.add(AEItems.ENERGY_CARD, DEBlocks.DATA_SOLAR_PANEL.get(), 3, "block.data_energistics.me_solar_panel");
        Upgrades.add(AEItems.SPEED_CARD, DEBlocks.ME_DATA_SOLAR_PANEL.get(), 3, "block.data_energistics.me_data_solar_panel");
        Upgrades.add(AEItems.ENERGY_CARD, DEBlocks.ME_DATA_SOLAR_PANEL.get(), 3, "block.data_energistics.me_data_solar_panel");
        Upgrades.add(AEItems.CAPACITY_CARD, DEBlocks.DIGITAL_STORAGE_DEPOT.get(), 4, "block.data_energistics.digital_storage_depot");
        Upgrades.add(AEItems.SPEED_CARD, DEItems.ME_SOLAR_PANEL_PART.get(), 3, "item.data_energistics.me_solar_panel_part");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.ME_SOLAR_PANEL_PART.get(), 3, "item.data_energistics.me_solar_panel_part");
        Upgrades.add(AEItems.SPEED_CARD, DEItems.ME_DATA_SOLAR_PANEL_PART.get(), 3, "item.data_energistics.me_data_solar_panel_part");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.ME_DATA_SOLAR_PANEL_PART.get(), 3, "item.data_energistics.me_data_solar_panel_part");
        Upgrades.add(AEItems.CAPACITY_CARD, DEBlocks.DATA_MIMETIC_FIELD.get(), 1, "block.data_energistics.data_mimetic_field");
        Upgrades.add(AEItems.SPEED_CARD, DEBlocks.DATA_MIMETIC_FIELD.get(), 4, "block.data_energistics.data_mimetic_field");
        Upgrades.add(AEItems.VOID_CARD, DEBlocks.DATA_MIMETIC_FIELD.get(), 1, "block.data_energistics.data_mimetic_field");
        Upgrades.add(AEItems.ENERGY_CARD, DEBlocks.DATA_SANCTUM.get(), 3, "block.data_energistics.data_sanctum");
        Upgrades.add(AEItems.CAPACITY_CARD, DEBlocks.COMPOSITE_INPUT_WAREHOUSE.get(), 5, "block.data_energistics.composite_input_warehouse");
        Upgrades.add(AEItems.CAPACITY_CARD, DEBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get(), 5, "block.data_energistics.composite_output_warehouse");
        Upgrades.add(AEItems.CAPACITY_CARD, DEBlocks.DATA_SANCTUM_INTERFACE.get(), 3, DATA_SANCTUM_INTERFACE_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(AEItems.CAPACITY_CARD, DEItems.DATA_SANCTUM_INTERFACE_PART.get(), 3, DATA_SANCTUM_INTERFACE_PART_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(AEItems.CRAFTING_CARD, DEBlocks.DATA_SANCTUM_INTERFACE.get(), 1, DATA_SANCTUM_INTERFACE_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(AEItems.CRAFTING_CARD, DEItems.DATA_SANCTUM_INTERFACE_PART.get(), 1, DATA_SANCTUM_INTERFACE_PART_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(AEItems.FUZZY_CARD, DEBlocks.DATA_SANCTUM_INTERFACE.get(), 1, DATA_SANCTUM_INTERFACE_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(AEItems.FUZZY_CARD, DEItems.DATA_SANCTUM_INTERFACE_PART.get(), 1, DATA_SANCTUM_INTERFACE_PART_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(AEItems.CAPACITY_CARD, DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get(), 3, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(AEItems.CAPACITY_CARD, DEItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), 3, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.MATTER_CONVERGING_CROSSBOW.get(), 2,
                "item.data_energistics.star_shard");
        Upgrades.add(AEItems.FUZZY_CARD, DEItems.MATTER_CONVERGING_CROSSBOW.get(), 1,
                "item.data_energistics.star_shard");
        Upgrades.add(AEItems.INVERTER_CARD, DEItems.MATTER_CONVERGING_CROSSBOW.get(), 1,
                "item.data_energistics.star_shard");
        Upgrades.add(AEItems.VOID_CARD, DEItems.MATTER_CONVERGING_CROSSBOW.get(), 1,
                "item.data_energistics.star_shard");
        Upgrades.add(AEItems.SPEED_CARD, DEItems.MATTER_CONVERGING_CROSSBOW.get(), 4,
                "item.data_energistics.star_shard");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEItems.MATTER_CONVERGING_CROSSBOW.get(), 2,
                "item.data_energistics.star_shard");
        Upgrades.add(DEItems.REDSTONE_TUNING_CARD.get(), DEItems.MATTER_CONVERGING_CROSSBOW.get(), 1,
                "item.data_energistics.star_shard");
        Upgrades.add(DEItems.REDSTONE_TUNING_CARD.get(), AEBlocks.PATTERN_PROVIDER.block(), 1, "block.ae2.pattern_provider");
        Upgrades.add(DEItems.REDSTONE_TUNING_CARD.get(), DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get(), 1, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(DEItems.REDSTONE_TUNING_CARD.get(), DEItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), 1,
                ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        StorageCells.addCellHandler(DigitalStorageCellHandler.INSTANCE);
        StorageCells.addCellHandler(InfiniteDataCellHandler.INSTANCE);
    }

    public static void registerPartModels() {
        PartModels.registerModels(PartModelsHelper.createModels(BeamFormerPart.class));
        PartModels.registerModels(
                PartModelsHelper.createModels(DataRipperPart.class));
        PartModels.registerModels(
                PartModelsHelper.createModels(AdaptivePatternProviderPart.class));
        PartModels.registerModels(
                PartModelsHelper.createModels(DataSanctumInterfacePart.class));
        PartModels.registerModels(
                PartModelsHelper.createModels(MeSolarPanelPart.class));
        PartModels.registerModels(
                PartModelsHelper.createModels(UniversalTerminalPart.class));
    }
}

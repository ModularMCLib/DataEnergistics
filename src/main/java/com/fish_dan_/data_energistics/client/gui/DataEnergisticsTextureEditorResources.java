package com.fish_dan_.data_energistics.client.gui;

import com.fish_dan_.data_energistics.Data_Energistics;

import com.lowdragmc.lowdraglib2.editor.resource.BuiltinResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.EditorResourceEvent;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance;
import com.lowdragmc.lowdraglib2.editor.resource.TexturesResource;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;

/**
 * Registers the Data Energistics texture library in the LDLib2 UI Editor.
 *
 * <p>
 * The editor already knows how to configure every texture type used here. This registry only
 * gives the existing assets and compositions stable, selectable resource names.
 * </p>
 */
@OnlyIn(Dist.CLIENT)
public final class DataEnergisticsTextureEditorResources {

    private static final String TRINITY_TEXTURE_ROOT = Data_Energistics.MODID + ":textures/guis/trinity_data_core/";
    private static final String AUTO_BUILD_TEXTURE_ROOT = Data_Energistics.MODID + ":textures/guis/autobuild/";
    private static final String CRAFTING_REPORT_TEXTURE_ROOT = Data_Energistics.MODID + ":textures/guis/craftingreport/";
    private static final String GUI_TEXTURE_ROOT = Data_Energistics.MODID + ":textures/guis/";
    private static final String BACKPACK_TEXTURE_ROOT = Data_Energistics.MODID + ":textures/guis/backpack/";
    private static final String MODEL_TEXTURE_ROOT = Data_Energistics.MODID + ":textures/guis/model/";
    private static final String PRIORITY_TEXTURE_ROOT = Data_Energistics.MODID + ":textures/guis/priority/";
    private static final String SLOT_TEXTURE_ROOT = Data_Energistics.MODID + ":textures/guis/slot/";
    private static final String STORAGE_TEXTURE_ROOT = Data_Energistics.MODID + ":textures/guis/storage/";

    private DataEnergisticsTextureEditorResources() {}

    /**
     * Attaches the LDLib2 resource event listener to the mod event bus before the editor is opened.
     *
     * @param modEventBus client mod event bus used by LDLib2 to publish editor resource events
     */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(DataEnergisticsTextureEditorResources::loadBuiltinResources);
    }

    /**
     * Adds the project's texture library after LDLib2 creates its built-in texture providers.
     *
     * @param event LDLib2 built-in resource event
     */
    private static void loadBuiltinResources(EditorResourceEvent.LoadBuiltin event) {
        if (event.resourceInstance.resource != TexturesResource.INSTANCE) {
            return;
        }

        ResourceInstance<IGuiTexture> resourceInstance = textureResourceInstance(event);
        var provider = new BuiltinResourceProvider<>(Data_Energistics.MODID, resourceInstance);
        registerComposedTextures(provider);
        registerTextureAssets(provider);
        registerScreenTextures(provider);
        registerStateTextures(provider);
        resourceInstance.addBuiltinProvider(provider);
    }

    /**
     * Obtains the texture resource instance currently being built by LDLib2 without triggering its
     * lazy initialization again from inside the LoadBuiltin callback.
     *
     * @param event LDLib2 built-in resource event for the texture resource
     * @return the resource instance carried by the event
     */
    @SuppressWarnings("unchecked")
    private static ResourceInstance<IGuiTexture> textureResourceInstance(EditorResourceEvent.LoadBuiltin event) {
        return (ResourceInstance<IGuiTexture>) event.resourceInstance;
    }

    /**
     * Registers the named compositions currently used by the LDLib2 Trinity and compartment UIs.
     *
     * @param provider provider receiving the composed textures
     */
    private static void registerComposedTextures(BuiltinResourceProvider<IGuiTexture> provider) {
        provider.addResource("trinity_root_background", GuiTextureGroup.of(
                new ColorRectTexture(0xFFE3E3EA),
                new ColorBorderTexture(-1, 0xFF696D88)));
        provider.addResource("trinity_player_slot_background", GuiTextureGroup.of(
                guiSprite("inventory_slot.png"),
                new ColorBorderTexture(-1, 0xFF696D88)));
        provider.addResource("trinity_section_background", GuiTextureGroup.of(
                new ColorRectTexture(0xFFA7ADBF),
                new ColorBorderTexture(-1, 0xFFF2F2F2)));
        provider.addResource("composite_warehouse_sidebar_background", GuiTextureGroup.of(
                new ColorRectTexture(0xFFE3E3EA),
                new ColorBorderTexture(-1, 0xFF777784)));
        provider.addResource("composite_warehouse_optional_fluid_slot", slotSprite("optional_fluid.png"));
        provider.addResource("composite_warehouse_optional_key_slot", slotSprite("optional_key.png"));
        provider.addResource("pattern_buffer_empty_slot", guiSprite("states/background/blank_pattern.png"));
    }

    /**
     * Registers the project's raw PNG assets so they can be selected as SpriteTexture sources.
     *
     * @param provider provider receiving the raw texture assets
     */
    private static void registerTextureAssets(BuiltinResourceProvider<IGuiTexture> provider) {
        provider.addResource("autobuild_build", autobuildSprite("build.png"));
        provider.addResource("autobuild_detailed_adjustment", autobuildSprite("detailed_adjustment.png"));
        provider.addResource("autobuild_jei_detailed_adjustment", autobuildSprite("jei_detailed_adjustment.png"));
        provider.addResource("autobuild_detailed_material_stats", autobuildSprite("detailed_material_stats.png"));
        provider.addResource("autobuild_structure_switch", autobuildSprite("structure_switch.png"));
        provider.addResource("autobuild_structure_view", autobuildSprite("structure_view.png"));
        provider.addResource("backpack_above_average", backpackSprite("above_average.png"));
        provider.addResource("backpack_below_average", backpackSprite("below_average.png"));
        provider.addResource("backpack_bottom_left", backpackSprite("bottom_left.png"));
        provider.addResource("backpack_bottom_right", backpackSprite("bottom_right.png"));
        provider.addResource("backpack_checkbox", backpackSprite("checkbox.png"));
        provider.addResource("backpack_in_the_inventory", backpackSprite("in_the_inventory.png"));
        provider.addResource("backpack_left_center", backpackSprite("left_center.png"));
        provider.addResource("backpack_left_of_the_inventory", backpackSprite("left_of_the_inventory.png"));
        provider.addResource("backpack_middle", backpackSprite("middle.png"));
        provider.addResource("backpack_right_middle", backpackSprite("right_middle.png"));
        provider.addResource("backpack_right_side_of_the_inventory", backpackSprite("right_side_of_the_inventory.png"));
        provider.addResource("backpack_top_left", backpackSprite("top_left.png"));
        provider.addResource("backpack_top_right", backpackSprite("top_right.png"));
        provider.addResource("craftingreport_background", craftingReportSprite("craftingreport.png"));
        provider.addResource("craftingreport_big_scroller", craftingReportSprite("big_scroller.png"));
        provider.addResource("craftingreport_big_scroller_disabled", craftingReportSprite("big_scroller_disabled.png"));
        provider.addResource("craftingreport_big_scroller_highlighted", craftingReportSprite("big_scroller_highlighted.png"));
        provider.addResource("craftingreport_button", craftingReportSprite("button.png"));
        provider.addResource("craftingreport_button_disabled", craftingReportSprite("button_disabled.png"));
        provider.addResource("craftingreport_button_highlighted", craftingReportSprite("button_highlighted.png"));
        provider.addResource("craftingreport_button_1", craftingReportSprite("button_1.png"));
        provider.addResource("craftingreport_button_disabled_1", craftingReportSprite("button_disabled_1.png"));
        provider.addResource("craftingreport_button_highlighted_1", craftingReportSprite("button_highlighted_1.png"));
        provider.addResource("gui_back", guiSprite("back.png"));
        provider.addResource("gui_background", guiSprite("background.png"));
        provider.addResource("gui_button", guiSprite("botton.png"));
        provider.addResource("gui_button_disabled", guiSprite("button_disabled.png"));
        provider.addResource("gui_button_highlighted", guiSprite("button_highlighted.png"));
        provider.addResource("gui_close", guiSprite("close.png"));
        provider.addResource("gui_front", guiSprite("front.png"));
        provider.addResource("gui_inventory_slot", guiSprite("inventory_slot.png"));
        provider.addResource("gui_trinity_information_exchange_depot", guiSprite("trinity_information_exchange_depot.png"));
        provider.addResource("gui_prepare_on", guiSprite("prepare_on.png"));
        provider.addResource("gui_prepare_off", guiSprite("prepare_off.png"));
        provider.addResource("gui_on", guiSprite("on.png"));
        provider.addResource("gui_off", guiSprite("off.png"));
        provider.addResource("gui_priority", guiSprite("priority.png"));
        provider.addResource("gui_small_highlighted", guiSprite("small_highlighted.png"));
        provider.addResource("gui_small_scroller", guiSprite("small_scroller.png"));
        provider.addResource("gui_small_scroller_disabled", guiSprite("small_scroller_disabled.png"));
        provider.addResource("gui_text_field", guiSprite("text_field.png"));
        provider.addResource("gui_text_field_checkbox", guiSprite("text_field_checkbox.png"));
        provider.addResource("model_home", modelSprite("home.png"));
        provider.addResource("model_input", modelSprite("input.png"));
        provider.addResource("model_input_and_output", modelSprite("input_and_output.png"));
        provider.addResource("model_material_recycling", modelSprite("material_recycling.png"));
        provider.addResource("model_pattern_row", modelSprite("model.png"));
        provider.addResource("model_output", modelSprite("output.png"));
        provider.addResource("model_template_rollback", modelSprite("template_rollback.png"));
        provider.addResource("model_template_send", modelSprite("template_send.png"));
        provider.addResource("priority_button", prioritySprite("botton.png"));
        provider.addResource("priority_button_disabled", prioritySprite("button_disabled.png"));
        provider.addResource("priority_button_highlighted", prioritySprite("button_highlighted.png"));
        provider.addResource("priority_home", prioritySprite("home.png"));
        provider.addResource("slot_button_slot", slotSprite("button_slot.png"));
        provider.addResource("slot_button_slot_1", slotSprite("button_slot_1.png"));
        provider.addResource("slot_button_slot_2", slotSprite("button_slot_2.png"));
        provider.addResource("slot_model", slotSprite("model.png"));
        provider.addResource("slot_storage", slotSprite("storage.png"));
        provider.addResource("storage_home", storageSprite("home.png"));
        provider.addResource("trinity_cpu_entry", sprite("cpu_entry.png"));
        provider.addResource("trinity_cpu_entry_selected", sprite("cpu_entry_selected.png"));
        provider.addResource("trinity_cpu_icon_craft", sprite("cpu_icon_craft.png"));
        provider.addResource("trinity_cpu_icon_machine", sprite("cpu_icon_machine.png"));
        provider.addResource("trinity_cpu_icon_processor", sprite("cpu_icon_processor.png"));
        provider.addResource("trinity_cpu_icon_storage", sprite("cpu_icon_storage.png"));
        provider.addResource("trinity_cpu_icon_terminal", sprite("cpu_icon_terminal.png"));
        provider.addResource("trinity_cpu_idle", sprite("cpu_idle.png"));
        provider.addResource("trinity_cpu_panel", sprite("cpu_panel.png"));
        provider.addResource("trinity_cpu_task_overlay", sprite("cpu_task_overlay.png"));
        provider.addResource("trinity_drive", sprite("trinity_drive.png"));
        provider.addResource("trinity_drive_slot_0", sprite("drive_slot_0.png"));
        provider.addResource("trinity_drive_slot_1", sprite("drive_slot_1.png"));
        provider.addResource("trinity_drive_slot_2", sprite("drive_slot_2.png"));
        provider.addResource("trinity_drive_slot_3", sprite("drive_slot_3.png"));
        provider.addResource("compartment_warehouse", guiSprite("compartment/warehouse.png"));
        provider.addResource("compartment_me_input", guiSprite("compartment/me_input.png"));
        provider.addResource("compartment_me_output", guiSprite("compartment/me_output.png"));
        provider.addResource("compartment_pattern_buffer", guiSprite("compartment/pattern_buffer.png"));
        provider.addResource("recipe_reassembler", guiSprite("recipe/reassembler.png"));
        provider.addResource("recipe_charge_press", guiSprite("recipe/charge_press.png"));
        provider.addResource("recipe_condenser", guiSprite("recipe/condenser.png"));
        provider.addResource("progress_processing", guiSprite("progress/processing.png"));
        provider.addResource("trinity_home", sprite("home.png"));
        provider.addResource("trinity_host_layout_reference", sprite("host_layout_reference.png"));
        provider.addResource("trinity_status_panel", sprite("status_panel.png"));
        provider.addResource("trinity_storage_capacity_track", sprite("storage_capacity_track.png"));
        provider.addResource("trinity_storage_fluid_fill", sprite("storage_fluid_fill.png"));
        provider.addResource("trinity_storage_item_fill", sprite("storage_item_fill.png"));
        provider.addResource("trinity_storage_other_fill", sprite("storage_other_fill.png"));
    }

    /** Registers complete screen backgrounds without source rectangles from the old GUI sheets. */
    private static void registerScreenTextures(BuiltinResourceProvider<IGuiTexture> provider) {
        provider.addResource("background_adaptive_pattern_provider", guiSprite("background/adaptive_pattern_provider.png"));
        provider.addResource("background_data_asynchronous_processing_factory", guiSprite("background/data_asynchronous_processing_factory.png"));
        provider.addResource("background_data_distribution_tower", guiSprite("background/data_distribution_tower.png"));
        provider.addResource("background_data_extractor", guiSprite("background/data_extractor.png"));
        provider.addResource("background_data_integrated_charger", guiSprite("background/data_integrated_charger.png"));
        provider.addResource("background_data_mimetic_field", guiSprite("background/data_mimetic_field.png"));
        provider.addResource("background_data_reassembler", guiSprite("background/data_reassembler.png"));
        provider.addResource("background_data_ripper", guiSprite("background/data_ripper.png"));
        provider.addResource("background_data_sanctum_interface", guiSprite("background/data_sanctum_interface.png"));
        provider.addResource("background_data_sanctum_status", guiSprite("background/data_sanctum_status.png"));
        provider.addResource("background_data_teleport_anchor", guiSprite("background/data_teleport_anchor.png"));
        provider.addResource("background_digital_packaged_pattern_provider", guiSprite("background/digital_packaged_pattern_provider.png"));
        provider.addResource("background_digital_storage_depot", guiSprite("background/digital_storage_depot.png"));
        provider.addResource("background_io_port", guiSprite("background/io_port.png"));
        provider.addResource("background_me_solar_panel", guiSprite("background/me_solar_panel.png"));
        provider.addResource("background_me_vacuum", guiSprite("background/me_vacuum.png"));
        provider.addResource("background_order_package", guiSprite("background/order_package.png"));
        provider.addResource("background_set_output_sides", guiSprite("background/set_output_sides.png"));
        provider.addResource("background_star_shard_config", guiSprite("background/star_shard_config.png"));
    }

    /** Registers all sliced state assets with category-prefixed, collision-free editor names. */
    private static void registerStateTextures(BuiltinResourceProvider<IGuiTexture> provider) {
        provider.addResource("states_progress", guiSprite("states/progress.png"));
        provider.addResource("states_progress_green", guiSprite("states/progress_green.png"));
        provider.addResource("states_progress_red", guiSprite("states/progress_red.png"));
        provider.addResource("states_progress_orange", guiSprite("states/progress_orange.png"));
        provider.addResource("states_progress_yellow", guiSprite("states/progress_yellow.png"));
        provider.addResource("states_progress_purple", guiSprite("states/progress_purple.png"));
        provider.addResource("states_background_addition", guiSprite("states/background/addition.png"));
        provider.addResource("states_background_carrier_0", guiSprite("states/background/carrier_0.png"));
        provider.addResource("states_background_carrier_1", guiSprite("states/background/carrier_1.png"));
        provider.addResource("states_background_complex_biological_chip", guiSprite("states/background/complex_biological_chip.png"));
        provider.addResource("states_background_crop", guiSprite("states/background/crop.png"));
        provider.addResource("states_background_ore", guiSprite("states/background/ore.png"));
        provider.addResource("states_background_progress", guiSprite("states/background/progress.png"));
        provider.addResource("states_background_small_anvil", guiSprite("states/background/small_anvil.png"));
        provider.addResource("states_background_small_forging", guiSprite("states/background/small_forging.png"));
        provider.addResource("states_background_small_handle", guiSprite("states/background/small_handle.png"));
        provider.addResource("states_background_small_stone_cutting", guiSprite("states/background/small_stone_cutting.png"));
        provider.addResource("states_background_small_synthesize", guiSprite("states/background/small_synthesize.png"));
        provider.addResource("states_background_square", guiSprite("states/background/square.png"));
        provider.addResource("states_background_blank_pattern", guiSprite("states/background/blank_pattern.png"));
        provider.addResource("states_background_trash", guiSprite("states/background/trash.png"));
        provider.addResource("states_background_trash_slot", guiSprite("states/background/trash_slot.png"));
        provider.addResource("states_background_sword", guiSprite("states/background/sword.png"));
        provider.addResource("states_background_synthesize", guiSprite("states/background/synthesize.png"));
        provider.addResource("states_background_toppin_background", guiSprite("states/background/toppin_background.png"));
        provider.addResource("states_small_top_surface_small_anvil", guiSprite("states/small_top_surface/small_anvil.png"));
        provider.addResource("states_small_top_surface_small_arrow_down_0", guiSprite("states/small_top_surface/small_arrow_down_0.png"));
        provider.addResource("states_small_top_surface_small_arrow_down_1", guiSprite("states/small_top_surface/small_arrow_down_1.png"));
        provider.addResource("states_small_top_surface_small_arrow_up_0", guiSprite("states/small_top_surface/small_arrow_up_0.png"));
        provider.addResource("states_small_top_surface_small_arrow_up_1", guiSprite("states/small_top_surface/small_arrow_up_1.png"));
        provider.addResource("states_small_top_surface_small_button_0", guiSprite("states/small_top_surface/small_button_0.png"));
        provider.addResource("states_small_top_surface_small_button_1", guiSprite("states/small_top_surface/small_button_1.png"));
        provider.addResource("states_small_top_surface_small_button_2", guiSprite("states/small_top_surface/small_button_2.png"));
        provider.addResource("states_small_top_surface_small_close_drag", guiSprite("states/small_top_surface/small_close_drag.png"));
        provider.addResource("states_small_top_surface_small_coding", guiSprite("states/small_top_surface/small_coding.png"));
        provider.addResource("states_small_top_surface_small_cog", guiSprite("states/small_top_surface/small_cog.png"));
        provider.addResource("states_small_top_surface_small_forge_button_0", guiSprite("states/small_top_surface/small_forge_button_0.png"));
        provider.addResource("states_small_top_surface_small_forge_button_1", guiSprite("states/small_top_surface/small_forge_button_1.png"));
        provider.addResource("states_small_top_surface_small_forge_button_2", guiSprite("states/small_top_surface/small_forge_button_2.png"));
        provider.addResource("states_small_top_surface_small_forging", guiSprite("states/small_top_surface/small_forging.png"));
        provider.addResource("states_small_top_surface_small_handle", guiSprite("states/small_top_surface/small_handle.png"));
        provider.addResource("states_small_top_surface_small_horizontal_tab", guiSprite("states/small_top_surface/small_horizontal_tab.png"));
        provider.addResource("states_small_top_surface_small_horizontal_tab_selected", guiSprite("states/small_top_surface/small_horizontal_tab_selected.png"));
        provider.addResource("states_small_top_surface_small_magnet", guiSprite("states/small_top_surface/small_magnet.png"));
        provider.addResource("states_small_top_surface_small_maintain", guiSprite("states/small_top_surface/small_maintain.png"));
        provider.addResource("states_small_top_surface_small_pattern_access_part", guiSprite("states/small_top_surface/small_pattern_access_part.png"));
        provider.addResource("states_small_top_surface_small_sidebar_0", guiSprite("states/small_top_surface/small_sidebar_0.png"));
        provider.addResource("states_small_top_surface_small_sidebar_1", guiSprite("states/small_top_surface/small_sidebar_1.png"));
        provider.addResource("states_small_top_surface_small_sidebar_2", guiSprite("states/small_top_surface/small_sidebar_2.png"));
        provider.addResource("states_small_top_surface_small_slider_0", guiSprite("states/small_top_surface/small_slider_0.png"));
        provider.addResource("states_small_top_surface_small_slider_1", guiSprite("states/small_top_surface/small_slider_1.png"));
        provider.addResource("states_small_top_surface_small_slider_2", guiSprite("states/small_top_surface/small_slider_2.png"));
        provider.addResource("states_small_top_surface_small_sort_by_amount", guiSprite("states/small_top_surface/small_sort_by_amount.png"));
        provider.addResource("states_small_top_surface_small_sort_by_name", guiSprite("states/small_top_surface/small_sort_by_name.png"));
        provider.addResource("states_small_top_surface_small_stone_cutting", guiSprite("states/small_top_surface/small_stone_cutting.png"));
        provider.addResource("states_small_top_surface_small_storage", guiSprite("states/small_top_surface/small_storage.png"));
        provider.addResource("states_small_top_surface_small_storage_synthesis", guiSprite("states/small_top_surface/small_storage_synthesis.png"));
        provider.addResource("states_small_top_surface_small_synthesis", guiSprite("states/small_top_surface/small_synthesis.png"));
        provider.addResource("states_small_top_surface_small_synthesize", guiSprite("states/small_top_surface/small_synthesize.png"));
        provider.addResource("states_small_top_surface_small_text_box", guiSprite("states/small_top_surface/small_text_box.png"));
        provider.addResource("states_small_top_surface_small_text_box_0", guiSprite("states/small_top_surface/small_text_box_0.png"));
        provider.addResource("states_small_top_surface_small_text_box_1", guiSprite("states/small_top_surface/small_text_box_1.png"));
        provider.addResource("states_small_top_surface_small_toppin", guiSprite("states/small_top_surface/small_toppin.png"));
        provider.addResource("states_small_top_surface_small_type_filter_all", guiSprite("states/small_top_surface/small_type_filter_all.png"));
        provider.addResource("states_small_top_surface_small_zoom", guiSprite("states/small_top_surface/small_zoom.png"));
        provider.addResource("states_small_top_surface_mode_button", guiSprite("states/small_top_surface/mode_button.png"));
        provider.addResource("states_top_surface_ae_fe", guiSprite("states/top_surface/ae_fe.png"));
        provider.addResource("states_top_surface_ae_output", guiSprite("states/top_surface/ae_output.png"));
        provider.addResource("states_top_surface_add", guiSprite("states/top_surface/add.png"));
        provider.addResource("states_top_surface_aggregation_mode", guiSprite("states/top_surface/aggregation_mode.png"));
        provider.addResource("states_top_surface_black", guiSprite("states/top_surface/black.png"));
        provider.addResource("states_top_surface_black_hole", guiSprite("states/top_surface/black_hole.png"));
        provider.addResource("states_top_surface_black_hole_crack_close", guiSprite("states/top_surface/black_hole_crack_close.png"));
        provider.addResource("states_top_surface_blacklist", guiSprite("states/top_surface/blacklist.png"));
        provider.addResource("states_top_surface_charging_mode", guiSprite("states/top_surface/charging_mode.png"));
        provider.addResource("states_top_surface_disabled", guiSprite("states/top_surface/disabled.png"));
        provider.addResource("states_top_surface_delete", guiSprite("states/top_surface/delete.png"));
        provider.addResource("states_top_surface_embossing_mode", guiSprite("states/top_surface/embossing_mode.png"));
        provider.addResource("states_top_surface_enabled", guiSprite("states/top_surface/enabled.png"));
        provider.addResource("states_top_surface_fluid", guiSprite("states/top_surface/fluid.png"));
        provider.addResource("states_top_surface_hammer", guiSprite("states/top_surface/hammer.png"));
        provider.addResource("states_top_surface_item", guiSprite("states/top_surface/item.png"));
        provider.addResource("states_top_surface_key", guiSprite("states/top_surface/key.png"));
        provider.addResource("states_top_surface_maintain", guiSprite("states/top_surface/maintain.png"));
        provider.addResource("states_top_surface_point_to_point_", guiSprite("states/top_surface/point_to_point_.png"));
        provider.addResource("states_top_surface_production_boost_mode", guiSprite("states/top_surface/production_boost_mode.png"));
        provider.addResource("states_top_surface_prohibit", guiSprite("states/top_surface/prohibit.png"));
        provider.addResource("states_top_surface_powder_mode", guiSprite("states/top_surface/powder_mode.png"));
        provider.addResource("states_top_surface_pull_enabled", guiSprite("states/top_surface/pull_enabled.png"));
        provider.addResource("states_top_surface_pull_disabled", guiSprite("states/top_surface/pull_disabled.png"));
        provider.addResource("states_top_surface_range", guiSprite("states/top_surface/range.png"));
        provider.addResource("states_top_surface_recipe_record_0", guiSprite("states/top_surface/recipe_record_0.png"));
        provider.addResource("states_top_surface_recipe_record_1", guiSprite("states/top_surface/recipe_record_1.png"));
        provider.addResource("states_top_surface_settings", guiSprite("states/top_surface/settings.png"));
        provider.addResource("states_top_surface_small_forge_button_0", guiSprite("states/top_surface/small_forge_button_0.png"));
        provider.addResource("states_top_surface_small_forge_button_1", guiSprite("states/top_surface/small_forge_button_1.png"));
        provider.addResource("states_top_surface_small_forge_button_2", guiSprite("states/top_surface/small_forge_button_2.png"));
        provider.addResource("states_top_surface_text_box", guiSprite("states/top_surface/text_box.png"));
        provider.addResource("states_top_surface_text_box_0", guiSprite("states/top_surface/text_box_0.png"));
        provider.addResource("states_top_surface_text_box_1", guiSprite("states/top_surface/text_box_1.png"));
        provider.addResource("states_top_surface_toolbar_button", guiSprite("states/top_surface/toolbar_button.png"));
        provider.addResource("states_top_surface_toppin", guiSprite("states/top_surface/toppin.png"));
        provider.addResource("states_top_surface_whitelist", guiSprite("states/top_surface/whitelist.png"));
        provider.addResource("digital_panel", guiSprite("digital_panel.png"));
    }

    /**
     * Creates an editable LDLib2 SpriteTexture for a Data Energistics GUI asset.
     *
     * @param fileName asset file under the Trinity Data Core GUI texture directory
     * @return SpriteTexture pointing at the mod asset
     */
    private static SpriteTexture sprite(String fileName) {
        return SpriteTexture.of(TRINITY_TEXTURE_ROOT + fileName);
    }

    /**
     * Creates an editable LDLib2 SpriteTexture for an automatic-build GUI asset.
     *
     * @param fileName asset file under the automatic-build GUI texture directory
     * @return SpriteTexture pointing at the mod asset
     */
    private static SpriteTexture autobuildSprite(String fileName) {
        return SpriteTexture.of(AUTO_BUILD_TEXTURE_ROOT + fileName);
    }

    /**
     * Creates an editable LDLib2 SpriteTexture for the crafting-report GUI assets.
     *
     * @param fileName asset file under the crafting-report GUI texture directory
     * @return SpriteTexture pointing at the mod asset
     */
    private static SpriteTexture craftingReportSprite(String fileName) {
        return SpriteTexture.of(CRAFTING_REPORT_TEXTURE_ROOT + fileName);
    }

    /**
     * Creates an editable LDLib2 SpriteTexture for a top-level GUI asset.
     *
     * @param fileName asset file directly under the GUI texture directory
     * @return SpriteTexture pointing at the mod asset
     */
    private static SpriteTexture guiSprite(String fileName) {
        return SpriteTexture.of(GUI_TEXTURE_ROOT + fileName);
    }

    /**
     * Creates an editable LDLib2 SpriteTexture for a model GUI asset.
     *
     * @param fileName asset file under the model GUI texture directory
     * @return SpriteTexture pointing at the mod asset
     */
    private static SpriteTexture modelSprite(String fileName) {
        return SpriteTexture.of(MODEL_TEXTURE_ROOT + fileName);
    }

    /**
     * Creates an editable LDLib2 SpriteTexture for a priority GUI asset.
     *
     * @param fileName asset file under the priority GUI texture directory
     * @return SpriteTexture pointing at the mod asset
     */
    private static SpriteTexture prioritySprite(String fileName) {
        return SpriteTexture.of(PRIORITY_TEXTURE_ROOT + fileName);
    }

    /**
     * Creates an editable LDLib2 SpriteTexture for a backpack GUI asset.
     *
     * @param fileName asset file under the backpack GUI texture directory
     * @return SpriteTexture pointing at the mod asset
     */
    private static SpriteTexture backpackSprite(String fileName) {
        return SpriteTexture.of(BACKPACK_TEXTURE_ROOT + fileName);
    }

    /**
     * Creates an editable LDLib2 SpriteTexture for a slot GUI asset.
     *
     * @param fileName asset file under the slot GUI texture directory
     * @return SpriteTexture pointing at the mod asset
     */
    private static SpriteTexture slotSprite(String fileName) {
        return SpriteTexture.of(SLOT_TEXTURE_ROOT + fileName);
    }

    /**
     * Creates an editable LDLib2 SpriteTexture for a storage GUI asset.
     *
     * @param fileName asset file under the storage GUI texture directory
     * @return SpriteTexture pointing at the mod asset
     */
    private static SpriteTexture storageSprite(String fileName) {
        return SpriteTexture.of(STORAGE_TEXTURE_ROOT + fileName);
    }
}

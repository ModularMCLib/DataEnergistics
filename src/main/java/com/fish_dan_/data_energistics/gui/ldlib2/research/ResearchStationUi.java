package com.fish_dan_.data_energistics.gui.ldlib2.research;

import com.fish_dan_.data_energistics.api.research.ResearchInput;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.api.research.ResearchScope;
import com.fish_dan_.data_energistics.menu.research.ResearchStationMenu;
import com.fish_dan_.data_energistics.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.research.persistence.ResearchNbt;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Locale;

/** Both-side slot tree; all research and team requests remain server-authoritative menu actions. */
public final class ResearchStationUi {

    public static final int WIDTH = 440;
    public static final int HEIGHT = 256;
    private static final ResourceLocation ITEM_ROOT = ResourceLocation.fromNamespaceAndPath("data_energistics", "research_item_root");
    private final ResearchStationMenu menu;
    private final UIElement root = new UIElement();
    private final UIElement research = new UIElement();
    private final ResearchGraphView graph = new ResearchGraphView();
    private final ScrollerView details = ResearchUiParts.scroll(280, 26, 151, 124);
    private final ScrollerView projects = ResearchUiParts.scroll(280, 166, 151, 81);
    private final Label empty = ResearchUiParts.label(ResearchUiParts.text("catalog_empty"));
    private final Label targetStatus = ResearchUiParts.label(Component.empty());
    private final Label projectTitle = ResearchUiParts.label(ResearchUiParts.text("projects", 0));
    private final ResearchTeamPanel teams;
    private final Object2ObjectMap<ResourceLocation, ResearchNode> projectNodes = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<ResourceLocation, CompoundTag> projectData = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<ResourceLocation, ResearchGraphNode> graphNodes = new Object2ObjectLinkedOpenHashMap<>();
    private ObjectSet<ResourceLocation> unlocked = new ObjectOpenHashSet<>();
    private ObjectList<ResourceLocation> visibleIds = new ObjectArrayList<>();
    private ItemStack previousTarget = ItemStack.EMPTY;
    private long revision = -1;
    private long graphDefinitionRevision = -1;
    private ResearchScope scope = ResearchScope.TECHNOLOGY;
    private Filter filter = Filter.ALL;
    private boolean showTeams;
    private boolean backpackOpen;
    private boolean rebuild = true;
    private @Nullable ResourceLocation selected;
    private @Nullable ResourceLocation hovered;

    private ResearchStationUi(ResearchStationMenu menu) {
        this.menu = menu;
        this.teams = new ResearchTeamPanel(menu);
        root.setId("research_station_root");
        root.layout(layout -> layout.width(WIDTH).height(HEIGHT));
        root.style(style -> style.backgroundTexture(SpriteTexture.of(ResourceLocation.fromNamespaceAndPath(
                "data_energistics", "textures/gui/digital_transformation_research_station.png"))));
        ResearchUiParts.at(research, 0, 0, WIDTH, HEIGHT);
        root.addChild(research);
        root.addChild(ResearchUiParts.at(ResearchUiParts.label(ResearchUiParts.text("title")), 8, 7, 280, 14));
        Button teamTab = ResearchUiParts.button(ResearchUiParts.text("team_tab"), this::toggleTeams);
        teamTab.addEventListener(UIEvents.TICK, event -> teamTab.text.setText(ResearchUiParts.text(showTeams ? "research_tab" : "team_tab")));
        root.addChild(ResearchUiParts.at(teamTab, 327, 5, 65, 16));
        root.addChild(ResearchUiParts.at(ResearchUiParts.button(Component.literal("×"), () -> menu.getPlayer().closeContainer()), 411, 5, 20, 16));
        Button technologyTab = ResearchUiParts.button(ResearchUiParts.text("technology"), () -> setScope(ResearchScope.TECHNOLOGY));
        technologyTab.addEventListener(UIEvents.TICK, event -> technologyTab.text.setText(
                Component.literal(scope == ResearchScope.TECHNOLOGY ? "● " : "").append(ResearchUiParts.text("technology"))));
        research.addChild(ResearchUiParts.at(technologyTab, 8, 26, 89, 17));
        Button upgradeTab = ResearchUiParts.button(ResearchUiParts.text("item_upgrade"), () -> setScope(ResearchScope.ITEM_UPGRADE));
        upgradeTab.addEventListener(UIEvents.TICK, event -> upgradeTab.text.setText(
                Component.literal(scope == ResearchScope.ITEM_UPGRADE ? "● " : "").append(ResearchUiParts.text("item_upgrade"))));
        research.addChild(ResearchUiParts.at(upgradeTab, 100, 26, 91, 17));
        Button filterButton = ResearchUiParts.button(ResearchUiParts.text("filter_all"), () -> {
            filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
            rebuild = true;
        });
        filterButton.addEventListener(UIEvents.TICK, event -> filterButton.text.setText(
                ResearchUiParts.text("filter_" + filter.name().toLowerCase(Locale.ROOT))));
        research.addChild(ResearchUiParts.at(filterButton, 194, 26, 79, 17));
        research.addChild(ResearchUiParts.at(graph, 8, 47, 265, 150));
        research.addChild(ResearchUiParts.at(empty, 15, 164, 249, 30));
        research.addChild(ResearchUiParts.at(ResearchUiParts.button(Component.literal("−"),
                () -> graph.graphView.setScale(graph.graphView.getScale() - 0.15F)), 8, 200, 20, 16));
        research.addChild(ResearchUiParts.at(ResearchUiParts.button(Component.literal("+"),
                () -> graph.graphView.setScale(graph.graphView.getScale() + 0.15F)), 31, 200, 20, 16));
        research.addChild(ResearchUiParts.at(ResearchUiParts.button(ResearchUiParts.text("reset"), graph::fitGraphChildren), 54, 200, 55, 16));
        research.addChild(ResearchUiParts.at(ResearchUiParts.label(ResearchUiParts.text("navigation")), 115, 202, 158, 13));
        research.addChild(ResearchUiParts.panel(278, 24, 155, 128));
        research.addChild(details);
        research.addChild(ResearchUiParts.panel(278, 155, 155, 94));
        research.addChild(ResearchUiParts.at(projectTitle, 282, 156, 146, 12));
        research.addChild(projects);
        ItemSlot target = new ItemSlot(menu.targetSlot());
        target.setId("research_station_target");
        target.style(style -> style.backgroundTexture(new ColorRectTexture(ResearchUiParts.SURFACE)));
        research.addChild(ResearchUiParts.at(target, 8, 225, 18, 18));
        research.addChild(ResearchUiParts.at(targetStatus, 30, 223, 158, 26));
        UIElement backpack = ResearchUiParts.panel(73, 111, 174, 105);
        backpack.style(style -> style.zIndex(1000));
        backpack.setId("research_station_backpack");
        Label backpackHeader = ResearchUiParts.at(ResearchUiParts.label(ResearchUiParts.text("backpack")), 4, 3, 155, 14);
        backpackHeader.setAllowHitTest(true);
        backpack.addChild(backpackHeader);
        InventorySlots inventory = ResearchUiParts.at(new InventorySlots(), 6, 22, 162, 77);
        inventory.setId("research_station_player_inventory");
        inventory.apply(slot -> slot.style(style -> style.backgroundTexture(new ColorRectTexture(ResearchUiParts.SURFACE))));
        inventory.hotbar.layout(layout -> layout.marginTop(4));
        backpack.addChild(inventory);
        WindowDragHelper.setDragMove(backpackHeader, backpack, event -> event.button == 0, null);
        root.addChild(teams);
        ResearchUiParts.show(teams, false);
        root.addChild(backpack);
        ResearchUiParts.show(backpack, false);
        root.addChild(ResearchUiParts.at(ResearchUiParts.button(ResearchUiParts.text("backpack"), () -> {
            backpackOpen = !backpackOpen;
            ResearchUiParts.show(backpack, backpackOpen);
        }), 193, 225, 80, 18));
        root.addEventListener(UIEvents.TICK, event -> {
            if (!menu.isServerSide()) refresh();
        });
    }

    /** Mount native LDLib2 slots once on both sides; this station has no AE menu or AE screen contract. */
    public static ModularUI mount(ResearchStationMenu menu) {
        AbstractContainerMenu container = menu;
        if (!(container instanceof IModularUIHolderMenu holder) || holder.getModularUI() != null || !menu.slots.isEmpty()) {
            throw new IllegalStateException("Research station requires an unmounted native LDLib2 menu");
        }
        ResearchStationUi station = new ResearchStationUi(menu);
        ModularUI ui = ModularUI.of(UI.of(station.root), menu.getPlayer());
        holder.setModularUI(ui);
        if (menu.slots.size() != 37 || menu.slots.getFirst() != menu.targetSlot()) {
            throw new IllegalStateException("Research station must mount exactly one target and 36 native player slots");
        }
        for (int index = 1; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            ItemSlot mounted = holder.getItemSlot(slot);
            int inventoryIndex = index <= 27 ? index + 8 : index - 28;
            if (slot.container != menu.getPlayer().getInventory() || slot.getContainerSlot() != inventoryIndex ||
                    mounted == null || mounted.getSlot() != slot) {
                throw new IllegalStateException("Research station native player slot mapping diverged at " + index);
            }
        }
        return ui;
    }

    private ItemStack target() {
        return menu.targetSlot().getItem();
    }

    private void setScope(ResearchScope value) {
        scope = value;
        selected = null;
        hovered = null;
        rebuild = true;
    }

    private void toggleTeams() {
        showTeams = !showTeams;
        ResearchUiParts.show(research, !showTeams);
        ResearchUiParts.show(teams, showTeams);
    }

    private void refresh() {
        boolean targetChanged = !ItemStack.matches(previousTarget, target());
        boolean snapshotChanged = revision != menu.viewRevision();
        if (!targetChanged && !snapshotChanged && !rebuild) return;
        if (targetChanged) {
            if (!target().isEmpty() && (previousTarget.isEmpty() || !ItemStack.isSameItem(previousTarget, target()))) {
                setScope(ResearchScope.ITEM_UPGRADE);
            }
            previousTarget = target().copy();
            rebuild = true;
        }
        if (snapshotChanged || targetChanged) {
            revision = menu.viewRevision();
            unlocked = ResearchNbt.readIds(menu.snapshot(), "unlocked");
            unlocked.addAll(ItemResearchUpgrades.of(target()).ids());
            projectNodes.clear();
            projectData.clear();
            for (Tag value : menu.snapshot().getList("projects", Tag.TAG_COMPOUND)) {
                CompoundTag project = (CompoundTag) value;
                ResearchNode node = ResearchNbt.readNode(project.getCompound("node"), menu.getPlayer().registryAccess());
                projectNodes.put(node.id(), node);
                projectData.put(node.id(), project);
            }
            teams.refresh(menu.snapshot().getCompound("teams"));
        }
        refreshGraph();
        refreshDetails();
        refreshProjects();
        targetStatus.setText(ResearchUiParts.text(menu.targetLocked() ? "target_locked" : target().isEmpty() ? "target_empty" : "target_ready"));
    }

    private boolean available(ResearchNode node) {
        return unlocked.containsAll(node.prerequisites()) && !unlocked.contains(node.id()) && !projectData.containsKey(node.id());
    }

    private void refreshGraph() {
        ObjectList<ResearchNode> shown = new ObjectArrayList<>();
        for (ResearchNode node : menu.nodes().values()) {
            if (node.scope() != scope || (scope == ResearchScope.ITEM_UPGRADE && !node.target().matches(target()))) continue;
            if (filter == Filter.AVAILABLE && !available(node)) continue;
            if (filter == Filter.UNLOCKED && !unlocked.contains(node.id())) continue;
            shown.add(node);
        }
        shown.sort(Comparator.comparing(node -> node.id().toString()));
        ObjectList<ResourceLocation> nextIds = new ObjectArrayList<>();
        shown.forEach(node -> nextIds.add(node.id()));
        long definitions = menu.snapshot().getLong("definitions_revision");
        boolean topologyChanged = rebuild || definitions != graphDefinitionRevision || !visibleIds.equals(nextIds);
        if (topologyChanged) {
            visibleIds = nextIds;
            graphDefinitionRevision = definitions;
            buildGraph(shown);
            rebuild = false;
        }
        for (ResearchNode node : shown) {
            CompoundTag pending = lookup(projectData, node.id());
            Component state = unlocked.contains(node.id()) ? ResearchUiParts.text("unlocked") : pending != null ?
                    projectStatus(node, pending) : ResearchUiParts.text(available(node) ? "available" : "unavailable");
            int color = unlocked.contains(node.id()) ? 0xFF50785F : pending != null ? 0xFF826941 : available(node) ? 0xFF505F87 : 0xFF666A78;
            graphNodes.get(node.id()).state(state, color);
        }
        ResearchUiParts.show(empty, shown.isEmpty());
        empty.setText(ResearchUiParts.text(scope == ResearchScope.ITEM_UPGRADE && target().isEmpty() ? "insert_item" :
                menu.nodes().isEmpty() ? "catalog_empty" : "filter_empty"));
        graph.focus(selected, hovered);
    }

    private void buildGraph(ObjectList<ResearchNode> nodes) {
        ResearchGraph model = new ResearchGraph();
        graphNodes.clear();
        Object2IntOpenHashMap<ResourceLocation> depth = new Object2IntOpenHashMap<>();
        ObjectSet<ResourceLocation> visible = new ObjectOpenHashSet<>();
        nodes.forEach(node -> visible.add(node.id()));
        // The validated definition catalog is a DAG; relaxing sorted nodes gives deterministic ranks.
        for (int pass = 0; pass < nodes.size(); pass++) {
            for (ResearchNode node : nodes) {
                int rank = scope == ResearchScope.ITEM_UPGRADE ? 1 : 0;
                for (ResourceLocation prerequisite : node.prerequisites()) {
                    if (visible.contains(prerequisite)) rank = Math.max(rank, depth.getInt(prerequisite) + 1);
                }
                depth.put(node.id(), rank);
            }
        }
        Int2IntOpenHashMap rows = new Int2IntOpenHashMap();
        if (scope == ResearchScope.ITEM_UPGRADE && !target().isEmpty()) {
            ResearchGraphNode item = new ResearchGraphNode(ITEM_ROOT, target().getHoverName(), target().copy(), this::select, this::hover);
            item.state(ResearchUiParts.text("initial_item"), 0xFF505F87);
            item.setPosition(new Vector2f(0, 0));
            model.models().add(item);
            graphNodes.put(ITEM_ROOT, item);
        }
        for (ResearchNode node : nodes) {
            ResearchGraphNode view = new ResearchGraphNode(node.id(), Component.translatable(node.name()),
                    new ItemStack(BuiltInRegistries.ITEM.get(node.icon())), this::select, this::hover);
            int column = depth.getInt(node.id());
            int row = rows.get(column);
            rows.put(column, row + 1);
            view.setPosition(new Vector2f(column * 145F, row * 82F));
            model.models().add(view);
            graphNodes.put(node.id(), view);
        }
        for (ResearchNode node : nodes) {
            ResearchGraphNode to = graphNodes.get(node.id());
            boolean connected = false;
            for (ResourceLocation id : node.prerequisites()) {
                ResearchGraphNode from = lookup(graphNodes, id);
                if (from != null) {
                    model.models().connect(from.getOutputsById().get("unlocks"), to.getInputsById().get("requires"));
                    connected = true;
                }
            }
            if (!connected && graphNodes.containsKey(ITEM_ROOT)) {
                model.models().connect(graphNodes.get(ITEM_ROOT).getOutputsById().get("unlocks"), to.getInputsById().get("requires"));
            }
        }
        graph.loadGraph(model);
        if (selected != null && graphNodes.containsKey(selected)) graph.addSelected(graphNodes.get(selected));
        graph.focus(selected, hovered);
    }

    private void select(ResourceLocation id) {
        selected = id;
        graph.clearAllSelected();
        if (graphNodes.containsKey(id)) graph.addSelected(graphNodes.get(id));
        graph.focus(selected, hovered);
        refreshDetails();
    }

    private void hover(@Nullable ResourceLocation id) {
        hovered = id;
        graph.focus(selected, hovered);
    }

    private void refreshDetails() {
        details.clearAllScrollViewChildren();
        if (ITEM_ROOT.equals(selected)) {
            details.addScrollViewChild(ResearchUiParts.label(target().getHoverName()));
            details.addScrollViewChild(ResearchUiParts.label(ResearchUiParts.text("initial_description")));
            return;
        }
        ResearchNode node = selected == null ? null : lookup(projectNodes, selected);
        if (node == null && selected != null) node = lookup(menu.nodes(), selected);
        if (node == null) {
            details.addScrollViewChild(ResearchUiParts.label(ResearchUiParts.text("select_node")));
            return;
        }
        ResearchNode displayed = node;
        details.addScrollViewChild(ResearchUiParts.label(Component.translatable(node.name())));
        details.addScrollViewChild(ResearchUiParts.label(Component.translatable(node.description())));
        details.addScrollViewChild(ResearchUiParts.label(ResearchUiParts.text("time", node.researchTicks(), format(node.researchTicks() / 20.0))));
        details.addScrollViewChild(ResearchUiParts.label(ResearchUiParts.text("energy", format(node.aePerTick()), format(node.aePerTick() * node.researchTicks()))));
        details.addScrollViewChild(ResearchUiParts.label(ResearchUiParts.text("materials")));
        if (node.inputs().isEmpty()) details.addScrollViewChild(ResearchUiParts.label(ResearchUiParts.text("materials_none")));
        for (ResearchInput input : node.inputs()) {
            details.addScrollViewChild(ResearchUiParts.label(ResearchUiParts.text("material", input.key().getDisplayName(), input.amount())));
        }
        for (ResourceLocation id : node.prerequisites()) {
            ResearchNode prerequisite = lookup(menu.nodes(), id);
            details.addScrollViewChild(ResearchUiParts.label(ResearchUiParts.text(unlocked.contains(id) ? "prerequisite_met" : "prerequisite_missing",
                    prerequisite == null ? Component.literal(id.toString()) : Component.translatable(prerequisite.name()))));
        }
        CompoundTag pending = lookup(projectData, node.id());
        if (unlocked.contains(node.id())) {
            details.addScrollViewChild(ResearchUiParts.label(ResearchUiParts.text("unlocked")));
        } else if (pending != null) {
            details.addScrollViewChild(ResearchUiParts.label(projectStatus(node, pending)));
            if (pending.getBoolean("running")) {
                details.addScrollViewChild(ResearchUiParts.button(ResearchUiParts.text("cancel"), () -> menu.request("cancel", displayed.id().toString())));
            } else {
                var resume = ResearchUiParts.button(ResearchUiParts.text("resume"), () -> menu.request("resume", displayed.id().toString()));
                resume.setActive(runningCount() < 2 && !pending.getBoolean("faulted"));
                details.addScrollViewChild(resume);
            }
        } else {
            var start = ResearchUiParts.button(ResearchUiParts.text("start"), () -> menu.request("start", displayed.id().toString()));
            start.setActive(available(node) && runningCount() < 2 && canTarget(node));
            details.addScrollViewChild(start);
        }
    }

    private boolean canTarget(ResearchNode node) {
        return node.scope() == ResearchScope.TECHNOLOGY || (!target().isEmpty() && node.target().matches(target()));
    }

    private int runningCount() {
        int count = 0;
        for (CompoundTag pending : projectData.values()) if (pending.getBoolean("running")) count++;
        return count;
    }

    private void refreshProjects() {
        projects.clearAllScrollViewChildren();
        projectTitle.setText(ResearchUiParts.text("projects", runningCount()));
        if (projectData.isEmpty()) projects.addScrollViewChild(ResearchUiParts.label(ResearchUiParts.text("projects_empty")));
        ObjectList<ResourceLocation> ordered = new ObjectArrayList<>(projectData.keySet());
        ordered.sort(Comparator.comparingLong(id -> projectData.get(id).getLong("order")));
        for (ResourceLocation id : ordered) {
            ResearchNode node = projectNodes.get(id);
            CompoundTag pending = projectData.get(id);
            projects.addScrollViewChild(ResearchUiParts.button(Component.translatable(node.name()), () -> select(id)));
            projects.addScrollViewChild(ResearchUiParts.label(projectStatus(node, pending)));
            int progress = pending.getInt("progress");
            UIElement bar = new UIElement();
            bar.layout(layout -> layout.widthPercent(100).height(4).flexShrink(0));
            bar.style(style -> style.backgroundTexture(new ColorRectTexture(0xFF696F87)));
            UIElement fill = new UIElement();
            fill.layout(layout -> layout.widthPercent(100F * progress / node.researchTicks()).height(4));
            fill.style(style -> style.backgroundTexture(new ColorRectTexture(0xFFF2F2F2)));
            bar.addChild(fill);
            projects.addScrollViewChild(bar);
            if (pending.getBoolean("running")) {
                projects.addScrollViewChild(ResearchUiParts.button(ResearchUiParts.text("cancel"), () -> menu.request("cancel", id.toString())));
            } else {
                var resume = ResearchUiParts.button(ResearchUiParts.text("resume"), () -> menu.request("resume", id.toString()));
                resume.setActive(runningCount() < 2 && !pending.getBoolean("faulted"));
                projects.addScrollViewChild(resume);
            }
        }
    }

    private static Component projectStatus(ResearchNode node, CompoundTag project) {
        String state = project.getBoolean("faulted") ? "faulted" : !project.getBoolean("running") ? "paused" :
                project.getBoolean("powered") ? "running" : "no_power";
        return ResearchUiParts.text("project_status", ResearchUiParts.text(state), project.getInt("progress"), node.researchTicks());
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** Fastutil's unannotated get contract needs an explicit absent-key result at this UI boundary. */
    private static <V> @Nullable V lookup(Object2ObjectMap<ResourceLocation, V> values, ResourceLocation id) {
        return values.containsKey(id) ? values.get(id) : null;
    }

    private enum Filter {
        ALL,
        AVAILABLE,
        UNLOCKED
    }
}

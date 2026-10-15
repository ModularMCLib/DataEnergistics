package com.fish_dan_.data_energistics.gui.ldlib2.research;

import com.fish_dan_.data_energistics.api.research.ResearchInput;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.api.research.ResearchScope;
import com.fish_dan_.data_energistics.gui.ldlib2.ae.resource.AeKeyAmountRow;
import com.fish_dan_.data_energistics.gui.ldlib2.research.ResearchGraphNode.Presentation;
import com.fish_dan_.data_energistics.gui.ldlib2.research.ResearchGraphNode.Progress;
import com.fish_dan_.data_energistics.gui.ldlib2.style.SharedBackpackStyles;
import com.fish_dan_.data_energistics.menu.research.ResearchStationMenu;
import com.fish_dan_.data_energistics.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.research.persistence.ResearchNbt;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;
import com.lowdragmc.lowdraglib2.math.Size;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import dev.vfyjxf.taffy.style.FlexDirection;
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

import java.math.BigInteger;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

/** Both-side slot tree; all research and team requests remain server-authoritative menu actions. */
public final class ResearchStationUi {

    private static final ResourceLocation ITEM_ROOT = ResourceLocation.fromNamespaceAndPath("data_energistics", "research_item_root");
    private final ResearchStationMenu menu;
    private final ResearchGraphSelection selection;
    private final UIElement root = new UIElement();
    private final UIElement research = new UIElement();
    private final UIElement graphPane = new UIElement();
    private final UIElement projectPane = new UIElement();
    private final ScrollerView controls = ResearchUiParts.scroll(0, 0, 1, 1);
    private final UIElement researchControls = new UIElement();
    private final ResearchPrerequisitePath prerequisitePath = new ResearchPrerequisitePath();
    private final ResearchGraphView graph = new ResearchGraphView(prerequisitePath);
    private final ScrollerView projects = ResearchUiParts.scroll(0, 0, 1, 1);
    private final Label title = ResearchUiParts.label(ResearchUiParts.text("title"));
    private final UIElement backpack = new UIElement();
    private final Label backpackHeader = ResearchUiParts.label(ResearchUiParts.text("backpack"));
    private final InventorySlots backpackInventory = new InventorySlots();
    private final Label empty = ResearchUiParts.label(ResearchUiParts.text("catalog_empty"));
    private final Label projectTitle = ResearchUiParts.label(ResearchUiParts.text("projects", 0));
    private final ResearchTeamPanel teams;
    private final ItemSlot targetSlot;
    private final ResearchGraphFrame graphFrame;
    private final Button teamTab;
    private final Button close;
    private final Button scopeToggle;
    private final Button pageToggle;
    private final Button filterButton;
    private final Button zoomOut;
    private final Button zoomIn;
    private final Button reset;
    private final Button backpackButton;
    private final Object2ObjectMap<ResourceLocation, ResearchNode> projectNodes = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<ResourceLocation, CompoundTag> projectData = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<ResourceLocation, ResearchGraphNode> graphNodes = new Object2ObjectLinkedOpenHashMap<>();
    private Object2IntOpenHashMap<ResourceLocation> graphRanks = new Object2IntOpenHashMap<>();
    private ObjectSet<ResourceLocation> unlocked = new ObjectOpenHashSet<>();
    private ObjectList<ResourceLocation> visibleIds = new ObjectArrayList<>();
    private ItemStack previousTarget = ItemStack.EMPTY;
    private long revision = -1;
    private long graphDefinitionRevision = -1;
    private ResearchScope scope;
    private Filter filter = Filter.ALL;
    private boolean showTeams;
    private boolean backpackOpen;
    private boolean rebuild = true;
    private ResearchStationLayout layout = ResearchStationLayout.forViewport(800, 480);
    private Page page = Page.GRAPH;
    private boolean backpackPositioned;
    private boolean backpackDragged;
    private @Nullable ResourceLocation selected;
    private @Nullable ResourceLocation hovered;

    private ResearchStationUi(ResearchStationMenu menu) {
        this.menu = menu;
        this.selection = ResearchGraphSelection.forMenu(menu);
        this.scope = selection.scope();
        this.selected = selection.selected();
        this.teams = new ResearchTeamPanel(menu);
        root.setId("research_station_root");
        root.style(style -> style.backgroundTexture(GuiTextureGroup.of(
                SpriteTexture.of(ResourceLocation.fromNamespaceAndPath("data_energistics", "textures/gui/digital_transformation_research_station.png")),
                new ColorRectTexture(0xC0DDE2F1))));
        root.addChild(research);
        title.textStyle(style -> style.adaptiveHeight(false).textWrap(TextWrap.HOVER_ROLL).fontSize(11));
        root.addChild(title);
        teamTab = ResearchUiParts.button(ResearchUiParts.text("team_tab"), this::toggleTeams);
        teamTab.addEventListener(UIEvents.TICK, event -> teamTab.text.setText(ResearchUiParts.text(showTeams ? "research_tab" : "team_tab")));
        close = ResearchUiParts.button(Component.literal("×"), () -> menu.getPlayer().closeContainer());
        scopeToggle = ResearchUiParts.button(ResearchUiParts.text(scope == ResearchScope.TECHNOLOGY ? "technology" : "item_upgrade"),
                () -> setScope(scope == ResearchScope.TECHNOLOGY ? ResearchScope.ITEM_UPGRADE : ResearchScope.TECHNOLOGY));
        scopeToggle.addEventListener(UIEvents.TICK, event -> scopeToggle.text.setText(
                ResearchUiParts.text(scope == ResearchScope.TECHNOLOGY ? "technology" : "item_upgrade")));
        filterButton = ResearchUiParts.button(ResearchUiParts.text("filter_all"), () -> {
            filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
            rebuild = true;
        });
        filterButton.addEventListener(UIEvents.TICK, event -> filterButton.text.setText(
                ResearchUiParts.text("filter_" + filter.name().toLowerCase(Locale.ROOT))));
        pageToggle = ResearchUiParts.button(ResearchUiParts.text(page.key), () -> {
            page = page == Page.GRAPH ? Page.PROJECTS : Page.GRAPH;
            updatePages();
        });
        zoomOut = ResearchUiParts.button(Component.literal("−"), () -> graph.graphView.setScale(graph.graphView.getScale() - 0.15F));
        zoomIn = ResearchUiParts.button(Component.literal("+"), () -> graph.graphView.setScale(graph.graphView.getScale() + 0.15F));
        reset = ResearchUiParts.button(ResearchUiParts.text("reset"), graph::fitGraphChildren);
        targetSlot = new ItemSlot(menu.targetSlot());
        targetSlot.setId("research_station_target");
        SharedBackpackStyles.standalone(targetSlot);
        targetSlot.style(style -> style.tooltips(ResearchUiParts.text("target_empty")));
        graphFrame = new ResearchGraphFrame(graph, targetSlot, empty);
        graphPane.addChild(graphFrame);
        projectPane.style(style -> style.backgroundTexture(ResearchUiParts.PANEL));
        projectPane.addChildren(projectTitle, projects);
        research.addChildren(graphPane, projectPane);
        backpack.style(style -> style.zIndex(1000));
        backpack.setId("research_station_backpack");
        SharedBackpackStyles.window(backpack, backpackHeader);
        backpackHeader.setAllowHitTest(true);
        backpack.addChild(backpackHeader);
        backpackInventory.setId("research_station_player_inventory");
        SharedBackpackStyles.inventory(backpackInventory);
        backpack.addChild(backpackInventory);
        WindowDragHelper.setDragMove(backpackHeader, backpack, event -> event.button == 0, event -> backpackDragged = true);
        root.addChild(teams);
        ResearchUiParts.show(teams, false);
        root.addChild(backpack);
        ResearchUiParts.show(backpack, false);
        backpackButton = ResearchUiParts.button(ResearchUiParts.text("backpack"), () -> {
            backpackOpen = !backpackOpen;
            ResearchUiParts.show(backpack, backpackOpen);
        });
        buildControls();
        root.addEventListener(UIEvents.TICK, event -> {
            if (!menu.isServerSide()) refresh();
        });
        resize(Size.of(layout.width(), layout.height()));
    }

    private void buildControls() {
        controls.setId("research_station_controls");
        controls.style(style -> style.zIndex(10));
        controls.viewPort.style(style -> style.backgroundTexture(ResearchUiParts.roundedFrame(
                ResearchUiParts.NODE_BACKGROUND, ResearchUiParts.NODE_BORDER)));
        controls.viewContainer(view -> view.layout(layout -> layout.paddingAll(6).gapAll(6)));
        UIElement shortcuts = new UIElement();
        shortcuts.layout(layout -> layout.widthPercent(100).height(22).flexDirection(FlexDirection.ROW).gapAll(4).flexShrink(0));
        backpackButton.layout(layout -> layout.flex(1).width(0).minWidth(0));
        close.layout(layout -> layout.width(22).flexShrink(0));
        shortcuts.addChildren(backpackButton, close);
        researchControls.layout(layout -> layout.widthPercent(100).heightAuto().flexDirection(FlexDirection.COLUMN)
                .gapAll(6).flexShrink(0));
        researchControls.addChildren(scopeToggle, filterButton, pageToggle);
        UIElement zoom = new UIElement();
        zoom.layout(layout -> layout.widthPercent(100).height(22).flexDirection(FlexDirection.ROW).gapAll(4).flexShrink(0));
        zoomOut.layout(layout -> layout.flex(1).width(0).minWidth(0));
        zoomIn.layout(layout -> layout.flex(1).width(0).minWidth(0));
        zoom.addChildren(zoomOut, zoomIn);
        researchControls.addChildren(zoom, reset);
        controls.addScrollViewChild(shortcuts);
        controls.addScrollViewChild(teamTab);
        controls.addScrollViewChild(researchControls);
        root.addChild(controls);
    }

    /** Mount native LDLib2 slots once on both sides; this station has no AE menu or AE screen contract. */
    public static ModularUI mount(ResearchStationMenu menu) {
        IModularUIHolderMenu holder = (IModularUIHolderMenu) (Object) menu;
        if (holder.getModularUI() != null || !menu.slots.isEmpty()) {
            throw new IllegalStateException("Research station requires an unmounted native LDLib2 menu");
        }
        ResearchStationUi station = new ResearchStationUi(menu);
        ObjectList<Stylesheet> styles = new ObjectArrayList<>();
        styles.add(SharedBackpackStyles.stylesheet());
        ModularUI ui = ModularUI.of(UI.of(station.root, styles, station::resize), menu.getPlayer());
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

    /** Repositions the existing tree only; the graph, view transform, selected node and slots retain identity. */
    private Size resize(Size viewport) {
        layout = ResearchStationLayout.forViewport(viewport.getWidth(), viewport.getHeight());
        int width = layout.width();
        int height = layout.height();
        int margin = layout.margin();
        int workspaceWidth = layout.workspaceWidth();
        root.layout(style -> style.width(width).height(height));
        ResearchUiParts.at(research, 0, 0, width, height);
        ResearchUiParts.at(title, margin, margin, workspaceWidth, 22);
        ResearchUiParts.at(controls, layout.controlsLeft(), margin, layout.controlsWidth(), Math.max(1, height - margin * 2));
        int projectHeight = layout.bodyHeight();
        ResearchUiParts.at(projectPane, layout.sidebarLeft(), layout.bodyTop(), layout.sidebarWidth(), projectHeight);
        ResearchUiParts.at(projectTitle, 5, 4, Math.max(1, layout.sidebarWidth() - 10), 16);
        ResearchUiParts.at(projects, 0, 23, layout.sidebarWidth(), Math.max(1, projectHeight - 23));
        teams.resize(margin, layout.bodyTop(), workspaceWidth, layout.bodyHeight());
        ResearchBackpackLayout backpackLayout = ResearchBackpackLayout.fixed();
        int backpackLeft = backpackDragged && backpackPositioned ? Math.round(backpack.getLayoutX()) : (width - backpackLayout.width()) / 2;
        int backpackTop = backpackDragged && backpackPositioned ? Math.round(backpack.getLayoutY()) : (height - backpackLayout.height()) / 2;
        ResearchUiParts.at(backpack, Math.clamp(backpackLeft, 0, Math.max(0, width - backpackLayout.width())),
                Math.clamp(backpackTop, 0, Math.max(0, height - backpackLayout.height())), backpackLayout.width(), backpackLayout.height());
        ResearchUiParts.at(backpackHeader, backpackLayout.padding(), backpackLayout.padding(),
                backpackLayout.inventoryWidth(), backpackLayout.headerHeight());
        backpackHeader.textStyle(style -> style.fontSize(12).adaptiveHeight(false));
        ResearchUiParts.at(backpackInventory, backpackLayout.padding(), backpackLayout.inventoryTop(),
                backpackLayout.inventoryWidth(), backpackLayout.inventoryHeight());
        backpackInventory.apply(slot -> slot.layout(style -> style.width(backpackLayout.slotSize()).height(backpackLayout.slotSize())
                .paddingAll(backpackLayout.slotSize() / 18F).flexShrink(0)));
        for (InventorySlots.Row row : backpackInventory.rows) {
            row.layout(style -> style.width(backpackLayout.inventoryWidth()).height(backpackLayout.slotSize()).gapAll(0).flexShrink(0));
        }
        backpackInventory.hotbar.layout(style -> style.width(backpackLayout.inventoryWidth()).height(backpackLayout.slotSize())
                .marginTop(backpackLayout.hotbarGap()).gapAll(0).flexShrink(0));
        backpackPositioned = true;
        updatePages();
        return Size.of(width, height);
    }

    private void updatePages() {
        ResearchUiParts.show(research, !showTeams);
        research.setAllowHitTest(false);
        ResearchUiParts.show(researchControls, !showTeams);
        ResearchUiParts.show(teams, showTeams);
        boolean projectsOpen = page == Page.PROJECTS;
        ResearchUiParts.show(graphPane, layout.wide() || !projectsOpen);
        ResearchUiParts.show(projectPane, projectsOpen);
        int graphWidth = projectsOpen && layout.wide() ? layout.graphWidth() : layout.workspaceWidth();
        ResearchUiParts.at(graphPane, layout.margin(), layout.bodyTop(), graphWidth, layout.bodyHeight());
        ResearchUiParts.at(graphFrame, 0, 0, graphWidth, layout.bodyHeight());
        pageToggle.text.setText(ResearchUiParts.text(page.key));
    }

    private ItemStack target() {
        return menu.targetSlot().getItem();
    }

    private void setScope(ResearchScope value) {
        scope = value;
        selection.scope(value);
        selected = selection.selected();
        hovered = null;
        rebuild = true;
    }

    private void toggleTeams() {
        showTeams = !showTeams;
        updatePages();
    }

    private void refresh() {
        boolean targetChanged = !ItemStack.matches(previousTarget, target());
        boolean snapshotChanged = revision != menu.viewRevision();
        if (!targetChanged && !snapshotChanged && !rebuild) return;
        if (targetChanged) {
            if (selected == null && !target().isEmpty() && (previousTarget.isEmpty() || !ItemStack.isSameItem(previousTarget, target()))) {
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
        refreshExpandedNodes();
        refreshProjects();
        targetSlot.style(style -> style.tooltips(ResearchUiParts.text(
                menu.targetLocked() ? "target_locked" : target().isEmpty() ? "target_empty" : "target_ready")));
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
        if (filter == Filter.ALL) {
            ObjectSet<ResourceLocation> shownIds = new ObjectOpenHashSet<>();
            shown.forEach(node -> shownIds.add(node.id()));
            for (ResearchNode node : projectNodes.values()) {
                if (node.scope() == scope && shownIds.add(node.id())) shown.add(node);
            }
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
            boolean completed = unlocked.contains(node.id());
            Component state = completed ? ResearchUiParts.text("unlocked") : pending != null ?
                    projectStatus(node, pending) : ResearchUiParts.text(available(node) ? "available" : "unavailable");
            Progress progress = completed ? Progress.COMPLETED :
                    pending != null && pending.getBoolean("running") ? Progress.RUNNING : Progress.IDLE;
            graphNodes.get(node.id()).state(state, progress);
        }
        ResearchUiParts.show(empty, scope == ResearchScope.TECHNOLOGY && shown.isEmpty());
        empty.setText(ResearchUiParts.text(menu.nodes().isEmpty() ? "catalog_empty" : "filter_empty"));
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
        graphRanks = depth;
        if (scope == ResearchScope.ITEM_UPGRADE && !target().isEmpty()) {
            ResearchGraphNode item = new ResearchGraphNode(ITEM_ROOT, target().getHoverName(), target().copy(),
                    this::select, this::hover, this::positionNodes);
            item.state(ResearchUiParts.text("initial_item"), Progress.IDLE);
            model.models().add(item);
            graphNodes.put(ITEM_ROOT, item);
        }
        for (ResearchNode node : nodes) {
            ResearchGraphNode view = new ResearchGraphNode(node.id(), Component.translatable(node.name()),
                    new ItemStack(BuiltInRegistries.ITEM.get(node.icon())), this::select, this::hover, this::positionNodes);
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
        refreshExpandedNodes();
        positionNodes();
        graph.loadGraph(model);
        if (selected != null && graphNodes.containsKey(selected)) graph.addSelected(graphNodes.get(selected));
        graph.focus(selected, hovered);
    }

    private void select(ResourceLocation id) {
        selected = id.equals(selected) ? null : id;
        selection.select(selected);
        graph.clearAllSelected();
        if (selected != null && graphNodes.containsKey(selected)) graph.addSelected(graphNodes.get(selected));
        graph.focus(selected, hovered);
        refreshExpandedNodes();
        page = Page.GRAPH;
        updatePages();
    }

    private void hover(ResourceLocation id, boolean inside) {
        if (!inside && !id.equals(hovered)) return;
        ResourceLocation next = inside ? id : null;
        if (Objects.equals(hovered, next)) return;
        hovered = next;
        graph.focus(selected, hovered);
        refreshExpandedNodes();
    }

    private void refreshExpandedNodes() {
        prerequisitePath.update(selected != null && graphNodes.containsKey(selected) ? selected : null,
                menu.nodes(), projectNodes, unlocked);
        boolean changed = false;
        for (ResearchGraphNode node : graphNodes.values()) {
            Presentation presentation = node.researchId().equals(selected) ? Presentation.DETAILS :
                    node.researchId().equals(hovered) ? Presentation.COSTS : Presentation.COLLAPSED;
            changed |= node.presentation() != presentation;
            node.present(presentation);
            if (presentation != Presentation.COLLAPSED) node.content((costs, details) -> populateNode(costs, details, node.researchId(), presentation));
        }
        if (changed) positionNodes();
    }

    /**
     * Expand column/row spacing around hovered and selected nodes without reloading the graph or resetting its view.
     */
    private void positionNodes() {
        Int2IntOpenHashMap widths = new Int2IntOpenHashMap();
        Int2IntOpenHashMap rows = new Int2IntOpenHashMap();
        int maxRank = 0;
        for (ResearchGraphNode node : graphNodes.values()) {
            int rank = graphRanks.getInt(node.researchId());
            widths.put(rank, Math.max(widths.get(rank), node.layoutWidth()));
            maxRank = Math.max(maxRank, rank);
        }
        Int2IntOpenHashMap columns = new Int2IntOpenHashMap();
        int left = 0;
        for (int rank = 0; rank <= maxRank; rank++) {
            columns.put(rank, left);
            left += widths.get(rank) + 44;
        }
        for (ResearchGraphNode node : graphNodes.values()) {
            int rank = graphRanks.getInt(node.researchId());
            int top = rows.get(rank);
            node.setPosition(new Vector2f(columns.get(rank), top));
            rows.put(rank, top + node.layoutHeight() + 26);
        }
    }

    private void populateNode(UIElement costs, UIElement details, ResourceLocation nodeId, Presentation presentation) {
        if (ITEM_ROOT.equals(nodeId)) {
            costs.addChild(ResearchUiParts.label(ResearchUiParts.text("materials_none")));
            if (presentation == Presentation.DETAILS) {
                details.addChild(ResearchUiParts.label(ResearchUiParts.text("initial_description")));
            }
            return;
        }
        ResearchNode node = lookup(projectNodes, nodeId);
        if (node == null) node = lookup(menu.nodes(), nodeId);
        if (node == null) {
            costs.addChild(ResearchUiParts.label(ResearchUiParts.text("select_node")));
            return;
        }
        ResearchNode displayed = node;
        if (presentation == Presentation.DETAILS) {
            details.addChild(ResearchUiParts.label(Component.translatable(node.description())));
        }
        costs.addChild(ResearchUiParts.label(ResearchUiParts.text("time", node.researchTicks(), format(node.researchTicks() / 20.0))));
        costs.addChild(ResearchUiParts.label(ResearchUiParts.text("energy", format(node.aePerTick()), format(node.aePerTick() * node.researchTicks()))));
        costs.addChild(ResearchUiParts.label(ResearchUiParts.text("materials")));
        if (node.inputs().isEmpty()) {
            costs.addChild(ResearchUiParts.label(ResearchUiParts.text("materials_none")));
        } else {
            UIElement materials = new UIElement();
            materials.layout(layout -> layout.widthPercent(100).heightAuto().flexDirection(FlexDirection.COLUMN)
                    .gapAll(0).flexShrink(0));
            for (ResearchInput input : node.inputs()) {
                AeKeyAmountRow row = new AeKeyAmountRow();
                row.setFontSize(9);
                row.setEntry(input.key(), BigInteger.valueOf(input.amount()));
                materials.addChild(row);
            }
            costs.addChild(materials);
        }
        if (presentation == Presentation.COSTS) return;
        if (!projectData.containsKey(node.id()) && !unlocked.contains(node.id())) {
            Button request = ResearchUiParts.graphButton(ResearchUiParts.text("request_materials"),
                    () -> menu.request("request_materials", displayed.id().toString()));
            request.setActive(stationOperable() && available(node) && canTarget(node));
            details.addChild(request);
        }
        for (ResourceLocation id : node.prerequisites()) {
            ResearchNode prerequisite = lookup(menu.nodes(), id);
            details.addChild(ResearchUiParts.label(ResearchUiParts.text(unlocked.contains(id) ? "prerequisite_met" : "prerequisite_missing",
                    prerequisite == null ? Component.literal(id.toString()) : Component.translatable(prerequisite.name()))));
        }
        CompoundTag pending = lookup(projectData, node.id());
        if (unlocked.contains(node.id())) {
            details.addChild(ResearchUiParts.label(ResearchUiParts.text("unlocked")));
        } else if (pending != null) {
            details.addChild(ResearchUiParts.label(projectStatus(node, pending)));
            if (pending.getBoolean("running")) {
                details.addChild(ResearchUiParts.graphButton(ResearchUiParts.text("cancel"), () -> menu.request("cancel", displayed.id().toString())));
            } else {
                var resume = ResearchUiParts.graphButton(ResearchUiParts.text("resume"), () -> menu.request("resume", displayed.id().toString()));
                resume.setActive(stationOperable() && runningCount() < 2 && !pending.getBoolean("faulted"));
                details.addChild(resume);
            }
        } else {
            var start = ResearchUiParts.graphButton(ResearchUiParts.text("start"), () -> menu.request("start", displayed.id().toString()));
            start.setActive(stationOperable() && available(node) && runningCount() < 2 && canTarget(node));
            details.addChild(start);
        }
    }

    private boolean canTarget(ResearchNode node) {
        return node.scope() == ResearchScope.TECHNOLOGY || (!target().isEmpty() && node.target().matches(target()));
    }

    private boolean stationOperable() {
        CompoundTag snapshot = menu.snapshot();
        return snapshot.getBoolean("online") && !snapshot.getBoolean("faulted") && !snapshot.getBoolean("refund_pending");
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
            projects.addScrollViewChild(ResearchUiParts.button(Component.translatable(node.name()), () -> {
                if (scope != node.scope() || filter != Filter.ALL || !graphNodes.containsKey(id)) {
                    scope = node.scope();
                    filter = Filter.ALL;
                    rebuild = true;
                    refreshGraph();
                }
                select(id);
            }));
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

    private enum Page {

        GRAPH("research_tab"),
        PROJECTS("projects_tab");

        private final String key;

        Page(String key) {
            this.key = key;
        }
    }
}

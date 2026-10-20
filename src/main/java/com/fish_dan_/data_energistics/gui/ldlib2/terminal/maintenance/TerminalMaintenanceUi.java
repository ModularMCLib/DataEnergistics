package com.fish_dan_.data_energistics.gui.ldlib2.terminal.maintenance;

import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalResourceCell;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalUiLayout;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.text.TerminalSearchField;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.text.TerminalTooltipLines;
import com.fish_dan_.data_energistics.util.amount.AmountExpressionParser;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.InventoryAction;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Scrollable stock rules composed from LDLib2 elements and the player's terminal stylesheet.
 */
@NullMarked
public final class TerminalMaintenanceUi {

    private static final int ROW_HEIGHT = 22;
    private static final int VIEW_TOP = 46;
    private static final int VIEW_HEIGHT = 90;
    private static final int VIEW_WIDTH = 210;
    private static final int[] COLUMN_DIVIDERS = { 14, 35, 80, 125, 170, 192 };
    private final UIElement content = new UIElement();
    private final UIElement frame = new UIElement();
    private final UIElement columns = new UIElement();
    private final UIElement viewport = new UIElement();
    private final UIElement addRow = new UIElement();
    private final Scroller.Vertical scrollbar = TerminalUiLayout.verticalScrollbar(new Scroller.Vertical());
    private final TextField search = new TerminalSearchField();
    private final Button addButton = TerminalUiLayout.icon("maintenance_add", "terminal-maintenance-add", "icon-maintenance-add", 0, 0, 14, 14);
    private final HolderLookup.Provider registries;
    private final BiConsumer<String, CompoundTag> request;
    private final ObjectList<Row> rows = new ObjectArrayList<>();
    private final ObjectList<Row> visibleRows = new ObjectArrayList<>();
    private CompoundTag previous = new CompoundTag();
    private int selectedDelete = -1;
    private int maximumScroll;

    public TerminalMaintenanceUi(UIElement root, HolderLookup.Provider registries, BiConsumer<String, CompoundTag> request) {
        this.registries = registries;
        this.request = request;
        frame.addClass("terminal-maintenance-frame");
        frame.setAllowHitTest(false);
        columns.addClass("terminal-maintenance-columns");
        columns.setAllowHitTest(false);
        viewport.setOverflowVisible(false);
        viewport.addChild(content);
        search.addClass("terminal-field");
        search.addClass("terminal-search-field");
        search.addClass("terminal-maintenance-search");
        search.setText("", false);
        search.textFieldStyle(style -> style.placeholder(TerminalSearchField.placeholder(text("search"))));
        search.setTextResponder(value -> applyFilter(true));
        search.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 1) {
                search.setText("");
                search.focus();
                event.stopPropagation();
            }
        });
        viewport.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (maximumScroll > 0)
                scrollbar.setNormalizedValue(scrollbar.getNormalizedValue() + (event.deltaY > 0 ? -1F : 1F) * ROW_HEIGHT / maximumScroll);
            event.stopPropagation();
        });
        scrollbar.addClass("terminal-maintenance-scroll");
        scrollbar.setRange(0, 1);
        scrollbar.scrollContainer.addChildAt(TerminalUiLayout.scrollbarRail("maintenance_rail"), 0);
        scrollbar.setOnValueChanged(value -> content.layout(layout -> layout.top(-Math.round(value * maximumScroll))));
        addRow.addClass("terminal-maintenance-row");
        addRow.addClass("terminal-maintenance-add-row");
        TerminalUiLayout.at(addButton, 98, 4, 14, 14);
        TerminalUiLayout.at(addRow, 0, 0, VIEW_WIDTH, ROW_HEIGHT);
        addRow.addChild(addButton);
        TerminalUiLayout.at(content, 0, 0, VIEW_WIDTH, ROW_HEIGHT);
        content.addChild(addRow);
        addButton.style(style -> style.tooltips(TerminalTooltipLines.split(text("maintenance_add"))));
        addButton.setOnClick(event -> {
            if (event.button == 0) request.accept("maintenance_add", new CompoundTag());
        });
        columns.addChildren(header("maintenance_col_status", 1, 13), header("maintenance_col_resource", 15, 18),
                header("maintenance_col_lower", 37, 43), header("maintenance_col_upper", 82, 43),
                header("maintenance_col_batch", 127, 43), header("maintenance_col_enabled", 171, 21),
                header("maintenance_col_delete", 193, 17));
        TerminalUiLayout.at(frame, 5, 45, 220, 92);
        TerminalUiLayout.at(columns, 5, 34, 220, 11);
        TerminalUiLayout.at(search, 6, 18, 216, 14);
        TerminalUiLayout.at(viewport, 6, VIEW_TOP, VIEW_WIDTH, VIEW_HEIGHT);
        TerminalUiLayout.at(scrollbar, 216, VIEW_TOP, 6, VIEW_HEIGHT);
        root.addChildren(frame, columns, search, viewport, scrollbar);
    }

    public void refresh(CompoundTag value, int deleteSelection) {
        ListTag values = value.getList("entries", Tag.TAG_COMPOUND);
        if (previous.equals(value) && selectedDelete == deleteSelection) return;
        previous = value.copy();
        selectedDelete = deleteSelection;
        if (rows.size() != values.size()) rebuild(values.size());

        for (int index = 0; index < rows.size(); index++) {
            CompoundTag entry = values.getCompound(index);
            Row row = rows.get(index);
            AEKey key = entry.contains("resource", Tag.TAG_COMPOUND) ? AEKey.fromTagGeneric(registries, entry.getCompound("resource")) : null;
            row.key = key;
            row.resource.entry(key == null ? null : new GenericStack(key, 1), false);
            row.resource.setActive(true);
            String status = entry.getString("status");
            if (!STATUS_IDS.contains(status)) status = "free";
            String statusId = status;
            row.status.removeClass("status-free");
            row.status.removeClass("status-idle");
            row.status.removeClass("status-lacking");
            row.status.removeClass("status-no_cpu");
            row.status.removeClass("status-synthesizing");
            row.status.removeClass("status-exporting");
            row.status.addClass("status-" + status);
            row.status.style(style -> style.tooltips(TerminalTooltipLines.split(text("maintenance_status." + statusId),
                    text("maintenance_status_desc." + statusId))));
            set(row.lower, entry.getLong("lower"), key);
            set(row.upper, entry.getLong("upper"), key);
            set(row.batch, entry.getLong("batch"), key);
            validateThresholds(row);
            boolean enabled = entry.getBoolean("enabled");
            if (enabled) row.enabled.addClass("maintenance-enabled");
            else row.enabled.removeClass("maintenance-enabled");
            row.enabled.style(style -> style.tooltips(TerminalTooltipLines.split(text(enabled ? "maintenance_enabled" : "maintenance_disabled"))));
            row.remove.setActive(true);
            if (index == selectedDelete) row.remove.addClass("selected");
            else row.remove.removeClass("selected");
            String deleteTooltip = index == selectedDelete ? "maintenance_delete_confirm" : "maintenance_delete";
            row.remove.style(style -> style.tooltips(TerminalTooltipLines.split(text(deleteTooltip))));
        }
        applyFilter(false);
    }

    public ObjectList<TerminalResourceCell> resourceCells() {
        ObjectList<TerminalResourceCell> cells = new ObjectArrayList<>(visibleRows.size());
        for (Row row : visibleRows) cells.add(row.resource);
        return cells;
    }

    private void rebuild(int size) {
        content.clearAllChildren();
        rows.clear();
        visibleRows.clear();
        for (int index = 0; index < size; index++) {
            Row row = new Row(index);
            TerminalUiLayout.at(row.root, 0, index * ROW_HEIGHT, VIEW_WIDTH, ROW_HEIGHT);
            rows.add(row);
            content.addChild(row.root);
        }
        content.addChild(addRow);
    }

    private void applyFilter(boolean resetScroll) {
        visibleRows.clear();
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        for (Row row : rows) {
            boolean visible = matches(row.key, query);
            row.root.setVisible(visible);
            if (visible) visibleRows.add(row);
        }
        int contentHeight = (visibleRows.size() + 1) * ROW_HEIGHT;
        for (int index = 0; index < visibleRows.size(); index++)
            TerminalUiLayout.at(visibleRows.get(index).root, 0, index * ROW_HEIGHT, VIEW_WIDTH, ROW_HEIGHT);
        TerminalUiLayout.at(addRow, 0, visibleRows.size() * ROW_HEIGHT, VIEW_WIDTH, ROW_HEIGHT);
        maximumScroll = Math.max(0, contentHeight - VIEW_HEIGHT);
        float value = maximumScroll == 0 || resetScroll ? 0 : scrollbar.getNormalizedValue();
        TerminalUiLayout.at(content, 0, -Math.round(value * maximumScroll), VIEW_WIDTH, contentHeight);
        scrollbar.setDisplay(true);
        scrollbar.setActive(maximumScroll > 0);
        if (maximumScroll == 0) scrollbar.addClass("inactive-scroll");
        else scrollbar.removeClass("inactive-scroll");
        scrollbar.setScrollBarSize(Math.min(100, 1500F / VIEW_HEIGHT));
        scrollbar.scrollerStyle(style -> style.scrollDelta(maximumScroll == 0 ? 1F : (float) ROW_HEIGHT / maximumScroll));
        scrollbar.setNormalizedValue(value, false);
    }

    private static boolean matches(@Nullable AEKey key, String query) {
        if (query.isEmpty()) return true;
        if (key == null) return false;
        return key.getDisplayName().getString().toLowerCase(Locale.ROOT).contains(query) ||
                key.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    private void set(TextField field, long amount, @Nullable AEKey key) {
        if (!field.isFocused()) field.setText(format(amount, key == null ? 1 : key.getAmountPerUnit()), false);
        validate(field, key);
    }

    private void validate(TextField field, @Nullable AEKey key) {
        long unit = key == null ? 1 : key.getAmountPerUnit();
        var parsed = AmountExpressionParser.parseStockAmount(field.getValue(), unit);
        boolean valid = parsed.isPresent() && (!field.getId().endsWith("_batch") || parsed.orElse(0) > 0);
        field.textFieldStyle(style -> style.textColor(valid ? 0xFFFFFFFF : 0xFFCC3030));
        field.style(style -> style.tooltips(TerminalTooltipLines.split(valid ? Component.empty() : text("maintenance_invalid_amount"))));
    }

    private void validateThresholds(Row row) {
        long unit = row.key == null ? 1 : row.key.getAmountPerUnit();
        var lower = AmountExpressionParser.parseStockAmount(row.lower.getValue(), unit);
        var upper = AmountExpressionParser.parseStockAmount(row.upper.getValue(), unit);
        boolean valid = lower.isPresent() && upper.isPresent() && lower.getAsLong() <= upper.getAsLong();
        if (!row.lower.isFocused()) row.lower.textFieldStyle(style -> style.textColor(valid ? 0xFFFFFFFF : 0xFFCC3030));
        if (!row.upper.isFocused()) row.upper.textFieldStyle(style -> style.textColor(valid ? 0xFFFFFFFF : 0xFFCC3030));
    }

    private void commit(int index, String id, TextField field, @Nullable AEKey key) {
        var parsed = AmountExpressionParser.parseStockAmount(field.getValue(), key == null ? 1 : key.getAmountPerUnit());
        if (parsed.isEmpty() || id.equals("batch") && parsed.getAsLong() <= 0) return;
        if (id.equals("lower") && parsed.getAsLong() > currentAmount(rows.get(index).upper, key)) return;
        if (id.equals("upper") && parsed.getAsLong() < currentAmount(rows.get(index).lower, key)) return;
        CompoundTag data = new CompoundTag();
        data.putInt("index", index);
        data.putString("field", id);
        data.putLong("value", parsed.getAsLong());
        request.accept("maintenance_value", data);
    }

    private static long currentAmount(TextField field, @Nullable AEKey key) {
        return AmountExpressionParser.parseStockAmount(field.getValue(), key == null ? 1 : key.getAmountPerUnit()).orElse(0);
    }

    private static String format(long value, long unit) {
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(unit), MathContext.DECIMAL128).stripTrailingZeros().toPlainString();
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("gui.data_energistics.player_terminal." + key, arguments);
    }

    private static Label header(String key, int left, int width) {
        Label label = new Label();
        label.addClass("terminal-maintenance-header");
        label.setText(text(key));
        label.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false));
        label.setAllowHitTest(false);
        return TerminalUiLayout.at(label, left, 0, width, 11);
    }

    private static CompoundTag index(int index) {
        CompoundTag data = new CompoundTag();
        data.putInt("index", index);
        return data;
    }

    private final class Row {

        private final int index;
        private final UIElement root = TerminalUiLayout.at(new UIElement(), 0, 0, VIEW_WIDTH, ROW_HEIGHT);
        private final UIElement status = TerminalUiLayout.at(new UIElement(), 0, 5, 12, 12);
        private final TerminalResourceCell resource = TerminalUiLayout.at(new TerminalResourceCell(), 15, 2, 18, 18);
        private final TextField lower;
        private final TextField upper;
        private final TextField batch;
        private final Button enabled;
        private final Button remove;
        private @Nullable AEKey key;

        private Row(int index) {
            this.index = index;
            lower = amountField(index + "_lower", 37);
            upper = amountField(index + "_upper", 82);
            batch = amountField(index + "_batch", 127);
            enabled = TerminalUiLayout.icon(index + "_enabled", "terminal-maintenance-toggle", "icon-maintenance-enabled", 174, 4, 14, 14);
            remove = TerminalUiLayout.icon(index + "_delete", "terminal-maintenance-delete", "icon-maintenance-delete", 195, 4, 14, 14);
            root.addClass("terminal-maintenance-row");
            status.addClass("terminal-maintenance-status");
            status.addClass("status-free");
            UIElement glyph = TerminalUiLayout.at(new UIElement(), 0, 0, 12, 12);
            glyph.addClass("terminal-maintenance-status-icon");
            glyph.setAllowHitTest(false);
            status.addChild(glyph);
            resource.addClass("terminal-maintenance-key");
            resource.actionHint(text("maintenance_resource_hint"));
            resource.onResourceClick(event -> {
                CompoundTag data = index(index);
                data.putString("inventory", "maintenance");
                data.putInt("button", event.button);
                data.putString("interaction", (event.button == 0 ? InventoryAction.PICKUP_OR_SET_DOWN :
                        InventoryAction.SPLIT_OR_PLACE_SINGLE).name());
                request.accept("ghost", data);
            });
            enabled.style(style -> style.tooltips(TerminalTooltipLines.split(text("maintenance_disabled"))));
            enabled.setOnClick(event -> {
                if (event.button == 0) {
                    CompoundTag data = index(index);
                    data.putBoolean("value", !enabled.hasClass("maintenance-enabled"));
                    request.accept("maintenance_enabled", data);
                }
            });
            remove.setOnClick(event -> {
                if (event.button == 0) request.accept("maintenance_delete", index(index));
            });
            TerminalUiLayout.at(status, 0, 4, 12, 12);
            TerminalUiLayout.at(resource, 15, 1, 18, 18);
            TerminalUiLayout.at(enabled, 174, 3, 14, 14);
            TerminalUiLayout.at(remove, 195, 3, 14, 14);
            for (int boundary : COLUMN_DIVIDERS) {
                UIElement divider = new UIElement();
                divider.addClass("terminal-maintenance-divider");
                divider.setAllowHitTest(false);
                TerminalUiLayout.at(divider, boundary, 1, 1, ROW_HEIGHT - 2);
                root.addChild(divider);
            }
            root.addChildren(status, resource, lower, upper, batch, enabled, remove);
        }

        private TextField amountField(String id, int left) {
            TextField field = TerminalUiLayout.at(new TextField(), left, 4, 43, 14);
            field.setId(id);
            field.addClass("terminal-field");
            field.addClass("terminal-maintenance-amount");
            field.setText("0", false).setTextRegexValidator(".{0,256}");
            field.setTextResponder(value -> validate(field, key));
            String amountType = id.endsWith("_lower") ? "maintenance_lower" : id.endsWith("_upper") ? "maintenance_upper" : "maintenance_batch";
            field.style(style -> style.tooltips(TerminalTooltipLines.split(text(amountType))));
            field.addEventListener(UIEvents.KEY_DOWN, event -> {
                if (event.keyCode == 257 || event.keyCode == 335) {
                    commit(this.index, id.substring(id.lastIndexOf('_') + 1), field, key);
                    event.stopPropagation();
                }
            });
            return field;
        }
    }

    private static final ObjectList<String> STATUS_IDS = ObjectList.of("free", "idle", "lacking", "no_cpu", "synthesizing", "exporting");
}

package com.fish_dan_.data_energistics.gui.ldlib2.terminal.amount;

import com.fish_dan_.data_energistics.api.crafting.matching.ProcessingMatchMode;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalActionButton;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalOverlayUi;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalResourceCell;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalUiLayout;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.text.TerminalTooltipLines;
import com.fish_dan_.data_energistics.util.AmountMath;
import com.fish_dan_.data_energistics.util.amount.AmountExpressionParser;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.localization.GuiText;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.BiConsumer;

/** LDLib2 counterpart of AE2's processing amount sub-screen, including the mod's per-slot matching toolbar. */
@NullMarked
public final class TerminalAmountUi {

    private static final int[] STEP_LEFT = { 20, 48, 82, 120 };
    private static final int[] STEP_WIDTH = { 22, 28, 32, 38 };
    private static final int[] DECIMAL_STEPS = { 1, 10, 100, 1000 };
    private static final int[] STACK_STEPS = { 1, 16, 32, 64 };
    private final UIElement root;
    private final UIElement toolbar = new UIElement();
    private final RegistryAccess registries;
    private final BiConsumer<String, CompoundTag> request;
    private final Runnable close;
    private final TextField value = new TextField();
    private final Label unit = new Label();
    private final TerminalResourceCell resource = new TerminalResourceCell();
    private final TerminalActionButton confirm = amountButton();
    private final TerminalActionButton match = new TerminalActionButton();
    private final ObjectList<TerminalActionButton> steps = new ObjectArrayList<>();
    private @Nullable GenericStack selected;
    private String inventory = "";
    private int index;
    private boolean processing;
    private boolean stackSteps;
    private ProcessingMatchMode mode = ProcessingMatchMode.EXACT;

    public TerminalAmountUi(UIElement root, RegistryAccess registries, BiConsumer<String, CompoundTag> request, Runnable close) {
        this.root = root;
        this.registries = registries;
        this.request = request;
        this.close = close;
        root.addClass("terminal-amount-popup");
        root.setOverflowVisible(true);
        TerminalUiLayout.at(TerminalUiLayout.require(root, "popup_title", Label.class), 8, 6, 136, 10).setText(GuiText.SelectAmount.text());
        UIElement inputFrame = new UIElement();
        inputFrame.addClass("terminal-amount-input-frame");
        inputFrame.setAllowHitTest(false);
        root.addChild(TerminalUiLayout.at(inputFrame, 46, 53, 70, 16));
        value.addClass("terminal-field");
        value.setText("1", false).setTextRegexValidator(".{0,256}");
        value.setTextResponder(ignored -> validate());
        value.addEventListener(UIEvents.KEY_DOWN, event -> {
            if (event.keyCode == 257 || event.keyCode == 335) {
                submit();
                event.stopPropagation();
            }
        });
        value.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            adjust(event.deltaY > 0 ? 1 : -1);
            event.stopPropagation();
        });
        unit.addClass("terminal-label");
        unit.addClass("terminal-amount-unit");
        unit.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false));
        unit.setAllowHitTest(false);
        resource.setAllowHitTest(false);
        resource.addClass("terminal-amount-resource");
        confirm.setText(GuiText.Set.text());
        confirm.setOnClick(event -> {
            if (event.button == 0) submit();
        });
        for (int row = 0; row < 2; row++) for (int column = 0; column < 4; column++) {
            int step = column;
            int direction = row == 0 ? 1 : -1;
            TerminalActionButton button = amountButton();
            button.setOnClick(event -> {
                if (event.button == 0) adjust(direction * (stackSteps ? STACK_STEPS[step] : DECIMAL_STEPS[step]));
            });
            steps.add(button);
            root.addChild(TerminalUiLayout.at(button, STEP_LEFT[column], 30 + row * 42, STEP_WIDTH[column], 20));
        }
        match.addClass("terminal-control");
        match.noText();
        UIElement glyph = TerminalUiLayout.at(new UIElement(), 1, 1, 8, 8);
        glyph.addClass("terminal-match-icon");
        glyph.setAllowHitTest(false);
        match.addChild(glyph);
        match.glyph(glyph, 1);
        match.setOnClick(event -> {
            if (event.button != 0) return;
            mode = mode.next();
            refreshMatching();
            CompoundTag data = target();
            data.putInt("mode", mode.ordinal());
            data.put("key", selection().what().toTagGeneric(registries));
            request.accept("processing_match", data);
        });
        toolbar.addClass("terminal-amount-toolbar");
        toolbar.setAllowHitTest(false);
        toolbar.setVisible(false);
        toolbar.addChildren(toolbarSurface("terminal-amount-toolbar-top", 0, 15, 3),
                toolbarSurface("terminal-amount-toolbar-middle", 3, 15, 10),
                toolbarSurface("terminal-amount-toolbar-bottom", 13, 12, 3),
                TerminalUiLayout.at(match, 3, 3, 10, 10));
        root.addChildren(TerminalUiLayout.at(resource, 22, 52, 18, 18), TerminalUiLayout.at(value, 48, 55, 66, 12),
                TerminalUiLayout.at(unit, 102, 55, 12, 12), TerminalUiLayout.at(confirm, 120, 51, 38, 20),
                TerminalUiLayout.at(toolbar, -12, 0, 15, 16));
        root.addEventListener(UIEvents.TICK, event -> {
            boolean next = UIElement.isShiftDown() || UIElement.isCtrlDown();
            if (next != stackSteps) {
                stackSteps = next;
                stepLabels();
            }
        });
        stepLabels();
    }

    private static TerminalActionButton amountButton() {
        TerminalActionButton button = new TerminalActionButton();
        button.addClass("terminal-control");
        button.addClass("terminal-amount-step");
        button.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false));
        button.text.layout(layout -> layout.widthPercent(100).heightPercent(100).marginAll(0));
        button.glyph(button.text, 0);
        return button;
    }

    private static UIElement toolbarSurface(String style, int top, int width, int height) {
        UIElement surface = new UIElement();
        surface.addClass(style);
        surface.setAllowHitTest(false);
        return TerminalUiLayout.at(surface, 0, top, width, height);
    }

    public void edit(String inventory, int index, GenericStack stack, boolean processing, CompoundTag work) {
        this.inventory = inventory;
        this.index = index;
        this.selected = stack;
        this.processing = processing;
        resource.entry(stack, false);
        String symbol = stack.what().getUnitSymbol();
        unit.setText(Component.literal(symbol == null ? "" : symbol));
        int unitWidth = symbol == null ? 0 : Minecraft.getInstance().font.width(symbol);
        value.layout(layout -> layout.width(66 - (unitWidth == 0 ? 0 : unitWidth + 3)));
        TerminalUiLayout.at(unit, 114 - unitWidth, 55, unitWidth, 12);
        value.setText(displayAmount(stack.amount()), false);
        updateMatching(work);
        validate();
    }

    public void apply(CompoundTag snapshot) {
        if (!root.isVisible()) return;
        if (processing && !snapshot.getString("workspace").equals("processing")) {
            close.run();
            return;
        }
        CompoundTag source = processing ? snapshot.getCompound("work") : snapshot.getCompound("magnet");
        GenericStack current = GenericStack.readTag(registries, source.getList(processing ? "ghost_" + inventory : inventory, Tag.TAG_COMPOUND).getCompound(index));
        if (current == null || !current.what().equals(selection().what())) {
            close.run();
            return;
        }
        updateMatching(snapshot.getCompound("work"));
    }

    private void updateMatching(CompoundTag work) {
        boolean available = processing && selection().what() instanceof AEItemKey;
        toolbar.setVisible(available);
        if (!available) return;
        String modes = work.getCompound("matching").getString(inventory);
        mode = index < modes.length() ? ProcessingMatchMode.values()[modes.charAt(index) - '0'] : ProcessingMatchMode.EXACT;
        refreshMatching();
    }

    private void refreshMatching() {
        match.removeClass("match-id");
        match.removeClass("match-tag");
        if (mode == ProcessingMatchMode.ID) match.addClass("match-id");
        if (mode == ProcessingMatchMode.TAG) match.addClass("match-tag");
        match.style(style -> style.tooltips(TerminalTooltipLines.split(Component.translatable("gui.data_energistics.processing_match." + mode.name().toLowerCase(Locale.ROOT)),
                Component.translatable("gui.data_energistics.processing_match.hint"))));
    }

    private OptionalLong amount() {
        return AmountExpressionParser.parseStockAmount(value.getValue(), selection().what().getAmountPerUnit());
    }

    private void validate() {
        boolean valid = amount().isPresent();
        confirm.setActive(valid);
        value.textFieldStyle(style -> style.textColor(valid ? 0xFFFFFFFF : 0xFFCC3030));
        value.style(style -> style.tooltips(TerminalTooltipLines.split(valid ? Component.empty() : GuiText.InvalidNumber.text())));
    }

    private void adjust(int delta) {
        long unitSize = selection().what().getAmountPerUnit();
        long current = amount().orElse(0);
        long step = Math.abs((long) delta) * unitSize;
        long adjusted = delta > 0 ? AmountMath.addNonNegative(current, step) : Math.max(0, current - step);
        if (current == unitSize && delta > 1) adjusted -= unitSize;
        value.setText(displayAmount(adjusted), true);
    }

    private String displayAmount(long amount) {
        return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(selection().what().getAmountPerUnit()), MathContext.DECIMAL128)
                .stripTrailingZeros().toPlainString();
    }

    private void stepLabels() {
        for (int index = 0; index < steps.size(); index++)
            steps.get(index).setText(Component.literal((index < 4 ? "+" : "-") + (stackSteps ? STACK_STEPS[index % 4] : DECIMAL_STEPS[index % 4])));
    }

    private void submit() {
        OptionalLong amount = amount();
        if (amount.isEmpty()) return;
        CompoundTag data = target();
        data.putInt("button", 0);
        data.putLong("amount", amount.getAsLong());
        data.put("expected", selection().what().toTagGeneric(registries));
        data.put("stack", GenericStack.writeTag(registries, selection()));
        request.accept("ghost", data);
        close.run();
    }

    private CompoundTag target() {
        CompoundTag data = new CompoundTag();
        data.putString("inventory", inventory);
        data.putInt("index", index);
        return data;
    }

    private GenericStack selection() {
        return Objects.requireNonNull(selected, "Amount editing requires a selected resource");
    }

    public boolean contains(double x, double y) {
        return root.isVisible() && (TerminalOverlayUi.over(root, x, y) || toolbar.isVisible() && TerminalOverlayUi.over(toolbar, x, y));
    }

    public void extraRectangles(ObjectList<Rect2i> rectangles) {
        if (!root.isVisible()) return;
        if (toolbar.isVisible()) rectangles.add(rect(toolbar));
    }

    private static Rect2i rect(UIElement element) {
        return new Rect2i(Math.round(element.getPositionX()), Math.round(element.getPositionY()), Math.round(element.getSizeWidth()), Math.round(element.getSizeHeight()));
    }
}

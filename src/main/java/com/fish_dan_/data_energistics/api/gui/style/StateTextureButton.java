package com.fish_dan_.data_energistics.api.gui.style;

import com.fish_dan_.data_energistics.Data_Energistics;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.network.chat.Component;

/** Editor-instantiable overlay button using the shared normal, hover and pressed state textures. */
@LDLRegister(name = "data_energistics:state_texture_button", group = "data_energistics", registry = "ldlib2:ui_element")
public final class StateTextureButton extends Button {

    @Configurable
    private Arrow arrow = Arrow.UP;

    public StateTextureButton() {
        text.setText(Component.empty());
        layout(layout -> layout.width(10).height(10));
        setArrow(arrow);
    }

    @ConfigSetter(field = "arrow")
    public void setArrow(Arrow arrow) {
        this.arrow = arrow;
        String path = switch (arrow) {
            case UP -> "small_top_surface/small_arrow_up_1";
            case DOWN -> "small_top_surface/small_arrow_down_1";
            case LEFT, RIGHT -> "small_top_surface/small_arrow_up_0";
        };
        float rotation = switch (arrow) {
            case LEFT -> -90;
            case RIGHT -> 90;
            default -> 0;
        };
        IGuiTexture overlay = texture(path).rotate(rotation).transform(0, arrow == Arrow.DOWN ? 1 : 0);
        IGuiTexture normal = GuiTextureGroup.of(texture("top_surface/button/toolbar/normal"), overlay);
        IGuiTexture hover = GuiTextureGroup.of(texture("top_surface/button/toolbar/hovered"), overlay);
        IGuiTexture pressed = GuiTextureGroup.of(texture("top_surface/button/toolbar/pressed"), overlay);
        IGuiTexture disabled = GuiTextureGroup.of(texture("top_surface/button/toolbar/disabled"),
                texture(path).rotate(rotation).transform(0, arrow == Arrow.DOWN ? 1 : 0).setColor(0xFF696D88));
        buttonStyle(style -> style.baseTexture(isActive() ? normal : disabled)
                .hoverTexture(hover).pressedTexture(pressed));
    }

    /** Inactive controls retain a dimmed direction glyph on the shared disabled surface. */
    @Override
    public StateTextureButton setActive(boolean active) {
        if (isActive() != active) {
            super.setActive(active);
            setArrow(arrow);
        }
        return this;
    }

    private static SpriteTexture texture(String path) {
        return SpriteTexture.of(Data_Energistics.id("textures/guis/states/" + path + ".png"));
    }

    public enum Arrow {
        UP,
        DOWN,
        LEFT,
        RIGHT
    }
}

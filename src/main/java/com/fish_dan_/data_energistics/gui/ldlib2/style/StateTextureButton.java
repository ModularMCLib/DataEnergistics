package com.fish_dan_.data_energistics.gui.ldlib2.style;

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
        String path = "small_top_surface/small_arrow_" + (arrow == Arrow.UP ? "up" : "down");
        IGuiTexture normal = GuiTextureGroup.of(texture("small_top_surface/small_button_0"), texture(path + "_0"));
        IGuiTexture hover = GuiTextureGroup.of(texture("small_top_surface/small_button_1"), texture(path + "_0"));
        IGuiTexture pressed = GuiTextureGroup.of(texture("small_top_surface/small_button_2"), texture(path + "_1"));
        buttonStyle(style -> style.baseTexture(normal).hoverTexture(hover).pressedTexture(pressed));
    }

    private static IGuiTexture texture(String path) {
        return SpriteTexture.of(Data_Energistics.id("textures/guis/states/" + path + ".png"));
    }

    public enum Arrow {
        UP,
        DOWN
    }
}

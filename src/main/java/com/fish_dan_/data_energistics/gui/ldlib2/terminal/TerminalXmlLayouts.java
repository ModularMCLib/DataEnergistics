package com.fish_dan_.data_energistics.gui.ldlib2.terminal;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/** Declarative trees and one stylesheet define every fixed terminal control. */
@NullMarked
public final class TerminalXmlLayouts {

    private TerminalXmlLayouts() {}

    public static UI load(String name) {
        String path = "/assets/data_energistics/ui/terminal/" + name + ".xml";
        try (InputStream input = TerminalXmlLayouts.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing " + path);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);
            return UI.of(factory.newDocumentBuilder().parse(input));
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot load player terminal layout " + path, failure);
        }
    }

    public static <T extends UIElement> T require(UIElement root, String id, Class<T> type) {
        return root.selectId(id, type).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing terminal layout element " + id));
    }
}

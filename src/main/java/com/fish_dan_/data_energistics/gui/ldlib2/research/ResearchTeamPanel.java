package com.fish_dan_.data_energistics.gui.ldlib2.research;

import com.fish_dan_.data_energistics.menu.research.ResearchStationMenu;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** Custom-team membership and external-team effective subjects, rendered from the server snapshot. */
final class ResearchTeamPanel extends UIElement {

    private final ResearchStationMenu menu;
    private final TextField teamName = new TextField();
    private final ScrollerView membership = ResearchUiParts.scroll(0, 23, 205, 194);
    private final ScrollerView invitations = ResearchUiParts.scroll(211, 0, 205, 217);

    ResearchTeamPanel(ResearchStationMenu menu) {
        this.menu = menu;
        ResearchUiParts.at(this, 8, 29, 424, 218);
        style(style -> style.backgroundTexture(new ColorRectTexture(ResearchUiParts.SURFACE)));
        teamName.setAnyString();
        teamName.textFieldStyle(style -> style.placeholder(ResearchUiParts.text("team_name")).fontSize(9));
        addChild(ResearchUiParts.at(teamName, 3, 2, 140, 17));
        addChild(ResearchUiParts.at(ResearchUiParts.button(ResearchUiParts.text("team_create"),
                () -> request("create", teamName.getValue())), 147, 2, 58, 17));
        addChildren(membership, invitations);
    }

    void refresh(CompoundTag data) {
        membership.clearAllScrollViewChildren();
        invitations.clearAllScrollViewChildren();
        CompoundTag custom = data.getCompound("custom_team");
        boolean hasTeam = data.contains("custom_team", Tag.TAG_COMPOUND);
        boolean owner = hasTeam && menu.getPlayer().getUUID().toString().equals(custom.getString("owner"));
        teamName.setActive(!hasTeam);
        getChildren().get(1).setActive(!hasTeam);
        if (hasTeam) {
            membership.addScrollViewChild(ResearchUiParts.label(ResearchUiParts.text("team_current", custom.getString("name"))));
            membership.addScrollViewChild(ResearchUiParts.label(ResearchUiParts.text("team_shared")));
            for (Tag value : custom.getList("members", Tag.TAG_COMPOUND)) {
                CompoundTag member = (CompoundTag) value;
                String uuid = member.getString("uuid");
                String name = member.getString("name");
                membership.addScrollViewChild(ResearchUiParts.label(Component.literal(name)
                        .append(uuid.equals(custom.getString("owner")) ? ResearchUiParts.text("team_owner") : Component.empty())));
                if (owner && !uuid.equals(menu.getPlayer().getUUID().toString())) {
                    membership.addScrollViewChild(ResearchUiParts.button(ResearchUiParts.text("team_kick", name), () -> request("kick", uuid)));
                    membership.addScrollViewChild(ResearchUiParts.button(ResearchUiParts.text("team_transfer", name), () -> request("transfer", uuid)));
                }
            }
            membership.addScrollViewChild(ResearchUiParts.button(ResearchUiParts.text("team_leave"), () -> request("leave", "")));
            if (owner) membership.addScrollViewChild(ResearchUiParts.button(ResearchUiParts.text("team_disband"), () -> request("disband", "")));
        } else {
            membership.addScrollViewChild(ResearchUiParts.label(ResearchUiParts.text("team_none")));
        }
        membership.addScrollViewChild(ResearchUiParts.label(ResearchUiParts.text("team_external")));
        for (Tag value : data.getList("external_subjects", Tag.TAG_COMPOUND)) {
            CompoundTag subject = (CompoundTag) value;
            membership.addScrollViewChild(ResearchUiParts.label(ResearchUiParts.text("team_external_entry",
                    subject.getString("name"), subject.getString("source"))));
        }
        invitations.addScrollViewChild(ResearchUiParts.label(ResearchUiParts.text("team_invitations")));
        for (Tag value : data.getList("invitations", Tag.TAG_COMPOUND)) {
            CompoundTag invitation = (CompoundTag) value;
            String id = invitation.getString("id");
            invitations.addScrollViewChild(ResearchUiParts.label(Component.literal(invitation.getString("name"))));
            var accept = ResearchUiParts.button(ResearchUiParts.text("team_accept"), () -> request("accept", id));
            accept.setActive(!hasTeam);
            invitations.addScrollViewChild(accept);
            invitations.addScrollViewChild(ResearchUiParts.button(ResearchUiParts.text("team_reject"), () -> request("reject", id)));
        }
        invitations.addScrollViewChild(ResearchUiParts.label(ResearchUiParts.text("team_online")));
        for (Tag value : data.getList("online_players", Tag.TAG_COMPOUND)) {
            CompoundTag player = (CompoundTag) value;
            if (player.getString("uuid").equals(menu.getPlayer().getUUID().toString())) continue;
            var invite = ResearchUiParts.button(ResearchUiParts.text("team_invite", player.getString("name")),
                    () -> request("invite", player.getString("uuid")));
            invite.setActive(owner && !player.getBoolean("has_custom_team"));
            invitations.addScrollViewChild(invite);
        }
    }

    private void request(String verb, String argument) {
        menu.request("team_" + verb, argument);
    }
}

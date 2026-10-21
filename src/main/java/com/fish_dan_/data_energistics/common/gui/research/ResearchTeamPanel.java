package com.fish_dan_.data_energistics.common.gui.research;

import com.fish_dan_.data_energistics.common.menu.research.ResearchStationMenu;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** Custom-team membership and external-team effective subjects, rendered from the server snapshot. */
final class ResearchTeamPanel extends UIElement {

    private final ResearchStationMenu menu;
    private final TextField teamName = new TextField();
    private final ScrollerView membership = ResearchUiParts.scroll(0, 0, 1, 1);
    private final ScrollerView invitations = ResearchUiParts.scroll(0, 0, 1, 1);
    private final Button create;

    ResearchTeamPanel(ResearchStationMenu menu) {
        this.menu = menu;
        IGuiTexture panel = ResearchUiParts.roundedFrame(0xFFEFF1F8, 0xFF53617D);
        style(style -> style.backgroundTexture(panel).overflowVisible(false)
                .overflowClip(ResearchUiParts.rounded(0xFFFFFFFF)));
        roundScroll(membership, panel);
        roundScroll(invitations, panel);
        teamName.setAnyString();
        ResearchUiParts.input(teamName);
        teamName.style(style -> style.backgroundTexture(ResearchUiParts.roundedFrame(0xFFF7F9FF, 0xFF4B5979)));
        teamName.textFieldStyle(style -> style.placeholder(ResearchUiParts.text("team_name"))
                .focusOverlay(ResearchUiParts.roundedFrame(0, 0xFF1C7653)));
        create = ResearchUiParts.button(ResearchUiParts.text("team_create"), () -> request("create", teamName.getValue()));
        addChildren(teamName, create, membership, invitations);
    }

    private static void roundScroll(ScrollerView scroll, IGuiTexture panel) {
        scroll.style(style -> style.overflowVisible(false).overflowClip(ResearchUiParts.rounded(0xFFFFFFFF)));
        scroll.viewPort.style(style -> style.backgroundTexture(panel));
        scroll.verticalScroller.scrollContainer.style(style -> style.backgroundTexture(ResearchUiParts.rounded(ResearchUiParts.SURFACE)));
    }

    void resize(int left, int top, int width, int height) {
        ResearchUiParts.at(this, left, top, width, height);
        int contentWidth = Math.max(1, width - 8);
        int createWidth = Math.clamp(contentWidth / 4, 40, 80);
        ResearchUiParts.at(teamName, 4, 4, Math.max(1, contentWidth - createWidth - 4), 22);
        ResearchUiParts.at(create, Math.max(4, width - createWidth - 4), 4, createWidth, 22);
        int contentHeight = Math.max(1, height - 34);
        if (width >= 500) {
            int columnWidth = Math.max(1, (contentWidth - 6) / 2);
            ResearchUiParts.at(membership, 4, 30, columnWidth, contentHeight);
            ResearchUiParts.at(invitations, 10 + columnWidth, 30, columnWidth, contentHeight);
        } else {
            int membershipHeight = Math.max(1, (contentHeight - 6) / 2);
            ResearchUiParts.at(membership, 4, 30, contentWidth, membershipHeight);
            ResearchUiParts.at(invitations, 4, 36 + membershipHeight, contentWidth,
                    Math.max(1, contentHeight - membershipHeight - 6));
        }
    }

    void refresh(CompoundTag data) {
        membership.clearAllScrollViewChildren();
        invitations.clearAllScrollViewChildren();
        CompoundTag custom = data.getCompound("custom_team");
        boolean hasTeam = data.contains("custom_team", Tag.TAG_COMPOUND);
        boolean owner = hasTeam && menu.getPlayer().getUUID().toString().equals(custom.getString("owner"));
        teamName.setActive(!hasTeam);
        create.setActive(!hasTeam);
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

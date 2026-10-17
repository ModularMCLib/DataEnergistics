package com.fish_dan_.data_energistics.common.terminal.player.magnet;

import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalGhostInventory;

import appeng.api.stacks.AEKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Player-owned magnet policy; filter slots are configuration and never contain physical items.
 */
public final class TerminalMagnetSettings {

    public enum Mode {

        OFF(false, false),
        INVENTORY(true, false),
        NETWORK(true, true),
        PICKUP_NETWORK(false, true);

        private final boolean attract;
        private final boolean insert;

        Mode(boolean attract, boolean insert) {
            this.attract = attract;
            this.insert = insert;
        }

        public boolean attract() {
            return attract;
        }

        public boolean insert() {
            return insert;
        }
    }

    public final TerminalGhostInventory pickup;
    public final TerminalGhostInventory insert;
    private final Runnable changed;
    private Mode mode = Mode.OFF;
    private boolean restock;
    private boolean pickupWhitelist;
    private boolean insertWhitelist;

    public TerminalMagnetSettings(Runnable changed) {
        this.changed = changed;
        pickup = new TerminalGhostInventory(27, changed);
        insert = new TerminalGhostInventory(27, changed);
    }

    public Mode mode() {
        return mode;
    }

    public boolean restock() {
        return restock;
    }

    public boolean pickupWhitelist() {
        return pickupWhitelist;
    }

    public boolean insertWhitelist() {
        return insertWhitelist;
    }

    public boolean active() {
        return mode != Mode.OFF || restock;
    }

    public void mode(Mode value) {
        mode = value;
        changed.run();
    }

    public void restock(boolean value) {
        restock = value;
        changed.run();
    }

    public void pickupWhitelist(boolean value) {
        pickupWhitelist = value;
        changed.run();
    }

    public void insertWhitelist(boolean value) {
        insertWhitelist = value;
        changed.run();
    }

    public boolean pickupAllows(AEKey key) {
        return allows(pickup, pickupWhitelist, key);
    }

    public boolean insertAllows(AEKey key) {
        return allows(insert, insertWhitelist, key);
    }

    private static boolean allows(TerminalGhostInventory inventory, boolean whitelist, AEKey key) {
        boolean matched = false;
        for (int index = 0; index < inventory.size(); index++) {
            var entry = inventory.get(index);
            if (entry != null && entry.what().equals(key)) {
                matched = true;
                break;
            }
        }
        return matched == whitelist;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode.name());
        tag.putBoolean("restock", restock);
        tag.putBoolean("pickup_whitelist", pickupWhitelist);
        tag.putBoolean("insert_whitelist", insertWhitelist);
        tag.put("pickup", pickup.save(registries));
        tag.put("insert", insert.save(registries));
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        mode = Mode.valueOf(tag.getString("mode"));
        restock = tag.getBoolean("restock");
        pickupWhitelist = tag.getBoolean("pickup_whitelist");
        insertWhitelist = tag.getBoolean("insert_whitelist");
        pickup.load(tag.getList("pickup", Tag.TAG_COMPOUND), registries);
        insert.load(tag.getList("insert", Tag.TAG_COMPOUND), registries);
    }
}

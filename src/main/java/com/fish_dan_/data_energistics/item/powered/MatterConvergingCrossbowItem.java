package com.fish_dan_.data_energistics.item.powered;

import com.fish_dan_.data_energistics.entity.projectile.MatterConvergingBoltEntity;
import com.fish_dan_.data_energistics.entity.projectile.cannon.CannonShot;
import com.fish_dan_.data_energistics.entity.projectile.cannon.ElementalGrenade;
import com.fish_dan_.data_energistics.entity.projectile.cannon.GrenadePayload;
import com.fish_dan_.data_energistics.item.powered.cannon.CannonBallistics;
import com.fish_dan_.data_energistics.item.powered.cannon.CannonCharge;
import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.RailAmmunition;
import com.fish_dan_.data_energistics.item.powered.cannon.bow.BowShotPattern;
import com.fish_dan_.data_energistics.item.powered.cannon.presentation.DigitizedWeaponName;
import com.fish_dan_.data_energistics.item.powered.cannon.rail.RailLauncher;
import com.fish_dan_.data_energistics.item.powered.cannon.storage.CannonCellMenuHost;
import com.fish_dan_.data_energistics.item.powered.cannon.storage.MountedAmmoCells;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.DEMenus;
import com.fish_dan_.data_energistics.registry.DESounds;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableItem;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.Tooltips;
import appeng.helpers.IMouseWheelItem;
import appeng.items.misc.PaintBallItem;
import appeng.me.helpers.PlayerSource;
import appeng.menu.MenuOpener;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class MatterConvergingCrossbowItem extends Item implements IAEItemPowerStorage, IUpgradeableItem, IMenuItem, IMouseWheelItem {

    private static final double MAX_POWER = 200_000.0D;
    private static final double CHARGE_RATE = 200_000.0D;
    private static final double ENERGY_PER_SHOT = 200.0D;
    private static final double DATA_DUST_ENERGY_PER_SHOT = 200_000.0D;
    private static final double DATA_DUST_ENERGY_PER_PERCENT = 25_000.0D;
    private static final int DATA_DUST_BASE_PERCENT = 1;
    private static final int DATA_DUST_MAX_PERCENT = 10;
    private static final double HOMING_ENERGY_MULTIPLIER = 5.0D;
    private static final float PROJECTILE_SPEED = 3.15F;
    private static final float SPEED_CARD_PROJECTILE_SPEED_BONUS = 1.0F;
    private static final float RAIL_SPEED_CARD_PROJECTILE_SPEED_BONUS = 4.0F;
    private static final int CHARGE_DURATION_TICKS = 20;
    private static final int MAX_UPGRADES = 6;
    private static final int MAX_SPEED_UPGRADES = 4;

    public MatterConvergingCrossbowItem(Item.Properties properties) {
        super(properties.stacksTo(1));
    }

    public static MatterConvergingCrossbowMode mode(ItemStack stack) {
        return MatterConvergingCrossbowMode.fromId(stack.getOrDefault(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(), MatterConvergingCrossbowMode.GRENADE.id()));
    }

    public static boolean isCannon(ItemStack stack) {
        return stack.getItem() instanceof MatterConvergingCrossbowItem && mode(stack) != MatterConvergingCrossbowMode.CROSSBOW;
    }

    public static boolean isCharged(ItemStack stack) {
        return !stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).isEmpty();
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return mode(stack) == MatterConvergingCrossbowMode.CROSSBOW ? UseAnim.CROSSBOW : UseAnim.NONE;
    }

    @Override
    public boolean useOnRelease(ItemStack stack) {
        return mode(stack) == MatterConvergingCrossbowMode.CROSSBOW;
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 1;
    }

    @Override
    public ItemMenuHost<?> getMenuHost(Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hitResult) {
        return new CannonCellMenuHost(this, player, locator);
    }

    @Override
    public void onWheel(ItemStack stack, boolean up) {
        RailLauncher.cancel(stack);
        MountedAmmoCells.cycle(stack, mode(stack), !up);
    }

    /** Server entry for a left-button press. No ammo or energy is spent until release. */
    public void beginCannonCharge(Player player, InteractionHand hand, ItemStack stack) {
        if (mode(stack) == MatterConvergingCrossbowMode.RAIL) {
            RailLauncher.begin(player, hand, stack);
            return;
        }
        if (player.level().isClientSide || !isCannon(stack) || !player.isAlive() || player.isSpectator() || player.isUsingItem() || player.containerMenu != player.inventoryMenu || stack.has(DEDataComponents.CANNON_CHARGE.get())) return;
        if (!this.isChargedAmmoSupported(stack)) return;
        if (!isCharged(stack)) {
            ItemStack ammo = this.peekAmmo(stack);
            if (ammo.isEmpty()) {
                this.tryStoreAmmoFromPlayer(stack, player);
                ammo = this.peekAmmo(stack);
            }
            if (ammo.isEmpty() || this.getAECurrentPower(stack) < this.getEnergyPerShot(stack, ammo)) return;
        }
        stack.set(DEDataComponents.CANNON_CHARGE.get(), new CannonCharge(player.level().getGameTime(), getChargeDuration(stack, player),
                mode(stack), hand, player.getUUID(), player.level().dimension().location()));
    }

    /** Server release consumes the charge exactly once; packets contain no client-chosen charge/damage value. */
    public void releaseCannonCharge(Player player, InteractionHand hand, ItemStack stack, Vec3 muzzleOffset, Vec3 direction) {
        if (mode(stack) == MatterConvergingCrossbowMode.RAIL) {
            RailLauncher.release(player, hand, stack, muzzleOffset, direction);
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) return;
        CannonCharge charge = stack.remove(DEDataComponents.CANNON_CHARGE.get());
        if (charge == null || !charge.belongsTo(player, hand, mode(stack)) || player.isSpectator() || player.containerMenu != player.inventoryMenu || player.getItemInHand(hand) != stack || !CannonBallistics.validAim(muzzleOffset, direction, player.getViewVector(1.0F))) return;
        float fraction = charge.progress(level.getGameTime());
        if (fraction <= 0.0F || player.getCooldowns().isOnCooldown(this)) return;
        Vec3 muzzle = player.getEyePosition().add(muzzleOffset);
        if (level.clip(new ClipContext(player.getEyePosition(), muzzle, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) return;
        if (!this.isChargedAmmoSupported(stack) || !isCharged(stack) && !this.tryLoadProjectile(player, stack)) return;
        ChargedProjectiles loaded = this.takeChargedProjectiles(stack);
        if (loaded.isEmpty()) return;
        float projectileSpeed = this.getProjectileSpeed(stack);
        for (ItemStack ammo : loaded.getItems()) {
            CannonShot shot = new CannonShot(charge.mode(), fraction, projectileSpeed);
            MatterConvergingBoltEntity projectile = this.createProjectile(level, player, stack, ammo, false);
            projectile.configureCannonShot(shot);
            Vec3 velocity = CannonBallistics.launchVelocity(charge.mode(), fraction, direction, projectileSpeed);
            projectile.setPos(muzzle);
            projectile.shoot(velocity.x, velocity.y, velocity.z, (float) velocity.length(), 0.0F);
            projectile.setDeltaMovement(velocity);
            level.addFreshEntity(projectile);
        }
        stack.set(DEDataComponents.CANNON_SHOT_SEQUENCE.get(), stack.getOrDefault(DEDataComponents.CANNON_SHOT_SEQUENCE.get(), 0) + 1);
        player.getCooldowns().addCooldown(this, 3);
        player.awardStat(Stats.ITEM_USED.get(this));
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, shotSound(charge.mode()), SoundSource.PLAYERS, 1.0F,
                charge.mode() == MatterConvergingCrossbowMode.RAIL ? 1.4F : 0.7F);
    }

    private static SoundEvent shotSound(MatterConvergingCrossbowMode mode) {
        return switch (mode) {
            case RAIL -> DESounds.STAR_SHARD_RAIL_SHOT.get();
            case GRENADE -> DESounds.STAR_SHARD_GRENADE_SHOT.get();
            case CROSSBOW -> DESounds.STAR_SHARD_CROSSBOW_SHOT.get();
        };
    }

    /** Shared with the preview; the rail mode value is its damage-speed snapshot, not its hitscan flight speed. */
    public float cannonAmmoSpeed(ItemStack stack) {
        return this.getProjectileSpeed(stack);
    }

    public boolean cannonAmmoIgnoresWaterDrag(ItemStack stack) {
        ChargedProjectiles loaded = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        return this.isDataDustAmmo(loaded.isEmpty() ? this.peekAmmo(stack) : loaded.getItems().getFirst());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide) {
            CannonCharge charge = stack.get(DEDataComponents.CANNON_CHARGE.get());
            if (charge != null && (!(entity instanceof Player player) || !charge.belongsTo(player, charge.hand(), mode(stack)) || player.getItemInHand(charge.hand()) != stack || player.containerMenu != player.inventoryMenu)) {
                RailLauncher.cancel(stack);
            }
        }
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        if (this.isBlockedBowEnchantment(enchantment)) {
            return false;
        }
        return super.supportsEnchantment(stack, enchantment);
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        if (stack.is(Items.BOOK)) {
            return true;
        }

        Optional<HolderSet<Item>> primaryItems = enchantment.value().definition().primaryItems();
        return this.supportsEnchantment(stack, enchantment) && (primaryItems.isEmpty() || stack.is(primaryItems.get()));
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        ItemEnchantments storedEnchantments = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> enchantment : storedEnchantments.keySet()) {
            if (this.isBlockedBowEnchantment(enchantment)) {
                return false;
            }
        }
        return super.isBookEnchantable(stack, book);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                RailLauncher.cancel(stack);
                stack.remove(DEDataComponents.CANNON_CHARGE.get());
                MenuOpener.open(DEMenus.MATTER_CONVERGING_CROSSBOW_CONFIG.get(), player, MenuLocators.forHand(player, hand));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (isCannon(stack)) return InteractionResultHolder.pass(stack);
        ChargedProjectiles charged = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        if (!charged.isEmpty()) {
            if (!this.isChargedAmmoSupported(stack)) return InteractionResultHolder.fail(stack);
            this.performShooting(level, player, stack, 1.0F, null);
            return InteractionResultHolder.consume(stack);
        }

        ItemStack nextAmmo = this.peekAmmo(stack);
        if (nextAmmo.isEmpty()) {
            this.tryStoreAmmoFromPlayer(stack, player);
            nextAmmo = this.peekAmmo(stack);
        }

        if (!nextAmmo.isEmpty() && this.getAECurrentPower(stack) >= this.getEnergyPerShot(stack, nextAmmo)) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }

        return InteractionResultHolder.fail(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (isCannon(stack)) return;
        if (this.hasMaxSpeedCards(stack)) {
            return;
        }
        int usedTicks = this.getUseDuration(stack, entity) - timeLeft;
        float progress = getPowerForTime(usedTicks, stack, entity);
        if (progress >= 1.0F && !isCharged(stack)) this.tryLoadProjectile(entity, stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseTicks) {
        if (isCannon(stack)) return;
        if (!level.isClientSide) {
            float progress = (float) (stack.getUseDuration(livingEntity) - remainingUseTicks) / (float) getChargeDuration(stack, livingEntity);
            if (this.hasMaxSpeedCards(stack) && progress >= 1.0F && !isCharged(stack) && this.tryLoadProjectile(livingEntity, stack)) {
                this.performShooting(level, livingEntity, stack, 1.0F, null);
                livingEntity.stopUsingItem();
            }
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return getChargeDuration(stack, entity) + 3;
    }

    public static int getChargeDuration(ItemStack stack, LivingEntity entity) {
        float baseSeconds = CHARGE_DURATION_TICKS / 20.0F;
        float seconds = EnchantmentHelper.modifyCrossbowChargingTime(stack, entity, baseSeconds);
        return Math.max(1, Mth.floor(seconds * 20.0F));
    }

    private static float getPowerForTime(int usedTicks, ItemStack stack, LivingEntity entity) {
        float progress = (float) usedTicks / (float) getChargeDuration(stack, entity);
        return Math.min(progress, 1.0F);
    }

    @Override
    public Component getName(ItemStack stack) {
        return DigitizedWeaponName.fullName(mode(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines,
                                TooltipFlag tooltipFlag) {
        if (isCannon(stack)) {
            lines.add(Component.translatable("tooltip.data_energistics.cannon." + (mode(stack) == MatterConvergingCrossbowMode.GRENADE ? "grenade" : "rail"), Component.keybind("key.attack")));
        }
        lines.add(Tooltips.energyStorageComponent(this.getAECurrentPower(stack), this.getAEMaxPower(stack)));
        lines.add(Component.translatable("tooltip.data_energistics.cannon.cells", Component.keybind("key.ae2.mouse_wheel_item_modifier")));
        ItemStack cell = MountedAmmoCells.cell(stack, mode(stack));
        lines.add(Component.translatable("tooltip.data_energistics.cannon.cell", cell.isEmpty() ? Component.translatable("item.data_energistics.star_shard.projectile.none") : cell.getHoverName()));
        var ammoKey = MountedAmmoCells.selectedKey(stack, mode(stack));
        if (ammoKey != null) {
            lines.add(Component.translatable("tooltip.data_energistics.cannon.ammo_remaining",
                    MountedAmmoCells.amount(stack, mode(stack), ammoKey)));
            if (mode(stack) == MatterConvergingCrossbowMode.RAIL) {
                RailAmmunition railAmmo = RailAmmunition.fromKey(ammoKey);
                if (railAmmo != null) {
                    lines.add(Component.translatable("tooltip.data_energistics.cannon.rail_ammo." + railAmmo.name().toLowerCase(Locale.ROOT),
                            railAmmo.damage(this.getUpgrades(stack).getInstalledUpgrades(DEItems.CARD_SABER_ENERGY.get()))));
                }
            }
        }
        lines.add(Component.translatable("item.data_energistics.star_shard.projectile",
                this.getDisplayedAmmoName(stack)));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Mth.clamp((int) Math.round(this.getAECurrentPower(stack) / this.getAEMaxPower(stack) * 13.0D), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(1.0F / 3.0F, 1.0F, 1.0F);
    }

    public void performShooting(Level level, LivingEntity shooter, ItemStack stack,
                                float inaccuracy, @Nullable LivingEntity target) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!this.isChargedAmmoSupported(stack)) return;

        if (shooter instanceof Player player && EventHooks.onArrowLoose(stack, level, player, 1, true) < 0) {
            return;
        }

        ChargedProjectiles charged = this.takeChargedProjectiles(stack);
        if (charged.isEmpty()) return;

        stack.set(DEDataComponents.CANNON_SHOT_SEQUENCE.get(), stack.getOrDefault(DEDataComponents.CANNON_SHOT_SEQUENCE.get(), 0) + 1);

        float projectileSpeed = this.getProjectileSpeed(stack);
        float spread = EnchantmentHelper.processProjectileSpread(serverLevel, stack, shooter, 0);
        ObjectList<ItemStack> ammunition = new ObjectArrayList<>(charged.getItems());
        for (int index = 0; index < ammunition.size(); index++) {
            MatterConvergingBoltEntity projectile = this.createProjectile(level, shooter, stack, ammunition.get(index), shooter instanceof Player);
            var direction = BowShotPattern.direction(shooter, projectile, BowShotPattern.angle(index, ammunition.size(), spread), target);
            projectile.shoot(direction.x, direction.y, direction.z, projectileSpeed, inaccuracy);
            serverLevel.addFreshEntity(projectile);
            float pitch = index == 0 ? 1 : 1 / (shooter.getRandom().nextFloat() * 0.5F + 1.8F) + ((index & 1) == 1 ? 0.63F : 0.43F);
            level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), shotSound(mode(stack)), shooter.getSoundSource(), 1, pitch);
        }
        if (shooter instanceof Player player) {
            player.awardStat(Stats.ITEM_USED.get(this));
        }
    }

    private boolean tryLoadProjectile(LivingEntity shooter, ItemStack crossbow) {
        if (!(shooter instanceof Player player)) {
            return false;
        }
        ItemStack nextAmmo = this.peekAmmo(crossbow);
        double energyPerShot = this.getEnergyPerShot(crossbow, nextAmmo);
        if (this.getAECurrentPower(crossbow) < energyPerShot) {
            return false;
        }

        ObjectList<ItemStack> ammo = this.extractAmmoForLoading(crossbow, player);
        if (ammo.isEmpty()) {
            return false;
        }

        this.extractAEPower(crossbow, energyPerShot, Actionable.MODULATE);
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(ammo));
        return true;
    }

    protected MatterConvergingBoltEntity createProjectile(Level level, LivingEntity shooter, ItemStack weaponStack,
                                                          ItemStack ammoStack, boolean isCrit) {
        MatterConvergingBoltEntity projectile = new MatterConvergingBoltEntity(level, shooter, ammoStack.copyWithCount(1));
        projectile.setWeaponStack(weaponStack);
        projectile.setModernEffects(ammoStack.getOrDefault(DEDataComponents.CANNON_MODERN_AMMO.get(), false));
        projectile.setCritical(isCrit);
        projectile.setHoming(this.hasRedstoneCard(weaponStack));
        if (level instanceof ServerLevel serverLevel) {
            projectile.setPierceLevel(EnchantmentHelper.getPiercingCount(serverLevel, weaponStack, ammoStack));
        }
        return projectile;
    }

    private ItemStack extractAmmo(ItemStack weaponStack, Player player) {
        return MountedAmmoCells.extractOne(weaponStack, mode(weaponStack), new PlayerSource(player));
    }

    private boolean isBlockedBowEnchantment(Holder<Enchantment> enchantment) {
        return enchantment.is(Enchantments.FLAME) || enchantment.is(Enchantments.INFINITY);
    }

    private ChargedProjectiles takeChargedProjectiles(ItemStack stack) {
        ChargedProjectiles charged = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        stack.remove(DataComponents.CHARGED_PROJECTILES);
        return charged;
    }

    private ObjectList<ItemStack> extractAmmoForLoading(ItemStack weaponStack, Player player) {
        ItemStack ammo = this.extractAmmo(weaponStack, player);
        if (ammo.isEmpty()) {
            return ObjectLists.emptyList();
        }
        ammo.set(DEDataComponents.CANNON_MODERN_AMMO.get(), true);
        if (this.isDataDustAmmo(ammo)) {
            this.applyDataDustShotData(weaponStack, ammo);
            return ObjectLists.singleton(ammo);
        }

        int projectileCount = 1;
        if (player.level() instanceof ServerLevel serverLevel) {
            projectileCount = EnchantmentHelper.processProjectileCount(serverLevel, weaponStack, player, 1);
        }

        ObjectList<ItemStack> projectiles = new ObjectArrayList<>(Math.max(projectileCount, 1));
        projectiles.add(ammo);
        for (int i = 1; i < projectileCount; i++) {
            ItemStack duplicate = ammo.copyWithCount(1);
            duplicate.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
            projectiles.add(duplicate);
        }

        return projectiles;
    }

    private void tryStoreAmmoFromPlayer(ItemStack weaponStack, Player player) {
        if (MountedAmmoCells.cell(weaponStack, mode(weaponStack)).isEmpty()) return;

        var playerInventory = player.getInventory();
        for (int i = 0; i < playerInventory.getContainerSize(); i++) {
            ItemStack candidate = playerInventory.getItem(i);
            if (candidate.isEmpty()) {
                continue;
            }

            AEItemKey itemKey = AEItemKey.of(candidate);
            if (itemKey == null || !supportsAmmo(mode(weaponStack), candidate)) {
                continue;
            }

            long inserted = MountedAmmoCells.insert(weaponStack, mode(weaponStack), itemKey, 1,
                    new PlayerSource(player), Actionable.MODULATE);
            if (inserted > 0) {
                candidate.shrink((int) inserted);
                return;
            }
        }
    }

    private Component getDisplayedAmmoName(ItemStack weaponStack) {
        var key = MountedAmmoCells.selectedKey(weaponStack, mode(weaponStack));
        if (key != null) return key.getDisplayName();
        ItemStack ammo = this.peekAmmo(weaponStack);
        if (ammo.isEmpty()) {
            return Component.translatable("item.data_energistics.star_shard.projectile.none");
        }
        return ammo.getHoverName();
    }

    @Override
    public double injectAEPower(ItemStack stack, double amount, Actionable mode) {
        double maxStorage = this.getAEMaxPower(stack);
        double currentStorage = this.getAECurrentPower(stack);
        double required = Math.max(0, maxStorage - currentStorage);
        double overflow = Math.max(0.0D, Math.min(amount - required, amount));
        if (mode == Actionable.MODULATE) {
            double toAdd = Math.min(amount, required);
            this.setAECurrentPower(stack, currentStorage + toAdd);
        }
        return overflow;
    }

    @Override
    public double extractAEPower(ItemStack stack, double amount, Actionable mode) {
        double currentStorage = this.getAECurrentPower(stack);
        double fulfillable = Math.min(amount, currentStorage);
        if (mode == Actionable.MODULATE) {
            this.setAECurrentPower(stack, currentStorage - fulfillable);
        }
        return fulfillable;
    }

    @Override
    public double getAEMaxPower(ItemStack stack) {
        return MAX_POWER * getEnergyCapacityMultiplier(stack);
    }

    @Override
    public double getAECurrentPower(ItemStack stack) {
        return stack.getOrDefault(AEComponents.STORED_ENERGY, 0.0D);
    }

    private void setAECurrentPower(ItemStack stack, double power) {
        if (power < 1.0E-4D) {
            stack.remove(AEComponents.STORED_ENERGY);
        } else {
            stack.set(AEComponents.STORED_ENERGY, power);
        }
    }

    @Override
    public AccessRestriction getPowerFlow(ItemStack stack) {
        return AccessRestriction.WRITE;
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return CHARGE_RATE + CHARGE_RATE * Upgrades.getEnergyCardMultiplier(this.getUpgrades(stack));
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return UpgradeInventories.forItem(stack, MAX_UPGRADES, this::onUpgradesChanged);
    }

    private void onUpgradesChanged(ItemStack stack, IUpgradeInventory upgrades) {
        RailLauncher.cancel(stack);
        double maxPower = this.getAEMaxPower(stack);
        if (this.getAECurrentPower(stack) > maxPower) {
            this.setAECurrentPower(stack, maxPower);
        }
        this.refreshChargedDataDustShotData(stack);
    }

    private static int getEnergyCapacityMultiplier(ItemStack stack) {
        return 1 + Upgrades.getEnergyCardMultiplier(UpgradeInventories.forItem(stack, MAX_UPGRADES)) * 8;
    }

    private double getEnergyPerShot(ItemStack stack, ItemStack ammoStack) {
        if (this.isDataDustAmmo(ammoStack)) {
            return this.getDataDustEnergyPerShot(stack);
        }
        return mode(stack) == MatterConvergingCrossbowMode.CROSSBOW && this.hasRedstoneCard(stack) ? ENERGY_PER_SHOT * HOMING_ENERGY_MULTIPLIER : ENERGY_PER_SHOT;
    }

    private boolean hasRedstoneCard(ItemStack stack) {
        return this.getUpgrades(stack).getInstalledUpgrades(DEItems.REDSTONE_TUNING_CARD.get()) > 0;
    }

    private boolean hasMaxSpeedCards(ItemStack stack) {
        return this.getUpgrades(stack).getInstalledUpgrades(AEItems.SPEED_CARD) >= MAX_SPEED_UPGRADES;
    }

    private float getProjectileSpeed(ItemStack stack) {
        int speedCards = Math.max(0, this.getUpgrades(stack).getInstalledUpgrades(AEItems.SPEED_CARD));
        return switch (mode(stack)) {
            case RAIL -> PROJECTILE_SPEED + speedCards * RAIL_SPEED_CARD_PROJECTILE_SPEED_BONUS;
            case GRENADE, CROSSBOW -> PROJECTILE_SPEED * 1.5F + speedCards * SPEED_CARD_PROJECTILE_SPEED_BONUS;
        };
    }

    private ItemStack peekAmmo(ItemStack weaponStack) {
        return MountedAmmoCells.peek(weaponStack, mode(weaponStack));
    }

    private boolean isDataDustAmmo(ItemStack ammoStack) {
        return ammoStack.is(DEItems.DATA_RESIDUAL_CRYSTAL.get());
    }

    private boolean isChargedAmmoSupported(ItemStack weaponStack) {
        return weaponStack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).getItems()
                .stream().allMatch(ammo -> supportsAmmo(mode(weaponStack), ammo) || ammo.is(AEItems.MATTER_BALL.asItem()) || ammo.is(AEItems.SINGULARITY.asItem()) || ammo.getItem() instanceof PaintBallItem);
    }

    /** Ammo filtering applies to a mode's installed disk without modifying its other stored contents. */
    public static boolean supportsAmmo(MatterConvergingCrossbowMode mode, ItemStack ammoStack) {
        if (ammoStack.isEmpty()) {
            return false;
        }
        Item item = ammoStack.getItem();
        return switch (mode) {
            case GRENADE -> GrenadePayload.isExplosive(ammoStack) || ElementalGrenade.accepts(ammoStack);
            case RAIL -> ammoStack.is(Items.BLAZE_ROD) || ammoStack.is(Items.HEAVY_CORE);
            case CROSSBOW -> item == AEItems.MATTER_BALL.asItem() || item == AEItems.SINGULARITY.asItem() || item instanceof PaintBallItem || ammoStack.is(DEItems.SINGULARITY_BLOCK.get()) || ammoStack.is(DEItems.DATA_RESIDUAL_CRYSTAL.get());
        };
    }

    private double getDataDustEnergyPerShot(ItemStack stack) {
        return DATA_DUST_ENERGY_PER_SHOT + this.getDataDustExtraEnergyForShot(stack);
    }

    private int getDataDustDamagePercentForShot(ItemStack stack) {
        double extraPower = this.getDataDustExtraEnergyForShot(stack);
        int extraPercent = (int) Math.floor(extraPower / DATA_DUST_ENERGY_PER_PERCENT);
        return Math.min(DATA_DUST_MAX_PERCENT, DATA_DUST_BASE_PERCENT + extraPercent);
    }

    private void applyDataDustShotData(ItemStack weaponStack, ItemStack ammoStack) {
        float ratio = this.getDataDustDamagePercentForShot(weaponStack) / 100.0F;
        ammoStack.set(DEDataComponents.MATTER_CONVERGING_BOLT_DAMAGE_RATIO.get(), ratio);
    }

    private double getDataDustExtraEnergyFromCards(ItemStack stack) {
        return Math.max(0.0D, this.getAEMaxPower(stack) - MAX_POWER);
    }

    private double getDataDustExtraEnergyForShot(ItemStack stack) {
        double maxExtraEnergy = (DATA_DUST_MAX_PERCENT - DATA_DUST_BASE_PERCENT) * DATA_DUST_ENERGY_PER_PERCENT;
        return Math.min(this.getDataDustExtraEnergyFromCards(stack), maxExtraEnergy);
    }

    private void refreshChargedDataDustShotData(ItemStack stack) {
        ChargedProjectiles charged = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        if (charged.isEmpty()) {
            return;
        }

        ObjectList<ItemStack> updatedProjectiles = new ObjectArrayList<>(charged.getItems().size());
        boolean changed = false;
        for (ItemStack projectile : charged.getItems()) {
            ItemStack updatedProjectile = projectile.copy();
            if (this.isDataDustAmmo(updatedProjectile)) {
                this.applyDataDustShotData(stack, updatedProjectile);
                changed = true;
            }
            updatedProjectiles.add(updatedProjectile);
        }

        if (changed) {
            stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(updatedProjectiles));
        }
    }
}

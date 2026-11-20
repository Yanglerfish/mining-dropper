package net.ponti.miningdropper;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MiningDropper implements ClientModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("Mining Dropper");
    static DropConfig config;
    private KeyMapping toggle, settings;
    private ClientPacketListener session;
    private boolean enabled;
    private boolean toggleHeld;
    private int cooldown;
    private int nextSlot = InventoryMenu.INV_SLOT_START;
    private static final AutoCompact compactor = new AutoCompact();

    public static void afterMenuSync(int containerId, boolean full) {
        if (config != null) compactor.onMenuSync(Minecraft.getInstance(), containerId, full, config);
    }

    public static void afterRecipeGhost(int containerId, RecipeDisplay display) {
        if (config != null) compactor.onGhostRecipe(Minecraft.getInstance(), containerId, display, config);
    }

    @Override public void onInitializeClient() {
        try {
            config = new DropConfig(FabricLoader.getInstance().getConfigDir().resolve("miningdropper.properties"));
        } catch (IOException e) {
            LOG.error("Could not load Mining Dropper settings", e);
        }
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("miningdropper", "controls"));
        toggle = binding("toggle", InputConstants.KEY_LBRACKET, category);
        settings = binding("settings", InputConstants.KEY_RBRACKET, category);
        compactor.registerInputHandlers();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private static KeyMapping binding(String action, int key, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.miningdropper." + action,
                InputConstants.Type.KEYBOARD, key, category));
    }

    private void tick(Minecraft client) {
        var connection = client.getConnection();
        if (session != connection) {
            session = connection;
            enabled = false;
            cooldown = 0;
            nextSlot = InventoryMenu.INV_SLOT_START;
        }
        boolean openSettings = settings.consumeClick();
        boolean toggleRequested = false;
        while (toggle.consumeClick()) {
            if (!toggleHeld) toggleRequested = true;
        }
        toggleHeld = toggle.isDown();
        var player = client.player;
        compactor.tick(client, config);
        if (player == null || connection == null || client.gui.screen() != null
                || client.gui.overlay() != null || !client.isWindowActive()) return;
        if (openSettings) {
            client.gui.setScreen(new SettingsScreen(null));
            return;
        }
        if (toggleRequested) {
            if (config == null) {
                player.sendSystemMessage(Component.literal("Mining Dropper could not read its settings. Check the game log."));
                return;
            }
            enabled = !enabled;
            client.gui.hud.setOverlayMessage(Component.literal("Mining Dropper " + (enabled ? "ON" : "OFF")), false);
            cooldown = 0;
        }
        if (!enabled || client.isPaused() || client.gameMode == null || !player.isAlive()
                || player.isSpectator() || !player.canDropItems() || player.containerMenu != player.inventoryMenu
                || !player.inventoryMenu.getCarried().isEmpty()) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        cooldown = 4;
        int slot = findDropSlot(player, config, nextSlot);
        if (slot >= 0) {
            client.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot, 1, ContainerInput.THROW, player);
            nextSlot = slot + 1;
        }
    }

    static int findDropSlot(LocalPlayer player, DropConfig config, int first) {
        int start = InventoryMenu.INV_SLOT_START;
        int end = config.scansHotbar() ? InventoryMenu.USE_ROW_SLOT_END : InventoryMenu.INV_SLOT_END;
        int i = first >= start && first < end ? first : start;
        for (int checked = 0; checked < end - start; checked++) {
            Slot slot = player.inventoryMenu.getSlot(i);
            if (slot.container == player.getInventory() && slot.mayPickup(player) && shouldDrop(slot.getItem(), config)) {
                return i;
            }
            if (++i == end) i = start;
        }
        return -1;
    }

    static boolean shouldDrop(ItemStack stack, DropConfig config) {
        if (stack.isEmpty() || !stack.getComponentsPatch().isEmpty()) return false;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        var rule = DropRules.BY_ITEM.get(id.toString());
        return rule != null && config.drops(rule);
    }
}

package net.ponti.miningdropper;

import java.io.IOException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

final class SettingsScreen extends Screen {
    private final Screen parent;
    private DropRules.Group group = DropRules.Group.TERRAIN;
    private boolean compacting;
    private int page;
    private String error;

    SettingsScreen(Screen parent) {
        super(Component.literal("Mining Dropper"));
        this.parent = parent;
    }

    @Override protected void init() {
        int panel = Math.min(width - 12, 390);
        int left = (width - panel) / 2;
        int bottom = height - 26;
        int half = (panel - 4) / 2;
        button("Done", left + half + 4, bottom, half, b -> onClose());
        DropConfig config = MiningDropper.config;
        if (config == null) {
            error = "Could not read settings. Check the game log.";
            return;
        }
        int tab = (panel - 12) / 4;
        for (var section : DropRules.Group.values()) {
            var selector = button(section.title, left + section.ordinal() * (tab + 4), 26, tab, b -> {
                group = section;
                compacting = false;
                page = 0;
                rebuildWidgets();
            });
            selector.active = compacting || section != group;
        }
        button("Compacting", left + 3 * (tab + 4), 26, tab, b -> {
            compacting = true;
            page = 0;
            rebuildWidgets();
        }).active = !compacting;
        var rules = DropRules.ALL.stream().filter(rule -> rule.group() == group).toList();
        int count = compacting ? AutoCompact.RECIPES.size() : rules.size();
        int pagerY = bottom - 47;
        int rows = Math.max(1, (pagerY - 52) / 22);
        int perPage = rows * 2;
        int pages = (count + perPage - 1) / perPage;
        page = Math.min(page, pages - 1);
        int first = page * perPage;
        for (int i = 0; i < Math.min(perPage, count - first); i++) {
            int x = left + (i % 2) * (half + 4);
            int y = 52 + (i / 2) * 22;
            if (compacting) {
                addMaterialButton(AutoCompact.RECIPES.get(first + i), x, y, half);
                continue;
            }
            var rule = rules.get(first + i);
            var widget = button(ruleLabel(rule), x, y,
                    half, b -> {
                        save(rule.key(), !config.drops(rule));
                        b.setMessage(Component.literal(ruleLabel(rule)));
                    });
            widget.setTooltip(Tooltip.create(Component.literal(String.join(", ", rule.items()))));
        }
        if (pages > 1) {
            button("<", left, pagerY, 38, b -> { page--; rebuildWidgets(); }).active = page > 0;
            button(">", left + panel - 38, pagerY, 38, b -> { page++; rebuildWidgets(); }).active = page + 1 < pages;
            int currentPage = page + 1;
            addRenderableOnly((g, x, y, t) -> g.centeredText(font, currentPage + " / " + pages,
                    width / 2, pagerY + 6, 0xFFFFFFFF));
        }
        var compact = button(compactLabel(), left, bottom - 23, panel, b -> {
            save("auto_compact", !config.compacts());
            b.setMessage(Component.literal(compactLabel()));
        });
        compact.setTooltip(Tooltip.create(Component.literal("Pack kept mining materials when opening a crafting table.")));
        var hotbar = button(hotbarLabel(), left, bottom, half, b -> {
            save("scan_hotbar", !config.scansHotbar());
            b.setMessage(Component.literal(hotbarLabel()));
        });
        hotbar.setTooltip(Tooltip.create(Component.literal("Choose whether the nine hotbar slots are included.")));
    }

    private static String ruleLabel(DropRules.Rule rule) {
        return rule.label() + "  " + (MiningDropper.config.drops(rule) ? "DROP" : "KEEP");
    }

    private static String hotbarLabel() {
        return MiningDropper.config.scansHotbar() ? "Hotbar: scan" : "Hotbar: keep";
    }

    private static String compactLabel() { return "Auto-compact: " + (MiningDropper.config.compacts() ? "ON" : "OFF"); }
    private void addMaterialButton(AutoCompact.Recipe recipe, int x, int y, int width) {
        Item item = recipe.input();
        var name = Component.translatable(BuiltInRegistries.ITEM.getKey(item).toLanguageKey("item"));
        var widget = button(materialLabel(name, item), x, y, width, b -> {
            save(DropConfig.compactKey(item), !MiningDropper.config.compacts(item));
            b.setMessage(Component.literal(materialLabel(name, item)));
        });
        String hint = "Choose BLOCK to auto-craft kept stacks or LOOSE to leave them in item form.";
        if (item == Items.QUARTZ) hint = "Four quartz make one block. This recipe cannot be reversed.";
        if (recipe.makesIngots()) hint = "Nuggets are used only when the corresponding ingot is also set to BLOCK.";
        widget.setTooltip(Tooltip.create(Component.literal(hint)));
    }

    private static String materialLabel(Component name, Item item) {
        return name.getString() + "  " + (MiningDropper.config.compacts(item) ? "BLOCK" : "LOOSE");
    }

    private void save(String key, boolean value) {
        try {
            MiningDropper.config.set(key, value);
            error = null;
        } catch (IOException e) {
            error = "Could not save setting. Check the game log.";
            MiningDropper.LOG.error("Could not save Mining Dropper settings", e);
        }
    }

    private Button button(String label, int x, int y, int w, Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.literal(label), action).bounds(x, y, w, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, error == null ? getTitle() : Component.literal(error),
                width / 2, 9, error == null ? 0xFFFFFFFF : 0xFFFF7777);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}

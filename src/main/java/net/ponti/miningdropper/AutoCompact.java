package net.ponti.miningdropper;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

final class AutoCompact {
    record Recipe(Item input, Item output, int width) {
        Recipe(Item input, Item output) { this(input, output, 3); }
        int cost() { return width * width; }
        boolean makesIngots() {
            return output == Items.GOLD_INGOT || output == Items.IRON_INGOT || output == Items.COPPER_INGOT;
        }
        boolean enabled(DropConfig config) {
            return config.compacts(input) && (!makesIngots() || config.compacts(output));
        }
    }

    static final List<Recipe> RECIPES = List.of(
            new Recipe(Items.RAW_IRON, Items.RAW_IRON_BLOCK),
            new Recipe(Items.RAW_COPPER, Items.RAW_COPPER_BLOCK),
            new Recipe(Items.RAW_GOLD, Items.RAW_GOLD_BLOCK),
            new Recipe(Items.COAL, Items.COAL_BLOCK),
            new Recipe(Items.REDSTONE, Items.REDSTONE_BLOCK),
            new Recipe(Items.LAPIS_LAZULI, Items.LAPIS_BLOCK),
            new Recipe(Items.DIAMOND, Items.DIAMOND_BLOCK),
            new Recipe(Items.EMERALD, Items.EMERALD_BLOCK),
            new Recipe(Items.IRON_INGOT, Items.IRON_BLOCK),
            new Recipe(Items.COPPER_INGOT, Items.COPPER_BLOCK.weathering().unaffected()),
            new Recipe(Items.GOLD_INGOT, Items.GOLD_BLOCK),
            new Recipe(Items.NETHERITE_INGOT, Items.NETHERITE_BLOCK),
            new Recipe(Items.QUARTZ, Items.QUARTZ_BLOCK, 2),
            new Recipe(Items.GOLD_NUGGET, Items.GOLD_INGOT),
            new Recipe(Items.IRON_NUGGET, Items.IRON_INGOT),
            new Recipe(Items.COPPER_NUGGET, Items.COPPER_INGOT));

    private Screen seen;
    private AbstractCraftingMenu menu;
    private AbstractCraftingMenu syncedMenu;
    private List<Slot> inventory, sources, grid;
    private boolean done;
    private Batch batch;
    private int idle;
    private ClientPacketListener connection;
    private final Set<Item> manualRecipes = new HashSet<>();

    void registerInputHandlers() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (craftingMenu(screen) == null) return;
            ScreenMouseEvents.beforeMouseClick(screen).register((s, event) -> cancel(s));
            ScreenKeyboardEvents.beforeKeyPress(screen).register((s, event) -> {
                // A modifier on its own does not click a slot or edit a recipe.
                if (event.key() < InputConstants.KEY_LCONTROL || event.key() > InputConstants.KEY_RGUI) cancel(s);
            });
        });
    }

    private static AbstractCraftingMenu craftingMenu(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> container
                && container.getMenu() instanceof AbstractCraftingMenu crafting
                && (crafting instanceof CraftingMenu || screen instanceof InventoryScreen)) return crafting;
        return null;
    }

    private void cancel(Screen screen) {
        seen = screen;
        done = true;
        batch = null;
    }

    void onMenuSync(Minecraft client, int containerId, boolean full, DropConfig config) {
        var player = client.player;
        if (player == null || !(player.containerMenu instanceof AbstractCraftingMenu crafting)
                || crafting.containerId != containerId || craftingMenu(client.gui.screen()) != crafting) return;
        if (full) {
            syncedMenu = crafting;
            if (seen != client.gui.screen() || (!done && inventory == null)) {
                tick(client, config);
                return;
            }
        }
        if (done || menu != crafting || seen != client.gui.screen() || batch == null || batch.collecting
                || config == null || !config.compacts() || !player.isAlive() || player.isSpectator()
                || client.gameMode == null || !client.isWindowActive() || client.gui.overlay() != null
                || !menu.getCarried().isEmpty() || batch.nextCell() >= 0 || !correctResult()) return;
        if (batch.matchesGrid(grid)) collectFilled(client);
    }

    void onGhostRecipe(Minecraft client, int containerId, RecipeDisplay display, DropConfig config) {
        if (done || batch == null || !batch.bookPlacement || batch.collecting || menu.containerId != containerId
                || client.gui.screen() != seen || client.player == null || client.player.containerMenu != menu
                || !matchesDisplay(display, batch.recipe) || !menu.getCarried().isEmpty()
                || menu.getResultSlot().hasItem() || grid.stream().anyMatch(Slot::hasItem) || !ownedInputs()) return;
        manualRecipes.add(batch.recipe.input);
        Arrays.fill(batch.expected, 0);
        batch.bookPlacement = false;
        idle = 0;
        tick(client, config);
    }

    void tick(Minecraft client, DropConfig config) {
        if (client.getConnection() != connection) {
            connection = client.getConnection();
            manualRecipes.clear();
        }
        Screen screen = client.gui.screen();
        if (screen != seen) {
            seen = screen;
            menu = craftingMenu(screen);
            batch = null;
            inventory = sources = grid = null;
            done = menu == null;
            idle = 0;
        }
        if (client.player == null) syncedMenu = null;
        if (done || menu == null) return;
        var player = client.player;
        if (config == null || !config.compacts() || player == null || client.gameMode == null
                || !player.isAlive() || player.isSpectator() || player.containerMenu != menu) {
            cancel(screen);
            return;
        }
        if (!client.isWindowActive() || client.gui.overlay() != null) return;
        // Table inventories arrive after the open-screen packet.
        if (menu instanceof CraftingMenu && menu != syncedMenu && menu.getStateId() == 0 && inventory == null && ++idle < 20) return;
        if (batch != null && batch.collecting && menu.getCarried().isEmpty() && !menu.getResultSlot().hasItem()
                && batch.uniformCount(grid) == 0 && verifiedOutput(0)) batch = null;
        if (batch == null) {
            grid = menu.getInputGridSlots();
            if (!menu.getCarried().isEmpty() || menu.getResultSlot().hasItem() || grid.stream().anyMatch(Slot::hasItem)) {
                cancel(screen);
                return;
            }
            inventory = menu.slots.stream().filter(s -> s.container == player.getInventory()
                    && s.getContainerSlot() >= 0 && s.getContainerSlot() < 36).toList();
            sources = inventory.stream().filter(s -> config.scansHotbar() || s.getContainerSlot() >= 9).toList();
            batch = choose(config);
            if (batch == null) { cancel(screen); return; }
            idle = 0;
            batch.inputBefore = count(inventory, batch.recipe.input);
            var display = bookRecipe(client);
            if (display != null) {
                for (int i : batch.cells) batch.expected[i] = batch.size;
                batch.bookPlacement = true;
                client.gameMode.handlePlaceRecipe(menu.containerId, display, true);
                return;
            }
        }
        if (++idle > 120) {
            if (menu.getCarried().isEmpty()) {
                int remaining = batch.uniformCount(grid);
                if ((!batch.collecting && ownedInputs()) || (batch.collecting && remaining > 0
                        && remaining <= batch.lastTake && verifiedOutput(remaining))) returnIngredients(client);
            }
            String message = "Auto-compact paused while waiting for the crafting table to sync.";
            if (batch.bookPlacement) {
                manualRecipes.add(batch.recipe.input);
                message = "The table didn't confirm this recipe. Reopen it to retry with slot clicks.";
            }
            client.gui.hud.setOverlayMessage(Component.literal(message), false);
            cancel(screen);
            return;
        }
        if (!menu.getCarried().isEmpty()) {
            // A full inventory sync may replay the cursor from the middle of a placement.
            if (!batch.collecting && idle >= 20 && idle % 20 == 0
                    && plain(menu.getCarried(), batch.recipe.input) && ownedInputs()) {
                Slot target = depositSlot(sources, menu.getCarried());
                if (target != null) click(client, target.index, 0, ContainerInput.PICKUP);
            }
            return;
        }
        if (batch.collecting) {
            int remaining = batch.uniformCount(grid);
            if (remaining < 0 || remaining > batch.lastTake || !verifiedOutput(remaining)) return;
            if (remaining > 0 && remaining < batch.lastTake && correctResult()
                    && room(outputSlots(batch.recipe), batch.recipe.output) >= remaining) {
                batch.takeRetries = 0;
                take(client, remaining);
            } else if (remaining > 0 && idle >= 30 && remaining == batch.lastTake && correctResult()
                    && batch.takeRetries < 2 && room(outputSlots(batch.recipe), batch.recipe.output) >= remaining) {
                // The result click was rolled back. Only retry if both inputs and outputs agree.
                batch.takeRetries++;
                take(client, remaining);
            }
            return;
        }
        if (!batch.matchesGrid(grid)) {
            // Wait out partial packets. Reconcile only the cells sent by this batch,
            // and only if every unit of our input is accounted for.
            if (idle < (batch.bookPlacement ? 60 : 12) || batch.repairs >= 8 || !ownedInputs()) return;
            if (batch.bookPlacement && grid.stream().noneMatch(Slot::hasItem)) return;
            for (int i = 0; i < grid.size(); i++) batch.expected[i] = grid.get(i).getItem().getCount();
            batch.bookPlacement = false;
            batch.repairs++;
        }
        int cell = batch.nextCell();
        if (cell < 0) {
            // The client predicts the ingredients. Only the server calculates the result,
            // and a previous partial pattern can briefly show a completely different recipe.
            collectFilled(client);
            return;
        }
        placeGrid(client);
    }

    private void collectFilled(Minecraft client) {
        if (!correctResult() || !ownedInputs()) return;
        if (room(outputSlots(batch.recipe), batch.recipe.output) < batch.size) {
            returnIngredients(client);
            cancel(seen);
            return;
        }
        batch.outputBefore = count(inventory, batch.recipe.output);
        take(client, batch.size);
    }

    private boolean ownedInputs() {
        int total = count(inventory, batch.recipe.input);
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty() && !plain(carried, batch.recipe.input)) return false;
        total += carried.getCount();
        for (int i = 0; i < grid.size(); i++) {
            ItemStack stack = grid.get(i).getItem();
            if (stack.isEmpty()) continue;
            if (!plain(stack, batch.recipe.input) || stack.getCount() > batch.expected[i]) return false;
            total += stack.getCount();
        }
        return total == batch.inputBefore;
    }

    private boolean verifiedOutput(int remaining) {
        return count(inventory, batch.recipe.output) == batch.outputBefore + batch.size - remaining;
    }

    private static Slot depositSlot(List<Slot> slots, ItemStack stack) {
        for (Slot slot : slots) {
            if (slot.mayPlace(stack) && (slot.getItem().isEmpty() || ItemStack.isSameItemSameComponents(slot.getItem(), stack))
                    && slot.getMaxStackSize(stack) - slot.getItem().getCount() >= stack.getCount()) return slot;
        }
        return null;
    }

    private RecipeDisplayId bookRecipe(Minecraft client) {
        // The vanilla recipe-book button lets the server fill all nine cells in one operation.
        // Use manual placement when the server could draw from a protected stack, or the
        // planned batch is smaller than the one its "place maximum" action would make.
        var recipe = batch.recipe;
        if (manualRecipes.contains(recipe.input)
                || Math.min(batch.inputBefore / recipe.cost(), new ItemStack(recipe.input).getMaxStackSize()) != batch.size)
            return null;
        int stacks = 0;
        for (Slot s : inventory) {
            ItemStack stack = s.getItem();
            if (!stack.is(recipe.input)) continue;
            if (!plain(stack, recipe.input) || !sources.contains(s)) return null;
            stacks++;
        }
        // If there are fewer leftovers than input stacks, placement must free a slot.
        if (room(outputSlots(recipe), recipe.output) < batch.size
                && batch.inputBefore - batch.size * recipe.cost() >= stacks) return null;
        for (var collection : client.player.getRecipeBook().getCollections()) {
            for (var entry : collection.getRecipes()) {
                if (matchesDisplay(entry.display(), recipe)) return entry.id();
            }
        }
        return null;
    }

    private boolean matchesDisplay(RecipeDisplay display, Recipe recipe) {
        List<SlotDisplay> ingredients;
        if (display instanceof ShapedCraftingRecipeDisplay shaped
                && shaped.width() == recipe.width && shaped.height() == recipe.width) {
            ingredients = shaped.ingredients();
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless && recipe.cost() == grid.size()) {
            ingredients = shapeless.ingredients();
        } else return false;
        return ingredients.size() == recipe.cost() && singlePlainItem(display.result(), recipe.output)
                && ingredients.stream().allMatch(s -> singlePlainItem(s, recipe.input));
    }

    private static boolean singlePlainItem(SlotDisplay display, Item item) {
        if (display instanceof SlotDisplay.ItemSlotDisplay d) return d.item().value() == item;
        if (display instanceof SlotDisplay.TagSlotDisplay d) return d.tag().size() == 1 && d.tag().get(0).value() == item;
        if (display instanceof SlotDisplay.ItemStackSlotDisplay d)
            return d.stack().item().value() == item && d.stack().count() == 1 && plain(d.stack().create(), item);
        return display instanceof SlotDisplay.Composite d && !d.contents().isEmpty()
                && d.contents().stream().allMatch(part -> singlePlainItem(part, item));
    }

    private Batch choose(DropConfig config) {
        for (Recipe recipe : RECIPES) {
            if (recipe.width > menu.getGridWidth() || !recipe.enabled(config)) continue;
            if (MiningDropper.shouldDrop(new ItemStack(recipe.input), config)
                    || MiningDropper.shouldDrop(new ItemStack(recipe.output), config)) continue;
            int possible = count(sources, recipe.input) / recipe.cost();
            if (possible == 0) continue;
            if (recipe.makesIngots()) {
                int loose = count(sources, recipe.output);
                // Make intermediate ingots only when they complete a block.
                possible = (loose + possible) / 9 * 9 - loose;
            }
            int size = Math.min(possible, Math.min(new ItemStack(recipe.input).getMaxStackSize(),
                    new ItemStack(recipe.output).getMaxStackSize()));
            if (size <= 0) continue;
            int capacity = room(outputSlots(recipe), recipe.output);
            Slot first = smallest(sources, recipe.input);
            if (capacity < size && first.getItem().getCount() > size * recipe.cost()) size = capacity;
            if (size > 0) return new Batch(recipe, size, menu.getGridWidth(), grid.size());
        }
        return null;
    }

    private boolean correctResult() {
        ItemStack result = menu.getResultSlot().getItem();
        return plain(result, batch.recipe.output) && result.getCount() == 1;
    }

    private List<Slot> outputSlots(Recipe recipe) {
        return recipe.makesIngots() ? sources : inventory;
    }

    private void take(Minecraft client, int remaining) {
        batch.collecting = true;
        batch.bookPlacement = false;
        batch.lastTake = remaining;
        if (batch.recipe.makesIngots() && sources.size() < inventory.size()) {
            // Shift-click normally puts the ingots in the hotbar first. Deliver them to an
            // eligible slot so the next recipe can use them without touching protected stock.
            Slot target = null;
            for (Slot s : sources) {
                if (plain(s.getItem(), batch.recipe.output) && s.getItem().getCount() < s.getMaxStackSize(s.getItem())) {
                    target = s;
                    break;
                }
                if (target == null && !s.hasItem()) target = s;
            }
            if (target == null) return;
            click(client, menu.getResultSlot().index, 0, ContainerInput.PICKUP);
            if (plain(menu.getCarried(), batch.recipe.output)) click(client, target.index, 0, ContainerInput.PICKUP);
        } else {
            click(client, menu.getResultSlot().index, 0, ContainerInput.QUICK_MOVE);
        }
        idle = 0;
    }

    private void returnIngredients(Minecraft client) {
        for (Slot slot : grid) {
            if (slot.hasItem()) click(client, slot.index, 0, ContainerInput.QUICK_MOVE);
        }
    }

    /** The complete, count-limited batch is placed in one client turn. Each target slot
     * receives its entire outstanding share in a single pickup or distribution gesture. */
    private void placeGrid(Minecraft client) {
        int first;
        while ((first = batch.nextCell()) >= 0) {
            if (!menu.getCarried().isEmpty() || !batch.matchesGrid(grid) || !ownedInputs()) return;
            int need = batch.size - batch.expected[first];
            List<Integer> targets = new ArrayList<>(batch.cells.length);
            for (int i : batch.cells) if (batch.size - batch.expected[i] == need) targets.add(i);
            Transfer transfer = chooseTransfer(need, targets.size());
            if (transfer == null) {
                // Every remaining pile is smaller than one target. Pool two and plan again.
                if (!mergeInputPiles(client)) return;
            } else if (!placeGroup(client, transfer, targets, need)) return;
            idle = 0;
        }
        // A full inventory can leave its remainder split between several occupied slots.
        while (room(outputSlots(batch.recipe), batch.recipe.output) < batch.size) {
            if (!menu.getCarried().isEmpty() || !ownedInputs() || !mergeInputPiles(client)) return;
        }
    }

    private record Transfer(Slot source, int button, int take, int cells, int putBack, int clicks) {}

    private Transfer chooseTransfer(int need, int targets) {
        Transfer best = null;
        for (Slot slot : sources) {
            ItemStack stack = slot.getItem();
            if (!plain(stack, batch.recipe.input)) continue;
            for (int button = 0; button <= 1; button++) {
                int take = button == 0 ? stack.getCount() : (stack.getCount() + 1) / 2;
                int cells = Math.min(targets, take / need);
                if (cells == 0) continue;
                int excess = take - cells * need;
                // An even drag leaves up to cells-1 items on the cursor. A right drag
                // always gives exactly one per slot. A single left-click uses the lot.
                int putBack = need == 1 ? 0 : Math.max(0, excess - (cells - 1));
                int clicks = 1 + (cells == 1 ? 1 : cells + 2) + putBack + (excess > putBack ? 1 : 0);
                if (best == null || clicks * best.cells < best.clicks * cells
                        || (clicks * best.cells == best.clicks * cells && cells > best.cells)) {
                    best = new Transfer(slot, button, take, cells, putBack, clicks);
                }
            }
        }
        return best;
    }

    private boolean placeGroup(Minecraft client, Transfer plan, List<Integer> targets, int need) {
        click(client, plan.source.index, plan.button, ContainerInput.PICKUP);
        ItemStack carried = menu.getCarried();
        if (!plain(carried, batch.recipe.input) || carried.getCount() != plan.take) return false;
        // Trim the source in inventory. Grid cells are only touched with a final allocation.
        for (int i = 0; i < plan.putBack; i++) click(client, plan.source.index, 1, ContainerInput.PICKUP);
        if (menu.getCarried().getCount() != plan.take - plan.putBack) return false;
        if (plan.cells == 1) {
            click(client, grid.get(targets.getFirst()).index, need == 1 ? 1 : 0, ContainerInput.PICKUP);
        } else {
            int type = need == 1 ? AbstractContainerMenu.QUICKCRAFT_TYPE_GREEDY : AbstractContainerMenu.QUICKCRAFT_TYPE_CHARITABLE;
            drag(client, AbstractContainerMenu.SLOT_CLICKED_OUTSIDE, AbstractContainerMenu.QUICKCRAFT_HEADER_START, type);
            for (int i = 0; i < plan.cells; i++)
                drag(client, grid.get(targets.get(i)).index, AbstractContainerMenu.QUICKCRAFT_HEADER_CONTINUE, type);
            drag(client, AbstractContainerMenu.SLOT_CLICKED_OUTSIDE, AbstractContainerMenu.QUICKCRAFT_HEADER_END, type);
        }
        for (int i = 0; i < plan.cells; i++) batch.expected[targets.get(i)] = batch.size;
        if (plain(menu.getCarried(), batch.recipe.input)) click(client, plan.source.index, 0, ContainerInput.PICKUP);
        return menu.getCarried().isEmpty() && batch.matchesGrid(grid);
    }

    private boolean mergeInputPiles(Minecraft client) {
        Slot donor = smallest(sources, batch.recipe.input);
        if (donor == null) return false;
        Slot target = null;
        for (Slot slot : sources) {
            ItemStack stack = slot.getItem();
            if (slot != donor && plain(stack, batch.recipe.input) && stack.getCount() < slot.getMaxStackSize(stack)
                    && (target == null || stack.getCount() > target.getItem().getCount())) target = slot;
        }
        if (target == null) return false;
        int before = donor.getItem().getCount();
        click(client, donor.index, 0, ContainerInput.PICKUP);
        if (!plain(menu.getCarried(), batch.recipe.input) || menu.getCarried().getCount() != before) return false;
        click(client, target.index, 0, ContainerInput.PICKUP);
        if (plain(menu.getCarried(), batch.recipe.input)) click(client, donor.index, 0, ContainerInput.PICKUP);
        return menu.getCarried().isEmpty() && donor.getItem().getCount() < before;
    }

    private void drag(Minecraft client, int slot, int stage, int type) {
        click(client, slot, AbstractContainerMenu.getQuickcraftMask(stage, type), ContainerInput.QUICK_CRAFT);
    }

    private void click(Minecraft client, int slot, int button, ContainerInput action) {
        client.gameMode.handleContainerInput(menu.containerId, slot, button, action, client.player);
    }

    private static boolean plain(ItemStack stack, Item item) {
        return !stack.isEmpty() && stack.is(item) && stack.getComponentsPatch().isEmpty();
    }

    private static Slot smallest(List<Slot> slots, Item item) {
        Slot best = null;
        for (Slot s : slots) {
            if (plain(s.getItem(), item) && (best == null || s.getItem().getCount() < best.getItem().getCount())) best = s;
        }
        return best;
    }

    private static int count(List<Slot> slots, Item item) {
        int total = 0;
        for (Slot s : slots) if (plain(s.getItem(), item)) total += s.getItem().getCount();
        return total;
    }

    private static int room(List<Slot> slots, Item item) {
        int total = 0;
        var output = new ItemStack(item);
        for (Slot s : slots) {
            if (s.mayPlace(output) && (!s.hasItem() || plain(s.getItem(), item)))
                total += s.getMaxStackSize(output) - s.getItem().getCount();
        }
        return total;
    }

    private static final class Batch {
        final Recipe recipe;
        final int size;
        final int[] cells, expected;
        int lastTake, inputBefore, outputBefore, takeRetries, repairs;
        boolean collecting, bookPlacement;

        Batch(Recipe recipe, int size, int gridWidth, int gridSize) {
            this.recipe = recipe;
            this.size = size;
            cells = new int[recipe.cost()];
            expected = new int[gridSize];
            for (int n = 0; n < cells.length; n++) cells[n] = n / recipe.width * gridWidth + n % recipe.width;
        }


        int nextCell() {
            for (int i : cells) if (expected[i] < size) return i;
            return -1;
        }

        boolean matchesGrid(List<Slot> grid) {
            for (int i = 0; i < expected.length; i++) {
                var stack = grid.get(i).getItem();
                if (stack.getCount() != expected[i] || (expected[i] > 0 && !plain(stack, recipe.input))) return false;
            }
            return true;
        }

        int uniformCount(List<Slot> grid) {
            int remaining = grid.get(cells[0]).getItem().getCount();
            for (int i = 0; i < expected.length; i++) {
                var stack = grid.get(i).getItem();
                int target = expected[i] == 0 ? 0 : remaining;
                if (stack.getCount() != target || (target > 0 && !plain(stack, recipe.input))) return -1;
            }
            return remaining;
        }
    }
}

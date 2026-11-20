# Mining Dropper 1.2.3 for Minecraft Java 26.3

- Auto-drop is opt-in each time you join. Press `[` to start it and `]` to edit the keep/drop and compacting rules.
- The compactor calculates the final count for each crafting slot before placing ingredients. The fallback can place the planned grid in one client tick, and collection waits for the server’s matching result.
- Handles split stacks, remainders, and explicitly declined recipe-book placements. Raw iron, raw copper, raw gold, and quartz stay loose by default.
- Requires Fabric Loader 0.19.5 or newer, Fabric API, and Java 25 or newer. Mod Menu is optional.

# Mining Dropper

Mining Dropper is a client-side Fabric mod for Minecraft Java 26.3. Choose which mining drops to throw out and which materials to pack into storage blocks.

[Download MiningDropper-1.2.3-mc26.3.jar](https://github.com/Yanglerfish/mining-dropper/raw/refs/heads/main/release/MiningDropper-1.2.3-mc26.3.jar)

## Auto-drop

Press `[` to toggle auto-drop. It starts **off** each time you join a world or server. While it’s on and you’re playing with menus closed, it drops a whole matching stack onto the ground at a time. Press `]` to change the rules.

By default, the drop list contains cobblestone, stone, deepslate, cobbled deepslate, andesite, granite, diorite, tuff, dirt, gravel, calcite, dripstone blocks, netherrack, blackstone, basalt, smooth basalt, and end stone. **All ore blocks and mined products are kept by default.** You can set a Silk Touch ore block and its mined product separately. For example, you can drop coal ore and keep loose coal. Hotbar scanning starts on and can be switched off. Named stacks and stacks with modified item components are skipped.

## Auto-compact

Auto-compact starts **on** and works independently of auto-drop. Open an empty crafting table with an empty cursor. For each kept material set to **BLOCK**, Mining Dropper divides the eligible count across the recipe slots, leaves the remainder loose, and collects the result when the server confirms it. For example, 175 diamonds make 19 diamond blocks and leave four diamonds. The mod can continue through several batches while the table stays open.

| Material | Default |
| --- | --- |
| Coal, redstone dust, lapis lazuli, diamonds, and emeralds | Pack into blocks |
| Iron, copper, gold, and netherite ingots | Pack into blocks |
| Raw iron, raw copper, and raw gold | Keep loose for smelting |
| Nether quartz | Keep loose. If enabled, four make a quartz block in a 2×2 or 3×3 grid |
| Iron, copper, and gold nuggets | Use for ingots only when both compacting switches are on and the ingots can complete a block |

Set a material to **LOOSE** to keep it in item form. A **DROP** rule takes priority over compacting. Existing recipes in the grid are left alone, and clicking the screen gives you manual control. On multiplayer, crafting speed depends on the server’s inventory updates.

## Installation

Install the JAR and Fabric API in your client’s `mods` folder. Version 1.2.3 targets Minecraft Java **26.3**, Fabric Loader **0.19.5 or newer**, and **Java 25 or newer**. Mod Menu is optional. The two shortcut keys can be rebound in Minecraft’s controls. Settings are saved in `config/miningdropper.properties`.

## Building from source

The included Python build script uses a local Prism Launcher installation. Run the 26.3 instance once so its game and loader libraries are available. The script expects Fabric API `0.165.2+26.3` and Mod Menu `21.0.0` in the build instance. Supply a JDK with `javac` 25 or newer.

```powershell
python build.py --prism "$env:APPDATA\PrismLauncher" --instance "Your instance name" --jdk "C:\path\to\jdk-25" --output dist
```

## Development

The feature specification and in-game feedback came from the project owner. Codex generated the implementation and this documentation. The 1.2.3 build was checked locally against Minecraft 26.3 crafting menus and recipes, including 150 inventory layouts.

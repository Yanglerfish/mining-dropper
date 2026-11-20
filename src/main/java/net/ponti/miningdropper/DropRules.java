package net.ponti.miningdropper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class DropRules {
    enum Group {
        TERRAIN("Terrain"), ORES("Ore blocks"), PRODUCTS("Mined items");
        final String title;
        Group(String title) { this.title = title; }
    }

    record Rule(Group group, String key, String label, boolean dropByDefault, List<String> items) {}
    static final List<Rule> ALL;
    static final Map<String, Rule> BY_ITEM;

    static {
        List<Rule> rules = new ArrayList<>();
        for (String id : List.of("cobblestone", "stone", "cobbled_deepslate", "deepslate",
                "andesite", "granite", "diorite", "tuff", "dirt", "gravel", "calcite",
                "dripstone_block", "netherrack", "blackstone", "basalt", "smooth_basalt",
                "end_stone")) {
            rules.add(terrain(id, true));
        }
        for (String id : List.of("sand", "red_sand", "sandstone", "red_sandstone", "clay",
                "pointed_dripstone", "soul_sand", "soul_soil", "magma_block", "flint")) {
            rules.add(terrain(id, false));
        }

        oreFamily(rules, "coal", "Coal", "coal");
        oreFamily(rules, "iron", "Iron", "raw_iron");
        oreFamily(rules, "copper", "Copper", "raw_copper");
        oreFamily(rules, "gold", "Gold", "raw_gold");
        oreFamily(rules, "redstone", "Redstone", "redstone");
        oreFamily(rules, "lapis", "Lapis lazuli", "lapis_lazuli");
        oreFamily(rules, "diamond", "Diamond", "diamond");
        oreFamily(rules, "emerald", "Emerald", "emerald");
        rules.add(rule(Group.ORES, "nether_gold", "Nether gold", false, "nether_gold_ore"));
        rules.add(rule(Group.ORES, "nether_quartz", "Nether quartz", false, "nether_quartz_ore"));
        rules.add(rule(Group.ORES, "ancient_debris", "Ancient debris", false, "ancient_debris"));
        rules.add(rule(Group.PRODUCTS, "gold_nugget", "Gold nuggets", false, "gold_nugget"));
        rules.add(rule(Group.PRODUCTS, "quartz", "Nether quartz", false, "quartz"));

        Map<String, Rule> byItem = new HashMap<>();
        for (Rule rule : rules) {
            for (String id : rule.items()) {
                if (byItem.put("minecraft:" + id, rule) != null)
                    throw new IllegalStateException("Duplicate item rule: " + id);
            }
        }
        ALL = List.copyOf(rules);
        BY_ITEM = Map.copyOf(byItem);
    }

    private static void oreFamily(List<Rule> rules, String id, String label, String product) {
        rules.add(rule(Group.ORES, id, label, false, id + "_ore", "deepslate_" + id + "_ore"));
        rules.add(rule(Group.PRODUCTS, product, displayName(product), false, product));
    }

    private static Rule terrain(String id, boolean drop) {
        return rule(Group.TERRAIN, id, displayName(id), drop, id);
    }

    private static Rule rule(Group group, String id, String label, boolean drop, String... items) {
        return new Rule(group, group.name().toLowerCase(java.util.Locale.ROOT) + "." + id,
                label, drop, List.of(items));
    }

    private static String displayName(String id) {
        return Character.toUpperCase(id.charAt(0)) + id.substring(1).replace('_', ' ');
    }
}

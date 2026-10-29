package neo.menu.service;

import neo.menu.config.ModManagerConfig;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.minecraft.client.resources.language.I18n;
import neo.menu.mod.Mod;
import neo.menu.mod.ModRegistry;

public class ModSearch {
    public static boolean validSearchQuery(String query) {
        return query != null && !query.isEmpty();
    }

    public static List<Mod> search(Predicate<String> hasConfigScreen, String query, List<Mod> candidates) {
        if (!validSearchQuery(query)) {
            return candidates;
        } else {
            return candidates.stream()
                    .map(modContainer -> Map.entry(modContainer,
                            passesFilters(hasConfigScreen, modContainer, query.toLowerCase(Locale.ROOT))
                    ))
                    .filter(pair -> pair.getValue() > 0)
                    .sorted(Comparator.<Map.Entry<Mod, Integer>>comparingInt(Map.Entry::getValue).reversed())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }
    }

    private static int passesFilters(Predicate<String> hasConfigScreen, Mod mod, String query) {
        String modId = mod.getId();
        String modName = mod.getName();
        String modTranslatedName = ModTexts.name(mod);
        String modDescription = mod.getDescription();
        String modSummary = ModTexts.summary(mod);

        String library = I18n.get("modmenu.searchTerms.library");
        String patchwork = I18n.get("modmenu.searchTerms.patchwork");
        String modpack = I18n.get("modmenu.searchTerms.modpack");
        String deprecated = I18n.get("modmenu.searchTerms.deprecated");
        String clientside = I18n.get("modmenu.searchTerms.clientside");
        String configurable = I18n.get("modmenu.searchTerms.configurable");

        // Libraries are currently hidden, ignore them entirely
        if (ModManagerConfig.isHidden(mod) || ModManagerConfig.LIBRARY_VISIBILITY.hideMod(mod, ConfigScreens.hasConfigScreen(mod.getId()))) {
            return 0;
        }

        // Some basic search, could do with something more advanced but this will do for now
        if (modName.toLowerCase(Locale.ROOT).contains(query) // Search default mod name
                || modTranslatedName.toLowerCase(Locale.ROOT).contains(query) // Search localized mod name
                || modId.toLowerCase(Locale.ROOT).contains(query) // Search mod ID
        ) {
            return query.length() >= 3 ? 2 : 1;
        }

        if (modDescription.toLowerCase(Locale.ROOT).contains(query) // Search default mod description
                || modSummary.toLowerCase(Locale.ROOT).contains(query) // Search mod summary
                || authorMatches(mod, query) // Search via author
                || library.contains(query) && mod.getBadges().contains(Mod.Badge.LIBRARY) // Search for lib mods
                || patchwork.contains(query) && mod.getBadges()
                .contains(Mod.Badge.PATCHWORK_FORGE) // Search for patchwork mods
                || modpack.contains(query) && mod.getBadges().contains(Mod.Badge.MODPACK) // Search for modpack mods
                || deprecated.contains(query) && mod.getBadges()
                .contains(Mod.Badge.DEPRECATED) // Search for deprecated mods
                || clientside.contains(query) && mod.getBadges().contains(Mod.Badge.CLIENT) // Search for clientside mods
                || configurable.contains(query) && hasConfigScreen.test(modId) // Search for mods that can be configured
        ) {
            return 1;
        }

        // Allow parent to pass filter if a child passes
        if (ModRegistry.PARENT_MAP.keySet().contains(mod)) {
            for (Mod child : ModRegistry.PARENT_MAP.get(mod)) {
                int result = passesFilters(hasConfigScreen, child, query);
                if (result > 0) {
                    return result;
                }
            }
        }

        return 0;
    }

    private static boolean authorMatches(Mod mod, String query) {
        return mod.getAuthors()
                .stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .anyMatch(s -> s.contains(query.toLowerCase(Locale.ROOT)));
    }
}

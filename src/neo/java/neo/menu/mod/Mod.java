package neo.menu.mod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import neo.menu.util.ModManagerInfo;

public interface Mod {
    @NotNull String getId();

    @NotNull String getName();

    @NotNull DynamicTexture getIcon(IconHandler iconHandler, int i);

    @NotNull String getDescription();

    @NotNull String getVersion();

    @NotNull String getPrefixedVersion();

    @NotNull List<String> getAuthors();

    ModContact getContact(String author);

    /**
     * @return a mapping of contributors to their roles.
     */
    @NotNull Map<String, Collection<String>> getContributors();

    /**
     * @return a mapping of roles to each contributor with that role.
     */
    @NotNull SortedMap<String, Set<String>> getCredits();

    @NotNull Set<Badge> getBadges();

    @Nullable String getWebsite();

    @Nullable String getIssueTracker();

    @Nullable String getSource();

    @Nullable String getParent();

    @NotNull Set<String> getLicense();

    @NotNull Map<String, String> getLinks();

    boolean isReal();

    enum Badge {
        LIBRARY(
                "modmenu.badge.library",
                0xFF107454,
                0xFF093929,
                "library"
        ),
        CLIENT(
                "modmenu.badge.clientsideOnly",
                0xFF2b4b7c,
                0xFF0e2a55,
                null
        ),
        DEPRECATED(
                "modmenu.badge.deprecated",
                0xFF841426,
                0xFF530C17,
                "deprecated"
        ),
        PATCHWORK_FORGE(
                "modmenu.badge.forge",
                0xFF1f2d42,
                0xFF101721,
                null
        ),
        MODPACK(
                "modmenu.badge.modpack",
                0xFF7a2b7c,
                0xFF510d54,
                null
        ),
        MINECRAFT(
                "modmenu.badge.minecraft",
                0xFF6f6c6a,
                0xFF31302f,
                null
        );

        private final Component text;
        private final int outlineColor, fillColor;
        private final String key;
        private static final Map<String, Badge> KEY_MAP = new HashMap<>();

        Badge(String translationKey, int outlineColor, int fillColor, String key) {
            this.text = Component.translatable(translationKey);
            this.outlineColor = outlineColor;
            this.fillColor = fillColor;
            this.key = key;
        }

        public Component getText() {
            return this.text;
        }

        public int getOutlineColor() {
            return this.outlineColor;
        }

        public int getFillColor() {
            return this.fillColor;
        }

        public static Set<Badge> convert(Set<String> badgeKeys, String modId) {
            return badgeKeys.stream().map(key -> {
                if (!KEY_MAP.containsKey(key)) {
                    ModManagerInfo.LOGGER.warn("Skipping unknown badge key '{}' specified by mod '{}'", key, modId);
                }

                return KEY_MAP.get(key);
            }).filter(Objects::nonNull).collect(Collectors.toSet());
        }

        static {
            Arrays.stream(values()).forEach(badge -> KEY_MAP.put(badge.key, badge));
        }
    }
}

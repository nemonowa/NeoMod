package neo.forge;

import neo.menu.mod.IconHandler;
import neo.menu.mod.IconSource;
import neo.menu.mod.Mod;
import neo.menu.mod.ModContact;
import neo.menu.util.ModManagerInfo;
import neo.menu.util.VersionUtil;
import neo.util.NeoAssets;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Forge の {@link IModInfo} (mods.toml) を Mod Menu の {@link Mod} として見せるアダプタ。
 * mods.toml に無い情報 (貢献者・役割・連絡先・親子関係・リンク) は空として扱う。
 */
final class ForgeMod implements Mod {
    private static boolean defaultIconWarningShown = false;

    private final IModInfo info;
    private final String id;
    private final Set<Badge> badges = new HashSet<>();

    ForgeMod(IModInfo info) {
        this.info = info;
        this.id = info.getModId();

        if (id.equals("minecraft")) {
            badges.add(Badge.MINECRAFT);
        } else if (id.equals("forge")) {
            badges.add(Badge.LIBRARY);
        }
        if (info.getOwningFile().isClientSideOnly()) {
            badges.add(Badge.CLIENT);
        }
    }

    @Override
    public @NotNull String getId() {
        return id;
    }

    @Override
    public @NotNull String getName() {
        return info.getDisplayName();
    }

    @Override
    public @NotNull DynamicTexture getIcon(IconHandler iconHandler, int i) {
        DynamicTexture icon = null;
        if (id.equals("minecraft")) {
            icon = iconHandler.createVanillaPackIcon(); // バニラのアセット (標準パックの pack.png) を参照する
        } else if (info.getLogoFile().isPresent()) {
            IconSource source = new ForgeIconSource(info.getOwningFile().getFile(), id);
            icon = iconHandler.createIcon(source, info.getLogoFile().get());
        }

        if (icon == null) {
            if (!id.equals("minecraft") && info.getLogoFile().isPresent() && !defaultIconWarningShown) {
                ModManagerInfo.LOGGER.warn("Warning! Mod {} has a broken icon, loading default icon", id);
                defaultIconWarningShown = true;
            }
            icon = Objects.requireNonNull(iconHandler.createIcon(ownSource(), NeoAssets.path("unknown_icon.png")));
        }
        return icon;
    }

    /** Mod Menu の同梱アセット (assets/neo/…) が入っている、このMod自身の jar。 */
    private static IconSource ownSource() {
        IModFileInfo own = Objects.requireNonNull(ModList.getModFileById(NeoForgeMod.MOD_ID),
                "Cannot find the mod file of " + NeoForgeMod.MOD_ID);
        return new ForgeIconSource(own.getFile(), NeoForgeMod.MOD_ID);
    }

    @Override
    public @NotNull String getDescription() {
        return info.getDescription();
    }

    @Override
    public @NotNull String getVersion() {
        return info.getVersion().toString();
    }

    @Override
    public @NotNull String getPrefixedVersion() {
        return VersionUtil.getPrefixedVersion(getVersion());
    }

    @Override
    public @NotNull List<String> getAuthors() {
        List<String> authors = new ArrayList<>();
        Object value = info.getConfig().<Object>getConfigElement("authors").orElse(null);
        if (value instanceof String text) {
            for (String author : text.split(",")) {
                if (!author.isBlank()) {
                    authors.add(author.trim());
                }
            }
        } else if (value instanceof Collection<?> list) {
            list.forEach(author -> authors.add(String.valueOf(author)));
        }
        return authors;
    }

    @Override
    public ModContact getContact(String author) {
        return null;
    }

    @Override
    public @NotNull Map<String, Collection<String>> getContributors() {
        return new HashMap<>();
    }

    @Override
    public @NotNull SortedMap<String, Set<String>> getCredits() {
        return new TreeMap<>();
    }

    @Override
    public @NotNull Set<Badge> getBadges() {
        return badges;
    }

    @Override
    public @Nullable String getWebsite() {
        return info.getModURL().map(Object::toString).orElse(null);
    }

    @Override
    public @Nullable String getIssueTracker() {
        return info.getOwningFile().getConfig().<String>getConfigElement("issueTrackerURL").orElse(null);
    }

    @Override
    public @Nullable String getSource() {
        return null;
    }

    @Override
    public @Nullable String getParent() {
        return null;
    }

    @Override
    public @NotNull Set<String> getLicense() {
        Set<String> license = new HashSet<>();
        String text = info.getOwningFile().getLicense();
        if (text != null && !text.isBlank()) {
            license.add(text);
        }
        return license;
    }

    @Override
    public @NotNull Map<String, String> getLinks() {
        return new HashMap<>();
    }

    @Override
    public boolean isReal() {
        return true;
    }
}

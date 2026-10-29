package neo.fabric;

import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import neo.menu.util.ModManagerInfo;
import neo.menu.mod.IconHandler;
import neo.menu.mod.Mod;
import neo.menu.mod.ModRegistry;
import neo.menu.mod.ModContact;
import neo.util.NeoAssets;

public class FabricDummyParentMod implements Mod {
    private final String id;
    private final FabricMod host;

    public FabricDummyParentMod(FabricMod host, String id) {
        this.host = host;
        this.id = id;
    }

    @Override
    public @NotNull String getId() {
        return id;
    }

    @Override
    public @NotNull String getName() {
        FabricMod.ModMenuData.DummyParentData parentData = host.getModMenuData().getDummyParentData();
        if (parentData != null) {
            return parentData.getName().orElse("");
        }
        if (id.equals("fabric-api")) {
            return "Fabric API";
        }
        return id;
    }

    @Override
    public @NotNull DynamicTexture getIcon(IconHandler iconHandler, int i) {
        String iconSourceId = host.getId();
        FabricMod.ModMenuData.DummyParentData parentData = host.getModMenuData().getDummyParentData();
        String iconPath = null;
        if (parentData != null) {
            iconPath = parentData.getIcon().orElse(null);
        }
        if ("inherit".equals(iconPath)) {
            return host.getIcon(iconHandler, i);
        }
        if (iconPath == null) {
            iconSourceId = ModManagerInfo.MOD_ID;
            if (id.equals("fabric-api")) {
                iconPath = NeoAssets.path("fabric.png");
            } else {
                iconPath = NeoAssets.path("unknown_parent.png");
            }
        }
        return Objects.requireNonNull(
                iconHandler.createIcon(ModContainerIconSource.of(iconSourceId), iconPath),
                "Mod icon for " + getId() + " is null somehow (should be filled with default in this case)"
        );
    }

    @Override
    public @NotNull String getDescription() {
        FabricMod.ModMenuData.DummyParentData parentData = host.getModMenuData().getDummyParentData();
        if (parentData != null) {
            return parentData.getDescription().orElse("");
        }
        return "";
    }

    @Override
    public @NotNull String getVersion() {
        return "";
    }

    @Override
    public @NotNull String getPrefixedVersion() {
        return "";
    }

    @Override
    public @NotNull List<String> getAuthors() {
        return new ArrayList<>();
    }

    @Override
    public ModContact getContact(String author) {
        return null;
    }

    @Override
    public @NotNull Map<String, Collection<String>> getContributors() {
        return Map.of();
    }

    @Override
    public @NotNull SortedMap<String, Set<String>> getCredits() {
        return new TreeMap<>();
    }

    @Override
    public @NotNull Set<Badge> getBadges() {
        FabricMod.ModMenuData.DummyParentData parentData = host.getModMenuData().getDummyParentData();
        if (parentData != null) {
            return parentData.getBadges();
        }
        var badges = new HashSet<Badge>();
        if (id.equals("fabric-api")) {
            badges.add(Badge.LIBRARY);
        }

        boolean modpackChildren = true;
        for (Mod mod : ModRegistry.PARENT_MAP.get(this)) {
            if (!mod.getBadges().contains(Badge.MODPACK)) {
                modpackChildren = false;
            }
        }
        if (modpackChildren) {
            badges.add(Badge.MODPACK);
        }

        return badges;
    }

    @Override
    public @Nullable String getWebsite() {
        return null;
    }

    @Override
    public @Nullable String getIssueTracker() {
        return null;
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
        return new HashSet<>();
    }

    @Override
    public @NotNull Map<String, String> getLinks() {
        return new HashMap<>();
    }

    @Override
    public boolean isReal() {
        return false;
    }

}

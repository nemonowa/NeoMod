package neo.fabric;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import neo.menu.util.VersionUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import neo.menu.util.ModManagerInfo;
import neo.menu.mod.IconHandler;
import neo.menu.mod.Mod;
import neo.menu.mod.ModContact;
import neo.util.NeoAssets;

public class FabricMod implements Mod {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mod Manager | FabricMod");

    protected final ModContainer container;
    protected final ModMetadata metadata;

    protected final ModMenuData modMenuData;

    protected final Set<Badge> badges;

    protected final Map<String, String> links = new HashMap<>();

    protected boolean defaultIconWarning = true;

    public FabricMod(ModContainer modContainer, Set<String> modpackMods) {
        this.container = modContainer;
        this.metadata = modContainer.getMetadata();

        String id = metadata.getId();
        /* Load modern mod menu custom value data */
        Optional<String> parentId = Optional.empty();
        ModMenuData.DummyParentData parentData = null;
        Set<String> badgeNames = new HashSet<>();
        CustomValue modMenuValue = metadata.getCustomValue("modmenu");
        if (modMenuValue != null && modMenuValue.getType() == CustomValue.CvType.OBJECT) {
            CustomValue.CvObject modMenuObject = modMenuValue.getAsObject();
            CustomValue parentCv = modMenuObject.get("parent");
            if (parentCv != null) {
                if (parentCv.getType() == CustomValue.CvType.STRING) {
                    parentId = Optional.of(parentCv.getAsString());
                } else if (parentCv.getType() == CustomValue.CvType.OBJECT) {
                    try {
                        CustomValue.CvObject parentObj = parentCv.getAsObject();
                        parentId = CustomValueUtil.getString("id", parentObj);
                        parentData = new ModMenuData.DummyParentData(
                                parentId.orElseThrow(() -> new RuntimeException("Parent object lacks an id")),
                                CustomValueUtil.getString("name", parentObj),
                                CustomValueUtil.getString("description", parentObj),
                                CustomValueUtil.getString("icon", parentObj),
                                CustomValueUtil.getStringSet("badges", parentObj).orElse(new HashSet<>())
                        );
                        if (parentId.orElse("").equals(id)) {
                            parentId = Optional.empty();
                            parentData = null;
                            throw new RuntimeException("Mod declared itself as its own parent");
                        }
                    } catch (Throwable t) {
                        LOGGER.error("Error loading parent data from mod: {}", id, t);
                    }
                }
            }
            badgeNames.addAll(CustomValueUtil.getStringSet("badges", modMenuObject).orElse(new HashSet<>()));
            links.putAll(CustomValueUtil.getStringMap("links", modMenuObject).orElse(new HashMap<>()));
        }

        boolean isGenerated = CustomValueUtil.getBoolean("fabric-loom:generated", metadata).orElse(false);

        /* Automatically set the mod containing a Loom-generated library as its parent */
        if (isGenerated && parentId.isEmpty() && container.getContainingMod().isPresent()) {
            ModContainer inside = container.getContainingMod().get();
            parentId = Optional.of(inside.getMetadata().getId());
        }
        this.modMenuData = new ModMenuData(badgeNames, parentId, parentData, id);

        /* Hardcode parents and badges for Fabric API & Fabric Loader */
        if (id.startsWith("fabric") && metadata.containsCustomValue("fabric-api:module-lifecycle")) {
            if (FabricLoader.getInstance().isModLoaded("fabric-api") || !FabricLoader.getInstance()
                    .isModLoaded("fabric")) {
                modMenuData.fillParentIfEmpty("fabric-api");
            } else {
                modMenuData.fillParentIfEmpty("fabric");
            }

            modMenuData.badges.add(Badge.LIBRARY);
        }
        if (id.startsWith("fabric") && (id.equals("fabricloader") || metadata.getProvides()
                .contains("fabricloader") || id.equals("fabric") || id.equals("fabric-api") || metadata.getProvides()
                .contains("fabric") || metadata.getProvides()
                .contains("fabric-api") || id.equals("fabric-language-kotlin"))) {
            modMenuData.badges.add(Badge.LIBRARY);
        }

        /* Add additional badges */
        this.badges = modMenuData.badges;
        if (this.metadata.getEnvironment() == ModEnvironment.CLIENT) {
            badges.add(Badge.CLIENT);
        }

        if (isGenerated) {
            badges.add(Badge.LIBRARY);
        }

        if ("deprecated".equals(CustomValueUtil.getString("fabric-api:module-lifecycle", metadata).orElse(null))) {
            badges.add(Badge.DEPRECATED);
        }

        if (metadata.containsCustomValue("patchwork:patcherMeta")) {
            badges.add(Badge.PATCHWORK_FORGE);
        }

        if (modpackMods.contains(getId()) && !"builtin".equals(this.metadata.getType())) {
            badges.add(Badge.MODPACK);
        }

        if ("minecraft".equals(getId())) {
            badges.add(Badge.MINECRAFT);
        }
    }

    public @NotNull ModContainer getContainer() {
        return container;
    }

    @Override
    public @NotNull String getId() {
        return metadata.getId();
    }

    @Override
    public @NotNull String getName() {
        return metadata.getName();
    }

    @Override
    public @NotNull DynamicTexture getIcon(IconHandler iconHandler, int i) {
        DynamicTexture icon;
        if ("minecraft".equals(getId())) {
            icon = iconHandler.createVanillaPackIcon(); // バニラのアセット (標準パックの pack.png) を参照する。この Mod は画像を持たない
        } else {
            String iconSourceId = getId();
            String iconPath = metadata.getIconPath(i).orElse("assets/" + getId() + "/icon.png");
            if ("java".equals(getId())) {
                iconSourceId = ModManagerInfo.MOD_ID;
                iconPath = NeoAssets.path("java_icon.png");
            }

            icon = iconHandler.createIcon(ModContainerIconSource.of(iconSourceId), iconPath);
        }
        if (icon == null) {
            if (defaultIconWarning) {
                LOGGER.warn("Warning! Mod {} has a broken icon, loading default icon", metadata.getId());
                defaultIconWarning = false;
            }

            return Objects.requireNonNull(iconHandler.createIcon(
                    ModContainerIconSource.of(ModManagerInfo.MOD_ID),
                    NeoAssets.path("unknown_icon.png")
            ));
        }
        return icon;
    }

    @Override
    public @NotNull String getDescription() {
        return metadata.getDescription();
    }

    @Override
    public @NotNull String getVersion() {
        if ("java".equals(getId())) {
            return System.getProperty("java.version");
        } else {
            return metadata.getVersion().getFriendlyString();
        }
    }

    public @NotNull String getPrefixedVersion() {
        return VersionUtil.getPrefixedVersion(getVersion());
    }

    @Override
    public @NotNull List<String> getAuthors() {
        var authors = metadata.getAuthors().stream().map(Person::getName).collect(Collectors.toList());
        if (authors.isEmpty()) {
            if ("minecraft".equals(getId())) {
                return Lists.newArrayList("Mojang Studios");
            } else if ("java".equals(getId())) {
                return Lists.newArrayList(System.getProperty("java.vendor"));
            }
        }

        return authors;
    }

    @Override
    public ModContact getContact(String author) {
        for (Person person : metadata.getAuthors()) {
            if (person.getName().equals(author)) {
                return person.getContact()::get;
            }
        }
        for (Person person : metadata.getContributors()) {
            if (person.getName().equals(author)) {
                return person.getContact()::get;
            }
        }
        return null;
    }

    @Override
    public @NotNull Map<String, Collection<String>> getContributors() {
        var contributors = new LinkedHashMap<String, Collection<String>>();
        for (var contributor : this.metadata.getContributors()) {
            contributors.put(contributor.getName(), List.of("Contributor"));
        }

        return contributors;
    }

    @Override
    public @NotNull SortedMap<String, Set<String>> getCredits() {
        SortedMap<String, Set<String>> credits = new TreeMap<>();

        var authors = this.getAuthors();
        var contributors = this.getContributors();
        for (var author : authors) {
            contributors.put(author, List.of("Author"));
        }

        for (var contributor : contributors.entrySet()) {
            for (var role : contributor.getValue()) {
                credits.computeIfAbsent(role, key -> new LinkedHashSet<>());
                credits.get(role).add(contributor.getKey());
            }
        }

        return credits;
    }

    @Override
    public @NotNull Set<Badge> getBadges() {
        return badges;
    }

    @Override
    public @Nullable String getWebsite() {
        if ("minecraft".equals(getId())) {
            return "https://www.minecraft.net/";
        } else if ("java".equals(getId())) {
            return System.getProperty("java.vendor.url");
        } else {
            return metadata.getContact().get("homepage").orElse(null);
        }
    }

    @Override
    public @Nullable String getIssueTracker() {
        if ("minecraft".equals(getId())) {
            return "https://aka.ms/snapshotbugs?ref=game";
        } else {
            return metadata.getContact().get("issues").orElse(null);
        }
    }

    @Override
    public @Nullable String getSource() {
        return metadata.getContact().get("sources").orElse(null);
    }

    @Override
    public @Nullable String getParent() {
        return modMenuData.parent.orElse(null);
    }

    @Override
    public @NotNull Set<String> getLicense() {
        if ("minecraft".equals(getId())) {
            return Sets.newHashSet("Minecraft EULA");
        } else {
            return Sets.newHashSet(metadata.getLicense());
        }
    }

    @Override
    public @NotNull Map<String, String> getLinks() {
        return links;
    }

    @Override
    public boolean isReal() {
        return true;
    }

    public ModMenuData getModMenuData() {
        return modMenuData;
    }

    static class ModMenuData {
        private final Set<Badge> badges;
        private Optional<String> parent;
        private @Nullable
        final DummyParentData dummyParentData;

        public ModMenuData(Set<String> badges, Optional<String> parent, DummyParentData dummyParentData, String id) {
            this.badges = Badge.convert(badges, id);
            this.parent = parent;
            this.dummyParentData = dummyParentData;
        }

        public Set<Badge> getBadges() {
            return badges;
        }

        public Optional<String> getParent() {
            return parent;
        }

        public @Nullable DummyParentData getDummyParentData() {
            return dummyParentData;
        }

        public void fillParentIfEmpty(String parent) {
            if (!this.parent.isPresent()) {
                this.parent = Optional.of(parent);
            }
        }

        public static class DummyParentData {
            private final String id;
            private final Optional<String> name;
            private final Optional<String> description;
            private final Optional<String> icon;
            private final Set<Badge> badges;

            public DummyParentData(
                    String id,
                    Optional<String> name,
                    Optional<String> description,
                    Optional<String> icon,
                    Set<String> badges
            ) {
                this.id = id;
                this.name = name;
                this.description = description;
                this.icon = icon;
                this.badges = Badge.convert(badges, id);
            }

            public String getId() {
                return id;
            }

            public Optional<String> getName() {
                return name;
            }

            public Optional<String> getDescription() {
                return description;
            }

            public Optional<String> getIcon() {
                return icon;
            }

            public Set<Badge> getBadges() {
                return badges;
            }
        }
    }
}

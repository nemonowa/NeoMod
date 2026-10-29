package neo.menu.gui;

import com.google.common.base.Joiner;
import com.mojang.blaze3d.Blaze3D;
import neo.menu.config.ModManagerConfig;
import neo.menu.util.ModManagerScreenTexts;
import neo.menu.util.TranslationUtil;
import neo.menu.mod.Mod;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonLinks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import neo.menu.mod.ModRegistry;
import neo.menu.service.ModTexts;
import neo.menu.service.ConfigScreens;
import net.minecraft.client.gui.components.events.GuiEventListener;
import neo.menu.util.ModManagerPlatform;
import neo.util.NeoAssets;

public class ModsScreen extends Screen implements ModsScreenHost {
    private static final Identifier CONFIGURE_SPRITE_ENABLED = Identifier.fromNamespaceAndPath(NeoAssets.NAMESPACE, "configure/enabled");
    private static final Identifier CONFIGURE_SPRITE_DISABLED = Identifier.fromNamespaceAndPath(NeoAssets.NAMESPACE, "configure/disabled");
    private static final Identifier CONFIGURE_SPRITE_FOCUSED = Identifier.fromNamespaceAndPath(NeoAssets.NAMESPACE, "configure/focused");

    private static final Logger LOGGER = LoggerFactory.getLogger("Mod Manager | ModsScreen");
    private final Screen previousScreen;
    private ModListEntry selected;
    private ModBadgeRenderer modBadgeRenderer;
    private boolean init = false;
    private static final int RIGHT_PANE_Y = 48;
    private int paneWidth;
    private int rightPaneX;
    private int searchBoxX;
    private final Set<String> showModChildren = new HashSet<>();

    private EditBox searchBox;
    private ModListWidget modList;
    private @Nullable SpriteIconButton configureButton;
    private AbstractWidget websiteButton;
    private AbstractWidget issuesButton;
    private DescriptionListWidget descriptionListWidget;

    public final Map<String, Boolean> modHasConfigScreen = new HashMap<>();
    private final Map<String, Throwable> modScreenErrors = new HashMap<>();


    public ModsScreen(Screen previousScreen) {
        super(ModManagerScreenTexts.SCREEN_TITLE);
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        int paneY = ModManagerConfig.CONFIG_MODE.getValue() ? 48 : 48 + 19;
        this.paneWidth = this.width / 2 - 8;
        this.rightPaneX = this.width - this.paneWidth;

        // Mod list
        this.modList = new ModListWidget(this.minecraft,
                this.paneWidth,
                this.height - paneY - 36,
                paneY,
                36,
                this.modList,
                this
        );
        this.modList.setX(0);

        // Search box
        int searchWidthMax = this.paneWidth - 32;
        int searchBoxWidth = ModManagerConfig.CONFIG_MODE.getValue() ? Math.min(200, searchWidthMax) : searchWidthMax;

        this.searchBoxX = this.paneWidth / 2 - searchBoxWidth / 2;
        this.searchBox = new EditBox(this.font,
                this.searchBoxX,
                22,
                searchBoxWidth,
                20,
                this.searchBox,
                ModManagerScreenTexts.SEARCH
        );
        this.searchBox.setResponder(text -> {
            this.modList.filter(text, false);
        });

        // Configure button
        if (!ModManagerConfig.HIDE_CONFIG_BUTTONS.getValue()) {
            this.configureButton =
                    SpriteIconButton.builder(
                            ModManagerScreenTexts.CONFIGURE,
                            button -> {
                                final String id = Objects.requireNonNull(selected).getMod().getId();
                                if (getModHasConfigScreen(id)) {
                                    this.safelyOpenConfigScreen(id);
                                } else {
                                    button.active = false;
                                }
                            },
                            true
                    )
                    .size(20,20)
                    .sprite(new WidgetSprites(
                            CONFIGURE_SPRITE_ENABLED,
                            CONFIGURE_SPRITE_DISABLED,
                            CONFIGURE_SPRITE_FOCUSED
                    ), 16, 16)
                    .build();
            this.configureButton.setPosition(width - 24, RIGHT_PANE_Y);
        }

        // Website button
        int urlButtonWidths = this.paneWidth / 2 - 2;
        int cappedButtonWidth = Math.min(urlButtonWidths, 200);
        this.websiteButton = Button.builder(ModManagerScreenTexts.WEBSITE, button -> {
                    final Mod mod = Objects.requireNonNull(selected).getMod();
                    boolean isMinecraft = selected.getMod().getId().equals("minecraft");
                    if (isMinecraft) {
                        var url = SharedConstants.getCurrentVersion().stable() ? CommonLinks.RELEASE_FEEDBACK : CommonLinks.SNAPSHOT_FEEDBACK;
                        ConfirmLinkScreen.confirmLinkNow(this, url, true);
                    } else {
                        var url = mod.getWebsite();
                        if (url != null) {
                            ConfirmLinkScreen.confirmLinkNow(this, URI.create(url), false);
                        }
                    }
                })
                .pos(this.rightPaneX + (urlButtonWidths / 2) - (cappedButtonWidth / 2), RIGHT_PANE_Y + 36)
                .size(Math.min(urlButtonWidths, 200), 20)
                .build();

        // Issues button
        this.issuesButton = Button.builder(ModManagerScreenTexts.ISSUES, button -> {
                    final Mod mod = Objects.requireNonNull(selected).getMod();
                    boolean isMinecraft = selected.getMod().getId().equals("minecraft");
                    if (isMinecraft) {
                        ConfirmLinkScreen.confirmLinkNow(this, CommonLinks.SNAPSHOT_BUGS_FEEDBACK, true);
                    } else {
                        var url = mod.getIssueTracker();
                        if (url != null) {
                            ConfirmLinkScreen.confirmLinkNow(this, URI.create(url), false);
                        }
                    }
                })
                .pos(this.rightPaneX + urlButtonWidths + 4 + (urlButtonWidths / 2) - (cappedButtonWidth / 2), RIGHT_PANE_Y + 36)
                .size(Math.min(urlButtonWidths, 200), 20)
                .build();

        // Description list
        this.descriptionListWidget = new DescriptionListWidget(
                this.minecraft,
                this.paneWidth,
                this.height - RIGHT_PANE_Y - 96,
                RIGHT_PANE_Y + 60,
                font.lineHeight + 1,
                this.descriptionListWidget,
                this
        );
        this.descriptionListWidget.setX(this.rightPaneX);

        // Mods folder button
        AbstractWidget modsFolderButton = Button.builder(ModManagerScreenTexts.MODS_FOLDER, button -> Blaze3D.openUri(ModManagerPlatform.get().modsDir().toUri())).pos(this.width / 2 - 154, this.height - 28).size(150, 20).build();

        // Done button
        AbstractWidget doneButton = Button.builder(CommonComponents.GUI_DONE, button -> minecraft.gui.setScreen(previousScreen)).pos(this.width / 2 + 4, this.height - 28).size(150, 20).build();

        // Initialize data
        modList.finalizeInit();

        // Add children
        this.addWidget(this.searchBox);
        this.setInitialFocus(this.searchBox);
        this.addWidget(this.modList);
        if (this.configureButton != null) {
            this.addRenderableWidget(this.configureButton);
        }

        this.addRenderableWidget(this.websiteButton);
        this.addRenderableWidget(this.issuesButton);
        this.addWidget(this.descriptionListWidget);
        this.addRenderableWidget(modsFolderButton);
        this.addRenderableWidget(doneButton);

        this.init = true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        return super.keyPressed(input) || this.searchBox.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        return this.searchBox.charTyped(input);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        ModListEntry selectedEntry = selected;
        if (selectedEntry != null) {
            this.descriptionListWidget.extractRenderState(graphics, mouseX, mouseY, delta);
        }

        this.modList.extractRenderState(graphics, mouseX, mouseY, delta);
        this.searchBox.extractRenderState(graphics, mouseX, mouseY, delta);
        // 左上のタイトル: 下線つき。カーソルを重ねると説明を出す
        Component underlinedTitle = this.title.copy().withStyle(style -> style.withUnderlined(true));
        int titleCenterX = this.modList.getWidth() / 2;
        int titleHalfWidth = this.font.width(underlinedTitle) / 2;
        graphics.centeredText(this.font, underlinedTitle, titleCenterX, 8, 0xFFFFFFFF);
        if (mouseX >= titleCenterX - titleHalfWidth && mouseX <= titleCenterX + titleHalfWidth
                && mouseY >= 8 && mouseY < 8 + this.font.lineHeight) {
            graphics.setTooltipForNextFrame(ModManagerScreenTexts.SCREEN_TITLE_TOOLTIP, mouseX, mouseY);
        }
        int grayColor = 0xFFAAAAAA;
        if (!ModManagerConfig.DISABLE_DRAG_AND_DROP.getValue()) {
            graphics.centeredText(
                    this.font,
                    ModManagerScreenTexts.DROP_INFO_LINE_1,
                    this.width - this.modList.getWidth() / 2,
                    RIGHT_PANE_Y / 2 - minecraft.font.lineHeight - 1,
                    grayColor
            );
            graphics.centeredText(
                    this.font,
                    ModManagerScreenTexts.DROP_INFO_LINE_2,
                    this.width - this.modList.getWidth() / 2,
                    RIGHT_PANE_Y / 2 + 1,
                    grayColor
            );
        }

        if (!ModManagerConfig.CONFIG_MODE.getValue()) {
            Component fullModCount = this.computeModCountText(true, false);
            if (font.width(fullModCount) <= modList.getWidth() - 5) {
                graphics.text(font,
                        fullModCount.getVisualOrderText(),
                        this.searchBoxX,
                        52,
                        0xFFFFFFFF,
                        true
                );
            } else {
                graphics.text(
                        font,
                        computeModCountText(false, false).getVisualOrderText(),
                        this.searchBoxX,
                        46,
                        0xFFFFFFFF,
                        true
                );
                graphics.text(
                        font,
                        computeLibraryCountText(false).getVisualOrderText(),
                        this.searchBoxX,
                        57,
                        0xFFFFFFFF,
                        true
                );
            }
        }

        if (selectedEntry != null) {
            Mod mod = selectedEntry.getMod();
            int x = this.rightPaneX;
            if ("java".equals(mod.getId())) {
                DrawingUtil.drawJavaBackground(graphics, x, RIGHT_PANE_Y, 32, 32);
            }

            graphics.blit(RenderPipelines.GUI_TEXTURED, this.selected.getIconTexture(), x, RIGHT_PANE_Y, 0.0F, 0.0F, 32, 32, 32, 32, 0xFFFFFFFF);
            int lineSpacing = font.lineHeight + 1;
            int imageOffset = 36;
            Component name = Component.literal(ModTexts.name(mod));
            FormattedText trimmedName = name;
            int maxNameWidth = this.width - (x + imageOffset);
            if (font.width(name) > maxNameWidth) {
                FormattedText ellipsis = FormattedText.of("...");
                trimmedName = FormattedText.composite(font.substrByWidth(name, maxNameWidth - font.width(ellipsis)), ellipsis);
            }

            graphics.text(
                    font,
                    Language.getInstance().getVisualOrder(trimmedName),
                    x + imageOffset,
                    RIGHT_PANE_Y + 1,
                    0xFFFFFFFF,
                    true
            );

            if (mouseX > x + imageOffset && mouseY > RIGHT_PANE_Y + 1 &&
                    mouseY < RIGHT_PANE_Y + 1 + font.lineHeight &&
                    mouseX < x + imageOffset + font.width(trimmedName)) {
                graphics.setTooltipForNextFrame(ModManagerScreenTexts.modIdTooltip(mod.getId()), mouseX, mouseY);
            }

            if (this.init || modBadgeRenderer == null || modBadgeRenderer.getMod() != mod) {
                modBadgeRenderer = new ModBadgeRenderer(
                        x + imageOffset + minecraft.font.width(trimmedName) + 2,
                        RIGHT_PANE_Y,
                        width - 28,
                        selectedEntry.mod
                );
                this.init = false;
            }

            modBadgeRenderer.draw(graphics, mouseX, mouseY);

            if (mod.isReal()) {
                graphics.text(
                        font,
                        mod.getPrefixedVersion(),
                        x + imageOffset,
                        RIGHT_PANE_Y + 2 + lineSpacing,
                        0xFFAAAAAA,
                        true
                );
            }

            String authors;
            List<String> names = mod.getAuthors();
            if (!names.isEmpty()) {
                if (names.size() > 1) {
                    authors = Joiner.on(", ").join(names);
                } else {
                    authors = names.getFirst();
                }

                DrawingUtil.drawWrappedString(
                        graphics,
                        I18n.get("modmenu.authorPrefix", authors),
                        x + imageOffset,
                        RIGHT_PANE_Y + 2 + lineSpacing * 2,
                        this.paneWidth - imageOffset - 4,
                        1,
                        0xFFAAAAAA
                );
            }
        }
    }

    private Component computeModCountText(boolean includeLibs, boolean onInit) {
        int[] rootMods = formatModCount(ModRegistry.ROOT_MODS.values()
                .stream()
                .filter(mod -> !ModManagerConfig.isHidden(mod) && !mod.getBadges().contains(Mod.Badge.LIBRARY))
                .map(Mod::getId)
                .collect(Collectors.toSet()), onInit);
        if (includeLibs && !onInit) {
            var visibility = ModManagerConfig.LIBRARY_VISIBILITY;

            int[] rootLibs = formatModCount(ModRegistry.ROOT_MODS.values()
                    .stream()
                    .filter(mod -> !ModManagerConfig.isHidden(mod) && mod.getBadges().contains(Mod.Badge.LIBRARY) && !visibility.hideMod(mod, getModHasConfigScreen(mod.getId())))
                    .map(Mod::getId)
                    .collect(Collectors.toSet()), false);

            if (rootLibs.length == 1 && rootLibs[0] != 0 || rootLibs.length == 2 && rootLibs[1] != 0) {
                return TranslationUtil.translateNumeric("modmenu.showingModsLibraries", rootMods, rootLibs);
            }
        }

        return TranslationUtil.translateNumeric("modmenu.showingMods", rootMods);
    }

    private Component computeLibraryCountText(boolean onInit) {
        var visibility = ModManagerConfig.LIBRARY_VISIBILITY;
        if (!onInit) {
            int[] rootLibs = formatModCount(ModRegistry.ROOT_MODS.values()
                    .stream()
                    .filter(mod -> !ModManagerConfig.isHidden(mod) && mod.getBadges().contains(Mod.Badge.LIBRARY) && !visibility.hideMod(mod, this.getModHasConfigScreen(mod.getId())))
                    .map(Mod::getId)
                    .collect(Collectors.toSet()), false);
            return TranslationUtil.translateNumeric("modmenu.showingLibraries", rootLibs);
        } else {
            return Component.empty();
        }
    }

    private int[] formatModCount(Set<String> set, boolean allVisible) {
        int visible = this.modList.getDisplayedCountFor(set);
        int total = set.size();
        if (visible == total || allVisible) {
            return new int[]{total};
        } else {
            return new int[]{visible, total};
        }
    }

    @Override
    public void onClose() {
        this.modList.close();
        this.minecraft.gui.setScreen(this.previousScreen);
    }

    public ModListEntry getSelectedEntry() {
        return selected;
    }

    public void updateSelectedEntry(ModListEntry entry) {
        if (entry == null) {
            return;
        }

        this.selected = entry;
        String modId = selected.getMod().getId();

        this.descriptionListWidget.updateSelectedMod(selected.getMod());

        if (this.configureButton != null) {
            this.configureButton.active = getModHasConfigScreen(modId);
            this.configureButton.visible =
                    getModHasConfigScreen(modId) || modScreenErrors.containsKey(modId);

            if (modScreenErrors.containsKey(modId)) {
                Throwable e = modScreenErrors.get(modId);
                this.configureButton.setTooltip(Tooltip.create(ModManagerScreenTexts.configureError(modId, e)));
            } else {
                this.configureButton.setTooltip(Tooltip.create(ModManagerScreenTexts.CONFIGURE));
            }
        }

        boolean isMinecraft = modId.equals("minecraft");
        // ボタンの文言は、Minecraft も他の Mod と同じ「ウェブサイト」「問題を報告」に統一 (押したときのリンク先は Minecraft 用のまま)
        this.websiteButton.setMessage(ModManagerScreenTexts.WEBSITE);
        this.issuesButton.setMessage(ModManagerScreenTexts.ISSUES);

        this.websiteButton.visible = true;
        this.websiteButton.active = isMinecraft || selected.getMod().getWebsite() != null;

        this.issuesButton.visible = true;
        this.issuesButton.active = isMinecraft || selected.getMod().getIssueTracker() != null;
    }

    public String getSearchInput() {
        return this.searchBox.getValue();
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        Path modsDirectory = ModManagerPlatform.get().modsDir();

        // Filter out none mods
        List<Path> mods = paths.stream().filter(ModManagerPlatform.get()::isModFile).toList();
        if (mods.isEmpty()) {
            return;
        }

        String modList = mods.stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining(", "));
        assert this.minecraft != null;
        this.minecraft.gui.setScreen(new ConfirmScreen((value) -> {
            if (value) {
                boolean allSuccessful = true;
                for (Path path : mods) {
                    try {
                        Files.copy(path, modsDirectory.resolve(path.getFileName()));
                    } catch (IOException e) {
                        LOGGER.warn("Failed to copy mod from {} to {}", path, modsDirectory.resolve(path.getFileName()));
                        SystemToast.onPackCopyFailure(minecraft, path.toString());
                        allSuccessful = false;
                        break;
                    }
                }

                if (allSuccessful) {
                    SystemToast.add(minecraft.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, ModManagerScreenTexts.DROP_SUCCESSFUL_LINE_1, ModManagerScreenTexts.DROP_SUCCESSFUL_LINE_2);
                }
            }
            this.minecraft.gui.setScreen(this);
        }, ModManagerScreenTexts.DROP_CONFIRM, Component.literal(modList)));
    }

    @Override
    public Screen asScreen() {
        return this;
    }

    @Override
    public boolean isFocusOwner(GuiEventListener child) {
        return getFocused() == child;
    }

    @Override
    public boolean hasConfigScreenError(String modId) {
        return modScreenErrors.containsKey(modId);
    }

    @Override
    public boolean isShowingChildren(String modId) {
        return showModChildren.contains(modId);
    }

    @Override
    public void setShowingChildren(String modId, boolean showing) {
        if (showing) {
            showModChildren.add(modId);
        } else {
            showModChildren.remove(modId);
        }
    }

    public boolean getModHasConfigScreen(String modId) {
        if (this.modScreenErrors.containsKey(modId)) {
            return false;
        } else {
            return this.modHasConfigScreen.computeIfAbsent(modId, ConfigScreens::hasConfigScreen);
        }
    }

    public void safelyOpenConfigScreen(String modId) {
        try {
            Screen screen = ConfigScreens.getConfigScreen(modId, this);
            if (screen != null) {
                assert this.minecraft != null;
                this.minecraft.gui.setScreen(screen);
            }
        } catch (java.lang.NoClassDefFoundError e) {
            LOGGER.warn("The '{}' mod config screen is not available because {} is missing.", modId, e.getLocalizedMessage());
            modScreenErrors.put(modId, e);
        } catch (Throwable e) {
            LOGGER.error("Error from mod '{}'", modId, e);
            modScreenErrors.put(modId, e);
        }
    }
}

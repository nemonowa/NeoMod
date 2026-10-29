package neo.menu.gui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import neo.menu.mod.Mod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import neo.menu.service.ModTexts;
import neo.util.NeoAssets;

@NullMarked
public class ModListEntry extends ObjectSelectionList.Entry<ModListEntry> {
    private static final Identifier MOD_CONFIGURATION_ICON = Identifier.fromNamespaceAndPath(NeoAssets.NAMESPACE, "textures/gui/mod_configuration.png");
    private static final Identifier ERROR_ICON = Identifier.withDefaultNamespace("world_list/error");
    private static final Identifier ERROR_HIGHLIGHTED_ICON = Identifier.withDefaultNamespace("world_list/error_highlighted");

    protected final Minecraft client;
    public final Mod mod;
    protected final ModListWidget list;
    protected @Nullable Identifier iconLocation;
    protected static final int FULL_ICON_SIZE = 32;
    /** 行のアイコンに重ねる画像 (設定・親 Mod の開閉) の 1 セルの大きさ (px)。FULL_ICON_SIZE の枠に引き伸ばして描く。 */
    protected static final int OVERLAY_CELL_SIZE = 16;
    protected long sinceLastClick;
    protected int yOffset = 0;

    public ModListEntry(Mod mod, ModListWidget list) {
        this.mod = mod;
        this.list = list;
        this.client = Minecraft.getInstance();
    }

    @Override
    public Component getNarration() {
        return Component.literal(ModTexts.name(mod));
    }

    @Override
    public void extractContent(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            boolean hovered,
            float delta
    ) {
        int x = this.getX() + this.getXOffset();
        int y = this.getContentY() + this.getYOffset();
        int rowWidth = this.getContentWidth();
//        int rowHeight = this.getContentHeight();
        int iconSize = FULL_ICON_SIZE;
        String modId = mod.getId();
        if ("java".equals(modId)) {
            DrawingUtil.drawJavaBackground(graphics, x, y, iconSize, iconSize);
        }

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                this.getIconTexture(),
                x,
                y,
                0.0F,
                0.0F,
                iconSize,
                iconSize,
                iconSize,
                iconSize,
                ARGB.white(1.0F)
        );

        Component name = Component.literal(ModTexts.name(mod));
        FormattedText trimmedName = name;
        int maxNameWidth = rowWidth - iconSize - 3;
        Font font = this.client.font;
        if (font.width(name) > maxNameWidth) {
            FormattedText ellipsis = FormattedText.of("...");
            trimmedName = FormattedText.composite(font.substrByWidth(name, maxNameWidth - font.width(ellipsis)), ellipsis);
        }

        graphics.text(font,
                Language.getInstance().getVisualOrder(trimmedName),
                x + iconSize + 3,
                y + 1,
                0xFFFFFFFF
        );

        new ModBadgeRenderer(
                x + iconSize + 3 + font.width(name) + 2,
                y,
                x + rowWidth,
                mod
        ).draw(graphics, mouseX, mouseY);

        String summary = ModTexts.summary(mod);
        DrawingUtil.drawWrappedString(
                graphics,
                summary,
                (x + iconSize + 3 + 4),
                (y + client.font.lineHeight + 2),
                rowWidth - iconSize - 7,
                2,
                0xFF808080
        );

        if (!(this instanceof ParentEntry) && (this.list.getParent().getModHasConfigScreen(modId) || this.list.getParent().hasConfigScreenError(modId))) {
            if (hovered) {
                graphics.fill(x, y, x + iconSize, y + iconSize, -1601138544);
                boolean hoveringIcon = mouseX - x < iconSize;
                if (this.list.getParent().hasConfigScreenError(modId)) {
                    graphics.blitSprite(
                            RenderPipelines.GUI_TEXTURED,
                            hoveringIcon ? ERROR_HIGHLIGHTED_ICON : ERROR_ICON,
                            x,
                            y,
                            iconSize,
                            iconSize
                    );
                } else {
                    int v = hoveringIcon ? OVERLAY_CELL_SIZE : 0;
                    graphics.blit(
                            RenderPipelines.GUI_TEXTURED,
                            MOD_CONFIGURATION_ICON,
                            x,
                            y,
                            0.0F,
                            (float) v,
                            iconSize,
                            iconSize,
                            OVERLAY_CELL_SIZE,
                            OVERLAY_CELL_SIZE,
                            OVERLAY_CELL_SIZE,
                            OVERLAY_CELL_SIZE * 2,
                            ARGB.white(1.0F)
                    );
                }
                if (hoveringIcon) {
                    graphics.requestCursor(this.shouldTakeFocusAfterInteraction() ? CursorTypes.POINTING_HAND : CursorTypes.NOT_ALLOWED);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        list.select(this);
        if (this.list.getParent().getModHasConfigScreen(this.mod.getId())) {
            int iconSize = FULL_ICON_SIZE;
            if (click.x() - list.getRowLeft() <= iconSize) {
                this.openConfig();
            } else if (Util.getMillis() - this.sinceLastClick < 250) {
                this.openConfig();
            }
        }

        this.sinceLastClick = Util.getMillis();
        return true;
    }

    public void openConfig() {
        this.list.getParent().safelyOpenConfigScreen(mod.getId());
    }

    public Mod getMod() {
        return mod;
    }

    public Identifier getIconTexture() {
        if (this.iconLocation == null) {
            this.iconLocation = Identifier.fromNamespaceAndPath(NeoAssets.NAMESPACE, mod.getId() + "_icon");
            DynamicTexture icon = mod.getIcon(list.getIconHandler(), (int) Math.ceil(FULL_ICON_SIZE * this.client.getWindow().getGuiScale()));
            this.client.getTextureManager().register(this.iconLocation, icon);
        }

        return iconLocation;
    }

    public void updatePlacement(int leftX, int width, int y) {
        this.setX(leftX);
        this.setWidth(width);
        this.setY(y);
    }

    public int getXOffset() {
        return 0;
    }

    public void setYOffset(int offset) {
        this.yOffset = offset;
    }

    public int getYOffset() {
        return this.yOffset;
    }
}

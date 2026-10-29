package neo.menu.gui;


import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import neo.menu.mod.Mod;

public class ModBadgeRenderer {
    protected int startX, startY, badgeX, badgeY, badgeMax;
    protected Mod mod;
    protected Minecraft client;

    public ModBadgeRenderer(int startX, int startY, int endX, Mod mod) {
        this.startX = startX;
        this.startY = startY;
        this.badgeMax = endX;
        this.mod = mod;
        this.client = Minecraft.getInstance();
    }

    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.badgeX = startX;
        this.badgeY = startY;
        Set<Mod.Badge> badges = mod.getBadges();
        badges.forEach(badge -> drawBadge(graphics, badge, mouseX, mouseY));
    }

    public void drawBadge(GuiGraphicsExtractor graphics, Mod.Badge badge, int mouseX, int mouseY) {
        this.drawBadge(
                graphics,
                badge.getText().getVisualOrderText(),
                badge.getOutlineColor(),
                badge.getFillColor(),
                mouseX,
                mouseY
        );
    }

    public void drawBadge(
            GuiGraphicsExtractor graphics,
            FormattedCharSequence text,
            int outlineColor,
            int fillColor,
            int mouseX,
            int mouseY
    ) {
        int width = client.font.width(text) + 6;
        if (badgeX + width < badgeMax) {
            DrawingUtil.drawBadge(graphics, badgeX, badgeY, width, text, outlineColor, fillColor, 0xFFCACACA);
            badgeX += width + 3;
        }
    }

    public Mod getMod() {
        return mod;
    }
}

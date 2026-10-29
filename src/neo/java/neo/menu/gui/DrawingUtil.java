package neo.menu.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class DrawingUtil {
    private static final Minecraft CLIENT = Minecraft.getInstance();

    /** Java のアイコンの背景 (赤の単色。固定)。 */
    public static void drawJavaBackground(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height
    ) {
        graphics.fill(x, y, x + width, y + height, 0xFFDD5656);
    }

    public static void drawWrappedString(
            GuiGraphicsExtractor graphics,
            String string,
            int x,
            int y,
            int wrapWidth,
            int lines,
            int color
    ) {
        while (string != null && string.endsWith("\n")) {
            string = string.substring(0, string.length() - 1);
        }

        List<FormattedText> strings = CLIENT.font.getSplitter().splitLines(Component.literal(string), wrapWidth, Style.EMPTY);
        for (int i = 0; i < strings.size(); i++) {
            if (i >= lines) {
                break;
            }

            FormattedText renderable = strings.get(i);
            if (i == lines - 1 && strings.size() > lines) {
                renderable = FormattedText.composite(strings.get(i), FormattedText.of("..."));
            }

            FormattedCharSequence line = Language.getInstance().getVisualOrder(renderable);
            int x1 = x;
            if (CLIENT.font.isBidirectional()) {
                x1 += wrapWidth - CLIENT.font.width(line);
            }

            graphics.text(CLIENT.font, line, x1, y + i * CLIENT.font.lineHeight, color);
        }
    }

    public static void drawBadge(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int tagWidth,
            FormattedCharSequence text,
            int outlineColor,
            int fillColor,
            int textColor
    ) {
        graphics.fill(x + 1, y - 1, x + tagWidth, y, outlineColor);
        graphics.fill(x, y, x + 1, y + CLIENT.font.lineHeight, outlineColor);
        graphics.fill(x + 1,
                y + 1 + CLIENT.font.lineHeight - 1,
                x + tagWidth,
                y + CLIENT.font.lineHeight + 1,
                outlineColor
        );
        graphics.fill(x + tagWidth, y, x + tagWidth + 1, y + CLIENT.font.lineHeight, outlineColor);
        graphics.fill(x + 1, y, x + tagWidth, y + CLIENT.font.lineHeight, fillColor);
        graphics.text(CLIENT.font,
                text,
                (int) (x + 1 + (tagWidth - CLIENT.font.width(text)) / (float) 2),
                y + 1,
                textColor,
                false
        );
    }
}

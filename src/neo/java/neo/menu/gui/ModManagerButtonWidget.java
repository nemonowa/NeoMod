package neo.menu.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

public class ModManagerButtonWidget extends Button.Plain {
    public ModManagerButtonWidget(int x, int y, int width, int height, net.minecraft.network.chat.Component text, Screen screen) {
        super(
                x,
                y,
                width,
                height,
                text,
                button -> Minecraft.getInstance().gui.setScreen(new ModsScreen(screen)),
                Button.DEFAULT_NARRATION
        );
    }
}

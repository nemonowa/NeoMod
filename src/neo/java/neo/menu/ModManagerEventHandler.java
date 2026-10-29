package neo.menu;

import com.mojang.blaze3d.platform.InputConstants;
import neo.event.NeoClientEvents;
import neo.event.NeoKeyMappings;
import neo.util.ScreenWidgets;
import neo.menu.config.ModManagerConfig;
import neo.menu.gui.ModManagerOptionsScreen;
import neo.menu.gui.ModManagerButtonWidget;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Arrays;
import java.util.List;
import neo.menu.service.ModsButtonText;

public class ModManagerEventHandler {
    private static KeyMapping MENU_KEY_MAPPING;

    public static void register() {
        MENU_KEY_MAPPING = NeoKeyMappings.register(new KeyMapping(
                "key.modmenu.open_menu",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_LBRACKET, // JIS 配列の「@」キー (GLFW 上の物理位置は US 配列の「[」)
                NeoKeyMappings.MAIN
        ));
        NeoClientEvents.registerEndClientTick(ModManagerEventHandler::onClientEndTick);
        NeoClientEvents.registerAfterScreenInit(ModManagerEventHandler::afterScreenInit);
    }

    public static void afterScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (screen instanceof TitleScreen) {
            afterTitleScreenInit(screen);
        }
    }

    private static void afterTitleScreenInit(Screen screen) {
        final List<AbstractWidget> buttons = ScreenWidgets.getWidgets(screen);
        if (ModManagerConfig.MODIFY_TITLE_SCREEN.getValue()) {
            final ModManagerConfig.TitleMenuButtonStyle style = ModManagerConfig.MODS_BUTTON_STYLE.getValue();
            int modsButtonIndex = -1;
            final int spacing = 24;
            int buttonsY = screen.height / 4 + 48;
            for (int i = 0; i < buttons.size(); i++) {
                AbstractWidget widget = buttons.get(i);
                if (widget instanceof Button button) {
                    if (style == ModManagerConfig.TitleMenuButtonStyle.CLASSIC) {
                        if (button.visible) {
                            shiftButtons(button, modsButtonIndex == -1, spacing);
                            if (modsButtonIndex == -1) {
                                buttonsY = button.getY();
                            }
                        }
                    }

                    if (buttonHasText(button, "menu.online")) {
                        if (style == ModManagerConfig.TitleMenuButtonStyle.REPLACE_REALMS) {
                            buttons.set(i, new ModManagerButtonWidget(
                                    button.getX(),
                                    button.getY(),
                                    button.getWidth(),
                                    button.getHeight(),
                                    ModsButtonText.create(),
                                    screen
                            ));
                        } else {
                            if (style == ModManagerConfig.TitleMenuButtonStyle.SHRINK) {
                                button.setWidth(98);
                            }

                            modsButtonIndex = i + 1;
                            if (button.visible) {
                                buttonsY = button.getY();
                            }
                        }
                    }
                }
            }

            if (modsButtonIndex != -1) {
                if (style == ModManagerConfig.TitleMenuButtonStyle.CLASSIC) {
                    buttons.add(modsButtonIndex, new ModManagerButtonWidget(
                            screen.width / 2 - 100,
                            buttonsY + spacing,
                            200,
                            20,
                            ModsButtonText.create(),
                            screen
                    ));
                } else if (style == ModManagerConfig.TitleMenuButtonStyle.SHRINK) {
                    buttons.add(modsButtonIndex,
                            new ModManagerButtonWidget(
                                    screen.width / 2 + 2,
                                    buttonsY,
                                    98,
                                    20,
                                    ModsButtonText.create(),
                                    screen
                            )
                    );
                }
            }
        }
    }

    private static void onClientEndTick(Minecraft client) {
        while (MENU_KEY_MAPPING.consumeClick()) {
            client.gui.setScreen(new ModManagerOptionsScreen(client.gui.screen()));
        }
    }

    public static boolean buttonHasText(LayoutElement widget, String... translationKeys) {
        if (widget instanceof Button button) {
            Component text = button.getMessage();
            ComponentContents textContent = text.getContents();
            return textContent instanceof TranslatableContents && Arrays.stream(translationKeys).anyMatch(s -> ((TranslatableContents) textContent).getKey().equals(s));
        } else {
            return false;
        }
    }

    public static void shiftButtons(LayoutElement widget, boolean shiftUp, int spacing) {
        if (shiftUp) {
            widget.setY(widget.getY() - spacing / 2);
        } else if (!(widget instanceof AbstractWidget button && button.getMessage().equals(Component.translatable("title.credits")))) {
            widget.setY(widget.getY() + spacing / 2);
        }
    }
}

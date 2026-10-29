package neo.menu.gui;

import neo.menu.config.ModManagerConfig;
import neo.menu.config.ModManagerConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ModManagerOptionsScreen extends OptionsSubScreen {
    public ModManagerOptionsScreen(Screen previous) {
        super(previous, Minecraft.getInstance().options, Component.translatable("modmenu.options"));
    }

    @Override
    protected void addOptions() {
        if (this.list != null) {
            this.list.addSmall(ModManagerConfig.asOptions());
        }
    }

    /** フッター: 「既定に戻す」と「完了」を横に並べる。 */
    @Override
    protected void addFooter() {
        LinearLayout footer = LinearLayout.horizontal().spacing(8);
        footer.addChild(Button.builder(Component.translatable("modmenu.options.reset"), button -> {
            ModManagerConfig.resetToDefaults();
            // 新しい設定画面に開き直して、各ボタンの表示を既定値にそろえる (保存は画面を閉じるとき)。
            // rebuildWidgets() は使わない: OptionsSubScreen#init は final の layout にタイトル・一覧・フッターを
            // 毎回「追加」するだけで消さないので、呼ぶと古いものが残って UI が重なる。
            this.minecraft.gui.setScreen(new ModManagerOptionsScreen(this.lastScreen));
        }).width(120).build());
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(120).build());
        this.layout.addToFooter(footer);
    }

    @Override
    public void removed() {
        ModManagerConfigManager.save();
    }
}

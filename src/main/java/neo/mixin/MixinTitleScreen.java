package neo.mixin;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import neo.menu.config.ModManagerConfig;
import neo.menu.gui.SmallModManagerButtonWidget;
import neo.util.ScreenWidgets;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import neo.menu.service.ModCount;


@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen {
    @Shadow
    protected abstract int getHorizontalPosition(int currentButton, int numberOfButtons, int buttonWidth);

    @ModifyArg(at = @At(value = "INVOKE", target = "Lcom/mojang/realmsclient/gui/screens/RealmsNotificationsScreen;init(II)V"), method = "init", index = 1)
    private int adjustRealmsHeight(int height) {
        if (ModManagerConfig.MODIFY_TITLE_SCREEN.getValue() && ModManagerConfig.MODS_BUTTON_STYLE.getValue() == ModManagerConfig.TitleMenuButtonStyle.CLASSIC) {
            return height - 51;
        } else if (ModManagerConfig.MODS_BUTTON_STYLE.getValue() == ModManagerConfig.TitleMenuButtonStyle.REPLACE_REALMS || ModManagerConfig.MODS_BUTTON_STYLE.getValue() == ModManagerConfig.TitleMenuButtonStyle.SHRINK) {
            return -99999;
        } else {
            return height;
        }
    }

    @Definition(id = "numberOfButtons", local = @Local(type = int.class, name = "numberOfButtons"))
    @Expression("numberOfButtons = ?")
    @Inject(method = "init", at = @At(value = "MIXINEXTRAS:EXPRESSION", shift = At.Shift.AFTER))
    private void adjustAmountOfIconButtons(CallbackInfo ci, @Local(name = "numberOfButtons") LocalIntRef numberOfButtons, @Share("addModManagerIconWidget") LocalBooleanRef addModManagerIconWidget) {
        if (ModManagerConfig.MODIFY_TITLE_SCREEN.getValue()) { // 設定画面を開くアイコンと Discord のアイコンは、Mods ボタンの形式に関係なく常に出す (アイコン 2 個ぶん)
            addModManagerIconWidget.set(true);
            numberOfButtons.set(numberOfButtons.get() + 2);
        }
    }

    @WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/TitleScreen;getHorizontalPosition(III)I"))
    private int replaceInlinedConstant(TitleScreen instance, int currentButton, int numberOfButtons, int buttonWidth, Operation<Integer> original, @Local(name = "numberOfButtons") int actualNumberOfButtons) {
        return original.call(instance, currentButton, actualNumberOfButtons, buttonWidth);
    }

    @Definition(id = "width", field = "Lnet/minecraft/client/gui/screens/TitleScreen;width:I")
    @Expression("this.width / 2 - 100")
    @Inject(method = "init", at = @At(value = "MIXINEXTRAS:EXPRESSION", ordinal = 0))
    private void addModManagerIconWidget(CallbackInfo ci, @Local(name = "currentButton") LocalIntRef currentButton, @Local(name = "topPos") int topPos, @Local(name = "numberOfButtons") int numberOfButtons, @Share("addModManagerIconWidget") LocalBooleanRef addModManagerIconWidget) {
        if (!addModManagerIconWidget.get()) return;
        currentButton.set(currentButton.get()+1);
        Screen screen = (TitleScreen) (Object) this;
        ScreenWidgets.getWidgets(screen).add(SmallModManagerButtonWidget.createModsIconButton(
                this.getHorizontalPosition(currentButton.get(), numberOfButtons, 20),
                topPos,
                screen
        ));
        currentButton.set(currentButton.get()+1);
        ScreenWidgets.getWidgets(screen).add(SmallModManagerButtonWidget.createDiscordIconButton(
                this.getHorizontalPosition(currentButton.get(), numberOfButtons, 20),
                topPos,
                screen
        ));
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/language/I18n;get(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"))
    String modifyModdedVersionString(String id, Object[] args, Operation<String> original) {
        if (ModManagerConfig.MODIFY_TITLE_SCREEN.getValue()) { // Mod 数はタイトル画面にも出す (固定)
            String count = ModCount.getDisplayed();
            String specificKey = "modmenu.mods." + count;
            String replacementKey = Language.getInstance().has(specificKey) ? specificKey : "modmenu.mods.n";
            if (Language.getInstance().has(specificKey + ".secret")) {
                replacementKey = specificKey + ".secret";
            }

            return I18n.get(replacementKey, count);
        }

        return original.call(id, args);
    }
}

package neo.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import neo.advancements.AdvancementsLayout;
import neo.advancements.AdvancementsWindow;
import neo.advancements.CloseTab;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 進捗画面の窓を画面いっぱいまで広げ、上部の見出し「進捗」と下部の「完了」ボタンを出さない (設定: Mod Menu の large_advancements)。
 * バニラは 252x140 (枠) / 234x113 (表示域) を数値のまま埋め込んでいるので、その数値を差し替える。
 * 数値の個数は require で固定してあり、Minecraft 更新で変わると起動時に失敗して気付ける。
 */
@Mixin(AdvancementsScreen.class)
public abstract class MixinAdvancementsScreen {
    @Shadow private int leftPos;
    @Shadow private int topPos;

    // 窓の位置 (中央寄せ) と、右クリックでのタブ判定。枠の描画は下の WrapOperation で置き換える
    @ModifyConstant(method = {"repositionElements", "mouseClicked"}, constant = @Constant(intValue = 252), require = 2)
    private int neo$windowWidth(int original) {
        return AdvancementsLayout.windowWidth(original);
    }

    @ModifyConstant(method = {"repositionElements", "mouseClicked"}, constant = @Constant(intValue = 140), require = 2)
    private int neo$windowHeight(int original) {
        return AdvancementsLayout.windowHeight(original);
    }

    // 「進捗がありません」表示時の黒い背景と文言の位置
    @ModifyConstant(method = "extractInside", constant = @Constant(intValue = 234), require = 1)
    private int neo$contentWidth(int original) {
        return AdvancementsLayout.contentWidth(original);
    }

    @ModifyConstant(method = "extractInside", constant = @Constant(intValue = 113), require = 2)
    private int neo$contentHeight(int original) {
        return AdvancementsLayout.contentHeight(original);
    }

    @ModifyConstant(method = "extractInside", constant = @Constant(intValue = 117), require = 1)
    private int neo$halfContentWidth(int original) {
        return AdvancementsLayout.halfContentWidth(original);
    }

    @ModifyConstant(method = "extractInside", constant = @Constant(intValue = 56), require = 1)
    private int neo$halfContentHeight(int original) {
        return AdvancementsLayout.halfContentHeight(original);
    }

    // 窓枠の描画: 固定サイズの blit を、引き伸ばし描画に置き換える
    @WrapOperation(
        method = "extractWindow",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"
        ),
        require = 1
    )
    private void neo$drawStretchedWindow(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture,
                                            int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight,
                                            Operation<Void> original) {
        if (!AdvancementsLayout.enabled()) {
            original.call(graphics, pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
            return;
        }
        AdvancementsWindow.draw(graphics, pipeline, texture, x, y, AdvancementsLayout.windowWidth(), AdvancementsLayout.windowHeight());
    }

    // 上部の見出し「進捗」(ヘッダーのタイトル) を出さない。窓の左上の「進捗」(extractWindow) は残る
    @WrapOperation(
        method = "init",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addTitleHeader(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/Font;)V"
        ),
        require = 1
    )
    private void neo$skipTitleHeader(HeaderAndFooterLayout layout, Component title, Font font, Operation<Void> original) {
        if (!AdvancementsLayout.enabled()) {
            original.call(layout, title, font);
        }
    }

    // 下部の「完了」ボタンを出さない (戻り値はバニラ側で捨てられるので null でよい)
    @WrapOperation(
        method = "init",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addToFooter(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"
        ),
        require = 1
    )
    private LayoutElement neo$skipDoneButton(HeaderAndFooterLayout layout, LayoutElement element, Operation<LayoutElement> original) {
        return AdvancementsLayout.enabled() ? null : original.call(layout, element);
    }

    // 右下の「閉じる」タブ (エンチャント付きバリアブロック): 描画・ツールチップ・クリック
    @Inject(method = "extractWindow", at = @At("TAIL"), require = 1)
    private void neo$drawCloseTab(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        CloseTab.extract(graphics, this.leftPos, this.topPos, mouseX, mouseY);
    }

    @Inject(method = "extractTooltips", at = @At("TAIL"), require = 1)
    private void neo$closeTabTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        CloseTab.extractTooltip(graphics, this.leftPos, this.topPos, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 1)
    private void neo$clickCloseTab(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (CloseTab.click(this.leftPos, this.topPos, event.x(), event.y())) {
            ((AdvancementsScreen) (Object) this).onClose();
            cir.setReturnValue(true);
        }
    }
}

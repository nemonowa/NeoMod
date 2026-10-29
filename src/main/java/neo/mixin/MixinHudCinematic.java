package neo.mixin;

import neo.cinematic.AfkCinematic;
import neo.cinematic.CinematicLetterbox;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AFK シネマティック: HUD の描画の先頭にレターボックス (上下の黒帯) を、末尾にチャットを足す。
 * 黒帯は HUD の下に描く (GUI が出ている間 (黒帯が消えていく途中など) も、ホットバーなどが黒帯に隠れない)。
 * シネマティック中の HUD は F1 と同じ仕組み (Hud#toggle) で隠してあるので、チャットだけをここで描き直す。
 */
@Mixin(Hud.class)
public abstract class MixinHudCinematic {
    @Shadow
    protected abstract void extractChat(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker);

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("HEAD"),
        require = 1
    )
    private void neo$cinematicLetterbox(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        CinematicLetterbox.render(graphics);
    }

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("TAIL"),
        require = 1
    )
    private void neo$cinematicChat(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (AfkCinematic.showsChat()) {
            this.extractChat(graphics, deltaTracker);
        }
    }
}

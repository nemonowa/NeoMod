package neo.mixin;

import neo.event.NeoClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import neo.util.ScreenInternals;
import java.util.List;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import org.spongepowered.asm.mixin.Final;

/**
 * ScreenEvents.AFTER_INIT の代替。ウィジェットが新しく作られた直後(初回 init と rebuildWidgets の末尾)にだけ発火する。
 * リサイズ時は repositionElements → rebuildWidgets 経由で発火する。
 */
@Mixin(Screen.class)
public abstract class MixinScreen implements ScreenInternals {
    @Shadow private boolean initialized;
    @Shadow public int width;
    @Shadow public int height;
    @Shadow @Final private List<GuiEventListener> children;
    @Shadow @Final private List<Renderable> renderables;
    @Shadow @Final private List<NarratableEntry> narratables;
    @Unique private boolean neo$wasInitialized;

    @Inject(method = "init(II)V", at = @At("HEAD"), require = 1)
    private void neo$initHead(int w, int h, CallbackInfo ci) {
        neo$wasInitialized = this.initialized;
    }

    @Inject(method = "init(II)V", at = @At("RETURN"), require = 1)
    private void neo$initReturn(int w, int h, CallbackInfo ci) {
        if (!neo$wasInitialized) {
            NeoClientEvents.fireAfterScreenInit(Minecraft.getInstance(), (Screen) (Object) this, w, h);
        }
    }

    @Inject(method = "rebuildWidgets", at = @At("RETURN"), require = 1)
    private void neo$rebuildReturn(CallbackInfo ci) {
        NeoClientEvents.fireAfterScreenInit(Minecraft.getInstance(), (Screen) (Object) this, this.width, this.height);
    }

    @Unique @Override public List<GuiEventListener> neo$getChildren() { return this.children; }

    @Unique @Override public List<Renderable> neo$getRenderables() { return this.renderables; }

    @Unique @Override public List<NarratableEntry> neo$getNarratables() { return this.narratables; }
}

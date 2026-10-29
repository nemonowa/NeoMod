package neo.mixin;

import neo.event.NeoClientEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** LevelExtractionEvents.END_EXTRACTION の代替。 */
@Mixin(LevelExtractor.class)
public abstract class MixinLevelExtractor {
    @Inject(method = "extract(Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/Camera;F)V", at = @At("RETURN"), require = 1)
    private void neo$endExtraction(DeltaTracker deltaTracker, Camera camera, float partialTick, CallbackInfo ci) {
        NeoClientEvents.fireEndExtraction();
    }
}

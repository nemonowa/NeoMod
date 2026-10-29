package neo.mixin;

import neo.map.HeldMapView;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ワールドマップ: 手持ちの地図の「中身の画像」だけを、自前の高解像度のテクスチャに差し替える
 * (バニラの地図は 128x128 ピクセル固定で、1 ブロック 1 ドットだと 128 ブロックしか見えない)。
 * バニラの描画は、MapRenderState の texture を四角に貼るだけなので、テクスチャの大きさは何でもよい。
 * 装飾・紙の枠・両腕はバニラのまま。
 */
@Mixin(MapRenderer.class)
public abstract class MixinMapRenderer {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/level/saveddata/maps/MapId;Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;Lnet/minecraft/client/renderer/state/MapRenderState;)V",
        at = @At("TAIL"), require = 1)
    private void neo$heldMapTexture(MapId mapId, MapItemSavedData mapData, MapRenderState state, CallbackInfo ci) {
        HeldMapView.overrideTexture(mapId, state);
    }
}

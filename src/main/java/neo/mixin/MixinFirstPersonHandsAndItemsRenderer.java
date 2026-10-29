package neo.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import neo.map.HeldMapView;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ワールドマップ: 両手持ちの地図の傾き (視線の上下で変わる) を 0 にして、地図を常に正面に向ける。
 * こうすると地図の画面上の位置が一定になり、地図の上のクリック位置を計算できる (HandMapGeometry)。
 * 全画面のときは、地図の下地 (紙の枠 + チェッカー) と、両腕も描かない (地図の画像だけを描く)。
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class MixinFirstPersonHandsAndItemsRenderer {
    @ModifyReturnValue(method = "calculateMapTilt(F)F", at = @At("RETURN"), require = 1)
    private float neo$flatMap(float tilt) {
        return HeldMapView.isActive() ? 0.0F : tilt;
    }

    /** 地図の下地 (紙の枠 + チェッカー)。全画面のときは描かない (地図の画像だけにする)。 */
    @WrapOperation(
        method = "renderMap(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/item/ItemStack;ZLnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V", ordinal = 0),
        require = 1
    )
    private void neo$noBackground(SubmitNodeCollector collector, PoseStack pose, RenderType type,
                                  SubmitNodeCollector.CustomGeometryRenderer vanilla, Operation<Void> original) {
        if (!HeldMapView.isFullscreen()) {
            original.call(collector, pose, type, vanilla);
        }
    }

    /** 両手持ちの地図の両腕 (2 回呼ばれる)。全画面のときは描かない。 */
    @WrapOperation(
        method = "renderTwoHandedMap(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFFLnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;renderMapHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;)V"),
        require = 2
    )
    private void neo$noArmsInFullscreen(FirstPersonHandsAndItemsRenderer self, PoseStack pose, SubmitNodeCollector collector, int light,
                                        HumanoidArm arm, PlayerRenderState player, Operation<Void> original) {
        if (!HeldMapView.isFullscreen()) {
            original.call(self, pose, collector, light, arm, player);
        }
    }
}

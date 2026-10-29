package neo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import neo.skinlayers.SkinLayerMesh;
import neo.skinlayers.SkinLayerPart;
import neo.skinlayers.SkinLayers;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 3D スキンレイヤー: ModelPart に立体メッシュを持たせ、持っているパーツはバニラの 2D の代わりにメッシュで描く。
 * メッシュが無い (null) ときは何もせずバニラの描画へ進む (全パーツの描画ごとに null 比較 1 回)。
 * 元の処理を呼び戻せる @WrapMethod にしないのは、全パーツ・毎フレームの呼び出しで Operation の割り当てが発生するのを避けるため (P-42)。
 */
@Mixin(ModelPart.class)
public abstract class MixinModelPartSkinLayer implements SkinLayerPart {
    @Shadow
    @Final
    private List<ModelPart.Cube> cubes;

    @Shadow
    @Final
    private Map<String, ModelPart> children;

    @Shadow
    public boolean visible;

    @Shadow
    public boolean skipDraw;

    @Shadow
    public abstract void translateAndRotate(PoseStack poseStack);

    @Unique
    private SkinLayerMesh neoSkinLayer;

    @Override
    public SkinLayerMesh neoGetSkinLayer() {
        return neoSkinLayer;
    }

    @Override
    public void neoSetSkinLayer(SkinLayerMesh mesh) {
        this.neoSkinLayer = mesh;
    }

    @Override
    public List<ModelPart.Cube> neoGetCubes() {
        return cubes;
    }

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void neo$renderSkinLayer(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color,
            CallbackInfo ci) {
        SkinLayerMesh mesh = this.neoSkinLayer;
        if (mesh == null || !this.visible || !SkinLayers.enabled()) {
            return; // バニラの描画のまま
        }

        poseStack.pushPose();
        this.translateAndRotate(poseStack);
        if (!this.skipDraw) {
            mesh.render(poseStack.last(), consumer, light, overlay, color);
        }
        // 子パーツはバニラと同じく、このパーツの位置・回転の下で描く
        for (ModelPart child : this.children.values()) {
            child.render(poseStack, consumer, light, overlay, color);
        }
        poseStack.popPose();
        ci.cancel();
    }
}

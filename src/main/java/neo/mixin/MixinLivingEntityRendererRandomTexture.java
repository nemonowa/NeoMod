package neo.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import neo.entitytex.RandomEntityTextures;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 生き物の本体の絵を、ランダムエンティティのルールで差し替える (neo.entitytex.RandomEntityTextures)。 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRendererRandomTexture {
    @WrapOperation(method = "getRenderType", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getTextureLocation(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Lnet/minecraft/resources/Identifier;"),
        require = 1)
    private Identifier neo$randomTexture(LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state, Operation<Identifier> original) {
        return RandomEntityTextures.choose(original.call(renderer, state), state);
    }
}

package neo.mixin;

import neo.entitytex.EntityConditions;
import neo.entitytex.NamedRenderState;
import neo.menu.config.ModManagerConfig;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 描画用データを作る時に、名前と個体の ID を写し、初めて見た個体なら状態の写しも作る (ランダムエンティティの絵。機能が OFF の時は空にする)。 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererRandomTexture {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
        at = @At("TAIL"), require = 1)
    private void neo$copyName(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        NamedRenderState named = (NamedRenderState) state;
        if (!ModManagerConfig.RANDOM_ENTITY_TEXTURES.getValue()) {
            named.neo$setEntity(null, null);
            return;
        }
        Component name = entity.getCustomName();
        named.neo$setEntity(name != null ? name.getString() : null, entity.getUUID());
        EntityConditions.capture(entity);
    }
}

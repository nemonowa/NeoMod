package neo.mixin;

import neo.entitytex.RandomEntityTextures;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** リソースの再読み込みで、ランダムエンティティの設定の覚え書きを捨てる。 */
@Mixin(ReloadableResourceManager.class)
public abstract class MixinReloadableResourceManager {
    @Inject(method = "createReload", at = @At("HEAD"), require = 1)
    private void neo$clearRandomTextures(CallbackInfoReturnable<?> cir) {
        RandomEntityTextures.clear();
    }
}

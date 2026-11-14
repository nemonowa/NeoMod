package neo.mixin;

import neo.bundle.BuiltinPacks;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** PackRepository の作成時に、同梱パック (jar の resourcepacks/) の読み込み元を足す (neo.bundle.BuiltinPacks)。 */
@Mixin(PackRepository.class)
public abstract class MixinPackRepository {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
        target = "Lcom/google/common/collect/ImmutableSet;copyOf([Ljava/lang/Object;)Lcom/google/common/collect/ImmutableSet;"),
        require = 1)
    private static Object[] neo$addBundledPacks(Object[] sources) {
        return sources instanceof RepositorySource[] array ? BuiltinPacks.withBundled(array) : sources;
    }
}

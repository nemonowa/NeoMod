package neo.mixin;

import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import neo.spawnerinfo.SpawnerInfoMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ServerTickEvents.END_SERVER_TICK の代替。 */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {
    @Inject(method = "tickServer", at = @At("RETURN"), require = 1)
    private void neo$endServerTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        SpawnerInfoMod.onServerTick((MinecraftServer) (Object) this);
    }
}

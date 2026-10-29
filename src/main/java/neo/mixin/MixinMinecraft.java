package neo.mixin;

import neo.event.NeoClientEvents;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ClientTickEvents.END_CLIENT_TICK の代替。 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Inject(method = "tick", at = @At("RETURN"), require = 1)
    private void neo$endTick(CallbackInfo ci) {
        NeoClientEvents.fireEndClientTick((Minecraft) (Object) this);
    }
}

package neo.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import neo.mount.LegacyRide;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 旧版の騎乗位置: 人型 MOB の乗せ位置を「足元 + 高さ × 0.75」に置き換える (neo.mount.LegacyRide)。 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityLegacyRide {
    @ModifyReturnValue(method = "getPassengerRidingPosition(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), require = 1)
    private Vec3 neo$legacyRidingPosition(Vec3 original) {
        return LegacyRide.ridingPosition((LivingEntity) (Object) this, original);
    }
}

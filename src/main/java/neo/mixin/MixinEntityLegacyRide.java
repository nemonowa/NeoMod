package neo.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import neo.mount.LegacyRide;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 旧版の騎乗位置: 乗る側の VEHICLE アタッチメントを 1.10.2 の補正に置き換える (neo.mount.LegacyRide)。 */
@Mixin(Entity.class)
public abstract class MixinEntityLegacyRide {
    @ModifyReturnValue(method = "getVehicleAttachmentPoint(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), require = 1)
    private Vec3 neo$legacyVehicleAttachment(Vec3 original) {
        return LegacyRide.vehicleAttachment((Entity) (Object) this, original);
    }
}

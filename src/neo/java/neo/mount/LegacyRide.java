package neo.mount;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 人型 MOB に乗ったときの高さを 1.10.2 に合わせる (設定 legacy_ride_position)。
 * 1.10.2 は「乗る側の足元 = 乗り物の足元 + 乗り物の高さ × 0.75 + 乗る側の補正」
 * (補正はプレイヤー・大人のゾンビ・スケルトンが −0.35、ほかは 0)。
 * 26.3 は「乗り物の乗せ位置 − 乗る側の VEHICLE アタッチメント」で決まるので、両方をこの式に置き換える。
 * 乗り物はゾンビ系・スケルトン系・ウィッチ・エンダーマンだけ (ほかの乗り物はバニラのまま)。
 *
 * サーバー側の当たり判定とずれないよう、効くのは自分がサーバーのとき (シングルプレイ・LAN のホスト) だけ。
 * クライアントの tick が {@link #enabled} を更新し、同じ JVM の内蔵サーバーもこの値を見る。専用サーバーでは常に false。
 * このクラスはサーバー側からも呼ばれるので、クライアント専用のクラスを参照しない (P-63)。
 */
public final class LegacyRide {
    /** 1.10.2 の getMountedYOffset (乗り物の高さに掛ける割合)。 */
    private static final double MOUNTED_RATIO = 0.75;
    /** 1.10.2 の getYOffset (プレイヤー・大人のゾンビ・スケルトン)。 */
    private static final double RIDER_OFFSET = 0.35;

    public static volatile boolean enabled;

    private LegacyRide() {}

    /** 乗り物として高さを合わせる型か。 */
    static boolean isLegacyVehicle(Entity e) {
        return e instanceof Zombie || e instanceof AbstractSkeleton || e instanceof Witch || e instanceof Enderman;
    }

    /** 乗り物の乗せ位置 (LivingEntity#getPassengerRidingPosition の戻り値) を置き換える。 */
    public static Vec3 ridingPosition(LivingEntity vehicle, Vec3 original) {
        if (!enabled || !isLegacyVehicle(vehicle)) {
            return original;
        }

        return new Vec3(original.x, vehicle.getY() + vehicle.getBbHeight() * MOUNTED_RATIO, original.z);
    }

    /** 乗る側の VEHICLE アタッチメント (Entity#getVehicleAttachmentPoint の戻り値) を置き換える。 */
    public static Vec3 vehicleAttachment(Entity rider, Vec3 original) {
        if (!enabled) {
            return original;
        }

        Entity vehicle = rider.getVehicle();
        if (vehicle == null || !isLegacyVehicle(vehicle)) {
            return original;
        }

        boolean offset = rider instanceof Player || rider instanceof AbstractSkeleton || (rider instanceof Zombie z && !z.isBaby());
        return new Vec3(original.x, offset ? RIDER_OFFSET : 0.0, original.z);
    }
}

package neo.mount;

import java.util.function.BooleanSupplier;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
 * 位置はサーバーとクライアントがそれぞれ計算するので、両方で値をそろえる。シングルプレイ・LAN のホストは
 * クライアントの設定を {@link #enabled()} で返し (同じ JVM の内蔵サーバーもこの値を見る)、専用サーバーは常に ON。
 * サーバーは参加時と設定が変わった時に {@link LegacyRidePayload} を送り、MOD 入りのクライアントはそれに従う。
 * MOD の無いクライアントはバニラの高さで描く。
 * このクラスはサーバー側からも呼ばれるので、クライアント専用のクラスを参照しない (P-63)。
 */
public final class LegacyRide {
    /** 1.10.2 の getMountedYOffset (乗り物の高さに掛ける割合)。 */
    private static final double MOUNTED_RATIO = 0.75;
    /** 1.10.2 の getYOffset (プレイヤー・大人のゾンビ・スケルトン)。 */
    private static final double RIDER_OFFSET = 0.35;

    /**
     * いま効いているか。専用サーバーは常に ON (クライアントが無いので、この既定のまま)。
     * クライアントは LegacyRideClient が「自分がサーバーなら設定、ほかのサーバーならそこから届いた値」を返す物に差し替える
     * (同じ JVM の内蔵サーバーもこれを見る)。毎 tick 写さずに、使う時に聞く (P-45)。
     */
    private static volatile BooleanSupplier source = () -> true;

    private LegacyRide() {}

    public static boolean enabled() {
        return source.getAsBoolean();
    }

    public static void setSource(BooleanSupplier supplier) {
        source = supplier;
    }

    public static void sendTo(ServerPlayer player) {
        player.connection.send(new ClientboundCustomPayloadPacket(new LegacyRidePayload(enabled())));
    }

    /** 値が変わった時に、参加者全員へ知らせる (サーバーのスレッドで呼ぶ)。 */
    public static void broadcast(MinecraftServer server) {
        server.getPlayerList().broadcastAll(new ClientboundCustomPayloadPacket(new LegacyRidePayload(enabled())));
    }

    /** 乗り物として高さを合わせる型か。 */
    static boolean isLegacyVehicle(Entity e) {
        return e instanceof Zombie || e instanceof AbstractSkeleton || e instanceof Witch || e instanceof Enderman;
    }

    /** 乗り物の乗せ位置 (LivingEntity#getPassengerRidingPosition の戻り値) を置き換える。 */
    public static Vec3 ridingPosition(LivingEntity vehicle, Vec3 original) {
        if (!isLegacyVehicle(vehicle) || !enabled()) {
            return original;
        }

        return new Vec3(original.x, vehicle.getY() + vehicle.getBbHeight() * MOUNTED_RATIO, original.z);
    }

    /** 乗る側の VEHICLE アタッチメント (Entity#getVehicleAttachmentPoint の戻り値) を置き換える。 */
    public static Vec3 vehicleAttachment(Entity rider, Vec3 original) {
        Entity vehicle = rider.getVehicle();
        if (vehicle == null || !isLegacyVehicle(vehicle) || !enabled()) {
            return original;
        }

        boolean offset = rider instanceof Player || rider instanceof AbstractSkeleton || (rider instanceof Zombie z && !z.isBaby());
        return new Vec3(original.x, offset ? RIDER_OFFSET : 0.0, original.z);
    }
}

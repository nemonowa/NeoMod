package neo.mount;

import neo.menu.config.ModManagerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/** 旧版の騎乗位置のクライアント側：自分がサーバーなら設定を、ほかのサーバーに入っている時はサーバーの値を {@link LegacyRide#enabled()} として返す。 */
public final class LegacyRideClient {
    /** 入っているサーバーから届いた値と、それを送ってきた接続。別のサーバーへ入り直したら古い値は使わない (MOD の無いサーバーでは届かない)。 */
    private static volatile boolean serverValue;
    private static volatile Object serverConnection;

    private LegacyRideClient() {}

    public static void init() {
        LegacyRide.setSource(LegacyRideClient::current);
        // 自分がサーバーの時に設定を変えたら、LAN の参加者へ知らせる (毎 tick 見張らない)
        ModManagerConfig.LEGACY_RIDE_POSITION.onChange(() -> {
            MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) {
                server.execute(() -> LegacyRide.broadcast(server));
            }
        });
    }

    public static void onPayload(LegacyRidePayload payload) {
        serverValue = payload.enabled();
        serverConnection = Minecraft.getInstance().getConnection();
    }

    /** 自分がサーバー (シングル・LAN のホスト) なら設定、ほかのサーバーならそこから届いた値。ワールドの外では OFF。 */
    private static boolean current() {
        Minecraft client = Minecraft.getInstance();
        if (client.hasSingleplayerServer()) {
            return ModManagerConfig.LEGACY_RIDE_POSITION.getValue();
        }
        Object connection = client.getConnection();
        return connection != null && connection == serverConnection && serverValue;
    }
}

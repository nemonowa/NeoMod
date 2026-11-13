package neo.mount;

import neo.event.NeoClientEvents;
import neo.menu.config.ModManagerConfig;
import net.minecraft.client.Minecraft;

/** 旧版の騎乗位置のクライアント側：自分がサーバーなら設定を、ほかのサーバーに入っている時はサーバーの値を {@link LegacyRide#enabled} へ写す。 */
public final class LegacyRideClient {
    /** 入っているサーバーから届いた値。MOD の無いサーバーでは届かず false のまま。 */
    private static volatile boolean serverValue;

    private LegacyRideClient() {}

    public static void init() {
        NeoClientEvents.registerEndClientTick(LegacyRideClient::onClientTick);
    }

    public static void onPayload(LegacyRidePayload payload) {
        serverValue = payload.enabled();
    }

    private static void onClientTick(Minecraft client) {
        if (client.getConnection() == null) {
            serverValue = false;
            LegacyRide.enabled = false;
        } else if (client.hasSingleplayerServer()) {
            LegacyRide.enabled = ModManagerConfig.LEGACY_RIDE_POSITION.getValue();
        } else {
            LegacyRide.enabled = serverValue;
        }
    }
}

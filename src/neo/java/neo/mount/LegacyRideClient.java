package neo.mount;

import neo.event.NeoClientEvents;
import neo.menu.config.ModManagerConfig;
import net.minecraft.client.Minecraft;

/** 旧版の騎乗位置のクライアント側：設定と「自分がサーバーか」を毎 tick {@link LegacyRide#enabled} へ写す。 */
public final class LegacyRideClient {
    private LegacyRideClient() {}

    public static void init() {
        NeoClientEvents.registerEndClientTick(LegacyRideClient::onClientTick);
    }

    private static void onClientTick(Minecraft client) {
        // ワールドを離れると内蔵サーバーが無くなり false に戻る。専用サーバー・LAN の参加者のときも false
        LegacyRide.enabled = client.hasSingleplayerServer() && ModManagerConfig.LEGACY_RIDE_POSITION.getValue();
    }
}

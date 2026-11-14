package neo.spawnerinfo;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** クライアント → サーバー: スポナー情報を画面に出し始めた (true) / やめた (false)。サーバーは出している人にだけ送る。MOD の無いサーバーは読み捨てる。 */
public record SpawnerSubscribePayload(boolean subscribe) implements CustomPacketPayload {
    public static final Type<SpawnerSubscribePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("neo", "spawner_subscribe"));
    public static final StreamCodec<FriendlyByteBuf, SpawnerSubscribePayload> CODEC =
        CustomPacketPayload.codec((payload, buf) -> buf.writeBoolean(payload.subscribe), buf -> new SpawnerSubscribePayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

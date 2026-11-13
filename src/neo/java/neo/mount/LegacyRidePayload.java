package neo.mount;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** サーバーの旧版の騎乗位置の ON/OFF をクライアントへ知らせる。MOD の無いクライアントは読み捨てる。 */
public record LegacyRidePayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<LegacyRidePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("neo", "legacy_ride"));
    public static final StreamCodec<FriendlyByteBuf, LegacyRidePayload> CODEC = CustomPacketPayload.codec(LegacyRidePayload::write, LegacyRidePayload::read);

    private static LegacyRidePayload read(FriendlyByteBuf buf) {
        return new LegacyRidePayload(buf.readBoolean());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package neo.map;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** サーバーが見つけた、プレイヤーの近くの構造物 (ワールドマップの目印)。 */
public record StructureMarkersPayload(String dimension, List<Entry> entries) implements CustomPacketPayload {
    public record Entry(String iconKey, int x, int z) {}

    /** 1 回に送る数の上限 (読む側で壊れた・大きすぎるデータを受け取らないため)。 */
    private static final int MAX_ENTRIES = 256;
    public static final Type<StructureMarkersPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("neo", "structure_markers"));
    public static final StreamCodec<FriendlyByteBuf, StructureMarkersPayload> CODEC = CustomPacketPayload.codec(StructureMarkersPayload::write, StructureMarkersPayload::read);

    private static StructureMarkersPayload read(FriendlyByteBuf buf) {
        String dimension = buf.readUtf();
        int count = Math.min(buf.readVarInt(), MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readUtf(), buf.readInt(), buf.readInt()));
        }
        return new StructureMarkersPayload(dimension, entries);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(dimension);
        int count = Math.min(entries.size(), MAX_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Entry entry = entries.get(i);
            buf.writeUtf(entry.iconKey());
            buf.writeInt(entry.x());
            buf.writeInt(entry.z());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

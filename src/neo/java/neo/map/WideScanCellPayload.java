package neo.map;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** サーバー → クライアント: 広域スキャンで色を付けた升目 1 枚 (128×128 の地図の色)。 */
public record WideScanCellPayload(ScanJob.CellMerge merge) implements CustomPacketPayload {
    private static final int COLORS = MapStore.SIZE * MapStore.SIZE;
    public static final Type<WideScanCellPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("neo", "wide_scan_cell"));
    public static final StreamCodec<FriendlyByteBuf, WideScanCellPayload> CODEC = CustomPacketPayload.codec(WideScanCellPayload::write, WideScanCellPayload::read);

    private static WideScanCellPayload read(FriendlyByteBuf buf) {
        Identifier dimension = buf.readIdentifier();
        int scale = buf.readByte();
        int cellX = buf.readInt();
        int cellZ = buf.readInt();
        byte[] colors = new byte[COLORS];
        buf.readBytes(colors);
        return new WideScanCellPayload(new ScanJob.CellMerge(ResourceKey.create(Registries.DIMENSION, dimension), scale, cellX, cellZ, colors));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeIdentifier(merge.dimension().identifier());
        buf.writeByte(merge.scale());
        buf.writeInt(merge.cellX());
        buf.writeInt(merge.cellZ());
        buf.writeBytes(merge.colors(), 0, COLORS);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

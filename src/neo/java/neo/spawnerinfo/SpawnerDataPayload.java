package neo.spawnerinfo;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SpawnerDataPayload(
   int entityId,
   int summonId,
   String customName,
   short delay,
   short minSpawnDelay,
   short maxSpawnDelay,
   short spawnCount,
   short maxNearbyEntities,
   short requiredPlayerRange,
   short spawnRange
) implements CustomPacketPayload {
   public static final Type<SpawnerDataPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("spawnerinfo", "spawner_data"));
   public static final StreamCodec<FriendlyByteBuf, SpawnerDataPayload> CODEC = CustomPacketPayload.codec(SpawnerDataPayload::write, SpawnerDataPayload::read);

   private static SpawnerDataPayload read(FriendlyByteBuf buf) {
      return new SpawnerDataPayload(
         buf.readInt(),
         buf.readInt(),
         buf.readUtf(),
         buf.readShort(),
         buf.readShort(),
         buf.readShort(),
         buf.readShort(),
         buf.readShort(),
         buf.readShort(),
         buf.readShort()
      );
   }

   private void write(FriendlyByteBuf buf) {
      buf.writeInt(this.entityId);
      buf.writeInt(this.summonId);
      buf.writeUtf(this.customName);
      buf.writeShort(this.delay);
      buf.writeShort(this.minSpawnDelay);
      buf.writeShort(this.maxSpawnDelay);
      buf.writeShort(this.spawnCount);
      buf.writeShort(this.maxNearbyEntities);
      buf.writeShort(this.requiredPlayerRange);
      buf.writeShort(this.spawnRange);
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}

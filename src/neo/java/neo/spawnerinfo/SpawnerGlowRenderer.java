package neo.spawnerinfo;

import neo.menu.config.ModManagerConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

public final class SpawnerGlowRenderer {
   private static final int SCAN_RADIUS_XZ = 48;
   private static final int SCAN_RADIUS_Y = 32;
   private static final int RESCAN_INTERVAL_TICKS = 40;
   private static final int COLOR_RGB = 16767296;
   private static final List<BlockPos> FOUND_SPAWNERS = new ArrayList<>();
   private static int tickCounter = RESCAN_INTERVAL_TICKS;

   private SpawnerGlowRenderer() {
   }

   /** スポナー情報が使える (権限がゲームマスター以上) うえで spawner_glow が ON のときだけ動く。OFF の間は走査も描画もしない。 */
   static boolean isActive() {
      return SpawnerInfoClient.isEnabled() && ModManagerConfig.SPAWNER_GLOW.getValue();
   }

   static void onClientTick(Minecraft client) {
      if (!isActive()) {
         FOUND_SPAWNERS.clear();
         tickCounter = RESCAN_INTERVAL_TICKS;
         return;
      }

      if (++tickCounter >= RESCAN_INTERVAL_TICKS) {
         tickCounter = 0;
         rescan(client);
      }
   }

   /** スポナーはブロックエンティティなので、全ブロックを調べず各チャンクのブロックエンティティだけを見る。 */
   private static void rescan(Minecraft client) {
      FOUND_SPAWNERS.clear();
      ClientLevel level = client.level;
      if (level == null || client.player == null) {
         return;
      }

      BlockPos center = client.player.blockPosition();
      int minX = center.getX() - SCAN_RADIUS_XZ;
      int maxX = center.getX() + SCAN_RADIUS_XZ;
      int minZ = center.getZ() - SCAN_RADIUS_XZ;
      int maxZ = center.getZ() + SCAN_RADIUS_XZ;
      int minY = Math.max(level.getMinY(), center.getY() - SCAN_RADIUS_Y);
      int maxY = Math.min(level.getMaxY(), center.getY() + SCAN_RADIUS_Y);

      for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
         for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
            if (!level.hasChunk(cx, cz)) {
               continue;
            }

            LevelChunk chunk = level.getChunk(cx, cz);
            for (BlockEntity be : chunk.getBlockEntities().values()) {
               if (be instanceof SpawnerBlockEntity) {
                  BlockPos pos = be.getBlockPos();
                  if (pos.getX() >= minX && pos.getX() <= maxX
                     && pos.getZ() >= minZ && pos.getZ() <= maxZ
                     && pos.getY() >= minY && pos.getY() <= maxY) {
                     FOUND_SPAWNERS.add(pos);
                  }
               }
            }
         }
      }
   }

   static void emitGizmos() {
      if (!isActive() || FOUND_SPAWNERS.isEmpty()) {
         return;
      }

      double phase = System.currentTimeMillis() % 2000L / 2000.0 * Math.PI * 2.0;
      float pulse = 0.55F + (float)Math.sin(phase) * 0.2F;
      int fill = ARGB.color(Math.round(pulse * 0.45F * 255.0F), COLOR_RGB);
      int stroke = ARGB.color(Math.round(229.5F), COLOR_RGB);
      GizmoStyle style = GizmoStyle.strokeAndFill(stroke, 2.0F, fill);

      for (BlockPos pos : FOUND_SPAWNERS) {
         Gizmos.cuboid(pos, style).setAlwaysOnTop();
      }
   }
}

package neo.spawnerinfo;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleFunction;
import neo.event.NeoClientEvents;
import neo.event.NeoKeyMappings;
import neo.menu.config.ModManagerConfig;
import neo.util.PermissionGate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.Gizmos.TemporaryCollection;
import net.minecraft.gizmos.TextGizmo.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class SpawnerInfoClient {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Map<Integer, SpawnerData> CACHE = new ConcurrentHashMap<>();
   private static final Category CATEGORY = Category.register(Identifier.fromNamespaceAndPath(SpawnerInfoMod.MOD_ID, "main"));
   private static final KeyMapping CYCLE_SPAWNER_KEY = register("key.spawnerinfo.cycle_spawner");

   private static final long CACHE_TTL_MILLIS = 5000L;
   private static final int RANGE_LINE_COLOR = -431964161;
   private static final float RANGE_LINE_WIDTH = 2.0F;
   private static final int CIRCLE_SEGMENTS = 32;
   private static final int MERIDIAN_COUNT = 6;
   private static final float[] SPHERE_LATITUDES = new float[]{-60.0F, -30.0F, 0.0F, 30.0F, 60.0F};

   private static boolean nearestOnlyMode = false;
   private static Integer nearestSelectedEntityId = null;

   private static KeyMapping register(String name) {
      return NeoKeyMappings.register(new KeyMapping(name, Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
   }

   /** neo のクライアント初期化 (com.fabric.NeoClient) から呼ばれる。独立した Fabric エントリポイントではない。 */
   public static void init() {
      LOGGER.info("[SpawnerInfo] クライアント側モジュールを初期化しました(neo 内部)。このログが出ていない場合、クライアント側にMODが正しく導入されていません。");
      // 受信は neo.mixin.MixinClientPacketListener から onPayload() が呼ばれる
      SpawnerInfoMod.setSendEnabled(false); // 設定が OFF の間、シングル/LAN のサーバー側も何もしない (tick で設定に追従)
      NeoClientEvents.registerEndExtraction(() -> {
         boolean glow = SpawnerGlowRenderer.isActive();
         boolean info = isActive();
         if (!glow && !info) {
            return; // OFF のときは Gizmo の収集用オブジェクトも作らない (毎フレームの処理をここで打ち切る)
         }

         try (TemporaryCollection ignored = Minecraft.getInstance().levelExtractor.collectPerFrameMainThreadGizmos()) {
            if (glow) {
               SpawnerGlowRenderer.emitGizmos();
            }

            if (info) {
               emitOverlay(Minecraft.getInstance());
            }
         }
      });
      NeoClientEvents.registerEndClientTick(client -> {
         boolean master = isEnabled();
         SpawnerInfoMod.setSendEnabled(master);
         if (!master) {
            clearState();
         }

         SpawnerGlowRenderer.onClientTick(client);
         handleKeys(client);
      });
   }

   /** スポナー情報を使ってよいか (全体スイッチが ON で、自分の権限がゲームマスター以上。ゲームモードは問わない)。スポナー系の機能はすべてこれを前提にする。 */
   static boolean isEnabled() {
      return ModManagerConfig.SPAWNER_INFO.getValue() && PermissionGate.allowed(Minecraft.getInstance().player);
   }

   /** 機能が実際に何かを描く状態か (使ってよく、かつ文字表示か範囲球のどちらかが ON)。 */
   private static boolean isActive() {
      return isEnabled()
         && (ModManagerConfig.SPAWNER_OVERLAY.getValue() || ModManagerConfig.SPAWNER_RANGE.getValue());
   }

   /** 使えなくなったとき (スイッチ OFF・権限がなくなった)、溜めたデータと選択状態を捨てる。 */
   private static void clearState() {
      if (!CACHE.isEmpty()) {
         CACHE.clear();
      }

      nearestOnlyMode = false;
      nearestSelectedEntityId = null;
   }

   /** ClientPlayNetworking.registerGlobalReceiver の代替。メインスレッドで呼ばれる。 */
   public static void onPayload(SpawnerDataPayload payload) {
      if (!isEnabled()) {
         return;
      }

      CACHE.put(payload.entityId(), new SpawnerData(payload, System.currentTimeMillis()));
   }

   // ---- キー入力 ----
   // 設定で切り替えられるもの (発光・範囲球・全表示) はキーを持たない。
   // 「最寄りのみ表示」だけは、押した瞬間の最寄りスポナーを選ぶ操作なので、キーを残している。

   private static void handleKeys(Minecraft client) {
      while (CYCLE_SPAWNER_KEY.consumeClick()) {
         if (isEnabled()) {
            toggleNearestOnly(client);
         } else {
            notify(client, "§7[SpawnerInfo] 無効です (Mod 設定の「スポナー情報」をオンにして、ゲームマスター以上の権限で使ってください)");
         }
      }
   }

   private static void toggleNearestOnly(Minecraft client) {
      nearestOnlyMode = !nearestOnlyMode;
      if (!nearestOnlyMode) {
         nearestSelectedEntityId = null;
         notify(client, "§7[SpawnerInfo] 最寄りのみ表示: OFF");
         return;
      }

      Entry<Integer, SpawnerData> nearest = findNearestEntry(client, collectVisibleEntries(client));
      nearestSelectedEntityId = nearest == null ? null : nearest.getKey();
      if (nearest == null) {
         notify(client, "§a[SpawnerInfo] 最寄りのみ表示: ON §7(近くにスポナーなし)");
      } else {
         notify(client, "§a[SpawnerInfo] 最寄りのみ表示: ON §f(" + displayLabel(nearest.getValue()) + ")");
      }
   }

   private static void notify(Minecraft client, String message) {
      if (client.player != null) {
         client.player.sendOverlayMessage(Component.literal(message));
      }
   }

   // ---- 対象の選別 ----

   private static boolean hasName(SpawnerData data) {
      return data.customName() != null && !data.customName().isEmpty();
   }

   /** 通知用ラベル: カスタム名があればそれ、なければ #召喚ID。 */
   private static String displayLabel(SpawnerData data) {
      return hasName(data) ? data.customName() : "#" + data.summonId();
   }

   private static List<Entry<Integer, SpawnerData>> collectVisibleEntries(Minecraft client) {
      List<Entry<Integer, SpawnerData>> visible = new ArrayList<>();
      if (client.level == null) {
         return visible;
      }

      for (Entry<Integer, SpawnerData> entry : CACHE.entrySet()) {
         if (client.level.getEntity(entry.getKey()) != null) {
            visible.add(entry);
         }
      }

      return visible;
   }

   private static Entry<Integer, SpawnerData> findNearestEntry(Minecraft client, List<Entry<Integer, SpawnerData>> visible) {
      if (client.player == null || client.level == null || visible.isEmpty()) {
         return null;
      }

      Vec3 playerPos = client.player.position();
      Entry<Integer, SpawnerData> nearest = null;
      double bestDistance = Double.MAX_VALUE;

      for (Entry<Integer, SpawnerData> entry : visible) {
         Entity entity = client.level.getEntity(entry.getKey());
         double distance = entity.position().distanceToSqr(playerPos);
         if (distance < bestDistance) {
            bestDistance = distance;
            nearest = entry;
         }
      }

      return nearest;
   }

   /** 「最寄りのみ」モードなら選択中の1件、そうでなければ可視の全件。 */
   private static List<Entry<Integer, SpawnerData>> selectEntriesToRender(List<Entry<Integer, SpawnerData>> visible) {
      if (!nearestOnlyMode) {
         return visible;
      }

      if (nearestSelectedEntityId != null) {
         for (Entry<Integer, SpawnerData> entry : visible) {
            if (entry.getKey().equals(nearestSelectedEntityId)) {
               return List.of(entry);
            }
         }
      }

      return List.of();
   }

   // ---- 描画 ----

   private static void emitOverlay(Minecraft client) {
      if (client.player == null || client.level == null) {
         return;
      }

      long now = System.currentTimeMillis();
      CACHE.entrySet().removeIf(e -> now - e.getValue().receivedAt() > CACHE_TTL_MILLIS);
      if (CACHE.isEmpty()) {
         return;
      }

      List<Entry<Integer, SpawnerData>> toRender = selectEntriesToRender(collectVisibleEntries(client));
      if (ModManagerConfig.SPAWNER_RANGE.getValue()) {
         emitRangeSpheres(client, toRender);
      }

      if (ModManagerConfig.SPAWNER_OVERLAY.getValue()) {
         emitLabels(client, toRender);
      }
   }

   private static void emitRangeSpheres(Minecraft client, List<Entry<Integer, SpawnerData>> entries) {
      for (Entry<Integer, SpawnerData> entry : entries) {
         Entity entity = client.level.getEntity(entry.getKey());
         int radius = entry.getValue().requiredPlayerRange();
         if (entity != null && radius > 0) {
            Vec3 pos = entity.position();
            emitSphereWireframe(new Vec3(pos.x, pos.y + entity.getBbHeight() / 2.0, pos.z), radius);
         }
      }
   }

   private static void emitLabels(Minecraft client, List<Entry<Integer, SpawnerData>> entries) {
      Style style = Style.whiteAndCentered().withScale(0.4F);

      for (Entry<Integer, SpawnerData> entry : entries) {
         Entity entity = client.level.getEntity(entry.getKey());
         List<String> lines = buildLabelLines(entry.getValue());
         Vec3 pos = entity.position();
         double baseY = pos.y + entity.getBbHeight() + 0.5;

         for (int i = 0; i < lines.size(); i++) {
            double y = baseY + (lines.size() - i) * 0.25;
            Gizmos.billboardText(lines.get(i), new Vec3(pos.x, y, pos.z), style);
         }
      }
   }

   private static List<String> buildLabelLines(SpawnerData data) {
      List<String> lines = new ArrayList<>();
      lines.add("§e#" + data.summonId());
      lines.add(hasName(data) ? "§f" + data.customName() : "§7(名前未取得)");
      lines.add("§bDelay: §f" + data.delay());
      lines.add("§bMin/Max: §f" + data.minSpawnDelay() + "/" + data.maxSpawnDelay());
      lines.add("§bCount: §f" + data.spawnCount());
      lines.add("§bMaxNearby: §f" + data.maxNearbyEntities());
      lines.add("§bPlayerRange: §f" + data.requiredPlayerRange());
      lines.add("§bSpawnRange: §f" + data.spawnRange());
      return lines;
   }

   private static void emitSphereWireframe(Vec3 center, double radius) {
      for (float latDeg : SPHERE_LATITUDES) {
         double lat = Math.toRadians(latDeg);
         double ringRadius = radius * Math.cos(lat);
         double y = center.y + radius * Math.sin(lat);
         emitCircleXZ(center.x, y, center.z, ringRadius);
      }

      for (int m = 0; m < MERIDIAN_COUNT; m++) {
         emitMeridianCircle(center, radius, Math.PI * m / MERIDIAN_COUNT);
      }
   }

   /** 水平な円(緯線)。半径がほぼ0なら描かない。 */
   private static void emitCircleXZ(double cx, double cy, double cz, double radius) {
      if (radius <= 0.001) {
         return;
      }

      emitClosedCircle(angle -> new Vec3(cx + Math.cos(angle) * radius, cy, cz + Math.sin(angle) * radius));
   }

   /** 方位角 azimuth の鉛直面上の円(経線)。 */
   private static void emitMeridianCircle(Vec3 center, double radius, double azimuth) {
      double dx = Math.cos(azimuth);
      double dz = Math.sin(azimuth);
      emitClosedCircle(angle -> {
         double horiz = Math.cos(angle) * radius;
         return new Vec3(center.x + dx * horiz, center.y + Math.sin(angle) * radius, center.z + dz * horiz);
      });
   }

   /** angle(0〜2π) から点を返す関数を CIRCLE_SEGMENTS 分割し、隣り合う点を線で結ぶ。 */
   private static void emitClosedCircle(DoubleFunction<Vec3> pointAt) {
      Vec3 prev = null;

      for (int i = 0; i <= CIRCLE_SEGMENTS; i++) {
         double angle = i * 2.0 * Math.PI / CIRCLE_SEGMENTS;
         Vec3 p = pointAt.apply(angle);
         if (prev != null) {
            Gizmos.line(prev, p, RANGE_LINE_COLOR, RANGE_LINE_WIDTH).setAlwaysOnTop();
         }

         prev = p;
      }
   }

   private record SpawnerData(
      int summonId,
      String customName,
      int delay,
      int minSpawnDelay,
      int maxSpawnDelay,
      int spawnCount,
      int maxNearbyEntities,
      int requiredPlayerRange,
      int spawnRange,
      long receivedAt
   ) {
      SpawnerData(SpawnerDataPayload p, long receivedAt) {
         this(
            p.summonId(),
            p.customName(),
            p.delay(),
            p.minSpawnDelay(),
            p.maxSpawnDelay(),
            p.spawnCount(),
            p.maxNearbyEntities(),
            p.requiredPlayerRange(),
            p.spawnRange(),
            receivedAt
         );
      }
   }
}

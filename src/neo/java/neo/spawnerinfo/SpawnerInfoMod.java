package neo.spawnerinfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.vehicle.minecart.MinecartSpawner;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import neo.util.PermissionGate;

/** サーバー側。Fabric API 不要: tick は neo.mixin.MixinMinecraftServer、ペイロード登録は MixinClientboundCustomPayloadPacket から。 */
public final class SpawnerInfoMod {
   public static final String MOD_ID = "spawnerinfo";
   private static final Map<Integer, String> NAME_CACHE = new ConcurrentHashMap<>();
   private static final Pattern TEXT_PATTERN = Pattern.compile("[\"']?text[\"']?\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|'((?:[^'\\\\]|\\\\.)*)')");
   private static final int UPDATE_INTERVAL_TICKS = 20;
   private static final double SEARCH_RADIUS = 64.0;
   private static final String[] SUMMON_FUNCTION_DIRS = {"function", "functions"};
   private static int tickCounter = 0;
   /** NAME_CACHE を作ったときのリソースマネージャー。/reload で MinecraftServer#reloadResources が新しいものに差し替えるので、違っていたら NAME_CACHE を捨てる。 */
   private static ResourceManager cachedResourceManager;
   /**
    * 送信してよいか。専用サーバーでは常に true (クライアントの設定は届かないため従来どおり送る)。
    * シングル/LAN ホストでは、クライアント側 (SpawnerInfoClient) が設定に合わせて更新する。
    * 設定が OFF の間は、サーバー側の走査・NBT 保存・送信をすべて省く。権限がゲームマスター以上でないプレイヤーには送らない。
    */
   private static volatile boolean sendEnabled = true;

   private SpawnerInfoMod() {
   }

   public static void setSendEnabled(boolean enabled) {
      sendEnabled = enabled;
   }

   public static void onServerTick(MinecraftServer server) {
      if (!sendEnabled) {
         return;
      }

      if (++tickCounter < UPDATE_INTERVAL_TICKS) {
         return;
      }

      tickCounter = 0;
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         if (!PermissionGate.allowed(player)) {
            continue; // スポナー情報は権限 (ゲームマスター以上) のあるプレイヤーにだけ送る (専用サーバーでも同じ。ゲームモードは問わない)
         }

         ServerLevel level = player.level();
         AABB searchBox = player.getBoundingBox().inflate(SEARCH_RADIUS);

         for (MinecartSpawner minecart : level.getEntitiesOfClass(MinecartSpawner.class, searchBox, e -> true)) {
            sendSpawnerData(server, player, minecart);
         }
      }
   }

   private static void sendSpawnerData(MinecraftServer server, ServerPlayer player, MinecartSpawner minecart) {
      TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
      minecart.getSpawner().save(output);
      CompoundTag nbt = output.buildResult();
      int summonId = readSummonId(nbt);
      String customName = summonId >= 0 ? resolveCustomName(server, summonId) : "";
      SpawnerDataPayload payload = new SpawnerDataPayload(
         minecart.getId(),
         summonId,
         customName,
         nbt.getShortOr("Delay", (short)0),
         nbt.getShortOr("MinSpawnDelay", (short)0),
         nbt.getShortOr("MaxSpawnDelay", (short)0),
         nbt.getShortOr("SpawnCount", (short)0),
         nbt.getShortOr("MaxNearbyEntities", (short)0),
         nbt.getShortOr("RequiredPlayerRange", (short)0),
         nbt.getShortOr("SpawnRange", (short)0)
      );
      // canSend の代替: 未導入クライアントは未知ペイロードを DiscardedPayload として捨てるので常に送る
      player.connection.send(new ClientboundCustomPayloadPacket(payload));
   }

   private static int readSummonId(CompoundTag spawnerNbt) {
      CompoundTag item = spawnerNbt.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity").getCompoundOrEmpty("Item");
      Optional<Integer> summon = item.getCompoundOrEmpty("components").getCompoundOrEmpty("minecraft:custom_data").getInt("summon");
      if (summon.isEmpty()) {
         summon = item.getCompoundOrEmpty("tag").getInt("summon");
      }

      return summon.orElse(-1);
   }

   private static String resolveCustomName(MinecraftServer server, int summonId) {
      ResourceManager resourceManager = server.getResourceManager();
      if (resourceManager != cachedResourceManager) {
         NAME_CACHE.clear();
         cachedResourceManager = resourceManager;
      }

      return NAME_CACHE.computeIfAbsent(summonId, id -> {
         Optional<Resource> resource = Optional.empty();
         for (String dir : SUMMON_FUNCTION_DIRS) {
            resource = resourceManager
               .getResource(Identifier.fromNamespaceAndPath("neofunction", dir + "/asset/summon/" + id + ".mcfunction"));
            if (resource.isPresent()) {
               break;
            }
         }

         if (resource.isEmpty()) {
            return "";
         }

         StringBuilder sb = new StringBuilder();
         try (BufferedReader reader = resource.get().openAsReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
               String trimmed = line.trim();
               if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                  sb.append(line).append('\n');
               }
            }
         } catch (IOException e) {
            return "";
         }

         return extractTopLevelCustomName(sb.toString());
      });
   }

   private static String extractTopLevelCustomName(String text) {
      int braceStart = text.indexOf('{');
      if (braceStart < 0) {
         return "";
      }

      int depth = 0;
      int i = braceStart;
      int len = text.length();

      while (i < len) {
         char c = text.charAt(i);
         if (c == '"' || c == '\'') {
            i = skipQuoted(text, i);
         } else if (c == '{' || c == '[') {
            depth++;
            i++;
         } else if (c != '}' && c != ']') {
            if (depth == 1 && text.startsWith("CustomName:", i)) {
               return parseCustomNameValue(text, i + "CustomName:".length());
            }

            i++;
         } else {
            depth--;
            i++;
            if (depth <= 0) {
               return "";
            }
         }
      }

      return "";
   }

   private static String parseCustomNameValue(String text, int start) {
      int len = text.length();
      int j = start;

      while (j < len && Character.isWhitespace(text.charAt(j))) {
         j++;
      }

      if (j >= len) {
         return "";
      }

      char first = text.charAt(j);
      if (first == '{') {
         int end = skipCompound(text, j);
         return findText(text.substring(j, end));
      }

      if (first != '"' && first != '\'') {
         return "";
      }

      StringBuilder raw = new StringBuilder();
      int m = j + 1;

      while (m < len) {
         char ch = text.charAt(m);
         if (ch == '\\' && m + 1 < len) {
            raw.append(text.charAt(m + 1));
            m += 2;
         } else {
            if (ch == first) {
               break;
            }

            raw.append(ch);
            m++;
         }
      }

      String value = raw.toString();
      String trimmed = value.trim();
      return !trimmed.startsWith("{") && !trimmed.startsWith("[") ? value : findText(value);
   }

   private static String findText(String component) {
      Matcher matcher = TEXT_PATTERN.matcher(component);
      if (!matcher.find()) {
         return "";
      }

      String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
      return value.replace("\\\"", "\"").replace("\\'", "'").replace("\\\\", "\\");
   }

   private static int skipQuoted(String text, int i) {
      char quote = text.charAt(i);
      i++;

      while (i < text.length()) {
         char qc = text.charAt(i);
         if (qc == '\\' && i + 1 < text.length()) {
            i += 2;
         } else {
            if (qc == quote) {
               return i + 1;
            }

            i++;
         }
      }

      return i;
   }

   private static int skipCompound(String text, int i) {
      int depth = 0;

      while (i < text.length()) {
         char c = text.charAt(i);
         if (c != '"' && c != '\'') {
            if (c == '{' || c == '[') {
               depth++;
            }

            if (c == '}' || c == ']') {
               if (--depth == 0) {
                  return i + 1;
               }
            }

            i++;
         } else {
            i = skipQuoted(text, i);
         }
      }

      return i;
   }
}

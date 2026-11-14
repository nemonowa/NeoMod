package neo.entitytex;

import java.time.Duration;
import java.util.UUID;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerDataHolder;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;

/**
 * ランダムエンティティのルールで比べる、個体の状態の写し。OptiFine と同じく、その個体を初めて見た時の値で決める (後から体力や時刻が変わっても絵は変えない)。
 * 名前だけは描くたびに見る (RandomEntityTextures)。見なくなった個体の写しは時間で捨てる。
 */
public record EntityConditions(String biome, int y, Boolean baby, float health, float maxHealth, int moonPhase, int dayTime,
                               String weather, Integer size, String profession, int professionLevel, String color, String block) {
    private static final Cache<UUID, EntityConditions> SNAPSHOTS = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(2)).maximumSize(4096).build();

    /** 描画用データを作る時に呼ぶ (neo.mixin.MixinEntityRendererRandomTexture)。初めて見た個体の時だけ写しを作る。 */
    public static void capture(Entity entity) {
        if (SNAPSHOTS.getIfPresent(entity.getUUID()) == null) SNAPSHOTS.put(entity.getUUID(), of(entity));
    }

    public static EntityConditions get(UUID id) {
        return SNAPSHOTS.getIfPresent(id);
    }

    public static void clear() {
        SNAPSHOTS.invalidateAll();
    }

    private static EntityConditions of(Entity e) {
        Level level = e.level();
        BlockPos pos = e.blockPosition();
        String biome = level.getBiome(pos).getRegisteredName();
        Boolean baby = null;
        float health = 0, maxHealth = 0;
        if (e instanceof LivingEntity living) {
            baby = living.isBaby();
            health = living.getHealth();
            maxHealth = living.getMaxHealth();
        }
        int moon = level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, e.position()).index();
        int dayTime = (int) Math.floorMod(level.getDefaultClockTime(), 24000L);
        String weather = level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear";
        Integer size = null;
        // OptiFine の大きさは、スライム・マグマキューブがバニラの大きさ - 1 (0 = 小)、ファントムはそのまま
        if (e instanceof AbstractCubeMob cube) size = cube.getSize() - 1;
        else if (e instanceof Phantom phantom) size = phantom.getPhantomSize();
        String profession = null;
        int professionLevel = 0;
        if (e instanceof VillagerDataHolder holder) {
            VillagerData data = holder.getVillagerData();
            profession = data.profession().getRegisteredName();
            professionLevel = data.level();
        }
        DyeColor dye = e instanceof Wolf wolf ? wolf.getCollarColor()
            : e instanceof Cat cat ? cat.getCollarColor()
            : e instanceof Sheep sheep ? sheep.getColor() : null;
        String block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(e.getBlockPosBelowThatAffectsMyMovement()).getBlock()).toString();
        return new EntityConditions(biome, (int) Math.floor(e.getY()), baby, health, maxHealth, moon, dayTime, weather, size,
            profession, professionLevel, dye != null ? dye.getSerializedName() : null, block);
    }
}

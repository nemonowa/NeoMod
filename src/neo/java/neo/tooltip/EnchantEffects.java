package neo.tooltip;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

public final class EnchantEffects {
   /** 効果なし(エンチャント未付与)を表す値。 */
   private static final double NONE = -1.0;

   private EnchantEffects() {
   }

   private static double perLevel(int level, double perLevel) {
      return level <= 0 ? NONE : level * perLevel;
   }

   public static double unbreakingSurvivalChance(ItemStack stack, EnchantLevels e) {
      int level = e.get(Enchantments.UNBREAKING);
      if (level <= 0) {
         return -1.0;
      }

      boolean isArmor = stack.is(ItemTags.ARMOR_ENCHANTABLE);
      double breakChance = isArmor ? 60.0 + 40.0 / (level + 1) : 100.0 / (level + 1);
      return 100.0 - breakChance;
   }

   public static double sharpnessBonus(EnchantLevels e) {
      int level = e.get(Enchantments.SHARPNESS);
      return level <= 0 ? -1.0 : 0.5 * level + 0.5;
   }

   public static double smiteBonus(EnchantLevels e) {
      int level = e.get(Enchantments.SMITE);
      return level <= 0 ? -1.0 : 2.5 * level;
   }

   public static double baneOfArthropodsBonus(EnchantLevels e) {
      int level = e.get(Enchantments.BANE_OF_ARTHROPODS);
      return level <= 0 ? -1.0 : 2.5 * level;
   }

   public static double impalingBonus(EnchantLevels e) {
      int level = e.get(Enchantments.IMPALING);
      return level <= 0 ? -1.0 : 2.5 * level;
   }

   public static double fireAspectDuration(EnchantLevels e) {
      int level = e.get(Enchantments.FIRE_ASPECT);
      return perLevel(level, 4.0);
   }

   public static double sweepingEdgePercent(ItemStack stack, EnchantLevels e) {
      if (stack.is(Items.FISHING_ROD)) {
         return -1.0;
      }

      int level = e.get(Enchantments.SWEEPING_EDGE);
      return level <= 0 ? -1.0 : level / (level + 1.0) * 100.0;
   }

   public static double fishingRodSweepingRadius(ItemStack stack, EnchantLevels e) {
      if (!stack.is(Items.FISHING_ROD)) {
         return -1.0;
      }

      int level = e.get(Enchantments.SWEEPING_EDGE);
      return level <= 0 ? -1.0 : level;
   }

   public static double thornsChance(EnchantLevels e) {
      int level = e.get(Enchantments.THORNS);
      return level <= 0 ? -1.0 : Math.min(level * 15.0, 100.0);
   }

   public static double thornsFixedDamage(EnchantLevels e) {
      int level = e.get(Enchantments.THORNS);
      return level <= 10 ? -1.0 : level - 10.0;
   }

   public static double knockbackBonus(EnchantLevels e) {
      int level = e.get(Enchantments.KNOCKBACK);
      return perLevel(level, 3.0);
   }

   public static double punchBonus(EnchantLevels e) {
      int level = e.get(Enchantments.PUNCH);
      return perLevel(level, 3.0);
   }

   public static double riptideDistance(EnchantLevels e) {
      int level = e.get(Enchantments.RIPTIDE);
      return level <= 0 ? -1.0 : level * 6.0 + 3.0;
   }

   public static double loyaltyReturnSpeed(EnchantLevels e) {
      int level = e.get(Enchantments.LOYALTY);
      return perLevel(level, 10.0);
   }

   public static double protectionPercent(EnchantLevels e) {
      int level = e.get(Enchantments.PROTECTION);
      return perLevel(level, 4.0);
   }

   public static double fireProtectionPercent(EnchantLevels e) {
      int level = e.get(Enchantments.FIRE_PROTECTION);
      return perLevel(level, 8.0);
   }

   public static double blastProtectionPercent(EnchantLevels e) {
      int level = e.get(Enchantments.BLAST_PROTECTION);
      return perLevel(level, 8.0);
   }

   public static double projectileProtectionPercent(EnchantLevels e) {
      int level = e.get(Enchantments.PROJECTILE_PROTECTION);
      return perLevel(level, 8.0);
   }

   public static double featherFallingPercent(EnchantLevels e) {
      int level = e.get(Enchantments.FEATHER_FALLING);
      return perLevel(level, 12.0);
   }

   public static double efficiencyBonus(EnchantLevels e) {
      int level = e.get(Enchantments.EFFICIENCY);
      return level <= 0 ? -1.0 : level * level + 1;
   }

   public static double axeShieldDisableChance(ItemStack stack, EnchantLevels e) {
      int level = e.get(Enchantments.EFFICIENCY);
      return level > 0 && stack.is(ItemTags.AXES) ? 25.0 + 5.0 * level : -1.0;
   }

   public static double powerPercent(EnchantLevels e) {
      int level = e.get(Enchantments.POWER);
      return level <= 0 ? -1.0 : 25.0 * (level + 1);
   }

   public static double piercingCount(EnchantLevels e) {
      int level = e.get(Enchantments.PIERCING);
      return level <= 0 ? -1.0 : level;
   }

   public static double quickChargeReduction(EnchantLevels e) {
      int level = e.get(Enchantments.QUICK_CHARGE);
      return perLevel(level, 0.25);
   }

   public static double depthStriderPercent(EnchantLevels e) {
      int level = e.get(Enchantments.DEPTH_STRIDER);
      return perLevel(level, 33.333333333333336);
   }

   public static double frostWalkerRadius(EnchantLevels e) {
      int level = e.get(Enchantments.FROST_WALKER);
      return level <= 0 ? -1.0 : level + 3.0;
   }

   public static double soulSpeedPercent(EnchantLevels e) {
      int level = e.get(Enchantments.SOUL_SPEED);
      return level <= 0 ? -1.0 : 40.5 + 10.5 * (level - 1);
   }

   public static double swiftSneakPercent(EnchantLevels e) {
      int level = e.get(Enchantments.SWIFT_SNEAK);
      return perLevel(level, 15.0);
   }

   public static double lureReduction(EnchantLevels e) {
      int level = e.get(Enchantments.LURE);
      return perLevel(level, 5.0);
   }

   public static boolean isLureBugged(EnchantLevels e) {
      return e.get(Enchantments.LURE) > 6;
   }

   public static double luckOfTheSeaPercent(EnchantLevels e) {
      int level = e.get(Enchantments.LUCK_OF_THE_SEA);
      return perLevel(level, 2.1);
   }
}

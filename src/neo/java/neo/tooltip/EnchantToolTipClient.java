package neo.tooltip;

import neo.menu.config.ModManagerConfig;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class EnchantToolTipClient {
   private EnchantToolTipClient() {}

   /** ツールチップに1行(以上)を足す規則。 */
   @FunctionalInterface
   private interface Line {
      void appendTo(List<Component> lines, ItemStack stack, EnchantLevels e);
   }

   /** 釣り針 Lure: レベル超過バグ時は警告、通常時は待ち時間短縮を表示する。 */
   private static final Line LURE = (lines, stack, e) -> {
      if (EnchantEffects.isLureBugged(e)) {
         lines.add(Component.translatable("neo.enchant.lure_bugged").withStyle(ChatFormatting.RED));
      } else {
         addLine(lines, EnchantEffects.lureReduction(e), "lure", "%.0f");
      }
   };

   /** エンチャント効果の表示規則。表示順 = 並び順。 */
   private static final List<Line> LINES = List.of(
        withStack(EnchantEffects::unbreakingSurvivalChance, "unbreaking", "%.1f"),
        of(EnchantEffects::sharpnessBonus, "sharpness", "%.1f"),
        of(EnchantEffects::smiteBonus, "smite", "%.1f"),
        of(EnchantEffects::baneOfArthropodsBonus, "bane_of_arthropods", "%.1f"),
        of(EnchantEffects::impalingBonus, "impaling", "%.1f"),
        of(EnchantEffects::fireAspectDuration, "fire_aspect", "%.0f"),
        withStack(EnchantEffects::sweepingEdgePercent, "sweeping_edge", "%.0f"),
        withStack(EnchantEffects::fishingRodSweepingRadius, "fishing_rod_sweeping", "%.0f"),
        of(EnchantEffects::thornsChance, "thorns_chance", "%.0f"),
        of(EnchantEffects::thornsFixedDamage, "thorns_damage", "%.0f"),
        of(EnchantEffects::knockbackBonus, "knockback", "%.0f"),
        of(EnchantEffects::punchBonus, "punch", "%.0f"),
        of(EnchantEffects::riptideDistance, "riptide", "%.0f"),
        of(EnchantEffects::loyaltyReturnSpeed, "loyalty", "%.0f"),
        of(EnchantEffects::protectionPercent, "protection", "%.0f"),
        of(EnchantEffects::fireProtectionPercent, "fire_protection", "%.0f"),
        of(EnchantEffects::blastProtectionPercent, "blast_protection", "%.0f"),
        of(EnchantEffects::projectileProtectionPercent, "projectile_protection", "%.0f"),
        of(EnchantEffects::featherFallingPercent, "feather_falling", "%.0f"),
        of(EnchantEffects::efficiencyBonus, "efficiency", "%.0f"),
        withStack(EnchantEffects::axeShieldDisableChance, "axe_shield_disable", "%.0f"),
        of(EnchantEffects::powerPercent, "power", "%.0f"),
        of(EnchantEffects::piercingCount, "piercing", "%.0f"),
        of(EnchantEffects::quickChargeReduction, "quick_charge", "%.2f"),
        of(EnchantEffects::depthStriderPercent, "depth_strider", "%.1f"),
        of(EnchantEffects::frostWalkerRadius, "frost_walker", "%.0f"),
        of(EnchantEffects::soulSpeedPercent, "soul_speed", "%.1f"),
        of(EnchantEffects::swiftSneakPercent, "swift_sneak", "%.0f"),
        LURE,
        of(EnchantEffects::luckOfTheSeaPercent, "luck_of_the_sea", "%.1f")
   );

   /** ItemStack#getTooltipLines の戻り値に呼ばれる (neo.mixin.MixinItemStack)。設定 enchant_tooltip が OFF なら何もしない。 */
   public static void appendLines(ItemStack stack, List<Component> lines) {
      if (!ModManagerConfig.ENCHANT_TOOLTIP.getValue()) {
         return;
      }
      EnchantLevels e = EnchantLevels.of(stack);
      if (e.isEmpty()) {
         return;
      }
      for (Line line : LINES) {
         line.appendTo(lines, stack, e);
      }
   }

   /** スタックに依存しない効果。 */
   private static Line of(ToDoubleFunction<EnchantLevels> effect, String key, String number) {
      return (lines, stack, e) -> addLine(lines, effect.applyAsDouble(e), key, number);
   }

   /** スタック(道具の種類)に依存する効果。 */
   private static Line withStack(ToDoubleBiFunction<ItemStack, EnchantLevels> effect, String key, String number) {
      return (lines, stack, e) -> addLine(lines, effect.applyAsDouble(stack, e), key, number);
   }

   /** 数値は number の書式で整え、文は翻訳 (neo.enchant.&lt;key&gt;) から作る。 */
   private static void addLine(List<Component> lines, double value, String key, String number) {
      if (value >= 0.0) {
         lines.add(Component.translatable("neo.enchant." + key, String.format(Locale.ROOT, number, value)).withStyle(ChatFormatting.GRAY));
      }
   }
}

package neo.tooltip;

import neo.menu.config.ModManagerConfig;
import java.util.List;
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
         lines.add(Component.literal("釣り不可(待ち時間の抽選が壊れるバグ、レベルVI超過)").withStyle(ChatFormatting.RED));
      } else {
         addLine(lines, EnchantEffects.lureReduction(e), "待ち時間短縮: 約%.0f秒");
      }
   };

   /** エンチャント効果の表示規則。表示順 = 並び順。 */
   private static final List<Line> LINES = List.of(
        withStack(EnchantEffects::unbreakingSurvivalChance, "耐久が減らない確率: %.1f%%"),
        of(EnchantEffects::sharpnessBonus, "追加ダメージ: +%.1f"),
        of(EnchantEffects::smiteBonus, "対アンデッド追加ダメージ: +%.1f"),
        of(EnchantEffects::baneOfArthropodsBonus, "対昆虫系追加ダメージ: +%.1f"),
        of(EnchantEffects::impalingBonus, "対水生モブ追加ダメージ: +%.1f"),
        of(EnchantEffects::fireAspectDuration, "燃焼時間: %.0f秒"),
        withStack(EnchantEffects::sweepingEdgePercent, "範囲攻撃ダメージ: 通常の%.0f%%"),
        withStack(EnchantEffects::fishingRodSweepingRadius, "命中半径 +%.0fm"),
        of(EnchantEffects::thornsChance, "反撃確率: %.0f%%"),
        of(EnchantEffects::thornsFixedDamage, "反撃ダメージ: %.0f(固定、通常のランダム1~5から変化)"),
        of(EnchantEffects::knockbackBonus, "ノックバック増加: 約+%.0fブロック(参考値)"),
        of(EnchantEffects::punchBonus, "ノックバック増加: 約+%.0fブロック(参考値)"),
        of(EnchantEffects::riptideDistance, "飛距離: 約%.0fブロック"),
        of(EnchantEffects::loyaltyReturnSpeed, "帰還速度: 約%.0fブロック/秒(参考値)"),
        of(EnchantEffects::protectionPercent, "防御軽減率: %.0f%%"),
        of(EnchantEffects::fireProtectionPercent, "火属性ダメージ軽減率: %.0f%%"),
        of(EnchantEffects::blastProtectionPercent, "爆発ダメージ軽減率: %.0f%%"),
        of(EnchantEffects::projectileProtectionPercent, "飛び道具ダメージ軽減率: %.0f%%"),
        of(EnchantEffects::featherFallingPercent, "落下ダメージ軽減率: %.0f%%"),
        of(EnchantEffects::efficiencyBonus, "採掘速度ボーナス: +%.0f(適した道具でのみ有効)"),
        withStack(EnchantEffects::axeShieldDisableChance, "盾を無効化する確率: %.0f%%"),
        of(EnchantEffects::powerPercent, "矢のダメージ増加: +%.0f%%(クロスボウには通常適用外)"),
        of(EnchantEffects::piercingCount, "追加貫通数: +%.0f体"),
        of(EnchantEffects::quickChargeReduction, "装填時間短縮: 約%.2f秒(参考値)"),
        of(EnchantEffects::depthStriderPercent, "水中減速の軽減率: %.1f%%"),
        of(EnchantEffects::frostWalkerRadius, "凍結半径: %.0fブロック"),
        of(EnchantEffects::soulSpeedPercent, "ソウルサンド上の速度増加: +%.1f%%"),
        of(EnchantEffects::swiftSneakPercent, "スニーク速度増加: +%.0f%%"),
        LURE,
        of(EnchantEffects::luckOfTheSeaPercent, "宝物率増加: 約+%.1f%%(参考値)")
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
   private static Line of(ToDoubleFunction<EnchantLevels> effect, String format) {
      return (lines, stack, e) -> addLine(lines, effect.applyAsDouble(e), format);
   }

   /** スタック(道具の種類)に依存する効果。 */
   private static Line withStack(ToDoubleBiFunction<ItemStack, EnchantLevels> effect, String format) {
      return (lines, stack, e) -> addLine(lines, effect.applyAsDouble(stack, e), format);
   }

   private static void addLine(List<Component> lines, double value, String format) {
      if (value >= 0.0) {
         lines.add(Component.literal(String.format(format, value)).withStyle(ChatFormatting.GRAY));
      }
   }
}

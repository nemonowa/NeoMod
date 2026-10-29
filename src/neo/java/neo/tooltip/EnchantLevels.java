package neo.tooltip;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class EnchantLevels {
   private final ItemEnchantments enchantments;

   private EnchantLevels(ItemEnchantments enchantments) {
      this.enchantments = enchantments;
   }

   public static EnchantLevels of(ItemStack stack) {
      return new EnchantLevels(stack.getEnchantments());
   }

   public boolean isEmpty() {
      return this.enchantments.isEmpty();
   }

   public int get(ResourceKey<Enchantment> key) {
      for (Entry<Holder<Enchantment>> entry : this.enchantments.entrySet()) {
         Holder<Enchantment> holder = (Holder<Enchantment>)entry.getKey();
         if (holder.is(key)) {
            return entry.getIntValue();
         }
      }

      return 0;
   }
}

package neo.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import neo.map.HeldMapView;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ワールドマップ: 画面を開いている間、メインハンドに「ワールドマップの地図」を持たせ、オフハンドを空にする
 * (バニラは、メインハンドに地図・オフハンドが空のとき、両腕で地図を持つ見た目にする)。
 * 持ち替えの動き (手を下げて上げる) もバニラのまま。閉じると本来のアイテムに戻る。
 */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class MixinFirstPersonHandsAndItems {
    @WrapOperation(
        method = "tick(Lnet/minecraft/client/player/LocalPlayer;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;", ordinal = 0),
        require = 1
    )
    private ItemStack neo$mainHand(LocalPlayer player, Operation<ItemStack> original) {
        return HeldMapView.isActive() ? HeldMapView.stack() : original.call(player);
    }

    @WrapOperation(
        method = "tick(Lnet/minecraft/client/player/LocalPlayer;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getOffhandItem()Lnet/minecraft/world/item/ItemStack;", ordinal = 0),
        require = 1
    )
    private ItemStack neo$offHand(LocalPlayer player, Operation<ItemStack> original) {
        return HeldMapView.isActive() ? ItemStack.EMPTY : original.call(player);
    }
}

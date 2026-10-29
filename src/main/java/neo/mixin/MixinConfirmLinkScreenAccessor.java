package neo.mixin;

import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.net.URI;

/** 「リンクを開きますか?」の確認画面が開こうとしている URL を読む (MixinGuiLinkConfirmation が使う)。 */
@Mixin(ConfirmLinkScreen.class)
public interface MixinConfirmLinkScreenAccessor {
    @Accessor("url")
    URI neo$getUrl();
}

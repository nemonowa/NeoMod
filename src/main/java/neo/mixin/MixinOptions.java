package neo.mixin;

import neo.event.NeoKeyMappings;
import neo.menu.config.ModManagerConfigManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Options への注入。
 * - load HEAD : 登録済みキーを keyMappings へ足す (KeyMappingHelper#registerKeyMapping の代替)。あわせて、この Mod の設定 (options.txt の neo_ の行) を読む
 * - save TAIL : バニラが options.txt を書き終えた後に、この Mod の設定の行を足す
 * - loadSelectedResourcePacks HEAD : 初めて見た同梱のリソースパックを有効の一覧へ足す (neo.bundle.BuiltinPackSelection)
 */
@Mixin(Options.class)
public abstract class MixinOptions {
    @Shadow @Final @Mutable public KeyMapping[] keyMappings;
    @Shadow public abstract java.io.File getFile();
    @Shadow public java.util.List<String> resourcePacks;

    @Inject(method = "load", at = @At("HEAD"), require = 1)
    private void neo$addKeyMappings(CallbackInfo ci) {
        this.keyMappings = NeoKeyMappings.merge(this.keyMappings);
        ModManagerConfigManager.load(this.getFile().toPath());
    }

    @Inject(method = "save", at = @At("TAIL"), require = 1)
    private void neo$saveConfig(CallbackInfo ci) {
        ModManagerConfigManager.appendTo(this.getFile().toPath());
    }

    @Inject(method = "loadSelectedResourcePacks", at = @At("HEAD"), require = 1)
    private void neo$addBundledPacks(net.minecraft.server.packs.repository.PackRepository repository, CallbackInfo ci) {
        neo.bundle.BuiltinPackSelection.addNew(this.resourcePacks);
    }
}

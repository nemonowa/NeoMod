package neo.forge;

import neo.event.NeoClientEvents;
import neo.event.NeoKeyMappings;
import neo.menu.ModManagerBootstrap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;

/** クライアント側の Forge 固有の配線 (Mod Menu 本体のみ)。 */
final class ForgeClientInit {
    private ForgeClientInit() {}

    static void run() {
        // Mod Menu の初期化は、キー登録イベントの中で行う。
        //  - Mod Menu が作る「メニューを開く」キーを、同じタイミングで Options に登録できる
        //  - 他 Mod の設定画面の拡張ポイントなど、全 Mod の構築が終わった後に読める
        RegisterKeyMappingsEvent.BUS.addListener(event -> {
            ModManagerBootstrap.init(new ForgePlatform());
            for (KeyMapping key : NeoKeyMappings.registered()) {
                event.register(key);
            }
        });

        // Fabric 版では MixinMinecraft が呼んでいるクライアント tick。キー入力 (メニューを開く) に使われる。
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> {
            NeoClientEvents.fireEndClientTick(Minecraft.getInstance());
        });
    }
}

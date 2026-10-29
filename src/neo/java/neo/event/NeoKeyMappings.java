package neo.event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Fabric API の KeyMappingHelper#registerKeyMapping の代替。
 * 登録したキーは Options#load の直前(= Options 生成時)に Options.keyMappings へ追加される
 * (neo.mixin.MixinOptions)。これで操作設定画面に表示され、options.txt に保存される。
 */
public final class NeoKeyMappings {
    private NeoKeyMappings() {}

    /** この Mod の操作設定のカテゴリ (ワールドマップ・ズームなど)。 */
    public static final KeyMapping.Category MAIN = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("neo", "main"));

    private static final List<KeyMapping> REGISTERED = new ArrayList<>();

    public static synchronized KeyMapping register(KeyMapping mapping) {
        if (!REGISTERED.contains(mapping)) REGISTERED.add(mapping);
        return mapping;
    }

    /** 登録済みキーの一覧 (Forge の RegisterKeyMappingsEvent など、1つずつ登録する側用)。 */
    public static synchronized List<KeyMapping> registered() {
        return List.copyOf(REGISTERED);
    }

    /** MixinOptions 用: 未追加の登録済みキーを足した配列を返す。 */
    public static synchronized KeyMapping[] merge(KeyMapping[] vanilla) {
        List<KeyMapping> extra = new ArrayList<>();
        for (KeyMapping m : REGISTERED) {
            if (!Arrays.asList(vanilla).contains(m)) extra.add(m);
        }
        if (extra.isEmpty()) return vanilla;
        KeyMapping[] out = Arrays.copyOf(vanilla, vanilla.length + extra.size());
        for (int i = 0; i < extra.size(); i++) out[vanilla.length + i] = extra.get(i);
        return out;
    }
}

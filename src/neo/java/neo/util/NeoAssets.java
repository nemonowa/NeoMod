package neo.util;

/**
 * この Mod が同梱するアセット (jar 内の {@code assets/<名前空間>/…}) の名前空間。
 * テクスチャ・スプライト・アイコン・翻訳 (lang) は、すべて {@code assets/neo/} の下に置く。
 *
 * <p>ここで決めるのは<b>アセットの置き場</b>だけ。次のものは別物なので、ここでは変えない:
 * 翻訳キーの接頭辞 ({@code option.modmenu.…} など。lang ファイルの置き場とは無関係)、設定の保存先 ({@code options.txt} の {@code neo_} 行)、
 * Mod の ID ({@code neo})、ネットワークのチャンネル ID、データの保存先 ({@code data/neo/}、{@code NeoData} が決める)。
 *
 * <p>他のどのクラスにも依存しない (パッケージの循環を作らない)。
 */
public final class NeoAssets {
    /** 同梱アセットの名前空間。 */
    public static final String NAMESPACE = "neo";

    private NeoAssets() {}

    /** jar 内のアセットのパス ({@code assets/neo/<relative>})。{@code IconSource} に渡す、アイコンのパス用。 */
    public static String path(String relative) {
        return "assets/" + NAMESPACE + "/" + relative;
    }
}

package neo.skinlayers;

import com.mojang.blaze3d.platform.NativeImage;
import neo.menu.config.ModManagerConfig;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerEarsModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 3D スキンレイヤー (帽子・上着・左右の袖・左右のズボンを立体にする)。設定 skin_layers (Mod Menu、既定 OFF)。
 *
 * <p>仕組み: PlayerModel#setupAnim の最後 (= 描画の直前。26.3 は ModelFeatureRenderer が描く直前に setupAnim を呼ぶ) で、
 * 各レイヤーのパーツに「そのプレイヤーのスキンから作った立体メッシュ」を持たせる。ModelPart#render はメッシュを持つパーツを
 * バニラの 2D の代わりにメッシュで描く (neo.mixin.MixinModelPartSkinLayer)。メッシュはスキンごとに作ってキャッシュする。
 * 一人称の手は setupAnim を通らないので、AvatarRenderer#renderHand の先頭で袖にメッシュを持たせる ({@link #applyHand})。
 *
 * <p>参考にした 3D Skin Layers (tr7zw Protective License) のコードは使っていない。動作 (レイヤーをボクセルにする、遠いと 2D に戻す) だけを参考にした独自実装。
 *
 * <p>OFF のときは {@link #enabled()} が false を返して即 return するだけ (割り当て・走査なし)。
 */
public final class SkinLayers {
    private static final Logger LOGGER = LoggerFactory.getLogger("neo/skinlayers");

    /** これより遠いプレイヤーは立体にしない (バニラの 2D のまま)。ブロック単位。 */
    private static final double LOD_DISTANCE = 16.0;
    private static final double LOD_DISTANCE_SQ = LOD_DISTANCE * LOD_DISTANCE;
    /** メッシュのキャッシュの上限 (スキン数 × 6 部位。古いものから捨てる)。 */
    private static final int CACHE_LIMIT = 384;

    private enum Kind {
        HAT, JACKET, LEFT_SLEEVE, RIGHT_SLEEVE, LEFT_PANTS, RIGHT_PANTS,
        /** 頭 (ブロック・アイテム・着用) の帽子の層。プレイヤーの帽子とは Cube の大きさが違うので、別に覚える。 */
        SKULL_HAT
    }

    /** 一時的に作れなかったもの (スキンの読み込み前など) をやり直すまでの時間。 */
    private static final long RETRY_MILLIS = 5_000L;

    private record Key(Identifier skin, boolean slim, Kind kind) {}

    /**
     * キャッシュの 1 件。{@code retryAtMillis} が 0 なら確定 (メッシュができた、全部透明、立体にできない形式のスキン)。
     * 0 でなければ「一時的に作れなかった」で、その時刻になったらもう一度作る (一時的な失敗を永久に覚えない)。
     */
    private record CachedMesh(SkinLayerMesh mesh, long retryAtMillis) {}

    private static final Map<Key, CachedMesh> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, CachedMesh> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    private static boolean wasEnabled;

    /**
     * 頭の描画の入口 (ブロック・アイテム・着用) で拾ったスキン。直後の SkullBlockRenderer#submitSkull が作る State が
     * {@link #takePendingSkull} で受け取る (描画スレッドだけが読み書きする)。
     */
    private static Identifier pendingSkull;

    private SkinLayers() {}

    /** 3D スキンレイヤーが ON か。ON → OFF に変わったときは、溜めたメッシュを捨てる。 */
    public static boolean enabled() {
        boolean on = ModManagerConfig.SKIN_LAYERS.getValue();
        if (!on && wasEnabled) {
            synchronized (CACHE) {
                CACHE.clear();
            }
        }
        wasEnabled = on;
        return on;
    }

    /** PlayerModel#setupAnim の最後に呼ぶ: 全レイヤーに、このプレイヤーのスキンのメッシュを持たせる (遠い・非表示のものは外す)。 */
    public static void apply(PlayerModel model, AvatarRenderState state) {
        // Deadmau5 の耳のモデルは PlayerModel を継承し、帽子・上着・袖・ズボンのパーツを持つが、Cube は空 (耳だけを足してある)。
        // ここでメッシュを付けると、キャッシュを汚したり、耳のレイヤーが全身を二重に描いたりする
        if (!enabled() || model instanceof PlayerEarsModel) {
            return;
        }

        boolean slim = ((SkinLayerModel) (Object) model).neoIsSlim();
        Identifier skin = state.skin.body().texturePath();
        if (state.distanceToCameraSq > LOD_DISTANCE_SQ) {
            skin = null;
        }

        assign(model.hat, Kind.HAT, skin, slim, true);
        assign(model.jacket, Kind.JACKET, skin, slim, true);
        assign(model.leftSleeve, Kind.LEFT_SLEEVE, skin, slim, true);
        assign(model.rightSleeve, Kind.RIGHT_SLEEVE, skin, slim, true);
        assign(model.leftPants, Kind.LEFT_PANTS, skin, slim, true);
        assign(model.rightPants, Kind.RIGHT_PANTS, skin, slim, true);
    }

    /** 頭の描画の入口で、これから作る State に渡すスキンを置く (null なら、プレイヤーの頭ではない)。 */
    public static void setPendingSkull(Identifier skin) {
        pendingSkull = skin;
    }

    /** State の生成時に呼ぶ: 置かれたスキンを受け取る。受け取ったら空にする (1 つの State にだけ渡すため)。 */
    public static Identifier takePendingSkull() {
        Identifier skin = pendingSkull;
        pendingSkull = null;
        return skin;
    }

    /** 頭の描画を置いてよいか: ON で、距離が LOD 以内か ({@code distanceSq} が負なら距離は見ない = アイテムなど)。 */
    public static boolean skullInRange(double distanceSq) {
        return enabled() && (distanceSq < 0 || distanceSq <= LOD_DISTANCE_SQ);
    }

    /** 頭モデルの setupAnim の最後に呼ぶ: 帽子の層に、そのスキンのメッシュを持たせる (スキンが無い・OFF なら外す)。 */
    public static void applySkull(ModelPart hat, Identifier skin) {
        if (hat == null) {
            return;
        }
        SkinLayerPart holder = (SkinLayerPart) (Object) hat;
        if (skin == null || !enabled() || holder.neoGetCubes().isEmpty()) {
            holder.neoSetSkinLayer(null);
            return;
        }
        holder.neoSetSkinLayer(meshFor(skin, Kind.SKULL_HAT, false, holder));
    }

    /** 一人称の手: AvatarRenderer#renderHand の先頭で、袖にメッシュを持たせる (袖の可視はこの後バニラが決めるので、ここでは見ない)。 */
    public static void applyHand(PlayerModel model, Identifier skin) {
        if (!enabled()) {
            return;
        }

        boolean slim = ((SkinLayerModel) (Object) model).neoIsSlim();
        assign(model.leftSleeve, Kind.LEFT_SLEEVE, skin, slim, false);
        assign(model.rightSleeve, Kind.RIGHT_SLEEVE, skin, slim, false);
    }

    private static void assign(ModelPart part, Kind kind, Identifier skin, boolean slim, boolean skipHidden) {
        SkinLayerPart holder = (SkinLayerPart) (Object) part;
        if (skin == null || (skipHidden && !part.visible) || holder.neoGetCubes().isEmpty()) {
            holder.neoSetSkinLayer(null);
            return;
        }
        holder.neoSetSkinLayer(meshFor(skin, kind, slim, holder));
    }

    private static SkinLayerMesh meshFor(Identifier skin, Kind kind, boolean slim, SkinLayerPart part) {
        Key key = new Key(skin, slim, kind);
        long now = System.currentTimeMillis();
        synchronized (CACHE) {
            CachedMesh cached = CACHE.get(key);
            if (cached != null && (cached.retryAtMillis() == 0 || now < cached.retryAtMillis())) {
                return cached.mesh();
            }
        }

        CachedMesh entry = create(skin, part, now);
        synchronized (CACHE) {
            CACHE.put(key, entry);
        }
        return entry.mesh();
    }

    /** スキンの絵を取り出して、メッシュを作る。取り出せなかったときは、一定時間後にやり直す印を付けて返す。 */
    private static CachedMesh create(Identifier skin, SkinLayerPart part, long now) {
        CachedMesh unavailable = new CachedMesh(null, now + RETRY_MILLIS);
        try {
            Minecraft minecraft = Minecraft.getInstance();
            AbstractTexture texture = minecraft.getTextureManager().getTexture(skin);
            if (texture instanceof DynamicTexture dynamic) {
                // ダウンロードされたスキン。登録済みのテクスチャが持つ絵をそのまま読む (複製しない・閉じない)
                NativeImage image = dynamic.getPixels();
                return image == null ? unavailable : definitive(SkinLayerMesh.build(part.neoGetCubes(), image));
            }

            // 既定のスキンなど、リソースとして入っているもの
            Optional<Resource> resource = minecraft.getResourceManager().getResource(skin);
            if (resource.isEmpty()) {
                return unavailable;
            }
            try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                return definitive(SkinLayerMesh.build(part.neoGetCubes(), image));
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("3D スキンレイヤーのメッシュを作れませんでした: {}", skin, e);
            return unavailable;
        }
    }

    /** 確定した結果: メッシュができた、全部透明 (描くものが無い)、立体にできない形式 (旧形式の 64x32 など) のどれか。 */
    private static CachedMesh definitive(SkinLayerMesh mesh) {
        return new CachedMesh(mesh == null || mesh.isEmpty() ? null : mesh, 0L);
    }
}

package neo.map;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import neo.util.NeoAssets;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 「バニラの地図アイテムを手に持った見た目」を作る。バニラの描画をそのまま使うため、
 * 表示する範囲を 1 枚のバニラの地図データ (128x128 ピクセル、スケール 0〜4) に詰めて、クライアントの地図として登録し、
 * メインハンドに「その地図を持っている」ことにする (MixinFirstPersonHandsAndItems)。
 * あとは、バニラの両手持ちの地図 (両腕・紙の枠・チェッカーの下地・装飾アイコンと名前) がそのまま描かれる。
 *  - 色: 表示範囲を、同じスケールの地図 (MapStore) から 1 ピクセルずつ写す (升目の境界に依存しない)。
 *    **中身の画像だけは、バニラの 128x128 ではなく RESOLUTION x RESOLUTION の自前のテクスチャ**にする
 *    (バニラの地図は 1 枚 128 ピクセルなので、1 ブロック 1 ドットだと見える範囲が 128 ブロックしかない)。
 *    バニラの描画は、地図の画像を「ID で渡されたテクスチャ」として四角に貼るだけなので、解像度は自由に変えられる
 *    (MixinMapRenderer で、この地図の画像を差し替える)。紙の枠・両腕・装飾はバニラのまま
 *  - 装飾: 自分の矢印・ピン・旗を、バニラの装飾 (MapDecoration) として渡す。位置は 128x128 の座標系の半ピクセル単位 (バニラと同じ。
 *    中身が高解像度でも、装飾の座標系は変わらない)
 */
public final class HeldMapView {
    /** クライアントの地図として登録する ID (本物の地図と重ならない値)。 */
    static final MapId VIRTUAL_ID = new MapId(2_000_000_000);
    /** 中身の画像の一辺のピクセル数 (バニラは 128)。スケール s で見える範囲は RESOLUTION * 2^s ブロック四方。 */
    static final int RESOLUTION = 512;
    /** 手持ちの地図の座標系 (装飾の位置に使う) = バニラの 128。 */
    private static final int VANILLA_SIZE = 128;
    /**
     * 中身の画像 (512x512 = 26 万ピクセル) の書き直しは重いので、頻度を大きく落とす (バニラの 128x128 = 1.6 万ピクセルとは違う):
     *  - 動かしている間 (表示窓が変わった): 最大でも VIEW_REFRESH_NANOS ごとに 1 回。止まった直後にも必ず 1 回 (最後の位置が出る)
     *  - 動かさずに地図の中身だけが変わった (探索が進んだ): CONTENT_REFRESH_NANOS ごとに確認して、変わっていれば書き直す
     *  - 開いた直後・スケールを変えたとき: すぐ 1 回 (開くときは、読み込む枚数の上限も増やす)
     * 画面の部品 (矢印・ピン・クリック位置) は、いま画面に出ている窓 (displayedView) を基準にするので、遅れてもずれない。
     */
    private static final long VIEW_REFRESH_NANOS = 150_000_000L;
    private static final long CONTENT_REFRESH_NANOS = 1_000_000_000L;
    /** 1 回の書き直しでディスクから読む地図の枚数の上限 (通常 / 開いた直後)。 */
    private static final int LOAD_BUDGET = 4;
    private static final int LOAD_BUDGET_FIRST = 36;
    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath(NeoAssets.NAMESPACE, "map/held");
    private static final MapItemSavedData[] DATA = new MapItemSavedData[MapStore.MAX_SCALE + 1];
    private static final byte[] SCRATCH = new byte[RESOLUTION * RESOLUTION];
    private static final byte[] COMPOSED = new byte[RESOLUTION * RESOLUTION];
    /** 地図の色 (packed id 0〜255) → ABGR。 */
    private static final int[] PALETTE = new int[256];
    /** 全画面: 下地 (チェッカー) を描かないので、未探索 (透明) の部分を地図の画像の側で塗る色 (ABGR)。 */
    private static final int FULLSCREEN_BACKDROP = 0xFF1E1E1E;

    private static DynamicTexture texture;
    private static int loadsLeft;
    /** いま画面に出ている (テクスチャに書いてある) 窓と、その中身の版。 */
    private static View displayed;
    private static long displayedStamp;
    private static long lastComposeNanos;
    private static long lastContentCheckNanos;

    private static boolean active;
    private static boolean fullscreen;
    private static boolean forceUpload;
    private static ItemStack stack;
    private static ResourceKey<Level> dimension;
    private static MapItemSavedData registered;

    /** 手持ちの地図の表示に使う、表示位置を決めた窓。origin は左上 (ピクセル 0,0) のブロック座標。 */
    record View(int scale, int originX, int originZ) {
        int blocksPerPixel() {
            return 1 << scale;
        }
    }

    /** 1 つのマーカー (ピン・旗など)。world はブロック座標。 */
    record Marker(Holder<MapDecorationType> type, double worldX, double worldZ, int rotation, Optional<Component> name) {}

    private HeldMapView() {}

    static {
        for (int i = 0; i < PALETTE.length; i++) {
            PALETTE[i] = MapColor.getColorFromPackedId((byte) i);
        }
    }

    // ---- バニラの手持ち描画への差し込み (Mixin から) ----

    public static boolean isActive() {
        return active;
    }

    /** 手持ちとして見せる地図 (毎回同じ ItemStack を返す: バニラは持ち替えを ItemStack の同一性で判定する)。 */
    public static ItemStack stack() {
        if (stack == null) {
            stack = new ItemStack(Items.FILLED_MAP);
            stack.set(DataComponents.MAP_ID, VIRTUAL_ID);
        }

        return stack;
    }

    /** 手の描画の視野角 (MixinCameraMapFullscreen から)。全画面のときだけ狭めて、地図が画面全体を覆うまで拡大する (画面の縦横比で決まる)。 */
    public static float hudFov(float vanilla) {
        if (!active || !fullscreen) {
            return vanilla;
        }

        Window window = Minecraft.getInstance().getWindow();
        return HandMapGeometry.fullscreenFov(window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    /** 全画面か (地図を開いていて、全画面のとき)。 */
    public static boolean isFullscreen() {
        return active && fullscreen;
    }

    static void setFullscreen(boolean value) {
        if (fullscreen != value) {
            displayed = null; // 未探索の部分の色が変わるので、画像を書き直す (次のフレームで必ず作り直される)
            forceUpload = true;
        }

        fullscreen = value;
    }

    static void setActive(boolean value) {
        active = value;
        if (!value) {
            fullscreen = false;
            registered = null; // 次に開くとき、必ず登録し直す
            displayed = null;
            if (texture != null) {
                Minecraft.getInstance().getTextureManager().release(TEXTURE_ID);
                texture = null;
            }
        }
    }

    /** MixinMapRenderer から: この地図の画像を、自前の高解像度のテクスチャに差し替える。 */
    public static void overrideTexture(MapId mapId, MapRenderState state) {
        if (active && texture != null && VIRTUAL_ID.equals(mapId)) {
            state.texture = TEXTURE_ID;
        }
    }

    // ---- 表示 ----

    /** 中心 (ブロック) をピクセルの升目に合わせた窓。ピクセル単位でしか動かせない (バニラの地図と同じ)。 */
    static View viewOf(int scale, double centerX, double centerZ) {
        int bpp = 1 << scale;
        int snapX = Math.floorDiv(Mth.floor(centerX), bpp) * bpp;
        int snapZ = Math.floorDiv(Mth.floor(centerZ), bpp) * bpp;
        return new View(scale, snapX - RESOLUTION / 2 * bpp, snapZ - RESOLUTION / 2 * bpp);
    }

    /**
     * 窓の内容を、手持ちの地図に反映する (毎フレーム呼ぶ)。色が変わったときだけテクスチャを作り直す。
     * markers: 窓の中に描くマーカー (ピン・旗以外)。player: 自分の矢印用。
     */
    static void show(Minecraft client, MapStore store, ResourceKey<Level> dimensionKey, View view, List<Marker> markers, Player player) {
        ClientLevel level = client.level;
        if (level == null) {
            return;
        }

        if (dimension != dimensionKey) {
            Arrays.fill(DATA, null);
            dimension = dimensionKey;
            registered = null;
        }

        boolean opening = texture == null;
        if (opening) {
            texture = new DynamicTexture(() -> "NeoMod held map", RESOLUTION, RESOLUTION, true);
            client.getTextureManager().register(TEXTURE_ID, texture);
            Arrays.fill(COMPOSED, (byte) 0);
            displayed = null;
        }

        long now = System.nanoTime();
        if (shouldRecompose(store, dimensionKey, view, now)) {
            loadsLeft = displayed == null ? LOAD_BUDGET_FIRST : LOAD_BUDGET;
            composeColors(store, dimensionKey, view);
            if (opening || forceUpload || !Arrays.equals(SCRATCH, COMPOSED)) {
                forceUpload = false;
                System.arraycopy(SCRATCH, 0, COMPOSED, 0, SCRATCH.length);
                uploadAll();
            }

            displayed = view;
            displayedStamp = contentStamp(store, dimensionKey, view);
            lastComposeNanos = now;
            lastContentCheckNanos = now;
        }

        // 装飾 (矢印・ピン・旗) は軽いので毎フレーム。基準は「いま画面に出ている窓」
        View shown = displayed;
        MapItemSavedData shownData = dataFor(shown.scale(), dimensionKey);
        List<MapDecoration> decorations = new ArrayList<>();
        collectTileDecorations(store, dimensionKey, shown, decorations);
        for (Marker marker : markers) {
            addDecoration(decorations, shown, marker.type(), marker.worldX(), marker.worldZ(), marker.rotation(), marker.name());
        }

        addPlayer(decorations, shown, player);
        shownData.addClientSideDecorations(decorations);

        // バニラの地図として登録する (装飾と、手に持つ ItemStack の MAP_ID の行き先)。画像は MixinMapRenderer で差し替える
        if (registered != shownData) {
            level.overrideMapData(VIRTUAL_ID, shownData);
            registered = shownData;
        }
    }

    /** 画面に出ている窓 (テクスチャに書いてある窓)。画面の部品の位置・クリック位置は、これを基準にする。まだ無ければ null。 */
    static View displayedView() {
        return displayed;
    }

    private static MapItemSavedData dataFor(int scale, ResourceKey<Level> dimensionKey) {
        MapItemSavedData data = DATA[scale];
        if (data == null) {
            data = MapItemSavedData.createForClient((byte) scale, true, dimensionKey);
            DATA[scale] = data;
        }

        return data;
    }

    /** 書き直すべきか (頻度を落とすための判定。画像の比較・書き直しはここが true のときだけ)。 */
    private static boolean shouldRecompose(MapStore store, ResourceKey<Level> dimensionKey, View target, long now) {
        if (displayed == null || displayed.scale() != target.scale()) {
            return true; // 開いた直後・スケールを変えたとき
        }

        if (!target.equals(displayed)) {
            return now - lastComposeNanos >= VIEW_REFRESH_NANOS; // 動かしている間は間引く (止まれば次のフレームで追いつく)
        }

        if (now - lastContentCheckNanos < CONTENT_REFRESH_NANOS) {
            return false;
        }

        lastContentCheckNanos = now;
        return contentStamp(store, dimensionKey, displayed) != displayedStamp; // 窓が同じ: 中身が変わったときだけ
    }

    /** 窓に重なる (読み込み済みの) 地図の「版」の合計。探索が進む・新しい升目が増えると変わる。画像を作らずに変化だけを調べる。 */
    private static long contentStamp(MapStore store, ResourceKey<Level> dimensionKey, View view) {
        int bpp = view.blocksPerPixel();
        int span = (RESOLUTION - 1) * bpp;
        long stamp = 0L;
        for (int cellX = MapStore.cellOf(view.originX(), view.scale()); cellX <= MapStore.cellOf(view.originX() + span, view.scale()); cellX++) {
            for (int cellZ = MapStore.cellOf(view.originZ(), view.scale()); cellZ <= MapStore.cellOf(view.originZ() + span, view.scale()); cellZ++) {
                MapStore.MapRef tile = store.peek(dimensionKey, view.scale(), cellX, cellZ);
                if (tile != null) {
                    stamp = stamp * 31L + tile.revision + 1L;
                } else if (store.has(dimensionKey, view.scale(), cellX, cellZ)) {
                    stamp = stamp * 31L + 7L; // まだ読み込んでいない地図 (読み込まれたら変わる)
                }
            }
        }

        return stamp;
    }

    /** COMPOSED をテクスチャに反映する (バニラの MapTextureManager と同じ変換: packed id → ABGR)。 */
    private static void uploadAll() {
        NativeImage pixels = texture.getPixels();
        for (int z = 0; z < RESOLUTION; z++) {
            int row = z * RESOLUTION;
            for (int x = 0; x < RESOLUTION; x++) {
                int color = PALETTE[COMPOSED[row + x] & 0xFF];
                pixels.setPixel(x, z, fullscreen && (color >>> 24) == 0 ? FULLSCREEN_BACKDROP : color);
            }
        }

        texture.upload();
    }

    /** 窓に重なる升目の範囲 (両端を含む)。 */
    private record CellRange(int firstX, int lastX, int firstZ, int lastZ) {
        static CellRange of(View view) {
            int span = (RESOLUTION - 1) * view.blocksPerPixel();
            return new CellRange(
                MapStore.cellOf(view.originX(), view.scale()),
                MapStore.cellOf(view.originX() + span, view.scale()),
                MapStore.cellOf(view.originZ(), view.scale()),
                MapStore.cellOf(view.originZ() + span, view.scale())
            );
        }
    }

    /** 窓の色を SCRATCH に作る (同じスケールの地図から 1 ピクセルずつ写す)。 */
    private static void composeColors(MapStore store, ResourceKey<Level> dimensionKey, View view) {
        int bpp = view.blocksPerPixel();
        Arrays.fill(SCRATCH, (byte) 0);

        CellRange cells = CellRange.of(view);
        for (int cellX = cells.firstX(); cellX <= cells.lastX(); cellX++) {
            for (int cellZ = cells.firstZ(); cellZ <= cells.lastZ(); cellZ++) {
                MapStore.MapRef tile = store.peek(dimensionKey, view.scale(), cellX, cellZ);
                if (tile == null && loadsLeft > 0 && store.has(dimensionKey, view.scale(), cellX, cellZ)) {
                    loadsLeft--;
                    tile = store.find(dimensionKey, view.scale(), cellX, cellZ);
                }

                if (tile == null) {
                    continue;
                }

                // 窓のピクセル (i, j) ↔ 升目のピクセル (i - shiftX, j - shiftZ)。どちらも bpp の倍数なので割り切れる
                int shiftX = (tile.originX() - view.originX()) / bpp;
                int shiftZ = (tile.originZ() - view.originZ()) / bpp;
                int iFrom = Math.max(0, shiftX);
                int iTo = Math.min(RESOLUTION - 1, shiftX + MapStore.SIZE - 1);
                int jFrom = Math.max(0, shiftZ);
                int jTo = Math.min(RESOLUTION - 1, shiftZ + MapStore.SIZE - 1);
                for (int j = jFrom; j <= jTo; j++) {
                    int source = (j - shiftZ) * MapStore.SIZE - shiftX;
                    int target = j * RESOLUTION;
                    for (int i = iFrom; i <= iTo; i++) {
                        SCRATCH[target + i] = tile.data.colors[source + i];
                    }
                }
            }
        }
    }

    /** 窓に重なる地図が持っている装飾 (本物の旗など) を、窓の座標に直して加える。 */
    private static void collectTileDecorations(MapStore store, ResourceKey<Level> dimensionKey, View view, List<MapDecoration> out) {
        int bpp = view.blocksPerPixel();
        CellRange cells = CellRange.of(view);
        for (int cellX = cells.firstX(); cellX <= cells.lastX(); cellX++) {
            for (int cellZ = cells.firstZ(); cellZ <= cells.lastZ(); cellZ++) {
                MapStore.MapRef tile = store.peek(dimensionKey, view.scale(), cellX, cellZ);
                if (tile == null) {
                    continue;
                }

                for (MapDecoration decoration : tile.data.getDecorations()) {
                    // 装飾の位置は、その地図の中心からの半ピクセル単位
                    double worldX = tile.data.centerX + decoration.x() / 2.0 * bpp;
                    double worldZ = tile.data.centerZ + decoration.y() / 2.0 * bpp;
                    addDecoration(out, view, decoration.type(), worldX, worldZ, decoration.rot(), decoration.name());
                }
            }
        }
    }

    /** 自分の矢印。窓の外にいるときは、バニラと同じく縁に寄せた「地図の外」のアイコンにする。 */
    private static void addPlayer(List<MapDecoration> out, View view, Player player) {
        double deltaX = fromCenter(player.getX() - view.originX(), view);
        double deltaZ = fromCenter(player.getZ() - view.originZ(), view);
        double yaw = Mth.wrapDegrees(player.getYRot());
        byte rotation = (byte) ((int) ((yaw + (yaw < 0.0 ? -8.0 : 8.0)) * 16.0 / 360.0));
        if (Math.abs(deltaX) <= 63.0 && Math.abs(deltaZ) <= 63.0) {
            out.add(new MapDecoration(MapDecorationTypes.PLAYER, halfPixel(deltaX), halfPixel(deltaZ), rotation, Optional.empty()));
        } else {
            Holder<MapDecorationType> type = Math.abs(deltaX) < 320.0 && Math.abs(deltaZ) < 320.0
                ? MapDecorationTypes.PLAYER_OFF_MAP : MapDecorationTypes.PLAYER_OFF_LIMITS;
            out.add(new MapDecoration(type, clampedHalfPixel(deltaX), clampedHalfPixel(deltaZ), (byte) 0, Optional.empty()));
        }
    }

    /** 窓の中にある地点だけ、バニラの装飾にして加える。 */
    private static void addDecoration(List<MapDecoration> out, View view, Holder<MapDecorationType> type, double worldX, double worldZ,
                                      int rotation, Optional<Component> name) {
        double deltaX = fromCenter(worldX - view.originX(), view);
        double deltaZ = fromCenter(worldZ - view.originZ(), view);
        if (Math.abs(deltaX) <= 63.0 && Math.abs(deltaZ) <= 63.0) {
            out.add(new MapDecoration(type, halfPixel(deltaX), halfPixel(deltaZ), (byte) rotation, name));
        }
    }

    /** 窓の左上からのブロック数 → 窓の中心からの距離 (バニラの 128 の座標系のピクセル)。装飾はこの座標系で渡す。 */
    private static double fromCenter(double blocksFromOrigin, View view) {
        return (blocksFromOrigin / view.blocksPerPixel() - RESOLUTION / 2.0) * VANILLA_SIZE / RESOLUTION;
    }

    /** バニラと同じ: 中心からのピクセル数 → 半ピクセル単位の byte。 */
    private static byte halfPixel(double pixelsFromCenter) {
        return (byte) (pixelsFromCenter * 2.0F + 0.5F);
    }

    private static byte clampedHalfPixel(double pixelsFromCenter) {
        return pixelsFromCenter <= -63.0 ? -127 : pixelsFromCenter >= 63.0 ? 127 : halfPixel(pixelsFromCenter);
    }
}

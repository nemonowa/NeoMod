package neo.zoom;

import neo.menu.config.ModManagerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * ズーム (素手での右クリック長押し)。倍率まわりの動作は Wurst Client 由来の「WI Zoom」と同じ:
 *  - 両手が空 (素手) のとき、右クリック (使用キー) を押している間だけズームする (視野角を倍率で割る)。既定の倍率は 3 倍
 *  - 画面を開いているとき・プレイヤーがいないときはズームしない。専用のキーは持たない
 *  - ズーム中は、視点移動の感度も倍率に応じて下げる (マウス感度の設定値 × 1/倍率)
 *  - ズーム中のホイールは、倍率を ×1.1 / ×0.9 する (1〜50 倍)。ホットバーは切り替わらない
 *  - 押し始めてから 0.25 秒たつまではズームしない (単押し = 通常の右クリック操作では動かない。F5 の自由視点の長押しと同じ秒数)
 *  - 右クリックを離す・手に物を持つと倍率は 3 倍に戻る
 *  - 視野角は一気に切り替えず、バニラの望遠鏡と同じ方式でなめらかに寄る/戻る (下の FOV_SMOOTH_PER_TICK)
 * 設定 zoom (Mod Menu) が OFF の間は何もしない。Mixin 側 (neo.mixin.MixinCameraZoom / MixinMouseHandlerZoom) は
 * ここのメソッドを呼ぶだけ。
 */
public final class ZoomState {
    public static final double DEFAULT_LEVEL = 3.0;
    private static final double MIN_LEVEL = 1.0;
    private static final double MAX_LEVEL = 50.0;
    private static final double STEP_IN = 1.1;
    private static final double STEP_OUT = 0.9;

    /** バニラの望遠鏡 (GameRenderer の視野角補正) と同じ: 1 tick ごとに目標との差の半分を詰める。 */
    private static final double FOV_SMOOTH_PER_TICK = 0.5;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double MAX_FRAME_SECONDS = 0.25;
    private static final double SNAP_EPSILON = 0.0005;

    /** 長押しとみなすまでの時間。これより短い押しはズームしない。 */
    private static final long HOLD_DELAY_NANOS = 250_000_000L;

    private static double level = DEFAULT_LEVEL;
    /** 右クリックを押し始めた時刻 (0 = 押していない)。 */
    private static long holdStartNanos;
    /** 視野角に掛ける現在の係数 (1 = ズームなし、1/level = ズーム完了)。 */
    private static double factor = 1.0;
    private static long lastNanos;

    private ZoomState() {}

    /** いまズーム中か (設定 ON かつ素手で右クリックを押している)。OFF のときは設定を 1 回引いて戻るだけ。 */
    public static boolean isZooming() {
        if (!ModManagerConfig.ZOOM.getValue()) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        boolean held = player != null && client.gui.screen() == null && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()
            && client.options.keyUse.isDown();
        if (!held) {
            holdStartNanos = 0L;
            level = DEFAULT_LEVEL; // 離したら倍率を戻す
            return false;
        }

        long now = System.nanoTime();
        if (holdStartNanos == 0L) {
            holdStartNanos = now;
        }

        return now - holdStartNanos >= HOLD_DELAY_NANOS; // 単押しでは発火しない
    }

    /**
     * 現在の係数を、経過時間に応じて目標へ近づけて返す (フレームレートに依存しない)。
     * OFF・ズームなしで係数が 1 のときは、時刻だけ更新して即戻る。
     */
    private static double smoothedFactor() {
        long now = System.nanoTime();
        double seconds = lastNanos == 0L ? 0.0 : Math.min((now - lastNanos) / 1.0e9, MAX_FRAME_SECONDS);
        lastNanos = now;

        boolean zooming = isZooming();
        if (!zooming && factor == 1.0) {
            return 1.0;
        }

        double target = zooming ? 1.0 / level : 1.0;
        double alpha = 1.0 - Math.pow(FOV_SMOOTH_PER_TICK, seconds * TICKS_PER_SECOND);
        factor += (target - factor) * alpha;
        if (Math.abs(target - factor) < SNAP_EPSILON) {
            factor = target;
        }

        return factor;
    }

    /** 視野角 (度) にズームを掛ける。ズームしていなければそのまま返す。 */
    public static float applyFov(float fov) {
        double f = smoothedFactor();
        return f == 1.0 ? fov : (float) (fov * f);
    }

    /** 視点移動の感度 (設定値 0〜1) にズームを掛ける。視野角と同じ係数なので、寄る/戻る途中も滑らかにつながる。 */
    public static double applySensitivity(double sensitivity) {
        return sensitivity * smoothedFactor();
    }

    /**
     * ホイールを倍率の変更に使う。使ったとき (= ホットバーの切り替えなどを止めるとき) は true。
     * 画面を開いているとき・オーバーレイ表示中は使わない。
     */
    public static boolean onScroll(double yOffset) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.screen() != null || client.gui.overlay() != null || !isZooming()) {
            return false;
        }

        if (yOffset > 0.0) {
            level *= STEP_IN;
        } else if (yOffset < 0.0) {
            level *= STEP_OUT;
        }

        level = Mth.clamp(level, MIN_LEVEL, MAX_LEVEL);
        return true;
    }
}

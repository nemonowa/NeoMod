package neo.map;

/**
 * バニラの両手持ちの地図 (FirstPersonHandsAndItemsRenderer#renderTwoHandedMap) が、画面のどこに描かれるかを計算する。
 * 地図を斜めに傾ける処理 (MixinFirstPersonHandsAndItemsRenderer で 0 にする) と、手を完全に上げた状態を前提にして、
 * 3D の変換を画面 (GUI 座標) の長方形に直す。手の描画の視野角は固定の 70° (Camera#calculateHudFov)。
 *
 * 地図のピクセル (px, py: 0〜128) のカメラ空間の位置 (バニラの変換をそのまま辿る):
 *   X = 0.76 * (px/128 - 0.5)、Y = 0.04 - 0.76 * (py/128 - 0.5)、Z = -0.72
 *   (0.76 = renderTwoHandedMap の scale(2) × renderMap の scale(0.38))
 * 全画面: 手の描画の視野角を 70° から狭めて、地図が画面全体を覆うまで拡大する (fullscreenFov)。地図は正方形なので、画面が横長なら
 *   「幅」を基準に拡大し (地図の左右の端が画面の左右の端に接する。上下は地図の一部が画面の外に出る)、縦長なら「下端」を基準にする。
 *   地図の中心が 0.04 だけ上にずれているので、地図の下端 (中心から 0.34 下) が画面の下端に接する以上には拡大しない = 下に隙間を作らない。
 *   視野角を変えるだけなので、両腕・紙の枠も同じ倍率で拡大される (MixinCameraMapFullscreen)。画面の縦横比に依存するので、画面の大きさ (GUI 座標) を渡す。
 * 透視投影: 画面の高さ H に対して、垂直方向 Y/(0.72 * tan(35°)) * H/2、水平方向は同じ倍率 (縦横で同じスケール)。
 */
final class HandMapGeometry {
    private static final double MAP_WORLD_SIZE = 0.76;
    private static final double MAP_CENTER_Y = 0.04;
    private static final double DISTANCE = 0.72;
    /** バニラの手の描画の視野角 (Camera#calculateHudFov の定数)。 */
    static final float NORMAL_FOV = 70.0F;
    private static final double HALF_FOV_TAN = Math.tan(Math.toRadians(NORMAL_FOV / 2.0));

    /** left / top: 地図の左上の GUI 座標、pixel: バニラの地図 (128 ピクセル) の 1 ピクセルが何 GUI ピクセルか (地図全体 = 128 * pixel)。 */
    record Rect(double left, double top, double pixel) {
        double size() {
            return 128.0 * pixel;
        }

        boolean contains(double x, double y) {
            return x >= left && y >= top && x < left + size() && y < top + size();
        }
    }

    private HandMapGeometry() {}

    /**
     * 全画面: 地図が画面全体を覆う、垂直の半視野角の tan。
     *  - 幅: 地図の半幅 (0.38) が画面の半幅 (縦横比 * 画面の半高さ) 以上になる
     *  - 下端: 地図の下端 (中心 0.04 から 0.38 下 = カメラ空間で 0.34 下) が画面の下端以上になる (縦長の画面では、こちらが効く)
     * 小さいほうが強い拡大。通常 (70°) より広がることはない。
     */
    private static double fullscreenHalfFovTan(int guiWidth, int guiHeight) {
        double aspect = (double) guiWidth / Math.max(1, guiHeight);
        double byWidth = MAP_WORLD_SIZE / 2.0 / (DISTANCE * aspect);
        double byBottom = (MAP_WORLD_SIZE / 2.0 - MAP_CENTER_Y) / DISTANCE;
        return Math.min(HALF_FOV_TAN, Math.min(byWidth, byBottom));
    }

    /** 全画面のときの、手の描画の視野角 (度)。画面の大きさは GUI 座標 (Screen の width / height と同じ値)。 */
    static float fullscreenFov(int guiWidth, int guiHeight) {
        return (float) Math.toDegrees(2.0 * Math.atan(fullscreenHalfFovTan(guiWidth, guiHeight)));
    }

    static Rect of(int guiWidth, int guiHeight) {
        return of(guiWidth, guiHeight, false);
    }

    static Rect of(int guiWidth, int guiHeight, boolean fullscreen) {
        double unit = guiHeight / 2.0 / (DISTANCE * (fullscreen ? fullscreenHalfFovTan(guiWidth, guiHeight) : HALF_FOV_TAN));
        double pixel = MAP_WORLD_SIZE / 128.0 * unit;
        double left = guiWidth / 2.0 - 64.0 * pixel;
        double top = guiHeight / 2.0 - (MAP_CENTER_Y + MAP_WORLD_SIZE / 2.0) * unit;
        return new Rect(left, top, pixel);
    }
}

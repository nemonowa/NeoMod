package neo.skinlayers;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * スキンの 1 レイヤー (帽子・上着・袖・ズボンのどれか 1 つ) を、ピクセルごとの小さな立方体 (ボクセル) にした立体メッシュ。
 *
 * <p>作り方: バニラのレイヤー用 Cube (例: jacket の Cube) が持つ 6 枚のポリゴンから、
 * 「スキン上のピクセル → 3D の位置」の対応と、頂点の巻き方向・法線の向きを<b>そのまま読み取る</b> (向き・ミラーを自分で決め打ちしない)。
 * 不透明なピクセルごとに、その面の位置を中心に内側 {@link #INWARD_PX}・外側 {@link #OUTWARD_PX} の厚さの立方体があるものとして、見える面だけを作る:
 * <ul>
 *   <li>内側の面 (体に隠れる) と、隣も不透明な側面は作らない</li>
 *   <li><b>外側の面は、不透明なピクセルの長方形ごとに 1 枚にまとめる</b> (貪欲法)。UV はバニラの面と同じくスキン上の実際の範囲を与えるので、
 *       ピクセルごとに作った場合と色は同じ (スキンのテクスチャは最近傍で引かれる)</li>
 *   <li><b>側面は、隣り合うピクセルが同じ色なら 1 枚にまとめる</b> (色はピクセルの中心の UV で引くので、同色なら見た目が変わらない)</li>
 *   <li>面の境目 (角) の側面は、隣の面のボクセルに半分だけ隠れて半分は見える (角を埋める) ので、省かない</li>
 * </ul>
 *
 * <p>できあがりは float 配列に固めてあり (Cube / Polygon などのオブジェクトは残さない)、描画は {@link #render} だけ。
 * 角で隣り合う別の面のボクセルは、つなぎ合わせていない (角にすき間が出ることがある)。
 */
public final class SkinLayerMesh {
    /** バニラのモデルが前提にしているテクスチャの大きさ (64x64。HD スキンはこの倍数)。 */
    private static final int MODEL_TEXTURE_SIZE = 64;
    /** ボクセルがバニラのレイヤー面から内側 (体の側) へ張り出す量 (モデルのピクセル単位)。体に埋まる側なので見えない。 */
    private static final float INWARD_PX = 0.5f;
    /**
     * ボクセルがバニラのレイヤー面から外側へ張り出す量 (モデルのピクセル単位)。
     * 0.5 にすると、帽子 (バニラで頭から 0.5px) の外面が頭の中心から 5.0px になり、OUTER 防具のヘルメット (頭 +1.0 = 5.0px) と
     * 同一平面になって Z-fighting する。わずかに手前で止めて、防具の内側に収める (上着・袖・ズボンは 0.25+0.4 で防具より内側)。
     */
    private static final float OUTWARD_PX = 0.4f;
    /** モデル座標 (ピクセル) をブロック単位にする係数。ModelPart.Vertex#worldX と同じ 1/16。 */
    private static final float PX = 1.0f / 16.0f;
    /** 1 枚の四角形あたりの float 数: 4 頂点 × (x, y, z, u, v) + 法線 (x, y, z)。 */
    private static final int FLOATS_PER_QUAD = 4 * 5 + 3;

    private final float[] data;
    private final int quadCount;

    private SkinLayerMesh(float[] data) {
        this.data = data;
        this.quadCount = data.length / FLOATS_PER_QUAD;
    }

    /** 四角形が 0 枚か (描くものが無い)。 */
    public boolean isEmpty() {
        return quadCount == 0;
    }

    public int quadCount() {
        return quadCount;
    }

    /**
     * メッシュを描く。{@code pose} はパーツの平行移動・回転を済ませたもの (ModelPart#render の Cube#compile と同じ位置)。
     * 頂点の書き出しは Cube#compile と同じ 12 引数の addVertex を使う。
     */
    public void render(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color) {
        Matrix4f matrix = pose.pose();
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        for (int q = 0; q < quadCount; q++) {
            int base = q * FLOATS_PER_QUAD;
            pose.transformNormal(data[base + 20], data[base + 21], data[base + 22], normal);
            for (int v = 0; v < 4; v++) {
                int i = base + v * 5;
                matrix.transformPosition(data[i], data[i + 1], data[i + 2], position);
                consumer.addVertex(position.x(), position.y(), position.z(), color, data[i + 3], data[i + 4], overlay, light,
                        normal.x(), normal.y(), normal.z());
            }
        }
    }

    /**
     * バニラのレイヤー Cube とスキンの絵から立体メッシュを作る。
     * 絵が 64 の倍数の正方形でない (旧形式の 64x32 など) ときは null (レイヤーが無いので 2D のまま)。
     */
    public static SkinLayerMesh build(List<ModelPart.Cube> cubes, NativeImage image) {
        return build(cubes, image.getWidth(), image.getHeight(), image::getPixel);
    }

    /**
     * {@link #build(List, NativeImage)} の本体。絵そのものではなく、座標 (x, y) のピクセルの値 (32 bit) を返す関数を受け取る (検証しやすくするため)。
     * 上位 8 bit がアルファ (ARGB / ABGR どちらでも同じ)。0 なら透明。同じ値なら同じ色として扱う。
     */
    public static SkinLayerMesh build(List<ModelPart.Cube> cubes, int width, int height, IntBinaryOperator pixelAt) {
        if (width < MODEL_TEXTURE_SIZE || width % MODEL_TEXTURE_SIZE != 0 || height != width) {
            return null;
        }

        FloatArrayList out = new FloatArrayList(4096);
        for (ModelPart.Cube cube : cubes) {
            float[] center = centerOf(cube);
            for (ModelPart.Polygon polygon : cube.polygons) {
                addPolygon(out, polygon, center, width, pixelAt);
            }
        }
        return new SkinLayerMesh(out.toFloatArray());
    }

    /** Cube の中心 (全頂点の最小・最大の中点)。ポリゴンの「外向き」を決めるのに使う。 */
    private static float[] centerOf(ModelPart.Cube cube) {
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (ModelPart.Polygon polygon : cube.polygons) {
            for (ModelPart.Vertex vertex : polygon.vertices()) {
                float[] p = {vertex.worldX(), vertex.worldY(), vertex.worldZ()};
                for (int a = 0; a < 3; a++) {
                    min[a] = Math.min(min[a], p[a]);
                    max[a] = Math.max(max[a], p[a]);
                }
            }
        }
        return new float[] {(min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2};
    }

    private static void addPolygon(FloatArrayList out, ModelPart.Polygon polygon, float[] cubeCenter, int imageWidth,
            IntBinaryOperator pixelAt) {
        ModelPart.Vertex[] vertices = polygon.vertices();
        if (vertices.length != 4) {
            return;
        }

        // 頂点の位置 (ブロック単位) と UV (64x64 のピクセル単位)
        float[][] p = new float[4][3];
        float[] u = new float[4];
        float[] v = new float[4];
        float[] centroid = new float[3];
        for (int k = 0; k < 4; k++) {
            p[k][0] = vertices[k].worldX();
            p[k][1] = vertices[k].worldY();
            p[k][2] = vertices[k].worldZ();
            u[k] = vertices[k].u() * MODEL_TEXTURE_SIZE;
            v[k] = vertices[k].v() * MODEL_TEXTURE_SIZE;
            for (int a = 0; a < 3; a++) {
                centroid[a] += p[k][a] / 4;
            }
        }

        // 外向きの軸 (0=x, 1=y, 2=z) と向き (+1 / -1): Cube の中心からこの面の中心への、最も大きい成分
        int axis = 0;
        float best = 0;
        for (int a = 0; a < 3; a++) {
            float d = centroid[a] - cubeCenter[a];
            if (Math.abs(d) > best) {
                best = Math.abs(d);
                axis = a;
            }
        }
        if (best == 0) {
            return;
        }
        int outward = centroid[axis] - cubeCenter[axis] > 0 ? 1 : -1;

        // 頂点の巻き方向: バニラの面が「外向きに対して」どちら回りか (全面で同じはずだが、この面から直接読む)
        float[] e1 = sub(p[1], p[0]);
        float[] e2 = sub(p[2], p[0]);
        float windingSign = Math.signum(cross(e1, e2)[axis] * outward);
        if (windingSign == 0) {
            return;
        }

        // 法線の向き: バニラの法線が外向きと同じ向きか逆か (モデルの Y 軸が下向きのため、規約をバニラに合わせる)
        float vanillaNormal = vanillaNormalComponent(polygon, axis);
        float normalSign = vanillaNormal == 0 ? 1 : Math.signum(vanillaNormal * outward);

        // UV → 位置の対応 (3 頂点から線形に解く): P(u, v) = p0 + A * (u - u0) + B * (v - v0)
        float du1 = u[1] - u[0];
        float dv1 = v[1] - v[0];
        float du3 = u[3] - u[0];
        float dv3 = v[3] - v[0];
        float det = du1 * dv3 - du3 * dv1;
        if (Math.abs(det) < 1.0e-6f) {
            return;
        }
        float[] e3 = sub(p[3], p[0]);
        float[] a = new float[3];
        float[] b = new float[3];
        for (int c = 0; c < 3; c++) {
            a[c] = (e1[c] * dv3 - e3[c] * dv1) / det;
            b[c] = (e3[c] * du1 - e1[c] * du3) / det;
        }

        // この面が使うスキン上のピクセルの範囲 (64x64 のピクセル単位)
        int uMin = Math.round(Math.min(Math.min(u[0], u[1]), Math.min(u[2], u[3])));
        int uMax = Math.round(Math.max(Math.max(u[0], u[1]), Math.max(u[2], u[3])));
        int vMin = Math.round(Math.min(Math.min(v[0], v[1]), Math.min(v[2], v[3])));
        int vMax = Math.round(Math.max(Math.max(v[0], v[1]), Math.max(v[2], v[3])));
        int cols = uMax - uMin;
        int rows = vMax - vMin;
        if (cols <= 0 || rows <= 0) {
            return;
        }

        boolean[][] opaque = new boolean[cols][rows];
        int[][] color = new int[cols][rows];
        int scale = imageWidth / MODEL_TEXTURE_SIZE;
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                // HD スキンは、1 ピクセルに相当する小区画の真ん中の 1 点で判定する
                int sx = (uMin + i) * scale + scale / 2;
                int sy = (vMin + j) * scale + scale / 2;
                int value = pixelAt.applyAsInt(sx, sy);
                color[i][j] = value;
                opaque[i][j] = (value >>> 24) != 0;
            }
        }

        FaceFrame frame = new FaceFrame(p[0], a, b, u[0], v[0], uMin, vMin, axis, outward, windingSign, normalSign);
        addOuterFaces(out, frame, opaque);
        addSideFaces(out, frame, opaque, color, true); // u 方向の側面 (左右)
        addSideFaces(out, frame, opaque, color, false); // v 方向の側面 (上下)
    }

    /** 1 つのバニラの面 (ポリゴン) について、ピクセルの格子 → 位置・UV の対応と、巻き方向・法線の向きをまとめたもの。 */
    private record FaceFrame(float[] origin, float[] a, float[] b, float u0, float v0, int uMin, int vMin, int axis, int outward,
            float windingSign, float normalSign) {
        /** 格子の点 (i, j) (ピクセルの角) の、バニラの面の平面上の位置。 */
        float[] point(int i, int j) {
            return at(origin, a, b, uMin + i - u0, vMin + j - v0);
        }
    }

    /** 外側の面: 不透明なピクセルの長方形ごとに 1 枚 (貪欲法)。UV はスキン上の実際の範囲。 */
    private static void addOuterFaces(FloatArrayList out, FaceFrame f, boolean[][] opaque) {
        int cols = opaque.length;
        int rows = opaque[0].length;
        boolean[][] used = new boolean[cols][rows];
        float plane = f.point(0, 0)[f.axis()] + f.outward() * OUTWARD_PX * PX;

        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                if (!opaque[i][j] || used[i][j]) {
                    continue;
                }

                int w = 1;
                while (i + w < cols && opaque[i + w][j] && !used[i + w][j]) {
                    w++;
                }
                int h = 1;
                grow:
                while (j + h < rows) {
                    for (int x = i; x < i + w; x++) {
                        if (!opaque[x][j + h] || used[x][j + h]) {
                            break grow;
                        }
                    }
                    h++;
                }
                for (int x = i; x < i + w; x++) {
                    for (int y = j; y < j + h; y++) {
                        used[x][y] = true;
                    }
                }

                int[][] corner = {{i, j}, {i + w, j}, {i + w, j + h}, {i, j + h}};
                float[][] q = new float[4][];
                float[][] uv = new float[4][2];
                for (int k = 0; k < 4; k++) {
                    q[k] = f.point(corner[k][0], corner[k][1]);
                    q[k][f.axis()] = plane;
                    uv[k][0] = (f.uMin() + corner[k][0]) / (float) MODEL_TEXTURE_SIZE;
                    uv[k][1] = (f.vMin() + corner[k][1]) / (float) MODEL_TEXTURE_SIZE;
                }
                addQuad(out, q, uv, f.axis(), f.outward(), f.windingSign(), f.normalSign());
            }
        }
    }

    /**
     * 側面: 隣のピクセルが透明 (または面の外) の辺ごとに出す。同じ向きの辺が続いて同じ色なら 1 枚にまとめる。
     * {@code alongU} = true: 左右の辺 (隣は i±1、続く向きは j)。false: 上下の辺 (隣は j±1、続く向きは i)。
     */
    private static void addSideFaces(FloatArrayList out, FaceFrame f, boolean[][] opaque, int[][] color, boolean alongU) {
        int cols = opaque.length;
        int rows = opaque[0].length;
        float[] direction = alongU ? f.a() : f.b();
        int sideAxis = dominantAxis(direction);
        float plane = f.point(0, 0)[f.axis()];

        for (int step = -1; step <= 1; step += 2) {
            int sign = direction[sideAxis] * step > 0 ? 1 : -1;
            // 走査: 外側のループが辺の位置、内側のループが続く向き
            int outerCount = alongU ? cols : rows;
            int innerCount = alongU ? rows : cols;
            for (int o = 0; o < outerCount; o++) {
                int n = 0;
                while (n < innerCount) {
                    int i = alongU ? o : n;
                    int j = alongU ? n : o;
                    if (!isOpenSide(opaque, i, j, alongU, step)) {
                        n++;
                        continue;
                    }

                    int runStart = n;
                    int value = color[i][j];
                    n++;
                    while (n < innerCount) {
                        int ni = alongU ? o : n;
                        int nj = alongU ? n : o;
                        if (!isOpenSide(opaque, ni, nj, alongU, step) || color[ni][nj] != value) {
                            break;
                        }
                        n++;
                    }
                    int runEnd = n - 1;

                    // 走る範囲全体の箱 (ピクセル 1 個分の厚みで、続く向きに伸ばす)
                    int i0 = alongU ? o : runStart;
                    int i1 = alongU ? o : runEnd;
                    int j0 = alongU ? runStart : o;
                    int j1 = alongU ? runEnd : o;
                    float[] c0 = f.point(i0, j0);
                    float[] c1 = f.point(i1 + 1, j1 + 1);
                    float[] lo = new float[3];
                    float[] hi = new float[3];
                    for (int c = 0; c < 3; c++) {
                        lo[c] = Math.min(c0[c], c1[c]);
                        hi[c] = Math.max(c0[c], c1[c]);
                    }
                    // 外向きが + / - のどちらでも、外側は OUTWARD_PX、内側は INWARD_PX だけ張り出す
                    float outer = plane + f.outward() * OUTWARD_PX * PX;
                    float inner = plane - f.outward() * INWARD_PX * PX;
                    lo[f.axis()] = Math.min(outer, inner);
                    hi[f.axis()] = Math.max(outer, inner);

                    // 色は、まとめた先頭のピクセルの中心の UV で引く (まとめたものは全部同色)
                    float pu = (f.uMin() + i0 + 0.5f) / MODEL_TEXTURE_SIZE;
                    float pv = (f.vMin() + j0 + 0.5f) / MODEL_TEXTURE_SIZE;
                    addFace(out, lo, hi, sideAxis, sign, f.windingSign(), f.normalSign(), pu, pv);
                }
            }
        }
    }

    /** (i, j) が不透明で、{@code step} 側の隣が透明または面の外 (= その辺に側面が要る) か。 */
    private static boolean isOpenSide(boolean[][] opaque, int i, int j, boolean alongU, int step) {
        if (!opaque[i][j]) {
            return false;
        }
        int ni = alongU ? i + step : i;
        int nj = alongU ? j : j + step;
        boolean neighborOpaque = ni >= 0 && nj >= 0 && ni < opaque.length && nj < opaque[0].length && opaque[ni][nj];
        return !neighborOpaque;
    }

    private static int dominantAxis(float[] v) {
        int axis = 0;
        for (int c = 1; c < 3; c++) {
            if (Math.abs(v[c]) > Math.abs(v[axis])) {
                axis = c;
            }
        }
        return axis;
    }

    /** 箱 (lo〜hi) の、軸 {@code axis} の {@code sign} 側の面を 1 枚 (四角形) 足す。色は (pu, pv) の 1 点で引く。 */
    private static void addFace(FloatArrayList out, float[] lo, float[] hi, int axis, int sign, float windingSign,
            float normalSign, float pu, float pv) {
        int b = (axis + 1) % 3;
        int c = (axis + 2) % 3;
        float coord = sign > 0 ? hi[axis] : lo[axis];

        float[][] q = new float[4][3];
        float[][] corner = {{lo[b], lo[c]}, {hi[b], lo[c]}, {hi[b], hi[c]}, {lo[b], hi[c]}};
        for (int k = 0; k < 4; k++) {
            q[k][axis] = coord;
            q[k][b] = corner[k][0];
            q[k][c] = corner[k][1];
        }
        float[][] uv = {{pu, pv}, {pu, pv}, {pu, pv}, {pu, pv}};
        addQuad(out, q, uv, axis, sign, windingSign, normalSign);
    }

    /** 四角形を 1 枚足す。巻き方向がバニラの面と逆なら頂点の並びを反転 (UV も一緒に入れ替える)。法線は軸方向。 */
    private static void addQuad(FloatArrayList out, float[][] q, float[][] uv, int axis, int sign, float windingSign,
            float normalSign) {
        float geometric = cross(sub(q[1], q[0]), sub(q[2], q[0]))[axis] * sign;
        if (Math.signum(geometric) != windingSign) {
            float[] tmp = q[1];
            q[1] = q[3];
            q[3] = tmp;
            float[] tmpUv = uv[1];
            uv[1] = uv[3];
            uv[3] = tmpUv;
        }

        for (int k = 0; k < 4; k++) {
            out.add(q[k][0]);
            out.add(q[k][1]);
            out.add(q[k][2]);
            out.add(uv[k][0]);
            out.add(uv[k][1]);
        }

        float[] normal = new float[3];
        normal[axis] = sign * normalSign;
        out.add(normal[0]);
        out.add(normal[1]);
        out.add(normal[2]);
    }

    private static float vanillaNormalComponent(ModelPart.Polygon polygon, int axis) {
        return switch (axis) {
            case 0 -> polygon.normal().x();
            case 1 -> polygon.normal().y();
            default -> polygon.normal().z();
        };
    }

    private static float[] at(float[] origin, float[] a, float[] b, float du, float dv) {
        return new float[] {
            origin[0] + a[0] * du + b[0] * dv,
            origin[1] + a[1] * du + b[1] * dv,
            origin[2] + a[2] * du + b[2] * dv,
        };
    }

    private static float[] sub(float[] x, float[] y) {
        return new float[] {x[0] - y[0], x[1] - y[1], x[2] - y[2]};
    }

    private static float[] cross(float[] x, float[] y) {
        return new float[] {x[1] * y[2] - x[2] * y[1], x[2] * y[0] - x[0] * y[2], x[0] * y[1] - x[1] * y[0]};
    }
}

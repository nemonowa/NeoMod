package neo.cinematic;

import neo.cinematic.CinematicShots.Cut;
import neo.event.NeoClientEvents;
import neo.menu.config.ModManagerConfig;
import neo.util.CameraTypeSwitch;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/**
 * AFK シネマティック: 何も操作せずに放置時間 (設定 afk_cinematic_idle。既定 3 分、10 秒〜10 分) が過ぎると、プレイヤーの周りを映画のようにカメラが動き回る (設定 afk_cinematic、既定 ON)。
 * 独自実装 (参考: Ji AFK Cinematic 2.3.1 の動作 (MIT、jiory_)。コードは取り込んでいない。音楽とキー割り当ては持たない)。
 *
 * 動作:
 *  - 画面を開いていない状態で、キー・マウスの操作が {@link #idleTicks()} 続けて無いと始まる。放置の判定は Mixin から届く操作 (onActivity) と、毎 tick の数え上げ
 *  - F1 の扱い (バニラの F1 の処理 Hud#toggle を、neo.mixin.MixinGuiCinematic が肩代わりする): GUI を隠した状態でもう一度 F1 を押すと、GUI を出さずにシネマティックが始まる。
 *    シネマティック中に F1 を押すと、シネマティックが終わって GUI が戻る (放置で始まった場合も)。F1 は放置の解除としては数えない (上の onKeyPress)
 *    手動で始めた場合は、ほかの操作で終わっても GUI が出た通常の画面に戻る (F1 で隠した状態は戻さない)
 *  - 始まると、視点を三人称にし、HUD を隠し (F1 と同じ Hud#toggle。チャットは残す)、上下に黒帯を出し、{@link CinematicShots} の計画に沿って 10 秒ごとにカットを切り替える
 *  - カメラの位置・向きは Camera#alignWithEntity の末尾で上書きする (neo.mixin.MixinCameraCinematic)。プレイヤー自身の位置・向きは変えず、サーバーへも何も送らない
 *  - 終わるのは: キー・マウスの操作 / 画面が開く / ダメージ / 死亡 / 設定を OFF / ワールドを離れる。
 *    ダメージと死亡で止まったときは、次の操作があるまで再開しない
 * 設定 OFF のとき: 毎 tick 設定を 1 回引いて即 return。入力のフックは状態変数を見るだけで戻る (設定も引かない)。
 */
public final class AfkCinematic {
    /** 設定の 1 刻み (10 秒) の tick 数。 */
    private static final int IDLE_STEP_TICKS = 10 * 20;
    /** 1 カットの長さ (tick。10 秒)。 */
    static final int SHOT_TICKS = 10 * 20;

    private static final Random RANDOM = new Random();

    private static int idleTicks;
    private static boolean lockedOut;
    private static boolean active;

    // 始まったときに保存して、終わるときに戻すもの
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;
    /** 終わるときに戻す HUD の状態 (true = 隠す)。 */
    private static boolean hudWasHidden;

    // 進行
    private static double baseYaw;
    private static List<Cut> plan = List.of();
    private static int cutIndex;
    private static int shotTicks;
    /** 壁に当たって手前に寄せた割合 (1 = 寄せない)。縮むときは即座に、戻るときはゆっくり。 */
    private static double clipFactor = 1.0;

    private AfkCinematic() {}

    /** カメラの位置と向き (Y 回転 / X 回転。Camera と同じ度)。 */
    public record View(Vec3 position, float yaw, float pitch) {}

    /** neo のクライアント初期化 (NeoCore) から呼ばれる。 */
    public static void init() {
        NeoClientEvents.registerEndClientTick(AfkCinematic::onClientTick);
    }

    /**
     * HUD を隠している間、チャットだけを描き直すか。始める前から F1 で HUD を隠していたなら、チャットも出さない。
     * HUD が出ている間は、バニラがチャットも描くので、ここでは描かない (二重にしない)。
     */
    public static boolean showsChat() {
        return active && !hudWasHidden && Minecraft.getInstance().gui.hud.isHidden();
    }

    // ---- 操作 (Mixin から) ----

    /**
     * キーの操作があった。シネマティック中の「GUI の表示を切り替えるキー」(既定 F1) だけは、操作と見なさない
     * (F1 の処理は {@link #onToggleGui} が行う。ここで解除すると、そのあとのバニラの切り替えで GUI がまた隠れてしまう)。それ以外は {@link #onActivity()}。
     */
    public static void onKeyPress(KeyEvent event) {
        if (active && Minecraft.getInstance().options.keyToggleGui.matches(event)) {
            return;
        }
        onActivity();
    }

    /** キー・マウスの操作があった。放置の数え直し。シネマティック中なら終わらせる。 */
    public static void onActivity() {
        if (idleTicks == 0 && !active && !lockedOut) {
            return; // 数えていない / 動いていない: OFF のときも、ここで戻る
        }

        idleTicks = 0;
        lockedOut = false;
        if (active) {
            stop(Minecraft.getInstance());
        }
    }

    /** マウスの移動 (相対量)。動いていないイベントは操作と見なさない。 */
    public static void onMouseMove(double xRel, double yRel) {
        if (xRel != 0.0 || yRel != 0.0) {
            onActivity();
        }
    }

    // ---- 毎 tick ----

    private static void onClientTick(Minecraft client) {
        LocalPlayer player = client.player;
        if (!ModManagerConfig.AFK_CINEMATIC.getValue() || player == null || client.level == null) {
            if (idleTicks != 0 || active || lockedOut) {
                reset(client); // OFF にした / ワールドを離れた: 状態を捨てて、元の視点・HUD に戻す
            }
            return;
        }

        if (client.gui.screen() != null) {
            // 画面を開いている間は、放置とは見なさない
            if (active) {
                stop(client);
            }
            idleTicks = 0;
            return;
        }

        if (active) {
            tickActive(client, player);
            return;
        }

        if (lockedOut || player.isDeadOrDying() || player.isSleeping()) {
            return;
        }

        if (++idleTicks >= idleTicks()) {
            start(client, player);
        }
    }

    /** 放置してから始まるまでの tick (設定 afk_cinematic_idle は 10 秒単位。既定 18 = 3 分 = 3600 tick)。 */
    public static int idleTicks() {
        return ModManagerConfig.AFK_CINEMATIC_IDLE.getValue() * IDLE_STEP_TICKS;
    }

    private static void tickActive(Minecraft client, LocalPlayer player) {
        if (player.isDeadOrDying() || player.hurtTime > 0) {
            stop(client);
            lockedOut = true; // 次の操作があるまで再開しない
            return;
        }

        if (++shotTicks >= SHOT_TICKS) {
            nextCut();
        }
    }

    // ---- 開始・終了 ----

    private static void start(Minecraft client, LocalPlayer player) {
        idleTicks = 0;
        if (client.getCameraEntity() != player) {
            return; // 他のエンティティの視点で見ているときは始めない (次の放置時間の後にまた判定)
        }

        begin(client, player, false);
    }

    /**
     * F1 (GUI の切り替え) の処理の肩代わり (Gui#handleKeybinds の Hud#toggle から)。処理したら true (バニラの切り替えは行わない)。
     *  - シネマティック中: シネマティックを終えて、GUI を戻す (始まり方に関わらず GUI が出た通常の画面になる)
     *  - GUI を隠している状態: GUI を出さずに、シネマティックを始める
     *  - それ以外 (OFF のとき・GUI が出ているとき・始められないとき): false。バニラの切り替えをそのまま行う
     */
    public static boolean onToggleGui(Hud hud) {
        Minecraft client = Minecraft.getInstance();
        if (active) {
            hudWasHidden = false; // GUI を出して終わる
            stop(client);
            return true;
        }

        if (!hud.isHidden() || !ModManagerConfig.AFK_CINEMATIC.getValue()) {
            return false;
        }

        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.gui.screen() != null || client.getCameraEntity() != player || player.isDeadOrDying()) {
            return false;
        }

        idleTicks = 0;
        lockedOut = false;
        begin(client, player, true);
        return true;
    }

    /** fromHiddenGui: F1 で GUI を隠した状態から始めた (終わると GUI を出す)。 */
    private static void begin(Minecraft client, LocalPlayer player, boolean fromHiddenGui) {
        active = true;
        baseYaw = Math.toRadians(player.getYRot());
        plan = CinematicShots.plan(RANDOM, null);
        cutIndex = 0;
        shotTicks = 0;
        clipFactor = 1.0;

        previousCameraType = client.options.getCameraType();
        Hud hud = client.gui.hud;
        hudWasHidden = hud.isHidden() && !fromHiddenGui;
        if (!hud.isHidden()) {
            hud.toggle();
        }
        CameraTypeSwitch.set(client, CameraType.THIRD_PERSON_BACK);
        CinematicLetterbox.setShown(true);
    }

    private static void stop(Minecraft client) {
        if (!active) {
            return;
        }

        active = false;
        CameraTypeSwitch.set(client, previousCameraType);
        Hud hud = client.gui.hud;
        if (hud.isHidden() != hudWasHidden) {
            hud.toggle(); // 始める前の表示状態に戻す
        }
        CinematicLetterbox.setShown(false);
    }

    private static void reset(Minecraft client) {
        stop(client);
        idleTicks = 0;
        lockedOut = false;
        plan = List.of();
    }

    private static void nextCut() {
        shotTicks = 0;
        clipFactor = 1.0;
        if (++cutIndex >= plan.size()) {
            CinematicShots.Shot last = plan.isEmpty() ? null : plan.get(plan.size() - 1).shot();
            plan = CinematicShots.plan(RANDOM, last);
            cutIndex = 0;
        }
    }

    // ---- カメラ (Mixin から) ----

    /**
     * いまのフレームのカメラ。シネマティック中でなければ null (Mixin は何もしない)。
     * 被写体 (プレイヤー) は、バニラのカメラと同じくフレーム間を補間した位置。
     * 被写体の目から見て、計画の位置までの間に壁があれば、その手前まで寄せる。
     */
    public static View view(float partialTick) {
        if (!active) {
            return null;
        }

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null || plan.isEmpty()) {
            return null;
        }

        Vec3 feet = new Vec3(
            Mth.lerp((double) partialTick, player.xo, player.getX()),
            Mth.lerp((double) partialTick, player.yo, player.getY()),
            Mth.lerp((double) partialTick, player.zo, player.getZ())
        );
        double eye = player.getEyeHeight();
        double t = Math.max(0.0, Math.min(1.0, (shotTicks + partialTick) / SHOT_TICKS));

        CinematicShots.Pose pose = CinematicShots.pose(plan.get(cutIndex), feet, eye, baseYaw, t);
        pose = keepBobberInView(player, partialTick, feet, eye, pose);

        Vec3 focus = feet.add(0.0, eye, 0.0);
        Vec3 toCamera = pose.position().subtract(focus);
        double length = toCamera.length();
        Vec3 position = pose.position();
        if (length > 1.0E-3) {
            double factor = 1.0;
            BlockHitResult hit = level.clip(new ClipContext(focus, pose.position(), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                factor = Math.min(1.0, Math.max(0.0, hit.getLocation().distanceTo(focus) - 0.3) / length);
            }
            clipFactor = factor < clipFactor ? factor : clipFactor + (factor - clipFactor) * 0.08;
            position = focus.add(toCamera.scale(clipFactor));
        }

        Vec3 look = pose.lookAt().subtract(position);
        if (look.lengthSqr() < 1.0E-6) {
            return null;
        }

        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, horizontal));
        return new View(position, yaw, pitch);
    }

    /**
     * 釣り中 (浮きが出ている間) は、プレイヤーと浮きの中間を見て、浮きが画面に入る距離に保つ。
     * カットの動き (回り込み・昇降など) はそのまま使い、見る点を中間に、距離を「浮きまでの水平距離に応じた下限〜下限 + 6」に直す。
     * 浮きが無いときは何もしない (毎フレーム、フィールドを 1 回見るだけ)。
     */
    private static CinematicShots.Pose keepBobberInView(LocalPlayer player, float partialTick, Vec3 feet, double eye, CinematicShots.Pose pose) {
        FishingHook hook = player.fishing;
        if (hook == null || hook.isRemoved()) {
            return pose;
        }

        Vec3 bobber = new Vec3(
            Mth.lerp((double) partialTick, hook.xo, hook.getX()),
            Mth.lerp((double) partialTick, hook.yo, hook.getY()),
            Mth.lerp((double) partialTick, hook.zo, hook.getZ())
        );
        Vec3 middle = feet.add(0.0, eye * 0.55, 0.0).add(bobber).scale(0.5);
        double dx = bobber.x - feet.x;
        double dz = bobber.z - feet.z;
        double minDistance = 3.0 + 0.75 * Math.sqrt(dx * dx + dz * dz);

        Vec3 offset = pose.position().subtract(middle);
        double distance = offset.length();
        if (distance < 1.0E-3) {
            return new CinematicShots.Pose(middle.add(0.0, 2.0, minDistance), middle);
        }
        double clamped = Math.max(minDistance, Math.min(minDistance + 6.0, distance));
        return new CinematicShots.Pose(middle.add(offset.scale(clamped / distance)), middle);
    }
}

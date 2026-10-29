package neo.freelook;

import neo.event.NeoClientEvents;
import neo.menu.config.ModManagerConfig;
import neo.util.CameraTypeSwitch;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.function.BooleanSupplier;

/**
 * F5 (視点切り替えキー) を「単押し = バニラの視点切り替え / 長押し = 自由視点」にする。自由視点の動作は Perspective Mod 3.0 (1.8.9) と同じ:
 *  - F5 を {@link #HOLD_MILLIS} ミリ秒より長く押し続けると三人称 (背面) の自由視点になり、マウスで動かすのは「カメラだけ」。
 *    プレイヤー自身の向きは変わらない (キャラクターは同じ方向を向いたまま、周りをぐるっと見回せる)。視線の当たり判定・操作もプレイヤーの向きのまま
 *  - 自由視点中に F5 を離すと、押す前の視点 (一人称など) に戻る
 *  - それより短く離したら単押し: バニラの 3 段切り替えを 1 回だけ行う (離した時点で行う)
 * 設定 free_look (Mod Menu、既定 ON) が OFF の間は、F5 はバニラのまま。
 * Mixin 側 (MixinMinecraftFreeLook / MixinMouseHandlerFreeLook / MixinCameraFreeLook) はここのメソッドを呼ぶだけ。
 */
public final class FreeLookState {
    private static boolean active;
    private static float yaw;
    private static float pitch;
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;

    /** これより長く押し続けたら「長押し」(= 自由視点)。短く離したら「単押し」(= バニラの切り替え)。 */
    public static final long HOLD_MILLIS = 250L;
    /** F5 が押されてから、単押しか長押しか決まるまでの開始時刻 (ミリ秒)。0 なら押されていない。 */
    private static long pressedAtMillis;
    /** 単押しと決まった: 次の判定 1 回だけ、バニラに「押された」と返してバニラの切り替えを走らせる。 */
    private static boolean tapPending;

    private FreeLookState() {}

    /** neo のクライアント初期化 (NeoCore) から呼ばれる。 */
    public static void init() {
        NeoClientEvents.registerEndClientTick(FreeLookState::onClientTick);
    }

    /** いま自由視点か。OFF のとき・自由視点でないときは false で、フックはここで即戻る (設定も引かない)。 */
    public static boolean isActive() {
        return active;
    }

    // ---- F5 ----

    /**
     * バニラの視点切り替えの書き込み (Options#setCameraType) を止めるか: 自由視点中だけ止める。
     * 自由視点中は {@link #consumePerspectiveKey} が false を返すのでバニラの切り替えは走らないが、
     * 書き込み側でも独立に止める (自由視点中にバニラの切り替えが 1 度でも走ればカメラの種類が壊れるため)。
     */
    public static boolean blocksVanillaSwitch() {
        return active;
    }

    /**
     * Minecraft#handleKeybinds の「視点切り替えキーが押されたか」を肩代わりする。
     * 設定 OFF のときはバニラの判定をそのまま返す。ON のときは、押下を長押し / 単押しに分ける:
     *  - 長押し (HOLD_MILLIS を超えて押し続けた): 自由視点に入る。離すと戻る。バニラには「押されていない」と返す
     *  - 単押し (それより短く離した): 1 回だけ true を返して、バニラの 3 段切り替えを走らせる
     */
    public static boolean consumePerspectiveKey(BooleanSupplier vanillaConsumeClick) {
        Minecraft client = Minecraft.getInstance();
        if (!ModManagerConfig.FREE_LOOK.getValue()) {
            if (active) {
                deactivate(client); // 自由視点中に設定を OFF にしたとき
            }
            pressedAtMillis = 0L;
            tapPending = false;

            return vanillaConsumeClick.getAsBoolean();
        }

        if (consumeTap()) {
            return true;
        }

        boolean pressed = false;
        while (vanillaConsumeClick.getAsBoolean()) {
            pressed = true;
        }
        if (pressed && !active && pressedAtMillis == 0L) {
            pressedAtMillis = System.currentTimeMillis();
        }

        update(client);

        return consumeTap();
    }

    /** 単押しと決まっていれば、それを 1 回だけ消費して true。 */
    private static boolean consumeTap() {
        boolean tap = tapPending;
        tapPending = false;
        return tap;
    }

    /**
     * 押している間の状態を進める (handleKeybinds と、毎 tick の終わりから呼ぶ)。
     *  - 自由視点中: 離したら戻る
     *  - 押された後で未決: 長押しの時間を超えたら自由視点に入る / その前に離したら単押し
     */
    private static void update(Minecraft client) {
        boolean down = client.options.keyTogglePerspective.isDown();
        if (active) {
            if (!down) {
                deactivate(client);
            }
            return;
        }

        if (pressedAtMillis == 0L) {
            return;
        }

        if (down) {
            if (System.currentTimeMillis() - pressedAtMillis > HOLD_MILLIS) {
                pressedAtMillis = 0L;
                activate(client);
            }
        } else {
            pressedAtMillis = 0L;
            tapPending = true;
        }
    }

    private static void onClientTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            // ワールドを離れた: カメラの種類は戻さない (次のワールドに持ち越さない)
            active = false;
            pressedAtMillis = 0L;
            tapPending = false;
            return;
        }

        // 画面を開いている間は handleKeybinds が呼ばれないので、離したこと・長押しになったことはここで見る
        if (ModManagerConfig.FREE_LOOK.getValue() && (active || pressedAtMillis != 0L)) {
            update(client);
        }
    }

    private static void activate(Minecraft client) {
        if (client.player == null) {
            return;
        }

        previousCameraType = client.options.getCameraType();
        yaw = client.player.getYRot();
        pitch = client.player.getXRot();
        active = true;
        CameraTypeSwitch.set(client, CameraType.THIRD_PERSON_BACK);
    }

    private static void deactivate(Minecraft client) {
        active = false;
        CameraTypeSwitch.set(client, previousCameraType);
    }

    // ---- マウスとカメラ (Mixin から) ----

    /**
     * マウスの動きでカメラだけを回す。バニラの Entity#turn と同じ計算 (感度は呼び出し側で掛け済み)。
     * 自由視点中は true を返し、プレイヤー自身は回さない。
     */
    public static boolean turn(double xo, double yo) {
        if (!active) {
            return false;
        }

        yaw += (float) xo * 0.15F;
        pitch = Mth.clamp(pitch + (float) yo * 0.15F, -90.0F, 90.0F);
        return true;
    }

    /** カメラの向き (Y 回転)。自由視点中のプレイヤーだけ、カメラ専用の向きにする。 */
    public static float cameraYaw(Entity entity, float vanilla) {
        return active && entity == Minecraft.getInstance().player ? yaw : vanilla;
    }

    /** カメラの向き (X 回転 = 上下)。 */
    public static float cameraPitch(Entity entity, float vanilla) {
        return active && entity == Minecraft.getInstance().player ? pitch : vanilla;
    }
}

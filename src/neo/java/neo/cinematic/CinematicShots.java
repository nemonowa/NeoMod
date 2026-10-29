package neo.cinematic;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * AFK シネマティックのカメラワーク (カット) の定義。独自実装 (参考: Ji AFK Cinematic 2.3.1 の動作。コードは取り込んでいない)。
 * 各カットは「被写体 (プレイヤー) の足元・目の高さ・向き」と進行度 t (0〜1) から、カメラの位置と注視点を返す純粋な計算。
 * 壁との衝突・向き (yaw / pitch) の計算は {@link AfkCinematic} が行う。
 * カットは「プレイヤー中心」6 種と「周りの景色中心」6 種。1 巡の計画は、プレイヤー中心 3 + 景色中心 7 を並べ替えたもの。
 */
final class CinematicShots {
    private CinematicShots() {}

    /** 1 巡あたりの、プレイヤー中心のカットの数。 */
    private static final int CHARACTER_CUTS = 3;
    /** 1 巡あたりの、景色中心のカットの数。 */
    private static final int ENVIRONMENT_CUTS = 7;

    enum Shot {
        // プレイヤー中心
        ORBIT_LOW(true),
        ORBIT_HIGH(true),
        DOLLY(true),
        FEET_TO_FACE(true),
        PROFILE(true),
        OVER_SHOULDER(true),
        // 景色中心
        PANORAMA(false),
        HORIZON(false),
        CRANE_UP(false),
        SKY_TILT(false),
        SIDE_GLIDE(false),
        WIDE_ORBIT(false);

        final boolean character;

        Shot(boolean character) {
            this.character = character;
        }
    }

    /** 1 つのカット: 種類・回る向きなどの符号 (+1 / -1)・開始の向き (ラジアン)。同じ種類でも毎回違う動きになる。 */
    record Cut(Shot shot, double sign, double phase) {}

    /** カメラの位置と、見る点。 */
    record Pose(Vec3 position, Vec3 lookAt) {}

    /** 1 巡ぶんの計画。直前の巡の最後のカットと同じ種類で始めない。 */
    static List<Cut> plan(Random random, Shot lastShot) {
        List<Shot> characters = new ArrayList<>();
        List<Shot> environments = new ArrayList<>();
        for (Shot shot : Shot.values()) {
            (shot.character ? characters : environments).add(shot);
        }
        Collections.shuffle(characters, random);
        Collections.shuffle(environments, random);

        List<Shot> shots = new ArrayList<>(characters.subList(0, CHARACTER_CUTS));
        shots.addAll(environments); // 景色中心は全種類を 1 回ずつ
        while (shots.size() < CHARACTER_CUTS + ENVIRONMENT_CUTS) {
            shots.add(environments.get(random.nextInt(environments.size()))); // 足りない分は重複を許す
        }

        // 同じ種類が連続しないよう並べ替える (数回試して、だめならそのまま)
        for (int attempt = 0; attempt < 50; attempt++) {
            Collections.shuffle(shots, random);
            if (!hasAdjacentRepeat(shots, lastShot)) {
                break;
            }
        }

        List<Cut> cuts = new ArrayList<>();
        for (Shot shot : shots) {
            cuts.add(new Cut(shot, random.nextBoolean() ? 1.0 : -1.0, random.nextDouble() * Math.PI * 2.0));
        }
        return cuts;
    }

    private static boolean hasAdjacentRepeat(List<Shot> shots, Shot lastShot) {
        if (lastShot != null && shots.get(0) == lastShot) {
            return true;
        }
        for (int i = 1; i < shots.size(); i++) {
            if (shots.get(i) == shots.get(i - 1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * カットの、進行度 t (0〜1) でのカメラ。
     * @param feet    被写体の足元
     * @param eye     被写体の目の高さ (足元からの高さ)
     * @param yaw     計画を立てた時点の被写体の向き (ラジアン。Minecraft の Y 回転と同じで、0 = 南)
     */
    static Pose pose(Cut cut, Vec3 feet, double eye, double yaw, double t) {
        double e = t * t * (3.0 - 2.0 * t); // なめらかな加減速
        double s = cut.sign();
        double p = cut.phase();
        double body = eye * 0.55;
        Vec3 eyePoint = feet.add(0.0, eye, 0.0);
        Vec3 bodyPoint = feet.add(0.0, body, 0.0);

        return switch (cut.shot()) {
            // 足元近くから見上げて、ゆっくり回り込む
            case ORBIT_LOW -> new Pose(ring(feet, yaw, p + s * Math.toRadians(110.0) * t, 4.2, 0.5), eyePoint);
            // 上から見下ろして回り込む
            case ORBIT_HIGH -> new Pose(ring(feet, yaw, p + s * Math.toRadians(90.0) * t, 8.0, 6.0), bodyPoint);
            // 正面から寄る (s > 0) / 引く (s < 0)
            case DOLLY -> {
                double from = s > 0 ? 9.0 : 3.0;
                double to = s > 0 ? 3.0 : 9.0;
                double az = Math.sin(p) * 0.5;
                yield new Pose(ring(feet, yaw, az, from + (to - from) * e, eye + 0.3), eyePoint);
            }
            // 足元から顔まで、カメラと視線が一緒に上がる
            case FEET_TO_FACE -> {
                double az = Math.sin(p) * 0.4;
                Vec3 position = ring(feet, yaw, az, 2.4, 0.15 + (eye - 0.1) * e);
                yield new Pose(position, feet.add(0.0, 0.5 + (eye - 0.5) * e, 0.0));
            }
            // 横から、被写体の脇を並走するように通り過ぎる
            case PROFILE -> {
                Vec3 position = feet.add(side(yaw).scale(s * 4.5)).add(forward(yaw).scale(-3.5 + 7.0 * t)).add(0.0, eye * 0.8, 0.0);
                yield new Pose(position, eyePoint);
            }
            // 肩越しに、プレイヤーが向いている先を見る
            case OVER_SHOULDER -> {
                double drift = Math.sin(t * Math.PI) * 0.3 * s;
                Vec3 position = feet.add(forward(yaw).scale(-1.7)).add(side(yaw).scale(0.55 * s + drift)).add(0.0, eye + 0.35, 0.0);
                Vec3 lookAt = eyePoint.add(forward(yaw).scale(14.0)).add(side(yaw).scale(s * (-3.0 + 6.0 * t))).add(0.0, -0.3, 0.0);
                yield new Pose(position, lookAt);
            }
            // 頭上から、水平線をゆっくり見回す
            case PANORAMA -> {
                Vec3 position = feet.add(0.0, eye + 2.2, 0.0);
                double az = p + s * Math.toRadians(140.0) * t;
                yield new Pose(position, position.add(direction(yaw, az).scale(50.0)).add(0.0, 4.0, 0.0));
            }
            // プレイヤーの背後の高い位置から、プレイヤー越しに遠くの水平線を見る
            case HORIZON -> {
                Vec3 back = direction(yaw, Math.PI + Math.sin(p) * 0.4);
                Vec3 position = feet.add(back.scale(10.0 + 6.0 * t)).add(0.0, 3.0 + 4.0 * t, 0.0);
                yield new Pose(position, position.add(back.scale(-60.0)).add(0.0, 2.0, 0.0));
            }
            // 斜め上へ昇りながら、プレイヤーのいる辺りを見下ろす
            case CRANE_UP -> new Pose(ring(feet, yaw, p, 7.0, 1.0 + 15.0 * e), bodyPoint);
            // 地面近くから空を見上げ、ゆっくり水平へ戻す
            case SKY_TILT -> {
                Vec3 position = ring(feet, yaw, p, 3.0, 0.6);
                double pitchUp = Math.toRadians(65.0) * (1.0 - e) + Math.toRadians(5.0);
                Vec3 dir = direction(yaw, p + Math.PI + s * 0.5 * t);
                yield new Pose(position, position.add(dir.x * Math.cos(pitchUp) * 20.0, Math.sin(pitchUp) * 20.0, dir.z * Math.cos(pitchUp) * 20.0));
            }
            // 横へ滑りながら、横の景色を見る
            case SIDE_GLIDE -> {
                Vec3 position = feet.add(side(yaw).scale(s * (4.0 + 6.0 * e))).add(forward(yaw).scale(2.0 - 4.0 * t)).add(0.0, eye + 1.0, 0.0);
                Vec3 lookAt = feet.add(side(yaw).scale(s * 30.0)).add(forward(yaw).scale(10.0 - 6.0 * t)).add(0.0, eye, 0.0);
                yield new Pose(position, lookAt);
            }
            // 遠くから、大きく回り込む
            case WIDE_ORBIT -> new Pose(ring(feet, yaw, p + s * Math.toRadians(70.0) * t, 18.0, 9.0), bodyPoint);
        };
    }

    // ---- 幾何の補助 (すべて水平面。yaw = 被写体の向き、az = 被写体の正面からの角度 (0 = 正面、π = 背後)) ----

    /** 被写体の前方 (水平の単位ベクトル)。Minecraft の向きの規約: yaw 0 = +Z (南)。 */
    private static Vec3 forward(double yaw) {
        return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    /** 被写体の側方 (水平の単位ベクトル)。 */
    private static Vec3 side(double yaw) {
        return new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
    }

    /** 正面から az だけ回った水平の方向。 */
    private static Vec3 direction(double yaw, double az) {
        return forward(yaw).scale(Math.cos(az)).add(side(yaw).scale(Math.sin(az)));
    }

    /** 足元を中心に、半径 radius・足元から height の高さで、方向 az にある点。 */
    private static Vec3 ring(Vec3 feet, double yaw, double az, double radius, double height) {
        return feet.add(direction(yaw, az).scale(radius)).add(0.0, height, 0.0);
    }
}

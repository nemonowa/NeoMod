package neo.util;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/**
 * 視点 (一人称 / 三人称) の切り替え。自由視点 (neo.freelook) と AFK シネマティック (neo.cinematic) が共有する。
 * バニラの視点切り替えと同じ後処理 (一人称 ↔ 三人称でスペクテイターのシェーダー効果を付け外す) をして、視点を設定する。
 */
public final class CameraTypeSwitch {
    private CameraTypeSwitch() {}

    public static void set(Minecraft client, CameraType type) {
        CameraType before = client.options.getCameraType();
        client.options.setCameraType(type);
        if (before.isFirstPerson() != type.isFirstPerson() && client.player != null) {
            client.gameRenderer.checkEntityPostEffect(type.isFirstPerson() ? client.getCameraEntity() : null);
        }
    }
}

package neo.util;

import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * 「チート ON」相当の判定の共通の窓口。ゲームモードやワールドの設定ではなく、**そのプレイヤーの権限**だけで決める
 * (ゲームマスター以上 = /gamemode などが使える権限)。シングル・マルチ・専用サーバーの区別はしない。
 * クライアント側は LocalPlayer、サーバー側は ServerPlayer を渡す (どちらも自分の権限を持つ)。
 * クライアント専用のクラスを import しないので、専用サーバーでも読める (P-63)。
 */
public final class PermissionGate {
    private PermissionGate() {}

    public static boolean allowed(@Nullable Player player) {
        return player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
}

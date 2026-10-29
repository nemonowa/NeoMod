package neo.menu.gui;


import java.net.URI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import neo.util.NeoAssets;

public class SmallModManagerButtonWidget extends SpriteIconButton.CenteredIcon {
    public static final Identifier MODS_SPRITE_ENABLED = Identifier.fromNamespaceAndPath(NeoAssets.NAMESPACE, "mods/enabled");
    public static final Identifier MODS_SPRITE_DISABLED = Identifier.fromNamespaceAndPath(NeoAssets.NAMESPACE, "mods/disabled");
    public static final Identifier MODS_SPRITE_FOCUSED = Identifier.fromNamespaceAndPath(NeoAssets.NAMESPACE, "mods/focused");

    public static final Identifier DISCORD_SPRITE_ENABLED = Identifier.fromNamespaceAndPath(NeoAssets.NAMESPACE, "discord/enabled");
    public static final Identifier DISCORD_SPRITE_DISABLED = Identifier.fromNamespaceAndPath(NeoAssets.NAMESPACE, "discord/disabled");
    public static final Identifier DISCORD_SPRITE_FOCUSED = Identifier.fromNamespaceAndPath(NeoAssets.NAMESPACE, "discord/focused");

    /** Discord の招待リンク (タイトル画面のアイコンから開く)。 */
    public static final String DISCORD_URL = "https://discord.gg/nqx8esTwzS";

    public SmallModManagerButtonWidget(
            int x,
            int y,
            int width,
            int height,
            Component message,
            int spriteWidth,
            int spriteHeight,
            int spriteOffsetX,
            int spriteOffsetY,
            WidgetSprites sprite,
            OnPress onPress,
            @Nullable Component tooltip,
            @Nullable CreateNarration narration,
            boolean switchToLoadingAfterPress) {
        super(width, height, message, spriteWidth, spriteHeight, spriteOffsetX, spriteOffsetY, sprite, onPress, tooltip, narration, switchToLoadingAfterPress);
        this.setPosition(x, y);
    }

    /** タイトル画面・ポーズ画面共通の 20x20 の Mods アイコンボタン。押すと parent を戻り先に NeoMods の設定画面を開く。 */
    public static SmallModManagerButtonWidget createModsIconButton(int x, int y, Screen parent) {
        return new SmallModManagerButtonWidget(
                x,
                y,
                20,
                20,
                Component.translatable("modmenu.options"),
                16,
                16,
                0,
                0,
                new WidgetSprites(MODS_SPRITE_ENABLED, MODS_SPRITE_DISABLED, MODS_SPRITE_FOCUSED),
                _ -> Minecraft.getInstance().gui.setScreen(new ModManagerOptionsScreen(parent)),
                Component.translatable("modmenu.options"),
                null,
                false
        );
    }

    /**
     * タイトル画面の 20x20 の Discord アイコンボタン。押すと {@link #DISCORD_URL} を開く (設定 skip_command_confirmation が ON の既定では、確認画面なしで直接開く。OFF のときは、確認画面 (ConfirmLinkScreen) を経て、閉じると parent に戻る)。
     * 画像は 16x16 のカラー (Discord のブランド色 #5865F2 の角丸の下地に白いロゴ。手描きのドット) で、ボタンの中央に等倍で描く (上下左右に 2px の余白)。
     */
    public static SmallModManagerButtonWidget createDiscordIconButton(int x, int y, Screen parent) {
        return new SmallModManagerButtonWidget(
                x,
                y,
                20,
                20,
                Component.translatable("neo.discord"),
                16,
                16,
                0,
                0,
                new WidgetSprites(DISCORD_SPRITE_ENABLED, DISCORD_SPRITE_DISABLED, DISCORD_SPRITE_FOCUSED),
                _ -> ConfirmLinkScreen.confirmLinkNow(parent, URI.create(DISCORD_URL), false),
                Component.translatable("neo.discord"),
                null,
                false
        );
    }
}

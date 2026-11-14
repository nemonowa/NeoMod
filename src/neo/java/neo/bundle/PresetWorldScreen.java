package neo.bundle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import neo.event.NeoClientEvents;
import neo.util.ScreenWidgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 配布ワールドの一覧。使う人が「取得」を押した物だけを取得し、saves/ に入れる (勝手に通信しない)。
 * ワールド選択画面の右上のボタンから開く。一覧 (assets/neo/preset_worlds.json) が空ならボタンを出さない。
 */
public final class PresetWorldScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("neo");
    private static final int ROW_HEIGHT = 24;
    private static final int BUTTON_WIDTH = 80;

    /** 取得中の項目 (同時に1つだけ)。画面を閉じても取得は続くので、画面ではなくここに持つ。 */
    private static volatile PresetWorlds.Entry running;
    private static volatile long received;
    private static volatile long expected = -1;
    /** 直近の結果の文 (翻訳キー + 引数)。 */
    private static volatile Component status = Component.empty();

    private final Screen parent;
    private final java.util.Set<String> installed = new java.util.HashSet<>();
    private final String installedText = Component.translatable("neo.preset_world.installed").getString();

    public PresetWorldScreen(Screen parent) {
        super(Component.translatable("neo.preset_world.title"));
        this.parent = parent;
    }

    public static void register() {
        NeoClientEvents.registerAfterScreenInit(PresetWorldScreen::afterScreenInit);
    }

    private static void afterScreenInit(Minecraft client, Screen screen, int width, int height) {
        Button button = openButton(client, screen, width);
        if (button != null) ScreenWidgets.getWidgets(screen).add(button);
    }

    /** ワールド選択画面に足す「配布ワールド」のボタン。足さない画面・一覧が空なら null (Forge 版は ScreenEvent.Init.Post でこれを足す)。 */
    public static Button openButton(Minecraft client, Screen screen, int width) {
        if (!(screen instanceof SelectWorldScreen) || PresetWorlds.all().isEmpty()) return null;
        return Button.builder(Component.translatable("neo.preset_world.open"),
                b -> client.gui.setScreen(new PresetWorldScreen(screen)))
            .bounds(width - 108, 6, 100, 20).build();
    }

    @Override
    protected void init() {
        List<PresetWorlds.Entry> list = PresetWorlds.all();
        installed.clear();
        for (PresetWorlds.Entry entry : list) {
            if (Files.exists(minecraft.getLevelSource().getBaseDir().resolve(entry.folder()))) installed.add(entry.folder());
        }
        int rowWidth = Math.min(360, width - 40);
        int left = (width - rowWidth) / 2;
        int y = 40;
        for (PresetWorlds.Entry entry : list) {
            Button button = Button.builder(Component.translatable("neo.preset_world.get"), b -> start(entry))
                .bounds(left + rowWidth - BUTTON_WIDTH, y, BUTTON_WIDTH, 20).build();
            button.active = running == null && !exists(entry);
            addRenderableWidget(button);
            y += ROW_HEIGHT;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
            .bounds(width / 2 - 100, height - 30, 200, 20).build());
    }

    @Override
    public void onClose() {
        // 入れたワールドを一覧に出すため、ワールド選択画面は作り直す (バニラの画面は開き直さないと一覧を読み直さない)
        minecraft.gui.setScreen(parent instanceof SelectWorldScreen ? new SelectWorldScreen(new TitleScreen()) : parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
        int rowWidth = Math.min(360, width - 40);
        int left = (width - rowWidth) / 2;
        int y = 40;
        for (PresetWorlds.Entry entry : PresetWorlds.all()) {
            g.text(font, entry.name(), left, y + 6, 0xFFFFFFFF, true);
            String note = entry == running ? progressText() : exists(entry) ? installedText : "";
            g.text(font, note, left + rowWidth - BUTTON_WIDTH - 6 - font.width(note), y + 6, 0xFFA0A0A0, false);
            y += ROW_HEIGHT;
        }
        g.centeredText(font, status, width / 2, height - 46, 0xFFFFFFFF);
    }

    private static String progressText() {
        double mb = received / 1048576.0;
        long total = expected;
        return total > 0
            ? String.format(Locale.ROOT, "%.1f / %.1f MB", mb, total / 1048576.0)
            : String.format(Locale.ROOT, "%.1f MB", mb);
    }

    private void start(PresetWorlds.Entry entry) {
        if (running != null || exists(entry)) return;
        running = entry;
        received = 0;
        expected = -1;
        status = Component.translatable("neo.preset_world.downloading", entry.name());
        rebuildWidgets();
        Path saves = minecraft.getLevelSource().getBaseDir();
        Minecraft client = minecraft;
        Thread t = new Thread(() -> {
            Component result;
            try {
                WorldInstaller.install(entry, saves, v -> expected = v, v -> received = v);
                result = Component.translatable("neo.preset_world.done", entry.name());
                LOGGER.info("[NeoMods] 配布ワールドを入れた: {}", entry.folder());
            } catch (Exception e) {
                result = Component.translatable("neo.preset_world.failed", entry.name(), String.valueOf(e.getMessage()));
                LOGGER.warn("[NeoMods] 配布ワールドを入れられない: {}", entry.name(), e);
            }
            Component message = result;
            client.execute(() -> {
                running = null;
                status = message;
                if (client.gui.screen() instanceof PresetWorldScreen s) s.rebuildWidgets();
            });
        }, "NeoMods-preset-world");
        t.setDaemon(true);
        t.start();
    }

    /** 入れてあるか。ディスクは画面を作る時 (init。取得の後も作り直す) にだけ見る。 */
    private boolean exists(PresetWorlds.Entry entry) {
        return installed.contains(entry.folder());
    }
}

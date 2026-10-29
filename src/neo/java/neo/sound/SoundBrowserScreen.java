package neo.sound;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import neo.menu.config.ModManagerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * サウンドブラウザの画面。ゲームが読み込み済みのサウンドイベント (SoundManager#getAvailableSounds) を木で一覧し、
 * ピッチ・音量・カテゴリを変えて自分のクライアントだけで鳴らす (サーバーへは何も送らない)。
 * 各音には、バニラの字幕 (アクセシビリティ設定の「字幕」= 音声の説明。sounds.json の subtitle) を添えて表示する。
 * 一覧は画面を開くたびに作り直すので、リソースの再読み込み (F3+T) やリソースパック・言語の変更が反映される。
 * 見た目はバニラのスプライト (ポップアップの枠・クリエイティブのスクロールバー・「Now Playing」トーストの枠と音符) だけで作る。
 */
public final class SoundBrowserScreen extends Screen {
    /** /playsound の pitch 引数と同じ範囲 (26.3 の PlaySoundCommand: floatArg(0, 2))。 */
    private static final float PITCH_MIN = 0.0F, PITCH_MAX = 2.0F;
    private static final int ROW = 11;
    private static final int STAR_WIDTH = 12;
    private static final int SCROLLER_WIDTH = 12, SCROLLER_HEIGHT = 15;
    private static final int CARD_HEIGHT = 32;

    // バニラのスプライト (26.3 の jar に実在を確認済み)
    private static final Identifier PANEL = Identifier.withDefaultNamespace("popup/background");
    private static final Identifier CARD = Identifier.withDefaultNamespace("toast/now_playing");
    private static final Identifier MUSIC_NOTES = Identifier.withDefaultNamespace("icon/music_notes");
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_DISABLED = Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    private static final ItemStack NOTE_BLOCK = new ItemStack(Items.NOTE_BLOCK);

    // バニラのコンテナ画面の色
    private static final int LABEL = 0xFF404040;
    private static final int SLOT_DARK = 0xFF373737, SLOT_FILL = 0xFF8B8B8B, SLOT_LIGHT = 0xFFFFFFFF;

    /** 画面を閉じても残す表示状態 (ゲームの起動中だけ)。 */
    private static final Set<String> EXPANDED = new HashSet<>();
    private static String query = "";
    private static boolean favoritesOnly;
    private static Identifier selected;
    private static float pitch = 1.0F, volume = 1.0F;
    private static SoundSource source = SoundSource.MASTER;

    private final Screen parent;
    private SoundTree tree;
    private final Map<Identifier, Component> subtitles = new HashMap<>();
    private final Map<Identifier, String> subtitleTexts = new HashMap<>();
    private List<SoundTree.Row> rows = List.of();
    private SoundInstance playing;
    private int scroll;
    private boolean draggingScroller;

    private int left, top, panelWidth, panelHeight;
    private int listLeft, listRight, listTop, listBottom, cardX, cardWidth, cardTop;
    private EditBox searchBox;

    public SoundBrowserScreen(Screen parent) {
        super(Component.translatable("neo.sound.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        var sounds = minecraft.getSoundManager();
        tree = new SoundTree(sounds.getAvailableSounds());
        subtitles.clear();
        subtitleTexts.clear();
        for (Identifier id : sounds.getAvailableSounds()) {
            WeighedSoundEvents event = sounds.getSoundEvent(id);
            Component subtitle = event == null ? null : event.getSubtitle();
            if (subtitle != null) {
                subtitles.put(id, subtitle);
                subtitleTexts.put(id, subtitle.getString());
            }
        }

        // 画面いっぱいを使う (右側面のタブの分だけ空ける)。一覧の行数を最大にするため、
        // 幅に余裕があれば「左 = 一覧 / 右 = 操作」の 2 列、狭いときだけ「上 = 一覧 / 下 = 操作」に積む
        panelWidth = Math.max(220, width - 8 - (SoundBrowser.TAB_WIDTH - SoundBrowser.TAB_OVERLAP) * 2);
        panelHeight = Math.max(160, height - 8);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        int x = left + 8;
        int inner = panelWidth - 16;
        int bottom = top + panelHeight - 8;
        boolean twoColumns = inner >= 300;
        int sideWidth = twoColumns ? Mth.clamp(inner * 2 / 5, 150, 220) : inner;
        int listWidth = twoColumns ? inner - sideWidth - 6 : inner;
        int sideX = twoColumns ? x + listWidth + 6 : x;

        searchBox = new EditBox(font, x + 1, top + 19, listWidth - 60, 18, Component.translatable("neo.sound.search"));
        searchBox.setHint(Component.translatable("neo.sound.search").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setMaxLength(128);
        searchBox.setValue(query);
        searchBox.setResponder(text -> {
            query = text;
            scroll = 0;
            refreshRows();
        });
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(favoritesLabel(), b -> {
            favoritesOnly = !favoritesOnly;
            b.setMessage(favoritesLabel());
            scroll = 0;
            refreshRows();
        }).bounds(x + listWidth - 56, top + 18, 56, 20).build());

        int half = (sideWidth - 4) / 2;
        int y;
        if (twoColumns) {
            // 右の列: 上から カード / ピッチ / 音量 / カテゴリ / 再生・停止 / コピー 2 種
            cardX = sideX;
            cardWidth = sideWidth;
            cardTop = top + 18;
            y = cardTop + CARD_HEIGHT + 4;
            addRenderableWidget(new ValueSlider(sideX, y, sideWidth, "neo.sound.pitch", PITCH_MIN, PITCH_MAX, pitch, v -> pitch = v));
            y += 22;
            addRenderableWidget(new ValueSlider(sideX, y, sideWidth, "neo.sound.volume", 0.0F, 1.0F, volume, v -> volume = v));
            y += 22;
            addRenderableWidget(categoryButton(sideX, y, sideWidth));
            y += 22;
            addRenderableWidget(Button.builder(Component.translatable("neo.sound.play"), b -> play()).bounds(sideX, y, half, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("neo.sound.stop"), b -> stop()).bounds(sideX + half + 4, y, sideWidth - half - 4, 20).build());
            y += 22;
            addRenderableWidget(Button.builder(Component.translatable("neo.sound.copy"), b -> copy(true)).bounds(sideX, y, sideWidth, 20).build());
            y += 22;
            addRenderableWidget(Button.builder(Component.translatable("neo.sound.copy_no_slash"), b -> copy(false)).bounds(sideX, y, sideWidth, 20).build());
            listBottom = bottom;
        } else {
            // 狭い画面: カードは出さず (選択中は一覧の下に 1 行で出す)、操作を 2 行にまとめて一覧の行数を確保する
            int third = (inner - 8) / 3;
            int quarter = (inner - 12) / 4;
            y = bottom - 20;
            addRenderableWidget(Button.builder(Component.translatable("neo.sound.play"), b -> play()).bounds(x, y, quarter, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("neo.sound.stop"), b -> stop()).bounds(x + quarter + 4, y, quarter, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("neo.sound.copy"), b -> copy(true)).bounds(x + (quarter + 4) * 2, y, quarter, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("neo.sound.copy_no_slash"), b -> copy(false)).bounds(x + (quarter + 4) * 3, y, inner - (quarter + 4) * 3, 20).build());
            y -= 22;
            addRenderableWidget(new ValueSlider(x, y, third, "neo.sound.pitch", PITCH_MIN, PITCH_MAX, pitch, v -> pitch = v));
            addRenderableWidget(new ValueSlider(x + third + 4, y, third, "neo.sound.volume", 0.0F, 1.0F, volume, v -> volume = v));
            addRenderableWidget(categoryButton(x + (third + 4) * 2, y, inner - (third + 4) * 2));
            cardX = x;
            cardWidth = 0;
            cardTop = y - 12;
            listBottom = cardTop - 3;
        }

        listLeft = x + 1;
        listRight = x + listWidth - 1;
        listTop = top + 43;

        // 右側面のタブ (選択中)。押すとクリエイティブインベントリに戻る
        addRenderableWidget(new SoundBrowser.SideTab(left + panelWidth - SoundBrowser.TAB_OVERLAP, top + 6, true, this::back));
        refreshRows();
    }

    private CycleButton<SoundSource> categoryButton(int x, int y, int w) {
        return CycleButton.<SoundSource>builder(s -> Component.translatable("soundCategory." + s.getName()), () -> source)
                .withValues(SoundSource.values())
                .create(x, y, w, 20, Component.translatable("neo.sound.category"), (b, v) -> source = v);
    }

    private Component favoritesLabel() {
        return Component.translatable(favoritesOnly ? "neo.sound.favorites_only.on" : "neo.sound.favorites_only.off");
    }

    private Set<String> favorites() {
        return ModManagerConfig.SOUND_FAVORITES.getValue();
    }

    private void refreshRows() {
        rows = tree.rows(EXPANDED, query, favoritesOnly, favorites(), subtitleTexts);
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW);
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - visibleRows());
    }

    private int textRight() {
        return listRight - SCROLLER_WIDTH - 2;
    }

    private void play() {
        if (selected == null) {
            return;
        }
        stop();
        // バニラの UI 音と同じく位置なし (相対・減衰なし) で鳴らす。ピッチはサウンドエンジンが 0.5〜2.0 に丸める
        playing = new SimpleSoundInstance(selected, source, volume, pitch, SoundInstance.createUnseededRandom(),
                false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        minecraft.getSoundManager().play(playing);
    }

    private void stop() {
        if (playing != null) {
            minecraft.getSoundManager().stop(playing);
            playing = null;
        }
    }

    private boolean isPlaying() {
        return playing != null && minecraft.getSoundManager().isActive(playing);
    }

    private void copy(boolean slash) {
        if (selected != null) {
            String command = SoundBrowser.command(selected, source, volume, pitch);
            minecraft.keyboardHandler.setClipboard(slash ? "/" + command : command);
        }
    }

    private void toggleFavorite(Identifier id) {
        Set<String> set = new HashSet<>(favorites());
        if (!set.remove(id.toString())) {
            set.add(id.toString());
        }
        ModManagerConfig.SOUND_FAVORITES.setValue(set);
        minecraft.options.save(); // options.txt の neo_sound_favorites 行に保存される (MixinOptions)
        refreshRows();
    }

    private void back() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        stop();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- 描画 ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // 枠はバニラのポップアップ (9 スライス)。その上にウィジェット
        g.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, left, top, panelWidth, panelHeight);
        inset(g, listLeft - 1, listTop - 1, listRight + 1, listBottom + 1);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.text(font, title, left + 8, top + 7, LABEL, false);
        Component count = Component.translatable("neo.sound.count", rows.isEmpty() ? 0 : countShown(), tree.total());
        g.text(font, count, left + panelWidth - 8 - font.width(count), top + 7, 0xFF707070, false);
        extractList(g, mouseX, mouseY);
        extractScroller(g);
        extractCard(g);
    }

    private int countShown() {
        int n = 0;
        for (SoundTree.Row row : rows) {
            if (row.depth() == 0) {
                n += row.count();
            }
        }
        return n;
    }

    /** バニラのスロットと同じ、くぼんだ枠 (左上が暗く、右下が明るい)。 */
    private static void inset(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, SLOT_FILL);
        g.fill(x0, y0, x1 - 1, y0 + 1, SLOT_DARK);
        g.fill(x0, y0, x0 + 1, y1 - 1, SLOT_DARK);
        g.fill(x0 + 1, y1 - 1, x1, y1, SLOT_LIGHT);
        g.fill(x1 - 1, y0 + 1, x1, y1, SLOT_LIGHT);
    }

    private void extractList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x0 = listLeft, x1 = textRight();
        g.enableScissor(x0, listTop, x1, listBottom);
        Set<String> fav = favorites();
        int end = Math.min(rows.size(), scroll + visibleRows());
        for (int i = scroll; i < end; i++) {
            SoundTree.Row row = rows.get(i);
            SoundTree.Node node = row.node();
            int y = listTop + (i - scroll) * ROW;
            boolean hovered = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW;
            boolean isSelected = node.id != null && node.id.equals(selected);
            if (isSelected) {
                // バニラの選択枠 (一覧の選択行と同じ白枠 + 黒地)
                g.fill(x0, y, x1, y + ROW, 0xFFFFFFFF);
                g.fill(x0 + 1, y + 1, x1 - 1, y + ROW - 1, 0xFF000000);
            } else if (hovered) {
                g.fill(x0, y, x1, y + ROW, 0x80FFFFFF); // スロットのホバーと同じ半透明の白
            }
            int tx = x0 + 3 + row.depth() * 8;
            int textY = y + 2;
            int right = x1 - 2;
            if (node.id != null) {
                boolean on = fav.contains(node.id.toString());
                g.text(font, on ? "\u2605" : "\u2606", right - 8, textY, on ? 0xFFFFD040 : (isSelected ? 0xFF909090 : 0xFF555555), on);
                right -= STAR_WIDTH;
            }
            if (node.hasChildren()) {
                g.text(font, row.expanded() ? "\u25bc" : "\u25b6", tx, textY, isSelected ? 0xFFFFFFFF : LABEL, false);
                tx += 9;
                String label = node.name;
                String countText = " " + row.count();
                g.text(font, label, tx, textY, isSelected ? 0xFFFFFFFF : 0xFF202020, false);
                g.text(font, countText, tx + font.width(label), textY, isSelected ? 0xFFA0A0A0 : 0xFF5A5A5A, false);
                if (node.id != null) {
                    extractSubtitle(g, node.id, tx + font.width(label + countText) + 6, textY, right, isSelected);
                }
            } else {
                // 音の行: 名前 (ID の末尾) と、右に字幕 (字幕の無い音は名前だけ)
                g.text(font, node.name, tx, textY, isSelected ? 0xFFFFFFFF : 0xFF202020, false);
                extractSubtitle(g, node.id, tx + font.width(node.name) + 6, textY, right, isSelected);
            }
        }
        g.disableScissor();
        if (rows.isEmpty()) {
            g.centeredText(font, Component.translatable("neo.sound.empty"), (x0 + x1) / 2, listTop + 6, 0xFFFFFFFF);
        }
    }

    /** 字幕を右寄せで描く (入りきらないときは末尾を「…」で切る)。 */
    private void extractSubtitle(GuiGraphicsExtractor g, Identifier id, int minX, int y, int right, boolean selectedRow) {
        String text = subtitleTexts.get(id);
        if (text == null || right - minX < 20) {
            return;
        }
        String clipped = clip(text, right - minX);
        g.text(font, clipped, right - font.width(clipped), y, selectedRow ? 0xFFFFFFA0 : 0xFF3F3F7F, false);
    }

    private String clip(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("\u2026"))) + "\u2026";
    }

    private void extractScroller(GuiGraphicsExtractor g) {
        int x = listRight - SCROLLER_WIDTH;
        g.fill(x - 1, listTop, x, listBottom, SLOT_DARK);
        int track = listBottom - listTop - SCROLLER_HEIGHT;
        int y = listTop + (maxScroll() == 0 ? 0 : track * scroll / maxScroll());
        g.blitSprite(RenderPipelines.GUI_TEXTURED, maxScroll() > 0 ? SCROLLER : SCROLLER_DISABLED, x, y, SCROLLER_WIDTH, SCROLLER_HEIGHT);
    }

    /** 選択中の音のカード (バニラの「Now Playing」トーストの枠)。再生中は音符が動く。 */
    private void extractCard(GuiGraphicsExtractor g) {
        int x = cardX, w = cardWidth;
        if (w == 0) {
            // 狭い画面: 選択中の音を 1 行で (字幕 + ID)
            if (selected != null) {
                Component sub = subtitles.get(selected);
                String line = (sub != null ? sub.getString() + "  " : "") + selected;
                g.text(font, clip(line, panelWidth - 16), x, cardTop + 1, LABEL, false);
            }
            return;
        }
        g.blitSprite(RenderPipelines.GUI_TEXTURED, CARD, x, cardTop, w, CARD_HEIGHT);
        if (isPlaying()) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, MUSIC_NOTES, x + 8, cardTop + 8, 16, 16);
        } else {
            g.item(NOTE_BLOCK, x + 8, cardTop + 8);
        }
        int tx = x + 30, tw = w - 38;
        if (selected == null) {
            g.text(font, Component.translatable("neo.sound.none"), tx, cardTop + 12, 0xFFA0A0A0, false);
            return;
        }
        Component subtitle = subtitles.get(selected);
        String line1 = subtitle != null ? subtitle.getString() : Component.translatable("neo.sound.no_subtitle").getString();
        String settings = "x" + SoundBrowser.number(pitch) + "  " + Component.translatable("soundCategory." + source.getName()).getString();
        g.text(font, settings, x + w - 8 - font.width(settings), cardTop + 7, 0xFFA0A0A0, false);
        g.text(font, clip(line1, tw - font.width(settings) - 6), tx, cardTop + 7, subtitle != null ? 0xFFFFFFFF : 0xFFA0A0A0, false);
        g.text(font, clip(selected.toString(), tw), tx, cardTop + 18, 0xFF8FA8C8, false);
    }

    // ---- 入力 ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        double mx = event.x(), my = event.y();
        if (my < listTop || my >= listBottom) {
            return false;
        }
        if (mx >= listRight - SCROLLER_WIDTH && mx < listRight) {
            draggingScroller = maxScroll() > 0;
            scrollTo(my);
            return true;
        }
        int x0 = listLeft, x1 = textRight();
        if (mx < x0 || mx >= x1) {
            return false;
        }
        int index = scroll + (int) ((my - listTop) / ROW);
        if (index >= rows.size()) {
            return true;
        }
        SoundTree.Row row = rows.get(index);
        SoundTree.Node node = row.node();
        int arrowEnd = x0 + 3 + row.depth() * 8 + 9;
        if (node.id != null && mx >= x1 - 2 - STAR_WIDTH) {
            toggleFavorite(node.id);
        } else if (node.hasChildren() && (node.id == null || mx < arrowEnd)) {
            // 階層の開閉 (音を兼ねる節は ▶ の部分だけ)。絞り込み中は常に展開
            if (query.isBlank() && !favoritesOnly) {
                if (!EXPANDED.remove(node.key)) {
                    EXPANDED.add(node.key);
                }
                refreshRows();
            }
        } else if (node.id != null) {
            // 音の行: クリックで選択、ダブルクリックで再生
            selected = node.id;
            if (doubleClick) {
                play();
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingScroller) {
            scrollTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScroller = false;
        return super.mouseReleased(event);
    }

    private void scrollTo(double mouseY) {
        int track = listBottom - listTop - SCROLLER_HEIGHT;
        if (track > 0) {
            float f = (float) (mouseY - listTop - SCROLLER_HEIGHT / 2.0) / track;
            scroll = Mth.clamp(Math.round(f * maxScroll()), 0, maxScroll());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= listTop && mouseY < listBottom && scrollY != 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * 3, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) {
            return true;
        }
        // インベントリのキー (E) で閉じる (クリエイティブインベントリと同じ。検索欄に入力中は除く)
        if (getFocused() != searchBox && minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return false;
    }

    /** 値をそのまま表示するスライダー (min〜max を 0.01 刻み)。 */
    private static final class ValueSlider extends AbstractSliderButton {
        private final String key;
        private final float min, max;
        private final Consumer<Float> sink;

        ValueSlider(int x, int y, int width, String key, float min, float max, float initial, Consumer<Float> sink) {
            super(x, y, width, 20, Component.empty(), (initial - min) / (max - min));
            this.key = key;
            this.min = min;
            this.max = max;
            this.sink = sink;
            updateMessage();
        }

        private float current() {
            return Math.round((min + (max - min) * (float) value) * 100.0F) / 100.0F;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(key, SoundBrowser.number(current())));
        }

        @Override
        protected void applyValue() {
            sink.accept(current());
        }
    }
}

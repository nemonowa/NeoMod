package neo.map;

import com.mojang.blaze3d.platform.InputConstants;
import neo.advancements.ScreenSwitch;
import neo.menu.config.ModManagerConfig;
import neo.util.PermissionGate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * M キーで開くワールドマップ。バニラの地図アイテムを両手で持った見た目 (両腕・紙の枠・装飾) をそのまま使い、
 * 中央に大きな地図を置いて、左右にボタン・ピン一覧・縮尺などを並べる。
 * 地図の絵と手の描画はバニラ (HeldMapView が地図データを作って渡す)。この画面は背景を描かず、操作と周りの部品だけを受け持つ。
 * 操作 (Google マップ風): ドラッグで移動 (離すと慣性) / ホイール・+/-・ダブルクリックでズーム / 右クリックでピン /
 * Ctrl+左クリックでテレポート。既定はプレイヤーを中心に追従し、ドラッグで追従を解く (「現在地へ」で戻る)。
 * ズームはバニラの地図の縮尺 (スケール 0 = 1px 1 ブロック 〜 4 = 1px 16 ブロック)。バニラの地図は縮尺が固定なので、段階で切り替わる。
 * 表示できる範囲は、自分のいる最大スケールの地図の中心から地図 1 枚分の幅 (2048 ブロック) まで。
 */
public final class WorldMapScreen extends Screen {
    private static final int MAX_SCALE = MapStore.MAX_SCALE;
    private static final double PAN_LIMIT = MapStore.blockSize(MAX_SCALE);
    private static final double INERTIA_DECAY = 5.0;
    private static final int PIN_HIT_RADIUS = 7;
    /** ピン一覧 (右の欄) の上端。ピンを選んでいる間は、編集の欄 (名前・アイコン・テレポート・削除) の下になる。 */
    private static final int LIST_TOP_EDITING = 24 + 22 * 6 + 12;
    private static final int LIST_TOP_PLAIN = 24 + 22 * 2 + 14;
    private static final int LIST_ROW_HEIGHT = 12;
    private static final int LIST_MAX_ROWS = 12;
    /** 左の欄の下 (ピン一覧の下端) に空ける余白。 */
    private static final int LIST_BOTTOM_MARGIN = 8;
    /** 広域スキャンのボタンの上端 (左の欄。ズーム・現在地へ・完了の下)。 */
    private static final int SCAN_TOP = 24 + 22 * 3 + 6;

    private final MapStore store;
    private final MapPins pins;
    private final MapMarkers autoMarkers;
    private final ResourceKey<Level> dimensionKey;
    private final String dimension;

    private double centerX;
    private double centerZ;
    private int scale;
    private boolean following = true;
    private boolean dragging;
    private double velocityX;
    private double velocityZ;
    private long lastDragNanos;
    private long lastFrameNanos;
    private boolean positioned;
    private List<HeldMapView.Marker> cachedAuto;
    private int cachedAutoVersion = -1;
    private HeldMapView.View cachedAutoView;
    private MapPins.Pin selected;
    private CameraType previousCameraType;

    private EditBox nameBox;
    private Button teleportButton;
    /** 直近の updatePinPanel で見た「テレポートの権限」(画面を開いたまま /op されたときの追従用)。 */
    private boolean teleportButtonShown;
    private Button iconButton;
    private Button deleteButton;
    private Button scanButton;
    private boolean fullscreen;
    private String scanTooltipText = "";

    WorldMapScreen(MapStore store, MapPins pins, MapMarkers autoMarkers, ResourceKey<Level> dimensionKey) {
        super(Component.translatable("neo.map.title"));
        this.store = store;
        this.pins = pins;
        this.autoMarkers = autoMarkers;
        this.dimensionKey = dimensionKey;
        this.dimension = dimensionKey.identifier().toString();
    }

    // ---- 画面の出入り ----

    @Override
    protected void init() {
        if (!positioned) {
            positioned = true;
            scale = 0;
            centerOnPlayer();
            // バニラの手は一人称でしか描かれない。閉じたら元の視点に戻す
            previousCameraType = minecraft.options.getCameraType();
            if (!previousCameraType.isFirstPerson()) {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            }

            HeldMapView.setActive(true);
            fullscreen = ModManagerConfig.MAP_FULLSCREEN.getValue(); // 前回の状態を引き継ぐ
            HeldMapView.setFullscreen(fullscreen);
        }

        // 左の欄 = 地図の操作 (ズーム・現在地へ・完了・広域スキャン)、右の欄 = ピンの機能だけ (置く・前後・編集・一覧)
        int panelWidth = panelWidth();
        int half = panelWidth / 2 - 1;
        int left = 4;
        int right = width - panelWidth - 4;
        int y = 24;
        // 全画面の切り替えボタン (左右の欄の上。全画面でも欄はそのまま地図の上に重ねて出す)
        addRenderableWidget(Button.builder(Component.translatable(fullscreen ? "neo.map.fullscreen_exit" : "neo.map.fullscreen"), button -> toggleFullscreen())
            .pos(left, 2).size(panelWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> zoomBy(-1, width / 2.0, height / 2.0))
            .pos(left, y).size(half, 20).tooltip(Tooltip.create(Component.translatable("neo.map.zoom_in"))).build());
        addRenderableWidget(Button.builder(Component.literal("\u2212"), button -> zoomBy(1, width / 2.0, height / 2.0))
            .pos(left + panelWidth / 2 + 1, y).size(half, 20).tooltip(Tooltip.create(Component.translatable("neo.map.zoom_out"))).build());
        addRenderableWidget(Button.builder(Component.translatable("neo.map.center_player"), button -> centerOnPlayer())
            .pos(left, y + 22).size(panelWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
            .pos(left, y + 44).size(panelWidth, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("neo.map.add_pin_here"), button -> addPinAtPlayer())
            .pos(right, y).size(panelWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("neo.map.prev_pin"), button -> cyclePin(-1))
            .pos(right, y + 22).size(half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("neo.map.next_pin"), button -> cyclePin(1))
            .pos(right + panelWidth / 2 + 1, y + 22).size(half, 20).build());
        nameBox = new EditBox(font, right, y + 44, panelWidth, 20, Component.translatable("neo.map.pin_name"));
        nameBox.setMaxLength(32);
        nameBox.setHint(Component.translatable("neo.map.pin_name"));
        nameBox.setResponder(text -> {
            if (selected != null && !text.equals(selected.name)) { // 空にもできる (名前なしのピン)
                selected.name = text;
            }
        });
        addRenderableWidget(nameBox);
        iconButton = addRenderableWidget(Button.builder(Component.translatable("neo.map.icon"), button -> cycleIcon())
            .pos(right + 18, y + 66).size(panelWidth - 18, 20).build());
        teleportButton = addRenderableWidget(Button.builder(Component.translatable("neo.map.teleport"), button -> teleportToSelected())
            .pos(right, y + 88).size(panelWidth, 20).build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable("neo.map.delete"), button -> deleteSelected())
            .pos(right, y + 110).size(panelWidth, 20).build());

        // 広域スキャン (シングルで、そのワールドのコマンドの使用 (チート) が ON のときだけ。設定は無く自動で出る): ボタン 1 つ。いま表示している範囲を対象にする
        scanButton = null;
        scanTooltipText = "";
        if (WideMapScan.available(minecraft)) {
            scanButton = addRenderableWidget(Button.builder(scanLabel(), button -> toggleScan())
                .pos(left, SCAN_TOP).size(panelWidth, 20).build());
        }

        updatePinPanel();
    }

    @Override
    public void tick() {
        super.tick();
        if (teleportButtonShown != canTeleport()) {
            updatePinPanel(); // 権限が変わったら、テレポートの出し入れを更新する
        }
    }

    private HandMapGeometry.Rect geometry() {
        return HandMapGeometry.of(width, height, fullscreen);
    }

    /** 全画面 ⇔ 通常。状態は options.txt (neo_map_fullscreen) に覚える。 */
    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        ModManagerConfig.MAP_FULLSCREEN.setValue(fullscreen);
        minecraft.options.save();
        HeldMapView.setFullscreen(fullscreen);
        rebuildWidgets();
    }

    // ---- 地図の上のツールチップ (見出し・カーソルの座標・現在地・ディメンション・縮尺・操作方法をまとめる) ----

    private void drawMapTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int worldX, int worldZ) {
        LocalPlayer player = minecraft.player;
        if (player == null || dragging) {
            return; // ドラッグ中は出さない
        }

        Integer ground = groundAt(worldX, worldZ);
        List<Component> lines = new ArrayList<>();
        lines.add(title.copy());
        lines.add(Component.literal(String.format(Locale.ROOT, "X %d   Z %d%s", worldX, worldZ, ground == null ? "" : "   Y " + ground)));
        lines.add(Component.translatable("neo.map.here", Mth.floor(player.getX()), Mth.floor(player.getY()), Mth.floor(player.getZ())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("neo.map.dimension", dimensionKey.identifier().getPath()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("neo.map.scale", scale, 1 << scale).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("neo.map.help.drag").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("neo.map.help.zoom").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("neo.map.help.pin").withStyle(ChatFormatting.DARK_GRAY));
        if (canTeleport()) {
            lines.add(Component.translatable("neo.map.help.teleport").withStyle(ChatFormatting.DARK_GRAY));
        }

        List<FormattedCharSequence> visual = new ArrayList<>();
        for (Component line : lines) {
            visual.add(line.getVisualOrderText());
        }

        graphics.setTooltipForNextFrame(font, visual, mouseX, mouseY);
    }

    // ---- 広域スキャン ----

    private Component scanLabel() {
        return Component.translatable(WideMapScan.running() ? "neo.map.scan.stop" : "neo.map.scan.start");
    }

    /** いま画面に出ている範囲: 中心 (ブロック) と一辺 (ブロック)。 */
    private int[] shownRange() {
        HeldMapView.View view = currentView();
        int span = HeldMapView.RESOLUTION * view.blocksPerPixel();
        return new int[] {view.originX() + span / 2, view.originZ() + span / 2, span};
    }

    private void toggleScan() {
        if (WideMapScan.running()) {
            WideMapScan.stop();
        } else {
            int[] range = shownRange();
            WideMapScan.start(minecraft, store, range[0], range[1], range[2]);
        }
    }

    /** ボタンの文言と、ツールチップ (範囲・予想時間 / 実行中は進み具合・残り時間)。変わったときだけ付け替える。 */
    private void updateScanButton() {
        if (scanButton == null) {
            return;
        }

        scanButton.setMessage(scanLabel());
        Component text;
        if (WideMapScan.running()) {
            Component status = WideMapScan.status();
            text = status == null ? Component.empty() : Component.translatable("neo.map.scan.tooltip.running", status);
        } else {
            int span = shownRange()[2];
            WideMapScan.Estimate estimate = WideMapScan.estimate(minecraft, span);
            Component eta = WideMapScan.formatDuration(estimate.seconds());
            text = lines(
                Component.translatable("neo.map.scan.tooltip.title"),
                Component.translatable("neo.map.scan.tooltip.area", span, span, estimate.chunks(), estimate.points()).withStyle(ChatFormatting.GRAY),
                Component.translatable(estimate.measured() ? "neo.map.scan.tooltip.eta_measured" : "neo.map.scan.tooltip.eta_rough", eta),
                Component.translatable("neo.map.scan.tooltip.note").withStyle(ChatFormatting.DARK_GRAY));
        }

        String plain = text.getString();
        if (!plain.equals(scanTooltipText)) {
            scanTooltipText = plain;
            scanButton.setTooltip(Tooltip.create(text));
        }
    }

    private static Component lines(Component... parts) {
        net.minecraft.network.chat.MutableComponent text = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                text.append(Component.literal("\n"));
            }

            text.append(parts[i]);
        }

        return text;
    }

    private void drawScanStatus(GuiGraphicsExtractor graphics) {
        updateScanButton();
        Component status = WideMapScan.status();
        if (scanButton == null || status == null) {
            return;
        }

        int x = 4;
        int y = SCAN_TOP + 24;
        for (FormattedCharSequence line : font.split(status, panelWidth())) {
            graphics.text(font, line, x, y, 0xFFFFFFFF, true);
            y += 10;
        }
    }

    /** 地図の左右に空いた幅に合わせて、ボタン欄の幅を決める (画面が狭いと細くなる)。 */
    private int panelWidth() {
        HandMapGeometry.Rect rect = HandMapGeometry.of(width, height);
        double room = rect.left() - 12;
        return (int) Mth.clamp(room, 90.0, 150.0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        HeldMapView.setActive(false);
        if (previousCameraType != null && minecraft.options.getCameraType() != previousCameraType) {
            minecraft.options.setCameraType(previousCameraType);
        }
    }

    /** 背景は描かない: バニラが描く手持ちの地図 (ワールドの描画の一部) をそのまま見せる。 */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    // ---- 描画 ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null || minecraft.player == null || minecraft.level.dimension() != dimensionKey) {
            onClose();
            return;
        }

        stepView();
        HandMapGeometry.Rect rect = geometry();
        HeldMapView.show(minecraft, store, dimensionKey, HeldMapView.viewOf(scale, centerX, centerZ), markers(), minecraft.player);
        // 部品の位置・クリック位置は、いま画面に出ている窓を基準にする (書き直しを間引いているので、動かした位置とは少し違う)
        HeldMapView.View view = currentView();

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (selected != null) {
            drawIcon(graphics, selected.icon, width - panelWidth() - 4, 24 + 66 + 5, 10);
        }

        drawSelection(graphics, rect, view);
        drawScanStatus(graphics);
        drawPinList(graphics, mouseX, mouseY);

        if (rect.contains(mouseX, mouseY)) {
            drawMapTooltip(graphics, mouseX, mouseY, Mth.floor(toWorldX(rect, view, mouseX)), Mth.floor(toWorldZ(rect, view, mouseY)));
        }
    }

    /**
     * バニラの地図に載せる目印: ピン + 自動で出す目印 (発見した構造物・旗)。アイコンと名前は、バニラの地図レンダラーが描く。
     * 自動の目印は、窓の範囲 (動かして遅れても足りるよう、少し広め) の分だけを取り、版 (目印が増減すると変わる) と窓が同じ間は使い回す。
     */
    private List<HeldMapView.Marker> markers() {
        List<HeldMapView.Marker> markers = new ArrayList<>();
        for (MapPins.Pin pin : pins.forDimension(dimension)) {
            // バニラの地図の装飾は、回転 8 (180°) で正立に描かれる (バニラも旗・目的地に 180° を使う)
            markers.add(new HeldMapView.Marker(pin.icon.type(), pin.x + 0.5, pin.z + 0.5, MapMarkers.UPRIGHT, Optional.of(Component.literal(pin.name))));
        }

        HeldMapView.View shown = currentView();
        int span = HeldMapView.RESOLUTION * shown.blocksPerPixel();
        if (cachedAuto == null || cachedAutoVersion != autoMarkers.version() || !shown.equals(cachedAutoView)) {
            cachedAuto = autoMarkers.inWindow(dimension, shown.originX() - span / 2, shown.originZ() - span / 2,
                shown.originX() + span + span / 2, shown.originZ() + span + span / 2);
            cachedAutoVersion = autoMarkers.version();
            cachedAutoView = shown;
        }

        markers.addAll(cachedAuto);
        return markers;
    }

    /** 選んだピンの周りに枠を描く (地図は 3D で描かれるので、GUI で上から重ねる)。 */
    private void drawSelection(GuiGraphicsExtractor graphics, HandMapGeometry.Rect rect, HeldMapView.View view) {
        if (selected == null) {
            return;
        }

        double sx = toScreenX(rect, view, selected.x + 0.5);
        double sy = toScreenZ(rect, view, selected.z + 0.5);
        if (!rect.contains(sx, sy)) {
            return;
        }

        int x = (int) Math.round(sx);
        int y = (int) Math.round(sy);
        int r = 8;
        graphics.fill(x - r, y - r, x + r, y - r + 1, 0xFFFFFFFF);
        graphics.fill(x - r, y + r - 1, x + r, y + r, 0xFFFFFFFF);
        graphics.fill(x - r, y - r, x - r + 1, y + r, 0xFFFFFFFF);
        graphics.fill(x + r - 1, y - r, x + r, y + r, 0xFFFFFFFF);
    }

    // ---- ピン一覧 (右) ----

    private int listTop() {
        return selected != null ? LIST_TOP_EDITING : LIST_TOP_PLAIN;
    }

    private int listLeft() {
        return width - panelWidth() - 4;
    }

    private void drawPinList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int listWidth = panelWidth();
        int listLeft = listLeft();
        int listTop = listTop();
        List<MapPins.Pin> list = pins.forDimension(dimension);
        graphics.text(font, Component.translatable("neo.map.pin_list", list.size()), listLeft, listTop - 11, 0xFFFFFFFF, true);
        int rows = Math.min(list.size(), listMaxRows());
        for (int i = 0; i < rows; i++) {
            MapPins.Pin pin = list.get(i);
            int y = listTop + i * LIST_ROW_HEIGHT;
            boolean hovered = mouseX >= listLeft && mouseX < listLeft + listWidth && mouseY >= y && mouseY < y + LIST_ROW_HEIGHT;
            graphics.fill(listLeft, y, listLeft + listWidth, y + LIST_ROW_HEIGHT - 1, pin == selected ? 0xC0405060 : hovered ? 0xA0303030 : 0x80000000);
            drawIcon(graphics, pin.icon, listLeft + 2, y + 1, 10);
            graphics.text(font, clip(pin.name, listWidth - 20), listLeft + 15, y + 2, 0xFFFFFFFF, false);
        }

        if (list.size() > rows) {
            graphics.text(font, Component.translatable("neo.map.pin_list_more", list.size() - rows),
                listLeft, listTop + rows * LIST_ROW_HEIGHT + 2, 0xFFB0B0B0, true);
        }
    }

    /** バニラの装飾の画像 (8x8) を、size x size で描く。 */
    private void drawIcon(GuiGraphicsExtractor graphics, PinIcons.Icon icon, int x, int y, int size) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon.texture(), x, y, 0.0F, 0.0F, size, size, 8, 8, 8, 8);
    }

    /** 画面の高さに収まる行数 (「… 他 N 件」の 1 行ぶんを残す)。 */
    private int listMaxRows() {
        return Mth.clamp((height - LIST_BOTTOM_MARGIN - listTop() - 10) / LIST_ROW_HEIGHT, 1, LIST_MAX_ROWS);
    }

    private MapPins.Pin pinListAt(double mouseX, double mouseY) {
        if (mouseX < listLeft() || mouseX >= listLeft() + panelWidth() || mouseY < listTop()) {
            return null;
        }

        int row = (int) ((mouseY - listTop()) / LIST_ROW_HEIGHT);
        List<MapPins.Pin> list = pins.forDimension(dimension);
        return row >= 0 && row < Math.min(list.size(), listMaxRows()) ? list.get(row) : null;
    }

    private String clip(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }

        String shortened = text;
        while (!shortened.isEmpty() && font.width(shortened + "\u2026") > maxWidth) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }

        return shortened + "\u2026";
    }

    // ---- 視点 (Google マップのような動き) ----

    /** 毎フレーム 1 回: 追従・慣性・移動範囲の制限を進める。 */
    private void stepView() {
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 0.0 : Math.min(0.1, (now - lastFrameNanos) / 1.0e9);
        lastFrameNanos = now;

        if (following) {
            centerOnPlayerNow();
        } else if (!dragging && (velocityX != 0.0 || velocityZ != 0.0)) {
            centerX += velocityX * dt;
            centerZ += velocityZ * dt;
            double decay = Math.exp(-dt * INERTIA_DECAY);
            velocityX *= decay;
            velocityZ *= decay;
            if (Math.abs(velocityX) < 1.0 && Math.abs(velocityZ) < 1.0) {
                velocityX = 0.0;
                velocityZ = 0.0;
            }
        }

        clampCenter();
    }

    /** 表示の中心を、自分のいる最大スケールの地図の中心から地図 1 枚分の幅までに収める。 */
    private void clampCenter() {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        double biggestCenterX = MapStore.centerOf(MapStore.cellOf(Mth.floor(player.getX()), MAX_SCALE), MAX_SCALE);
        double biggestCenterZ = MapStore.centerOf(MapStore.cellOf(Mth.floor(player.getZ()), MAX_SCALE), MAX_SCALE);
        centerX = Mth.clamp(centerX, biggestCenterX - PAN_LIMIT, biggestCenterX + PAN_LIMIT);
        centerZ = Mth.clamp(centerZ, biggestCenterZ - PAN_LIMIT, biggestCenterZ + PAN_LIMIT);
    }

    /** ズームを 1 段階 (バニラの地図のスケール 1 つ分) 動かす。delta が負で拡大 (細かく)、正で縮小 (広く)。 */
    private void zoomBy(int delta, double screenX, double screenY) {
        int next = Mth.clamp(scale + delta, 0, MAX_SCALE);
        if (next == scale) {
            return;
        }

        HandMapGeometry.Rect rect = geometry();
        if (!following && rect.contains(screenX, screenY)) {
            // カーソルの下の地点が動かないように、新しい縮尺での中心を決める
            HeldMapView.View view = currentView();
            double worldX = toWorldX(rect, view, screenX);
            double worldZ = toWorldZ(rect, view, screenY);
            double fromCenterX = (screenX - rect.left()) / mapPixel(rect) - HeldMapView.RESOLUTION / 2.0;
            double fromCenterZ = (screenY - rect.top()) / mapPixel(rect) - HeldMapView.RESOLUTION / 2.0;
            scale = next;
            centerX = worldX - fromCenterX * (1 << scale);
            centerZ = worldZ - fromCenterZ * (1 << scale);
        } else {
            scale = next;
        }
    }

    private void centerOnPlayer() {
        following = true;
        velocityX = 0.0;
        velocityZ = 0.0;
        centerOnPlayerNow();
    }

    private void centerOnPlayerNow() {
        LocalPlayer player = minecraft.player;
        if (player != null) {
            centerX = player.getX();
            centerZ = player.getZ();
        }
    }

    /** 中身の画像の 1 ピクセルが、画面 (GUI 座標) で何ピクセルか (地図全体 = HeldMapView.RESOLUTION ピクセル)。 */
    private static double mapPixel(HandMapGeometry.Rect rect) {
        return rect.size() / HeldMapView.RESOLUTION;
    }

    // ---- 座標変換 (画面 ↔ ブロック): 地図の窓 (HeldMapView.View) と、地図の画面上の長方形 (HandMapGeometry) から ----

    private double toWorldX(HandMapGeometry.Rect rect, HeldMapView.View view, double screenX) {
        return view.originX() + (screenX - rect.left()) / mapPixel(rect) * view.blocksPerPixel();
    }

    private double toWorldZ(HandMapGeometry.Rect rect, HeldMapView.View view, double screenY) {
        return view.originZ() + (screenY - rect.top()) / mapPixel(rect) * view.blocksPerPixel();
    }

    private double toScreenX(HandMapGeometry.Rect rect, HeldMapView.View view, double worldX) {
        return rect.left() + (worldX - view.originX()) / view.blocksPerPixel() * mapPixel(rect);
    }

    private double toScreenZ(HandMapGeometry.Rect rect, HeldMapView.View view, double worldZ) {
        return rect.top() + (worldZ - view.originZ()) / view.blocksPerPixel() * mapPixel(rect);
    }

    private HeldMapView.View currentView() {
        HeldMapView.View shown = HeldMapView.displayedView();
        return shown != null ? shown : HeldMapView.viewOf(scale, centerX, centerZ);
    }

    /** そのブロックが探索済み (どれかのスケールの地図でピクセルに色が付いている) か。 */
    private boolean isExplored(int worldX, int worldZ) {
        for (int s = 0; s <= MAX_SCALE; s++) {
            MapStore.MapRef ref = store.peek(dimensionKey, s, MapStore.cellOf(worldX, s), MapStore.cellOf(worldZ, s));
            if (ref != null && ref.isExplored(worldX, worldZ)) {
                return true;
            }
        }

        return false;
    }

    /** そのブロック列の地面の上 (最初の空き Y)。チャンクが読み込まれていなければ null。 */
    private Integer groundAt(int worldX, int worldZ) {
        return WorldMapClient.loadedGround(minecraft.level, worldX, worldZ);
    }

    // ---- 入力 ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        clearFocus(); // 名前欄の外をクリックしたら入力を終える
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            MapPins.Pin listed = pinListAt(mouseX, mouseY);
            if (listed != null) {
                select(listed);
                focusOn(listed);
                return true;
            }
        }

        HandMapGeometry.Rect rect = geometry();
        if (!rect.contains(mouseX, mouseY)) {
            return false;
        }

        HeldMapView.View view = currentView();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (event.hasControlDown() && canTeleport()) {
                teleportToPoint(Mth.floor(toWorldX(rect, view, mouseX)), Mth.floor(toWorldZ(rect, view, mouseY)));
                return true;
            }

            MapPins.Pin hit = pinAt(rect, view, mouseX, mouseY);
            if (hit != null) {
                select(hit);
            } else if (doubleClick) {
                zoomBy(-1, mouseX, mouseY); // ダブルクリックでそこへ拡大
            } else {
                dragging = true;
                velocityX = 0.0;
                velocityZ = 0.0;
                lastDragNanos = System.nanoTime();
            }

            return true;
        }

        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            addPinAtPoint(Mth.floor(toWorldX(rect, view, mouseX)), Mth.floor(toWorldZ(rect, view, mouseY)));
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            following = false; // 動かしたら追従を解く
            HandMapGeometry.Rect rect = geometry();
            double blocksPerScreenPixel = (double) (1 << scale) / mapPixel(rect);
            centerX -= dx * blocksPerScreenPixel;
            centerZ -= dy * blocksPerScreenPixel;

            // 離したあとの慣性のために、ドラッグ中の速さ (ブロック/秒) を覚えておく
            long now = System.nanoTime();
            double seconds = Math.max(1.0e-3, (now - lastDragNanos) / 1.0e9);
            lastDragNanos = now;
            velocityX = velocityX * 0.5 + (-dx * blocksPerScreenPixel / seconds) * 0.5;
            velocityZ = velocityZ * 0.5 + (-dy * blocksPerScreenPixel / seconds) * 0.5;
            return true;
        }

        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = false;
            if ((System.nanoTime() - lastDragNanos) / 1.0e9 > 0.08) {
                velocityX = 0.0; // 止めてから離したときは慣性を付けない
                velocityZ = 0.0;
            }

            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        zoomBy(scrollY > 0.0 ? -1 : 1, mouseX, mouseY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) {
            return true; // Esc で閉じる / 名前欄への入力
        }

        if (!(getFocused() instanceof EditBox) && WorldMapClient.isOpenKey(event)) {
            onClose(); // もう一度 M で閉じる
            return true;
        }

        // 進捗のキー → 進捗画面へ直接切り替え (ピンの名前欄に入力中は文字として使う)
        if (!(getFocused() instanceof EditBox) && ScreenSwitch.toAdvancements(this, event)) {
            return true;
        }

        return false;
    }

    private MapPins.Pin pinAt(HandMapGeometry.Rect rect, HeldMapView.View view, double mouseX, double mouseY) {
        MapPins.Pin nearest = null;
        double best = PIN_HIT_RADIUS * PIN_HIT_RADIUS;
        for (MapPins.Pin pin : pins.forDimension(dimension)) {
            double dx = toScreenX(rect, view, pin.x + 0.5) - mouseX;
            double dy = toScreenZ(rect, view, pin.z + 0.5) - mouseY;
            double distance = dx * dx + dy * dy;
            if (distance <= best) {
                best = distance;
                nearest = pin;
            }
        }

        return nearest;
    }

    // ---- ピン操作 ----

    private void select(MapPins.Pin pin) {
        selected = pin;
        updatePinPanel();
    }

    private void updatePinPanel() {
        boolean has = selected != null;
        nameBox.visible = has;
        nameBox.active = has;
        iconButton.visible = has;
        iconButton.active = has;
        boolean canTeleport = has && canTeleport();
        teleportButtonShown = canTeleport();
        teleportButton.visible = canTeleport;
        teleportButton.active = canTeleport;
        // テレポートが出ないときは、削除ボタンを詰めて欄に空きを作らない
        deleteButton.setY(canTeleport ? teleportButton.getY() + 22 : teleportButton.getY());
        deleteButton.visible = has;
        deleteButton.active = has;
        if (has && !nameBox.getValue().equals(selected.name)) {
            nameBox.setValue(selected.name);
        }
    }

    private void addPinAtPlayer() {
        LocalPlayer player = minecraft.player;
        if (player != null) {
            addPin(Mth.floor(player.getX()), Mth.floor(player.getY()), Mth.floor(player.getZ()));
        }
    }

    private void addPinAtPoint(int worldX, int worldZ) {
        if (!isExplored(worldX, worldZ)) {
            notifyPlayer("neo.map.unexplored");
            return;
        }

        Integer ground = groundAt(worldX, worldZ);
        addPin(worldX, ground == null ? MapPins.NO_Y : ground, worldZ);
    }

    private void addPin(int x, int y, int z) {
        int number = pins.forDimension(dimension).size() + 1;
        String name = Component.translatable("neo.map.pin_default", number).getString();
        select(pins.add(dimension, x, y, z, name, PinIcons.ALL.get(0)));
    }

    private void cyclePin(int step) {
        List<MapPins.Pin> list = pins.forDimension(dimension);
        if (list.isEmpty()) {
            return;
        }

        int index = selected == null ? (step > 0 ? -1 : 0) : list.indexOf(selected);
        MapPins.Pin next = list.get(Math.floorMod(index + step, list.size()));
        select(next);
        focusOn(next);
    }

    /** ピンを地図の中心に持ってくる (追従は解く)。 */
    private void focusOn(MapPins.Pin pin) {
        following = false;
        velocityX = 0.0;
        velocityZ = 0.0;
        centerX = pin.x + 0.5;
        centerZ = pin.z + 0.5;
    }

    private void cycleIcon() {
        if (selected != null) {
            selected.icon = PinIcons.next(selected.icon, 1);
        }
    }

    private void deleteSelected() {
        if (selected != null) {
            pins.remove(selected);
            selected = null;
            updatePinPanel();
        }
    }

    // ---- テレポート ----

    /** テレポート (/tp) を使えるか: そのプレイヤーの権限 (ゲームマスター以上) だけで決める (PermissionGate)。権限が無いと、ボタンも Ctrl+クリックも出さない。 */
    private boolean canTeleport() {
        return PermissionGate.allowed(minecraft.player);
    }

    private void teleportToSelected() {
        if (selected != null) {
            sendTeleport(selected.x, selected.y, selected.z);
        }
    }

    private void teleportToPoint(int worldX, int worldZ) {
        if (!isExplored(worldX, worldZ)) {
            notifyPlayer("neo.map.unexplored");
            return;
        }

        Integer ground = groundAt(worldX, worldZ);
        sendTeleport(worldX, ground == null ? MapPins.NO_Y : ground, worldZ);
    }

    /** y が NO_Y のときは、WorldMapClient が 2 段階 (高所へ → チャンクが来たら地面へ) で移動する。 */
    private void sendTeleport(int x, int y, int z) {
        WorldMapClient.teleport(x, y, z);
        onClose();
    }

    private void notifyPlayer(String translationKey) {
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(Component.translatable(translationKey));
        }
    }
}

package neo.map;

import neo.data.NeoData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 1 ワールドぶんのピン (座標メモ)。保存の共通規約 (NeoData) で dataFolder/neo/map/pins.dat に保存する
 * (マルチもサーバーごと。30 秒ごと・ワールドを離れるとき・ゲーム終了時に、前回の保存から変わっていたときだけ書く)。
 * 見た目はバニラの地図の装飾 (アイコン + 名前) として、バニラの地図レンダラーが描く。
 */
final class MapPins {
    /** y が分からないピン (チャンクが未ロードのまま置いたもの)。テレポート時に地面の高さを調べる。 */
    static final int NO_Y = Integer.MIN_VALUE;

    static final class Pin {
        final String dimension;
        final int x;
        final int y;
        final int z;
        String name;
        PinIcons.Icon icon;

        private Pin(String dimension, int x, int y, int z, String name, PinIcons.Icon icon) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = name;
            this.icon = icon;
        }
    }

    private static final String DATA_PATH = "map/pins";

    private final Path dataFolder;
    private final List<Pin> pins = new ArrayList<>();
    /** 最後に保存した (または読み込んだ) 内容。名前・アイコンは画面が直接書き換えるので、保存のときに中身を比べて変化を調べる。 */
    private CompoundTag saved;

    MapPins(Path dataFolder) {
        this.dataFolder = dataFolder;
        load();
        saved = snapshot();
    }

    List<Pin> forDimension(String dimension) {
        List<Pin> result = new ArrayList<>();
        for (Pin pin : pins) {
            if (pin.dimension.equals(dimension)) {
                result.add(pin);
            }
        }

        return result;
    }

    Pin add(String dimension, int x, int y, int z, String name, PinIcons.Icon icon) {
        Pin pin = new Pin(dimension, x, y, z, name, icon);
        pins.add(pin);
        return pin;
    }

    void remove(Pin pin) {
        pins.remove(pin);
    }

    /** 前回の保存から変わっていれば保存する。 */
    void saveIfChanged() {
        CompoundTag now = snapshot();
        if (!now.equals(saved) && NeoData.write(dataFolder, DATA_PATH, now)) {
            saved = now;
        }
    }

    private void load() {
        CompoundTag root = NeoData.read(dataFolder, DATA_PATH);
        if (root == null) {
            return;
        }

        ListTag list = root.getListOrEmpty("pins");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            String dimension = entry.getStringOr("dimension", "");
            if (!dimension.isEmpty()) {
                pins.add(new Pin(dimension, entry.getIntOr("x", 0), entry.getIntOr("y", NO_Y), entry.getIntOr("z", 0),
                    entry.getStringOr("name", ""), PinIcons.byKey(entry.getStringOr("icon", ""))));
            }
        }
    }

    private CompoundTag snapshot() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (Pin pin : pins) {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", pin.dimension);
            tag.putInt("x", pin.x);
            tag.putInt("y", pin.y);
            tag.putInt("z", pin.z);
            tag.putString("name", pin.name == null ? "" : pin.name);
            tag.putString("icon", pin.icon.key());
            list.add(tag);
        }

        root.put("pins", list);
        return root;
    }
}

package neo.map;

import java.util.ArrayList;
import java.util.List;

/**
 * 1 ワールドぶんのピン (座標メモ)。**保存しない** (そのワールドで遊んでいる間だけ覚えている)。
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

    private final List<Pin> pins = new ArrayList<>();

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
}

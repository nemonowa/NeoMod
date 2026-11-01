package neo.menu.config;

import neo.menu.util.TranslationUtil;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.IntFunction;

/**
 * 整数の設定 (設定画面ではスライダー = シークバー)。範囲は min〜max (両端を含む)。
 * 値の見せ方は valueText で決める (例: 18 → 「3分」)。options.txt には、既定値から変えたときだけ整数で書く。
 */
public class IntConfigOption implements OptionConvertible {
    private final String key, translationKey;
    private final int defaultValue;
    private final int min;
    private final int max;
    private final IntFunction<Component> valueText;

    public IntConfigOption(String key, int defaultValue, int min, int max, IntFunction<Component> valueText) {
        this.key = key;
        this.translationKey = TranslationUtil.translationKeyOf("option", key);
        this.min = min;
        this.max = max;
        this.defaultValue = clamp(defaultValue);
        this.valueText = valueText;
        ConfigOptionStorage.setInt(key, this.defaultValue);
    }

    public String getKey() {
        return key;
    }

    public int getValue() {
        return clamp(ConfigOptionStorage.getInt(key));
    }

    /** 範囲の外の値は、範囲の中に丸める。 */
    public void setValue(int value) {
        ConfigOptionStorage.setInt(key, clamp(value));
    }

    public int getDefaultValue() {
        return defaultValue;
    }

    public boolean isDefault() {
        return getValue() == defaultValue;
    }

    @Override
    public void resetToDefault() {
        setValue(defaultValue);
    }

    @Override
    public OptionInstance<Integer> asOption() {
        return new OptionInstance<>(
                translationKey,
                OptionInstance.noTooltip(),
                (text, value) -> CommonComponents.optionNameValue(text, valueText.apply(value)),
                new OptionInstance.IntRange(min, max),
                getValue(),
                this::setValue
        );
    }

    private int clamp(int value) {
        return Math.max(min, Math.min(max, value));
    }
}

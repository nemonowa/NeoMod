package neo.menu.config;

import com.mojang.serialization.Codec;
import neo.menu.util.TranslationUtil;

import java.util.Arrays;
import java.util.Locale;

import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class EnumConfigOption<E extends Enum<E>> implements OptionConvertible {
    private final String key, translationKey;
    private final Class<E> enumClass;
    private final E defaultValue;

    public EnumConfigOption(String key, E defaultValue) {
        ConfigOptionStorage.setEnum(key, defaultValue);
        this.key = key;
        this.translationKey = TranslationUtil.translationKeyOf("option", key);
        this.enumClass = defaultValue.getDeclaringClass();
        this.defaultValue = defaultValue;
    }

    public String getKey() {
        return key;
    }

    public E getValue() {
        return ConfigOptionStorage.getEnum(key, enumClass);
    }

    public void setValue(E value) {
        ConfigOptionStorage.setEnum(key, value);
    }

    public void cycleValue() {
        ConfigOptionStorage.cycleEnum(key, enumClass);
    }

    public void cycleValue(int amount) {
        ConfigOptionStorage.cycleEnum(key, enumClass, amount);
    }

    /** options.txt に書く値 (小文字の名前)。 */
    public String getFileValue() {
        return getValue().name().toLowerCase(Locale.ROOT);
    }

    /** options.txt の値から設定する。知らない値なら何もしない。 */
    public void setFileValue(String fileValue) {
        for (E constant : enumClass.getEnumConstants()) {
            if (constant.name().toLowerCase(Locale.ROOT).equals(fileValue)) {
                setValue(constant);
                return;
            }
        }
    }

    public E getDefaultValue() {
        return defaultValue;
    }

    public boolean isDefault() {
        return getValue() == defaultValue;
    }

    @Override
    public void resetToDefault() {
        setValue(defaultValue);
    }

    private static <E extends Enum<E>> Component getValueText(EnumConfigOption<E> option, E value) {
        return Component.translatable(option.translationKey + "." + value.name().toLowerCase(Locale.ROOT));
    }

    public Component getButtonText() {
        return CommonComponents.optionNameValue(Component.translatable(translationKey), getValueText(this, getValue()));
    }

    @Override
    public OptionInstance<E> asOption() {
        return new OptionInstance<>(translationKey,
                OptionInstance.noTooltip(),
                (text, value) -> getValueText(this, value),
                new OptionInstance.Enum<>(Arrays.asList(enumClass.getEnumConstants()),
                        Codec.STRING.xmap(string -> Arrays.stream(enumClass.getEnumConstants())
                                .filter(e -> e.name().toLowerCase().equals(string))
                                .findAny()
                                .orElse(null), newValue -> newValue.name().toLowerCase())
                ),
                getValue(),
                value -> ConfigOptionStorage.setEnum(key, value)
        );
    }
}

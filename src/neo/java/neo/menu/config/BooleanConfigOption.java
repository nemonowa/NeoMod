package neo.menu.config;

import neo.menu.util.TranslationUtil;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;

public class BooleanConfigOption implements OptionConvertible {
    private final String key, translationKey;
    private final boolean defaultValue;
    private final java.util.List<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Component enabledText;
    private final Component disabledText;

    public BooleanConfigOption(String key, boolean defaultValue) {
        ConfigOptionStorage.setBoolean(key, defaultValue);
        this.key = key;
        this.translationKey = TranslationUtil.translationKeyOf("option", key);
        this.defaultValue = defaultValue;
        this.enabledText = Component.translatable(translationKey + ".true");
        this.disabledText = Component.translatable(translationKey + ".false");
    }

    public String getKey() {
        return key;
    }

    public boolean getValue() {
        return ConfigOptionStorage.getBoolean(key);
    }

    public void setValue(boolean value) {
        boolean changed = getValue() != value;
        ConfigOptionStorage.setBoolean(key, value);
        if (changed) {
            for (Runnable listener : listeners) listener.run();
        }
    }

    /** 設定画面などで値が変わった時に呼ぶ処理を足す (毎 tick 設定を見張らずに、変わった時だけ反映するため。P-45)。 */
    public void onChange(Runnable listener) {
        listeners.add(listener);
    }

    public boolean getDefaultValue() {
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
    public OptionInstance<Boolean> asOption() {
        return new OptionInstance<>(
                translationKey,
                OptionInstance.noTooltip(),
                (text, value) -> value ? enabledText : disabledText,
                OptionInstance.BOOLEAN_VALUES,
                getValue(),
                this::setValue
        );
    }
}

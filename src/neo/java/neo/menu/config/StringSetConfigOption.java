package neo.menu.config;

import neo.menu.util.TranslationUtil;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.network.chat.Component;

public class StringSetConfigOption {
    private final String key, translationKey;
    private final Set<String> defaultValue;

    public StringSetConfigOption(String key, Set<String> defaultValue) {
        super();
        // 保存用の集合と既定値は別のコピーにする (保存側を変更しても既定値が変わらないように)
        ConfigOptionStorage.setStringSet(key, new HashSet<>(defaultValue));
        this.key = key;
        this.translationKey = TranslationUtil.translationKeyOf("option", key);
        this.defaultValue = Set.copyOf(defaultValue);
    }

    public String getKey() {
        return key;
    }

    public Set<String> getValue() {
        return ConfigOptionStorage.getStringSet(key);
    }

    public void setValue(Set<String> value) {
        ConfigOptionStorage.setStringSet(key, value);
    }

    public Component getMessage() {
        return Component.translatable(translationKey);
    }

    public Set<String> getDefaultValue() {
        return defaultValue;
    }

    public boolean isDefault() {
        return defaultValue.equals(getValue());
    }
}

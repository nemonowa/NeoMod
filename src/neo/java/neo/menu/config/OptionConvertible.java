package neo.menu.config;

import net.minecraft.client.OptionInstance;

public interface OptionConvertible {
    OptionInstance<?> asOption();

    /** 値を既定値に戻す。 */
    void resetToDefault();
}

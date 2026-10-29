package neo.util;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * KeyMapping が内部に持つ「割り当てられたキー」への窓口 (protected なため)。
 * KeyMapping 側への実装は neo.mixin.MixinKeyMapping が行う (util は mixin に依存しない)。
 */
public interface KeyMappingInternals {
    InputConstants.Key neo$getKey();
}

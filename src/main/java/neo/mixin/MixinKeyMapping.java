package neo.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import neo.util.KeyMappingInternals;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** KeyMapping の割り当てキー (protected) を neo 側から読めるようにする窓口。 */
@Mixin(KeyMapping.class)
public abstract class MixinKeyMapping implements KeyMappingInternals {
    @Shadow protected InputConstants.Key key;

    @Unique @Override public InputConstants.Key neo$getKey() { return this.key; }
}

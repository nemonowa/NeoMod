package neo.util;

import java.util.List;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;

/**
 * Screen が内部に持つ3つのウィジェットリストへの窓口。
 * Screen 側への実装は neo.mixin.MixinScreen が行う (util は mixin に依存しない)。
 */
public interface ScreenInternals {
    List<GuiEventListener> neo$getChildren();

    List<Renderable> neo$getRenderables();

    List<NarratableEntry> neo$getNarratables();
}

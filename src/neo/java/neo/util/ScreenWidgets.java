package neo.util;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;

/**
 * Fabric API の Screens#getWidgets の代替。
 * Screen 内の「描画 + 入力 + ナレーション」を兼ねたウィジェットの一覧を、
 * 追加・置換・削除が3つの内部リストに反映される List として返す。
 */
public final class ScreenWidgets {
    private ScreenWidgets() {}

    public static List<AbstractWidget> getWidgets(Screen screen) {
        return new WidgetList((ScreenInternals) screen);
    }

    private static final class WidgetList extends AbstractList<AbstractWidget> {
        private final ScreenInternals screen;
        private final List<AbstractWidget> view = new ArrayList<>();

        WidgetList(ScreenInternals screen) {
            this.screen = screen;
            for (GuiEventListener l : screen.neo$getChildren()) {
                if (l instanceof AbstractWidget w && screen.neo$getRenderables().contains(w)) {
                    view.add(w);
                }
            }
        }

        @Override public AbstractWidget get(int index) { return view.get(index); }

        @Override public int size() { return view.size(); }

        @Override
        public AbstractWidget set(int index, AbstractWidget element) {
            AbstractWidget old = view.set(index, element);
            replace(screen.neo$getChildren(), old, element);
            replace(screen.neo$getRenderables(), old, element);
            replace(screen.neo$getNarratables(), old, element);
            return old;
        }

        @Override
        public void add(int index, AbstractWidget element) {
            AbstractWidget anchor = index < view.size() ? view.get(index) : null;
            view.add(index, element);
            insertBefore(screen.neo$getChildren(), anchor, element);
            insertBefore(screen.neo$getRenderables(), anchor, element);
            insertBefore(screen.neo$getNarratables(), anchor, element);
            modCount++;
        }

        @Override
        public AbstractWidget remove(int index) {
            AbstractWidget old = view.remove(index);
            screen.neo$getChildren().remove((GuiEventListener) old);
            screen.neo$getRenderables().remove((Renderable) old);
            screen.neo$getNarratables().remove((NarratableEntry) old);
            modCount++;
            return old;
        }

        private static <T> void replace(List<T> list, T old, T element) {
            int i = list.indexOf(old);
            if (i >= 0) list.set(i, element); else list.add(element);
        }

        private static <T> void insertBefore(List<T> list, T anchor, T element) {
            int i = anchor == null ? -1 : list.indexOf(anchor);
            if (i >= 0) list.add(i, element); else list.add(element);
        }
    }
}

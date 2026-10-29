package neo.menu.gui;

import neo.menu.config.ModManagerConfig;
import neo.menu.mod.Mod;
import neo.menu.service.ModSearch;
import neo.menu.mod.IconHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.*;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import neo.menu.mod.ModRegistry;
import neo.menu.service.ModTexts;
import neo.menu.service.ModOrdering;

@NullMarked
public class ModListWidget extends ObjectSelectionList<ModListEntry> implements AutoCloseable {
    public static final boolean DEBUG = Boolean.getBoolean("modmenu.debug");
    private final ModsScreenHost parent;
    private @Nullable List<Mod> mods = null;
    private final Set<Mod> addedMods = new HashSet<>();
    private @Nullable String selectedModId = null;
    private final IconHandler iconHandler = new IconHandler();
    private @Nullable Double restoreScrollY = null;

    public ModListWidget(
            Minecraft client,
            int width,
            int height,
            int y,
            int itemHeight,
            @Nullable ModListWidget list,
            ModsScreenHost parent
    ) {
        super(client, width, height, y, itemHeight);
        this.parent = parent;
        if (list != null) {
            this.mods = list.mods;
            this.restoreScrollY = list.scrollAmount();
        }
    }

    @Override
    public boolean isFocused() {
        return parent.isFocusOwner(this);
    }

    public void select(@Nullable ModListEntry entry) {
        this.setSelected(entry);
        if (entry != null) {
            Mod mod = entry.getMod();
            this.minecraft.getNarrator().saySystemChatQueued(Component.translatable("narrator.select", ModTexts.name(mod)));
        }
    }

    @Override
    public void setSelected(@Nullable ModListEntry entry) {
        super.setSelected(entry);
        if (entry == null) {
            selectedModId = null;
        } else {
            selectedModId = entry.getMod().getId();
        }

        parent.updateSelectedEntry(getSelected());
    }

    protected boolean isSelectedEntry(int index) {
        ModListEntry selected = getSelected();
        ModListEntry entry = this.getEntry(index);
        return selected != null && entry != null && selected.getMod().getId().equals(entry.getMod().getId());
    }

    @Override
    public int addEntry(ModListEntry entry) {
        if (addedMods.contains(entry.mod)) {
            return 0;
        }

        addedMods.add(entry.mod);
        int i = super.addEntry(entry);
        if (entry.getMod().getId().equals(selectedModId)) {
            setSelected(entry);
        }

        return i;
    }

    @Nullable
    public ModListEntry getEntry(int index) {
        if (this.children().size() > index) {
            return this.children().get(index);
        }

        return null;
    }

    @Override
    protected void removeEntry(ModListEntry entry) {
        addedMods.remove(entry.mod);
        super.removeEntry(entry);
    }

    @Override
    protected void clearEntries() {
        this.setSelected(null);
        addedMods.clear();
        super.clearEntries();
    }

    protected void remove(int index) {
        ModListEntry entry = this.children().get(index);
        addedMods.remove(entry.mod);
        super.removeEntry(entry);
    }

    public void finalizeInit() {
        filter(parent.getSearchInput(), true, true);
        if (restoreScrollY != null) {
            setScrollAmount(restoreScrollY);
            restoreScrollY = null;
        }
    }

    public void filter(String searchTerm, boolean refresh) {
        filter(searchTerm, refresh, true);
    }

    private boolean hasVisibleChildMods(Mod parent) {
        List<Mod> children = ModRegistry.PARENT_MAP.get(parent);
        ModManagerConfig.LibraryVisibility libraryVisibility = ModManagerConfig.LIBRARY_VISIBILITY;
        return !children.stream().allMatch(child -> ModManagerConfig.isHidden(child) || libraryVisibility.hideMod(child, this.parent.getModHasConfigScreen(child.getId())));
    }

    public void filter(String searchTerm, boolean refresh, boolean reposition) {
        this.clearEntries();
        addedMods.clear();
        Collection<Mod> mods = ModRegistry.MODS.values().stream().filter(mod -> {
            if (ModManagerConfig.CONFIG_MODE.getValue()) {
                return parent.getModHasConfigScreen(mod.getId());
            } else {
                return !ModManagerConfig.isHidden(mod);
            }
        }).collect(Collectors.toSet());

        if (DEBUG) {
            mods = new ArrayList<>(mods);
        }

        if (this.mods == null || refresh) {
            this.mods = new ArrayList<>();
            this.mods.addAll(mods);
            this.mods.sort(ModOrdering.comparator());
        }

        for (Mod mod : ModSearch.search(parent::getModHasConfigScreen, searchTerm, this.mods)) {
            String modId = mod.getId();

            //Hide parent lib mods when the config is set to hide
            if (ModManagerConfig.LIBRARY_VISIBILITY.hideMod(mod, this.parent.getModHasConfigScreen(modId))) {
                continue;
            }

            if (!ModRegistry.PARENT_MAP.values().contains(mod)) {
                if (ModRegistry.PARENT_MAP.keySet().contains(mod) && hasVisibleChildMods(mod)) {
                    //Add parent mods when not searching
                    List<Mod> children = ModRegistry.PARENT_MAP.get(mod);
                    children.sort(ModOrdering.comparator());
                    ParentEntry parent = new ParentEntry(mod, children, this);
                    this.addEntry(parent);
                    //Add children if they are meant to be shown
                    if (this.parent.isShowingChildren(modId)) {
                        List<Mod> validChildren = ModSearch.search(this.parent::getModHasConfigScreen, searchTerm, children);
                        for (Mod child : validChildren) {
                            //Hide child lib mods when the config is set to hide
                            if (ModManagerConfig.LIBRARY_VISIBILITY.hideMod(child, this.parent.getModHasConfigScreen(child.getId()))) {
                                continue;
                            }

                            this.addEntry(new ChildEntry(child, parent, this, validChildren.indexOf(child) == validChildren.size() - 1));
                        }
                    }
                } else {
                    //A mod with no children
                    this.addEntry(new IndependentEntry(mod, this));
                }
            }
        }

        // Entries are assigned their width as they are added. The first entries can be
        // measured before the final content height makes scrollbar presence stable,
        // which leaves wrapped descriptions using a stale row width until scrolling
        // happens to reposition the list.
        this.repositionEntriesAfterContentChange();

        if (!reposition) {
            // This generally leaves the same mod selected, but no mod highlighted, and the scrolling is unmodified.
            return;
        }

        if (parent.getSelectedEntry() != null && !children().isEmpty() || this.getSelected() != null && getSelected().getMod() != parent.getSelectedEntry().getMod()) {
            for (ModListEntry entry : children()) {
                if (entry.getMod().equals(parent.getSelectedEntry().getMod())) {
                    setSelected(entry);
                }
            }
        } else {
            if (getSelected() == null && !children().isEmpty() && getEntry(0) != null) {
                setSelected(getEntry(0));
            }
        }

        if (scrollAmount() > Math.max(0, this.contentHeight() - (this.getBottom() - this.getY() - 4))) {
            setScrollAmount(Math.max(0, this.contentHeight() - (this.getBottom() - this.getY() - 4)));
        }
    }

    private void repositionEntriesAfterContentChange() {
        int rowLeft = this.getRowLeft();
        int rowWidth = this.getRowWidth();
        int nextY = this.getY() + 2 - (int) this.scrollAmount();

        for (ModListEntry entry : children()) {
            entry.updatePlacement(rowLeft, rowWidth, nextY);
            nextY += entry.getHeight();
        }
    }

    @Override
    protected void extractListItems(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int entryLeft = this.getRowLeft();
        int entryWidth = this.getRowWidth();
        int entryHeight = this.defaultEntryHeight - 4;
        int entryCount = this.getItemCount();
        int y = this.getY();
        int yOffset = 2;
        for (int index = 0; index < entryCount; ++index) {
            int entryTop = this.getRowTop(index) + 2;
            int entryBottom = this.getRowBottom(index);
            if (entryBottom >= y && entryTop <= this.getBottom()) {
                ModListEntry entry = this.getEntry(index);
                if (entry == null) continue;
                if (this.isSelectedEntry(index)) {
                    int entryContentLeft = entryLeft + entry.getXOffset() - 2;
                    int entryContentWidth = entryWidth - entry.getXOffset() + 4;
                    this.drawSelectionHighlight(
                            graphics,
                            entryContentLeft,
                            entryTop + yOffset,
                            entryContentWidth,
                            entryHeight,
                            this.isFocused() ? CommonColors.WHITE : CommonColors.GRAY, CommonColors.BLACK
                    );
                }

                entry.setYOffset(yOffset);
                entry.extractContent(
                        graphics,
                        mouseX,
                        mouseY,
                        this.isMouseOver(mouseX, mouseY) && Objects.equals(this.getEntryAtPos(mouseX, mouseY), entry),
                        delta
                );
            }
        }
    }

    /**
     * Version of {@link #drawSelectionHighlight(GuiGraphicsExtractor, int, int, int, int, int, int)} with unconstrained positioning and sizing.
     */
    protected void drawSelectionHighlight(GuiGraphicsExtractor context, int x, int y, int width, int height, int borderColor, int fillColor) {
        context.fill(x, y - 2, x + width, y + height + 2, borderColor);
        context.fill(x + 1, y - 1, x + width - 1, y + height + 1, fillColor);
    }

    public void ensureVisible(ModListEntry entry) {
        int i = this.getRowTop(this.children().indexOf(entry));
        int j = i - this.getY() - 4 - this.defaultEntryHeight;
        if (j < 0) {
            this.setScrollAmount(this.scrollAmount() + j);
        }

        int k = this.getBottom() - i - (this.defaultEntryHeight * 2);
        if (k < 0) {
            this.setScrollAmount(this.scrollAmount() - k);
        }
    }

    public boolean keyPressed(KeyEvent input) {
        if (input.isUp() || input.isDown()) {
            return super.keyPressed(input);
        }

        if (getSelected() != null) {
            return getSelected().keyPressed(input);
        }

        return false;
    }

    public final @Nullable ModListEntry getEntryAtPos(double x, double y) {
        int int_5 = Mth.floor(y - (double) this.getY()) + (int) this.scrollAmount() - 4;
        int index = int_5 / this.defaultEntryHeight;
        return x < (double) this.scrollBarX() && x >= (double) getRowLeft() && x <= (double) (getRowLeft() + getRowWidth()) && index >= 0 && int_5 >= 0 && index < this.getItemCount() ? this.children().get(index) : null;
    }

    @Override
    protected int scrollBarX() {
        return this.width - 6;
    }

    @Override
    public int getRowWidth() {
        return this.width - (Math.max(0, this.contentHeight() - (this.getBottom() - this.getY() - 4)) > 0 ? 18 : 12);
    }

    @Override
    public int getRowLeft() {
        return this.getX() + 6;
    }

    public ModsScreenHost getParent() {
        return parent;
    }

    @Override
    protected int contentHeight() {
        return super.contentHeight() + 4;
    }

    public int getDisplayedCountFor(Set<String> set) {
        int count = 0;
        for (ModListEntry c : children()) {
            if (set.contains(c.getMod().getId())) {
                count++;
            }
        }

        return count;
    }

    @Override
    public void close() {
        iconHandler.close();
    }

    public IconHandler getIconHandler() {
        return iconHandler;
    }
}

package neo.menu.gui;

import com.mojang.blaze3d.Blaze3D;
import neo.menu.mod.Mod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.net.URI;
import java.util.*;
import neo.menu.service.ModTexts;
import neo.menu.mod.ModContact;

public class DescriptionListWidget extends AbstractSelectionList<DescriptionListWidget.DescriptionEntry> {
    private static final Component LINKS_TEXT = Component.translatable("modmenu.links");
    private static final Component SOURCE_TEXT = Component.translatable("modmenu.source").withStyle(ChatFormatting.BLUE).withStyle(ChatFormatting.UNDERLINE);
    private static final Component LICENSE_TEXT = Component.translatable("modmenu.license");
    private static final Component VIEW_CREDITS_TEXT = Component.translatable("modmenu.viewCredits").withStyle(ChatFormatting.BLUE).withStyle(ChatFormatting.UNDERLINE);
    private static final Component CREDITS_TEXT = Component.translatable("modmenu.credits");

    private final ModsScreenHost parent;
    private final Font textRenderer;
    private Mod selectedMod = null;

    public DescriptionListWidget(
            Minecraft client,
            int width,
            int height,
            int y,
            int itemHeight,
            DescriptionListWidget copyFrom,
            ModsScreenHost parent
    ) {
        super(client, width, height, y, itemHeight);
        this.parent = parent;
        this.textRenderer = client.font;

        if (copyFrom != null) {
            updateSelectedMod(copyFrom.selectedMod);
            setScrollAmount(copyFrom.scrollAmount());
        }

        if (parent.getSelectedEntry() != null) {
            updateSelectedMod(parent.getSelectedEntry().getMod());
        }
    }

    @Override
    public DescriptionEntry getSelected() {
        return null;
    }

    @Override
    public int getRowWidth() {
        return this.width - 10;
    }

    @Override
    protected int scrollBarX() {
        return this.width - 6 + this.getX();
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
        if (selectedMod != null) {
            builder.add(
                    NarratedElementType.TITLE,
                    ModTexts.name(selectedMod) + " " + selectedMod.getPrefixedVersion());
        }
    }

    private void rebuildUI() {
        if (selectedMod == null) {
            return;
        }

        DescriptionEntry emptyEntry = new DescriptionEntry(FormattedCharSequence.EMPTY);
        int wrapWidth = getRowWidth() - 5;

        Mod mod = selectedMod;
        Component description = ModTexts.formattedDescription(mod);
        if (!description.getString().isEmpty()) {
            for (FormattedCharSequence line : textRenderer.split(description, wrapWidth)) {
                this.addEntry(new DescriptionEntry(line));
            }
        }

        Map<String, String> links = mod.getLinks();
        String sourceLink = mod.getSource();
        if ((!links.isEmpty() || sourceLink != null)) {
            this.addEntry(emptyEntry);

            for (FormattedCharSequence line : textRenderer.split(LINKS_TEXT, wrapWidth)) {
                this.addEntry(new DescriptionEntry(line));
            }

            if (sourceLink != null) {
                int indent = 8;
                for (FormattedCharSequence line : textRenderer.split(SOURCE_TEXT, wrapWidth - 16)) {
                    this.addEntry(new LinkEntry(line, sourceLink, indent));
                    indent = 16;
                }
            }

            links.forEach((key, value) -> {
                int indent = 8;
                for (FormattedCharSequence line : textRenderer.split(Component.translatable(key)
                                .withStyle(ChatFormatting.BLUE)
                                .withStyle(ChatFormatting.UNDERLINE),
                        wrapWidth - 16
                )) {
                    this.addEntry(new LinkEntry(line, value, indent));
                    indent = 16;
                }
            });
        }

        Set<String> licenses = mod.getLicense();
        if (!licenses.isEmpty()) {
            this.addEntry(emptyEntry);

            for (FormattedCharSequence line : textRenderer.split(LICENSE_TEXT, wrapWidth)) {
                this.addEntry(new DescriptionEntry(line));
            }

            for (String license : licenses) {
                int indent = 8;
                for (FormattedCharSequence line : textRenderer.split(Component.literal(license), wrapWidth - 16)) {
                    this.addEntry(new DescriptionEntry(line, indent));
                    indent = 16;
                }
            }
        }

        if ("minecraft".equals(mod.getId())) {
            this.addEntry(emptyEntry);

            for (FormattedCharSequence line : textRenderer.split(VIEW_CREDITS_TEXT, wrapWidth)) {
                this.addEntry(new MojangCreditsEntry(line));
            }
        } else if (!"java".equals(mod.getId())) {
            var credits = mod.getCredits();

            if (!credits.isEmpty()) {
                this.addEntry(emptyEntry);

                for (FormattedCharSequence line : textRenderer.split(CREDITS_TEXT, wrapWidth)) {
                    this.addEntry(new DescriptionEntry(line));
                }

                var iterator = credits.entrySet().iterator();

                while (iterator.hasNext()) {
                    int indent = 8;

                    var role = iterator.next();


                    for (var line : textRenderer.split(this.creditsRoleText(role.getKey()),
                            wrapWidth - 16
                    )) {
                        this.addEntry(new DescriptionEntry(line, indent));
                        indent = 16;
                    }

                    for (var contributor : role.getValue()) {
                        indent = 16;

                        for (var line : textRenderer.split(Component.literal(contributor), wrapWidth - 24)) {
                            ModContact contact = mod.getContact(contributor);
                            if (contact != null && contact.get("email").isPresent()) {
                                this.addEntry(new MailableContactEntry(line, contact.get("email").get(), indent));
                            } else {
                                this.addEntry(new DescriptionEntry(line, indent));
                            }
                            indent = 24;
                        }
                    }

                    if (iterator.hasNext()) {
                        this.addEntry(emptyEntry);
                    }
                }
            }
        }
    }

    public void updateSelectedMod(Mod mod) {
        selectedMod = mod;
        clearEntries();
        setScrollAmount(-Double.MAX_VALUE);
        rebuildUI();
    }

    @Override
    public void extractListItems(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.enableScissor(graphics);
        super.extractListItems(graphics, mouseX, mouseY, delta);
        graphics.disableScissor();
    }

    private Component creditsRoleText(String roleName) {
        // Replace spaces and dashes in role names with underscores if they exist
        // Notably Quilted Fabric API does this with FabricMC as "Upstream Owner"
        var translationKey = roleName.replaceAll("[ -]", "_").toLowerCase();
        // Add an s to the default untranslated string if it ends in r since this
        // Fixes common role names people use in English (e.g. Author -> Authors)
        var fallback = roleName.endsWith("r") ? roleName + "s" : roleName;
        return Component.translatableWithFallback("modmenu.credits.role." + translationKey, fallback).append(Component.literal(":"));
    }

    protected class DescriptionEntry extends ContainerObjectSelectionList.Entry<DescriptionEntry> {
        protected FormattedCharSequence text;
        protected int indent;

        public DescriptionEntry(FormattedCharSequence text, int indent) {
            this.text = text;
            this.indent = indent;
        }

        public DescriptionEntry(FormattedCharSequence text) {
            this(text, 0);
        }

        @Override
        public void extractContent(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                boolean isSelected,
                float delta
        ) {
            int x = this.getX();
            int y = this.getContentY();
            graphics.text(textRenderer, text, x + indent, y, 0xFFAAAAAA);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            if (!super.isMouseOver(mouseX, mouseY)) {
                return false;
            }

            int width = DescriptionListWidget.this.textRenderer.width(text);

            double relativeX = mouseX - DescriptionListWidget.this.getRowLeft() - indent;

            return relativeX >= 0 && relativeX < width;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.emptyList();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }

    protected class MojangCreditsEntry extends DescriptionEntry {
        public MojangCreditsEntry(FormattedCharSequence text) {
            super(text);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
            if (isMouseOver(click.x(), click.y())) {
                minecraft.gui.setScreen(new MinecraftCredits());
            }

            return super.mouseClicked(click, doubleClick);
        }

        class MinecraftCredits extends CreditsAndAttributionScreen {
            public MinecraftCredits() {
                super(parent.asScreen());
            }
        }
    }

    protected class LinkEntry extends DescriptionEntry {
        private final String link;

        public LinkEntry(FormattedCharSequence text, String link, int indent) {
            super(text, indent);
            this.link = link;
        }

        public LinkEntry(FormattedCharSequence text, String link) {
            this(text, link, 0);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
            if (isMouseOver(click.x(), click.y())) {
                minecraft.gui.setScreen(new ConfirmLinkScreen((open) -> {
                    if (open) {
                        Blaze3D.openUri(URI.create(link));
                    }
                    minecraft.gui.setScreen(parent.asScreen());
                }, URI.create(link), false));
            }

            return super.mouseClicked(click, doubleClick);
        }
    }

    protected class MailableContactEntry extends DescriptionEntry {
        private final String email;

        public MailableContactEntry(FormattedCharSequence text, String email, int indent) {
            super(text, indent);
            this.email = email;
        }

        public MailableContactEntry(FormattedCharSequence text, String link) {
            this(text, link, 0);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean isSelected, float delta) {
            super.extractContent(graphics, mouseX, mouseY, isSelected, delta);
            graphics.text(textRenderer, Component.literal(" ").append(Component.literal("✉")), this.getContentX() + indent + textRenderer.width(text) + 1, this.getContentY(), 0xFFAAAAAA);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (isMouseOver(event.x(), event.y())) {
                minecraft.gui.setScreen(new ConfirmLinkScreen((open) -> {
                    if (open) {
                        Blaze3D.openUri(URI.create("mailto:" + email));
                    }
                    minecraft.gui.setScreen(parent.asScreen());
                }, URI.create("mailto:" + email), false));
            }
            return super.mouseClicked(event, doubleClick);
        }
    }
}

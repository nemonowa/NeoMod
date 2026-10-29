package eu.pb4.placeholders.api.parsers;
import eu.pb4.placeholders.api.ParserContext;
import net.minecraft.network.chat.Component;
public interface NodeParser {
    static Builder builder() { throw new UnsupportedOperationException(); }
    Component parseComponent(String input, ParserContext context);
    interface Builder {
        Builder quickText();
        NodeParser build();
    }
}

package neo.menu.service;

import neo.menu.mod.Mod;
import eu.pb4.placeholders.api.ParserContext;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import neo.menu.util.ModManagerPlatform;

/**
 * Mod の表示用テキスト (翻訳・要約・説明)。翻訳は、バニラの言語設定 (いま選んでいる言語) に翻訳キー
 * ({@code modmenu.nameTranslation.<id>} など) があるときだけ使い、無ければ Mod 自身の名前・説明を使う (専用の設定は持たない)。
 * 言語を参照するため Mod モデルとは分離している。
 */
public final class ModTexts {
    private static final boolean TEXT_PLACEHOLDER_COMPAT = ModManagerPlatform.get().isModLoaded("placeholder-api");

    private ModTexts() {}

    public static String name(Mod mod) {
        String key = "modmenu.nameTranslation." + mod.getId();
        if (Language.getInstance().has(key)) {
            return I18n.get(key);
        }
        return mod.getName();
    }

    public static String summary(Mod mod) {
        String string = translatedSummary(mod);
        return TEXT_PLACEHOLDER_COMPAT
                ? TextPlaceholderApiCompat.PARSER.parseComponent(string, ParserContext.of()).getString()
                : string;
    }

    public static String translatedSummary(Mod mod) {
        if (mod.getId().equals("java")) {
            // Java は一覧の行にも、実行中の Java のバージョンと名前を出す
            return I18n.get("modmenu.javaSummary", mod.getVersion(), mod.getName());
        }

        String key = "modmenu.summaryTranslation." + mod.getId();
        if (Language.getInstance().has(key)) {
            return I18n.get(key);
        }
        return translatedDescription(mod);
    }

    public static String translatedDescription(Mod mod) {
        String key = "modmenu.descriptionTranslation." + mod.getId();
        String description;
        if (Language.getInstance().has(key)) {
            description = I18n.get(key);
        } else {
            description = mod.getDescription();
        }

        if (mod.getId().equals("java")) {
            description = description + "\n" + I18n.get("modmenu.javaDistributionName", mod.getName());
        }
        return description;
    }

    public static Component formattedDescription(Mod mod) {
        String string = translatedDescription(mod);
        return TEXT_PLACEHOLDER_COMPAT
                ? TextPlaceholderApiCompat.PARSER.parseComponent(string, ParserContext.of())
                : Component.literal(string);
    }
}

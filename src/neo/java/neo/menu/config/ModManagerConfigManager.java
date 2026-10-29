package neo.menu.config;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.client.Minecraft;

/**
 * 設定の保存 (P-22): バニラの options.txt に、キー名の頭に {@code neo_} を付けた行
 * ({@code neo_zoom:true} など) で保存する。値は boolean は true/false、enum は小文字の名前、
 * 文字列の集合 (hidden_mods など) は「,」区切り。**既定値から変えた項目の行だけ**を書く (既定値のままの項目は行を作らない)。
 *
 * <p>バニラの {@code Options#processOptions} が受け取る {@code Options.FieldAccess} は package-private で
 * Mixin の注入先の引数に書けないので、{@code Options#load} の直前 ({@link #load}) と {@code Options#save} の直後
 * ({@link #appendTo}) で、この Mod の行だけを読み書きする (バニラの行は触らない)。
 * バニラは知らないキーを読み飛ばし、保存のときは自分の行だけを書き直すので、保存の直後にこの Mod の行を足す。
 */
public final class ModManagerConfigManager {
    /** options.txt でのキーの接頭辞 (他の Mod・バニラのキーと衝突させない)。 */
    private static final String PREFIX = "neo_";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Runnable> SAVE_LISTENERS = new CopyOnWriteArrayList<>();

    private ModManagerConfigManager() {}

    /** 設定保存時に呼ばれる処理を登録する (config 層が上位層に依存しないための逆向きの口)。 */
    public static void addSaveListener(Runnable listener) {
        SAVE_LISTENERS.add(listener);
    }

    /** 設定を options.txt に書く (バニラの保存をそのまま呼ぶ。この Mod の行は MixinOptions が足す)。 */
    public static void save() {
        SAVE_LISTENERS.forEach(Runnable::run);
        Minecraft.getInstance().options.save();
    }

    /** options.txt から、この Mod の行を読んで設定に反映する。ファイルが無い・読めない・値が不正なときは既定値のまま。 */
    public static void load(Path optionsFile) {
        // ModManagerConfig のクラス初期化 (= 既定値の登録) は、下の field.get(null) で最初に走る。値の反映はその後なので、既定値は上書きされない
        // (Options の読み込みは Mod の初期化より先のことがある)
        List<Field> fields = optionFields();
        if (!Files.isRegularFile(optionsFile)) {
            return;
        }

        Map<String, String> stored = new HashMap<>();
        try {
            for (String line : Files.readAllLines(optionsFile, StandardCharsets.UTF_8)) {
                if (line.startsWith(PREFIX)) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        stored.put(line.substring(0, colon), line.substring(colon + 1));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[Neo] options.txt を読めませんでした (設定は既定値): {}", optionsFile, e);
            return;
        }

        for (Field field : fields) {
            String value = stored.get(fileKey(field));
            if (value == null) {
                continue;
            }

            try {
                apply(field, value);
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.warn("[Neo] 設定 {} の値が不正なので既定値のままにします: {}", fileKey(field), value);
            }
        }
    }

    /** options.txt の末尾に、この Mod の行を足す (バニラの保存が終わった直後に呼ぶ)。 */
    public static void appendTo(Path optionsFile) {
        StringBuilder text = new StringBuilder();
        for (Field field : optionFields()) {
            try {
                String value = format(field);
                if (value != null) {
                    text.append(fileKey(field)).append(':').append(value).append(System.lineSeparator());
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.warn("[Neo] 設定 {} を書けませんでした", fileKey(field), e);
            }
        }

        try {
            Files.writeString(optionsFile, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.warn("[Neo] options.txt に設定を書けませんでした: {}", optionsFile, e);
        }
    }

    /** ModManagerConfig の設定項目 (static final の BooleanConfigOption / EnumConfigOption / StringSetConfigOption)。 */
    private static List<Field> optionFields() {
        List<Field> fields = new ArrayList<>();
        for (Field field : ModManagerConfig.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            Class<?> type = field.getType();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
                    && (BooleanConfigOption.class.isAssignableFrom(type)
                    || StringSetConfigOption.class.isAssignableFrom(type)
                    || EnumConfigOption.class.isAssignableFrom(type))) {
                fields.add(field);
            }
        }

        return fields;
    }

    private static String fileKey(Field field) {
        return PREFIX + field.getName().toLowerCase(Locale.ROOT);
    }

    private static void apply(Field field, String value) throws ReflectiveOperationException {
        Object option = field.get(null);
        if (option instanceof BooleanConfigOption boolOption) {
            if (value.equals("true") || value.equals("false")) {
                boolOption.setValue(Boolean.parseBoolean(value));
            }
        } else if (option instanceof StringSetConfigOption setOption) {
            Set<String> set = new TreeSet<>();
            for (String part : value.split(",")) {
                if (!part.isBlank()) {
                    set.add(part.strip());
                }
            }

            setOption.setValue(set);
        } else if (option instanceof EnumConfigOption<?> enumOption) {
            enumOption.setFileValue(value);
        }
    }

    private static String format(Field field) throws ReflectiveOperationException {
        // 既定値から変えたものだけを書く (既定値のままの項目は options.txt に行を作らない)。null = 書かない
        Object option = field.get(null);
        if (option instanceof BooleanConfigOption boolOption) {
            return boolOption.isDefault() ? null : Boolean.toString(boolOption.getValue());
        } else if (option instanceof StringSetConfigOption setOption) {
            return setOption.isDefault() ? null : String.join(",", new TreeSet<>(setOption.getValue()));
        } else if (option instanceof EnumConfigOption<?> enumOption) {
            return enumOption.isDefault() ? null : enumOption.getFileValue();
        }

        return null;
    }
}

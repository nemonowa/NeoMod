#!/usr/bin/env python3
"""agents.md の規約のうち、機械で確認できるものを検査する (標準ライブラリだけ。Python 3.8 以上)。

使い方: プロジェクトのルートで  python tools/check_rules.py
違反があれば一覧を表示して終了コード 1。規約を変えたときは、この中の表 (EXPECTED_DEFAULTS など) も合わせて直す。
"""
import collections
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NEO = os.path.join(ROOT, "src", "neo", "java")
MAIN = os.path.join(ROOT, "src", "main", "java")
RES = os.path.join(ROOT, "src", "main", "resources")

LOADER_PREFIXES = ("net.fabricmc", "org.spongepowered", "com.llamalad7", "net.minecraftforge", "net.neoforged")
STUB_PREFIXES = ("org.jetbrains", "org.jspecify", "eu.pb4")
# P-13: 階層を浅くする。外部の名前に合わせる必要があるものは例外。
DEPTH_LIMIT = 3
DEPTH_EXCEPTIONS = ("com.terraformersmc.modmenu.api",) + STUB_PREFIXES
# P-50: 使ってよいパッケージ (Minecraft / Fabric Loader が同梱・提供しているもの + 自分のコード)。
ALLOWED_IMPORTS = (
    "java.", "javax.annotation", "javax.crypto", "javax.net",
    "net.minecraft.", "com.mojang.", "neo.", "com.terraformersmc.",
    "net.fabricmc.", "org.spongepowered.", "com.llamalad7.",
    "com.google.gson", "com.google.common", "org.slf4j", "org.apache.commons.lang3", "org.apache.logging.log4j",
    "it.unimi.dsi.fastutil", "org.joml", "org.lwjgl", "io.netty", "com.google.errorprone", "org.jetbrains.annotations",
    "org.jspecify", "eu.pb4.placeholders",
)
# P-52: UI ツールキットは使わない
FORBIDDEN_IMPORTS = ("java.awt", "javax.swing", "javafx", "net.fabricmc.fabric.")
# P-41: 機能スイッチの既定値 (これ以外の既定 ON は禁止。新しい機能は既定 OFF)
EXPECTED_DEFAULTS = {
    "enchant_tooltip": False, "spawner_info": False, "spawner_glow": False, "spawner_range": False, "spawner_overlay": False,
    "free_look": True,
    "large_advancements": True, "zoom": True, "inventory_move": True, "screen_switch": True, "skin_layers": True, "world_map": True, "map_auto_markers": True, "skip_command_confirmation": True,
    "sound_browser": True,
    "afk_cinematic": True,
}
# P-26: NeoData / バニラの保管以外でファイルを書いてよいクラス
# ModsScreen: ユーザーがドロップした Mod の jar を mods フォルダへコピーする機能 (データの保存ではない)
WRITE_ALLOWED = ("neo.data.NeoData", "neo.fabric.NeoCore", "neo.menu.config.ModManagerConfigManager", "neo.menu.gui.ModsScreen")
WRITE_PATTERNS = ("Files.write", "Files.writeString", "Files.newOutputStream", "NbtIo.write", "new FileWriter", "new FileOutputStream",
                  "Files.createFile", "Files.copy(", "Files.delete", "Files.move(")

violations = []


def fail(rule, where, message):
    violations.append((rule, where, message))


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def strip(source):
    source = re.sub(r"/\*.*?\*/", " ", source, flags=re.S)
    source = re.sub(r"//[^\n]*", "", source)
    source = re.sub(r'"(?:[^"\\\n]|\\.)*"', '""', source)
    return re.sub(r"'(?:[^'\\\n]|\\.)'", "''", source)


def body(source):
    return re.sub(r"^\s*(package|import)\s[^;]*;", "", strip(source), flags=re.M)


classes = {}  # fqn -> (root name, path, source)
for root_name, root in (("neo", NEO), ("main", MAIN)):
    for path in glob.glob(root + "/**/*.java", recursive=True):
        text = read(path)
        match = re.search(r"^package ([\w.]+);", text, re.M)
        if match:
            classes[match.group(1) + "." + os.path.basename(path)[:-5]] = (root_name, path, text)

pkg = lambda fqn: fqn.rsplit(".", 1)[0]
is_stub = lambda fqn: fqn.startswith(STUB_PREFIXES)

# ---- 依存グラフ ----
deps = collections.defaultdict(set)
for fqn, (root_name, path, text) in classes.items():
    for m in re.finditer(r"^import (?:static )?([\w.]+?)(?:\.\*)?;", text, re.M):
        parts = m.group(1).split(".")
        for k in range(len(parts), 0, -1):
            candidate = ".".join(parts[:k])
            if candidate in classes:
                deps[fqn].add(candidate)
                break
    b = body(text)
    for other in classes:
        if other != fqn and pkg(other) == pkg(fqn) and re.search(r"\b" + other.rsplit(".", 1)[1] + r"\b", b):
            deps[fqn].add(other)

# P-11: neo は main を参照しない / ローダーの型を import しない
for fqn, (root_name, path, text) in classes.items():
    if root_name != "neo":
        continue
    for dep in deps[fqn]:
        if classes[dep][0] == "main":
            fail("P-11", fqn, "src/neo から src/main の " + dep + " を参照している")
    for m in re.finditer(r"^import (?:static )?([\w.]+);", text, re.M):
        if m.group(1).startswith(LOADER_PREFIXES):
            fail("P-11", fqn, "src/neo でローダーの型を import している: " + m.group(1))

# P-12: 分割パッケージ
roots_of = collections.defaultdict(set)
for fqn, (root_name, _, _) in classes.items():
    roots_of[pkg(fqn)].add(root_name)
for p, roots in roots_of.items():
    if len(roots) > 1:
        fail("P-12", p, "パッケージが src/neo と src/main にまたがっている")

# P-13: 階層の深さ
for p in roots_of:
    if p.startswith(DEPTH_EXCEPTIONS):
        continue
    if p.count(".") + 1 > DEPTH_LIMIT:
        fail("P-13", p, "パッケージの階層が %d を超えている (%d)" % (DEPTH_LIMIT, p.count(".") + 1))

# P-14: パッケージ循環 (Tarjan)
package_graph = collections.defaultdict(set)
for fqn, ds in deps.items():
    if is_stub(fqn):
        continue
    for d in ds:
        if not is_stub(d) and pkg(d) != pkg(fqn):
            package_graph[pkg(fqn)].add(pkg(d))
nodes = sorted({pkg(f) for f in classes if not is_stub(f)})
sys.setrecursionlimit(10000)
index, low, stack, on_stack, counter, components = {}, {}, [], set(), [0], []


def strongconnect(v):
    index[v] = low[v] = counter[0]
    counter[0] += 1
    stack.append(v)
    on_stack.add(v)
    for w in package_graph[v]:
        if w not in index:
            strongconnect(w)
            low[v] = min(low[v], low[w])
        elif w in on_stack:
            low[v] = min(low[v], index[w])
    if low[v] == index[v]:
        component = []
        while True:
            w = stack.pop()
            on_stack.discard(w)
            component.append(w)
            if w == v:
                break
        components.append(component)


for v in nodes:
    if v not in index:
        strongconnect(v)
for component in components:
    if len(component) > 1:
        fail("P-14", ", ".join(sorted(component)), "パッケージが循環している")

# P-50 / P-52: サードパーティ・UI ツールキット
for fqn, (root_name, path, text) in classes.items():
    if is_stub(fqn):
        continue
    for m in re.finditer(r"^import (?:static )?([\w.]+);", text, re.M):
        name = m.group(1)
        if name.startswith(FORBIDDEN_IMPORTS):
            fail("P-50/52", fqn, "使ってはいけない import: " + name)
        elif not name.startswith(ALLOWED_IMPORTS):
            fail("P-50", fqn, "許可されていない外部ライブラリの import: " + name + " (必要ならユーザーに確認して規約に追記)")

# P-26: ファイルの書き込みは NeoData か バニラの保管を通す
for fqn, (root_name, path, text) in classes.items():
    if fqn in WRITE_ALLOWED or is_stub(fqn):
        continue
    b = body(text)
    for pattern in WRITE_PATTERNS:
        if pattern in b:
            fail("P-26", fqn, pattern + " を直接使っている (NeoData / SavedDataStorage を通す)")

# ---- 設定 (P-40 / P-41) ----
config_path = os.path.join(NEO, "neo", "menu", "config", "ModManagerConfig.java")
config_text = read(config_path)
features = dict(re.findall(r'BooleanConfigOption\s+(\w+)\s*=\s*new BooleanConfigOption\("(\w+)",\s*(true|false)\)', config_text) and
                [(key, default == "true") for _, key, default in re.findall(r'BooleanConfigOption\s+(\w+)\s*=\s*new BooleanConfigOption\("(\w+)",\s*(true|false)\)', config_text)])
for key, expected in EXPECTED_DEFAULTS.items():
    if key not in features:
        fail("P-40", "ModManagerConfig", "規約にある設定 " + key + " が無い")
    elif features[key] != expected:
        fail("P-41", "ModManagerConfig", "%s の既定値が規約と違う (規約: %s)" % (key, expected))
marker = config_text.find("NeoMod の機能")
if marker < 0:
    fail("P-40", "ModManagerConfig", "「NeoMod の機能」の区切りコメントが無い")
else:
    end = config_text.find("@FileOnlyConfig", marker)  # 以降は Mod Menu 本来のファイル専用設定 (対象外)
    section = config_text[marker:end if end > 0 else len(config_text)]
    for _, key, default in re.findall(r'BooleanConfigOption\s+(\w+)\s*=\s*new BooleanConfigOption\("(\w+)",\s*(true|false)\)', section):
        if key not in EXPECTED_DEFAULTS:
            fail("P-41", "ModManagerConfig", "新しい機能スイッチ " + key + " が tools/check_rules.py と agents.md の表に無い (既定は OFF が原則)")

for lang in ("en_us", "ja_jp"):
    lang_path = os.path.join(RES, "assets", "neo", "lang", lang + ".json")
    translations = json.loads(read(lang_path))
    for key in EXPECTED_DEFAULTS:
        for suffix in ("", ".true", ".false"):
            if "option.modmenu." + key + suffix not in translations:
                fail("P-04/40", lang, "翻訳が無い: option.modmenu." + key + suffix)

# ---- Mixin (P-16 / P-60 / P-61) ----
MIXIN_JSON_NAME = "mixins.neo.json"
mixin_json = json.loads(read(os.path.join(RES, MIXIN_JSON_NAME)))
listed = set(mixin_json.get("mixins", [])) | set(mixin_json.get("client", [])) | set(mixin_json.get("server", []))
present = {fqn.rsplit(".", 1)[1] for fqn in classes if pkg(fqn) == "neo.mixin"}
for name in sorted(listed - present):
    fail("P-16", "mixins.neo.json", name + " が一覧にあるがクラスが無い")
for name in sorted(present - listed):
    fail("P-16", "mixins.neo.json", name + " のクラスがあるが一覧に無い")
for fqn, (root_name, path, text) in classes.items():
    if "@Mixin(" in text and pkg(fqn) != "neo.mixin":
        fail("P-16", fqn, "Mixin クラスは neo.mixin に置く")
    if "@Mixin(" in text and root_name != "main":
        fail("P-16", fqn, "Mixin クラスは src/main に置く")
    # 自分が書いた注入 (ハンドラ名が neo$ で始まる) は require を明示する
    for m in re.finditer(r"@(WrapOperation|ModifyConstant|ModifyReturnValue|Inject|ModifyArg|ModifyVariable)\((.*?)\)\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:private|public|protected)[^;{]*?\b(neo\$\w+)\s*\(", text, re.S):
        if "require" not in m.group(2):
            fail("P-61", fqn, "@" + m.group(1) + " " + m.group(3) + " に require が無い")
    if re.search(r"@(Overwrite|Redirect)\b", strip(text)) and "neo$" in text:
        fail("P-60", fqn, "@Overwrite / @Redirect は使わない (@WrapOperation などにする)")

# ---- 参照のある翻訳キー (P-04): neo 由来のキーが両言語で同じ ----
# lang は assets/neo/lang の 1 ファイルに統合してある。neo 由来のキーは、次の接頭辞で見分ける
NEO_KEY_PREFIXES = ("neo.", "key.neo.", "key.category.neo.")
neo_keys = {}
for lang in ("en_us", "ja_jp"):
    all_keys = json.loads(read(os.path.join(RES, "assets", "neo", "lang", lang + ".json")))
    neo_keys[lang] = set(k for k in all_keys if k.startswith(NEO_KEY_PREFIXES))
for key in sorted(neo_keys["en_us"] ^ neo_keys["ja_jp"]):
    fail("P-04", "assets/neo/lang", key + " が片方の言語にしか無い")

# ---- アセットの名前空間 (P-05): assets/neo だけ ----
ASSETS_DIR = os.path.join(RES, "assets")
namespaces = sorted(d for d in os.listdir(ASSETS_DIR) if os.path.isdir(os.path.join(ASSETS_DIR, d)))
if namespaces != ["neo"]:
    fail("P-05", "assets", "アセットの名前空間は neo だけ (見つかった: " + ", ".join(namespaces) + ")")
for fqn, (root_name, path, text) in classes.items():
    code = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    code = re.sub(r"//[^\n]*", "", code)
    for m in re.finditer(r'"assets/(?:modmenu|spawnerinfo|neomod)\b', code):
        fail("P-05", fqn, "アセットのパスに旧名前空間を直書きしている (NeoAssets.path を使う)")
    if re.search(r'fromNamespaceAndPath\(\s*ModManagerInfo\.MOD_ID', code) or re.search(r'"assets/"\s*\+\s*\w+\.MOD_ID', code):
        fail("P-05", fqn, "アセットの名前空間に MOD_ID を使っている (NeoAssets.NAMESPACE / NeoAssets.path を使う)")
    # 参照しているアセットのファイルが実在するか (動的に作るもの = 実行時登録のテクスチャは対象外)
    for m in re.finditer(r'fromNamespaceAndPath\(\s*NeoAssets\.NAMESPACE,\s*"([^"]+)"\s*\)', code):
        ident = m.group(1)
        if ident.endswith(".png"):
            target = os.path.join(ASSETS_DIR, "neo", ident)
        elif re.match(r"(mods|filters|configure)/\w+$", ident):
            target = os.path.join(ASSETS_DIR, "neo", "textures", "gui", "sprites", ident + ".png")
        else:
            continue
        if not os.path.isfile(target):
            fail("P-05", fqn, "参照しているアセットが無い: " + os.path.relpath(target, RES))
    for m in re.finditer(r'NeoAssets\.path\(\s*"([^"]+)"\s*\)', code):
        if not os.path.isfile(os.path.join(ASSETS_DIR, "neo", m.group(1))):
            fail("P-05", fqn, "参照しているアセットが無い: assets/neo/" + m.group(1))

# ---- P-92: 古い環境でも最低限起動する (縮退起動) ----
# 本体は Java 25 のバイトコード・Minecraft 26.3 専用。ローダーが最初に呼ぶ入口だけを src/compat (Java 8 のバイトコード) に置き、
# 環境が合うときだけ本体を呼ぶ。合わないときは mods/NeoMods.log に書いて何もしない (ゲームを止めない)。
COMPAT = os.path.join(ROOT, "src", "compat", "java")
for path in glob.glob(COMPAT + "/**/*.java", recursive=True):
    text = read(path)
    rel = os.path.relpath(path, ROOT)
    for m in re.finditer(r"^import\s+(?:static\s+)?([\w.]+)", text, re.M):
        name = m.group(1)
        if not (name.startswith("java.") or name == "net.minecraftforge.fml.common.Mod"):
            fail("P-92", rel, "src/compat は Java 標準 (と Forge の @Mod の代用品) 以外を import しない (古い環境に無い型に頼らない): " + name)

fabric_mod = json.loads(read(os.path.join(RES, "fabric.mod.json")))
for kind in ("preLaunch", "main", "client"):
    entries = fabric_mod.get("entrypoints", {}).get(kind, [])
    if not entries:
        fail("P-92", "fabric.mod.json", "entrypoints." + kind + " が無い (neo.compat.FabricGate を指す)")
    for entry in entries:
        if not entry.startswith("neo.compat.FabricGate::"):
            fail("P-92", "fabric.mod.json", "entrypoints." + kind + " は neo.compat.FabricGate を指す (古い環境で本体を読まないため): " + entry)
if "mixins" in fabric_mod:
    fail("P-92", "fabric.mod.json", "mixins は書かない (古い環境で Java 25 の Mixin クラスを読んでしまう。FabricGate が登録する)")
for forbidden in ("minecraft", "java"):
    if forbidden in fabric_mod.get("depends", {}):
        fail("P-92", "fabric.mod.json", "depends に " + forbidden + " を書かない (古い環境でも読み込んで、縮退起動するため)")
fabric_gate = os.path.join(COMPAT, "neo", "compat", "FabricGate.java")
if not os.path.isfile(fabric_gate) or os.path.basename(MIXIN_JSON_NAME) not in read(fabric_gate):
    fail("P-92", "FabricGate.java", "mixins.neo.json を登録していない")

FORGE_RES = os.path.join(ROOT, "src", "forge", "resources", "META-INF", "mods.toml")
if os.path.isfile(FORGE_RES) and re.search(r'modId\s*=\s*"minecraft"', read(FORGE_RES)):
    fail("P-92", "mods.toml", "Minecraft の版の依存を書かない (古い Minecraft でも読み込んで、縮退起動するため)")
for path in glob.glob(os.path.join(ROOT, "src", "forge", "java", "**", "*.java"), recursive=True):
    if re.search(r"^\s*@Mod\s*\(", read(path), re.M):
        fail("P-92", os.path.relpath(path, ROOT), "@Mod は neo.compat.ForgeGate だけに置く")
build_script = read(os.path.join(ROOT, "build.ps1"))
if "src/compat/java" not in build_script or not re.search(r"Compile\s+'compat'.*\b8\b", build_script):
    fail("P-92", "build.ps1", "src/compat を --release 8 でコンパイルしていない")

# Eclipse は全体を 1 つの水準 (JDK 25) でコンパイルするので、compat が Java 8 に無い書き方・API を使っても気づけない。
# 正式なビルドの --release 8 が最終的な基準だが、よくあるものはここでも先に止める (build.sh / build.ps1 の両方が compat を 8 でビルドすることも確認する)。
JAVA9_PLUS = [
    (r"\bvar\s+\w+\s*=", "var (Java 10)"),
    (r"\b(?:List|Set|Map)\.(?:of|copyOf|ofEntries)\(", "List/Set/Map.of (Java 9)"),
    (r"\.(?:isBlank|strip|stripLeading|stripTrailing|lines|repeat)\(", "String の新しいメソッド (Java 11)"),
    (r"\b(?:Files\.(?:readString|writeString)|Optional\.isEmpty|\.orElseThrow\(\))", "新しい API (Java 10/11)"),
    (r"\b(?:record|sealed|permits)\s+\w+|\"\"\"|\binstanceof\s+\w+(?:\.\w+)*\s+\w+\s*[)&|]", "新しい構文 (record / sealed / テキストブロック / instanceof パターン)"),
]
for path in glob.glob(COMPAT + "/**/*.java", recursive=True):
    code = re.sub(r"//[^\n]*", "", re.sub(r"/\*.*?\*/", "", read(path), flags=re.S))
    for pattern, label in JAVA9_PLUS:
        if re.search(pattern, code):
            fail("P-92", os.path.relpath(path, ROOT), "src/compat は Java 8 のバイトコード (--release 8)。Java 8 に無い書き方・API は使わない: " + label)
build_sh = read(os.path.join(ROOT, "build.sh"))
if "src/compat/java" not in build_sh or not re.search(r"--release 8\b.*compat|compat.*--release 8\b", build_sh.replace(chr(10), " ")):
    fail("P-92", "build.sh", "src/compat を --release 8 でコンパイルしていない")


# ---- 出力 ----
if violations:
    print("規約違反: %d 件" % len(violations))
    for rule, where, message in sorted(violations):
        print("  [%s] %s: %s" % (rule, where, message))
    sys.exit(1)
print("規約チェック OK (クラス %d 本、違反 0)" % len(classes))

#!/usr/bin/env sh
# NeoMod ビルドスクリプト (JDK のみ使用: javac + jar)。build.ps1 と同じ手順・同じ出力。
# Gradle / Loom / gradlew は不要です。
#
#   ./build.sh                    -> NeoMod.jar (build.sh と同じ階層。途中の生成物は bin/ に出る)
#   JAVA_RELEASE=25 ./build.sh    (既定は 25)
#   MC_DIR=/path/to/.minecraft ./build.sh   (.minecraft の場所が違うとき)
#   BUILD_FORGE=1 ./build.sh      (libs/forge/ に Forge の jar があるとき、または .minecraft の libraries に Forge があるとき、Forge の入口も一緒にビルドする)
#
# 事前準備: libs/README.txt を読んで libs/ に jar を置いてください。
set -eu
cd "$(dirname "$0")"

NAME=NeoMod
VERSION=26.3
RELEASE="${JAVA_RELEASE:-25}"
OUT=bin
JAR="$NAME.jar"   # build.sh と同じ階層 (bin/ の中ではない)。ファイル名に版は入れない (版は fabric.mod.json / mods.toml に入る)

die() { echo "ERROR: $*" >&2; exit 1; }

command -v javac >/dev/null 2>&1 || die "javac が見つかりません。JDK $RELEASE 以上をインストールしてください。"
command -v jar   >/dev/null 2>&1 || die "jar コマンドが見つかりません (JDK が必要です)。"

JV=$(javac -version 2>&1 | sed -E 's/^javac ([0-9]+).*/\1/')
[ "$JV" -ge "$RELEASE" ] 2>/dev/null || die "JDK $RELEASE 以上が必要です (検出: javac $JV)。"

# --- classpath: libs/ 以下の全 jar + 導入済みの .minecraft の libraries/ (ライブラリごとに最新の版だけ) ---
if [ -z "${MC_DIR:-}" ]; then
  for d in "$HOME/.minecraft" "$HOME/Library/Application Support/minecraft"; do
    [ -d "$d/libraries" ] && MC_DIR="$d" && break
  done
fi

# libraries/<グループ>/<名前>/<版>/*.jar のうち、各ライブラリの最新の版のディレクトリの jar だけを出す
# (文字列順だと 2.8.9 が 2.11.0 より新しくなるので、版は sort -V で比べる)。META-INF の中の jar は除く。
newest_jars() {
  find "$1" -name '*.jar' -not -path '*/META-INF/*' | sort -V | awk '
    { line[NR] = $0
      ver = $0; sub(/\/[^\/]+$/, "", ver)        # 版のディレクトリ
      art = ver; sub(/\/[^\/]+$/, "", art)       # ライブラリのディレクトリ
      best[art] = ver }                          # sort -V の昇順なので、最後に来たものが最新
    END { for (i = 1; i <= NR; i++) {
            ver = line[i]; sub(/\/[^\/]+$/, "", ver)
            art = ver; sub(/\/[^\/]+$/, "", art)
            if (best[art] == ver) print line[i] } }'
}

CP=""
for j in $(find libs -name '*.jar' | sort); do CP="${CP:+$CP:}$j"; done
if [ -n "${MC_DIR:-}" ] && [ -d "$MC_DIR/libraries" ]; then
  echo "libraries を使用: $MC_DIR/libraries"
  while IFS= read -r j; do CP="${CP:+$CP:}$j"; done <<EOL
$(newest_jars "$MC_DIR/libraries")
EOL
else
  echo "WARNING: .minecraft/libraries が見つかりません。Fabric Loader / Mixin / MixinExtras / Gson / Guava の jar が必要です。" >&2
  echo "         Fabric Loader 0.19.5 のプロファイルで Minecraft 26.3 を一度起動するか、MC_DIR=/path/to/.minecraft を指定してください。" >&2
fi
[ -n "$CP" ] || die "依存 jar がありません。libs/README.txt を参照してください。"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/res" "$OUT/nested"

# Fabric Loader が MixinExtras を入れ子の jar として持つことがある。最も優先度が低くなるよう、classpath の最後に足す。
# 古い MixinExtras (0.3.x 以前) は @Local(type=...) が無いので classpath から外す (0.5.x だけ残す)。
LOADERS=$(echo "$CP" | tr ':' '\n' | grep '/fabric-loader[^/]*\.jar$' || true)
while IFS= read -r loader; do
  [ -n "$loader" ] || continue
  case "$loader" in /*) abs="$loader" ;; *) abs="$PWD/$loader" ;; esac
  jar --list --file "$loader" | grep '\.jar$' | while IFS= read -r entry; do
    ( cd "$OUT/nested" && jar --extract --file "$abs" "$entry" )
  done
done <<EOL
$LOADERS
EOL
while IFS= read -r nested; do
  [ -n "$nested" ] && CP="$CP:$nested"
done <<EOL
$(find "$OUT/nested" -name '*.jar' | sort)
EOL
CP=$(echo "$CP" | tr ':' '\n' | grep -Ev 'mixinextras-[a-z]+-0\.[0-4]\.' | tr '\n' ':' | sed 's/:$//')
echo "選択した jar (主なもの):"
echo "$CP" | tr ':' '\n' | grep -E 'gson|mixinextras|sponge-mixin|fabric-loader|guava' | sed 's/^/  /' || true

# --- 1. 本体コンパイル ---
# src/neo/java = Fabric/Forge 非依存コード、src/main/java = Fabric 依存コード (Mixin / 入口 / Mod Manager 本体)。両方をまとめてコンパイルする。
# コンパイル専用のスタブ (org/jetbrains, org/jspecify, eu/pb4) は src/neo/java の中にあるが、jar には入れない (下で取り除く)。
echo "[1/3] ソースをコンパイル"
find src/neo/java src/main/java -name '*.java' > "$OUT/sources.txt"
javac -encoding UTF-8 --release "$RELEASE" -proc:none -Xlint:-options \
  -cp "$CP" -d "$OUT/classes" @"$OUT/sources.txt"

# --- 1b. compat (neo.compat): 入口のゲート。Java 8 のバイトコードで作る ---
# 本体は Java 25 のバイトコード・Minecraft 26.3 専用。古い Java / Minecraft ではログ (mods/NeoMods.log) だけ書いて何もせず、ゲームを止めない。
# Forge の @Mod は、コンパイル専用の代用品 (src/compat/stubs。jar には入れない)。
mkdir -p "$OUT/compat-stub"
find src/compat/stubs -name '*.java' > "$OUT/compat-stub.txt"
javac -encoding UTF-8 --release 8 -proc:none -Xlint:-options -d "$OUT/compat-stub" @"$OUT/compat-stub.txt"
find src/compat/java -name '*.java' > "$OUT/compat.txt"
javac -encoding UTF-8 --release 8 -proc:none -Xlint:-options -cp "$OUT/compat-stub" -d "$OUT/classes" @"$OUT/compat.txt"

# --- 1c. Forge (任意): src/forge は、Forge の jar があるときだけコンパイルする ---
# libs/forge/ に Forge の jar を置く (libs/README.txt)。または、.minecraft の libraries から取れるときは BUILD_FORGE=1。
# Forge の jar が無ければ Fabric 専用の jar になる (mods.toml も Forge のクラスも入らない)。
BUILD_FORGE_NOW=0
FORGE_JARS=""
if [ -d libs/forge ]; then
  for j in $(find libs/forge -name '*.jar' | sort); do FORGE_JARS="${FORGE_JARS:+$FORGE_JARS:}$j"; done
fi
if [ -n "$FORGE_JARS" ] || [ "${BUILD_FORGE:-}" = "1" ]; then BUILD_FORGE_NOW=1; fi
if [ "$BUILD_FORGE_NOW" = "1" ]; then
  echo "      + Forge の入口 (src/forge) もコンパイルする"
  find src/forge/java -name '*.java' > "$OUT/forge.txt"
  javac -encoding UTF-8 --release "$RELEASE" -proc:none -Xlint:-options \
    -cp "$OUT/classes:$CP${FORGE_JARS:+:$FORGE_JARS}" -d "$OUT/classes" @"$OUT/forge.txt"
else
  echo "NOTE: Forge の jar が無い (libs/forge が空) ので Fabric 専用の jar を作ります。mods.toml / Forge のクラスは入りません。" >&2
fi
for d in org/jetbrains org/jspecify eu/pb4; do rm -rf "$OUT/classes/$d"; done
find "$OUT/classes" -depth -type d -empty -delete   # スタブを消して空になった親ディレクトリ (eu/ org/ など) を jar に入れない

# --- 2. リソース (fabric.mod.json の $version と mods.toml の ${version} を置換) ---
echo "[2/3] リソースを処理"
cp -r src/main/resources/. "$OUT/res/"
for f in LICENSE_*; do [ -e "$f" ] && cp "$f" "$OUT/res/"; done   # ライセンス表記のファイルがあるときだけ
sed "s/\\\$version/$VERSION/g" src/main/resources/fabric.mod.json > "$OUT/res/fabric.mod.json"
if [ "$BUILD_FORGE_NOW" = "1" ]; then
  cp -r src/forge/resources/. "$OUT/res/"
  sed 's/\${version}/'"$VERSION"'/g' src/forge/resources/META-INF/mods.toml > "$OUT/res/META-INF/mods.toml"
fi
find "$OUT/res" -depth -type d -empty -delete

# --- 3. jar 作成 ---
echo "[3/3] jar を作成"
jar --create --file "$JAR" -C "$OUT/classes" . -C "$OUT/res" .

if [ "$BUILD_FORGE_NOW" = "1" ]; then echo "完了: $JAR (Fabric + Forge)"; else echo "完了: $JAR (Fabric のみ)"; fi

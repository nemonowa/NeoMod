libs/ について
==============
build.bat は次の jar を classpath にして javac でコンパイルします。

【同梱済み】
  libs/minecraft/minecraft-26.3.jar      Minecraft 26.3 (非難読化, リマップ不要)
                                         (Mojang 公式の無加工 jar。Fabric Loom 加工済みの jar は
                                          Fabric API の型を参照するため、コンパイルに Fabric API が要る。使わないこと)
  src/main/java 内の org/jetbrains, org/jspecify, eu/pb4
                                         コンパイル専用スタブ (Eclipse用。jar には入りません)

【自動で拾うもの】 Fabric Loader 導入済みの .minecraft/libraries
  Fabric Loader, Mixin, MixinExtras, Guava, Gson, slf4j, brigadier, DFU, joml, lwjgl など。
  - Linux/macOS: ~/.minecraft を自動検出
  - Windows: %APPDATA%\.minecraft を自動検出
  - 別の場所: MC_DIR=/path/to/.minecraft ./build.sh   (Windowsは set MC_DIR=... )
  Fabric Loader 0.19.5 + Minecraft 26.3 のプロファイルを一度ランチャーで作成/起動しておけば
  libraries/ に揃います。

【手動で入れる場合】
  .minecraft が使えない環境では、上記ライブラリの jar を libs/ 以下(サブフォルダ可)に置いてください。

必要な JDK: 25 以上 (javac / jar コマンドのみ使用。Gradle/Loom 不要)

【統合済み mod (ソースとして src/ に入っています / jar-in-jar なし)】
  Spawner Info 2.0.0 (neo.spawnerinfo) と EnchantToolTip 2.0.0 (neo.tooltip), どちらも CC0-1.0。
  元の jar にソースが無かったためデコンパイルして取り込んでいます (Vineflower 1.12.0)。
  Fabric API は不要です (Mixin で置き換え済み)。実行に必要なのは fabricloader と minecraft のみ。

【Forge 版も同じ jar に入れる場合 (任意)】
  libs/forge/ を作り、Forge 26.3 の jar を置いてから build.bat を実行します。
  - Forge 本体 (client の jar) と、classpath に要るもの: fmlcore / javafmllanguage / eventbus / forgespi など
    (Forge をランチャーで一度導入すると .minecraft/libraries にも揃います。その場合は set BUILD_FORGE=1 でも可)
  - 置いてあると src/forge/java をコンパイルし、META-INF/mods.toml も同梱します (${version} は置換されます)。
  - 置いていないときは Fabric 専用の jar になります (build.bat の最後に "Fabric only" と出ます)。

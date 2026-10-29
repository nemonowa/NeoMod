# エージェント向け 方針・規約 (Policy Rules)

この文書は、このプロジェクト (Minecraft 26.3 向け Fabric Mod 「neo」/ NeoMod) を触る**エージェントが必ず守る規約**です。
人間の開発者向けの説明は `docs/development.md`、変更の経緯は `refactor_notes.md`。ここは「やってよいこと・いけないこと」だけを書きます。
規約は **ID (P-xx)** で参照します。規約を変えるのは**ユーザーの指示があったときだけ**で、変えたら `tools/check_rules.py` も合わせて直します。

**変更を終える前に必ず**: `python tools/check_rules.py` を実行し、違反 0 にする (機械で確認できる規約: P-04 / 11〜14 / 16 / 26 / 40 / 41 / 50 / 52 / 60 / 61。チェックリストは末尾)。

---

## 1. プロジェクトの前提

- **P-01 対象**: Minecraft **26.3** (非難読化、リマップ不要)、**Fabric Loader** 向けの Mod。Java **25**。
- **P-02 ビルド**: Gradle / Loom は使わない。`build.bat` / `build.ps1` / `build.sh` が **javac + jar だけ**でビルドする。ビルドの正は**ユーザーの `build.bat` の結果**で、エージェントの手元のコンパイルは補助 (P-71)。
- **P-03 出自**: Mod Menu 部分は**完全に独立したフォーク**。機能が同じなら、コードはいくら変えてよい。Spawner Info 2.0.0 / EnchantToolTip 2.0.0 (どちらも CC0-1.0) は、元の jar にソースが無かったためデコンパイルして取り込んだもの。
- **P-04 言語**: ユーザーとのやり取り・コメント・ドキュメントは**日本語**。翻訳は `en_us` と `ja_jp` の**両方**を必ず用意する (`assets/neo/lang/`)。
- **P-05 アセットの名前空間**: 同梱アセット (テクスチャ・スプライト・アイコン・lang) は **`assets/neo/` だけ**に置く。名前空間は `neo.util.NeoAssets` (`NAMESPACE` / `path(...)`) を使い、`"modmenu"` などをアセットのパスに直書きしない。別物なので変えない: 翻訳キーの接頭辞 (`option.modmenu.…` など)・設定の保存先 (`options.txt` の `neo_` 行)・ネットワークのチャンネル ID・データの保存先 (`data/neo/`)。Mod の ID (`fabric.mod.json` の `id` と `mods.toml` の `modId`、`NeoForgeMod.MOD_ID`) は `neo` で、名前空間と同じ値だが別物なので `NeoAssets.NAMESPACE` から作らない。`tools/check_rules.py` が、名前空間が `neo` だけであること・参照しているアセットが実在することを検査する。

## 2. ソース構成

- **P-10 ルート**: `src/neo/java` = **ローダー非依存の本体**、`src/main/java` = **Fabric の接着部分** (入口・Mixin・Fabric アダプタ)、`src/forge` = Forge 版 (Mod Menu 本体のみ、通常ビルドに含めない)。
- **P-11 依存の向き**: `src/neo` は `src/main` / `src/forge` を**参照してはいけない**。`src/neo` は `net.fabricmc` / `org.spongepowered` / `com.llamalad7` / `net.minecraftforge` / `net.neoforged` を **import しない**。
- **P-12 分割パッケージ禁止**: 1 つのパッケージは**必ずどちらか一方のルートだけ**に置く。
- **P-13 階層を浅く**: パッケージは `neo.<名前>` か `neo.menu.<名前>` まで (最大 3 階層)。小さいパッケージは作らず統合する。**外部の名前に合わせる必要があるもの** (`com.terraformersmc.modmenu.api` = 他 Mod が実装する Mod Menu API、`eu.pb4…`、`org.jetbrains…`、`org.jspecify…` = コンパイル専用スタブ) だけ例外。
- **P-14 パッケージ循環禁止**: Mod Menu 内部は一方向 `util ← mod ← config ← service ← gui ← menu / mixin / api ← fabric`。クラス間の循環は `gui` 内の一覧とその行 (`ModListWidget` 系 6 クラス) **だけ**が意図的に許可されている。
- **P-15 ローダー依存の隔離**: ローダー固有の機能は抽象 (`ModManagerPlatform` / `IconSource` / `ModContact` など) の後ろに置き、Fabric 実装は `neo.fabric` に集める。新しいローダー依存を `src/neo` に持ち込まない。
- **P-16 Mixin**: Mixin クラスは `neo.mixin` (`src/main`) **だけ**。**薄く**する — ロジックは `src/neo` 側のクラスに置き、Mixin はそれを呼ぶだけ。設定は `mixins.neo.json` の **1 本**で、クラスと一覧を必ず一致させる。
- **P-17 Forge**: 当面の対象は **Mod Menu 本体だけ**。Spawner Info / EnchantToolTip / Mixin 依存部は対象外。Forge のために `src/neo` を汚さない。

## 3. データの保存 (共通規約)

保存は `neo.data.NeoData` を通す。

```
<ワールド>/data/
  minecraft/maps/<id>.dat    バニラのデータは、バニラの階層・形式のまま
  neo/<path>.dat             この Mod 固有のデータ (索引など) は、必ず neo/ の下
```

- **P-20 バニラのデータ**: バニラの階層・形式 (`SavedDataStorage`、gzip NBT、`MapItemSavedData` など) のまま置く。独自形式を作らない。
- **P-21 Mod 固有のデータ**: **`data/neo/<path>.dat`** に、バニラの SavedData と同じ gzip NBT (`{DataVersion, data}`) で置く。JSON・独自バイナリにしない。`path` は小文字・数字・`/ . _ -` だけ。
- **P-22 `config/` は設定専用**: ワールド (サーバー) ごとのデータを置かない。設定は**バニラの `options.txt`** に、キーの頭に `neo_` を付けた行 (**既定値から変えた項目の行だけ**を書く。既定値のものは行を作らない) (`neo_zoom:true`、enum は小文字の名前、`hidden_mods` などの集合は「,」区切り) で保存する (`ModManagerConfigManager`。`config/modmenu.json` は使わない・読まない)。`Options.FieldAccess` は package-private で Mixin の引数に書けないため、`MixinOptions` が `Options#load` の直前に読み、`Options#save` の直後に自分の行だけを足す。**Forge 版は対象外** (Mixin が無いので設定は保存されない)。
- **P-23 バニラの採番に触らない**: バニラの `last_id.dat` (`MapIndex`) など、サーバーが同じファイルをキャッシュして持つ採番・索引を**こちらから進めない** (サーバーが作るデータを上書きするため)。Mod 側の ID は `MapStore.ID_BASE` (1000000) 以上の別範囲で採番する。
- **P-24 書き込みは安全に**: 一時ファイルに書いてから置き換える (`NeoData` が行う)。ディスク書き込みでクライアントを止めない (非同期 / 間引き)。ワールドを離れるとき・終了時にも保存する。
- **P-25 置き場**: シングルは実際のワールドの `data` フォルダ (`getWorldPath(LevelResource.DATA)`)、マルチはワールドが無いので `gameDir/neo/<サーバー>/data/` に同じ構造。ファイル名・パスは必ずサニタイズする。
- **P-26 例外**: `mods/NeoMods.log` は起動ログ (最新の起動のみ・上書き) でデータではない。`ModsScreen` の「ドロップした Mod の jar を mods フォルダへコピー」(Mod Menu 本来の機能) はユーザー操作によるファイルコピーで、データの保存ではない。設定 (`options.txt` の `neo_` 行) の読み書きは `ModManagerConfigManager` (バニラの行は触らない)。それ以外の書き込みは `NeoData` か バニラの保管 (`SavedDataStorage`) を使う。

## 4. バニラ順守

- **P-30 バニラの仕組みを使う**: バニラにある処理・形式・範囲・縮尺・UI の作法は**再発明せず、そのまま使う** (例: 地図は `MapItem#update`・`MapItemSavedData#createFresh`・`SavedDataStorage`・スケール 0〜4・更新半径 128/64。視点は `Camera` / `MouseHandler` の既存の処理に乗る)。独自の近似を作らない。
- **P-31 バニラの挙動は壊さない**: 機能が OFF のときは**バニラと同じ動作**。ON のときも、バニラの規則 (権限・署名付きコマンドの確認・チート判定など) を迂回しない。
- **P-32 バニラの API は記憶で書かない**: 使う型・メソッド・フィールド・ordinal・記述子は、**26.3 の jar をデコンパイル / バイトコードで読んで確認**してから書く (`libs/minecraft/minecraft-26.3.jar`)。確認できないものは「未確認」と明記する。
- **P-33 バニラの見た目に合わせる**: UI は Minecraft の既存ウィジェット (`Button`・`EditBox` など) と GUI スケールの座標系で作る。独自の描画基盤を持ち込まない。

## 5. 機能の追加と設定

- **P-40 機能のスイッチは Mod Menu の設定に集約**: 内蔵機能の ON/OFF は `ModManagerConfig` の `BooleanConfigOption` で持つ。設定画面は `ModManagerConfig` の項目を自動で並べるので、UI の追加コードは要らない。`option.modmenu.<key>` と `.true` / `.false` の翻訳を `en_us` / `ja_jp` に必ず足す。
- **P-41 既定値** (この表の対象は「この Mod が内蔵する機能」。Mod Menu 本来の設定 — 並べ替え・バッジ・ファイル専用設定・タイトル画面の Mods ボタンの位置 (既定は「Realms を置換」。ユーザーが明示した) など — は対象外): 既定 **ON なのは 11 個だけ**: 進捗画面の拡大 (`large_advancements`)、クリックコマンドの確認省略 (`skip_command_confirmation`)、ズーム (`zoom`)、インベントリを開いても移動 (`inventory_move`。ユーザーが「既定 ON」と明示した)、進捗画面とインベントリの直接切り替え (`screen_switch`。ユーザーが修正を求めた)、3D スキンレイヤー (`skin_layers`。ユーザーが「規定は ON」と明示した)、ワールドマップ (`world_map`) と、その目印の自動表示 (`map_auto_markers`。構造物と旗を 1 つに統合。ユーザーが「自動で出す」と明示し、統合を指示した)、F5 自由視点 (`free_look`。ユーザーが「規定 ON」と明示した)、サウンドブラウザ (`sound_browser`。ユーザーが「既定 ON」を選んだ)、AFK シネマティック (`afk_cinematic`。ユーザーが「既定で放置 ON」と明示した。放置時間は後に 30 秒へ変更)。**それ以外はすべて既定 OFF** (ツールチップ・スポナー情報など)。新しい機能の既定は、**ユーザーが明示しない限り OFF**。既定を変えるときは、この表・`docs/development.md`・`tools/check_rules.py` を揃えて直す。
- **P-42 OFF は負荷にしない**: OFF のとき、毎フレーム・毎 tick・サーバー側の処理は**入口で打ち切る** (設定を 1 回引いて即 return)。OFF の間に割り当て・走査・保存・送信をしない。ON/OFF を切り替えたときに溜めた状態は捨てる。専用サーバーなど設定が届かない場所は、負荷の残りを文書に書く。
- **P-43 キーバインドは最小限**: 設定で切り替えられるものはキーにしない。キーを持つのは**即時の操作で、設定では代替できないもの**だけ (現状: ワールドマップ = M、スポナーの「最寄りのみ表示」、NeoMods の設定画面を開く = @ キー)。カテゴリは `NeoKeyMappings.MAIN`。新しいキーを足す前にユーザーに確認する。
- **P-44 参考にする他の Mod**: ユーザーが渡した jar は**動作の参考**にする。**コードを取り込まない** (独自実装)。ライセンス (WI Zoom は GPL-3.0-or-later など) を確認して README / 注記に書く。

## 6. サードパーティ

- **P-50 不要なサードパーティを避ける**: 使ってよいのは、**Minecraft / Fabric Loader が既に同梱・提供しているもの**だけ: Minecraft 本体、Mojang のライブラリ (DFU・Brigadier・Blaze3D 等)、Gson・Guava・slf4j・commons-lang3・fastutil・joml・lwjgl・netty、Fabric Loader、Mixin、MixinExtras。それ以外のライブラリ・ツールを**追加しない** (追加が必要に見えたら、先にユーザーに確認する)。
- **P-51 Fabric API は使わない**: Mixin と Fabric Loader の API で置き換える。jar-in-jar も使わない。
- **P-52 UI ツールキットを持ち込まない**: AWT / Swing 等を使わない (起動時の Swing ポップアップは削除済み)。
- **P-53 通信・収集をしない**: 外部への通信、テレメトリ、自動更新、利用状況の収集を入れない。
- **P-54 開発用ツールは同梱しない**: デコンパイラ (Vineflower) や JDK、検査スクリプトは開発時のみ。成果物の jar に入れない。

## 7. Mixin

- **P-60 手段の優先順位**: `@ModifyConstant` / `@ModifyReturnValue` / `@WrapOperation` (MixinExtras) を優先。`@Redirect` と `@Overwrite` は避ける。呼び出しを**置き換える**ときは、元の処理を `Operation#call` で呼べる形 (OFF のとき元の動作に戻せる形) にする。
- **P-61 `require` を明示**: 自分が書く注入には、注入の個数を `require = N` で**明示**する (Minecraft 更新で構造が変わったら、起動時に失敗して気付けるように)。`defaultRequire: 1` は残す。
- **P-62 注入先はバイトコードで確認**: 対象メソッド名・記述子・`ordinal`・呼び出しの所有クラスは、バイトコードで確認する (P-32)。
- **P-63 サーバーの安全**: 共通 (server でも読み込まれる) の Mixin やコードから、クライアント専用のクラス・`ModManagerConfig` を触らない。クライアントの設定は、クライアント側が更新する `volatile` の値で渡す。

## 8. 変更の進め方

- **P-70 リファクタリングは挙動を変えない**。挙動が変わる整理は、**変更案を一覧にしてユーザーが採否を決める**。
- **P-71 検証**: 手元に JDK 25 を入れ、**実際の API に対して自分のコードを完全にコンパイルする**。classpath は Minecraft 26.3 の jar + Gson / Guava / slf4j / commons-lang3 / fastutil / ASM / log4j-api (Ubuntu の apt で入る) に加え、**Mixin・MixinExtras・Fabric Loader・Mojang の DFU・Brigadier は GitHub からソースを `git clone --depth 1` して `-sourcepath` に渡す** (jar は maven が使えないため)。**「cannot find symbol」を「ライブラリが無いから」と除外してはいけない** (これで `ChunkPos.asLong` が 26.3 に無いという本物のエラーを見逃した)。許容するのは、そのコードが依存する**環境に実在するがここに無いライブラリ** (LWJGL の SDL など、`ParentEntry` の 2 件) だけで、除外した件数と中身を報告に書く。`javac -proc:none` は Mixin の対象 (メソッド名・記述子・ordinal) を検証しないので、それはバイトコードで確認する (P-62)。`tools/check_rules.py` も通す。**コンパイル・実機で確認していないことは、確認したことと分けて必ず書く**。
- **P-72 設計の選択はユーザーに聞く**: 設計方針・既定値・範囲・見た目などの**判断が分かれる点は、選択肢を挙げて質問**する。ユーザーの指示が具体的なときは、聞き返さず実装する。曖昧なまま走らない。
- **P-73 言われたことだけを実装**: 求められていない機能・キー・設定・ライブラリを足さない。足したくなったら提案にとどめる。
- **P-74 ドキュメントを揃える**: 機能・設定・規約を変えたら `docs/development.md` (設定表・機能の節)、`refactor_notes.md` (実施内容・確認できたこと・**未確認**・判断)、翻訳、この文書を**同時に**更新する。
- **P-75 報告**: 完了報告は日本語で、(1) 何をしたか (2) 確認できたこと (3) **未確認のこと** (4) ユーザーの判断が要る点、の順に短く書く。成果物は `/mnt/user-data/outputs` の zip (`*.class`・ビルド出力 (`bin/` とルートの `NeoMod.jar`) を含めない)。ただし `libs/` の jar (特に `libs/minecraft/minecraft-26.3.jar`) は**実体で入れる** (シンボリックリンクにしない。入っていないとユーザーの `build.bat` が全部失敗する)。

## 9. 安全

- **P-80 コマンド確認の省略**: 「署名が必要なコマンド」の確認は**残す** (本から勝手に自分名義で発言させられるのを防ぐ保護)。 **リンク (URL) を開く確認は省略する** (ユーザー指示。同じ設定 `skip_command_confirmation` に統合。`MixinGuiLinkConfirmation`)。
- **P-81 テレポート等**: `/tp` などを**コマンドとして送るだけ**。権限の判定を迂回しない・権限を前提にしない。
- **P-82 ファイル操作**: ユーザーが渡していない場所を読み書きしない。パスは組み立てずに `Identifier` 等で検証する。ワールドの既存データを**上書き・削除しない**。

## 10. エージェントの運用

- **P-90 モデルの使い分け**: トークンを節約するため、サブエージェントが使えるときは、**実装は Sonnet、単純作業は Haiku** に任せる。設計判断・ユーザーへの質問・最終確認はメインのエージェントが行う。使えない環境ではメインが直接行い、そのことを報告する。
- **P-92 古い環境でも最低限起動する (縮退起動)**: 本体 (`neo.*`) は Java 25 のバイトコード・Minecraft 26.3 専用。ローダーが最初に呼ぶ入口だけを `src/compat` (`neo.compat`。**Java 8 のバイトコード = `--release 8`**) に置き、環境が合う (Java 25 以上 かつ Minecraft 26.3 以上) ときだけ本体を呼ぶ。合わないときは `mods/NeoMods.log` に理由を書いて何もしない (**ゲームを止めない**)。そのため: (1) `fabric.mod.json` の `preLaunch` / `main` / `client` は `neo.compat.FabricGate::…` を指す (本体を直接指さない)。(2) `fabric.mod.json` に `mixins` を書かない (古い環境で Java 25 の Mixin クラスを読んでしまうため。`FabricGate#preLaunch` が `mixins.neo.json` を登録する)。(3) `depends` に `minecraft` / `java` を書かない。Forge の `mods.toml` にも Minecraft の版の依存を書かない。(4) `@Mod` は `neo.compat.ForgeGate` だけ (コンパイル専用の代用品 `src/compat/stubs` を使い、jar には入れない)。(5) `src/compat` は Java 標準 (と `@Mod` の代用品) 以外を import しない (リフレクションで文字列として呼ぶ)。(6) ビルドの基準は **JDK 25 (本体・Minecraft も 25)**、**Java 8 は `src/compat` だけ** (`JAVA_RELEASE` を変えても compat は 8 のまま。Eclipse は JDK 25 で全体をコンパイルするので、compat の Java 9 以降の書き方・API は `check_rules.py` が先に止める。最終的な基準は `--release 8`)。`check_rules.py` が (1)〜(6) を検査する。
- **P-91 ルールを守れない / 衝突するとき**: 黙って破らない。どの規約と衝突するかを示して、ユーザーに選択肢を出す。

---

## 変更前チェックリスト

1. [ ] 使う Minecraft API は 26.3 の jar で**確認した** (P-32)。確認できないものは「未確認」と書いた
2. [ ] `src/neo` にローダー依存を入れていない / `src/neo` → `src/main` の参照が無い / 分割パッケージ・パッケージ循環が無い (P-11〜14)
3. [ ] 保存は `NeoData` かバニラの保管を通した。バニラの採番・ファイルに触れていない (P-20〜26)
4. [ ] 機能スイッチは Mod Menu の設定 + `en_us` / `ja_jp` の翻訳。既定は P-41 どおり (新機能は OFF)
5. [ ] OFF のときの負荷は入口で打ち切った (P-42)
6. [ ] 新しいサードパーティ・Fabric API・UI ツールキットを足していない (P-50〜52)
7. [ ] Mixin は薄く、`require` を明示し、注入先をバイトコードで確認した (P-60〜63)
8. [ ] 実際の API (Minecraft jar + Mixin / MixinExtras / Fabric Loader / DFU / Brigadier のソース) に対して JDK 25 で完全にコンパイルし、エラー 0 (LWJGL SDL の 2 件を除く)。`python tools/check_rules.py` が違反 0
9. [ ] `docs/development.md` / `refactor_notes.md` / 翻訳 / この文書を更新した (P-74)
10. [ ] 報告に「確認したこと」と「未確認のこと」を分けて書いた (P-71, P-75)

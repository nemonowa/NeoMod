# リファクタリング記録 / 要判断リスト

## このファイルの読み方
- **「縮退起動を新しい版へ移植」より前の古い節は、下の「経緯の要約」にまとめた**。当時の原文 (確認の細部・当時の判断・当時の名前) は `REFACTOR_NOTES_archive.md` にそのまま残してある (更新しない)。それ以降の節は原文のまま、この下に続く。
- **名前の移り変わり** (古い記録は当時の名前で書かれている): Mod の ID `neomod` → `neo` / パッケージ `com.neo.*` → `neo.*` / `ModMenu*` → `ModManager*` (ただし外部互換の `com.terraformersmc.modmenu.api`・`modmenu` エントリポイント・`provides: modmenu`・翻訳キー `modmenu.*`・`option.modmenu.*` は据え置き / P-05) / 表示名は「NeoMods」/ 起動ログ `mods/NeoModLog.txt` → `mods/NeoMods.log` / ビルドの出力先 `build/` → `bin/` (jar は `build.bat` と同じ階層の `NeoMods.jar`) / `CheatGate` → `PermissionGate`。
- 「未確認」は、**書いた当時の記録**。その後に実機で確認した結果は、このファイルには無い (ユーザーの `build.bat` と実機の結果を、改めて記録に反映する必要がある)。
- この環境の限界 (P-71): 手元は JDK 21 の実行環境だけで、`javac` も依存 jar も無い時期がある。コンパイル・実機は、ユーザーの `build.bat` が正。

## 経緯の要約 (古い節。原文は `REFACTOR_NOTES_archive.md`)

### 1. 構成の整理 (挙動は変えていない)
- **Mod Menu 部分の循環依存の解消**: 24 クラスの循環を、一方向の層 `util ← mod ← config ← service ← gui ← menu / mixin / api ← fabric` に再編 (P-14)。神クラスだった `ModMenu` を、定数・登録簿・列挙・設定画面登録・Mod 数・ボタン文言に分解。`Mod` モデルから設定依存を除去。`ModsScreen` ⇄ 一覧の相互参照は `ModsScreenHost` で解消。設定保存 → キャッシュ破棄は `addSaveListener` の逆向き通知。残る循環は `gui` 内の 6 クラスだけ (意図的)。
- **ローダー依存の隔離** (P-10 / P-11 / P-15): `src/neo` = 本体、`src/main` = Fabric の接着部分。抽象 (`ModManagerPlatform` / `IconSource` / `ModContact`) の後ろに Fabric 実装を置く。分割パッケージなし。パッケージは最大 3 階層に統合。Mixin 設定は `mixins.neo.json` の 1 本。
- **Forge 版** (`src/forge`、Mod Menu 本体のみ、P-17): 入口・プラットフォーム・アイコン元の 5 クラスと `mods.toml`。当時から Forge のコンパイルは未検査 (Forge の jar が無い)。未確認だったもの: EventBus の `addListener` のオーバーロード、`RegisterKeyMappingsEvent` の発火時期、他 Mod の設定画面の登録時期。
- **アセットの名前空間を `neo` に統合** (P-05): `modmenu` / `spawnerinfo` / `neomod` のアセットと lang を `assets/neo/` へ。参照は `NeoAssets` (`NAMESPACE` / `path`) 経由。変えなかったもの: 翻訳キーの接頭辞、設定の保存先 (`options.txt` の `neo_` 行)、ネットワークのチャンネル ID、データの保存先、Mod ID。`check_rules.py` に P-05 の検査を追加 (陰性対照 7 件で検出を確認)。
- **整理**: Swing ポップアップの削除 (ログは最新の起動のみ上書き)、デッドコード・未使用 import・コメントアウトされた旧コードの削除、重複の除去 (`CameraTypeSwitch#set`、`HeldMapView` の升目の範囲 `CellRange`)、命名 (`drawContext` → `graphics`)、`EnchantToolTipClient` のルール表化、`SpawnerInfoClient` の分割。**見送り** (挙動が変わりうる / 大きい): `SoundBrowserScreen#clip` と `WorldMapScreen#clip` の統合 (実装が違い、書式コードで結果が変わりうる)、`FabricMod#getContainer` (外部向けアクセサ)、700 行級のクラス (`WorldMapScreen` / `ModsScreen` / `SoundBrowserScreen`) の分割。
- **規約 (`AGENTS.md` + `CLAUDE.md` + `tools/check_rules.py`)**: これまでの指示・判断を ID 付き (P-xx) の規約にまとめた。機械で検査できるもの: neo→main の参照・ローダー型の import (P-11)、分割パッケージ (P-12)、階層 (P-13)、循環 (P-14)、Mixin の配置と一覧 (P-16)、書き込み (P-26)、既定値と翻訳 (P-40 / P-41 / P-04)、サードパーティ・UI ツールキット (P-50 / P-52)、`require` と `@Overwrite` / `@Redirect` (P-60 / P-61)、縮退起動 (P-92)。
- **検証方法の見直し (P-71)**: `ChunkPos.asLong` が 26.3 に無いというビルドエラーを、手元のコンパイル確認で「ライブラリが無いため」と除外して見逃した。以降は、Mixin・MixinExtras・Fabric Loader・DFU・Brigadier・JOML のソースを GitHub から取得し、**実際の API に対して全体を完全にコンパイル**する。「cannot find symbol」を除外しない。許容は LWJGL SDL の 2 件 (`ParentEntry`) だけ。`javac -proc:none` は Mixin の注入先を検証しないので、バイトコードで確認する (P-62)。

### 2. 設定まわり
- **保存先**: `config/modmenu.json` をやめ、`options.txt` の `neo_<キー>:<値>` 行へ (P-22)。`Options$FieldAccess` が package-private で Mixin の引数に書けないため、`MixinOptions` が `Options#load` の HEAD で読み、`Options#save` の TAIL で自分の行だけ足す。**既定値から変えた項目だけ**書く。Forge 版は Mixin が無く対象外。
- **機能のスイッチは `ModManagerConfig` の `BooleanConfigOption` に集約** (P-40)。既定は **ON が 11 個だけ** (P-41)、新機能は明示がない限り OFF。OFF のときは入口で打ち切る (P-42。OFF 時の負荷を監査して、毎フレームの Gizmo 収集・サーバー側のスポナー走査・受信データの残りを修正。残る負荷は専用サーバーのスポナー情報送信)。
- **固定値にして設定から削除**: Mod Menu 由来の `easter_eggs` / `count_hidden_mods` / `hide_mod_links` / `hide_mod_license` / `hide_mod_credits` / `hide_badges` / `mod_count_location` / `show_libraries` / `count_libraries` / `count_children` / `quick_configure` / `random_java_colors` / `sorting` (と、連動して漏斗ボタン) / `translate_names` / `translate_descriptions`。`options.txt` に残る古い行は無視される。
- **設定画面**: 名称は「NeoModsの設定」。「既定に戻す」ボタンを追加。`rebuildWidgets()` を呼ぶと `OptionsSubScreen#layout` に部品が重なって積まれる不具合があり、**新しい画面を開き直す**形に修正 (スクロール位置は先頭に戻る)。
- **既定値の変更**: F5 自由視点を ON、タイトル画面の Mods ボタンの位置を「Realms を置換」(Mod Menu 本来の設定で P-41 の対象外)。ほかにユーザーの明示で ON: インベントリ移動・画面の直接切り替え・3D スキンレイヤー・ワールドマップと自動表示・サウンドブラウザ・AFK シネマティック。
- **ファイル専用の設定**: `@FileOnlyConfig` は設定画面に出さず `options.txt` にだけ持つ (`hidden_mods` / `hidden_configs` / `sound_favorites` など)。

### 3. Mod Manager の見た目と入口
- 「Mod Menu」→「Mod Manager」に改名 (表示名・クラス名)。「開く」キーは NeoMod カテゴリ・既定は JIS の @ キー (`KEY_LBRACKET`)。**Mods アイコンと @ キーの行き先は NeoMods の設定画面** (Mod 一覧に入る口はタイトル画面の「Mods」ボタンだけ。形式が「アイコン」だと一覧を開けない)。
- Mod 一覧: 並びは固定 (Java → Minecraft → NeoMods → 表示名の昇順)。Java は前提 Mod ではなく Mod として数える・説明は「Java実行環境 …」・アイコンは自作。Minecraft のアイコンは**バニラの標準リソースパックの `pack.png`** を参照 (`IconHandler#createVanillaPackIcon`。読めなければ「不明」のアイコン)。説明枠に NeoMods の機能一覧 (`modmenu.descriptionTranslation.neo`)。画面タイトルは「Mod Manager」(ホバーで説明)。Mods ボタンの Mod 数は一覧の「N個のModを表示中」と同じ数え方に統一、表記は「7 Mods Loaded!!」。
- タイトル画面に **Discord アイコン** (リンク https://discord.gg/nqx8esTwzS)。**アイコン画像はすべて 16x16 に統一** (Minecraft だけ 128px を縮小表示)。設定ボタンは歯車。Discord のロゴの利用条件は Discord 側のもの。公式ロゴではなく手描きのドット。
- **ビルド**: jar は `build.bat` と同じ階層の `NeoMods.jar`、途中の生成物は `bin/`。基準は JDK 25、`src/compat` だけ `--release 8` (P-92 縮退起動)。

### 4. 機能ごとの経緯
- **進捗画面**: 窓の拡大 (最初は画面の 85% → 見出し「進捗」と「完了」ボタンを消して `画面 − 2×30` に。ボタンが無いので Esc かキーで閉じる)。右下に「閉じる」タブ (エンチャント付きバリアブロック)。`@ModifyConstant` の個数は `require` で固定。**直接切り替え** (`ScreenSwitch#toAdvancements`): 全コンテナ画面・本 (教卓を含む)・ワールドマップが対象。文字入力画面・ポーズ画面は対象外。メニューを持つ画面からは `closeContainer()` を呼んでから進捗画面を開く (バニラの E と同じ。切り替えの瞬間にカーソルが中央に戻る可能性がある点が当時の「要確認」)。
- **コマンド・リンクの確認の省略** (P-80): 署名が必要なコマンドの確認は**残す**。リンク (URL) を開く確認は省略 (`MixinGuiLinkConfirmation`)。同じ設定 `skip_command_confirmation` に統合 (安全面の注意: 怪しい URL を誤って開きうる)。
- **ズーム** (WI Zoom 1.5 を参考、GPL-3.0-or-later のためコードは取り込まない / P-44): `Camera#calculateFov` の戻り値と `MouseHandler#turnPlayer` の感度。素手で右クリックを押している間。
- **F5 自由視点** (Perspective Mod 3.0 を参考): 最終的な動作は「**単押し = バニラの視点切り替え / 長押し (`HOLD_MILLIS` = 250 ms) = 自由視点**」(切り替え式 → 押している間だけ → 現在の形、と変遷)。`handleKeybinds` の `consumeClick` (ordinal 0) と `Options#setCameraType` の肩代わり、`turnPlayer` の `LocalPlayer#turn` の置き換え、`Camera#alignWithEntity` の `getViewYRot/XRot` (`require = 2`)。
- **3D スキンレイヤー** (3D Skin Layers 1.11.3 の動作を参考に独自実装。ライセンスが商用不可の保護ライセンスで、TRansition (外部送信) / TRender は P-50・P-53 違反のため使わない): 本体 6 レイヤー・一人称の袖・頭ブロック / 頭アイテム / 着用中の頭。距離 16 ブロック・厚さ固定。頂点数の削減 (外側は長方形ごとに 1 枚、側面は同色を 1 枚、角の側面は省かない) で四角形は約 47.6%。Z-fighting 対策で厚さを内側 0.5px / 外側 0.4px に。Deadmau5 の耳のモデルは何もせず返す (キャッシュの衝突と二重描画を防ぐ)。一時的な失敗は 5 秒後にやり直す。Mixin の適用は、Mixin 0.8.7 の本物の変換エンジンで確認した (Fabric Loader 同梱のフォークでは未確認)。**未対応 (要判断)**: 角で隣り合う 2 面の側面が同じ平面に重なる箇所 (色が違うと縁で Z-fighting しうる。直すと見た目が変わる)。
- **サウンドブラウザ**: クリエイティブ画面の右側面のタブ (バニラの 14 枠は満杯で、Fabric API は使えない) → 専用画面。名前空間・階層のツリー + 検索 (ID と字幕)、ピッチ (0〜2)・音量・カテゴリ、お気に入り (`neo_sound_favorites`)、`/playsound` のコピー。見た目はバニラのスプライトだけ。一覧は画面いっぱい (2 列)。字幕は `sounds.json` の `subtitle`。
- **AFK シネマティック** (Ji AFK Cinematic 2.3.1 (MIT) を参考。音楽・キー割り当て・周回上限は取り込まない): 放置 30 秒 (`IDLE_TICKS`)、黒帯 2.1:1 (`CinematicLetterbox.ASPECT`)、カットは独自設計、釣り中は浮きが見える距離。F1 は「GUI 有り → 隠す → (もう一度) シネマティック → (中で) F1 で終了して GUI 有り」と一巡する (設定 ON では、F1 で隠した GUI をバニラのように F1 で戻せない)。HUD は `Hud#toggle` で隠してチャットだけ描き直す。
- **ワールドマップ** (`neo.map`):
  - 経緯: 独自タイル → バニラの地図データ・更新・縮尺 (0〜4) に作り直し → **バニラの両手持ちの地図の描画をそのまま使う** 形に (メインハンドに仮想の地図を持たせ、傾きは `calculateMapTilt` を 0 に固定、画面上の位置は `HandMapGeometry` で計算)。中身だけ **512×512 の自前テクスチャ** (P-30 の例外。ユーザーが明示)。書き直しは間引き (動かしている間 0.15 秒に 1 回、止まっていれば 1 秒に 1 回だけ版を確認)。装飾は 128 の座標系の半ピクセル単位。
  - 保存 (P-20〜25): バニラのデータはバニラの階層・形式のまま、この Mod 固有のデータは `data/neo/<path>.dat` (gzip NBT)。ID は `MapStore.ID_BASE` (1000000) 以上で**別に採番** (バニラの `last_id.dat` を進めると、サーバーが次に作る地図が同じ ID になって上書きされるため。P-23)。ピンは保存しない。
  - **訂正 (誤りだったもの)**: ①「バニラはインベントリの地図をすべて毎 tick 更新する」→ 手に持っている、ロックされていない地図だけ (最大 2 枚/tick)。当方は 1 tick 4 枚 (`MapUpdater#UPDATES_PER_TICK`)。② 構造物は手動のピン以外は出ていなかった。③「本物の旗は地図のデータから自動で出る」→ 誤り (旗の目印は地図を持って右クリックしたときだけ追加される)。
  - 自動表示 (`MapMarkers`): 構造物はシングルのみ (サーバー側にしか配置が無い)、構造物の近く 96 ブロックで「発見済み」、旗はロード済みチャンクから拾う (上限 4096)。設定は `map_auto_markers` に統合 (既定 ON)。保存は `data/neo/map/markers.dat`。ピンのアイコンが上下逆だった不具合を回転 8 (`MapMarkers.UPRIGHT`) で修正。
  - UI の整理: 地図の左右にタブを並べた版は「改悪」としてロールバック。機能のない「情報」「操作」ボタン・N のコンパス・縮尺バーは削除し、情報と操作方法は**地図の上のカーソルの横のツールチップ**に統合。**左の欄 = 地図の操作、右の欄 = ピンの機能だけ**。
  - **広域スキャン**: 表示中の範囲 (縮尺に合わせた四角) を、内蔵サーバーでチャンクを生成して一括で地図にする (`addTicketAndLoadWithRadius(PLAYER_LOADING, …)` で読み込みのみ、地点ごとにバニラの `MapItem#update` を `RemotePlayer` の視点で 16 回、縁 2 ピクセルを除いて写す)。負荷の制御はサーバー 12 ms/tick・同時 4 地点・クライアントの写し 4 ms/tick。ボタンのツールチップに範囲と予想時間 (初期値は 1 地点/秒 = 根拠のない**目安**、実行中の実測で更新)。範囲が広いと数時間・数 GB になりうる (未測定の概算)。開始直後に失敗する不具合 (`Tried to access entity ID before ID assignment`) は仮のプレイヤーの ID を -1 にして修正。
- **スポナー情報**: `isCreative` 限定にした経緯がある (表示側・サーバー側とも)。現在の条件は最新の節 (PermissionGate) を参照。

### 5. 初期の「要判断」リストの現状
- build.sh が実態と不一致 → **対応済み** (下の「build.sh を build.ps1 に揃える」)。
- スタブの置き場 (`org/jetbrains`・`org/jspecify`・`eu/pb4` が `src/neo/java` にあり、`build.ps1` が名前決め打ちで jar から除く) → 未対応。
- 巨大クラスの分割 (`ModsScreen`・`FabricMod`・`ModListWidget`・`DescriptionListWidget`、のちに `WorldMapScreen` など) → 未対応 (実機ビルドできる状態で段階的に、が推奨)。
- `SpawnerInfoMod` の `NAME_CACHE` が datapack のリロードで更新されず、空文字も永続する / 名前空間 `"neofunction"` の決め打ち → 未対応。
- 日本語文字列の直書き (ツールチップ・スポナー表示) の lang ファイル化 → 未対応。
- Mixin の `required: true` + `defaultRequire: 1` (Minecraft 更新で注入先が変わると起動時に失敗して気付ける) → 現状維持。
- 配布物に Mojang の `minecraft-26.3.jar` (約 41MB) を同梱している (build.bat のため。再配布する場合のライセンス上の扱いに注意)。

### 6. 古い節の「未確認」の主なもの (実機で確認するもの。当時の記録)
- **起動時の Mixin の適用** (`require` の成否): 進捗画面・ズーム・F5・スキンレイヤー・マップ・シネマティック・リンク確認ほか、各機能の Mixin。
- **見た目**: 進捗画面の窓・タブ・閉じるタブ、手持ち地図の位置と大きさ・クリック位置のずれ・装飾・512 画像の収まり、3D スキンレイヤー (向き・色・角のすき間・HD スキン)、サウンドブラウザのタブ・枠・字幕、シネマティックのカット・黒帯、アイコン列の並び・16px 画像、Mods 画面の検索欄と件数表示。
- **操作・負荷**: F5 長押し 250 ms の体感、ズームの感度、広域スキャンの負荷・所要時間・チケットの後始末・ネザー/エンド、手持ち地図の書き直しの負荷、スキンレイヤーの描画負荷と Sodium / Iris との相性、専用サーバーでのスポナー情報送信。
- **設定・保存**: `options.txt` の `neo_` 行の読み書き、「既定に戻す」の表示更新、`hidden_mods` が空のときの行。
- **その他**: JIS の @ キーが `KEY_LBRACKET` として届くこと、直接切り替えでのサーバー側の閉じ通知とカーソル位置、Minecraft アイコン (`vanilla` パックの `pack.png`) が出ること、Discord アイコンの押下、Forge 版 (コンパイルすら未検査)。

---

## 古い環境でも最低限起動する (縮退起動) を新しい版へ移植 (ユーザー指示)
- 経緯: 古い版 (neomod-mod-manager.zip, 10/17) にだけあった縮退起動 (`src/compat`) が新しい版 (neomod.zip, 10/20) に無かったため、新しい版を土台に移植した。ログ名は新しい版に合わせて `mods/NeoMods.log`、規則番号は新しい版の P-90 (モデルの使い分け) と衝突するため P-92 にした。
- 以下は古い版での元の記録 (読み替え済み):

## 古い環境でも最低限起動する (縮退起動) を追加 (ユーザー指示)
- 依頼: 古い `NeoMod.jar` は古い Forge / Fabric / Java でも最低限起動した。新しいほうもそうしたい。回答: **Fabric と Forge の両方**、古い環境では**ログだけ書いて機能は無効 (ゲームは止めない)**、Java の下限は **8 以上**。
- 以前の状態 (比較して分かったこと): 新しいほうは `depends` (fabricloader≥0.19.2 / minecraft≥26.3- / java≥25)・`--release 25`・Forge の `minecraft [26.3,26.4)` 必須で、古い環境では**読み込みを拒否**していた (起動できない)。
- 実装 (新規 `src/compat` = パッケージ `neo.compat`、**`--release 8`**): `CompatCheck` (環境の判定とログ。Java のメジャー・Minecraft のバージョンの解析・`mods/NeoMods.log` への書き込み・本体へのリフレクション呼び出し)、`FabricGate` (`preLaunch` / `main` / `client`)、`ForgeGate` (`@Mod("neo")`、引数なしのコンストラクター)。**環境が合う (Java 25 以上 かつ Minecraft 26.3 以上) ときだけ本体を呼ぶ**。Minecraft の版が分からないときは Java だけで判断する (25 以上なら動かす = これまでと同じ)。合わないときは、ログ 1 つ (ローダーの版・Minecraft・Java・必要な環境) を書いて何もしない。ログが書けなくても標準エラーに出すだけで続行する。
- 変更点: `fabric.mod.json` — `entrypoints` の `preLaunch` / `main` / `client` を `neo.compat.FabricGate::…` (メソッド参照。ローダーの型を import しなくてよい) に、**`mixins` と `depends` (minecraft / java / fabricloader) を削除** (古い環境で Java 25 の Mixin クラスを ASM が読んで止まるのを避けるため、Mixin の設定は `FabricGate#preLaunch` が `Mixins.addConfiguration("mixins.neo.json")` で、環境が合うときだけ登録する)。`modmenu` の入口は据え置き (読むのは Mod Menu 本体 = 合うときの自分自身だけ)。本体: `NeoMain#start()` / `NeoClient#start()` を追加 (既存の `onInitialize` / `onInitializeClient` はそのまま)。Forge: `NeoForgeMod` から `@Mod` と `FMLJavaModLoadingContext` の引数を外し、`start()` (static) に (処理は同じ)、`mods.toml` から Minecraft の版の依存を削除。ビルド: `build.ps1` / `build.sh` に `src/compat` を `--release 8` でコンパイルする手順を追加。Forge の `@Mod` はコンパイル専用の代用品 `src/compat/stubs` を使い、**jar には入れない** (本物の Forge の jar は新しい Java 用で、Java 8 のコンパイラが読めないため)。`check_rules.py` に **P-92** を追加 (入口の指す先、`mixins` / `depends` が無いこと、`src/compat` が Java 標準以外を import しないこと、`@Mod` が `ForgeGate` だけ、`--release 8`)。AGENTS.md に P-92、README に「古い環境での動作」。
- 確認できたこと: JDK 25 で `--release 8` のコンパイルが通り、出力のクラス形式が **52 (Java 8)**、Forge の代用品が出力に**入っていない**。単体のチェック 36 件 (版の解析 `26.3` `26.3.1` `26.3-rc1` `26.3-snapshot-2` `1.20.4` `abc` `null` など、Java の版 `1.8` `17` `25` など、判定の組み合わせ) が**すべて合格**。偽のローダー (FabricLoader / Mixins / 本体クラスの代用品) を使った入口の動作確認: Java 25 + MC 26.3 / 26.3-rc1 → Mixin 設定の登録・本体の `start()` 呼び出し・ログなし。Java 25 + MC 1.20.4 / 26.2.1、Java 21 + MC 26.3 / 1.20.4 → ログのみ・例外なし。ローダーが無い (版が不明) → Java 25 は動かす、Java 21 は無効。`mods` を作れない場合 → 標準エラーに出して続行。Forge 側 (偽の `MCPVersion` / `FMLLoader#versionInfo`) も同様の結果。`ForgeGate` のクラスに `Lnet/minecraftforge/fml/common/Mod;` の注釈 (値 `neo`) が入っている。型検査エラー 0 (依存ソース側 155 件は変わらず)、`check_rules.py` 違反 0、P-92 の陰性対照 4 件 (`mixins` を書く / `depends` に minecraft / `main` が本体を指す / compat がローダーの型を import) がすべて検出された。
- **未確認 (要・実機)**: ① **本物の古い Fabric / Forge / Java 8 での動作** (この環境には Java 8 と古いローダーが無い。Java 8 は `--release 8` と、Java 21 での実行で代用)。② **Fabric の「メソッド参照」の入口 (`Class::method`)** が、古いローダーで使えるか (使えない版では、ローダー側の読み込みエラーになる)。③ **26.3 でのMixin 設定の動的登録** (`Mixins.addConfiguration` を `preLaunch` で呼ぶ方式に変えた = **合う環境でも実機で初めて確認できる変更**。`fabric.mod.json` に書く従来の方式と、Fabric が付ける設定の付随情報 (どの Mod の設定か) が違う可能性)。④ **Forge**: 引数なしの `@Mod` クラスのコンストラクターが 26.x の Forge で受け付けられるか、Forge 側は Forge の jar が無く、コンパイルも未検査。⑤ 実際の `FabricLoader` / `MCPVersion` のリフレクション (偽物でしか試していない)。
- **ユーザーの判断が要る点 / 注意**: (1) **合う環境 (26.3) の起動方法が変わった** (Mixin の登録がデータ → コード、入口が本体 → ゲート)。動作は同じはずだが、実機で必ず確認してほしい。問題があれば、Mixin の登録を元の方式に戻す代わりに古い環境のためのゲートを外す、などの案がある。(2) **本物の Mod Menu とは併用できない** (`provides: modmenu`)。古い環境でも同じ。(3) 古い環境では、本体の機能は何も動かない (できるのは「ゲームを止めない」「ログに理由を書く」まで)。(4) Java 8 を下限にしたが、Fabric / Forge のローダー自体の最低要件はローダー側の仕様 (古すぎるローダーは対象外)。(5) `modmenu` の入口 (`neo.menu.ModManagerCompat`) は据え置いたので、古い環境に**本物の Mod Menu** があると、これを読もうとして失敗しうる (併用不可の (2) と同じ状況)。

## 広域スキャンが開始直後に失敗する不具合の修正 (ユーザー提供の修正)
- 症状: 広域スキャンを開始するとすぐ失敗し、`IllegalStateException: Tried to access entity ID before ID assignment` (`MapItemSavedData#getHoldingPlayer` → `Entity#hashCode` → `Entity#getId`)。
- 原因: 視点用の仮のプレイヤー (`RemotePlayer`) はワールドに追加しないので ID が 0 (未割り当て) のまま。26.3 の `Entity#getId` は ID 0 で例外を投げ、バニラの `MapItem#update` が持ち主を HashMap に入れるときに止まる。
- 修正: `WideMapScan` で仮のプレイヤーを作ったあと `setId(-1)` を呼ぶ (本物のエンティティの ID は 1 から付くので重ならない。この HashMap は升目ごとに新しく作る地図データのもので、ほかへの影響なし)。変更は `WideMapScan.java` の 1 か所のみ。
- 備考: 提供された差分は古い版 (`tier.radius` を使う版) 向けだったため、新しい版 (`spanBlocks, centerX, centerZ` を使う版) の同じ行に手で同じ修正を当てた。未確認 (要・実機): 広域スキャンが最後まで動くこと。

## 縮退起動の移植の抜けを補う (調査の指摘)
- `setup-eclipse.ps1` と `.classpath` に `src/compat/java` と `src/compat/stubs` を追加 (Eclipse でも `neo.compat` がコンパイル対象になる。Eclipse は全体を 1 つの水準でコンパイルするので Java 8 の確認はできない。正式なビルドの `--release 8` が基準)。
- `build.sh` のソース jar に `src/compat/java` を追加。
- 備考: `build.sh` は `build.ps1` より古い手順 (存在しない `stubs` ディレクトリ・`LICENSE_*` の参照、Forge 非対応) のままで、この移植の対象外。正式なビルドは `build.ps1`。

## ビルドの基準は JDK 25、Java 8 は縮退起動 (compat) だけ (ユーザー指示)
- 確認: `build.ps1` / `build.sh` の既定は `--release 25` (JDK 25 以上が必須)、`src/compat` だけ `--release 8` を固定 (`JAVA_RELEASE` の影響を受けない)。`mixins.neo.json` は `JAVA_25`、gate の判定も Java 25 以上。
- 追加: `check_rules.py` (P-92) に、`src/compat` の Java 9 以降の書き方・API の検査 (`var` / `List.of` 等 / `String#isBlank` 等 / `record`・`sealed`・テキストブロック・`instanceof` パターン) と、`build.sh` が compat を `--release 8` でビルドしていることの検査を追加。Eclipse (JDK 25 で全体をコンパイル) では compat の Java 8 違反に気づけないための先回り。陰性対照 3 件 (`List.of` / `var` / `isBlank`) はすべて検出、元に戻して違反 0。

## 広域スキャンを設定から外し、ワールドのコマンド設定が ON なら自動で使えるようにする (ユーザー指示)
- 変更: 設定 `map_wide_scan` (`ModManagerConfig.MAP_WIDE_SCAN`) と、その言語キー (`option.modmenu.map_wide_scan*`) を削除。`check_rules.py` の既定値の表からも削除。options.txt に古い `neo_map_wide_scan` の行が残っていても、読み込みは対象のキーだけを見るので無視される。
- 使える条件 (`WideMapScan#available`): ワールドマップ ON・シングルプレイ (内蔵サーバー)・**`server.getWorldData().isAllowCommands()` (そのワールドの「コマンドの使用」= チート) が ON**。以前の「クリエイティブ」と「設定 ON」の条件は外した。条件が合えばマップ画面にボタンが自動で出る。実行中に条件が外れた場合は、これまでどおり止まる。
- ツールチップの注意書き (`neo.map.scan.tooltip.note`) を新しい条件に合わせて更新。README を更新。
- 未確認 (要・実機): コマンドの使用が OFF のワールドでボタンが出ないこと、LAN に公開して許可を変えたときの `isAllowCommands` の値。

## ゲームモードで出し分けていた機能を、チート ON 限定に変える (ユーザー指示)
- 洗い出し: ゲームモードで分けていたのは、スポナー情報 (表示側・サーバー側とも `isCreative`)、サウンドブラウザ (クリエイティブのインベントリ画面)、ワールドマップ (スペクテイターで開けない) の 3 系統。広域スキャンは先に「コマンドの使用が ON」へ変更済み。
- 変更したもの: スポナー情報の**表示側** (`SpawnerInfoClient#isEnabled`) を、`isCreative` から新設の `neo.util.PermissionGate#allowed` に。シングルは `server.getWorldData().isAllowCommands()`、マルチはクライアントがワールドの設定を知らないので `player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)`。設定項目の表示名 (en / ja) と README の注意書きを合わせて更新。
- 変更しないもの (ユーザー指示): ①**サーバー側の送信** (`SpawnerInfoMod#onServerTick` は従来どおりクリエイティブのプレイヤーにだけ送る) ②**画面が開かない / 出ない条件** (スペクテイターでワールドマップが開かない・サウンドブラウザはクリエイティブのインベントリ画面にだけ付く) ③**設定項目の有無** (表示名の文言だけ更新)。
- 注意: ①を変えていないので、スポナー情報は「チート ON かつ クリエイティブ」のときだけ実際に表示される (サバイバルでチート ON でも、サーバーが送らないので出ない)。
- 未確認 (要・実機): コンパイル (この環境では fastutil など Minecraft の依存 jar が無く、全体のビルドはできない。使った API は javap で存在を確認)、マルチでの権限判定。

## PermissionGate を「権限」だけの判定に一本化する (ユーザー指示)
- 経緯: 機能の可否が「クリエイティブ限定」「チート ON 限定 (`isAllowCommands`)」「権限はサーバー任せ」とばらばらだった。
- 変更: `neo.util.PermissionGate#allowed(Player)` を、`player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)` **だけ**にした (シングルの `getWorldData().isAllowCommands()` の分岐を削除)。クライアント専用のクラスを import しないので専用サーバーでも読める (P-63)。引数は `LocalPlayer` / `ServerPlayer`。
- 使う場所 (権限に統一したのはスポナー情報の 2 か所だけ): ①スポナー情報の表示側 (`SpawnerInfoClient#isEnabled`) ②スポナー情報の**サーバー側の送信** (`SpawnerInfoMod#onServerTick`。`isCreative` をやめた。前回の「変えない」を今回の指示で変更) (広域スキャンは、内蔵サーバーを使うシングル専用なので、当初は権限に寄せたが、ユーザーの指摘でワールドの `isAllowCommands` に戻した。)
- 挙動の変化: スポナー情報は、**サバイバルでも権限があれば表示される** (以前はクリエイティブ限定)。クリエイティブでも権限が無ければ表示されない。広域スキャンは変更なし (ワールドの「コマンドの使用」で判定)。
- 変えていないもの: ワールドマップ (スペクテイターで開かない = バニラの手が描かれないため) とサウンドブラウザ (クリエイティブのインベントリ画面にだけ付く) は、権限ではなく**画面の都合**なので据え置き。構造物の自動表示 (シングルのみ) はデータがサーバー側にしか無いため据え置き。マップの `/tp` は P-81 のとおり権限を判定せず、サーバーに任せる (失敗時に Mod 側が何も出さない点も据え置き)。
- 文言: 設定項目の名前 (`option.modmenu.spawner_info`) と広域スキャンの注意書き (`neo.map.scan.tooltip.note`) を en / ja で更新。README を更新。
- 確認できたこと: 26.3 の jar のクラス定数で `Player` / `ServerPlayer` / `LocalPlayer` に `permissions` と `hasPermission`、`Permissions.COMMANDS_GAMEMASTER` があること。`check_rules.py` 違反 0。`isCreative` / `isAllowCommands` が `src` (menu を除く) に残っていないこと。
- **未確認 (要・実機)**: ① コンパイル (この環境は JDK 21 のみで、Minecraft の依存 jar も無く、`javap` も無い。`Player#permissions()` の可視性・戻り値は定数の存在までで、シグネチャは未確認) ② シングルで「コマンドの使用」ON/OFF のワールドの権限が `LocalPlayer#permissions()` に反映されること、LAN 公開後に許可を変えたとき ③ 専用サーバーで OP にした/外したときの `ServerPlayer` の判定。

## `CheatGate` を `PermissionGate` に改名 (ユーザー指示)
- 判定が「チート」ではなく権限 (ゲームマスター以上) だけになったため。`neo.util.CheatGate` → `neo.util.PermissionGate` (呼び出しはスポナー情報の 2 か所)。挙動の変更なし。上の節の記述も新しい名前に書き換えてある (当時の名前は `CheatGate`)。
- 確認できたこと: `CheatGate` の参照が `src` に残っていないこと、`check_rules.py` 違反 0。未確認: コンパイル (この環境では不可)。

## ワールドマップの全画面表示と、全画面化ボタン (ユーザー指示)
- 指示と選択: 「全画面 UI と全画面化ボタン」。選択肢のうち、**地図を画面いっぱいに拡大する (左右の欄は隠す。描画の変換を変える)** と、**前回の状態を覚える (options.txt の neo_ 行に 1 項目)** を選んだ。
- 方式: 地図はバニラの両手持ちの地図を 3D で描いたものなので、手の描画の視野角 (`Camera#calculateHudFov` = `modifyFovBasedOnDeathOrFluid(partialTick, 70.0F)`、バイトコードで確認) の定数 70 を、全画面のときだけ `HandMapGeometry.FULLSCREEN_FOV` (= 2·atan((0.04 + 0.38) / 0.72) ≈ 60.5°) に差し替える。新しい Mixin `MixinCameraMapFullscreen` (`@ModifyConstant`、`require = 1`、ロジックは `HeldMapView#hudFov`)。バニラの変換 (両腕・枠・装飾) は触らず、視野角だけなので、すべて同じ倍率で拡大される。死亡・水中の補正は従来どおり通る。
- 幾何: `HandMapGeometry.of(w, h, fullscreen)` を追加 (既存の `of(w, h)` は通常)。地図の上端が画面の上端に接する (高さの約 90.5%)。画面の座標 ⇔ ブロックの変換・クリック・ドラッグ・ズームは、画面の `geometry()` を通すので、全画面でもずれない。欄の幅 (`panelWidth`) は通常表示の幾何で決める。
- 画面: 左の欄の上 (4, 2) に切り替えボタン (欄と同じ幅。文言は「全画面」/「全画面を終了」)。**左右の欄・ピン一覧・広域スキャンは、全画面でも隠さず地図の上にそのまま重ねる** (ユーザー指示。当初は隠す案で作ったが、「全画面地図の上にもボタンを書けばよい」で変更)。欄の幅は通常表示の幾何で決める。切り替えは `rebuildWidgets()`。
- 状態: `ModManagerConfig.MAP_FULLSCREEN` (`@FileOnlyConfig`、既定 OFF = 通常表示。`neo_map_fullscreen` は既定から変えたときだけ書かれる)。切り替えたとき `options.save()`。マップを開くときに読む。設定画面には出ない (P-40 の「機能のスイッチ」ではなく画面の状態なので、`SOUND_FAVORITES` と同じ扱い)。
- 翻訳: `neo.map.fullscreen` / `neo.map.fullscreen_exit` (en / ja)。キーは増やしていない (P-43)。
- 確認できたこと: `Camera#calculateHudFov(F)F` のバイトコード (`aload_0, fload_1, ldc_w 70.0f, invokevirtual modifyFovBasedOnDeathOrFluid, freturn`)、`Screen#rebuildWidgets` の存在、`check_rules.py` 違反 0、JSON (lang / mixins) の構文。
- **未確認 (要・実機)**: ① コンパイル (JDK 21 の実行環境のみ。`javac` も依存 jar も無い) ② 全画面で地図が上端に接して見えること、クリック位置・ピンの枠・ツールチップがずれないこと (`HandMapGeometry` の式は通常表示のものと同じ形で、視野角だけ変えた計算上の値) ③ 両腕が画面の下にはみ出す見た目 ④ 全画面で地図の紙の枠が上と左右で切れること ⑤ 開き直したときに状態が戻ること、`options.txt` の `neo_map_fullscreen` 行。
- 判断が要る点: 高さ約 90% で上端が接する形なので、「下に約 10% 余る」。完全に画面いっぱいにするには、地図の位置を中心に寄せる変換 (バニラの `renderTwoHandedMap` の平行移動) への差し込みが要る (視野角だけでは不可)。必要なら別途。

## 全画面が「近づくだけ」で画面全体に広がらない不具合の修正 (ユーザー指示)
- 原因: 全画面の拡大率は「地図の上端が画面の上端に接する」(高さ基準 = 画面の高さの約 90%) で固定だった。地図は正方形なので、横長の画面では左右が大きく余り、拡大しただけに見えた。
- 修正: `HandMapGeometry.fullscreenFov(guiWidth, guiHeight)` を追加し、視野角を**画面の縦横比から**決める。地図が画面全体を覆う (横長 = 地図の幅が画面の幅に接する / 縦長 = 地図の下端が画面の下端に接する)。16:9 で約 33°。`HandMapGeometry.of(w, h, true)` も同じ式を使うので、画面 ⇔ ブロックの変換・クリック・ドラッグ・ズームは全画面でもずれない。`HeldMapView#hudFov` が `Window#getGuiScaledWidth/Height` (Screen の width/height と同じ値) を渡す。Mixin・設定・翻訳・キーは増やしていない。
- 下地の廃止 (ユーザー指示「全画面では地図のテクスチャの縁取りも必要ない」「背景が面もなく完全に 2D の地図だけか」): バニラは `renderMap` で、地図 (0〜128) の外に 7 ピクセルの縁を付けた 142x142 の下地 (`map_background` / `map_background_checkerboard`) を 1 枚の四角として出す。全画面のときだけ、`MixinFirstPersonHandsAndItemsRenderer` が `SubmitNodeCollector#submitCustomGeometry` (`renderMap` の ordinal 0。バイトコードで 1 箇所だけと確認) を `@WrapOperation` し、呼ばない。下地が無いと未探索 (透明) の部分からワールドが透けるので、全画面のときは自前の画像 (`HeldMapView` の 512x512) の透明なピクセルを暗い灰色 (`FULLSCREEN_BACKDROP`) で塗る (全画面の切り替えで画像を書き直す)。両腕 (`renderTwoHandedMap` が 2 回呼ぶ `renderMapHand`。バイトコードで 2 箇所と確認) も `@WrapOperation` (`require = 2`) で描かない (ユーザー指示「腕なども描画しない」)。通常表示はバニラのまま。地図そのものは、バニラの手持ち描画が出す「正面を向いた平らな四角」(傾き 0) で、GUI の 2D 描画ではない (見た目は 2D)。
- 副作用 (仕様): 横長の画面では、地図の上下が画面の外に出る (16:9 で地図の縦の約 56% が見える)。地図の中心は画面の中心より少し上 (バニラの両手持ちの位置のずれ 0.04) に来る。広域スキャンの範囲 (`shownRange`) は、従来どおり画面外を含む正方形全体。
- 確認できたこと: 変更した `HandMapGeometry` / `HeldMapView` は JDK 25 + Minecraft 26.3 jar でエラー 0 (`Window#getGuiScaledWidth/Height` と `Camera#calculateHudFov` の `70.0f` をバイトコードで確認)。`tools/check_rules.py` 違反 0。16:9・4:3・縦長で、地図の長方形が画面全体を覆うことを式で確認。
- **未確認 (要・実機)**: ① 実機で地図が画面全体を覆い、隙間 (特に右端・下端の 1 px) が出ないこと (視野角の計算は GUI 座標の縦横比、実際の投影はフレームバッファの縦横比なので、GUI スケールによっては 1 px 未満ずれうる) ② クリック位置・ピンの枠・ツールチップが全画面でずれないこと ③ 下地・縁取りが消え、未探索の部分が灰色で、ワールドが透けないこと・切り替え直後に灰色が反映されること (Mixin の適用は起動して初めて分かる) ④ 全画面で腕が出ないこと (縦長の窓など腕が画面に入る場合) ⑤ `WorldMapScreen` を含む全体のコンパイル (この環境には DFU / slf4j などが無く、全体は通せていない。変更した 2 クラスのみ確認)。

## build.sh を build.ps1 に揃える (ユーザー指示)
- 経緯: `build.sh` は古い手順のまま (存在しない `stubs/`・`LICENSE_*`、Fabric API の jar-in-jar、`--main-class neo`、`-sources.jar`) で、Forge と `src/compat` の一部に未対応だった。`build.ps1` を正として手順をそろえた (出力は同じ: `build.sh` と同じ階層の `NeoMods.jar`、途中の生成物は `bin/`)。
- 変更: (1) 手順を 3 つ (ソース → リソース → jar) に。コンパイル専用のスタブ (`org/jetbrains`・`org/jspecify`・`eu/pb4`) は `src/neo/java` の中にあるので、コンパイル後に `bin/classes` から取り除き、空になったディレクトリも消す。(2) classpath: `libs/` の全 jar に加えて、`.minecraft/libraries` は**ライブラリごとに最新の版だけ** (`sort -V`。以前は全部の版を入れていた)。Fabric Loader の jar に入れ子の jar (MixinExtras) があれば `bin/nested` に取り出して**最後に**足し、古い MixinExtras (0.3.x〜0.4.x) は外す。(3) `src/compat` は `--release 8` (従来どおり)。(4) **Forge は任意**: `libs/forge/` に jar があるとき、または `BUILD_FORGE=1` のとき `src/forge` もコンパイルし、`mods.toml` の `${version}` を置換して同梱する (無ければ Fabric 専用)。(5) リソース: `fabric.mod.json` の `$version` を置換、`LICENSE_*` は**あるときだけ**コピー。(6) 削除: `libs/include` の jar-in-jar (P-51 で Fabric API は使わない)、`--main-class`、`-sources.jar` (P-54 で配布物に開発用のものを入れない)、存在しない `stubs/`。
- README の「`build.sh` / Eclipse には Forge を含めていません」を、実態 (build.sh も Forge は任意で対応) に合わせて直した。`check_rules.py` の P-92 (build.sh が `src/compat` を `--release 8` でビルドしている) は通る。
- 確認できたこと: `sh -n build.sh` (構文)、疑似の `libraries/` (`gson 2.8.9` と `2.11.0`、`META-INF` 内の jar) に対して、最新の版だけが選ばれて `META-INF` が除かれること、`$version` / `${version}` の置換、`check_rules.py` 違反 0。
- **未確認 (要・実機)**: この環境には `javac` / `jar` が無く、`build.sh` は**ビルドとして一度も実行していない**。① 入れ子の jar の取り出し (`jar --extract`。パスに空白がある macOS の `Application Support` を想定した書き方にしてあるが未実行) ② Forge あり/なしの両方のビルド ③ `sort -V` が無い環境 (古い macOS) では最新の版の選択が動かない可能性 ④ `NeoMods.jar` が `build.ps1` の出力と同じ中身になること。

## `REFACTOR_NOTES.md` の整理 (ユーザー指示)
- 古い節 (「縮退起動を新しい版へ移植」より前の 572 行) を、冒頭の「経緯の要約」に置き換えた。原文は `REFACTOR_NOTES_archive.md` に**そのまま**移した (更新しない)。それ以降の節は原文のまま。
- 要約で意識したこと: ①「判断」「訂正」「要判断 (未対応)」「当時の未確認」は残す ② 確認の細部 (バイトコードの署名・検証の数値) と当時の名前は archive にだけ残す ③ 名前の移り変わりを冒頭に表で ④ 初期の「要判断」リストの現状を 1 節にまとめた。
- **注意**: 要約は私が書き直したもの。原文と食い違っていないかは、必要なら archive と見比べてほしい。「未確認」は当時の記録で、その後の実機確認の結果は反映されていない。
- 規約 (`AGENTS.md`) の「`REFACTOR_NOTES.md` に書く」(P-74) はそのまま。archive は読むだけで、書かない。

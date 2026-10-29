# リファクタリング記録 (原文・退避版)

`REFACTOR_NOTES.md` を整理したときに、古い節 (「縮退起動を新しい版へ移植」より前) の**原文をそのまま**移したものです。
`REFACTOR_NOTES.md` にはこれらの要約だけを置いています。細かい確認の内容・当時の判断・当時の名前 (`com.neo.*` / `ModMenu*` / `CheatGate` など) を知りたいときに読みます。
この文書は更新しません (新しい変更は `REFACTOR_NOTES.md` に書く)。

---

## 実施済み (挙動は変えていません)
- `com.fabric.neo` → `com.fabric.NeoMain` に改名 (クラス名の小文字始まりを解消)。`fabric.mod.json` の entrypoints も更新。
- `NeoCore.init`: Swing ポップアップを削除し、ログは最新起動のみのタイムスタンプ付き上書きに変更 (挙動変更・依頼により実施)。
- `EnchantToolTipClient`: 30 行の `addLine(...)` 羅列を、表示順を保ったルール表 (`LINES`) に変更。Lure の特殊分岐は `LURE` に分離。
- `SpawnerInfoClient`: キー処理 (`handleKeys`/`toggleNearestOnly`/`notifyToggle`)、描画 (`emitRangeSpheres`/`emitLabels`/`buildLabelLines`)、対象選別 (`selectEntriesToRender`) に分割。
  緯線・経線の円描画は `emitClosedCircle` に共通化。マジックナンバーを定数化。`Entry<Integer, SpawnerInfoClient.SpawnerData>` の冗長な修飾を除去。
- Mod Menu: タイトル画面・ポーズ画面で重複していたアイコンボタン生成を `SmallModMenuButtonWidget.createModsIconButton` に集約。
  `ModMenuEventHandler#afterTitleScreenInit` で `MODS_BUTTON_STYLE` を1回だけ読むよう整理。
- **Mod Menu の循環依存を解消**: 24 クラスの巨大な循環を、下記の一方向の層構造に再編 (機能は同じ)。残る循環は `gui.list` 内の 6 クラスのみ (一覧とその行は相互参照が本質的なため同一パッケージに閉じ込めた)。
  ```
  util(ModMenuInfo / ModMenuPlatform …) ← mod(Mod / ModRegistry …) ← config ← service(ModTexts / ModSearch / ModCount …)
        ← gui(ModsScreen / 一覧 / ボタン / 描画) ← menu(ModMenuEventHandler / Compat) / mixin / api ← fabric(ModMenuFabric) 
  ```
  - `ModMenu` (どこからも参照されていた神クラス) を分解: 定数 → `ModMenuInfo`、登録簿 → `ModRegistry`、列挙 → `FabricModDiscovery`、設定画面登録 → `ConfigScreens`、Mod 数 → `ModCount`、ボタン文言 → `ModsButtonText`。`ModMenu` は初期化手順だけ。
  - `Mod` モデルから設定依存を除去: 翻訳/要約/説明は `ModTexts`、並び順は `ModOrdering`、非表示判定は `ModMenuConfig.isHidden(mod)` へ (`Mod` → `config` の逆流を解消)。
  - `ModsScreen` ⇄ 一覧/説明ウィジェットの相互参照を `ModsScreenHost` インターフェースで解消。`ModSearch` は画面ではなく `Predicate<String>` を受け取る。`ModBadgeRenderer` の未使用引数 `screen` を削除。
  - 設定保存→Mod 数キャッシュ破棄は、`ModMenuConfigManager.addSaveListener` による逆向きの通知に変更 (config が上位層を参照しない)。
  - `ConfigScreens` は API 型 (`ConfigScreenFactory`) ではなく `Function<Screen, Screen>` を保持。API 型との変換は `ModMenu` で行う。
  - パッケージ移動: `util.mod(.fabric)` → `mod`、`util.DrawingUtil` → `gui.draw`、`gui.widget.{ModListWidget,entries}` → `gui.list`、ボタン → `gui.button`、`ModSearch` → `service`。`NullScreenFactory` は API パッケージへ。
  - 削除: 未使用の `GITHUB_REF` / `DEV_ENVIRONMENT` / `GSON_MINIFIED`。`ModMenuApi` の公開 static (`createModsScreen` / `createModsButtonText`) は互換のため残した。
  - `src/neo` ↔ `src/main` の振り分けは依存解析でやり直し済み (neo → main への依存は 0 件)。
- **ローダー依存を Fabric アダプタに隔離** (`src/neo` = 本体 / `src/main` = Fabric 接着部分):
  - 抽象を追加: `ModMenuPlatform` (config/mods フォルダ・Mod 読み込み判定・Mod 有無)、`IconSource` (アイコン読み出し元)、`ModContact` (連絡先)。`Mod` モデルと GUI から `net.fabricmc` の import をなくした。
  - Fabric 実装を `com.neo.fabric` に集約 (後に統合): `FabricPlatform` / `ModContainerIconSource` / `FabricMod` / `FabricDummyParentMod` / `FabricModDiscovery` / `CustomValueUtil` / `ModMenuFabric` (旧 `ModMenu`)。
  - `FabricIconHandler` → loader 非依存の `IconHandler` に改名 (読み出し元は `IconSource`)。`DrawingUtil` の不要な `@Environment` を削除。`ModsScreen` の未使用コード (`getModsFolder` ほか) を削除。
  - `com.neo.util.ScreenWidgets` → `com.neo.mixin.ScreenAccessor` の逆向き依存を解消: `ScreenAccessor` を廃止し、util の `ScreenInternals` を `MixinScreen` が実装する形に変更 (mixin → util の向き)。
  - 分割パッケージを解消: 全パッケージが neo か main のどちらか一方にだけ存在 (neo 57+スタブ6 / main 21)。
- **パッケージの階層を浅く統合** (最大 `com.neo.menu.<名前>` = 4 階層):
  `config.option`→`config`、`core`→`util`、`gui.{button,draw,list,widget}`→`gui`、`event`→`menu`、`spawnerinfo.client`→`spawnerinfo`、
  `menu.mixin`→`mixin`、`com.fabric` / `com.neo` (NeoCore) / `menu.fabric`→`com.neo.fabric`。クラス名の衝突なし。
  Mixin 設定は `mixins.modmenu.json` を `mixins.neomod.json` に統合 (fabric.mod.json の `mixins` と entrypoints も更新)。
  外部互換で名前が固定のもの (`com.terraformersmc.modmenu.api`、`eu.pb4…`、`org.jetbrains…`、`org.jspecify…`) は階層を変えていない。
- **Mod Menu 本体の Forge 対応**:
  - neo に loader 非依存の共通部分を追加: `ModMenuBootstrap` (初期化手順)、`ModHierarchy` (親子関係の組み立て。ダミー親の生成だけ loader 側から渡す)、`ModMenuPlatform` に `loadIntegrations` / `discoverMods` を追加、`NeoKeyMappings.registered()`。
  - Fabric 側はこれを使う薄い実装に (`ModMenuFabric` は `ModMenuBootstrap.init(new FabricPlatform())` だけ)。
  - Forge 側を `src/forge` に新設 (`NeoForgeMod` / `ForgeClientInit` / `ForgePlatform` / `ForgeMod` / `ForgeIconSource` + `mods.toml`)。通常ビルドには含めない。
  - 確認できたこと: Forge 26.3 ブランチの存在、`ModList` / `FMLPaths` / `FMLEnvironment` / `IModInfo` / `IModFileInfo` / `IModFile` / `IConfigurable` / `ConfigScreenHandler` / `RegisterKeyMappingsEvent.BUS` / `TickEvent.ClientTickEvent.Post.BUS` / `@Mod` + `FMLJavaModLoadingContext` の署名 (ソースで確認)。
  - **未確認**: EventBus の `addListener` のオーバーロード (イベントバス jar のソースは未取得)、`RegisterKeyMappingsEvent` が Mod 構築後に発火するか、他 Mod の設定画面拡張ポイントの登録タイミング、コンパイル・実機動作。
- **進捗画面の拡大** (`com.neo.advancements` + `MixinAdvancement{sScreen,Tab,TabType,Widget}`):
  バニラは枠 252x140・表示域 234x113 を数値のまま埋め込んでいる。有効時は窓を画面 (GUI スケール後) の **85%** まで広げる (等倍のまま表示域を広げる方式。ズームはなし)。
  - 窓サイズ = `max(バニラ, min(画面×0.85, 画面 − 2×33))`。33 は上のタブ (最大 32) と下の「完了」ボタン (フッター 33) を隠さないための余白。小さい画面では自動的にバニラと同じになる。
  - 切り替え: Mod Menu の設定 `large_advancements` (既定 ON、オプション画面に表示、`config/modmenu.json` に保存)。OFF のときは全 Mixin が元の値をそのまま返す。
  - 実装: ① `@ModifyConstant` で 252/140/234/113/117/56/248/136 と背景タイルの列数 15・行数 8 を差し替え (個数は `require` で固定 → Minecraft 更新で変わると起動時に失敗して気付ける)、② 枠は `window.png` を 4 隅等倍・4 辺引き伸ばしで描画 (`AdvancementsWindow`)、中央は元から透明。
  - 確認できたこと: 26.3 の該当クラスを逆コンパイルして、各メソッド内の定数の個数をバイトコードで数えて `require` と一致、`window.png` の中央が透明・辺が一様、`blit` の 12 引数版 (引き伸ばし) の存在、`HeaderAndFooterLayout` の既定高さ 33。
  - **未確認 (要・実機)**: コンパイル、見た目 (枠の辺の引き伸ばし、タブ位置、上部の「進捗」見出しとの重なり)、ホバー説明の位置、スクロールの端、設定 ON/OFF の切り替え。
- **機能の ON/OFF を Mod Menu の設定へ集約**: `ModMenuConfig` に `enchant_tooltip` / `spawner_info` / `spawner_glow` / `spawner_range` / `spawner_overlay` (すべて既定 OFF) と、`large_advancements` / `skip_command_confirmation` (既定 ON) を追加 (表は README)。既定 ON は後者の 2 つだけ。
  - ツールチップ: OFF なら `appendLines` が何もしない。スポナー情報: OFF なら受信ペイロードを捨て、発光・文字表示・範囲球を描かない。コマンド確認の抑止: `@Redirect` を `@WrapOperation` に変え、OFF のときは元の確認画面を出す。
  - キーバインド: スポナーの 3 つ (発光・範囲球・全表示) を廃止して設定に移行。「最寄りのみ表示」だけは操作が即時の選択なのでキーを残した。Mod Menu を開くキーも残している。lang の該当キー名も削除。
  - 設定は `config/modmenu.json` に保存される (既存ファイルに無いキーは既定値)。options 画面は ModMenuConfig の項目を自動列挙するので UI の追加コードは不要。
  - 設計メモ: 機能側 (`spawnerinfo` / `tooltip` / `advancements`) が `menu.config` に依存する形になった。設定基盤が `menu.config` にあるため。気になる場合は設定基盤を `com.neo.config` に切り出す案がある。
  - サーバー側のスポナー情報送信 (`SpawnerInfoMod.onServerTick`) は設定に依存しない (専用サーバーにはクライアント設定がなく、クライアント専用クラスを触れないため)。クライアント側で受信を捨てる。
- **OFF 時の負荷を監査して修正**:
  - 修正: ① 毎フレーム、OFF でも `collectPerFrameMainThreadGizmos()` の収集オブジェクトを作っていた → OFF なら作らず即 return。② サーバー側の走査・NBT 保存・送信が設定に関係なく動いていた → シングル/LAN ホストでは OFF の間すべて省く (`SpawnerInfoMod.setSendEnabled`、クライアント tick で設定に追従する volatile 値)。③ OFF にしても受信データと「最寄りのみ」状態が残っていた → 全体スイッチ OFF で破棄。
  - 挙動の整理: 範囲球が「文字表示 (全表示)」に従属していた (全表示 OFF だと範囲球も出ない) → 独立に変更。設定画面の項目名も「全表示」→「文字表示」。
  - 問題なしと確認: ツールチップ (設定参照が最初で、OFF なら解析前に return) / `NeoClientEvents` の発火 (CopyOnWriteArrayList の走査のみ・割り当てなし) / 各 Mixin のフック (instanceof 判定や設定参照のみ) / 進捗 Mixin (進捗画面を開いている間だけ) / 設定参照 (`HashMap<String, Boolean>` の get 1 回)。
  - 残る負荷: 専用サーバーでのスポナー情報送信 (クライアント設定が届かない)。解消するにはクライアント→サーバーの購読通知 (serverbound ペイロードの登録が必要で、コンパイル確認なしでは危険) が必要。
- **ワールドマップ (M キー)** (`com.neo.map`、設定 `world_map` 既定 ON): **バニラの地図データ・更新処理・保存方式・縮尺をそのまま使う**。詳細は README。
  - 構成: `MapStore` (バニラの `SavedDataStorage` + (次元, スケール, 升目)→ID の索引 + ID 採番) / `MapUpdater` (バニラの `MapItem#update` を全スケールの周囲の升目に 1 tick 4 枚ずつ実行) / `MapPins` (NBT) / `WorldMapScreen` (UI) / `WorldMapClient` (キー・tick・ワールド切替・テレポート)。すべて loader 非依存で `src/neo`、Mixin 不要。
  - 経緯: 初版は独自のタイル形式・半径 8 チャンクの自前走査・自前の色計算 → 「保存方式と走査範囲がバニラに従っていない」との指摘で、バニラの地図データ方式に作り直し → さらに「ズームもバニラの最小〜最大マップの範囲、未ロードはグレー、プレイヤー中心で Google マップのような操作感、表示範囲は最大マップ中心から 1 マップ幅まで」との指摘で、全スケール (0〜4) の地図・追従・慣性・範囲制限を追加。
  - 判断したこと: ① 「表示可能な最大距離は自分のいる最大マップを中心として幅をもう一マップほどまで」は、「表示の中心を、自分のいるスケール 4 の地図の中心から ±2048 ブロック (地図 1 枚分) に制限」と解釈 (定数 `PAN_LIMIT`)。② 「グレー」は未探索ピクセルが透明な場所に灰色 (`UNEXPLORED_COLOR`、バニラの地図背景の茶色ではない) を敷く。③ 縮尺は連続ではなく 5 段階にスナップ (バニラの地図の縮尺そのもの)。④ スケールごとに別の地図を更新するため、全スケールの更新で 1 tick 4 枚 (バニラが地図を数枚持つのと同程度)。⑤ 保存先は当初 `neomod/map/<ワールド>/data` (ワールドと別) だったが、「バニラのデータはバニラの階層に、Mod 固有のデータは `data/neo/` に」の規約に合わせて、シングルでは実際のワールドの `data` フォルダへ変更 (下の「保存の共通規約」)。
  - 確認できたこと: 使った Minecraft API を 26.3 の jar で署名まで確認 (`SavedDataStorage`、`MapItemSavedData.type/createFresh/MAX_SCALE/scale/colors`、`MapIndex`、`MapItem#update` が public でクライアントの `Level` からも呼べる、`Mth.clamp`、`Font.width(FormattedText)` ほか)。括弧・import・未解決メソッドの機械チェック済み。
  - **未確認 (要・実機)**: コンパイル、見た目、更新の負荷 (1 tick 4 枚)、ズームのなめらかさと慣性の体感、`SavedDataStorage` をクライアントで独自に使うこと、2 段階テレポートの挙動、マルチのワールド識別、スケール 4 の地図が大きく探索されたときの描画 (visible タイル数)。
  - **保存の共通規約** (`com.neo.data.NeoData`、README 参照): バニラのデータはバニラの階層・形式のまま (`<ワールド>/data/minecraft/maps/<id>.dat`)、この Mod 固有のデータ (索引 `neo/map/index.dat`・ピン `neo/map/pins.dat`) は `data/neo/` の下。形式は `{DataVersion, data}` の gzip NBT。ピンは JSON から NBT に変更 (規約に合わせるため。旧 `pins.json` / `neomod/map/` の読み込み・移行はしていない)。マルチはワールドフォルダが無いので `gameDir/neo/<サーバー>/data/` に同じ構造。
  - **ID の採番を変更**: バニラの `MapIndex` (`last_id.dat`) から取るのをやめ、索引のカウンタで `1000000` 以上を採番。理由: シングルでは本物の `data` フォルダにサーバーが同じ `last_id` をキャッシュして持っており、こちらが先に進めるとサーバーが次に作る地図が同じ ID になって**プレイヤーの地図を上書きする**ため。重ならない範囲を使えば、バニラの地図アイテムのデータには一切触れない。
  - **UI を分かりやすく**: ディメンション名、北 (N)、縮尺バー、ピン一覧 (クリックで移動) を追加。
  - **未確認 (追加)**: シングルで、サーバーが動いている間に同じ `data` フォルダへ書くこと (別ファイルなので競合しないはずだが実機で確認が要る)、`getWorldPath(LevelResource.DATA)` が実際の `<ワールド>/data` を指すこと (ソースでは `MinecraftServer` が `SavedDataStorage` に同じ値を渡している)。
- **実際のビルドで見つかったエラーを修正** (初めてのコンパイル結果: Windows の `build.bat` + JDK 25 + Fabric Loader 0.19.5 / Mixin 0.17.4 / MixinExtras 0.5.5 / Gson 2.14.0 / Guava 33.6.0): 4 件。
  ① `DescriptionListWidget` の 3 箇所 — ウィジェットの親を `ModsScreen` から `ModsScreenHost` に置き換えた際、クレジット画面の `super(parent)` と「戻る」の `setScreen(parent)` が `Screen` を要求していた → `ModsScreenHost#asScreen()` を追加して解決。
  ② `ModsScreen` の `new ModBadgeRenderer(..., this)` — `ModBadgeRenderer` から未使用引数 `screen` を削除したときの呼び出し側の直し漏れ → 引数を削除。
  以降は、ローカルに JDK 25 を入れ、Minecraft 26.3 の jar + Gson / Guava / slf4j / commons-lang3 / fastutil で自分のコードをコンパイルして確認している (Fabric Loader・Mixin・MixinExtras・Mojang の DFU / Brigadier 等はこの環境に無いため、それらに依存する箇所のエラーは除外)。このチェックでは上記 2 件は解消、他に型エラーは出ていない。
- **ズーム (C キー)** (`com.neo.zoom`、設定 `zoom` 既定 ON): アップロードされた WI Zoom 1.5 (MC 1.20.4) を逆コンパイルして動作を確認し、同じ動作を 26.3 向けに独自実装 (コードは取り込まない: WI Zoom は GPL-3.0-or-later)。詳細は README。
  - 構成: `ZoomState` (倍率・キー・設定の判定。loader 非依存で `src/neo`) + Mixin 2 つ (`MixinCameraZoom`: `Camera#calculateFov(F)F` の戻り値に `@ModifyReturnValue` / `MixinMouseHandlerZoom`: `turnPlayer` の感度取得 `OptionInstance#get` (ordinal 0) に `@WrapOperation`、`onScroll` の HEAD でホイールを倍率に使って `cancel`)。キーのカテゴリは `NeoKeyMappings.MAIN` に共通化 (ワールドマップと共用)。
  - 26.3 側で確認したこと: FOV の計算が `GameRenderer#getFov` ではなく `Camera#calculateFov(F)F` になっていること、`turnPlayer` の最初の `OptionInstance#get` が感度で、直後に `Double#doubleValue` が続くこと (バイトコードで確認)、`onScroll(JDD)V` の構造 (画面なし・プレイヤーありのときにホットバー/スペクテイターの処理)、`InputConstants.KEY_C`、`KeyMapping#isDown`。自分のコードを JDK 25 でコンパイルして型エラーなし (Mixin 本体・Fabric の型が無いことによる除外分を除く)。
  - **未確認 (要・実機)**: ズームの見た目 (感度の下がり方は WI Zoom と同じ式)、C キーが他の Mod / バニラのキーと衝突しないか、`@ModifyReturnValue` / `@WrapOperation` の起動時の適用 (`require = 1` で失敗すれば起動時に分かる)。
- **F5 の自由視点** (`com.neo.freelook`、設定 `free_look` 既定 **OFF** / `free_look_hold` 既定 OFF): アップロードされた Perspective Mod 3.0 (1.8.9) を逆コンパイルして動作を確認し、26.3 向けに独自実装。詳細は README。
  - 参考 Mod の仕組み: キーで `perspectiveToggled` を切り替え、押した時点のプレイヤーの向きを `cameraYaw/Pitch` に保存、三人称 (背面) に切り替え、マウスの動きをプレイヤーではなく `cameraYaw/Pitch` に加え (感度の式 `(s*0.6+0.2)^3*8` ×0.15)、描画側 (`EntityRenderer#orientCamera`) だけ ASM でプレイヤーの向きの読み取りを置き換える。`hold` なら離したら戻る。
  - 26.3 での対応: ① F5: `Minecraft#handleKeybinds` の最初の `KeyMapping#consumeClick` (ordinal 0) を `@WrapOperation` で肩代わり ② マウス: `MouseHandler#turnPlayer` の `LocalPlayer#turn(DD)V` を `@WrapOperation` でカメラだけ回す処理に置き換え (`Entity#turn` と同じ ×0.15・±90° クランプ) ③ カメラ: `Camera#alignWithEntity(F)V` の `Entity#getViewYRot` / `getViewXRot` (各 2 回、トロッコ搭乗用と通常用) を `@WrapOperation` (`require = 2`)。
  - 確認できたこと: 上の 3 つの呼び出しの所有クラス・記述子・ordinal をバイトコードで確認 (consumeClick の 0 番目が `keyTogglePerspective`、`turn` は `LocalPlayer` の `(DD)V` が 1 箇所、`getViewYRot/XRot` が各 2 箇所)。使った API (`Options#setCameraType`、`Minecraft#getCameraEntity`、`GameRenderer#checkEntityPostEffect` など) を jar で確認。自分のコードを JDK 25 でコンパイルして型エラーなし (Mixin 本体・Fabric の型が無いことによる除外分を除く)。
  - **未確認 (要・実機)**: 見た目と操作感、自由視点中にプレイヤーが「押す前の視点」へ戻るタイミング、トロッコ搭乗中・スペクテイター・死亡中の挙動、`require` の起動時の適用。
- **エージェント向けの規約を追加** (`AGENTS.md` + `CLAUDE.md` + `tools/check_rules.py`): これまでの指示・判断を、ID 付き (P-01〜P-91) の規約にまとめた — 前提 / ソース構成 / データの保存 (共通規約) / バニラ順守 / 機能と設定 (既定値の表) / サードパーティ / Mixin / 変更の進め方 / 安全 / エージェントの運用 + 変更前チェックリスト。
  - **機械で検査できるもの** (`tools/check_rules.py`、標準ライブラリのみ): neo→main の参照・ローダー型の import (P-11) / 分割パッケージ (P-12) / 階層の深さ (P-13) / パッケージ循環 (P-14) / Mixin の配置と一覧の一致 (P-16) / `NeoData` を通さないファイル書き込み (P-26) / 設定の既定値と翻訳 (P-40, P-41, P-04) / 許可外のライブラリ・UI ツールキット (P-50, P-52) / Mixin の `require` 明示と `@Overwrite` / `@Redirect` 禁止 (P-60, P-61)。意図的に違反を入れて検出されること・現状で違反 0 であることを確認済み。
  - 検査で見つかって直したもの: 自分で書いた Mixin の注入 (8 クラス・11 か所) に `require = 1` が無かった → 明示 (`defaultRequire: 1` が既に効いていたので挙動は同じ)。
  - 規約として明文化した判断: 既定 ON は 4 つだけ (P-41) / キーは最小限 (P-43) / バニラの採番 (`last_id.dat`) に触らない (P-23) / 参考にした他の Mod のコードは取り込まない (P-44) / 設計判断はユーザーに選択肢を出して聞く (P-72) / 実装は Sonnet・単純作業は Haiku (P-90) / 未確認のことを分けて報告 (P-71, P-75)。
  - 例外として許可: `ModsScreen` の「ドロップした Mod の jar を mods フォルダへコピー」(Mod Menu 本来の機能、データの保存ではない) / `mods/NeoModLog.txt` (起動ログ)。
- **ワールドマップを「バニラの手持ち地図」の見た目に作り直し**: 調査・質問 (両腕まで描くか / 中央の地図の動かし方 / 目印) に対する回答 (「両腕も描く。できればバニラの処理をできるだけ流用」「これまでどおり自由に動かす」「ピンも改良、バニラのロケーションと旗のアイコンを使う、ピンは保存しない」) に基づく。
  - 方式: 画面に独自の絵を描くのをやめ、**バニラの両手持ちの地図の描画をそのまま使う**。表示範囲を 1 枚のバニラの地図データ (128x128) に詰めて `ClientLevel#overrideMapData` + `MapTextureManager#update` で登録 (`ClientPacketListener#handleMapItemData` と同じ手順) → Mixin でメインハンドを「その地図」・オフハンドを空にする (`FirstPersonHandsAndItems#tick` の `getMainHandItem` / `getOffhandItem`)。バニラが両腕・紙の枠・チェッカー下地・装飾 (アイコン + 名前) を描く。傾きは `calculateMapTilt` を 0 にして固定。画面側は背景を描かず (透明)、左右の部品と入力だけを持つ。
  - 地図上の位置: 傾き 0・手を上げきった状態でのバニラの変換 (`translate(0, 0.04, -0.72)` → `scale(2)` → `renderMap` の `scale(0.38)` ほか) を辿って、地図の画面上の長方形を計算 (`HandMapGeometry`)。手の視野角は `Camera#calculateHudFov` の固定 70°。
  - 装飾: `MapItemSavedData#createForClient` + `addClientSideDecorations` (どちらもバニラのクライアントの公開 API)。位置は 128x128 の中の半ピクセル単位 (バニラと同じ計算)。自分の矢印は窓の外では縁の「地図の外」アイコン。本物の旗は、重なる地図の装飾から座標を直して加える。
  - ピン: `PinIcons` = バニラの装飾 36 種 (マーカー 5・構造物 15・旗 16)。保存はやめ、メモリ上だけ (`data/neo/map/pins.dat` は廃止)。
  - 変更・廃止: 自前のテクスチャ描画 (`TileTexture` 等)・ズームのなめらかなアニメーション・ピンの色・ピンの保存。ズームは段階切り替え (バニラの地図は縮尺固定のため)、動かせるのも 1 ピクセル単位。
  - 確認できたこと: 26.3 の `FirstPersonHandsAndItems` / `FirstPersonHandsAndItemsRenderer` / `GameRenderer#renderItemInHand` / `MapRenderer` / `MapTextureManager` / `MapItemSavedData` / `MapDecoration(Types)` を読み、`tick` の `getMainHandItem` / `getOffhandItem` が各 1 回 (ordinal 0)、`calculateMapTilt(F)F` が private、手の描画に画面を開いているかの条件が無いこと (一人称・非スペクテイター・HUD 非表示でない・非睡眠のみ) を確認。JDK 25 で `com.neo.map` をコンパイルして型エラーなし。`tools/check_rules.py` 違反 0。
  - **未確認 (要・実機)**: 見た目全般 (地図の大きさ・位置、両腕の見え方)、地図上のクリック位置が合っているか (`HandMapGeometry` の計算)、持ち替えの動きの最中のずれ、視点を一人称にして戻す動き、`require` の起動時の適用、旗・ピン・矢印の装飾の位置。
- **手持ち地図の中身を 512×512 に拡大** (「画像部分はバニラでなくてよいが、128px だと 1 ブロック 1 ドットの範囲が狭すぎる」との指摘): バニラの地図データは 128×128 固定。バニラの描画 (`MapRenderer#render`) は `MapRenderState#texture` の ID のテクスチャを四角に貼るだけなので、`MapRenderer#extractRenderState` の TAIL (`MixinMapRenderer`) で、この地図の画像だけを自前の 512×512 の `DynamicTexture` に差し替えた。紙の枠・両腕・装飾はバニラのまま。
  - 効果 (GUI スケール 2 / 1080p の場合): 手持ち地図は画面の高さの約 75% (GUI 407px = 画面 814px)。中身 512px → 1 ピクセル ≒ 画面 1.6 ドット。スケール 0 で見える範囲は **512 ブロック四方 (バニラの 4 倍の幅)**、1 ブロック ≒ 1.6 ドット。スケール 1〜4 は 1024 / 2048 / 4096 / 8192 ブロック四方。
  - 装飾 (自分の矢印・ピン・旗): 位置はバニラの 128 の座標系の半ピクセル単位 (byte)。中身が 512 でも座標系は変わらず、`HeldMapView#fromCenter` で変換 (1 単位 = 中身の 2 ピクセル)。アイコンの大きさはバニラと同じ (地図の幅の 1/16)。
  - 描き直し: 窓の色が前フレームと違うときだけ、262144 ピクセルを書き換えてアップロード (動かしている間は毎フレーム)。ディスクからの地図の読み込みは 1 フレーム 4 枚まで。画面を閉じるとテクスチャを解放。
  - 規約との関係: P-30 (バニラの仕組みを使う) の例外。ユーザーが「中身の画像はバニラでなくてよい」と明示したため。それ以外 (枠・腕・装飾・手持ちの動き・データ形式) はバニラのまま。
  - 確認できたこと: `MapRenderer#extractRenderState(MapId, MapItemSavedData, MapRenderState)V` のシグネチャ、`MapRenderState#texture` が public、`TextureManager#register(Identifier, AbstractTexture)` / `release`、`NativeImage#setPixel(III)V`。JDK 25 でコンパイルして型エラーなし、`tools/check_rules.py` 違反 0。
  - **未確認 (要・実機)**: 見た目 (512 の画像が紙の枠に正しく収まるか、ドットの粗さ)、動かしている間の負荷 (毎フレーム 262144 ピクセル)、装飾の位置。重いときは `RESOLUTION` を 256 に、細かくしたいときは 1024 にする (1024 は 4 倍重い)。
- **中身の画像の書き直しを間引き** (「パフォーマンスを考慮して書き直し頻度を大きく落としてよい」): 以前は毎フレーム、窓の再構成 (26 万ピクセル) + 前回との全比較をしていた。今は `HeldMapView#shouldRecompose` で判定してから動く: ① 開いた直後・スケール変更 = 即時 (開くときはディスクから読む地図の上限を 4 → 36) ② 窓が動いている = 0.15 秒に 1 回まで (止まれば次のフレームで必ず追いつく) ③ 窓が同じ = 1 秒に 1 回だけ「中身が変わったか」を `contentStamp` (窓に重なる地図の版 `MapRef#revision` の合計) で調べ、変わっていなければ何もしない。画像の全比較・書き込み・アップロードは、①〜③が真のときだけ。矢印・ピン・旗の装飾は軽いので毎フレーム更新するが、基準は「いま画面に出ている窓」(`displayedView`) で、画面の部品・クリック位置・ピンの当たり判定も同じ窓を基準にする (描き直しが遅れてもずれない)。
  - 効果: 動かしていない間の重い処理はほぼゼロ (1 秒に 1 回、最大 25 枚程度の版の合計を取るだけ)。動かしている間は 60 回/秒 → 約 7 回/秒 (約 1/9)。
  - 副作用: 動かしている間、地図の絵が約 0.15 秒刻みで更新される (滑らかさは落ちる)。`VIEW_REFRESH_NANOS` / `CONTENT_REFRESH_NANOS` で調整できる。
- **訂正**: 以前、「バニラはインベントリの地図すべてを毎 tick 更新する」と書いたが誤り。`MapItem#inventoryTick` は**手 (メインハンド・オフハンド) に持っている、ロックされていない地図だけ**を更新する (最大 2 枚/tick)。コメントと README を訂正。当方の 1 tick 4 枚はバニラの最大の 2 倍なので、負荷を下げたいときは `MapUpdater#UPDATES_PER_TICK` を減らす。
- **構造物・旗の自動表示と、誤りの訂正**: 「バニラの森の洋館などのアイコンがある重要な POI やストラクチャーを地図内に表示できているか？」への回答と実装。
  - **訂正 (誤り 2 件)**: ① 構造物は、手動で置いたピン以外は出ていなかった。② 以前 README に「本物の旗は地図のデータから自動で出る」と書いたが誤り。バニラの旗の目印は、地図を持って旗を右クリックしたとき (`MapItemSavedData#toggleBanner`) にだけ追加され、自動の更新 (`checkBanners`) は既にある目印が消えていないか確かめるだけ。この Mod には右クリックする地図アイテムが無いので、旗は出ていなかった。
  - **回答 (ユーザーの選択)**: 構造物 = **シングルのみ自動** (マルチは手動ピン) / 「発見済み」= **構造物の近くに来たとき** / 旗 = **自動で出す** (ロード済みチャンクのバナーを位置を記憶して表示)。
  - 構造物 (`MapMarkers`): 統合サーバーのスレッドで (`MinecraftServer#execute`)、プレイヤーの周り (半径 98 ブロック) のロード済みチャンクの `ChunkAccess#getAllStarts` を読み、`StructureIcons` でバニラの構造物 ID → アイコンに対応付け、構造物の範囲 (水平) に 96 ブロックまで近づいたものを発見済みにする (40 tick ごと)。位置はバニラの探検家の地図と同じ「開始チャンクの中心」。アイコンがない構造物 (要塞・ネザー要塞・エンドシティ・難破船など) は対象外。**廃坑・廃墟のキャンプは数が多いので画面が賑やかになる** (気になるなら `StructureIcons` から外す)。
  - 旗: クライアントのロード済みチャンクを、1 tick 2 チャンクずつ (近い 200 tick・遠い 2400 tick ごとに取り直し)、`BannerBlockEntity` → `MapBanner#fromWorld` (バニラと同じ判定) で拾う。上限 4096。消えた旗は、チャンクが読み込まれていれば消す。
  - 保存: `data/neo/map/markers.dat` (NeoData、保存の共通規約)。
  - 設定: `map_structures` / `map_banners` (どちらも既定 ON。「自動で出す」とのご指示に合わせた。AGENTS.md の P-41 と `tools/check_rules.py` の既定値表を更新)。OFF のときは走査も保存もせず、tick では設定を引いて戻るだけ。
  - **不具合を 1 件修正**: 手動ピンのアイコンが**上下逆**に描かれていた。バニラの地図の装飾は、画像が回転 0 だと逆さまに出る (バニラは旗・目的地に 180° = 回転 8 を使う)。ピンも回転 8 に統一 (`MapMarkers.UPRIGHT`)。
  - 確認できたこと: `ChunkAccess#getAllStarts`、`StructureStart#isValid/getBoundingBox/getChunkPos`、`ServerChunkCache#getChunkNow`、`MinecraftServer#execute`、`MapBanner#fromWorld`・コンストラクタ・`getDecoration`、`ChunkPos#getMiddleBlockX/Z`、バニラの構造物 ID (`mansion`・`monument`・`jungle_pyramid` など) を `BuiltinStructures` の文字列で確認。JDK 25 でコンパイルして型エラーなし、`tools/check_rules.py` 違反 0。
  - **未確認 (要・実機)**: 構造物が実際に出るか (発見の距離・アイコンの位置)、旗が出るか、アイコンの向き (回転 8)、チャンク境界をまたぐ大きな構造物 (村) の位置、サーバーのスレッドでの読み取りの安全性。
- **ビルドエラー (`ChunkPos.asLong`) の修正と、検証方法の見直し**: ユーザーの `build.bat` で `MapMarkers` の `ChunkPos.asLong(int,int)` が 26.3 に無いというエラー (2 件)。26.3 の `ChunkPos` は `pack(int,int)` / `getX(long)` / `getZ(long)` → `pack` に直した (`getX/getZ` は元から存在)。
  - **見逃した原因**: 手元のコンパイル確認で「cannot find symbol」を「Fabric / Mixin / Mojang のライブラリが無いため」として一括で除外していた。そのせいで、Minecraft の jar にあるはずのメソッドが無いという本物のエラーまで除外していた。
  - **改善**: Mixin・MixinExtras・Fabric Loader・Mojang の DFU・Brigadier のソースを GitHub から取得して `-sourcepath` に渡し、**実際の API に対してプロジェクト全体を完全にコンパイル**するようにした。結果: プロジェクトのコードのエラーは **LWJGL の SDL (`ParentEntry` の `org.lwjgl.sdl.SDLScancode`) の 2 件だけ** (ユーザーの環境には実在し、ここに無いだけ。Mod Menu 由来のコード)。それ以外は、これまで除外していた分も含めてすべて型が合うことを確認。AGENTS.md の P-71 を、この方法と「`cannot find symbol` を除外しない」ことに更新。
  - 限界: `javac -proc:none` は Mixin の注入先 (メソッド名・記述子・ordinal) を検証しない → それはバイトコードで確認 (P-62)。
- 未使用 import を 9 件削除 (全ソース機械チェック済み)。
- README のパッケージ名・スタブ配置の記述を実態に合わせて修正。

- **ソースルート分割**: `src/neo/java` を `src/main` と並べて新設し、Fabric/Forge/Mixin に依存せず依存先も loader 非依存のクラス 26 本を移動 (パッケージ名は不変)。
  `src/main/java` には `net.fabricmc` / Mixin / MixinExtras を使うクラスと、それに依存するクラス 43 本が残る。`src/neo` → `src/main` の依存は 0 件。
  `build.ps1` / `build.sh` / `.classpath` / `setup-eclipse.ps1` を2ルート対応に更新。
  注意: `com.terraformersmc.modmenu.api` は `ConfigScreenFactory` が neo 側、`ModMenuApi` が main 側 (ModMenu に依存するため) に分かれる。

- **パッケージを `com.neo.*` から `neo.*` に変更**: 自分のコードはすべて直下の `neo` に移した (`neo.menu.*` / `neo.fabric` / `neo.mixin` / `neo.forge` など。クラス名・中身は変更なし)。`fabric.mod.json` のエントリポイントと `mixins.neomod.json` の `package` も更新。階層の上限は 4 → 3 (`neo.menu.<名前>` まで)。**変えていないもの**: `com.terraformersmc.modmenu.api` (`ModMenuApi` / `ConfigScreenFactory` / `NullScreenFactory`)。他の Mod が `"modmenu"` エントリポイントでこの名前の型を実装するため、`FabricPlatform#loadIntegrations` が受け取れるよう名前を保つ (一度 `neo.menu` に統合したが、他 Mod の設定画面ボタン・モドパックバッジが取り込めなくなるため元に戻した)。ほかに、コンパイル専用スタブ `eu.pb4` / `org.jetbrains` / `org.jspecify` (他ライブラリの型の代わりで、名前そのものが外部のもの。jar には入らない)。

- **Mod の ID を `neomod` から `neo` に変更**: `fabric.mod.json` の `id`、`mods.toml` の `modId` / `[[dependencies.neo]]`、`NeoForgeMod.MOD_ID`、キーカテゴリの ID (`neo:main`) を `neo` に。それに合わせて、翻訳キーの接頭辞 `neomod.` / `key.neomod.` / `key.category.neomod.` → `neo.` / `key.neo.` / `key.category.neo.` (`en_us` / `ja_jp` の両方)、Mixin の注入ハンドラ・追加メンバの接頭辞 `neomod$…` / `neomodXxx` → `neo$…` / `neoXxx` (36 ファイル)、`mixins.neomod.json` → `mixins.neo.json`、ログ名 `neomod/skinlayers` → `neo/skinlayers` を変更。`tools/check_rules.py` も追従 (旧名前空間 `assets/neomod` を禁止する検査だけは `neomod` のまま)。**影響**: キー割り当て (ズーム C / ワールドマップ M) は `options.txt` にキー名で保存されているため、割り当てを変えていた場合は初期化される。ワールド・設定のデータ (`data/neo/`、`config/modmenu.json`) は名前を変えていないので影響なし。

## 追加機能
- **クリックイベントのコマンド確認ダイアログを抑止** (`com.neo.mixin.MixinUnattendedCommand`): 26.3 は本・チャット・ダイアログの `run_command` を `ClientPacketListener#sendUnattendedCommand` で検証し、
  「権限が必要」(OP でも毎回出る) と「解釈できないコマンド」で確認画面を挟む。この2つの確認画面を、1.20.4 以前と同じ「そのまま送信」に置き換えた。
  **署名が必要なコマンド** (`/say` `/msg` など自分の名前で発言させるもの) の確認は残した。本から勝手に自分名義で発言させられるのを防ぐ保護で、1.20.4 でもこの種のコマンドは実行を拒否していたため。
  Mixin は `openCommandSendConfirmationWindow` の呼び出し 2 箇所を `@Redirect` (`require = 2`)。Minecraft 更新で構造が変わると起動時に失敗して気付ける。

## 検証状況
この環境には JDK 25 / Minecraft 26.3 の実行環境・Fabric Loader がなく、**コンパイルも実機起動も未実施**です。
差分は小さく機械チェック (括弧の対応・import の使用有無) までです。お手元で `build.bat` を通してください。

## 要判断 (未適用。採否を教えてください)
1. ~~起動時の Swing ウィンドウ~~ → **対応済み**: Swing 処理を削除。`mods/NeoModLog.txt` は「最新の起動のみ・タイムスタンプ付き」で上書きする方式に変更 (追記しないので肥大化しない)。
2. **build.sh が実態と不一致**: 存在しない `stubs/` ディレクトリ・`LICENSE_*`・`--main-class neo` (該当クラスなし、`main` も無い) を参照。build.ps1 は正しく動く構成。 → build.sh を ps1 相当に直す / 削除
3. **スタブの置き場**: `org/jetbrains`, `org/jspecify`, `eu/pb4` が `src/neo/java` にあり、build.ps1 が名前決め打ちで jar から除外。 → `src/stubs/java` に移して別コンパイル (README の元の記述どおり)
4. ~~**パッケージ構成**~~ → **対応済み**: 自分のコードは `neo.*` に統一 (上記)。Mod Menu API 互換の `com.terraformersmc.modmenu.api` だけは外部の名前なので残してある。
5. **巨大クラスの分割**: `ModsScreen` (約 690 行)、`FabricMod` (約 400 行)、`ModListWidget`、`DescriptionListWidget`。循環は解消したが行数はそのまま。 → 次の段階として分割可 → 実機ビルド可能な状態で段階的に分割するのを推奨
6. **SpawnerInfoMod のキャッシュ**: `NAME_CACHE` が datapack リロードで更新されず、空文字も永続キャッシュされる。データパック名前空間 `"neofunction"` も決め打ち。
7. **日本語文字列の直書き**: ツールチップ・スポナー表示の文言がコード内にあり、`assets/spawnerinfo/lang` を使っていない (現在は `assets/neo/lang` に統合)。 → lang ファイル化
8. **Mixin の `required: true` + `defaultRequire: 1`**: Minecraft 更新で注入先が変わると起動時クラッシュ。 → 現状維持 or 緩和
9. **配布物**: Mojang の `minecraft-26.3.jar` (約 41MB) と mixinextras jar を zip に同梱。再配布する場合のライセンス上の扱いに注意。
- **3D スキンレイヤー** (`com.neo.skinlayers`、設定 `skin_layers` 既定 **ON** (ユーザー指示)、スイッチはこの 1 つだけ): アップロードされた 3D Skin Layers 1.11.3 (Fabric / MC 26.3) の**動作を参考**に、独自実装。
  - 調査結果: 参考 jar のライセンスは tr7zw Protective License (商用利用・金銭的利益の禁止、表示の同梱が条件)。依存は Fabric API / TRansition (Sentry による外部送信を含む → P-53 違反) / TRender (GUI ライブラリ → P-50 違反) で、**どれも使わない**。コードは取り込まない (P-44)。
  - 構成: `SkinLayerMesh` (ボクセルのメッシュ生成・描画) / `SkinLayers` (設定参照・スキンごとのキャッシュ・パーツへの割り当て) / `SkinLayerPart`・`SkinLayerModel` (Mixin が実装する口)。Mixin は 3 本: `MixinModelPartSkinLayer` (`ModelPart#render` の HEAD でメッシュを持つパーツだけ差し替え。子パーツはバニラと同じく描く)、`MixinPlayerModelSkinLayer` (`setupAnim` の TAIL で割り当て)、`MixinAvatarRendererSkinLayer` (`renderHand` の HEAD で一人称の袖に割り当て)。すべて `require = 1`。
  - 26.3 側で確認したこと (バイトコード): ① `ModelFeatureRenderer#prepareModel` が `Model#setupAnim(state)` を描く直前に呼ぶ (setupAnim の TAIL への割り当てが描画時に効く) ② `Model#renderToBuffer` → `ModelPart#render(5 引数)` ③ `ModelPart#render` の構造 (visible → pushPose → translateAndRotate → skipDraw でなければ compile → 子 → popPose) ④ 一人称の手は `submitModelPart` → `Model$Simple` → `submitModel` を経由し、袖は腕の子パーツ ⑤ `@Shadow` の対象 (`ModelPart.cubes/children/visible/skipDraw`、`PlayerModel.slim`、`translateAndRotate`) の名前と記述子 ⑥ `PlayerModel` の各レイヤーの Cube の寸法・UV 原点 (ワイド/スリム)。
  - 手元で確認したこと: JDK 25 + 本物の Minecraft 26.3 jar + Mixin / MixinExtras / DFU / Brigadier のソースで、プロジェクト全体をコンパイル。**新規ファイルのエラーは 0 件**、既存コードの残りは `ParentEntry` の LWJGL SDL の 2 件のみ (許容)。`tools/check_rules.py` は違反 0。さらに、本物の `PlayerModel.createMesh` (ワイド/スリム × 6 レイヤー) から作った Cube に対して `SkinLayerMesh` を実行し、(a) 全面不透明のとき外側の四角形数がバニラの面のピクセル数と一致 (b) 各四角形の UV がバニラの面の UV の補間値と一致 (c) 頂点の巻き方向と法線の向きがバニラの面と同じ、全部 OK。孤立 1 ピクセルで 5 枚 (外側 1 + 側面 4)、全透明で空、128x128 でも同数、64x32 は null。
  - **未確認 (要・実機)**: ① **Mixin の実際の適用** (起動時の `require` の成否) ② 見た目 (向き・色・影・半透明の縁、角のすき間) ③ **一人称の手**: 手は延期描画なので、同じフレームに他のプレイヤーの `setupAnim` が割り込むと、袖に別のスキンのメッシュが残る可能性がある (手の描画が他のエンティティと別のパスなら起きない。未確認) ④ 描画の負荷 (1 体で四角形が数百〜千枚、毎フレーム頂点を書き出す) ⑤ スキンの絵の取得 (ダウンロード済みは `DynamicTexture#getPixels`、既定スキンはリソースから読む。**登録済みテクスチャが `DynamicTexture` 以外の型のとき**はリソースから読めないと作れず、2D のままになる) ⑥ ログイン直後などスキンが未取得のとき ⑦ Sodium / Iris との相性 ⑧ 検証テストはプロジェクトに含めていない (`/tmp` のみ)。authlib など非公開ライブラリは最小のスタブで代用した。
  - 判断したこと: ① 範囲は本体 6 レイヤーと一人称の袖 (頭ブロック・耳・EMF 対応は対象外) ② 数値設定は持たず固定値 (距離 16 ブロック、厚さ 1 ピクセル) ③ `ModelPart#render` の差し替えは、全パーツ・毎フレーム呼ばれるため `@WrapMethod` ではなく `@Inject` (HEAD, cancellable) にした。メッシュが無い・OFF のときは何もせずバニラへ進む (P-60 の「元の動作に戻せる形」は満たす) ④ 既定は当初 P-41 に従い OFF としたが、ユーザーが「規定は ON」と指示したので ON に変更 (P-41 の表・README・`check_rules.py` を揃えた)。
- **3D スキンレイヤーの頂点数削減** (見た目は変えない。`SkinLayerMesh` のみの変更):
  - 元の 3D Skin Layers の対策 (jar のバイトコードで確認した範囲): LOD 距離 (既定 14 ブロック)、隣のピクセルを見て見えない面を作らない、Fast Render (既定 ON。内側の面を省く)、スキンごとのメッシュのキャッシュ。面をまとめる処理は確認できなかった。こちらは当初、LOD 16 ブロック・内側と隣接面の省略・キャッシュまでだった。
  - 追加した削減: ① **外側の面を長方形ごとに 1 枚へ** (貪欲法)。UV は 1 ピクセル 1 点ではなく、バニラの面と同じスキン上の実際の範囲 (スキンのテクスチャは最近傍で引かれるので色は同じ) ② **側面を、同じ向きで続き、色が同じものは 1 枚へ** (色はピクセル中心の 1 点の UV で引くので、同色なら同じ)。
  - **削らなかったもの**: 面の境目 (角) の側面。隣の面のボクセルに高さの半分だけ隠れるが、残り半分は角を埋めていて見える。省くと見た目が変わる。
  - 確認できたこと: 統合前の版 (zip の一つ前の版) と統合後で、本物の `PlayerModel` の Cube (ワイド/スリム × 6 レイヤー) に 6 種の模様 (単色・ランダム色・市松・ランダム穴・縞・半透明混在) を当て、**各四角形の上に探査点 約 403 万個**を置いて、その点を覆う四角形の「枚数・色・巻き方向」が統合前後で一致することを確認 (新旧の両方向。余計な面や重なりも検出)。不一致 0。四角形の合計は 33,726 → 16,062 (47.6%)。全面不透明・単色は 576 → 30 (5%)、全面不透明・ランダム色は 576 → 198 (34%)、市松は 100% (まとまるものが無い)。HD (128・256) でも同じ枚数、64x32 は null、全透明は空。
  - **未確認**: 実機での見た目 (特に HD スキンでは、外側の面が実際のテクスチャを引くので、1 ピクセル内の細かい絵が見える = 旧方式の「ピクセル中心の 1 点」より細かくなる。バニラの 2D と同じ見え方)、描画負荷の実測、テクスチャ補間の境目の滲み (バニラの面と同じ条件なので問題ないはずだが未確認)。検証用の模様は人工のもので、実際のスキンでの削減率はスキン次第 (市松のような細かい模様では減らない)。
- **3D スキンレイヤー: 頭ブロック・頭アイテム (GUI 含む)・着用中の頭に対応** (最初は対象外だったが、ユーザーの確認を受けて追加):
  - 難所: 頭の描画 (`SkullBlockRenderer#submitSkull`) は `RenderType` しか受け取らず、**スキンの情報が描画側に渡らない** (`SkullModelBase$State` は向きとアニメーションだけ)。しかも `State` は `submitSkull` の中で作られ、描画は延期される (`setupAnim` が描画の直前に呼ばれる)。
  - 方式: ① 3 つの入口でスキンを拾い、`SkinLayers.pendingSkull` に一時的に置く (ブロック: `extractRenderState` で `SkullBlockRenderState` に入れて `submit` の間だけ置く / アイテム・GUI: `PlayerHeadSpecialRenderer#submit` の `RenderInfo` から / 着用: `CustomHeadLayer#submit` の `wornHeadProfile` から)。② `State` の生成 (`<init>` の RETURN) でそれを受け取り、受け取ったら空にする (1 つの State にだけ渡す。例外などで残っても次の State には渡らない)。③ `SkullModel#setupAnim` の TAIL で、`head` の子 `hat` にメッシュを持たせる (`hat` を持つのは人型の頭だけ。モブの頭は何もしない)。メッシュは既存の `SkinLayerMesh` をそのまま使う (Cube を読んで作るので、帽子と形が違う `hat` の ±4.25px でも同じ手順。キャッシュは別の種類 `SKULL_HAT` で覚える)。
  - 確認できたこと: 新しい Mixin 6 本の注入先 (メソッド名+記述子) と `@Shadow` のフィールドを、26.3 の jar に対して**機械的に照合** (既存 3 本と合わせて 15 注入・8 フィールドすべて実在、不一致 0)。本物の `SkullModel.createHumanoidHeadLayer()` で `head.hat` が在り、`createMobHeadLayer()` には無いこと、`hat` の Cube から作ったメッシュ (全面不透明で 30 枚、ランダム色で 198 枚、全透明で空)、外側への張り出しが厚みの半分 (0.5px) 以内。pending の受け渡し (1 回目は値、2 回目は null)。スキンレイヤー関連のコンパイルエラー 0。
  - **未確認 (要・実機)**: ① Mixin の実際の適用 (特に `State` の `<init>` への注入と、`SkullModelBase.State` を対象にした Mixin の指定) ② 頭ブロック・GUI のインベントリ・手・額縁・被っている頭の実際の見た目 ③ GUI での 3D の見え方 (小さく描かれるので、ボクセルが細かすぎて 2D との差が分かりにくい可能性) ④ `PlayerSkinRenderCache#getOrDefault` を、バニラの呼び出しとは別にもう一度呼ぶ (キャッシュ参照だが、副作用は未確認) ⑤ 読み込み前の頭 (デフォルトのスキンの `RenderInfo`) では、デフォルトスキンのレイヤーで立体になる (多くは透明なので 2D と同じ見た目) ⑥ 頭アイテムは距離で切らないので、地面に多数の頭アイテムがあると負荷が上がりうる (メッシュはキャッシュ済み、描画は毎フレーム)。
- **3D スキンレイヤー: 見直しで見つけた不具合の修正と、Mixin 適用の確認** (これまで「未確認」「対象外」としていた項目の見直し):
  - **不具合 (修正済み): Deadmau5 の耳のモデル** (`PlayerEarsModel`) は `PlayerModel` を継承し、`createEarsLayer` が `clearRecursively()` で全パーツの Cube を空にしてから耳だけを足す。つまり帽子・上着・袖・ズボンのパーツを持つが Cube は 0 個 (実測)。そのままだと ① メッシュのキャッシュ鍵 `(スキン, スリムか, 部位)` が本体と耳モデルで衝突し、耳モデルが先に処理されると「作れない」が記録されて**本体のレイヤーが立体にならなくなる** ② 本体が先だと、耳モデルの空のパーツにも本体のメッシュが付いて、**耳のレイヤーが全身を二重に描く**。対策: `SkinLayers.apply` は `PlayerEarsModel` を何もせず返す (元の mod も耳のモデルに「無視」フラグを立てていた。耳そのものを立体にはしていない)。さらに保険として、**Cube が空のパーツにはメッシュを付けない**。
  - **弱点 (修正済み): 一時的な失敗が永久にキャッシュされる** (スキンのテクスチャが未登録・読み込み前、など)。メッシュを作れなかった結果は 5 秒後にやり直す印を付け、確定した結果 (できた・全部透明・立体にできない形式) だけを永久に覚える (`CachedMesh`)。
  - **一人称の手の「別スキンのメッシュが残る」疑い (解消)**: バイトコードで確認。手とスクリーンは、エンティティとは**別の保管先** (`GameRenderer.handAndScreenSubmitNodeStorage`) を使い、`renderItemInHand` の中で「手を submit → `prepareFrame` → `renderAllFeatures`」の順に即座に描かれる。手の submit (= `renderHand` の HEAD で割り当て) と描画の間に、他プレイヤーの `setupAnim` は入らない。
  - **Mixin の適用 (確認)**: Mixin 0.8.7 (SpongePowered、ソースをビルド) の**本物の変換エンジン**に、自分の Mixin 9 本 (注入 15 か所) と本物の 26.3 のクラスを通した検証用ハーネスを作って確認 (プロジェクトには含めない)。ローダーは 26.3 の jar とプロジェクトのクラスだけを読み、ASM 9.7 が Java 25 のクラスファイルを読めるよう、**バージョン番号だけを 67 に下げて**変換した。結果: ① 9 クラスすべてで例外なく適用 (`require = 1` を含む) ② `ModelPart` に `SkinLayerPart`、`PlayerModel` に `SkinLayerModel`、`SkullBlockRenderState` と `SkullModelBase$State` に `SkinLayerSkull` が追加された ③ 変換後のバイトコードに、注入ハンドラと `SkinLayers.*` の呼び出しが入っている (`AvatarRenderer`・`SkullBlockRenderer`・`SkullModel`・`PlayerHeadSpecialRenderer`・`CustomHeadLayer` を含む)。
  - **変換後の本物のクラスで動かして確認**: `ModelPart#render` (メッシュ無しはバニラの 24 頂点のまま / 有りはメッシュのみ・位置と UV と法線が `translateAndRotate` 後の `mesh.render` と完全一致 / 親経由では親の Cube とメッシュの両方 / `visible=false`・`skipDraw`・設定 OFF では描かない・バニラに戻る)、頭の `State` へのスキンの受け渡し (1 つ目だけ、2 つ目は null)、`SkullModel#setupAnim` (人型・モブとも例外なし)、`PlayerModel#setupAnim` → 注入 → `apply` (16 ブロックより遠いと 6 レイヤーとも外れる / 設定 OFF では何もしない)、`PlayerEarsModel` (付けたメッシュに触れない)。
  - **この確認の限界**: ① 使ったのは **Mixin 0.8.7** で、Fabric Loader 同梱の Mixin (フォーク) ではない (本プロジェクトの設定の `compatibilityLevel: JAVA_25` は 0.8.7 に無いため、検証用の設定では JAVA_21 にした)。注入の意味は同じだが、フォーク固有の差は未確認 ② スキンのテクスチャを取る処理 (`SkinLayers.create`) は `Minecraft` 本体の初期化 (jtracy などの実行時ライブラリ) が要るので、この環境では動かしていない (ここより先は実機) ③ 描画の見た目・負荷・Sodium/Iris との相性は、従来どおり実機でしか分からない。
- **アセットの名前空間を `neo` に統合** (`assets/modmenu`・`assets/spawnerinfo`・`assets/neomod` → `assets/neo`、P-05 を追加):
  - **ファイル**: `modmenu` のアイコン 6 枚・テクスチャ・スプライト (計 17 ファイル) を `assets/neo/` へ移動 (中身は変更なし)。lang は 3 名前空間 × 2 言語の 6 ファイルを `assets/neo/lang/{en_us,ja_jp}.json` の 2 ファイルに統合 (**キーは 1 つも変えていない**。統合前後で内容が完全一致することをスクリプトで確認: en_us 220 キー・ja_jp 218 キー、キーの重複なし)。旧ディレクトリは削除。
  - **コード**: 新しい `com.neo.util.NeoAssets` (`NAMESPACE = "neo"`、`path(relative)` = `assets/neo/<relative>`)。依存のない葉のクラスなので、パッケージの循環も新しいパッケージも作らない (P-13・P-14)。アセットの名前空間として使っていた箇所だけを切り替えた: `ParentEntry`・`SmallModMenuButtonWidget`・`ModListEntry`・`ModsScreen` (テクスチャ・スプライト・アイコンの識別子)、`IconHandler` (動的テクスチャの名前)、`FabricMod`・`FabricDummyParentMod`・`ForgeMod` (jar 内のアイコンのパス `assets/neo/…`)、`HeldMapView` (動的テクスチャ `neo:map/held`)。
  - **変えなかったもの (別物)**: ① 翻訳キーの接頭辞 (`option.modmenu.…`・`key.category.neomod.main` など。lang ファイルの置き場とは無関係で、Minecraft は全名前空間の lang を読むため、置き場を変えてもキーは同じまま引ける) ② 設定ファイル名 `config/modmenu.json` (変えると既存ユーザーの設定が消える) ③ `ModContainerIconSource.of("modmenu")` などの Fabric のコンテナ ID (`provides` で解決される) ④ Mod ID `neomod`・Spawner Info のネットワークのチャンネル ID `spawnerinfo:spawner_data` (変えるとサーバーとクライアントで食い違う)・キーカテゴリの ID ⑤ データの保存先 `data/neo/` ⑥ `IconHandler` の「他の Mod 自身のデフォルトアイコン `assets/<その Mod の ID>/icon.png`」の判定 (他の Mod の名前空間なので `neo` に寄せられない)。
  - **検査**: `tools/check_rules.py` に P-05 を追加 (アセットの名前空間が `neo` だけ / 旧名前空間のパスの直書きが無い / `MOD_ID` をアセットの名前空間に使っていない / `NeoAssets` で参照しているテクスチャ・スプライト・アイコンのファイルが実在する)。また neomod 由来の翻訳キーの両言語一致の検査を、統合後の lang に合わせて接頭辞で見分ける形にした。**検査が本当に違反を検出するかも、7 通りの意図的な破壊 (余分な名前空間・参照先のテクスチャ/スプライト/アイコンの削除・旧名前空間の直書き・MOD_ID の誤用・片方の言語のキー削除) で確認**し、すべて検出、元に戻して違反 0。
  - **確認できたこと**: 全体のコンパイルで、この変更に関するエラーは 0 件 (残りは従来どおり `ParentEntry` の LWJGL SDL の 2 件)。`check_rules.py` は違反 0 (128 クラス)。ビルドスクリプトは `src/main/resources/*` を丸ごとコピーするので、移動の影響を受けない。
  - **未確認 (要・実機 / ビルド)**: ① `build.bat` の結果の jar に `assets/neo/` が入り、Mod 一覧のアイコン・スプライト・翻訳が表示されるか (参照先のファイルが実在することは検査で確認したが、実際の読み込みは未実行) ② Forge 版 (`ForgeMod`) は、Forge の jar が無いのでコンパイルしていない (import と参照の整合のみ確認) ③ 他の Mod やリソースパックが旧パス (`assets/modmenu/…`) を上書きしていた場合は効かなくなる。

## 3D スキンレイヤーの Z-fighting 修正 (挙動変更)
- 原因: ボクセルの厚さが「レイヤー面を中心に ±0.5px」だったため、帽子 (バニラで頭から 0.5px) の外面が頭の中心から 5.0px になり、OUTER 防具のヘルメット (頭 +1.0 = 5.0px) と同一平面で Z-fighting した。
- 修正: `SkinLayerMesh` の厚さを内側 `INWARD_PX = 0.5` / 外側 `OUTWARD_PX = 0.4` の非対称に変更 (帽子の外面は 4.9px)。内側は変えていない。
- 確認できたこと: `check_rules.py` 違反 0。`LayerDefinitions` の定数プールに 0.5 / 0.25 があること。
- **未確認**: コンパイル (手元は JRE 21 のみで javac なし)、実機での見た目、OUTER = 1.0 / INNER = 0.5 の直接確認 (1.0 は fconst_1 で定数プールに出ない)。ズボンは INNER 防具 (0.5) より外 (0.25+0.4=0.65) に出るので、レギンスを突き抜ける見た目は残る (Z-fighting ではない)。
- **未対応 (要判断)**: 角で隣り合う 2 つの面のボクセルが、同じ平面に側面 (上下) を重ねて出す箇所が残る。色が違うと縁で Z-fighting し得る。直すなら角の側面の範囲を片方の面に寄せる必要があり、見た目が変わるので採否をお願いします。

## F5 自由視点: バニラの切り替えを二重に止める (挙動変更)
- 依頼: 自由視点を ON にしたとき、バニラの視点切り替えも動いて自由視点が壊れる。自由視点中はバニラの切り替えをしない。
- 調べたこと: 26.3 の `Minecraft#handleKeybinds` の先頭が `keyTogglePerspective.consumeClick` → `CameraType#cycle` → `Options#setCameraType` で、`keyTogglePerspective` を読むバニラのクラスは `Minecraft` と `Options` だけ (jar 全体を検索)。既存の注入 (consumeClick の ordinal 0) は正しい位置で、設定 ON なら false を返してバニラの切り替えを飛ばす作りだった。設定の読み書き・初期化・Mixin 登録・キー定義にも不具合は見つからなかった。**静的には、バニラの切り替えが走る経路を特定できていない**。
- 対応: `FreeLookState.blocksVanillaSwitch()` (自由視点中、または設定 ON の間 true) を追加し、`MixinMinecraftFreeLook` に `handleKeybinds` の `Options#setCameraType` (ordinal 0、offset 35) の WrapOperation を追加。判定の肩代わりとは独立に、書き込みも止める。設定 OFF かつ自由視点でないときは、設定を 1 回引いて元の処理を呼ぶだけ。
- 確認できたこと: JDK 25 で、26.3 の jar + Mixin / MixinExtras / DFU / Brigadier のソースに対して FreeLookState と 3 つの Mixin をコンパイル (エラー 0)。注入先 (`Options.setCameraType:(Lnet/minecraft/client/CameraType;)V`、`handleKeybinds` 内で 1 か所) はバイトコードで確認。`check_rules.py` 違反 0。
- **未確認**: 実機での動作 (Mixin が実際に適用されること、症状が消えること)。症状が残る場合は、どの操作で何が起きるか (F5 を押した直後か、押している間か、など) と `logs/latest.log` が必要。

## F5 自由視点: 押している間だけに一本化 (挙動変更)
- 依頼: 「F5 は長押しに対応していないのが原因だった。押している間だけでいい」。
- 変更: 切り替え式と設定 `free_look_hold` を廃止。F5 を押すと自由視点に入り、離すと押す前の視点に戻る (`FreeLookState#consumePerspectiveKey`: 押下で activate、`isDown` が false なら deactivate。画面を開いていて handleKeybinds が呼ばれない間の「離した」は `onClientTick` が見る)。`free_look_hold` の設定・翻訳・README・check_rules の既定値表を削除。既存の `config/modmenu.json` に残る `free_look_hold` は無視される。
- 未確認: 実機での動作 (特に、押した直後の 1 tick で離す短いタップ、押したまま画面を開く操作)。

## F5: 単押し = バニラ / 長押し = 自由視点 (挙動変更)
- 依頼: 単押しでバニラの視点切り替え、長押しで自由視点。直前の「押している間だけ」を置き換え。
- 実装 (`FreeLookState`): 押下で `pressedAtMillis` を記録 → `HOLD_MILLIS` (250 ms、判断で決めた値) を超えて押し続けたら自由視点に入る / それより前に離したら単押し (`tapPending`) で、次の判定 1 回だけ true を返してバニラの切り替えを 1 度走らせる (離した時点で切り替わる)。自由視点中は離したら戻る。画面を開いていて handleKeybinds が呼ばれない間は `onClientTick` が同じ判定を進める。`blocksVanillaSwitch` は自由視点中だけ true (単押しのバニラ切り替えを通すため)。
- 確認できたこと: JDK 25 で 26.3 の jar に対してコンパイル、`check_rules.py` 違反 0。
- **未確認 (要・実機)**: 250 ms の体感、単押しのバニラ切り替え (離した時点)、長押し中に画面を開く / ワールドを離れる操作。**判断**: 長押しの時間 (250 ms) を変えたいときは `HOLD_MILLIS`。

## Mod Menu の項目を固定値に (挙動変更・設定項目の削除)
- 依頼: イースターエッグ・非表示 Mod をカウント・Mod リンク/ライセンス/クレジット・Mod カウントの位置・前提 Mod を表示・Mod タグは、既定値のみでオプション不要。
- 削除した設定項目: `easter_eggs` / `count_hidden_mods` / `hide_mod_links` / `hide_mod_license` / `hide_mod_credits` / `hide_badges` / `mod_count_location` / `show_libraries` (設定画面・翻訳 en/ja・`ModMenuConfig` から)。値は固定: オン / 数える / 表示 / 表示 / 表示 / 表示 / 両方 / 設定のある前提 Mod のみ (`ModMenuConfig.LIBRARY_VISIBILITY`)。
- `ModCountLocation` enum と、Mods 画面の「前提 Mod を表示」切り替えボタン (`librariesButton`) も削除。既存の `config/modmenu.json` に残っている該当キーは無視される (読み込みは項目単位)。
- 未確認: Mods 画面の絞り込みの並び (ボタンが 1 つ減った見た目)、実機。

## 設定の保存先を config/modmenu.json から options.txt へ (挙動変更)
- 依頼: `config/modmenu.json` をやめ、バニラの `options.txt` に保存する。既存の `modmenu.json` の移行は不要。Forge 版は対象外。
- 変更: `ModMenuConfigManager` を作り直し (JSON/Gson/ファイル配置を削除)。設定は `options.txt` に `neo_<キー>:<値>` の行で保存 (boolean は true/false、enum は小文字の名前、`hidden_mods` / `hidden_configs` は「,」区切り)。`ModMenuConfigManager#save` はバニラの `Options#save` を呼ぶだけ。`initializeConfig` は不要になり削除 (`ModMenuBootstrap` の呼び出しも削除)。
- 方式の判断: バニラの `Options#processOptions` に差し込むのが素直だが、引数の `Options$FieldAccess` が **package-private** (26.3 の jar を javap で確認) で、Mixin の引数に型を書けない (`@Coerce` は @Inject では「より具体的な型」への指定のみで、`Object` にはできない)。そのため `MixinOptions` が `Options#load` の HEAD で `neo_` の行を読み、`Options#save` の TAIL (return は 1 か所) でバニラの保存の直後に自分の行だけを追記する。バニラは知らないキーを読み飛ばす (保存でも消える) ので、追記が必要。
- 規約: AGENTS.md の P-05 / P-22 / P-26、README を更新。`config/modmenu.json` は読まない・書かない。`ModMenuPlatform#configDir` は Forge 版のために残している (この Mod の本体からは使わない)。Forge 版は Mixin が無いので設定が保存されない (対象外)。
- 確認できたこと: JDK 25 で `ModMenuConfigManager` と `MixinOptions` をコンパイル (この 2 ファイルのエラー 0。ほかのクラスは手元に Mojang のライブラリが無く全体のコンパイルはできていない)。`Options#load` / `save` の記述子・`getFile()`・`save` の return が 1 か所であることはバイトコードで確認。
- **未確認 (要・実機)**: Mixin の適用 (`require = 1`)、`options.txt` の `neo_` の行が保存・読み込みされること、`hidden_mods` が空のときの `neo_hidden_mods:`、ゲーム終了時 / 設定画面を閉じたときの保存。

## デッドコード・不要コードの整理 (挙動は変えない)
- 削除したクラス (どこからも参照されない): `OptionalUtil`、`JsonUtil`、`EnumToLowerCaseJsonConverter` (設定を JSON にしていた名残)。
- 削除したメソッド・定数 (参照ゼロを確認): `VersionUtil#removeBuildMetadata`、`BooleanConfigOption#toggleValue`、`ConfigOptionStorage#toggleBoolean` / `getEnumTypeless` / `setEnumTypeless`、`ModMenuConfig.LibraryVisibility#isTrue`、`ParentEntry#setChildren`、`CustomValueUtil#getStringArray`、`FabricMod` の `addClientBadge` / `addLibraryBadge`、`ModListEntry.UNKNOWN_ICON`。未使用 import 2 件 (`ModMenuConfigManager` の `Options`、`DescriptionListWidget` の `ModMenuConfig`)。
- 簡略化: `BooleanConfigOption` の 4 引数コンストラクタ (常に "true"/"false" で呼ばれていた) と、`null` にならない文字列の判定・到達しない `createBoolean` 側の分岐を削除。`EnumConfigOption` に `getFileValue` / `setFileValue` を持たせ、`ModMenuConfigManager` のジェネリクスのリフレクション (`ParameterizedType`) を無くした。`ModMenuConfigManager#save` の不要な null 判定を削除。
- 残したもの: `ModMenuApi#createModsScreen` など Mod Menu API 互換のメソッド (他 Mod が実装する名前)、`ModMenuPlatform#configDir` (Forge 版用)、`LibraryVisibility` の `FALSE` / `TRUE` (固定値にした経緯の記録として。使うなら定数を変えるだけ)。
- 確認できたこと: 削除した名前への参照が `src` / `tools` / ドキュメントに残っていないこと、未使用 import が 0 であること、`check_rules.py` 違反 0、設定まわり (`ModMenuConfigManager` ほか) のコンパイルエラーのうち手元のライブラリ不足 (DFU・Brigadier 等) に起因しないものが 0 であること。**未確認**: プロジェクト全体のビルド (手元に Mojang / Fabric のライブラリが無い)。`build.bat` での確認をお願いします。

## 既定値の変更: F5 自由視点を ON、Mods ボタンの位置を「Realms を置換」(挙動変更)
- 依頼: 「フリールックを規定 ON で、MOD ボタンの位置は規定で置き換え」。
- 変更: `free_look` の既定を OFF → ON。`mods_button_style` の既定を `ICON` → `REPLACE_REALMS` (表示は「Realms を置換」)。`options.txt` に値が無いとき (初回・`neo_` の行が無いとき) だけ効く。すでに `neo_free_look` / `neo_mods_button_style` が書かれていれば、その値が優先。
- 規約: P-41 の既定 ON を 9 → 10 個に (`AGENTS.md`・`README.md`・`tools/check_rules.py`・`FreeLookState` のコメントを同期)。Mods ボタンの位置は P-41 の対象外 (Mod Menu 本来の設定) と明記。`check_rules.py` 違反 0。
- 解釈: 「MOD ボタンの位置」= タイトル画面の Mods ボタン (`mods_button_style`)、「置き換え」= `REPLACE_REALMS`。別の意味 (ゲーム内メニューなど) なら指示してください。
- 未確認: 実機でのタイトル画面の見た目 (Realms ボタンが Mods に置き換わること)。

## マップの自動表示を 1 つに統合 / スポナー情報をクリエイティブ限定に (挙動変更)
- 依頼: 「マップに自動表示 ON を一つのオプションに統合。スポナー系のオプションはクリエイティブ時にのみ使えるように」。
- 統合: `map_structures` と `map_banners` を `map_auto_markers` (既定 ON) に統合。構造物と旗は同じスイッチで出入りする (構造物はこれまでどおりシングルのみ)。`MapMarkers` は OFF のとき tick・描画 (`inWindow`) とも入口で打ち切る。翻訳 (en/ja)・README・AGENTS.md (P-41 の既定 ON は 9 個に)・`check_rules.py` を同期。`options.txt` に残る `neo_map_structures` / `neo_map_banners` は無視される。
- スポナー: `SpawnerInfoClient.isEnabled()` (`spawner_info` が ON **かつ** `LocalPlayer#isCreative`) を、描画 (`isActive`)・発光 (`SpawnerGlowRenderer.isActive`)・受信 (`onPayload`)・キー (最寄りのみ表示)・サーバーへの送信許可 (`setSendEnabled`) の共通の条件にした。クリエイティブでなくなると溜めたデータを捨てる。サーバー側 (`SpawnerInfoMod.onServerTick`) も、`ServerPlayer#isCreative` でないプレイヤーには送らない (専用サーバーでも有効。走査も省ける)。スポナー情報の翻訳に「(クリエイティブのみ)」を追記。
- 確認できたこと: `Player#isCreative()` と `LocalPlayer` を 26.3 の jar で確認、変更したクラスのコンパイルで新規のエラー無し (手元のライブラリ不足によるものを除く)、`check_rules.py` 違反 0。
- **未確認 (要・実機)**: サバイバル/スペクテイターでスポナー情報が出ないこと、ゲームモード切替の直後の挙動 (クライアントがモードを知るまで数 tick)、専用サーバーでの送信抑止。

## Mod Manager への改名 / 開くキー (ユーザー指示)
- 表示名を「Mod Menu」→「Mod Manager」に変更 (`en_us` / `ja_jp` の `category.modmenu.name` / `key.modmenu.open_menu` / `modmenu.nameTranslation.modmenu` / `modmenu.options`、`modmenu.configure.error`)。翻訳キー・クラス名・`com.terraformersmc.modmenu.api`・ログ名・出自表記 (MIT の Mod Menu) は変えていない (P-05 / 外部 API 互換)。
- 「Mod Manager を開く」キー (`key.modmenu.open_menu`) を、カテゴリ「その他」(`KeyMapping.Category.MISC`) から「NeoMod」(`NeoKeyMappings.MAIN`) に移し、既定を JIS の @ キー (`InputConstants.KEY_LBRACKET`) に変更。
- 確認したこと: `InputConstants` に `KEY_LBRACKET` が実在する (26.3 jar) / `KEY_AT` は無い。
- **未確認**: コンパイル・実機。JIS 配列の @ キーが `KEY_LBRACKET` として届くこと (GLFW は物理位置基準のため想定どおりのはず)。他のキー (`[` を使う操作) との重複。
- **クラス名の改名 (ユーザー指示)**: `ModMenu*` → `ModManager*` (`ModMenuEventHandler`/`Bootstrap`/`Platform`/`Info`/`ScreenTexts`/`Config`/`ConfigManager`/`OptionsScreen`/`ButtonWidget`/`SmallModMenuButtonWidget`/`ModMenuFabric`)。入口の `ModMenuModMenuCompat` は `ModManagerCompat`。ログ名も「Mod Manager | …」に。上の過去の記録は当時の名前のまま。
  - 残したもの: `com.terraformersmc.modmenu.api.ModMenuApi` (他 Mod が実装する外部 API)、`FabricMod.ModMenuData` / `getModMenuData` (他 Mod の `custom.modmenu` を読む外部スキーマ)、`modmenu` エントリポイント名・`provides: modmenu`・翻訳キー `modmenu.*`・`option.modmenu.*` (P-05)。
  - **未確認**: コンパイル (JDK 25 なし)。名前の置換は機械的に行い、ファイル名とクラス名の一致・重複なし・`check_rules.py` 違反 0 までは確認。

- **Mod 数の「前提 Mod / 子 Mod をカウント」を固定 ON にして設定から削除 (ユーザー指示)**: `ModManagerConfig` の `COUNT_LIBRARIES` / `COUNT_CHILDREN` と翻訳 (`option.modmenu.count_*`) を削除。`ModCount` は全 Mod を数える (`ModRegistry.MODS.size()`)。options.txt に残る `neo_count_*` の行は、読み込みで無視される。
  - **未確認**: コンパイル・実機。

- **Mod 一覧の先頭固定 (ユーザー指示)**: `ModOrdering` で、並べ替え (昇順 / 降順) の設定に関係なく上から `java` → `minecraft` → `neo` の順に固定。残りは従来どおり表示名順。
  - Java は `LIBRARY` バッジ付きで、設定画面を持たない。`LIBRARY_VISIBILITY = WITH_CONFIG` では「設定のない前提 Mod」として一覧から隠れていたため、`LibraryVisibility#hideMod` で `java` だけ例外 (常に表示) にした。
  - **未確認**: コンパイル・実機。Java アイコン (`assets/neo/java_icon.png`、16x16、透明背景に黒と白の絵) が表示されない原因は、静的な調査では「一覧から隠れていた」以外に見つからなかった。実機でまだ見えない場合は、ログの `Invalid mod icon for icon source` / `Must be square icon` の有無を確認する (アイコンの読み出しは `ModManagerInfo.MOD_ID` = `modmenu` を `provides` の別名として引く)。

- **クイック設定ボタンを固定 ON にして設定から削除 (ユーザー指示)**: `QUICK_CONFIGURE` と翻訳 (`option.modmenu.quick_configure*`) を削除し、`ModListEntry` / `ParentEntry` は常に ON の動作 (アイコン・ダブルクリックで設定画面。親 Mod のダブルクリックは設定画面、アイコンで子 Mod の展開)。`ParentEntry` の「OFF のときダブルクリックで展開」の分岐を削除。
  - **未確認**: コンパイル・実機。

- **設定画面に「既定に戻す」ボタンを追加 / options.txt には既定値から変えたものだけ保存 (ユーザー指示)**:
  - 確認の結果、変更前は `appendTo` が全項目の `neo_` 行を毎回書いていた (「変更分だけ」にはなっていなかった)。`ModManagerConfigManager#format` が、既定値と同じ項目は `null` を返して行を作らないよう変更。`BooleanConfigOption` / `EnumConfigOption` / `StringSetConfigOption` に `isDefault()`、`OptionConvertible` に `resetToDefault()` を追加。`StringSetConfigOption` は保存用の集合と既定値を別コピーにした (保存側の変更が既定値に影響しないように)。
  - `ModManagerOptionsScreen#addFooter` を上書きし、「既定に戻す」と「完了」を横に並べた。「既定に戻す」は `ModManagerConfig.resetToDefaults()` (設定画面に出る項目だけ。`hidden_mods` などのファイル専用は対象外) のあと `rebuildWidgets()`。保存は画面を閉じるとき (従来どおり)。翻訳 `modmenu.options.reset` を `en_us` / `ja_jp` に追加。
  - jar で確認: `OptionsSubScreen#addFooter` (protected)・`layout` (HeaderAndFooterLayout)・`Screen#rebuildWidgets`・`LinearLayout#horizontal/spacing/addChild`・`Button#builder(Component, OnPress)`。
  - **未確認**: コンパイル・実機 (ボタンの幅・並び、リセット後の表示更新)。

- **Java の表示を整理 (ユーザー指示)**:
  - Java に付いていた「前提 Mod」(`Badge.LIBRARY`) をやめた (`FabricMod`)。これに伴い、前回足した `LibraryVisibility#hideMod` の Java 例外は不要になったので削除 (Java は前提 Mod ではないので、もともと隠れない)。Mod 数の内訳 (「N mods and M libraries」) では、Java が前提 Mod ではなく Mod に数えられる。
  - 「Java の色」(`RANDOM_JAVA_COLORS`) と翻訳 (`option.modmenu.random_java_colors*`) を削除。背景は赤固定 (従来の既定 OFF と同じ見た目)。`DrawingUtil#drawRandomVersionBackground` は `drawJavaBackground` に改名・簡素化。
  - 一覧の行の要約 (`ModTexts#translatedSummary`) を、Java だけ「Java実行環境 <java.version> (<JVM 名>)」にした (翻訳 `modmenu.javaSummary`)。詳細欄のバージョン表示 (`v<バージョン>`) は以前から出る。
  - アイコン `assets/neo/java_icon.png` を、32x32 の自作コーヒーカップに差し替え (公式の Java ロゴではない。商標のため同梱していない。公式ロゴ等を使うときは、同じ名前の正方形 PNG に置き換えれば動く)。
  - **未確認**: コンパイル・実機。

- **Mod 一覧画面のタイトル (ユーザー指示)**: 左上の「Mods」を「Mod Manager」に変更し、下線を付け、カーソルを重ねるとツールチップ「説明：読み込まれているMODを管理するNeoModsの機能」を出す。新しい翻訳キー `modmenu.screenTitle` / `modmenu.screenTitle.tooltip`、定数 `ModManagerScreenTexts.SCREEN_TITLE` / `SCREEN_TITLE_TOOLTIP`。「Mods」ボタンの文言 `modmenu.title` (`TITLE`) は別なので変更していない。ツールチップの文言は指定どおり「NeoMods」(Mod 名の「NeoMod」と表記が違う)。
  - **未確認**: コンパイル・実機 (下線の表示、ホバー範囲、ツールチップの位置)。

- **Minecraft のボタン文言を統一 (ユーザー指示)**: 詳細欄の Minecraft だけ「フィードバックを送る」「バグを報告」(`menu.sendFeedback` / `menu.reportBugs`) だったのを、他の Mod と同じ「ウェブサイト」「問題を報告」に統一。リンク先 (`CommonLinks` のフィードバック / バグ報告) は変えていない。未使用になった `SEND_FEEDBACK_TEXT` / `REPORT_BUGS_TEXT` を削除。
  - **未確認**: コンパイル・実機。

- **Mod の表示名を「NeoMods」に変更 (ユーザー指示)**: `fabric.mod.json` の `name` / ライセンス表記、Forge 版 `mods.toml` の `displayName`、操作設定のカテゴリ名 (`key.category.neo.main`、`en_us` / `ja_jp`)、起動ログの見出し、README の表記。Mod の ID (`neo`)・ファイル名 (`mods/NeoModLog.txt`)・パッケージ名・設定の保存先は変えていない。
  - **未確認**: コンパイル・実機。

## 画面の直接切り替えを、すべてのコンテナ画面・本・ワールドマップへ拡大 (挙動変更・ユーザー指示)
- 依頼: インベントリ以外の UI (チェストなど) を開いているときに進捗のキーを押しても、進捗画面に切り替わらない。できる限り対応したい。
- 原因: `ScreenSwitch#fromInventory` が `InventoryScreen` / `CreativeModeInventoryScreen` だけを許可していた (Mixin 自体は `AbstractContainerScreen` 全体に掛かっており、判定で弾いていた)。
- 変更:
  - `ScreenSwitch#fromInventory` → `toAdvancements(Screen, KeyEvent)` に改名・一般化 (画面の種類の絞り込みを削除。対象は呼び出し側が選ぶ)。
  - `MixinAbstractContainerScreenSwitch`: 呼び出しを `toAdvancements` に変更 (注入そのものは変更なし)。これでコンテナ画面の全サブクラスが対象。
  - 新規 `MixinBookViewScreenSwitch`: `BookViewScreen#keyPressed` の RETURN (2 箇所、`require = 2`) で、キーが使われなかったときだけ判定。`LecternScreen` は `keyPressed` を上書きしていないので同じ経路で対象になる。
  - `WorldMapScreen#keyPressed`: ピン名欄にフォーカスがないときだけ `toAdvancements` を呼ぶ (自前の画面なので Mixin 不要)。
  - `mixins.neo.json` に `MixinBookViewScreenSwitch` を追加。翻訳 (`option.modmenu.screen_switch`) と README の文言を実態に合わせた。
- 26.3 のバイトコードで確認したこと (`javap`):
  - `AbstractContainerScreen#keyPressed` の末尾は `return false` (ホットバー・ピック・ドロップは処理しても false)。`AnvilScreen` / `AbstractRecipeBookScreen` / `CreativeModeInventoryScreen` は入力欄がキーを使うと true を返して先に抜けるので、**入力中に L が切り替えを起こさない**。いずれも最後は `AbstractContainerScreen#keyPressed` を呼ぶので、RETURN の注入に届く。
  - `AbstractContainerScreen#removed` は `menu.removed(player)` だけで、サーバーへの閉じる通知は `onClose` → `LocalPlayer#closeContainer` (`ServerboundContainerClosePacket` を送り、`clientSideCloseContainer` で `setScreen(null)`)。そのため、メニューを持つ画面 (`MenuAccess`) からの切り替えでは `closeContainer()` を呼んでから進捗画面を開く。
  - `Gui#setScreen` は旧画面の `removed()` を 1 回呼ぶ。`closeContainer` 内の `setScreen(null)` で 1 回走り、続く `setScreen(進捗)` では旧画面が null なので二重に呼ばれない。`WorldMapScreen#removed` はカメラを元に戻すので、切り替えでも後始末される。
- **既存の挙動の変更 (要確認)**: これまで、インベントリ → 進捗は `setScreen` だけでサーバーへ閉じる通知を送っていなかった。バニラの E で閉じる動作に揃えて `closeContainer()` を呼ぶようにした (2x2 の作業枠にアイテムを置いたまま切り替えたとき、サーバーとクライアントがずれるのを防ぐため)。副作用として、切り替えの瞬間にカーソルが画面中央に戻る可能性がある (`setScreen(null)` → `setScreen(進捗)` の間にマウスの掴み直しが入るため。バニラで「閉じてから L」を押したときと同じ)。気になる場合は、`InventoryScreen` / クリエイティブだけ従来どおり `closeContainer()` を呼ばない形に戻せる。
- 対象外にした画面 (判断): チャット・看板・本の編集 / 署名・コマンドブロック・ストラクチャーブロック・ジグソーなどの文字入力画面 (L を文字として使うため)、ポーズ・オプション画面 (キー割り当ての操作中に誤爆するため)。
- 検証: JDK 25 + Minecraft 26.3 jar に対し、変更したファイル (`ScreenSwitch` / `WorldMapScreen` / 3 つの切り替え Mixin) を `javac -XDshould-stop.ifError=FLOW` で型検査し、**自分のコードに由来するエラー 0** (陰性対照で、わざと壊したコードがエラーになることを確認)。残ったエラー 2 件は環境側: `com.mojang.logging` (Mojang の logging ライブラリが jar に無い。`NeoData` の import) と、clone した Mixin ソースの `CompanionPluginError` の欠落。`tools/check_rules.py` は違反 0。
- **未確認 (要・実機)**: ビルド (`build.bat`) と起動 (Mixin の `require` 判定)、各画面 (チェスト・作業台・かまど・金床・取引・教卓・本・ワールドマップ) で L を押したときの切り替え、金床の名前欄・レシピ検索欄・クリエイティブ検索欄で L を打ったとき切り替わらないこと、マルチサーバーでチェストを開いたまま切り替えた後にサーバー側が閉じていること、カーソル位置の挙動。

## 設定画面の名称変更と「既定に戻す」で UI が重なる不具合の修正 (ユーザー指示)
- 名称: 設定画面のタイトル `modmenu.options` を「Mod Managerのオプション」→「NeoModsの設定」(`en_us` は "NeoMods Settings") に変更。キー名・他の文言は変更なし。
- 不具合の原因 (26.3 のバイトコードで確認): `OptionsSubScreen#layout` は `final` でコンストラクタで 1 回だけ作られ、`init()` は呼ばれるたびに `addTitle` / `addContents` / `addFooter` で layout に**追加**するだけで消さない。「既定に戻す」が `rebuildWidgets()` (`clearWidgets` → `init`) を呼んでいたため、古いタイトル・オプション一覧・フッターが layout に残ったまま新しいものが重なっていた。バニラのサブ画面は `rebuildWidgets()` を使わない。
- 修正 (`ModManagerOptionsScreen`): 既定値に戻したあと、`rebuildWidgets()` ではなく**新しい `ModManagerOptionsScreen` を開き直す** (`lastScreen` は引き継ぐ)。`asOptions()` は呼ぶたびに現在の設定値から項目を作るので、新しい画面は既定値で表示される。旧画面の `removed()` で保存される。副作用: 一覧のスクロール位置が先頭に戻る。
- 検証: 変更ファイルを JDK 25 + 26.3 jar で型検査、エラー 0。`check_rules.py` 違反 0。
- **未確認 (要・実機)**: 「既定に戻す」を押したときに重ならないこと・全項目が既定値の表示になること、複数回押しても崩れないこと、押した後に「完了」で前の画面へ戻ること、設定が保存されること。

## Mod 一覧の「表示順」(並べ替え) の機能とボタンを削除 (挙動変更・ユーザー指示)
- 依頼: 表示順を変える機能やボタンを削除。
- 削除したもの:
  - `ModsScreen` の「表示順」ボタン (`sortingButton`)。
  - 設定 `sorting` (`ModManagerConfig.SORTING` と `Sorting` enum)。設定画面 (NeoModsの設定) からも消える。翻訳 `option.modmenu.sorting` / `.ascending` / `.descending` (`en_us` / `ja_jp`)。
  - **連動して削除**: フィルターボタン (漏斗アイコン)。開くと出るのが「表示順」ボタンだけで、残すと空のパネルを開くボタンになるため。`filtersButton` / `filterOptionsShown` / `keepFilterOptionsShown` / `filtersX` / `filtersWidth` / `searchRowWidth` / `setFilterOptionsShown` / `updateFiltersX`、スプライト `textures/gui/sprites/filters/` (3 枚)、`ModManagerScreenTexts.TOGGLE_FILTER_OPTIONS`、翻訳 `modmenu.toggleFilterOptions`。
- 変えたもの:
  - 並び順は固定: 上から Java・Minecraft・NeoMods、それ以外は表示名 (翻訳後) の昇順 (`ModOrdering#comparator()` は引数なし)。従来の既定 (昇順) と同じ。
  - 検索欄は、フィルターボタンの分 (22px) の余白が不要になったので、その分だけ広がり、中央に置く。
  - 件数表示は、従来の「フィルターパネルが閉じているとき」の描画だけを残した (開いているときの分岐と幅計算を削除)。閉じているときの見た目・位置は従来どおり。
- 既存の `options.txt` に残る `neo_sorting` の行は読まれず、次に `Options#save` が走ると消える。降順にしていた人は昇順になる。
- 検証: JDK 25 + 26.3 jar で全ソースを型検査。**編集したファイルに由来するエラー 0**。残りのエラーは環境側 (joml・`com.mojang.logging`・LWJGL SDL・netty が classpath に無い、Fabric Loader の依存)。`check_rules.py` 違反 0。
- 作業中に起きたこと (報告): `ModsScreen` を一度、置換位置の誤りで壊した (件数表示のブロックが重複)。検出して元のファイルから復元し、やり直した。最終版は 552 行で重複なし。
- **未確認 (要・実機)**: ビルドと起動、Mods 画面の見た目 (検索欄の幅と位置、件数表示の位置、狭い画面での 2 行表示)、設定画面に「表示順」が出ないこと、漏斗ボタンが消えていること、一覧が Java → Minecraft → NeoMods → 昇順で並ぶこと、`config_mode` ON のときの表示。

## Mods アイコンと @ キーの行き先を NeoMods の設定画面に変更 (ユーザー指示)
- 依頼: ゲームメニューにある Mod Manager のアイコンをタイトル画面にも足し、NeoMods の設定画面を開くようにする。@ キーで開く画面も設定画面にする。
- 変更:
  - `SmallModManagerButtonWidget#createModsIconButton`: 押下で `ModsScreen` ではなく `ModManagerOptionsScreen(parent)` を開く。ラベルとツールチップは `modmenu.options` (「NeoModsの設定」)。タイトル・ポーズ共通の関数なので、**ポーズ画面のアイコンも設定画面を開く**。
  - `MixinTitleScreen`: アイコンを足す条件から「Mods ボタンの形式が ICON」を外した。`modify_title_screen` が ON なら、どの形式でもタイトル画面の言語ボタン列にアイコンが出る (`require` や注入先は変更なし)。
  - `ModManagerEventHandler`: @ キー (`key.modmenu.open_menu`) の行き先を `ModManagerOptionsScreen` に変更。
  - 翻訳: `key.modmenu.open_menu` の表示を「NeoModsの設定を開く」(en_us は "Open NeoMods Settings") に変更。`AGENTS.md` P-43・`README.md` の記述も揃えた。
- 影響 (要判断): Mod 一覧 (`ModsScreen`) へ入る口は、タイトル画面の「Mods」ボタン (形式が「アイコン」以外) だけになった。形式を「アイコン」にすると、タイトルからも、ポーズ画面・@ キーからも Mod 一覧を開けない。
- 確認: `tools/check_rules.py` 違反 0。
- **未確認 (要・実機 / ビルド)**: この環境の JDK は 21 で javac も無く、コンパイルしていない (変更は既存クラスの参照の差し替えのみ)。タイトル画面の言語ボタン列にアイコンが追加されたときの並びと位置 (Mods ボタンが「Realms を置換」のときも含む)、アイコンのクリックで設定画面が開き「完了」で戻ること、@ キーで設定画面が開くこと、ポーズ画面のアイコンの動作。

## Mod 一覧の説明枠に NeoMods の機能一覧を追加 (ユーザー指示)
- 翻訳キー `modmenu.descriptionTranslation.neo` (複数行の機能一覧) と `modmenu.summaryTranslation.neo` (一覧の行に出す短い 1 行) を `en_us` / `ja_jp` に追加。説明枠は改行で折り返される (Java の説明も同じく改行を使っている)。
- `ModTexts#alwaysTranslated` に `neo` を追加: 設定「Mod概要の翻訳」が OFF でも、この説明を出す (Minecraft / Java と同じ扱い)。名前は `nameTranslation.neo` が無いので従来どおり。
- `fabric.mod.json` の `description` は変更なし (翻訳が引けないときの代替)。
- **未確認 (要・実機)**: 説明枠での改行・折り返しの見た目、一覧の行の 1 行表示が長すぎて切れないか、`check_rules.py` の翻訳キー両言語一致 (下記)。

## サウンドブラウザを追加 (新機能・ユーザー指示)
- 依頼: クリエイティブインベントリに、読み込み済みの音を一覧してピッチなどを変えて試聴でき、`/playsound` としてコピーもできる UI。
- ユーザーの選択: 置き場所は**クリエイティブ画面の右側面に縦向きのタブ** (バニラの 14 枠はすべて埋まっており、Fabric API のページ送りは P-51 で使えないため)、既定 **ON** (P-41 に追加)、名前空間・階層のツリー + 検索、ピッチ・音量・カテゴリ指定の試聴、お気に入り (`options.txt` の `neo_sound_favorites`)、対象は `@s`、ピッチの範囲は `/playsound` と同じ 0〜2、先頭の `/` なしのコピーも用意。
- 実装: 新規パッケージ `neo.sound` (`src/neo`): `SoundBrowser` (タブの追加・コマンド文字列)、`SoundBrowserScreen` (画面)、`SoundTree` (木の構築と絞り込み)。タブは既存の `NeoClientEvents.registerAfterScreenInit` + `ScreenWidgets` で足すので **Mixin の追加なし**。設定 `ModManagerConfig.SOUND_BROWSER` (既定 ON) と `SOUND_FAVORITES` (ファイル専用)、翻訳 (`en_us` / `ja_jp`)、`NeoCore#initClient` で初期化、`check_rules.py` の既定値表、README、AGENTS.md P-41。
- 判断: 一覧はクリエイティブ画面の中 (195x136) に収まらないため、タブを押すと**専用の画面**を開く (右側面に同じタブを選択状態で出し、押すとクリエイティブ画面に戻る。E キーで閉じる)。新しい音の再生時は前の音を止める。ポーズしない画面。
- 26.3 のバイトコードで確認したこと: クリエイティブのタブ配置 (上下 2 段 × 0〜6 列、空き無し)、`CreativeModeInventoryScreen` の大きさ 195x136 と `leftPos = (width - imageWidth) / 2`、`SoundManager#getAvailableSounds` / `play` / `stop(SoundInstance)`、`SimpleSoundInstance` の 12 引数コンストラクタ、`SoundEngine#calculatePitch` の `clamp(0.5, 2.0)`、`PlaySoundCommand` の pitch 引数 `floatArg(0, 2)`、`Gui#setScreen`、`KeyboardHandler#setClipboard`、`AbstractSliderButton` / `CycleButton.Builder` / `EditBox` / `AbstractButton` の各メソッド。
- 検証: JDK 25 + 26.3 jar + Guava 等 (apt) + Brigadier / DFU のソース (`git clone --depth 1`) で、`neo.sound` の 3 クラスと `ModManagerConfig` を型検査し**エラー 0** (陰性対照で、わざと壊した呼び出しがエラーになることを確認)。`NeoCore` は Fabric Loader のソースを入れていないため全体は通らないが、`NeoCore` と `neo.sound` に由来するエラーは 0。`check_rules.py` 違反 0。
- **未確認 (要・実機 / ビルド)**: `build.bat` でのビルド、タブの位置と見た目 (状態効果の表示と重ならないか、`leftPos` を式で求めているのでずれないか)、タブのクリック、一覧・展開・検索・お気に入りの保存と再起動後の復元、再生・停止・ピッチ / 音量 / カテゴリの反映、コピーの内容、クリエイティブ画面へ戻ったときの状態 (同じ画面インスタンスを開き直している)、狭い画面 / GUI スケール大での配置、音の数が多い環境での描画負荷。

## サウンドブラウザの見た目をバニラ調に整え、字幕を表示 (ユーザー指示)
- 依頼: 聴覚補助機能の音声の説明 (字幕) のリソースを活かし、バニラの雰囲気を壊さないおしゃれな UI に。
- 字幕: `SoundManager#getSoundEvent(id).getSubtitle()` (`WeighedSoundEvents`、`sounds.json` の `subtitle`) を画面を開くときに引き、一覧の各行の右に表示 (入らないときは「…」で切る)。検索は ID と字幕 (現在の言語の文字列) の両方に一致。選択中のカードは字幕を大きく、ID をその下に表示。
- 見た目 (すべてバニラのスプライト・色。独自テクスチャは足していない): 枠 `popup/background` (9 スライス)、一覧はスロットと同じくぼみ (#373737 / #8B8B8B / 白)、選択行はバニラの一覧と同じ白枠 + 黒地、ホバーはスロットと同じ半透明の白、スクロールバー `container/creative_inventory/scroller` (ドラッグ可)、選択中の音のカード `toast/now_playing` (再生中は `icon/music_notes` のアニメーション、止まっているときは音符ブロック)、側面タブはレシピ本のタブ `recipe_book/tab*` (35x27) を左右反転し、アイコン位置・選択時の 2px のずれもバニラの `RecipeBookTabButton` に合わせた。カテゴリのボタンはバニラの翻訳 (`soundCategory.*`) で表示。件数表示を「表示中 / 読み込み済み」に変更。
- 26.3 で確認: 各スプライトの実在と大きさ・9 スライスの設定 (mcmeta)、`WeighedSoundEvents#getSubtitle`、`RecipeBookTabButton#extractContents` / `extractIcon` の座標、`GuiGraphicsExtractor#pose()` (`Matrix3x2fStack`) と `blitSprite`、`Font#plainSubstrByWidth`、`SoundManager#isActive`、`SoundSource#getName` と `soundCategory.*` の翻訳キーの一致。
- 検証: 型検査に JOML のソース (`git clone --depth 1`、注釈 `MagicConstant` だけ自作スタブ) を追加。`neo.sound` 由来のエラー 0 (残り 88 件はすべて JOML 内部の `JvmciCode` / `MemUtil` が JDK 内部 API を使う分で環境側)。`check_rules.py` 違反 0。
- **未確認 (要・実機)**: 反転したタブの見た目と本体の縁とのつながり、ポップアップ枠・トースト枠の 9 スライスの見え方、字幕の色の読みやすさ、音符のアニメーション、スクロールバーのドラッグ、日本語の字幕の切れ方。

## サウンドブラウザの一覧性を改善 (ユーザー指示)
- 依頼: 音は 2000 件近いのに一覧が 6 行ほどしか出ない (GUI 座標で約 427x240 の画面)。
- 原因: 画面の大きさを 360x240 前後に制限し、一覧の下にカード・スライダー・ボタン 3 段を積んでいた。
- 変更 (`SoundBrowserScreen#init`):
  - 画面いっぱいを使う (左右は側面タブの分だけ空ける)。行の高さ 12 → 11。
  - 内側の幅が 300 以上なら **2 列**: 左 = 一覧 (検索欄の下から画面の下端まで)、右 = 操作 (カード / ピッチ / 音量 / カテゴリ / 再生・停止 / コピー 2 種を縦に)。
  - それより狭いときは、カードを出さず選択中の音を一覧の下に 1 行で出し、操作を 2 行 (ピッチ・音量・カテゴリ / 再生・停止・コピー 2 種) にまとめる。
  - 「お気に入り」ボタンを一覧の列に収めるため短くした (「★ お気に入りのみ」→「★ お気に入り」)。
- 見積もり (GUI 座標): 427x240 で 6 行 → **16 行**、480x270 で 19 行、640x360 で 27 行、狭い画面 (320x240) で 11 行。
- 検証: `neo.sound` 由来のエラー 0、`check_rules.py` 違反 0。
- **未確認 (要・実機)**: 2 列のときの一覧の幅 (427 幅で約 185) で字幕がどこまで読めるか、狭い画面の 4 分割ボタンの文字の収まり (入らない文字はバニラのボタンがスクロール表示する)。

## AFK シネマティックを追加 (新機能・ユーザー指示)
- 依頼: Ji AFK Cinematic 2.3.1 (MIT) の機能を独自実装で取り込む。**音楽とキーバインドは不要**。既定で **1 分放置で ON**。
- 実装: 新規パッケージ `neo.cinematic` (`src/neo`): `AfkCinematic` (放置の数え上げ・開始/終了・毎フレームのカメラ計算)、`CinematicShots` (12 種のカットと 1 巡の計画)、`CinematicLetterbox` (黒帯)。Mixin 4 本 (すべて `require = 1`): `MixinCameraCinematic` (`Camera#alignWithEntity` の TAIL で位置・向きを上書き)、`MixinHudCinematic` (`Hud#extractRenderState` の TAIL で黒帯とチャットを描く)、`MixinKeyboardHandlerCinematic` (`keyPress`)、`MixinMouseHandlerCinematic` (`onMove` / `onButton` / `onScroll`)。設定 `ModManagerConfig.AFK_CINEMATIC` (既定 **ON**)、翻訳 (`en_us` / `ja_jp`)、`NeoCore#initClient`、`mixins.neo.json`、`check_rules.py` の既定値表、README、AGENTS.md P-41 (10 → 11 個)。
- 取り込まなかったもの: 音楽 (バニラ音楽の差し替え・ローカル音楽・サードパーティ曲)、キー割り当て、Ji 側の設定画面 (設定は Mod Manager の ON/OFF 1 つに集約 / P-40・P-43)、Smooth F5 互換。
- 判断 (**ユーザーの判断が要る点**): (1) 放置時間は **1 分の固定値** (設定項目にしていない。変えたい場合は `AfkCinematic.IDLE_TICKS` か、列挙型の設定を足す)。(2) Ji にあった周回数の上限 (既定 3 周で終了) は付けず、**操作かダメージまで続ける**。(3) 演出の細かい設定 (カット長 10 秒・黒帯・ダメージ時の挙動) は固定 (ダメージで止まり、次の操作まで再開しない = Ji の既定の「キャンセル」相当)。(4) HUD は `Hud#toggle` (F1 と同じ) で隠し、チャットだけ描き直す。始める前から F1 で隠していた場合は、終わっても隠したまま。(5) 視点は三人称 (背面) に切り替えて終了時に戻す (自分のモデルを映すため)。(6) 画面 (インベントリ・チャット等) を開いている間は放置と見なさない。(7) カットの種類・数・動きは独自設計 (プレイヤー中心 3 : 景色中心 7 の比率だけ Ji の既定に近づけた)。
- 26.3 のバイトコード (ASM でメンバーを読んだ) で確認したこと: `Camera#alignWithEntity (F)V` (private。`Camera#update` が呼んだ後に視錐台を作る)、`Camera#setRotation (FF)V` / `setPosition (Lnet/minecraft/world/phys/Vec3;)V` (protected)、`Hud#extractRenderState (GuiGraphicsExtractor, DeltaTracker)V` (単一)、`Hud#extractChat` (private)、`Hud#toggle` / `isHidden`、`Gui#hud` (public final)、`Gui#screen()`、`KeyboardHandler#keyPress (JILKeyEvent;)V`、`MouseHandler#onMove (JDDDD)V` / `onButton (J MouseButtonInfo I)V` / `onScroll (JDD)V` (いずれも 1 つだけ)、`GuiGraphicsExtractor#fill(IIIII)` / `guiWidth` / `guiHeight`、`Options#setCameraType`、`ClipContext` のコンストラクタ。
- 検証: JDK 25.0.4 + 26.3 jar + Guava 等 (apt) + MixinExtras jar + Mixin / Fabric Loader / Brigadier / DFU / JOML のソース (`git clone --depth 1`) で、`neo.cinematic` の 3 クラス・Mixin 4 本・`ModManagerConfig` を `-XDshould-stop.ifError=FLOW` で型検査し**エラー 0** (Mixin のソースに足りない `CompanionPluginError` だけ環境用の空スタブを置いた。陰性対照で、存在しない `Hud` / `Vec3` / `Gui` のメソッド 3 件がエラーになることを確認)。`check_rules.py` 違反 0。
- **未確認 (要・実機 / ビルド)**: `build.bat` でのビルド、Mixin が実際に適用されること (起動時の失敗の有無)、1 分後に始まること、各カットの見た目 (向き・距離・壁での寄り方・景色が遠すぎないか)、黒帯の出入りと文字・チャットの重なり、`Hud#toggle` で隠れる範囲 (F3 や通知は残る)、操作・ダメージ・死亡・画面を開く・ワールドを離れるでの終了と視点・HUD の復元、F5 / F1 を押して終わらせたときの挙動 (その押下はバニラの処理も走る)、FreeLook・ズームとの併用、マルチでの動作。

## AFK シネマティック: 釣り中は浮きが見える範囲にする (ユーザー指示)
- 依頼: 釣り中もシネマティックにするが、浮きが見える程度にする。
- 実装: `AfkCinematic#keepBobberInView`。`Player#fishing` (`FishingHook`、public) が出ている間、カットのカメラ位置を、見る点 = プレイヤーの体と浮きの中間、距離 = 中間から「浮きまでの水平距離 × 0.75 + 3」以上・+6 以下に直す。浮きが無いときはフィールドを 1 回見るだけ。開始・終了の条件は変えていない (釣り中でも始まる。ウキの引きはダメージではないので止まらない)。
- 確認: `Player#fishing` のバイトコード (public、型 `FishingHook`)。型検査エラー 0、`check_rules.py` 違反 0。
- **未確認 (要・実機)**: 浮きが実際に画面に収まるか (距離の係数 0.75 と +6 の妥当性。浮きが遠いときの見え方)、浮きを引き上げた瞬間のカメラの跳び、釣り竿を手に持ったままのときの見た目、バニラの音楽選択の扱い (未実装。ユーザーの回答待ち)。

## 全体の整理 (ユーザー指示: 重複の除去・命名・コメントなど小さな整理のみ。挙動は変えない / P-70)
- 範囲: プロジェクト全体 (`src/neo` + `src/main`、144 → 145 クラス。Forge は対象外)。大きなクラスの分割はしていない。
- 実施:
  1. **視点切り替えの重複を除去**: `FreeLookState` と `AfkCinematic` に同じ `setCameraType` があったので、`neo.util.CameraTypeSwitch#set` に 1 本化。`player == null` のときだけ後処理を飛ばす点は、`FreeLookState` の呼び出しがすべてワールド内 (プレイヤーあり) なので、実質同じ挙動。
  2. **`HeldMapView` の升目の範囲の計算の重複を除去**: `composeColors` と `collectTileDecorations` に同じ 5 行があったので、`CellRange.of(view)` に 1 本化 (純粋な計算で、式は同じ)。
  3. **未使用の import を削除**: `DrawingUtil` の 4 件 (`ModManagerConfig` / `Mod` / `Mth` / `Random`)。
  4. **コメントアウトされた旧コードを削除**: `ModListEntry` (ツールチップの 4 行)、`ModListWidget` (`super.ensureVisible` の 1 行)。
  5. **未使用の公開メソッドを削除**: `ModListWidget#reloadFilters` (どこからも呼ばれていない)。
  6. **命名の統一**: `GuiGraphicsExtractor` の引数名 `drawContext` (Fabric の旧型名 `DrawContext` の名残) を `graphics` に変更 (8 ファイル・72 か所。新しい側のコードが使う名前に合わせた)。
- 確認できたこと: JDK 25.0.4 + 26.3 jar + 依存ソースで全 145 クラスを `-Xlint:all -XDshould-stop.ifError=FLOW` で型検査し、**プロジェクト側のエラー 0・警告 0** (整理前と同じ)。依存ソース側のエラー 155 件も整理前と同数 (環境用に取得したソースの不足で、プロジェクトとは無関係)。`check_rules.py` 違反 0。静的な走査 (未使用 import / 未使用 private / 同一メソッド本体 / 6 行以上の重複ブロック / コメントアウトされたコード) の残りは下記だけ。
- 見送り (**ユーザーの判断が要る点**。挙動が変わるか、変わらないと言い切れないため適用していない / P-70):
  - `SoundBrowserScreen#clip` と `WorldMapScreen#clip` は同じ目的 (長い文字列を「…」で切り詰める) だが、実装が違う (前者は `Font#plainSubstrByWidth`、後者は 1 文字ずつ削るループ)。書式コードを含む文字列などで結果が変わりうるので、統合していない。統合するならどちらかに揃える (見た目が変わりうる)。
  - `FabricMod#getContainer` はどこからも呼ばれていないが、Fabric アダプタの公開アクセサなので残した。
  - `MixinTitleScreen` / `MixinPauseScreen` の「未使用に見える private」は、Mixin が注入するハンドラなので対象外。
  - 700 行級のクラス (`WorldMapScreen` 693 行、`ModsScreen` 551 行、`SoundBrowserScreen` 522 行) の分割。
- **未確認 (要・ユーザーの `build.bat` と実機)**: ビルド、`HeldMapView` の地図の窓の表示 (升目の範囲)、自由視点 (F5 長押し) の出入りで視点が元に戻ること、Mod 一覧・説明欄の描画 (引数名の変更のみ)。

## AFK シネマティック: 放置判定を 30 秒に、黒帯を細く (ユーザー指示)
- 依頼: 放置判定を 30 秒にする。シネマ風の黒帯が太すぎる気がする。
- 実装: `AfkCinematic.IDLE_TICKS` を 60 * 20 → **30 * 20** (600 tick)。`CinematicLetterbox.ASPECT` を 2.39 → **2.0** (黒帯の高さは 16:9 の画面で上下それぞれ画面の高さの約 12.9% → 約 5.6%。幅 / 高さ = 2.0 より横長の画面では帯が出ない)。README・設定の説明・翻訳 (`en_us` / `ja_jp`)・AGENTS.md P-41 の「1 分」「2.39:1」を揃えた。これより前の記述 (上の「AFK シネマティックを追加」) は当時のまま残している。
- **ユーザーの判断が要る点**: 黒帯の太さは私が **2.0:1** を選んだ (ユーザーの指定は「太すぎる」のみ)。もっと細く / 太くしたいときは `CinematicLetterbox.ASPECT` を変える (2.2 で約 9.6%、2.4 で約 13%、1.9 で約 4.2%)。
- 確認: 型検査エラー 0、`check_rules.py` 違反 0。**未確認 (要・実機)**: 30 秒で始まること、黒帯の見た目の太さ。

## AFK シネマティック: F1 で GUI の有り無しを切り替えられるようにする (ユーザー指示)
- 依頼: シネマティック中に F1 で、GUI 有り / 無しのシネマティックを切り替えられるようにする。
- 実装: `AfkCinematic#onKeyPress` を追加し、`MixinKeyboardHandlerCinematic` から呼ぶ (これまでは `onActivity` 直呼び)。**シネマティック中**に「GUI の表示を切り替えるキー」(`Options#keyToggleGui`。既定 F1。キー割り当てを変えていても追従) を押す / 離す操作は、放置の解除と見なさない。切り替え自体はバニラのまま (`Gui#handleKeybinds` が `keyToggleGui.consumeClick()` → `Hud#toggle`) なので、F1 を押すと GUI が出て、もう一度押すと隠れ、シネマティックは続く。シネマティックでないときの F1 は、これまでどおり操作 (放置の解除) として数える。
- 付随して直したこと: (1) 終わるときは、「始める前の表示状態」に戻す (`hud.isHidden() != hudWasHidden` なら toggle。以前は「隠していなかった場合に隠し直す」だけだった)。(2) `showsChat` は、HUD が隠れている間だけ true (GUI を出したあとはバニラがチャットも描くので、二重に描かない)。(3) 黒帯を `Hud#extractRenderState` の**先頭**に移した (HUD の下)。GUI を出したとき、ホットバーなどが黒帯に隠れない。Mixin は先頭・末尾の 2 注入 (どちらも `require = 1`、同じメソッドで確認済み)。
- 確認: `Gui#handleKeybinds` が `keyToggleGui.consumeClick()` → `hud.toggle()` (逆コンパイルで確認)、`KeyMapping#matches(KeyEvent)`、`Options#keyToggleGui`。型検査エラー 0 (環境側の依存ソースのエラー 155 件は変わらず)、`check_rules.py` 違反 0。
- **未確認 (要・実機)**: シネマティック中の F1 で実際に GUI が出入りしてシネマティックが続くこと、GUI を出した状態での黒帯とホットバー・チャットの重なり、GUI を出したまま操作 / ダメージで終わったときの表示状態の復元、F1 を既に押した状態 (始める前から隠していた) での挙動。

## AFK シネマティック: GUI なしでもう一度 F1 を押したらシネマティックに (ユーザー指示)
- 依頼: 「GUI なしの時にもう一度 F1 を押したらシネマモードに」。**私の解釈**: 通常のプレイ中、F1 で GUI を隠した状態でもう一度 F1 を押すと、バニラのように GUI を戻す代わりに、シネマティックを手動で始める。
- 実装: `neo.mixin.MixinGuiCinematic` (新規。`Gui#handleKeybinds` の `Hud#toggle` 呼び出し 1 か所を `@WrapOperation`、`require = 1`) が `AfkCinematic#startFromHiddenGui` を呼ぶ。始めたら元の `toggle` は呼ばない。**設定 OFF・すでにシネマティック中 (そのときの F1 は、先に追加した GUI の有り無し切り替え)・GUI が出ている (そのときの F1 はバニラどおり隠す)・画面を開いている・他の視点・死亡**のときは、`Operation#call` でバニラの切り替えをそのまま行う。`AfkCinematic#start` を `begin(…, fromHiddenGui)` に分けた。
- この始め方の終わり方: 操作 / ダメージなどで終わると、**GUI が出た通常の画面に戻る** (`hudWasHidden = false`)。AFK (放置) で始まった場合は、これまでどおり「始める前の表示状態」に戻る。手動で始めると、ダメージ後の「次の操作まで再開しない」状態も解除する。
- 確認: `Gui#handleKeybinds()V` の中の `Hud#toggle()V` 呼び出しが 1 か所 (`invokevirtual`) であることをバイトコードで確認。型検査エラー 0 (依存ソース側 155 件は変わらず)、`check_rules.py` 違反 0 (146 クラス)。
- **ユーザーの判断が要る点**: (1) この解釈でよいか。(2) **F1 の動きが変わる** (設定 ON が既定のため): F1 → GUI を隠す、F1 → シネマティック、(シネマティック中) F1 → GUI を出す / 隠す、(シネマティック中) F1 以外の操作 → 終了して GUI が出た通常の画面。つまり、**F1 で隠した GUI をバニラのように F1 で戻すことは、設定 ON ではできない** (戻すには、シネマティックを経由して別のキーで終わらせるか、設定を OFF にする)。スクリーンショット用に GUI を隠す使い方が多い場合は、別のキーにする / F1 の 3 段階をやめるなどの案がある。
- **未確認 (要・実機)**: 起動時の Mixin 適用 (新規の `MixinGuiCinematic`)、F1 の 3 段階の動き、手動で始めたシネマティックの終了後の表示 (GUI が出ること)、F1 を素早く連打したとき。

## AFK シネマティック: 黒帯を 2.1:1 に・シネマティック中の F1 で GUI を戻す (ユーザー指示)
- 依頼: 黒帯が少し細かった (2.0:1 → **2.1:1**)。シネマティック中の F1 で、GUI を戻す。
- 黒帯: `CinematicLetterbox.ASPECT` を 2.1 に (16:9 の画面で上下それぞれ高さの約 **7.7%**。2.39:1 は約 12.9%、2.0:1 は約 5.6%)。
- F1: `AfkCinematic#startFromHiddenGui` を `onToggleGui` に改め、`MixinGuiCinematic` から呼ぶ。**シネマティック中の F1 は、シネマティックを終えて GUI を戻す** (`hudWasHidden = false` にして `stop`。放置で始まった場合も。バニラの `Hud#toggle` は呼ばない)。「GUI を隠している状態の F1 → シネマティックを始める」はそのまま。これで F1 は「GUI 有り → 隠す → シネマティック → GUI 有り」と一巡する。**先に追加した「シネマティック中に F1 で GUI の有り無しを切り替え、シネマティックは続く」は、この指示で置き換えた** (私の解釈: 「GUI を戻す」= シネマティックを終えて通常の画面に戻る)。F1 は引き続き放置の解除としては数えない (数えると、解除 → そのあとのバニラの切り替えで GUI がまた隠れる)。
- 確認: 型検査エラー 0 (依存ソース側 155 件は変わらず)、`check_rules.py` 違反 0。
- **ユーザーの判断が要る点**: 「GUI を戻す」を「シネマティックを終えて GUI を戻す」と解釈した。シネマティックを続けたまま GUI だけ出したい (前の動き) なら、別のキーにするなどの案がある。黒帯の太さは 2.1 のまま、変えるなら `CinematicLetterbox.ASPECT` (2.2 で約 9.6%、2.0 で約 5.6%)。
- **未確認 (要・実機)**: シネマティック中の F1 で GUI が戻りシネマティックが終わること、そのあとの F1 (隠す) の動き、2.1:1 の黒帯の見た目。

## ビルドの出力先: jar を build.bat と同じ階層に・build ディレクトリを bin に (ユーザー指示)
- 依頼: jar が `build.bat` と同じ階層に出力されるようにする。`build` ディレクトリ名は `bin` に変更する。
- 変更: `build.ps1` — `$Out = 'build'` → **`'bin'`** (classes / res / nested / *.args などの途中の生成物)、`$Jar = "$Out/$Name.jar"` → **`"$Name.jar"`** (= `build.bat` と同じ階層の `NeoMods.jar`。`Set-Location $PSScriptRoot` 済みなので相対パスでよい)、先頭コメントも更新 (ASCII のまま、CRLF のまま。差分は 3 行だけで、元からある非 ASCII のバイト数は変わらない)。`build.sh` — 同様に `OUT=bin`、`JAR="$NAME.jar"`、`-sources.jar` もルートへ (`build.sh` はもともと実態と合っていない / 上の「build.sh が実態と不一致」。出力先だけ揃えた)。`.classpath` — Eclipse の出力先 `build/classes` → `bin/classes` (`build` ディレクトリが Eclipse によって作り直されないように)。README (ビルドの項) と AGENTS.md P-75 (zip に入れないもの) も更新。
- 挙動のメモ: `build.ps1` は、始めに `bin` を丸ごと削除してから作り直す (これまでの `build` と同じ)。jar はルートに出るので、この削除では消えず、`jar --create` が上書きする。`libs/` の jar だけを classpath に集めるので、ルートの `NeoMods.jar` が classpath に混ざることはない。`setup-eclipse.ps1` は、既存の `.classpath` があれば出力先をそのまま引き継ぐ (無いときの既定は `bin`)。
- 注意: **以前の `build/` ディレクトリは、手元に残る** (自動では消えない)。不要なら削除してよい。Eclipse を使う場合は、`.classpath` を読み直す (プロジェクトの再読み込み) と、出力が `bin/classes` に変わる。
- **未確認 (要・ユーザーの `build.bat`)**: エージェントの環境に PowerShell が無く、`build.ps1` は実行していない (差分の目視と、パス変数の使われ方の確認のみ)。`build.bat` の実行で、ルートに `NeoMods.jar` ができ、`bin/` に中間生成物ができること。`build.sh` も未実行。

## 翻訳の設定 2 つを削除 (バニラの言語設定に従う) (ユーザー指示)
- 依頼: 翻訳に関する設定が 2 つあるが、バニラの言語設定を参照すれば必要ないはず。
- 削除: `ModManagerConfig.TRANSLATE_NAMES` (`translate_names`、Mod 名の翻訳) と `TRANSLATE_DESCRIPTIONS` (`translate_descriptions`、Mod 概要の翻訳)。どちらも**既定 ON**だった。`en_us` / `ja_jp` の翻訳キー各 6 つ (`option.modmenu.translate_names` / `translate_descriptions` と、それぞれの `.true` / `.false`) も削除。
- `ModTexts` の変更: 翻訳するかは、**「いまの言語に翻訳キーがあるか」だけ**で決める (`Language.getInstance().has(key)`。キーが無ければ Mod 自身の名前・説明)。これまで「`minecraft` / `java` / `neo` は設定に関わらず常に翻訳」としていた `alwaysTranslated` は、全 Mod が常に翻訳対象になったので不要になり削除。
- 挙動: 既定どおり (ON) で使っていた場合は、**見た目は変わらない**。設定を OFF にしていた場合は、これからは常に翻訳される (OFF にする手段が無くなる)。`options.txt` に残っている `neo_translate_names` / `neo_translate_descriptions` の行は、読み込みでは登録済みの設定だけを見るので無視され、次の保存で消える (バニラは保存のとき自分の行だけを書き直し、この Mod が自分の行を足すため)。
- 確認: 型検査エラー 0 (依存ソース側 155 件は変わらず)、`check_rules.py` 違反 0 (146 クラス)。`en_us` / `ja_jp` はどちらも JSON として読め、`translate_` のキーは残っていない (`modmenu.bluesky` が `en_us` にだけあるのは、もとからの差)。P-41 の既定 ON の数 (11 個) は、この 2 つを数えていなかった (Mod Menu 本来の設定のため) ので変わらない。
- **未確認 (要・実機)**: 言語を英語 / 日本語に切り替えたときの Mod 名・概要の表示 (Mod Manager の一覧と詳細)、設定画面に「Names」「Descriptions」が出なくなっていること、古い `options.txt` (translate の行が残っているもの) での起動。

## タイトル画面に Discord アイコンを追加 (ユーザー指示)
- 依頼: タイトル画面のアイコンに Discord アイコンも追加する (リンク: https://discord.gg/nqx8esTwzS)。
- 実装: `SmallModManagerButtonWidget.createDiscordIconButton` (新規。20x20、Mods アイコンと同じ `SpriteIconButton.CenteredIcon`)、定数 `DISCORD_URL`。押すと `ConfirmLinkScreen.confirmLinkNow(parent, URI, false)` で**確認画面を経てから**開く (`trusted = false` = 外部サイトの扱い。ModsScreen の Mod の Web サイトと同じ)。`MixinTitleScreen`: アイコン数の加算を +1 → **+2**、`addModManagerIconWidget` で Mods アイコンの次 (`currentButton` を 1 つ進めて同じ `topPos`) に追加。Mixin は既存の注入のままで、新しい注入は増やしていない。翻訳 `neo.discord` (`en_us`「Join our Discord」/ `ja_jp`「Discord に参加」。ボタンのツールチップ兼ナレーション)。画像 `assets/neo/textures/gui/sprites/discord/{enabled,disabled,focused}.png` (20x20、LA、3 状態とも同一)。
- 表示の条件: Mods アイコンと同じ (`modify_title_screen` が ON = 既定 ON のとき)。**単独の ON/OFF 設定は付けていない** (P-73)。タイトル画面を変更しない設定 (OFF) のときは、どちらのアイコンも出ない (バニラどおり)。ポーズ画面には追加していない (依頼がタイトル画面のみ)。
- 画像について: 既存の Mods アイコンの形式 (白い図形+1 ピクセルずらした暗い影 (輝度 63)、図形は 20x20 の中) に合わせて、**ドットで描いた簡略版のマスクの形** (私が作成)。公式のロゴ画像ではない。公式のものに替えるなら、同名の PNG を差し替える (ロゴの利用条件は Discord 側のもの)。
- 確認: `ConfirmLinkScreen.confirmLinkNow(Screen, URI, boolean)` の署名 (26.3 の逆コンパイル)。型検査エラー 0 (依存ソース側 155 件は変わらず)、`check_rules.py` 違反 0。翻訳 JSON は 2 つとも読める。画像は 10 倍に拡大して目視した (ボタン色の上で、マスクの形に見える)。
- **未確認 (要・実機)**: アイコン列が 5 個 (フレンド・言語・アクセシビリティ・Mods・Discord) になったときの並びと幅、ボタンの見た目 (背景色・マウスを重ねた状態)、押したときの確認画面とブラウザでの起動、確認画面を閉じたあとタイトル画面に戻ること、Realms 通知アイコンなど他の表示との重なり、他の言語・画面サイズ。

## Discord アイコンを 64px・ブランド色に作り直す (ユーザーの指摘)
- 指摘: 「アイコンは 64px で正しい色を使えているか?」 → **使えていなかった** (前の画像は 20x20 の白+暗い影の 2 色で、ブランド色ではなかった)。
- 修正: `textures/gui/sprites/discord/{enabled,disabled,focused}.png` を **64x64 の RGBA** に作り直した (3 状態とも同一)。Discord のブランド色 **`#5865F2`** の角丸 (半径 14) の下地に、白いロゴ。ロゴの形は **Simple Icons 16.34.0** (npm、CC0) の `discord.svg` の path (現行のマーク)、色は同パッケージのデータにある Discord のブランド色 (出典 https://discord.com/branding)。`cairosvg` でレンダリング (角は透明、中央付近の下地の画素が `#5865F2` = (88, 101, 242) であることを確認)。
- 表示: `SmallModManagerButtonWidget#createDiscordIconButton` の描画サイズを 20x20 → **16x16** に (`CenteredIcon` は「ボタンの中央 − 描画幅 / 2」に置くので、上下左右 2px の余白で中央)。64 → 16 は整数比 (4:1)。GUI 倍率 4 でちょうど等倍、倍率 2・3 でも縮小される。
- 確認: 画像の寸法・モードと色の画素値、10 倍に拡大した見た目 (左) と、16px に縮めてボタン色の上に置いた見た目 (右。最悪のケースとして最近傍で縮めたので、ざらつきが出る)。型検査エラー 0 (依存ソース側 155 件は変わらず)、`check_rules.py` 違反 0。
- **ユーザーの判断が要る点**: ロゴの利用条件は Discord 側のもの (https://discord.com/branding)。ブランド色の下地を付けた形にした (灰色のボタンの上でも見える)。ロゴだけにしたい場合は下地を外せる。
- **未確認 (要・実機)**: 16px に縮めたときの見え方 (GUI 倍率 1・2・3・4。縮小のざらつき・エッジの滑らかさ)、ボタンの背景との重なり、アイコン列の並びと幅。

## アイコンをすべて 16px に揃える (ユーザー指示。前の「64px」の指摘に代わる)
- 依頼: すべて 16px に合わせたい。 → **この Mod のアイコン画像 17 枚をすべて 16x16 に** (オーバーレイのシートはセルを 16px に)。Discord は前の 64x64 をやめ、16x16 の手描きドットにした。
- 画像 (`assets/neo/`): 
  - ボタン用スプライト `textures/gui/sprites/{mods,configure}/*` (20x20): 絵は (2..17) に収まっていたので **16x16 に切り出しただけ** (画面上は 1 画素も変わらない)。
  - `discord/*` (64x64 → **16x16**): 手描きのドット。Discord のブランド色 `#5865F2` の角丸の下地 + 白いマーク (14 幅、耳 2 つ、目の穴、下の尾 2 つ)。アンチエイリアスしたレンダリング (16px) は輪郭がぼやけて他のドット絵と質感が合わなかったので、使わなかった。
  - `fabric.png` (32 → 16): 色を混ぜない縮小 (2x2 ブロックの最頻色。同数なら暗い方)、4 通りの位相のうち元との差が最小のもの。
  - `java_icon.png` (32 → 16): **手描きし直し** (縮小すると細い湯気の線が潰れたため)。
  - `minecraft_icon.png` (9 → 16): **自作の 16x16** (草のブロックの横から。**バニラの画素配置はコピーしていない**。配色だけ元に近づけた)。以前の 9px は 32 の枠に非整数倍で伸ばされて、ドットの太さが不揃いだった。
  - `textures/gui/mod_configuration.png` (256x256 → **16x32**): スライダーを手描きし直し (縮小すると黒い塊になったため。つまみは突き出す形)。`parent_mod.png` (256x256 → **32x32**): 三角の矢印 4 つ (32 → 16 の縮小)。
  - 変更なし: `icon.png` / `unknown_icon.png` / `unknown_parent.png` はもともと 16x16。
- コード: `SmallModManagerButtonWidget#createModsIconButton` の描画サイズ 20 → **16** (`CenteredIcon` が中央に置く)、`ModsScreen` の設定ボタンの描画サイズ 20 → **16**、`ModListEntry` / `ParentEntry` の行に重ねる画像を、16px のセル (**`OVERLAY_CELL_SIZE = 16`**) から `iconSize` (32) の枠へ描く **13 引数の `blit`** (描画サイズ・元の領域・シートの大きさを別々に渡せる) に変更。`ParentEntry` の以前の呼び出し (`幅 = iconSize + xOffset` という元からの癖) は整理した (描かれる位置・大きさは同じ)。
- 確認: 26.3 の `DynamicTexture` が**最近傍 (NEAREST)** で補間する (逆コンパイル) → 16 の絵を 32 の枠に 2 倍にしても、にじまず、くっきりする。`IconHandler` が要求するのは「正方形」だけで、サイズは問わない。26.3 の `GuiGraphicsExtractor#blit` の 13 引数版 (逆コンパイル)。全アイコンが 16x16 であることを `assert` で確認。型検査エラー 0 (依存ソース側 155 件は変わらず)、`check_rules.py` 違反 0。変更前後の見た目を並べて目視した。
- **ユーザーの判断が要る点**: (1) 他の Mod が持ってくる 32px などのアイコンは、この Mod の管轄外なので、そのまま。(2) ポーズ画面側や、描画サイズ 20 のまま残るボタンが無いかは、コードの検索 (`SpriteIconButton` の使用 2 か所) でのみ確認した。(3) 湯気・スライダーなどは私の手描きなので、好みで差し替えてほしい。
- **未確認 (要・実機)**: ゲーム内でのスライダー・矢印・Discord・Java・Minecraft の見え方 (特に 32 の枠への 2 倍の拡大と、背景色との重なり)、行にマウスを重ねたときの設定アイコンと親 Mod の開閉アイコンの位置・大きさ (`blit` の引数の変更が、元と同じ表示になること)、アイコン列の並び。

## Minecraft のアイコンは、バニラのアセットを参照する (ユーザーの質問: できないのか)
- 質問: 「マインクラフトはバニラアセットを参照するようにできないのか」 → **できる**ので変更した。
- 参照先: バニラの標準リソースパック (id `vanilla`) のルートにある **`pack.png`** (128x128、標準の草のブロック。リソースパック選択画面の「Default」のアイコンと同じ)。26.3 の jar に実在することを確認した。読み方はバニラの `PackSelectionScreen#loadPackIcon` と同じ手順 (`Pack#openMetadata` → `PackMetadataResources#getRootResource("pack.png")` → `NativeImage.read` → `DynamicTexture`)。
- 実装: `IconHandler#createVanillaPackIcon` (新規。キャッシュを持ち、`close` で解放)。`FabricMod#getIcon` の `minecraft` の分岐と、`ForgeMod#getIcon` (`src/forge`。**Forge 側はコンパイルできる環境が無く未検査**) から呼ぶ。読めない / 正方形でないときは `null` → 既存の処理どおり「不明」のアイコン (`unknown_icon.png`) になる。同梱の `assets/neo/minecraft_icon.png` は**削除**した (この Mod は Minecraft の画像を持たなくなった)。上の「アイコンをすべて 16px に揃える」の `minecraft_icon.png` の項 (自作の 16x16) は、この変更で置き換えた。
- 確認: `pack.png` が jar のルートにある (128x128、RGB)。`Minecraft#getResourcePackRepository()`、`PackRepository#getPack(String)`、`Pack#openMetadata()`、`PackMetadataResources#getRootResource(String...)` (26.3 のバイトコード / 逆コンパイル)。型検査エラー 0 (依存ソース側 155 件は変わらず)、`check_rules.py` 違反 0。128px を 64 / 32 画素に最近傍で縮めた見た目を目視した。
- **ユーザーの判断が要る点**: (1) 「すべて 16px」の例外になる (Minecraft だけ 128x128 の絵。32 の枠に縮めて出す。最近傍なので、小さい GUI 倍率ではざらつく)。(2) 別のバニラの絵が良ければ差し替えられる (例: 草のブロックの横の面 `textures/block/grass_block_side.png` は 16x16 だが、草の部分が灰色で、色は実行時に付くため、そのままでは緑にならない)。(3) 「不明」のアイコン (`unknown_icon.png`) も、バニラの `textures/misc/unknown_pack.png` に替えられる (今回は依頼外なのでしていない)。
- **未確認 (要・実機)**: Minecraft のアイコンが実際に出ること (Fabric で `vanilla` のパックが取れること)、見た目 (縮小時のざらつき)、リソースパックを替えたとき / 再読み込みしたときの挙動、Forge 版。

## 設定ボタンを歯車風にする (ユーザー指示)
- 依頼: 歯車風の設定ボタンにして。
- 変更 (画像のみ。コードは変更なし): 次の 2 か所の「スライダー」を、**8 枚の歯の歯車** (13x13 のドット絵。上下左右の歯 3 幅 + 斜めの歯 2x2、中央に 3x3 の穴) に置き換えた。(1) `textures/gui/sprites/configure/{enabled,focused,disabled}.png` (16x16): Mod 一覧の右の**設定ボタン** (`ModsScreen`。選んだ Mod の設定画面を開く)。通常 / フォーカスは**白い歯車+1 ピクセルずらした暗い影 (輝度 63)** (Mods アイコンと同じ流儀)、無効 (設定画面がエラーのとき) は**赤い歯車** (元の無効の赤 `#EE4040` と影 `#3B1010` を引き継いだ。以前あった「!」は、歯車の穴が 3x3 で入らないので無くした。エラーはツールチップで分かる)。(2) `textures/gui/mod_configuration.png` (16x32): 行のアイコンに重ねる**設定アイコン** (`ModListEntry`)。上段 (通常) は黒縁・灰色 (`#8B8B8B`)、下段 (アイコンにマウスを重ねたとき) は濃紺縁 (`#00073E`)・青灰色 (`#8892C9`)。縁は歯車の外周の画素 (上下左右が歯車の外の画素)。位置は 16x16 の中の (2,2)、ボタンの方は影を含めて (1,1)。
- 確認: 4 枚とも 16x16 / 16x32 の RGBA。見た目を 8 倍に拡大して目視した (4 種類とも歯車に読める)。オーバーレイを一覧のアイコン (Fabric) に重ねた見た目も確認した。`check_rules.py` 違反 0。コードは変更していない (型検査は前回のまま、エラー 0)。
- **ユーザーの判断が要る点**: (1) 「設定ボタン」を、**Mod 一覧の設定ボタンと、行に重ねる設定アイコン**と解釈した。**タイトル画面・ポーズ画面の Mods アイコン** (リストの形。押すと NeoMods の設定画面が開く) は**そのまま**にした。こちらも歯車にするかは、ご指示ください (`textures/gui/sprites/mods/*`)。(2) 歯の数・形は私の設計 (8 枚)。
- **未確認 (要・実機)**: ゲーム内の見え方 (GUI 倍率 1〜4、ボタンの背景色との重なり、無効の赤い歯車)。

## マップの広域スキャン (半径 64 / 128 / 512 チャンク) を追加 (ユーザー指示)
- 依頼: 「マップ機能でクリエイティブの時に大規模に周りの地図を読み込む機能 64チャンク→128チャンク→512チャンク」。確認の回答: ① 3 つの段階から選ぶ (ボタンで切り替え) ② シングルのみ・内蔵サーバーでチャンクを生成して地図にする ③ マップ画面のボタンで手動で開始。
- 実装 (新規 `neo.map.WideMapScan`、変更 `MapUpdater` / `WorldMapClient` / `WorldMapScreen`): 設定 `map_wide_scan` (**既定 OFF** / P-40・P-41。ユーザーは既定 ON を指示していない)。マップ画面の右の欄に、段階ボタン (64 → 128 → 512 を巡る) と開始 / 停止ボタン、進み具合の表示。**使える条件**: 設定 ON・`world_map` ON・内蔵サーバーあり・クリエイティブ。
- 仕組み (**バニラの仕組みを使う / P-30**): ① 地点の格子 (間隔は更新円の縁を除いた内側に収まる値。オーバーワールド 128 ブロック、天井のある次元は 40 ブロック) を、中心から外へ (輪の順、同じ輪では角度順) 並べる。② 地点ごとに `ServerChunkCache#addTicketAndLoadWithRadius(PLAYER_LOADING, 位置, 半径)` で、周囲 (オーバーワールドで半径 11 チャンク) を**読み込みのみ (ティックなし)** で保持して読み込む (未生成なら生成)。非ブロッキング (`getChunkFuture` はサーバースレッドから呼ぶとブロックするため使わない)。③ 読み込めた地点で、更新半径に掛かる全スケールの升目それぞれに、白紙の地図へバニラの `MapItem#update` を**サーバーのレベルに対して 16 回** (バニラは 1 回に 16 列に 1 列だけ更新するので、16 回で全列) 実行し、**縁 2 ピクセルを除いた内側のピクセルだけ**を作業中の升目へ写す (バニラは縁を市松で 1 つおきにしか更新しないため)。視点には、位置を地点に動かした `RemotePlayer` を使う (`ServerPlayer` は進捗・統計を登録してしまうため使わない)。④ 作業中の升目 (最大 1536 枚) を、クライアントスレッドへキューで渡し、`MapStore` へ「色が付いたピクセルだけ上書き」で写す (何度写しても同じ結果)。新しい升目は通常の蓄積と同じ `adopt` (ID 採番・保存)。
- 負荷の制御: サーバースレッドでの計算は 1 tick 12ms まで、同時に待つ地点は 4、クライアントの写しは 1 tick 4ms まで。写しが 3000 枚たまったら新しい地点を始めない。チケットは地点の処理が終わると外す (止める / ワールドを離れるときも必ず外す)。要求に失敗した地点は 3 回まで再試行。
- 確認: 26.3 のバイトコード / 逆コンパイルで、`ServerChunkCache#addTicketAndLoadWithRadius` (読み込み型・期限切れ型でないチケットだけ可。`PLAYER_LOADING` は読み込みのみ・期限なし)、`getChunkFuture` がサーバースレッドからだとブロックすること、`MapItem#update` の式 (視点のピクセル・更新半径・縁の市松・`step & 15` の列) と、私の再現が一致すること。**格子の間隔の網羅性**を、バニラの式そのままのシミュレーションで確認 (スケール 2〜4・格子の位相の総当たりとランダム 360 通りで、取りこぼしのピクセルは 0)。型検査エラー 0 (依存ソース側 155 件は変わらず。環境用に `com.mojang.authlib.GameProfile` の空スタブを使った)、`check_rules.py` 違反 0。
- **ユーザーの判断が要る点 / 注意**: (1) 範囲は**中心からの半径**と解釈した (四角形、周囲に最大 11 チャンクの余白)。(2) **512 チャンクは約 105 万チャンク**: 生成に数時間以上・ディスクに数 GB 以上 (未測定の概算)。地図ストアは地図をメモリに保持するので約 350MB (21,824 枚 x 16KB の計算値)。途中で止めても、ここまでの分は残る。(3) 「クリエイティブの時」に合わせて、**クリエイティブでのみ使える**ようにした (ご指示は「マップ画面のボタンで手動で開始」。サバイバルでも使いたければ、条件 `available()` から外せる)。(4) 内蔵サーバーのチャンクを生成するので、**ワールドのファイルが増える**。(5) スキャン中はサーバースレッドを 12ms/tick 使うので、遊ぶと少し重い。(6) マップ画面を閉じてもスキャンは続く (進み具合の表示はマップ画面のみ)。(7) 地図データはクライアント側の地図ストアに入る (バニラの地図アイテムとは別)。(8) Forge 版は未接続。
- **未確認 (要・実機)**: 起動して動くこと全般 — ① ボタンの出方・段階の切り替え・開始 / 停止、② チャンクが実際に生成されて地図に色が付くこと (特にマップ画面の表示、全スケール)、③ サーバースレッドの負荷 (ゲームの重さ)、④ 停止・ワールドを離れる・クリエイティブをやめたときの後始末 (チケットの解除、保存)、⑤ `PLAYER_LOADING` チケットの他の処理との干渉 (私は確認できていない)、⑥ 半径 64 / 128 の所要時間とメモリ、⑦ ネザー / エンドの動作。`MapItem#update` を ServerLevel に対して RemotePlayer で呼ぶ点も、実機で初めて確認できる。

## リンク (URL) を開く確認を省略し、既存の「クリックの確認を省略」に統合する (ユーザー指示)
- 依頼: URL を開くときもクリック数の無駄があるので、これもデフォルトで省略に統合。→ 既存の設定 `skip_command_confirmation` (既定 **ON**、`MixinUnattendedCommand`) に統合した (**設定項目は増やしていない**。設定名のキーは据え置き、表示名を「クリックの確認を省略 (コマンド・リンク)」/「Skip Click Confirmations (Commands & Links)」に変更。`en_us` の機能一覧の 1 行も更新)。
- 実装: `neo.mixin.MixinGuiLinkConfirmation` (新規。`Gui#setScreen(Screen)` の HEAD に `@Inject`、`cancellable`、`require = 1`)。渡された画面が `ConfirmLinkScreen` で、設定が ON なら、画面を切り替えず、`Blaze3D.openUri` でそのままブラウザで開く。URL は `neo.mixin.MixinConfirmLinkScreenAccessor` (新規。`ConfirmLinkScreen#url` (private final URI) の `@Accessor`) で読む。確認画面の「開く」は、元は `callback.accept(true)` → 開く + 親の画面へ `setScreen` (= 親の画面の作り直し) だったが、新しい方は**今の画面をそのまま残す** (クリックの処理の最中に親の画面を作り直さないため)。
- 対象: チャットのリンクのクリック (バニラの `Screen#clickUrlAction`。バニラの設定「リンクの確認」が ON のとき確認画面になる経路)、バニラの各画面のリンクのボタン (`ConfirmLinkScreen#confirmLinkNow` / `confirmLink`)、この Mod の Mod 一覧の Web サイト・ソース・問題報告のリンク、説明欄のリンク、タイトル画面の Discord アイコン。すべて `Gui#setScreen` を通る。`Minecraft#setScreenAndShow` はワールドの読み込み・切断などの別の流れで、確認画面には関係しない。
- OFF のとき: 何もしない (確認画面を出す)。ON のときも、`ConfirmLinkScreen` 以外の画面の切り替えでは `instanceof` だけ。
- 確認: `Gui#setScreen(Screen)` が 1 つだけ、`ConfirmLinkScreen#url` が `private final URI`、`Blaze3D#openUri(URI)` が static で存在する (26.3 のバイトコード)。`Screen#clickUrlAction` が `minecraft.gui.setScreen(new ConfirmLinkScreen(...))` を呼ぶ (逆コンパイル)。`mixins.neo.json` の差分は 2 行の追加のみ。型検査エラー 0 (依存ソース側 155 件は変わらず)、`check_rules.py` 違反 0 (149 クラス)。README・AGENTS.md P-80 (署名が必要なコマンドの確認は残す、リンクの確認は省略する)・Discord の説明を更新。
- **ユーザーの判断が要る点 / 注意**: (1) **安全面**: リンクの確認は、マルチプレイでチャットに貼られた**怪しい URL を、誤って開かない**ための保護。省略すると、クリックした瞬間にブラウザで開く。気になる場合は設定を OFF に (コマンドの確認の省略も一緒に OFF になる)。(2) コマンドとリンクを**別々に切り替えたい**場合は、設定を 2 つに分ける案がある (今回は依頼どおり統合)。(3) 「クリップボードにコピー」の選択肢も確認画面と一緒に無くなる (リンクはブラウザで開くだけ)。(4) バニラの設定「リンクの確認」 (`chatLinksPrompt`) が OFF のときは、もともと確認なしで開く (そのまま)。
- **未確認 (要・実機)**: 起動時の Mixin の適用 (新規の 2 本)、チャットのリンク・Mod 一覧のリンク・Discord アイコンを押したときにブラウザが開くこと、押したあとの画面 (そのまま残ること)、設定 OFF のときに確認画面が出ること、Realms などのリンク。

## 進捗画面: 見出し「進捗」と「完了」ボタンを消し、表示域をさらに広げる (ユーザー指示)
- 依頼: 進捗の表示領域を拡大。スクリーンショットで、上部の見出し「進捗」と下部の「完了」ボタンに delete の指示。
- 変更: (1) `MixinAdvancementsScreen` に `@WrapOperation` を 2 本追加 (`init` 内の `HeaderAndFooterLayout#addTitleHeader` と `#addToFooter(LayoutElement)`、どちらも `require = 1`)。`large_advancements` が ON のときだけ呼ばない (OFF のときは `Operation#call` でバニラのまま / P-31・P-60)。窓の左上の「進捗」(`extractWindow` が描く文字) は残した。(2) `AdvancementsLayout`: 窓サイズ = `max(バニラ, 画面 − 2×30)` に変更 (旧: `min(画面×0.85, 画面 − 2×33)`)。30 は、枠の外へ 28px はみ出すタブ (`AdvancementTabType` の ABOVE 28x32 / LEFT 32x28) を隠さない最小限の余白。「完了」ボタン用の 33 は不要になった。
- 確認 (26.3 の jar を javap で): `AdvancementsScreen#init` に `addTitleHeader(Component, Font)V` と `addToFooter(LayoutElement)LayoutElement` の呼び出しが各 1 つ (後者の戻り値は直後の `pop` で捨てられる)。`HeaderAndFooterLayout` に `addToFooter` は 2 種 (1 引数 / 2 引数) あり、今回は 1 引数のほう。窓の位置は `width` / `height` から中央寄せで、ヘッダー・フッターの高さに依存しない。
- 注意: ボタンが無いので、閉じるのは Esc か進捗のキー (L)。ボタンの無い見た目はこの設定が ON の間だけ。
- **未確認 (要・実機)**: コンパイル (この環境は JDK 21 で、Mixin / Fabric Loader のソースも無いため通していない)、起動時の Mixin 適用 (`require = 1` が 2 本)、見た目 (タブが画面の端で切れないか、画面が小さいときの窓の大きさ)、設定 OFF でバニラどおりに「進捗」「完了」が出ること。

## 進捗画面: 右下に「閉じる」タブ (エンチャント付きバリアブロック) を追加 (ユーザー指示)
- 依頼: 進捗のタブの UI を使い、エンチャントされたバリアブロックのアイコンで閉じるボタンを右下の追加タブとして作る。
- 実装: 新規 `neo.advancements.CloseTab` (描画・当たり判定・ツールチップ・クリック)、`MixinAdvancementsScreen` に `@Inject` 3 本 (`extractWindow` TAIL / `extractTooltips` TAIL / `mouseClicked` HEAD、すべて `require = 1`) と `@Shadow leftPos/topPos`。`large_advancements` が ON のときだけ出る (設定は増やしていない / P-73)。
- 見た目: バニラの `AdvancementTabType.BELOW` を、`index = max - 1` で `extractRenderState` に渡して右端の絵にし、窓の右端 (x = 窓の幅 − 28)、下側タブと同じ Y (窓の高さ − 4) に置く。アイコンは `Items.BARRIER` + `DataComponents.ENCHANTMENT_GLINT_OVERRIDE = true` を `fakeItem` で描く (位置ずれは BELOW と同じ +6, +6)。押すと `UI_BUTTON_CLICK` を鳴らして `onClose()`。ツールチップは `neo.advancements.close` (「閉じる」/ "Close")。
- 確認 (26.3 の jar を javap で): `AdvancementTabType#extractRenderState(GuiGraphicsExtractor,int,int,boolean,int)` が `index == max-1` で `last` を使うこと、`getWidth()=28` / `getHeight()=32` / `getMax()`、`AdvancementTab#extractTab` の座標計算、`GuiGraphicsExtractor#fakeItem(ItemStack,int,int)`、`setTooltipForNextFrame(Font,Component,int,int)`、`DataComponents.ENCHANTMENT_GLINT_OVERRIDE`、`Items.BARRIER`、`SoundEvents.UI_BUTTON_CLICK`、`SimpleSoundInstance#forUI(Holder,float)`。`extractWindow(g, mouseX, mouseY)` の 2 つの int はマウス座標。
- 注意: 下側に並ぶバニラのタブ (進捗のタブが 9 個以上のとき、左から 32px 刻み) と、窓が狭いと重なりうる。`mouseClicked` のバニラは `button() == 1` でタブを選ぶ (逆アセンブルで確認)。閉じるタブは `x()` / `y()` の範囲だけで判定し、ボタンの種類は問わない。
- **未確認 (要・実機)**: コンパイル (JDK 21・Mixin 無しのため未実施)、起動時の Mixin 適用 (新規 3 本)、バリアブロックが光ること、タブの位置・向きの見た目 (右端のスプライト)、クリック音、ツールチップ、設定 OFF のとき出ないこと。

## Mod 数の表示を、Mod 一覧の「N個のModを表示中」に合わせる (ユーザー指示)
- 不具合: Mods ボタン / タイトル画面の「(61 導入済み)」が、Mod 一覧の「7個のModを表示中」とずれていた。原因: `ModCount` が `ModRegistry.MODS` の全件 (子 Mod・前提 Mod・非表示を含む) を数えていた。
- 修正: `ModCount.getDisplayed` を、一覧の `ModsScreen#computeModCountText` と同じ条件 (`ROOT_MODS` のうち、`ModManagerConfig.isHidden` でなく `Badge.LIBRARY` でないもの) の数に変更。設定の保存でキャッシュが消える仕組みは従来どおり (`ModManagerBootstrap`)。
- 判断: 以前のコメントは「全部数える (固定)」だった。ライブラリの表示設定 (`LIBRARY_VISIBILITY`) には影響されない数にした。ライブラリも含めたい場合は条件を変えられる。
- **未確認 (要・実機)**: コンパイル、実際の表示 (一覧の数と一致すること)。

## 起動ログのファイル名を変更 (ユーザー指示)
- `mods/NeoModLog.txt` → `mods/NeoMods.log` (`NeoCore.LOG_FILE_NAME`)。書き方 (最新の起動のみ・上書き) は変更なし。README・AGENTS.md P-26 の表記も更新。上の過去の記述 (旧名) は履歴として残した。
- 注意: 以前の `mods/NeoModLog.txt` が残っていれば、手で消してよい (この Mod は読まない・消さない / P-82)。
- **未確認 (要・実機)**: 起動後に `mods/NeoMods.log` ができること。

## Mods ボタンの Mod 数の表記を「7 Mods Loaded!!」に統一 (ユーザー指示)
- 依頼: 「Mods (7 導入済み)」のような表記を、日本語でも「7 Mods Loaded!!」に変更。
- 変更: `modmenu.loaded` を `%s Mods Loaded!!` に (`ja_jp` / `en_us` とも同じ)、`modmenu.loaded.1` (`%s Mod Loaded!!`) を追加、隠し文言 (69 / 420) も `Mods Loaded...` の形に。`ModsButtonText` は、通常の表示のとき「Mods」の語を前に付けず、この文言だけを出す。
- 変えていない: 「隣に並べる (SHRINK)」の短い表示 `Mods (7)`、アイコン表示、タイトル画面右下の `(7 Mods)` (`modmenu.mods.*`)。
- **ユーザーの判断が要る点**: 「Mods (7 導入済み)」を Mods ボタンの文言と解釈した。英語 (`Mods (7 Loaded)`) も同じ形に揃えた。タイトル画面右下の `(7 Mods)` も変えたい場合や、短い表示も変えたい場合は指示を。
- **未確認 (要・実機)**: ボタンの幅に収まるか (「Replace Realms」の幅)、表示。

## ワールドマップ: 先に作った「タブ + アイコン」の UI を取り消し、文字だけの表示をボタン + ホバーにまとめ直す (ユーザー指示)
- 経緯: 地図の左右にタブ (進捗画面風) を並べた版は「改悪」との指示で、**ロールバックした** (`WorldMapScreen` を元の状態に戻し、`neo.map.help_title` / `help_detail` と README・本ファイルの該当の記述を消した)。そのうえで、今回は次の方針でやり直した。
- 方針: 機能を持たない、ただ文字を出しているだけの表示 (見出し「ワールドマップ」、ディメンション名、操作の説明の 1 行、縮尺の文字、座標の文字) を、ボタン + ホバー (ツールチップ) にまとめる。ボタンは左右の欄にまとめて、同じ幅・同じ段の並びにそろえる。バニラのボタンの見た目は変えない。
- 変更 (`WorldMapScreen`): (1) 左の欄に **「情報」「操作」** の半幅ボタンを 1 段追加 (押しても何も起きない)。「情報」の説明 = 見出し・ディメンション・縮尺・今いる座標 (変わったときだけ付け替える `updateInfoTooltip`)、「操作」の説明 = 操作方法 4 行 (`neo.map.help.*`、旧 `neo.map.help` は削除)。(2) 右下に離れていた **+/−** を、右の欄の最上段 (半幅 2 つ、ホバーで「拡大」「縮小」) に移し、左の「前のピン / 次のピン」の段と形をそろえた。ピンの編集 (名前・アイコン・テレポート・削除) は 1 段下げ、広域スキャン (`SCAN_TOP` = 140) もその下。(3) 画面の隅の文字 (見出し・ディメンション・操作の説明・縮尺・座標) の描画を削除。カーソルが地図の上にあるときの **座標は、カーソルの横のツールチップ**にした (ドラッグ中は出さない)。(4) ピン一覧の上端を 1 段分下げ (`LIST_TOP`)、行数は画面の高さに収まる分まで (`listMaxRows`、最大 12)。
- 残したもの: 縮尺バー・北 (N) のコンパス・ピン一覧の見出し・スキャンの状況の文字は、画面の絵・実際の情報なので残した (機能のない文字表示ではないと判断)。
- 確認: `check_rules.py` は違反 0、`AbstractWidget#setTooltip(Tooltip)` / `Tooltip.create(Component)` / `GuiGraphicsExtractor#setTooltipForNextFrame(Font, Component, int, int)` が 26.3 の jar にあることを javap で確認。
- **未確認 (要・実機)**: コンパイル、ボタンの幅 (「情報」「操作」の半幅に収まるか)、ツールチップの出る遅れ、画面が低いときのピン一覧の行数、地図の上でのカーソル横の座標の見え方。

### 追加の指示: 「情報」「操作」ボタンと N のコンパスを消し、地図のツールチップに統合
- 機能のない「情報」「操作」ボタン (押しても何も起きない) を削除 (`neo.map.info` / `neo.map.controls` も削除)。内容は、地図の上のカーソルの横のツールチップに統合 (`drawMapTooltip`): 見出し・カーソル位置の座標と Y・自分のいる座標 (`neo.map.here`)・ディメンション・縮尺・操作方法 4 行。ドラッグ中は出さない。
- 北 (N) のコンパス (`drawCompass`) を削除。左の欄は「現在地にピン / 前後のピン / 現在地へ / 完了」の 4 段に戻り、ピン一覧の上端 (`LIST_TOP`) も元に戻した。右の欄 (+/−、ピンの編集、スキャン) はそのまま。
- 注意: 地図の上にカーソルがある間は常に 9 行のツールチップが出るので、**地図が見えにくい可能性** (実機で確認)。長ければ、操作方法の 4 行を削る / 見出しを消すなどで減らせる。
- **未確認 (要・実機)**: コンパイル、ツールチップの大きさと見え方。

### 追加の指示: 縮尺バーを消し、右の欄をピンの機能だけにまとめる
- 縮尺バー (`drawScaleBar`、`neo.map.scale_bar`、`SCALE_BAR_*`) を削除 (縮尺はツールチップで分かるため)。
- 配置: **左の欄 = 地図の操作** (+ / −、現在地へ、完了、広域スキャン — スキャンは `SCAN_TOP` = 96)。**右の欄 = ピンの機能だけ** (現在地にピン、前 / 次のピン、ピンを選んだときの名前・アイコン変更・テレポート・削除、ピン一覧)。ピン一覧は左から右へ移し、上端はピンを選んでいる間 (編集の欄の下、`LIST_TOP_EDITING`) と選んでいない間 (`LIST_TOP_PLAIN`) で変わる (`listTop()`。選んだ瞬間に一覧が下へ動く)。
- **未確認 (要・実機)**: コンパイル、ピンを選んだときの一覧の動き、画面が低いときの行数。

### 追加の指示: 広域スキャンを「表示中の範囲を対象にする、ボタン 1 つ」に変更 + 予想時間をツールチップに明示
- 選択方法の確認 (ユーザーの回答): 「地図の縮尺に合わせて表示されている範囲を読み込む。読み込む際にツールチップで具体的なかかる時間の予想を明示」。
- 変更 (`WideMapScan`): 段階 (`Tier`: 64 / 128 / 512 チャンク、`cycleTier`) を削除。`start(client, store, centerX, centerZ, spanBlocks)` で、**マップ画面に表示中の範囲 (一辺 = `HeldMapView.RESOLUTION` × ブロック / ピクセル) の中心と一辺**を受け取り、その四角が地点の格子に収まるように始める (格子の間隔・保持するチャンクの半径は、これまでの計算を `spacingOf` / `footprintOf` / `halfCount` に取り出しただけで、同じ値)。`estimate(client, span)` が地点の数・読み込むチャンクのおよその数・予想の秒数を返し、`remainingSeconds()` が実行中の残り時間、`formatDuration` が「N 秒 / 分 / 時間」の文言にする。
- 予想時間: 速さ = **1 秒あたりの地点数**。初期値は `DEFAULT_POINTS_PER_SECOND` = 1.0 (1 地点 ≒ 新しい 64 チャンク)。**この初期値は根拠のある測定ではなく目安** (実機で測っていない)。ツールチップには「(目安。実行中に実測して更新)」と明示。実行中は `done / 経過秒` の実測 (4 地点以上進んだら) で残り時間を出し、終わったとき (8 地点以上・2 秒超) の実測値を `measuredPointsPerSecond` に覚えて、次の見積もりに使う (「前回の速さから」と表示。ゲームを起動している間だけ)。
- 変更 (`WorldMapScreen`): 左の欄のスキャンのボタンを 1 つ (「広域スキャン」/ 実行中は「スキャン停止」) に。ツールチップは、停止中 = 範囲 (ブロック・チャンク・地点) + 予想時間 + 注意、実行中 = 進み具合と残り時間 (`updateScanButton`、変わったときだけ付け替え)。ボタンの下の状況の文字も「残り約 …」付きに。設定 `map_wide_scan` が ON のクリエイティブ・シングルのときだけ出る点は変えていない。
- 翻訳: `neo.map.scan.tier` / `neo.map.scan.tooltip` を削除、`neo.map.scan.tooltip.*` と `neo.map.scan.time.*` を追加、`neo.map.scan.running` / `done` を新しい形に (`ja_jp` / `en_us`)。
- 注意: ズームを大きく引く (スケール 4) と、範囲は 8192 ブロック四方・約 28.6 万チャンクで、予想時間も長くなる (初期値で約 70 分)。上限は設けていない (ツールチップの予想時間で判断する)。表示範囲は地図の動かしている途中で変わる (押した瞬間の範囲で始まる)。
- **未確認 (要・実機)**: コンパイル、予想時間の初期値 (1 地点 / 秒) が実際とどれだけずれるか、ツールチップの出方、スキャン中に地図を動かしても影響が無いこと、範囲が地図の端で切れる場合の格子。


# 最新检查点：Iris／Bliss 实验特效黑屏修复已部署

用户录像和Bliss final.fsh仅写RGB定位display alpha误当premul，实际rawGL旧版alpha0输出黑。仅三个产品文件新增displayRGB入口，普通透明图路径保留premul；四效果×四时刻×四alpha实际session.render回归过，两分辨率UI边界／边缘15点一致，限定独审无确认P1/P2。完整1551/1563/1551零失败，各4旧skip，新Windows包ac7b36a2d301a837/15fbd8bfe9a9885f/d86f3291082aed81备份部署，其他163/73/201文件不变。以docs/verification/2026-10-29-experimental-iris.md与packages/deployment为最新，源码指纹ab8db5864aa588b9b3ef3c40dd513f53e21ce93137c00e52cf27d864d053ad90。仍待用户同光影实机复测；不许称已在CCB换包实测通过，不清.work、不启停游戏、不提交推送发布。旧实验功能／图标回执仅历史，下文不覆盖此状态

---

# 最新检查点：实验性 SkSL 已构建部署，待新特效实机验收

用户已验收此前UI／图标，然后授权本轮实验性内容：高级页趣味特效下仅默认关开关；开时一次三秒全屏引力星云冲击波，之后可见聊天震惊/冷死了/黑洞触发水波/冰霜/引力。27个实际标题仅新增一个原子星芒glyph，仍用统一尺寸线宽。完整1550/1562/1550零失败，各4skip；三项独审P2均有红测修复且限定复查通过。真实GL两分辨率640帧通过，串行1080p暖p95 .175~.444ms，但首轮冷编译三构建并发时曾156.887ms，不能称首播不卡。新包e6c96e68932803d8/ffc917f1b7c1613d/150593c92e116a92已备份部署，其他163/73/201文件不变。以docs/verification/2026-10-29-experimental-effects.md及同前缀packages/deployment为准，sourceFingerprint=5faaf52fb73d828f0ee33b5573a85c28893f24ba5d378b0aa0f8a72048897718。未启停游戏/改配置玩家数据/提交推送发布，ARM/Linux未更新，不许擅自清.work或无反馈反复构建。以下是历史阶段记录，待验状态不覆盖本轮用户已给的前序UI验收

---

# 最新检查点：设置图标语义已修正并部署

以docs/verification/2026-10-29-settings-icons.md及同名前缀packages/deployment JSON为准。26个实际副标题、9复用17新绘，只有气泡样式用气泡；完整key映射与全chip覆盖测试阻止namespace误匹配。完整1541/1553/1541，0失败，各4skip，安装SHA前缀6b9063457bc5faec/a93c7e2a3f1ebc72/4ef012bf9f34d529。其他163/73/201文件不变，备份D:\AtomChat-Backups\2026-10-29-settings-icons\20261029-122619。未启停/提交/推送/发布，图标实机观感待用户，旧Emoji性能和旧独审限制仍在。不要无新反馈反复构建或扩功能

---

# 最新检查点：10/29 确认窗与标题图标已部署

当前以 docs/verification/2026-10-29-confirmation-family.md 为准，覆盖下文ui-menu-family包。完整测试1537/1549/1537，0失败，各4跳过，安装SHA前缀46562cb6289dd110/341a73ca7e9eb3b4/bb63475bd502efa5。独审：独立初审发现1项P2：窄窗正文孤字，已补真实中英文窄宽回归并修复；其他本轮指定范围未发现明确P1/P2。修复后的限定复查因服务429未完成，不能声称最终独审通过，审查不覆盖此前全部0.4.0改动。用户已锁定取色器美术、红确认、无accent取消框、红三角、两行短文案；关窗后原卡片回弹已通过真实回归，设置全部fold标题有图标。未启停游戏／提交／推送／发布，实机待用户；不要无反馈扩功能或重复构建

---

# 最新检查点：四图菜单窄修已部署

当前交付为 ui-menu-family，覆盖下文旧 ui-final。完整测试 1530/1542/1530，0失败，各4跳过；三实例SHA前缀 0d401fed3b9c810c/a616d48af0634e8d/88b523679cb65550，备份 D:\AtomChat-Backups\2026-10-28-ui-menu-family\20261028-235846。独立审查两P2已补红测修复，修复后限定复查429未完成；实际观感／Emoji性能仍待用户。不要重复扩功能或全量构建，见ui-corrections顶部与ui-menu-family的packages/deployment JSON。未提交推送发布、未启停游戏。

---

# UI 返工检查点（23:27 已验证并部署，实机待验）

**最新状态覆盖下文旧阻塞清单：两项兼容问题已解决，三端完整构建与部署均完成。不要按旧清单再修一次或重新全量测试。**

- Forge 1526／Fabric 1538／NeoForge 1526，失败／错误 0，各 4 跳过。日志 `{forge,fabric,neoforge}-final-ui.log`；接缝141/74、孪生77无未归类／陈旧、目标／日志／裸值／工具17项及变异通过。
- 3 Windows x64 已备份部署，SHA前缀 `172a7a538645bd7e`／`02de0bc5f3596de5`／`52539718b6a96e18`，其他163/73/201文件不变。备份 `D:/AtomChat-Backups/2026-10-28-ui-final/20261028-232745`。最终指纹与回执 `2026-10-28-ui-final-packages.json`、`2026-10-28-ui-final-deployment.json`。
- 最新返工内容和范围以同目录 `2026-10-28-ui-corrections.md` 顶部为准。源码、实际组件离屏图、状态和共享记忆已更新；未提交／推送／发布／清理，也未启动关闭游戏。
- 剩余：用户用最终包复测 CCB Emoji 开关与持续帧率、常用语编辑补全、菜单／弹窗观感。独立最终复审此前429失败，明确未完成；不能声称实机或最终独审通过。用户若只说继续，应先确定有无新反馈，不扩功能或循环构建。

---

下文是部署前的历史排查检查点，兼容阻塞与未部署状态已经过期。

用户最新追问“这么慢，上下文要爆”，已承认范围失控，承诺不再扩功能，只修兼容阻塞、验证、部署。**不要继续增加 UI 功能或追逐假设。**

## 工作区与授权

- `D:/AtomChat-Main`，PowerShell；main 基线 `93f58a16a7e910adec2106891e13f717473680b2`，大量本轮未提交修改，**不允许 git reset/checkout/stash 覆盖**。
- 用户最初 0.4.0 六项 UI 任务仍有效，不写上帝类；当前多次拒收，不得以构建通过宣称 UI/性能已验收。
- 自动部署三个既定实例已获长期授权：Forge `D:/Myworld/.minecraft/versions/1.20.1-main/mods`；Fabric `.../1.21.1-CCB/mods`；NeoForge `.../Mechanomania-航空学/mods`。先备份、后替换、验哈希。不启动/关闭游戏，不 commit/push/tag/release。
- 三端 JDK：Forge `C:/Program Files/Java/jdk-17.0.18`，其余 `jdk-21.0.11`；都 `--offline --no-daemon --console=plain`，Forge另 `-Dnet.minecraftforge.gradle.check.certs=false`。

## 最新确定需求

- ＋浮层三行搜索／玩家／图片；玩家在同一菜单变在线列表，不再开页面；复用原玩家／图片路径。
- 搜索按常用语方式借用真实输入框，蓝色高亮边框、发送变暗禁用；只搜当前页，公屏遵循筛选、私聊仅当前联系人，含折叠保留历史；上下/滚轮定位，输入栏上一行计数，退出恢复草稿和光标。
- 表情、常用语、＋菜单间距统一走原有布局；三个都保留 `DotTexture.drawConnector` 蓝色连接尾迹（用户明确确认）。
- 图标大小复用原 composer `s(18)`（新 token `COMPOSER_ICON_SIZE`），不采用 context `s(16)`。
- 确认窗用于三种清理操作，主题材质/透明/模糊、开关动画、hover/按压，默认取消，完整退场阻断输入。
- **补全必须遵循原版逻辑，常用语编辑时也能弹出；同族群间距/动画/hover，不自己写第二套补全。**

## 已部署的旧候选（不是最新源码）

- 17:34 部署返工候选：Forge `b6ec099ef568b0a3…`，Fabric `157d11abfc8a8bba…`，NF `39e774d7981be5d7…`。
- 记录 `2026-10-28-ui-corrections-{packages,deployment}.json`，备份 `D:/AtomChat-Backups/2026-10-28-ui-corrections/20261028-173418/`，其他文件160/73/201保持不变。
- 用户随后又拒收间距、图标、补全等，所以不能把这批当最终交付。最新 glyph 缓存／补全修改尚未部署。

## Emoji 性能证据与修复

- 真实 CCB，用户自行启动并复现，JFR 标记事件 `com.atomchat.UiFrame`：反复开 Emoji 每次2～4帧70～125ms；常态约3ms，CHAT约2.9ms。主要慢栈 `_nFlushSurface`、`_nDrawString`。首次“固定矩阵预热”探针漏掉动态缩放。
- `.work/2026-10-28-ui/ccb-emoji-labeled.jfr` 约10MB，PID当时52784，录制已到期，游戏用户已关闭。另有 snapshot JSON、`analyze-labeled-jfr.py`。不要再停止不存在的录制或打印完整 Java 命令行（含登录令牌）。
- 动态真 GL 探针 RTX5060、2560×1600、density1600/1080：64Emoji原 paint p50≈44ms、flush≈87ms；普通文字正常；栅格图正常。
- 已做全局 `render/EmojiGlyphCache.java`，`SkiaFontRenderer.drawRuns` 接入，固定分辨率 CPU 图像缓存后变换图片；256项/32MiB、有界LRU、淘汰close；字体、RGB、原宽度保留。缓存原生2倍不透明像素与原绘制完全相同，动画重复只栅格化一次。
- 修复后同GL动态探针 paint≈0.17ms、flush≈0.09ms。对应 `EmojiPanelPerformanceTest`（默认 env gate跳过）和 `EmojiGlyphCacheTest`已通过。**仍需最终新包实机验证，不宣称真实CCB已修复。**

## 目前源码结构

- `ComposerPopupLayout` 从原 Emoji/QuickPhrase 抽取唯一 left/top/bottom gap；`COMPOSER_POPUP_GAP=PANEL_TOP_GAP+s(6)`，不是发明新间距。
- `ComposerPopupTransform` 共享 .92→1 + drop10 的实际矩阵及逆指针；ToolMenu、Emoji、QuickPhrase、SuggestionPopup 使用，避免动画期间画/点不一致。原 QuickPhrase hit-zone测试工厂已改为open+update后测试稳定几何。
- 列表行 `COMPOSER_POPUP_ROW_HEIGHT=s(36)` 源于原 QuickPhrase；工具／补全使用同 token；Emoji grid保持原34。
- `MenuPopup.drawComposerSurface` 共用14圆角/原dark/8shadow，context原10不变。
- `SuggestionPopup` 是有状态纯外观组件，`prepare`确定同一pose后Wiring做native hover，再`paint`；退场快照；usage不交互。Native tooltip走原生全局 tooltip家族，不再自造tooltip面板。
- `CompletionInput` 注册在 confirmation之后、foreign/modal之前，让 native候选先处理Tab/UpDown/Esc，第二次Esc再到常用语编辑器。Main screen `canUseCompletions`允许 `QuickPhrasePanel.isEditing`，阻断搜索／其它模态／Emoji等。
- `SuggestorWiring`保留原解析/请求/排序/select/complete/key/narration；点击按原版任意button均可应用；滚轮当前真实指针＋clamp±1＋原版mouseScrolled；viewport capacity写回native line limit；新window重置lastMouse；presentation按window/usage身份缓存，避免每帧重转上千候选。
- `ComposerSuggestionAccessor`新client Mixin读 suffix、maxLength、formatter；三client mixin JSON已登记。Native formatter转 `FormattedLine`（颜色/下划线与原字面字符分开），`PlainText`负责编辑态字面绘制/保留软换行空格，避免§被二次解析或源索引漂移；`ComposerPointer.drawGhostSuffix`覆盖空输入。
- `FormattedLine.drawOnSurface`仅对输入语法色用现有 ColorUtil.readableAccent + MIN_GLYPH_CONTRAST 保底，原metadata不变，alpha保留。UI其它颜色不乱改。

## 已验证的原版源码事实

MCDev MCP已读1.21.1 Yarn `ChatInputSuggestor`、`ChatScreen`、`TextFieldWidget`以及1.20.1官方 `EditBox`，完整官方1.21 CommandSuggestions在 `.work/CommandSuggestions.java`。

- 原版窗口 `mouseClicked`不检查button，任意按钮都select+complete。
- ChatScreen先把wheel clamp[-1,1]，窗口检查真实鼠标是否在rect，不用lastMouse。
- 1.21.1 `setCanLeave(false)`／official `setAllowHiding(false)`使无窗口时Tab可show，refresh使pending就绪。
- **Fabric主屏原先已补设setCanLeave(false)，缺项主要在组件自足性与official端，不要说所有实际Fabric首次Tab都坏。** 用户实际常用语不弹主要是render可见性一刀切＋按键优先级。
- Native `maxSuggestionSize` ↔ official `suggestionLineLimit`；`renderTextProvider` ↔ `formatter`；`suggestion`、`maxLength`字段两端同名。`TextFieldWidget.getMaxLength`是private，已改为Accessor读字段。
- tooltip转换必须 `Texts.toText(message)` ↔ `ComponentUtils.fromMessage(message)`，现代码已是正确方法。

## 当前两个真实阻塞（必须先修）

1. **Forge1.20.1编译：没有 `CommandSuggestions.setAllowHiding(boolean)`。**
   - `.work/2026-10-28-ui/forge-ui-audit-full.log` 有准确错误。
   - 该方法是1.21新增；1.20 Tab本来没有这层可隐藏策略。需要先读1.20官方 CommandSuggestions source，按真实API做薄版本桥，不反复猜接口。
   - 当前 official Wring直接调用 `suggestor.setAllowHiding(false)`，新增官方 NativeStartup test也直接 `engine.setAllowHiding(true)`，两处都要兼容。
   - 可采用平台小桥 `CompletionVersionPolicy`（Forge1.20 no-op，NF1.21 setAllowHiding(false)，Fabric setCanLeave(false)），或其它明确version seam；不要引UI框架。测试恢复构造默认可使用仅测试反射的可选方法。

2. **NeoForge JUnit client Mixin加载不适用：新增测试的Engine/Input直接implements @Mixin接口。**
   - `.work/.../neoforge-ui-audit-full.log`，XML `platforms/1.21.1-neoforge/build/test-results/test/TEST-Gradle-Test-Executor-1.xml`。
   - 根异常 `IllegalClassLoadError: Illegal classload request for com.atom.chat.mixin.ChatInputSuggestorAccessor…Mixin…cannot be referenced directly`，Input同样 `ComposerSuggestionAccessor`。
   - 不要跳过测试、假装通过或让测试启动游戏。当前 NativeStartup真实key测试通过Unsafe仅绕过重游戏构造，并无运行MC；Fabric已成功。
   - 合理修法：让Wiring的Native字段访问通过普通窄port，默认实现调用现有Mixin，测试注入普通访问port；Input字段访问可放Host默认方法（生产默认Mixin、测试override读真实字段），而不是测试fixture直接implements client @Mixin。目前尚未实施这个方案。

## 新增真实Native测试

- official `layers/mapping/official/src/test/java/com/atom/chat/screen/VanillaCompletionStartupTest.java`，Fabric同名文件在 `net/minecraft/client/gui/screen/`。
- 默认构造flag通过Native setter恢复true再装替换器，运行**原版 keyPressed**，旧安装器Tab测试确实红；修正后Fabric通过。
- 另两项测试读真实native suffix显示条件（含空输入、光标与maxLength）及真实Brigadier parse＋native formatter逐字符颜色/原字符串（含§）保留。
- Fixture的Engine仅override show/refresh避开client网络，Input字段通过反射读取；**目前直接implements client Mixin是NF失败原因，待按上节改port**。
- `sun.misc`已在versions/third-party-apis `_test_only`声明，测试不入产物。

## 最近执行结果

- Fabric最新完整 `test build` **成功**：`.work/2026-10-28-ui/fabric-ui-audit-full.log`（最终测试数需读XML，不要沿用旧1512/1524；源码后仅metadata变更，当前Java锁定除了必要兼容修复）。
- Forge编译失败、NF测试加载失败，如上。不要用旧三端green当当前green。
- Fabric定向 native/PlainText/Composer/Suggestion/QuickPhrase/InputWiring 已多轮绿：`native-ui-literal-parity.log`等。
- Emoji动态GL与像素/缓存测试绿；共享gap/icon测试修正夹具后绿（图标reference需相同clip，否则AA差2值；Quick必须open/update才测）。`completion-motion-red.log`清楚观察旧补全无动画/间距655 vs662.5失败。
- 浮层/补全最新代码还没实机、还没最终打包部署。

## 元数据守卫待收尾

- `versions/mapping-aliases.json`已添加NativeStartup test对应Fabric路径。
- `tools/twin_residual_alias.json`新增真实映射，但**长规则先执行**，几条使用规范化后类型 `EditBox.class,"text"` 的规则未命中原始 `TextFieldWidget.class,"text"`。需改为真实Yarn类型上下文（不要全局 text→value）。
- 最新 twin77对，剩official driver结构3块＋native测试若干纯映射块；旧Wring `9394fd549188`台账陈旧。先修真实aliases，再只对Wring driver真实结构差异精确登记，删旧指纹。不要 blanket accept。
- `tools/twin_diff_report.py --only src/main/java/com/atom/chat/screen/SuggestorWiring.java --accept --reason ...` 可限定；理由为official自建responder/initialText，Fabric继承原版listener，保留10/09既定接缝。
- `gen_seams --check`旧139/71；新PlainText/FormattedLine引用RichTextRenderer可能需要重新生成，按实际输出。
- 最新裸值459/505、日志269/0；需最终重跑。

## 审查与沟通

- 早先独立review Peirce成功，修过7项＋隐藏横幅；但都是较早返工，不覆盖本轮最新cache/补全。
- 后续独审全部429：Wegener、Hooke、Hegel等，最新id `01a182f8-097d-77f2-817e-5db07433a4d1`也errored。已对用户明确说**最新修改未拿到独审结果**，不能冒充通过。不要频繁空重试。
- 用户截图已保存 `docs/verification/assets/2026-10-28-ui-corrections/user-popup-gap-and-connector.png`，图中Emoji/下栏间蓝色为DotTexture连接尾迹，用户选择三者保留统一。
- 源码不再扩展。修兼容、最终三端检查、保存证据/更新STATUS与memory，再按授权备份部署三Windows目标；原ARM/Linux仍旧拒收包，不宣称已更新。
- 最后可请求一次具体新包实机验证，不能代理启动/关闭游戏，不能宣称所有UI/所有Native情形绝对无错。

## 工具与脚本

- 所有长脚本用 `.work/*.py` + apply_patch，**不要再在PowerShell双引号内塞带反引号/嵌套引号的python -c**，之前已出现转义编译失败。
- `.work/package-ui-corrections.py`目前要求最新全量XML>1400、零失败，并验证三Windows jar/废弃页面未入包/无测试类，写corrections包JSON和source指纹。兼容修正后更新required类（新glyphcache/新Mixin）再用，别在过滤测试后用被覆盖的XML冒充全量。
- `.work/deploy-ui-corrections.py`已验证能备份/换三实例/验哈希；最后用新receipt名称可保留旧候选证据。
- 新源指纹包含versions，所以元数据改完要重新package；不含docs，无需因文档改动重编所有测试。
- `exec_command`返回session必须保留，用write_stdin；不要再poll已结束旧id。当前无必须等待的构建（55335/90111/86008已完成，前两个/后一个结果如上）。

# AtomChat Design

> 当前实现进度、版本、部署与待办见 [STATUS.md](STATUS.md)；本文件记录设计意图，个别数值（如面板默认尺寸）会随迭代变化。

## Goal

Build a high-quality, highly customizable phone-app-style chat UI for Minecraft, distinct from E33Chat. The vanilla chat box remains visible normally; pressing the chat key opens AtomChat and hides the vanilla chat HUD.

## Rendering engine

- **Skija (HumbleUI Skia bindings, 0.143.18)** — Java bindings and native libraries are pinned together; HumbleUI types is 0.2.0.
- Draw directly onto Minecraft's main framebuffer via `BackendRenderTarget.makeGL` + `Surface.wrapBackendRenderTarget`.
- Save/restore GL state around Skia drawing (`GlStateUtil`).
- Bundled native libraries (`skija-windows-x64`, `skija-windows-arm64` or `skija-linux-x64`, selected per artifact) are auto-extracted by Skija at runtime. Windows ARM64 still requires in-game validation.

## Architecture (learned from Tuui)

| Concept | AtomChat class |
| --- | --- |
| Skia screen bridge | `SkiaGraphics`, `AtomChatScreen` |
| GL state guard | `GlStateUtil` |
| Components | `UiComponent`, `Box`, `Text`, `AbstractCanvas` |
| Fonts | `FontManager`, `SkiaFontRenderer` |
| Drawing helpers | `SkiaDraw` |
| Animation | `Animator`, `Easing` |
| Config | `AtomChatConfig` |

## UI layout

- Centered phone panel (default 420×780), rounded corners, dark translucent background with Skia ImageFilter blur.
- Header: world channel name + time.
- Scrollable message list with bubbles/avatars.
- Input bar: image / emoji buttons, two-line text area, send button.

## Interaction design

### 0.4.0 UI 族群与职责（10 月 28 日返工）

| 控件 | 既有族群与标杆 | 实际接入 |
| --- | --- | --- |
| 圆圈＋入口 | 顶栏／输入栏方形动作按钮 | `DrawPrimitives` 的 hover、按压回弹、激活色和反馈音效 |
| 工具浮层／玩家列表 | 输入栏浮层的锚点／开关；右键紧凑菜单的完整材质及行控件 | `ComposerPopupLayout`／`ComposerPopupTransform` 负责位置与开关；底板、圆角关系、高亮、峰值余量、回弹整套使用右键菜单 `MenuPopup.drawSurface`／`drawRowWash`，玩家在同一浮层替换内容 |
| 菜单图标 | 现有路径资产 | 玩家复用 `AppIcons.ICON_TAB_PROFILE_PATH`，图片复用 `DrawPrimitives.ICON_IMAGE_PATH`；尺寸与输入栏共用 `COMPOSER_ICON_SIZE`，只有新增搜索／圆圈＋需要新路径 |
| 聊天搜索 | `QuickPhraseWiring` 的真实输入框借用模式 | `ComposerSearch` 暂存聊天草稿与光标；高亮输入框，禁用且变暗发送按钮；输入、选择、粘贴和 IME 仍归原输入框；上方一行匹配计数提示 |
| 确认窗口 | 颜色选取弹窗的固定深色模态与按钮材质 | `PickerModalPaint` 同时被真实取色器和确认窗调用，统一底板、遮罩、圆角、阴影及中性 hover；开关沿用淡入淡出，按钮保留文字与回弹，取消无强调色边框、默认键盘焦点用中性高亮，确认与标题三角警告采用 `dangerColor` |
| 原版补全 | 输入栏浮层的锚点／开关；右键紧凑菜单的完整材质及行控件 | 外框左边与＋菜单／输入栏对齐，完整底板和行控件使用右键菜单绘制；候选、解析、滚动、Tab 应用和叙述继续由原版负责；常用语编辑时也允许补全 |
| 水波纹 | 共享按钮反馈 | 有界、轮廓裁切，跟随动画开关，不把它当作 hover 或开关过渡的替代品 |

搜索只查询当前页：公屏遵循当前筛选，私聊只查询当前联系人，包含该页折叠的保留记录；上下键／滚轮切换匹配并持续高亮。Esc 或再次选搜索退出，恢复草稿与光标，不跨页、不改筛选。切入其他输入框借用者或保存导航草稿前先退出搜索；在途上传回写暂存聊天草稿，搜索期间的真实拖图入口被阻断。

清空聊天记录、清除壁纸、清理图片缓存仍是同一确认组件的三处调用。弹窗打开前先调用设置页既有 `cancelPointer` 释放底层按压，因为模态会消费本次鼠标松开事件；关闭后原卡片必须能恢复悬停回弹。回调先清除再执行，进入动画尚未落定时不命中确认；退场期间保留输入所有权。正文使用已确认的两行短句，不加句号；未开启历史保存时明确为本次会话且不声称删除本地文件。

取消始终在左、确认始终在右，窄窗也不改成上下排布。短段落先按可用宽度在既有字号下限内适配，再换行，避免孤字；长英文保留完整含义并按词换行，正文仍需完整位于按钮上方。

设置页全部可折叠分组从同一个 `drawLabel` 绘制入口获得左侧图标，`SettingsGroupIcons` 按完整翻译键明确分配语义，不能用命名空间里的子串（例如 `atomchat` 中的 `chat`）推断类别。优先复用现有路径，缺失图标集中于 `SettingsGroupGlyphs`，仍由 `DrawPrimitives.drawIconCentered` 控制尺寸、居中、线宽和圆头圆角。文字和点阵尾迹右移，右侧展开箭头仍保留原位置。回归枚举所有实际设置页和chips，新增标题必须有显式分配，只有气泡样式使用聊天气泡图标。

“复用了族群”的验收需要同时核对材质、几何、图标、hover、按压、开关过渡、输入和生命周期；只调用底板或使用同一个 token 不算完成。Emoji、常用语、＋菜单共用从原表情／常用语实现提取的下沿间距；三者保留 `DotTexture.drawConnector` 连接尾迹。动画绘制和命中使用同一个变换及其逆变换。

Emoji 字形在动态缩放时使用有容量限制的共享位图缓存，保持既有字体选择和宽度；像素／独立动态 GL 回归不能代替最终 CCB 实机验收。

### 防止族群再次混配

- `MenuPopup.drawPickerSurface` 只表示 Emoji／常用语选择器的大外框；原先模糊的 `drawComposerSurface` 名称已移除。位于输入栏上方不等于使用选择器美术。
- 紧凑菜单用 `drawSurface` 的既有外框及 `drawRowWash` 的内框、`rowInset` 回弹峰值公式，调用者不另选半径或固定 inset。常用语自己的内容内边距仍保留。
- `CompactMenuFamilyTest` 直接将实际＋／补全控件与 `ContextMenuController` 使用的原绘制调用对照，在两种宽度、首末行、32帧悬停进退过程中逐像素比较右侧轮廓。完整行不通过额外圆角裁剪掩盖偏差，滚动玩家列表的局部行才需要视口裁剪。
- 对齐测试独立读取实际输入栏和＋菜单边缘，覆盖普通与半屏模式，不能以补全自身计算结果作为期望；长文本在回弹变换内单独限宽，不能靠裁剪整行遮住错误。渲染导出必须包括高亮／放大状态。测试负责已知族群约束，实机观感仍由用户截图验收。

- Click avatar → insert `@名字 `.
- Double-click avatar → poke shake (600ms damped sine).
- Right-click message → copy / quote context menu.
- Quote target is shown above the input bar.

### 实验性全屏装饰

“实验性内容”是既有高级页的独立折叠卡片，位于趣味特效之后，仅一个默认关闭开关；标题图标使用原子轨道／星芒原生路径，完整键名显式登记，仍走现有图标尺寸、线宽和居中

全屏SkSL属于装饰层而非模态族：在面板及其模态绘制完成、变换恢复后绘制，不另做控件或输入规则。当前帧Surface快照仅在当前Skia作用域采样和释放，不跨context.resetAll；显示帧的RGB已合成，光影可能不定义alpha，专用display入口直接读取RGB并输出alpha1；普通透明图像入口仍遵守预乘alpha，两者都以SRC完整替换已采样画面，避免重复合成

状态、关键词匹配、shader方程和native绘制各自独立小类；ChatEffectPresentation接现有可见消息订阅及生命周期。手动开关激活一次演出，已有配置和重装仅作为基线；离屏资源随removed释放，复用屏幕时重建。默认关闭不编译／采样，播放中不排队，关闭全局动画不补播

## MVP scope

1. Skija render bridge + phone panel.
2. Chat key interception + vanilla chat HUD hiding.
3. Message capture/send, bubbles, avatars, names, time.
4. @, poke, copy/quote, emoji panel.
5. JSON config.

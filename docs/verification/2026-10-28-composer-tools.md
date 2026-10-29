# 0.4.0 输入工具与界面完善

> 本文记录初版候选，已被用户拒收，不代表当前验收通过；最新需求和返工状态见 `docs/verification/2026-10-28-ui-corrections.md`。

基线为 `main@93f58a1`，版本保持 `0.4.0`。本轮源码尚未提交或推送，未打 tag 或公开发布。

## 实现与 UI 族群

| 内容 | 族群与实现 |
| --- | --- |
| 圆圈＋工具入口 | 延续输入栏／顶栏的方形动作按钮，保留按压回弹和反馈音效 |
| 搜索／玩家／图片菜单 | 使用 `MenuPopup` 的固定深色浮层、圆角和菜单行选中态 |
| 搜索与在线玩家 | 独立的面板内列表页面，共用现有标题栏、返回按钮和列表卡片；查询状态不借用聊天草稿 |
| 清理确认窗口 | 模态层，卡片颜色与材质从 `UiCards`／`UiTokens.cardCutout()` 派生；独立状态、布局、绘制和输入路由 |
| 指令补全 | 原版保留解析、服务端候选、应用补全和键盘语义；候选、用法、错误提示与说明统一使用 Skija 菜单族绘制 |
| 水波纹 | 按钮共享反馈，按点击位置扩散并裁切在控件轮廓内；资源有界，禁动画立即清空，已消失控件不继续绘制 |
| 主题 | 原有颜色／材质 token，保留旧 ID 与个人预设；新增澄蓝、暮紫、冰雾、暖砂，深浅各两款，明显半透明且保留模糊 |

搜索覆盖当前世界保留的完整公屏和私聊快照，包括列表里折叠的历史，不改变现有记录容量和保存期限。支持频道筛选、中文查询与粘贴、按时间倒序展示；结果点击会展开历史并定位高亮。若公屏视图筛选排除目标，切换为全部后定位；结果已失效则保留搜索页并在其上方显示提示。玩家列表来自当前连接的在线名单，点击在原光标处插入真实名字的 @。

清空聊天记录、清除壁纸和清理图片缓存均使用取消／确认窗口。默认键盘焦点是取消，Tab／左右键切换，Esc 或点击遮罩取消。确认前没有删除副作用，关闭屏幕会丢弃待确认回调；确认时先清空状态再执行业务，重复事件不会重复删除。窄窗口使用纵向按钮并适配正文，完整显示警告。

各功能按职责分文件；聊天主屏仅增加组件接线，未引入新 UI 框架或新增上帝类。完整职责表见 [设计说明](../design.md) 和 [实施方案](../superpowers/plans/2026-10-28-composer-tools.md)。

## 验证

使用 JDK 17.0.18（Forge）和 JDK 21.0.11（Fabric／NeoForge），在各平台工程目录运行：

```powershell
# Forge 1.20.1，JAVA_HOME 指向 JDK 17
.\gradlew.bat '-Dnet.minecraftforge.gradle.check.certs=false' test build --offline --no-daemon --console=plain

# Fabric / NeoForge 1.21.1，JAVA_HOME 指向 JDK 21
.\gradlew.bat test build --offline --no-daemon --console=plain

# 每端另外生成 Windows ARM64 / Linux x64 包，Forge 保留上述证书参数
.\gradlew.bat build -x test '-Patomchat.nativePlatform=windows-arm64' --offline --no-daemon --console=plain
.\gradlew.bat build -x test '-Patomchat.nativePlatform=linux-x64' --offline --no-daemon --console=plain
```

| 目标 | 测试数 | 失败／错误 | 跳过 |
| --- | ---: | ---: | ---: |
| Forge 1.20.1 | 1500 | 0／0 | 2 |
| Fabric 1.21.1 | 1512 | 0／0 | 2 |
| NeoForge 1.21.1 | 1500 | 0／0 | 2 |

每端新增 19 项测试；两项跳过仍为原有 golden／benchmark。新增覆盖查询范围与顺序、频道筛选、Unicode 边界、清理确认状态、真实组件的按钮命中与输入隔离、多帧 Canvas 配平、水波纹像素反馈、透明主题应用和消息定位。新能力与审查发现的主要回归均观察过失败后修正。

独立审查的三项 P2 已修正并复核：公屏筛选导致搜索定位失败；＋入口提前消费按下／释放导致旧反馈丢失；搜索失败 toast 被工具页遮挡。最终限定复核未发现剩余明确 P1/P2；审查没有代替三端测试或游戏内验收。

删除工具输入处理器的实际变异使 `InputWiringSourceTest` 变红，随后按字节备份恢复源文件；恢复步骤校验 SHA-256，没有使用 Git 回退覆盖工作区。工具侧 `python -m unittest discover -s tools -p 'test_*.py' -q` 通过，其中含孪生与裸值守卫的既有变异验证。

接缝 139／71、目标矩阵、孪生 75 对（未归类 0、陈旧 0）、日志 269 条（违规 0）、裸值 468／505、`git diff --check` 均通过。新增访问器的映射依据为缓存的官方源码与 [Yarn 1.21.1 API](https://maven.fabricmc.net/docs/yarn-1.21.1%2Bbuild.3/net/minecraft/client/gui/screen/ChatInputSuggestor.SuggestionWindow.html)，没有把平台差异改成同一种实现。

更新日志的本轮 v0.4.0 段分别通过现有 `_parity_check.py`。全历史扫描另报告 CHANGELOG 六处、RELEASE_NOTES 两处旧版本中英数量不等，均位于本轮未修改的旧版本段，未扩大本轮范围。

## 离屏图片

下图使用实际 Java／Skija 组件生成并逐张检查。测试为 CPU 光栅渲染，背景是测试底色，不是游戏实机截图。

- [工具菜单](assets/2026-10-28-composer-tools/tool-menu.png)
- [搜索页](assets/2026-10-28-composer-tools/search-page.png)
- [命令补全与说明](assets/2026-10-28-composer-tools/command-completion.png)
- [确认窗口](assets/2026-10-28-composer-tools/confirmation.png)、[窄窗口](assets/2026-10-28-composer-tools/confirmation-narrow.png)
- [全部主题的实际列表组件](assets/2026-10-28-composer-tools/theme-contact-sheet.png)

## 产物与部署

三加载器 × Windows x64／Windows ARM64／Linux x64 共九包完成。每包按明确 JAR 路径通过 `tools/verify_jars.py --platform <系统架构> <jar>`，并检查新组件及中英文资源入包、测试代码未入包；同加载器三种系统的全部应用 class／assets 内容逐字节一致。

完整 SHA-256、路径、应用条目数与源码输入指纹见 [包清单](2026-10-28-composer-tools-packages.json)。三个既定 Windows x64 实例已备份、替换并读回核验：

| 实例 | 新包 SHA-256 前缀 | 其他顶层文件保持不变 |
| --- | --- | ---: |
| 1.20.1-main | `1af076fa8f344cbc` | 157 |
| 1.21.1-CCB | `25a65e339c33c198` | 73 |
| Mechanomania-航空学 | `6776f78d0dfea785` | 201 |

各实例只有一个活动 AtomChat，备份、完整哈希与时间见 [部署记录](2026-10-28-composer-tools-deployment.json)。部署后重新核对构建输入未变化；未改玩家数据、配置或其他模组，未由代理启动或关闭游戏。

## 未覆盖

- 未在游戏中验收新菜单、搜索跳转、玩家 @、命令补全及三项真实清理操作。
- 未测整合包中的 IME／其他模组补全交互、多人名单变化、真实 GPU／整帧成本或繁忙世界背景上的透明主题观感。
- ARM64／Linux 完成编译与包内容验证，没有把交叉构建当作对应平台实机运行通过。
- 搜索保留原有历史容量与过期清理，不恢复已经淘汰或删除的记录。

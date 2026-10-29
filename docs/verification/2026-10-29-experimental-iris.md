# 实验特效与 Iris／Bliss 世界黑屏修复

2026-10-29，保持 0.4.0，未提交／推送／发布

## 根因与证据

用户录像在开启动画、水波和冰霜播放期间显示世界区域变黑，聊天 UI 与快捷栏仍可见，特效结束后世界恢复。原文件 SHA256 `c109d58ccb44fa2788874a7b0e0b05ff047cf341aa4f7a6151e9968d0e4bb758`，备份在 `.work/experimental-iris/user-video.mp4`，[录像抽帧](assets/2026-10-29-experimental-iris/contact.png) 保留该现象

CCB 为 Iris 1.8.0、低配 Bliss 2.0.4；实际光影包 `shaders/dimensions/final.fsh:138` 仅写入 `gl_FragColor.rgb = FINAL_COLOR`，并未定义最终 alpha。包身份与这一条输出记录在 [shaderpack evidence](assets/2026-10-29-experimental-iris/shaderpack-output-evidence.json)，未修改光影文件或配置

全屏实验特效错误地将这个已经合成的显示 RGB 当作透明图片的预乘颜色，还原直通色后再乘入输入 alpha，导致 alpha=0 时世界 RGB 归零，alpha 在 0～1 之间时颜色被裁剪。UI 区域具有有效 alpha，因而仍然显示；特效结束不再回贴，世界恢复

真实 OpenGL 最小复现，输入 RGB 固定为 `(0.2, 0.5, 0.8)`，在原实现近结束的水波帧中：

| 输入 alpha | 原输出 RGBA |
| --- | --- |
| 0 | 0, 0, 0, 0 |
| 0.5 | 51, 127, 127, 127 |
| 1 | 51, 127, 204, 255 |

前轮探针使用 Skia 写出的不透明合成背景，未覆盖“RGB 有效但 alpha 非 coverage”的真实光影输出，这是漏测原因

## 修正边界

- `ExperimentalEffectRenderer.drawDisplayFramebuffer` 明确接收最终显示帧，shader 对该路径直接使用已合成 RGB，并输出不透明显示颜色
- 普通 `draw` 入口保留真实透明图像的预乘算法和 alpha；原先预乘契约及 SRC 回贴修复保留
- `ExperimentalEffects.render` 使用显示帧入口，三端聊天屏幕仍通过同一会话接线
- 只修改三个产品文件，不改变通用 SkiaGraphics、主帧缓冲包装格式、光影配置或 UI 样式
- `RGB_888X` 包装真实 RGBA8 backend 的候选在独立探针中被原生 API 明确拒绝，未用于产品

## 回归与审查

新增 `ExperimentalFramebufferAlphaTest`，使用真实隐藏 GLFW／OpenGL，在 driver 层写入 RGB 有效、alpha 分别为 0、0.2、0.5、1 的世界，以及不透明 UI 标记，经实际 `ExperimentalEffects.render` 播放入口执行四种效果、四个时刻；比较可见颜色与 alpha=1 参考一致。旧实现首项期望红通道 51、实得 0，新实现通过，且三个 Windows 平台均执行，非跳过

既有 `ExperimentalEffectRendererTest` 的半透明图像、预乘 RGB、alpha 保持及 SRC 回贴回归仍通过，避免把显示帧修复推广到普通透明图片

`ExperimentalEffectsProbe` 真实 GPU 检查两种分辨率、四种效果、四种源 alpha，在 15 个 UI 边界／屏幕边缘采样点逐像素比较，结果完全一致；常规 640 帧 reset／snapshot／提交检查继续通过。截图为原生合成场景，**不是换包后的游戏截图**

独立强模型限定只读审查：本轮三个产品文件未确认新的 P1/P2；核读新旧日志、实际非跳过 XML、颜色空间／采样／资源释放及入口接线。主代理随后补充运行边界／屏幕边缘探针，不扩大为全版本独立审查

## 构建与部署

- 三端 `gradlew.bat test build --offline --no-daemon --console=plain` 成功；Forge Java 17 与既有证书参数，Fabric／NeoForge Java 21
- Forge／Fabric／NeoForge：1551／1563／1551，失败 0、错误 0，各 4 项既有跳过
- targets、twins、seams、日志、bare 与 diff-check 通过
- 原生 GPU 最终 1080p 特效段 p95 约 0.265～0.496 ms，计时含采样／绘制／提交等待，不是游戏整帧；首次着色器编译限制仍保留，不声称首播无卡顿
- 三 Windows x64 包的完整校验与源码指纹见 [packages](2026-10-29-experimental-iris-packages.json)，旧包备份和实例安装哈希见 [deployment](2026-10-29-experimental-iris-deployment.json)

当前结论为原生复现已修复、三端回归通过。需要用户保持 Iris／同一 Bliss 光影开启，复测手动启用、水波和冰霜时世界是否正常；未由代理启动或关闭游戏，也未宣称修复后已完成该整合包实机验收

安装SHA简写 `ac7b36a2d301a837/15fbd8bfe9a9885f/d86f3291082aed81`；其他mods文件 `163/73/201` 均未变动；源码指纹 `ab8db5864aa588b9b3ef3c40dd513f53e21ce93137c00e52cf27d864d053ad90`；备份 `D:\AtomChat-Backups\2026-10-29-experimental-iris\20261029-162329`

## 用户验收与提交授权

2026-10-29 用户确认“验收通过，提交，推送main，更新项目状态”，本轮光影黑屏修复实机验收完成，授权将全部已验收的 0.4.0 续开发改动提交并推送 origin/main

以上待复测描述保留为修复交付时的历史记录，当前状态以本节及 docs/STATUS.md 为准；未授权打 tag 或公开发布，其他 GPU 和平台的覆盖范围不变

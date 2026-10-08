# 历史视频生成器 Android MVP

Kotlin + Jetpack Compose 的手机端工程。首页提供新建视频、历史项目和 API/提示词设置。新建项目默认题材为《古代的驿站到底有多爽？》。当前流程为题材/AI候选题 -> 文案 -> 分镜 -> 逐镜生图 -> 逐镜豆包 MP3 -> 本地 FFmpeg 合成 -> MP4 保存到相册。

## 导入和运行

使用 Android Studio 打开本目录，需要 JDK 17、Android SDK 37、Gradle 8.11.1。工程固定使用 Gradle 8.11.1；如果 Android Studio 没有本地缓存，会从项目 wrapper 配置的镜像下载。最低支持 Android 10（API 29）。

在“API 设置”分别填入文本、图片、豆包的 HTTPS 地址、模型及密钥。首次安装会预填电脑端当前使用的文本、图片和豆包接口地址及模型；Key 仍需在手机上输入一次。文本端使用 OpenAI Compatible `/v1/chat/completions`，生图端使用 `/v1/images/generations`；豆包使用火山引擎单向 TTS v3 的 Resource ID 和音色 ID。图片接口需返回 `data[0].url` 或 `data[0].b64_json`。不同中转站的 `size` 参数可能不同，当前默认 `1792x1024`（16:9），需用自己的服务先验证 1 张图片。

每个镜头的配音独立保存于 `files/projects/{id}/audio/001.mp3` 等文件。点“重配此段”仅调用当前镜头的 TTS，生成临时文件并验证时长后替换对应 MP3；其他镜头音频不动。可点“试听”立即检查该段。之后点“重新剪辑”会复用其他镜头的视频片段并重建最终 MP4。修改镜头文字再点完整生成流程，也只补做状态失效的音频。

制作页的“AI帮我选题”会调用文本 Provider 生成 6 个候选题材，候选列表会写入项目状态，选择后仍可手动改题材。历史项目支持继续制作、播放成片、保存到相册、系统分享和删除；运行中的项目不能删除。

项目状态保存在 `project.json`，每个阶段和镜头的素材状态随进度写入。中断后打开项目继续制作；成片保存到系统相册 `Movies/HistoryVideo`。API Key 经 Android Keystore 加密后放在应用私有设置中，不进入项目目录和 Git。

## 已知限制与验证

- 本机缺少 Android 构建工具和设备，尚未验证 Gradle 同步、API 实际响应或真机 FFmpeg 编码。首次真机测试应先做 10～15 镜短片，再做长片压力测试。
- FFmpeg 使用 `dev.ffmpegkit-maintained:ffmpeg-kit-video:8.1.9`，该非 GPL 包为 LGPL-3.0。MVP 用 MPEG-4 视频编码，不使用桌面端 GPL 的 `libx264`；成片体积和速度需要真机测量。发行时须保留依赖的许可证和第三方声明。
- 当前未加入 SRT/ASS、BGM、随机运动、音效、开场模板、人物抠图或成本统计。这些属于后续阶段。
- 前台服务提供进度和暂停；Android 对后台服务有时长与启动限制，系统终止后需重新打开项目并继续。暂停 FFmpeg 时会取消当前命令，已有图片和 MP3 保留。

FFmpeg 依赖说明：[维护版项目](https://github.com/ffmpegkit-maintained/ffmpeg)，[版本列表](https://mvnrepository.com/artifact/dev.ffmpegkit-maintained/ffmpeg-kit-video/versions)。

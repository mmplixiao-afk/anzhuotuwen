# 参考图功能留档

这是撤回前的代码存档，不含密钥、凭据或构建缓存。没有把云端没有的真实 API 响应写进来。父 box 另存 DeepSeek 实测证据。

## 记录

| 项 | 值 |
| --- | --- |
| 已发布参考图实现 | commit `90f6075dc40aae8e60cd0fe7c385919c5903caec` |
| 分支 | `cursor/multi-character-reference-4320` |
| PR | https://github.com/mmplixiao-afk/anzhuotuwen/pull/8 |
| 已发布版本 | versionName `0.5.16`，versionCode `47` |
| 基线 | tag `v0.5.15-polyphonic`，commit `b10c9f728b931e0e24cb34347895e7861f5ed55d`，versionName `0.5.15`，versionCode `46` |
| 未提交实验 | 工作区相对 `90f6075` 的 diff。本地 versionName `0.5.17`，versionCode `48`。未 commit、未 push、未发布 |

## 文件

- `v0.5.16-reference-vs-v0.5.15.patch`：已发布 v0.5.16 相对 v0.5.15 的完整参考图实现。
- `uncommitted-0.5.17-experiment.patch`：撤回前工作区里的联合拆镜角色实验（character id、提示词修订、版本号 0.5.17/48）。
- `sources-v0.5.16/`：`90f6075` 的相关源码快照。
- `sources-experiment/`：撤回前工作区源码快照。
- `RESTORE.md`：如何在新工作区重新得到这两份代码，不改写已发布历史。

## 不要做的事

不要 `reset --hard`、force-push、rebase 已发布提交，不要改 `main`，不要合 PR，不要删分支、tag 或 Release。

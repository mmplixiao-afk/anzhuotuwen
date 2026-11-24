# 恢复步骤

已发布历史保持不动。下面只在新的工作区或新分支上恢复文件，不重写 `90f6075`。

## 恢复已发布 v0.5.16 参考图

```bash
git checkout -b restore-reference-v0.5.16 b10c9f728b931e0e24cb34347895e7861f5ed55d
git apply --check v0.5.16-reference-vs-v0.5.15.patch
git apply v0.5.16-reference-vs-v0.5.15.patch
```

应用后的树应与 `90f6075dc40aae8e60cd0fe7c385919c5903caec` 的 `app/` 内容一致。该提交仍在 `cursor/multi-character-reference-4320` 和 PR #8 上，也可直接 `git checkout 90f6075`。

## 恢复未提交的 0.5.17 实验

在 `90f6075` 上：

```bash
git checkout 90f6075dc40aae8e60cd0fe7c385919c5903caec
git apply --check uncommitted-0.5.17-experiment.patch
git apply uncommitted-0.5.17-experiment.patch
```

这只恢复联合拆镜角色实验的未提交 diff，版本号为 0.5.17 / 48。不要把它当成已发布版本。

## 设计要点

v0.5.16 在「成文」和「生图」之间加了「参考图」节点。拆镜后用正文扫描人名，每个角色一张参考图和明文提示词。生图时按镜头附参考图，并在提示词里写「第一张参考图是……」。设置里单独保留「生图提示词」。

0.5.17 实验改为同一次拆镜响应返回 characters 和每镜 character_ids，并收紧角色判定提示词。真实 DeepSeek 试跑两轮语义不对，产品决定放弃参考图，而不是继续改提示词。

撤回后的工作区应不再生成或挂载参考图。旧项目里的角色字段和参考图文件可以留在磁盘上，生成时忽略。

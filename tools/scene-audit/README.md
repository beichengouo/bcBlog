# 模拟日志体检（沙盒故事逻辑）

把一次沙盒模拟的**报告**（`target/probe/scene-*.md`，用的是落库后的字段）和 **AI 调用日志**
（`target/probe/ai-log-scene-*.md`，含模型原始输出）配起来，用**同一套规则**数逻辑问题，
顺便统计速度、调用量、自检触发与改写情况。用来对比"命中才查 / 每步都查"这类开关的效果。

## 用法

```bash
node tools/scene-audit/audit-scene-log.mjs \
  --report target/probe/scene-real-suspicious.md --log target/probe/ai-log-scene-real-suspicious.md \
  --report target/probe/scene-real-always.md     --log target/probe/ai-log-scene-real-always.md
```

输出：每个模拟的步数、总调用、平均/中位耗时、自检调用次数与"模型判定需要修正"的次数、
**落库内容与模型原始输出不一致的步数**（= 真的被改写过的步数），以及四类逻辑问题的条数：

- 同伴位置对不上（写了与某人同行，但 TA 最新一步在别处）
- 遭遇被无视（服务端指定了遭遇，叙述里没有应对）
- 金币变化没交代（金币变了但叙述里没提钱）
- 原地踏步（概括与上一步完全相同）

## 两个注意点

1. **时间对不上**：AI 日志头部的时间是**真实墙钟**，不是模拟时间；所以脚本按"角色 + 顺序"配对，
   不要按时间配对。
2. **同伴要用落库后的值**：模型原始输出里的 `companions` 还没经过服务端"同区域 + 距离"过滤，
   直接用会误报（实测踩到过）；所以脚本以报告里的"同行：X"为准。

参考产出：`target/probe/selfcheck-compare.md`（命中才查 vs 每步都查的一天对比）。

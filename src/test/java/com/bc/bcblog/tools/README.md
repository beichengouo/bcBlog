# 沙盒测试工具（保留，不是临时脚本）

这里的工具用来整段跑沙盒剧情、检查各项机制是否符合预期。它们**不以 Test 结尾**，
所以平时跑 `mvn test` 不会执行，需要时手动指定。

> 这些工具都会新建一个"临时世界"，把要测的角色放进去，跑完自动删掉，不碰真实世界的数据。
>
> **写新工具时务必加上这一段**（在 Spring 启动前禁用定时任务）：
> ```java
> static {
>     System.setProperty("bcblog.sandbox.scheduler.disabled", "true");
> }
> ```
> 否则测试进程会带着"模拟时钟"去驱动真实世界的定时任务，把本该明天才行动的正式角色提前执行掉。

## SandboxSceneRun — 通用场景模拟器（最常用）

整段模拟多角色几天的生活，报告里能看到每步的地点、时长、摘要、**运气**、**遭遇**、伤势、
金币、物品、纪闻引用、回应行动，以及汇总统计。

```powershell
# 三个角色、模拟一天、混合风格
mvn test "-Dtest=SandboxSceneRun"

# 两个角色、模拟半天、爱冒险的风格（往危险地区跑）
mvn test "-Dtest=SandboxSceneRun" "-Dsandbox.scene.chars=2" "-Dsandbox.scene.minutes=720" "-Dsandbox.scene.style=danger"
```

可调参数：

| 参数 | 说明 | 默认 |
| --- | --- | --- |
| `sandbox.scene.chars` | 角色人数（1~4） | 3 |
| `sandbox.scene.minutes` | 模拟时长（分钟） | 1440（一天） |
| `sandbox.scene.location` | 起始一级地点 | 自由城邦联盟 |
| `sandbox.scene.style` | `mix` 混合 / `danger` 爱冒险 / `daily` 日常 | mix |
| `sandbox.scene.news` | 是否插入纪闻 | true |
| `sandbox.scene.keep` | 跑完是否保留临时世界（调试用） | false |
| `sandbox.scene.out` | 报告文件名（落在 target/probe/） | scene.md |

## SandboxPromptDump — 导出某个角色的真实提示词

不调用 AI、不写库，直接把"某个角色下一步行动会收到的系统提示词 + 用户提示词"导出成文件，
用来核对提示词改动的效果。

```powershell
mvn test "-Dtest=SandboxPromptDump" "-Dsandbox.dump.charName=伊露雅"
mvn test "-Dtest=SandboxPromptDump" "-Dsandbox.dump.charId=14"
```

结果写在 `target/prompt-dump/{system,user}.txt`。

## 用模拟时钟跑多天剧情

上面的工具会设置系统属性 `bcblog.sandbox.time-offset-minutes`，让"沙盒时钟"整体平移
（提示词时间、行动时间戳、位置结算、运气、遭遇判定都跟着走）。这个属性线上不设置即为 0，
行为与真实时间完全一致。

## SandboxQuestProbe — 旅人委托板的服务端规则（不调用 AI）

在一个临时世界里把"接取 → 推进进度 → 校验 → 完成结算 → 放弃"整条链路跑一遍，
再检查注进提示词的委托段落长什么样。**不调用 AI**，所以随时可以跑：

```powershell
mvn test "-Dtest=SandboxQuestProbe"
```

覆盖的规则：一人一委托、单步进度封顶、进度不允许回落、讨伐类"没到目标地区不算完成"、
采集类"没拿到东西不算完成"、完成后金币与物品是否真的到账、放弃后是否回到可接、
对手战力是否被夹到地点区间、提示词里"可接清单 / 当前委托 / 刚刚完成的委托"三种形态。

结果写在 `target/probe/quest-probe.md`（UTF-8，控制台在 Windows 下容易看成乱码，看文件更稳）。

## SandboxQuestRun — 旅人委托 × 角色行动（**会真的调 AI**）

建一个临时世界（地图从正式世界复制，战力区间一致），放一个急着赚钱的角色，
委托板上挂 5 条不同类型/距离的委托，让角色真刀真枪地行动十几步；
中途还会模拟管理员的后台操作（**改委托、改进度、下架、重新上板**），
验证"后台动过之后角色再行动会不会出问题"，以及委托做完奖励有没有到账、心态有没有变化。

```powershell
mvn test "-Dtest=SandboxQuestRun"
mvn test "-Dtest=SandboxQuestRun" "-Dsandbox.questRun.steps=16"
```

参数：`sandbox.questRun.steps`（默认 12）、`sandbox.questRun.model`（默认公益站 flash）、
`sandbox.questRun.keep`、`sandbox.questRun.out`。
报告：`target/probe/quest-run.md`（结论清单 + 每步剧情全文 + 完成结算核对 + 想法变化）。

> 这个工具**真的会消耗模型额度**（每步 1~2 次请求），跑之前先确认可以调用；
> 它同样在 static 块里禁用了定时任务，跑完自动删除临时世界。

## SandboxAutoRefreshCheck — 自动刷新到期判定（不调用 AI）

旅人集市 / 旅人委托板 / 旅人纪闻共用一套口径：**间隔 ≥ 24 小时按天对齐（24 = 明天这个点，
48 = 后天，写 30 这种当作 1 天），间隔 < 24 小时就是"距上一批满 N 小时"**。
这几个判定是私有方法，写错了只会表现成"该刷的时候不刷 / 一直在刷"，界面上看不出来，
所以用探针把组合穷举一遍：

```powershell
mvn test "-Dtest=SandboxAutoRefreshCheck"
```

覆盖：委托板与纪闻各自的到期组合（今天刷过 / 昨天刷过 / 6 小时间隔够不够 / 48 小时间隔、
根本没有上一批）、关掉开关时定时入口直接返回 0（**不会偷偷发 AI 请求**）、
失败重试冷却 10 分钟内只放行一次。报告：`target/probe/auto-refresh.md`。

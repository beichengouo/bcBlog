# 数据库脚本说明（MySQL 8 / utf8mb4）

## 一、全新安装

执行 `bc_blog_full.sql`：在 `bc_blog` 库中创建全部 **37 张表**（只含结构，不含数据）。
首次启动后端时，程序会自动写入默认超级管理员 `admin / Admin@123456`。

## 二、老库升级（备案完成后更新线上数据库，用这一份）

只执行 **`upgrade_20260916_batch.sql`** 一份脚本即可：它已经把 `upgrade_001 ~ upgrade_033`
的全部内容合并进去了，**不需要再单独执行其它脚本**。

| | |
| --- | --- |
| 起点 | 上一次部署版本（Git 提交 `3f13623`，2026-09-14 上线，17 张表） |
| 终点 | 当前版本（37 张表） |

脚本特点：

- **幂等**：内部先查 `information_schema`，已存在的表 / 字段 / 索引自动跳过，重复执行不会报错，中途失败可以直接重跑。
- **只增不删**：不删表、不删业务数据；配置项一律 `INSERT IGNORE`，后台已经改过的配置不会被覆盖。
- **不含敏感信息**：QQ 邮箱账号、授权码、各类 API Key 都是空值占位，部署后到后台填写。
- **不打扰线上**：沙盒模块默认关闭（`sandbox_enabled = 0`），需要时再在后台开启。
- **自带自检**：脚本结尾会返回「已就绪的表」清单（应为 37 张）与关键配置项清单。

执行方式（二选一）：

1. **Navicat**：连接数据库 → 选中 `bc_blog` → 右键「运行 SQL 文件」→ 选择 `upgrade_20260916_batch.sql`。
2. **命令行**：

```bash
mysql --default-character-set=utf8mb4 -uroot -p bc_blog < upgrade_20260916_batch.sql
```

> 执行前请先备份：Navicat 右键数据库 →「转储 SQL 文件」→「结构和数据」。
> 如果数据库名不是 `bc_blog`，请修改脚本里的 `USE \`bc_blog\`;` 一行。

> **以后怎么出升级脚本**：不需要再靠人工回忆执行过哪些脚本。上线前把服务器现有表结构导出到
> `docs/sql/schema/`，运行 [`tools/schema-diff/schema-diff.ps1`](../../tools/schema-diff/README.md)，
> 即可得到「服务器缺哪些表 / 字段 / 索引」的差异报告与一份升级 SQL 草稿，再整理成正式的
> `upgrade_YYYYMMDD_batch.sql`。详见 [`tools/schema-diff/README.md`](../../tools/schema-diff/README.md)。

### 本次升级新增的表（20 张）

第 1 部分：用户体系与管理功能

| 表名 | 用途 |
| --- | --- |
| `sys_level` | 等级配置（等级名称与升级所需经验后台可改） |
| `sys_sign_log` | 每日签到记录 |
| `sys_invite_code` | 邀请码（无限次使用，短时间被频繁使用会自动更换） |
| `sys_point_log` | 积分流水 |
| `sys_resource_unlock` | 智库资源解锁记录（解锁后不重复扣积分） |
| `sys_emoji` | 表情包 |
| `sys_email_template` | 邮件模板（同一场景可保存多套并切换启用） |

第 2 部分：沙盒世界模块 + 管理端安全加固

| 表名 | 用途 |
| --- | --- |
| `sandbox_world` | 沙盒世界（地图背景、世界观设定） |
| `sandbox_location` | 地图地点（区域范围与图标） |
| `sandbox_character` | 沙盒角色（人设、立绘、AI 绑定、位置状态、金币） |
| `sandbox_act` | 角色行动记录 |
| `sandbox_interaction` | 旅人低语（前台登录用户留言） |
| `sandbox_coin_log` | 沙盒金币流水 |
| `sandbox_relation` | 角色之间的好感度（有方向：A→B、B→A 各一条） |
| `sandbox_memory` | 角色每日记忆（故事化总结，长期记忆来源） |
| `sandbox_item` | 角色背包物品 |
| `sandbox_news` | 旅人纪闻（当天世界事件） |
| `admin_api_key` | 管理员 AI 服务商 Key（加密存储，后台只回显掩码） |
| `admin_api_log` | API 调用审计（谁、何时、来源、成败、耗时、IP） |
| `sys_login_ip` | 管理员登录 IP 记录（异地 / 异常时段检测用） |

### 原有表新增的字段

| 表名 | 新增字段 | 说明 |
| --- | --- | --- |
| `sys_user` | `email` / `exp` / `points` / `level` / `can_invite` / `sign_days` / `last_sign_date` / `security_password` | 邮箱账号、经验、积分、等级、邀请权限、签到、安全密码 |
| `blog_comment` | `user_id` / `avatar` / `level` / `level_name` | 原生评论显示登录用户与等级 |
| `blog_resource` | `cover` / `points` / `content` | 智库封面、所需积分、详情内容 |
| `ai_provider` | `owner_id` | 服务商归属（为空表示系统服务商，定时任务使用） |

### 新增或调整的配置项（`sys_config`）

- 评论系统切换（`comment_system`：`gitalk` / `native`）
- 注册：是否需要邀请码、是否需要邮箱验证码
- 等级升级经验、签到经验、评论经验、积分兑换金币比例
- 邮件：QQ 邮箱账号、授权码、SMTP 主机与端口、发件人昵称
- 沙盒：总开关（默认关闭）、自动间隔、夜间静默、每日上限、记忆总结、旅人纪闻、输出自查、系统调用模型
- 数据清理：总开关与执行时间，以及各日志表各自的保留天数（见第三节）

### 分步脚本对照（需要单独执行时使用）

| 脚本 | 内容 |
| --- | --- |
| `upgrade_001_category_parent.sql` | 多级分类 `parent_id` |
| `upgrade_002_live2d_model.sql` | Live2D 模型库 |
| `upgrade_003_announcement.sql`、`upgrade_004_announcement_author.sql` | 站点公告与发布人 |
| `upgrade_005_photo_resource.sql` | 流光忆庭、智库建表 |
| `upgrade_006_visit_stat.sql` | 访问量统计表 |
| `upgrade_007_music_fallback.sql` | 默认歌曲表与初始 4 首 |
| `upgrade_008_user_system.sql` | 用户体系、等级、签到、邀请码 |
| `upgrade_009_email.sql` | 邮件配置与邮件模板表 |
| `upgrade_010_register_email_template.sql` | 注册验证码邮件模板（二次元风格） |
| `upgrade_011_email_template_multi.sql` | 邮件模板支持多套 + `active` 字段 |
| `upgrade_012_user_email_unique.sql` | 用户邮箱唯一索引 |
| `upgrade_013_user_points.sql` | 积分字段与积分流水表 |
| `upgrade_014_resource_point_emoji.sql` | 智库封面积分详情、资源解锁、表情包 |
| `upgrade_015_cleanup_config.sql`、`upgrade_016_cleanup_tighten.sql` | 定期清理配置与默认保留天数 |
| `upgrade_017_sandbox.sql` | 沙盒世界模块（5 张表 + 运行参数配置） |
| `upgrade_018_sandbox_location_icon.sql` | 沙盒地点图标字段 |
| `upgrade_019_sandbox_coins.sql` | 沙盒角色金币、行动金币变化与金币流水 |
| `upgrade_020_sandbox_multi_character.sql` | 沙盒多角色同时行动与互动角色字段 |
| `upgrade_021_sandbox_relation.sql` | 沙盒角色好感度表与行动好感变化字段 |
| `upgrade_022_sandbox_favor_audit.sql` | 好感度实际生效变化字段与 AI 自查开关配置 |
| `upgrade_023_sandbox_chain.sql` | 互动回应回合字段与防循环配置 |
| `upgrade_024_sandbox_memory_item.sql` | 沙盒每日记忆表、角色背包表与物品变化字段 |
| `upgrade_025_sandbox_item_rarity.sql` | 背包物品品质与图标字段 |
| `upgrade_026_sandbox_sub_location.sql` | 沙盒二级地点字段（行动记录 + 角色当前位置） |
| `upgrade_027_sandbox_location_area.sql` | 地点改为「区域」范围（宽高）与坐标夹取 |
| `upgrade_028_sandbox_ai_interval.sql` | 由 AI 决定下次行动间隔（分钟 + 原因）与间隔区间配置 |
| `upgrade_029_sandbox_news.sql` | 旅人纪闻表与相关配置 |
| `upgrade_030_sandbox_news_auto.sql` | 纪闻自动生成开关与生成时间 |
| `upgrade_031_admin_key_security.sql` | 管理员 Key 加密存储表、API 调用审计表与 `ai_provider.owner_id` |
| `upgrade_032_admin_login_security.sql` | 管理员登录安全（安全密码、登录 IP 记录、异常提醒配置） |
| `upgrade_033_sandbox_memory_prompt.sql` | 记忆故事化提示词与 `sandbox_system_model` 配置 |
| `upgrade_20260916_batch.sql` | **以上全部合并版（推荐，只用这一份）** |

> 说明：旧的 `upgrade_20260915_batch.sql` 已经把内容并入 `upgrade_20260916_batch.sql`，
> 文件已从仓库删除（需要时可以从 Git 历史取回）。

## 三、表的用途与定期清理说明

后台「系统设置 → 数据清理」可以按天自动清理下面这些日志类表，**其他业务表不会被清理**。
清理任务每天按后台配置的时间执行一次（默认 03:30，服务器时间），只删除超过保留天数的历史记录。

| 表名 | 用途 | 是否参与清理 | 默认保留 |
| --- | --- | --- | --- |
| `sys_login_log` | 后台登录日志 | 是 | 7 天 |
| `sys_visit_stat` | 每日访问量（PV） | 是 | 30 天 |
| `sys_sign_log` | 签到记录 | 是 | 30 天 |
| `sys_point_log` | 积分流水 | 是 | 30 天 |
| `sandbox_act` | 沙盒角色行动记录 | 是 | 7 天 |
| `sandbox_memory` | 沙盒角色每日记忆 | 是 | 30 天 |
| `sandbox_news` | 旅人纪闻（世界事件） | 是 | 1 天（只留当天） |
| `admin_api_log` | API 调用审计日志 | 是 | 3 天 |
| `blog_article` / `blog_category` / `blog_tag` | 文章、分类、标签 | 否 | 永久 |
| `blog_comment` | 评论 | 否 | 永久 |
| `blog_photo` / `blog_resource` | 流光忆庭、智库内容 | 否 | 永久 |
| `sys_user` / `sys_level` / `sys_invite_code` | 用户、等级、邀请码 | 否 | 永久 |
| `sys_resource_unlock` | 智库解锁记录 | 否 | 永久 |
| `admin_api_key` / `sys_login_ip` | 管理员密钥与登录 IP 记录 | 否 | 永久 |
| `sys_email_template` / `sys_emoji` / `live2d_model` / `site_announcement` / `music_fallback` / `background` / `music_playlist` / `ai_provider` | 各类配置与素材 | 否 | 永久 |
| `sys_config` | 站点配置 | 否 | 永久 |
| `sandbox_world` | 沙盒世界（地图背景、世界观设定） | 否 | 永久 |
| `sandbox_location` | 沙盒地图地点与坐标 | 否 | 永久 |
| `sandbox_character` | 沙盒角色（人设、立绘、AI 绑定、当前位置与状态） | 否 | 永久 |
| `sandbox_interaction` | 旅人低语（前台登录用户留言，消耗积分） | 否 | 永久 |
| `sandbox_coin_log` | 沙盒金币流水（旅人贡献 / 角色赚取 / 角色消耗 / 管理员调整） | 否 | 永久 |
| `sandbox_relation` | 角色之间的好感度（有方向：A 对 B、B 对 A 各一条） | 否 | 永久 |
| `sandbox_item` | 沙盒角色背包物品 | 否 | 永久 |

额外备份建议：`bcblog-secret.key`（与 jar 同级）保存的是 API Key 的解密主密钥，
**不参与任何清理，也绝对不要提交到仓库**，请单独备份；密钥文件丢失后，后台保存过的 Key 需要重新填写。

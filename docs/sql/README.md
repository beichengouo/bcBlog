# 数据库脚本说明（MySQL 8 / utf8mb4）

## 一、全新安装

执行 `bc_blog_full.sql`：会自动建库 `bc_blog` 并创建全部 24 张表（只含结构，不含数据）。
首次启动后端时，程序会自动写入默认超级管理员 `admin / Admin@123456`。

## 二、老库升级（备案完成后更新线上数据库，用这一份）

直接执行 **`upgrade_20260915_batch.sql`** 即可，它把「上一次部署版本（Git 提交 `3f13623`，2026-09-14 上线）」
到当前版本之间的**全部数据库改动**合并成了一份脚本。

脚本特点：

- **幂等**：内部先查 `information_schema`，已存在的表 / 字段 / 索引自动跳过，同一个脚本重复执行不会报错。
- **只增不删**：不删表、不删业务数据；配置项用 `INSERT IGNORE`，不会覆盖你已经在后台改好的配置。
- **不含敏感信息**：QQ 邮箱账号、授权码、各类 API Key 都是空值占位，部署后在后台填写。
- **自检**：执行完会返回一张「已就绪的表」清单（13 张），看到清单即表示升级成功。

执行方式（二选一）：

1. **Navicat**：连接数据库 → 选中 `bc_blog` → 右键「运行 SQL 文件」→ 选择 `upgrade_20260915_batch.sql`。
2. **命令行**：

```bash
mysql --default-character-set=utf8mb4 -uroot -p bc_blog < upgrade_20260915_batch.sql
```

> 执行前请先备份：Navicat 右键数据库 → 「转储 SQL 文件」→ 「结构和数据」。
> 如果数据库名不是 `bc_blog`，请修改脚本里的 `USE \`bc_blog\`;` 一行。

### 本次升级包含的改动

新增表（13 张，脚本第 2~10 段）：

| 表名 | 用途 |
| --- | --- |
| `sys_level` | 等级配置（默认 10 级，名称与升级经验后台可改） |
| `sys_sign_log` | 每日签到记录 |
| `sys_invite_code` | 邀请码（无限次使用，10 分钟内用 5 次自动更换） |
| `sys_point_log` | 积分流水 |
| `sys_resource_unlock` | 智库资源解锁记录（解锁后不重复扣积分） |
| `sys_emoji` | 表情包 |
| `sys_email_template` | 邮件模板（同一场景可存多套并切换启用） |
| `blog_photo` | 流光忆庭照片墙 |
| `blog_resource` | 智库资源 |
| `sys_visit_stat` | 每日访问量统计 |
| `music_fallback` | 歌单加载失败时的默认歌曲 |
| `live2d_model` | Live2D 看板娘模型库 |
| `site_announcement` | 站点公告 |

字段变更：

| 表名 | 新增字段 | 说明 |
| --- | --- | --- |
| `sys_user` | `email` / `exp` / `points` / `level` / `can_invite` / `sign_days` / `last_sign_date` | 普通用户邮箱账号、经验、积分、等级、邀请权限、签到 |
| `sys_user` | `role` / `menus` | 管理员分级（SUPER / ADMIN1 / ADMIN2）与菜单授权 |
| `blog_comment` | `user_id` / `avatar` / `level` / `level_name` | 原生评论显示登录用户与等级 |
| `blog_article` | `author_id` / `author_name` | 文章发布人 |
| `blog_category` | `parent_id` | 多级分类 |
| `site_announcement` | `author` | 公告发布人 |
| `blog_resource` | `cover` / `points` / `content` | 资源封面、所需积分、详情内容 |

索引与唯一约束：`sys_user.uk_email`（邮箱唯一）、`blog_category.idx_parent`、`sys_email_template` 去掉 `uk_scenario` 并新增 `active` 字段。

配置项（`sys_config`）：评论系统切换、注册是否需要邀请码 / 邮箱验证码、签到与评论经验、数据清理开关与保留天数。

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
| `upgrade_20260915_batch.sql` | **以上全部合并版（推荐）** |

## 三、表的用途与定期清理说明

后台「系统设置 → 数据清理」可以按天自动清理下面这些日志类表，**其他业务表不会被清理**。

| 表名 | 用途 | 是否参与清理 | 默认保留 |
| --- | --- | --- | --- |
| `sys_login_log` | 后台登录日志 | 是 | 7 天 |
| `sys_visit_stat` | 每日访问量（PV） | 是 | 30 天 |
| `sys_sign_log` | 签到记录 | 是 | 30 天 |
| `sys_point_log` | 积分流水 | 是 | 30 天 |
| `blog_article` / `blog_category` / `blog_tag` | 文章、分类、标签 | 否 | 永久 |
| `blog_comment` | 评论 | 否 | 永久 |
| `blog_photo` / `blog_resource` | 流光忆庭、智库内容 | 否 | 永久 |
| `sys_user` / `sys_level` / `sys_invite_code` | 用户、等级、邀请码 | 否 | 永久 |
| `sys_email_template` / `sys_emoji` / `live2d_model` / `site_announcement` / `music_fallback` / `background` / `music_playlist` / `ai_provider` | 各类配置与素材 | 否 | 永久 |
| `sys_config` | 站点配置 | 否 | 永久 |

清理任务每天按后台配置的时间执行一次（默认 03:30，服务器时间），只删除超过保留天数的历史记录。

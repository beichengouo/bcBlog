# 数据库结构对比工具（schema-diff.ps1）

上线前用来自动比对**服务器现有的表结构**和**本地目标表结构**，输出差异清单，并且可以顺手生成升级 SQL 草稿。
这样就不用再靠「记得哪些脚本执行过」来推断，而是直接测量差异。

## 一、每轮上线前的流程

1. **导出服务器结构**（只要结构，不要数据）

   - Navicat：右键 `bc_blog` →「转储 SQL 文件」→ 只勾**结构**，不要勾数据
   - 命令行：

   ```bash
   mysqldump -uroot -p --no-data --skip-comments --default-character-set=utf8mb4 bc_blog > server_20261001.sql
   ```

2. **把文件放进 `docs/sql/schema/`**（命名建议 `server_YYYYMMDD.sql`），并提交到 Git，方便以后追溯每次上线的起点。

3. **运行对比**：

   ```powershell
   cd E:\IdeaPooject\bcBlog
   .\tools\schema-diff\schema-diff.ps1 -ServerDump docs\sql\schema\server_20261001.sql -Password 你的MySQL密码
   ```

4. **看差异报告**：`docs\sql\schema\server_20261001.sql.diff.txt`

5. **需要生成 SQL 草稿**就加 `-EmitSkeleton`：

   ```powershell
   .\tools\schema-diff\schema-diff.ps1 -ServerDump docs\sql\schema\server_20261001.sql -Password 密码 -EmitSkeleton
   ```

   会额外生成 `docs\sql\upgrade_draft_<日期>.sql`（表 → 字段 → 索引，已经做幂等包装）。

6. 再由我把它整理成正式的 `upgrade_YYYYMMDD_batch.sql`：补上配置项、默认数据、脚本头部说明和末尾自检，
   并**先在本地回放验证**（服务器结构 → 跑脚本 → 再对比，差异必须为 0）之后才交给你上服务器执行。

## 二、参数

| 参数 | 说明 |
| --- | --- |
| `-ServerDump` | 服务器导出的「仅结构」SQL 文件（必填） |
| `-ReferenceDb` | 作为目标结构的本地库，默认 `bc_blog` |
| `-ScratchDb` | 导入服务器结构用的临时库，默认 `bc_schema_diff_tmp`（每次运行先重建） |
| `-User` / `-Password` / `-HostName` / `-Port` | MySQL 连接参数，默认 `root` / 空 / `localhost` / `3306` |
| `-MySqlBin` | `mysql.exe` 所在**目录**，默认自动探测 `C:\Program Files\MySQL\MySQL Server *\bin` |
| `-EmitSkeleton` | 额外生成升级 SQL 草稿 |
| `-SkeletonPath` | 指定草稿输出路径（默认 `docs\sql\upgrade_draft_<日期>.sql`） |
| `-KeepScratch` | 对比结束后保留临时库，方便自己用 Navicat 核对 |

退出码：`0` = 结构一致（不需要新脚本）；`1` = 存在差异；`2` = 执行出错。

## 三、产物

| 文件 | 内容 |
| --- | --- |
| `<导出文件>.diff.txt` | 差异报告：表 / 字段 / 索引，分「需要新增」与「服务器多出（需人工确认）」两类 |
| `docs\sql\upgrade_draft_<日期>.sql` | 升级 SQL 草稿（用 `-EmitSkeleton` 时生成） |

## 四、安全说明

- 服务器 dump 会被导入**临时库**（默认 `bc_schema_diff_tmp`），不会碰本地其他库；
- 导入前会自动剔除 dump 里的 `CREATE DATABASE` / `USE` / `DROP DATABASE` 语句——
  Navicat 导出的文件里常带 `USE 线上库名` 和 `DROP TABLE`，不剔除会误伤本地库；
- 本地库只被读取（只查 `information_schema` 和 `SHOW CREATE TABLE`），不会被修改；
- 对比结束会自动删除临时库，加 `-KeepScratch` 才保留。

## 五、注意事项

- 草稿只覆盖**表 / 字段 / 索引**三种差异；配置项（`sys_config`）、默认数据、字段**类型变更**不会自动生成，需要人工补。
- `UNIQUE` 索引要求数据不重复，服务器上若已有重复数据会执行失败，需要先清理。
- 导出只要结构；万一带了数据，脚本会提示，但对比只用结构，不会使用这些数据。
- 建议用 PowerShell 7（`pwsh`）运行；脚本也兼容 Windows PowerShell 5.1（文件带 UTF-8 BOM）。

## 六、实测记录

2026-09-16 用「上次部署版本（Git `3f13623`，17 张表）」的结构作为服务器侧跑过一轮：

| 项目 | 结果 |
| --- | --- |
| 表 | 需要新增 20 张 |
| 字段 | 需要新增 16 个（`sys_user` 8、`blog_comment` 4、`blog_resource` 3、`ai_provider` 1） |
| 索引 | 需要新增 1 个（`sys_user.uk_email`） |
| 字段定义不同 | 0 个 |

生成的草稿在本地应用后，与目标库的结构差异为 **0 条**（字段含排序规则、索引、表选项全部一致），重复执行也不报错。

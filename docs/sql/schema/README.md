# 服务器结构快照目录

每次准备上线前，把**服务器当前的表结构**导出成「仅结构」的 SQL 文件放在这个目录里，命名建议
`server_YYYYMMDD.sql`，并提交到 Git，方便以后追溯每一次上线的起点。

导出方式（只要结构，不要数据）：

- Navicat：右键 `bc_blog` →「转储 SQL 文件」→ 只勾**结构**，不要勾数据
- 命令行：

  ```bash
  mysqldump -uroot -p --no-data --skip-comments --default-character-set=utf8mb4 bc_blog > server_20261001.sql
  ```

导出之后运行结构对比工具，就能直接得到「服务器缺哪些表 / 字段 / 索引」和一份升级 SQL 草稿：

```powershell
.\tools\schema-diff\schema-diff.ps1 -ServerDump docs\sql\schema\server_20261001.sql -Password 你的密码 -EmitSkeleton
```

完整流程、参数说明与注意事项见 [`tools/schema-diff/README.md`](../../../tools/schema-diff/README.md)。

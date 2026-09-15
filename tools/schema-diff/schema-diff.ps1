#requires -Version 5.1
<#
.SYNOPSIS
    对比「服务器数据库结构」与「本地目标库结构」，输出差异清单，并可生成升级 SQL 草稿。

.DESCRIPTION
    上线前的标准流程：
      1. 在服务器上导出「仅结构」SQL（Navicat 只勾结构，或 mysqldump --no-data）
      2. 把导出文件放进 docs/sql/schema/ 目录（例如 server_20261001.sql）
      3. 运行本脚本，得到「服务器缺哪些表 / 字段 / 索引」的差异清单
      4. 加 -EmitSkeleton 还会生成一份升级 SQL 草稿（表 / 字段 / 索引三部分，已做幂等包装）

    安全说明：
      - 服务器 dump 会被导入一个**临时库**（默认 bc_schema_diff_tmp），不会碰本地其他库；
      - 导入前会自动剔除 dump 里的 CREATE DATABASE / USE / DROP DATABASE 语句，
        避免 Navicat 导出文件里的 `USE 线上库名` 和 `DROP TABLE` 作用到本地；
      - 本脚本只读取本地目标库的 information_schema 与 SHOW CREATE TABLE，不会修改本地库；
      - 默认对比结束后删除临时库，加 -KeepScratch 可以保留下来自己用 Navicat 看。

.PARAMETER ServerDump
    服务器导出的「仅结构」SQL 文件（必填）。

.PARAMETER ReferenceDb
    作为目标结构的本地库名，默认 bc_blog（也就是你本地开发库）。

.PARAMETER ScratchDb
    导入服务器结构用的临时库名，默认 bc_schema_diff_tmp（每次运行会先重建）。

.PARAMETER MySqlBin
    mysql.exe 所在**目录**，默认自动探测 `C:\Program Files\MySQL\MySQL Server *\bin`。

.PARAMETER EmitSkeleton
    额外生成升级 SQL 草稿，默认写到 docs/sql/upgrade_draft_<日期>.sql（可用 -SkeletonPath 指定）。

.PARAMETER KeepScratch
    对比结束后保留临时库，方便自己核对。

.EXAMPLE
    .\tools\schema-diff\schema-diff.ps1 -ServerDump docs\sql\schema\server_20261001.sql -Password 你的密码

.EXAMPLE
    # 顺便生成升级草稿
    .\tools\schema-diff\schema-diff.ps1 -ServerDump docs\sql\schema\server_20261001.sql -Password 你的密码 -EmitSkeleton

.NOTES
    退出码：0 = 结构一致；1 = 存在差异；2 = 执行出错。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$ServerDump,

    [string]$ReferenceDb = 'bc_blog',
    [string]$ScratchDb = 'bc_schema_diff_tmp',
    [string]$HostName = 'localhost',
    [int]$Port = 3306,
    [string]$User = 'root',
    [string]$Password = '',
    [string]$MySqlBin = '',
    [switch]$EmitSkeleton,
    [string]$SkeletonPath = '',
    [switch]$KeepScratch
)

$ErrorActionPreference = 'Stop'

# ============================== 基础工具 ==============================

function Write-Step($text) { Write-Host "`n==> $text" -ForegroundColor Cyan }
function Write-Ok($text) { Write-Host "    $text" -ForegroundColor Green }
function Write-Note($text) { Write-Host "    $text" -ForegroundColor Yellow }

function Resolve-MySql {
    param([string]$BinDir)
    $candidates = @()
    if ($BinDir) { $candidates += (Join-Path $BinDir 'mysql.exe') }
    $onPath = Get-Command 'mysql.exe' -ErrorAction SilentlyContinue
    if ($onPath) { $candidates += $onPath.Source }
    $candidates += (Get-ChildItem 'C:\Program Files\MySQL\MySQL Server *\bin\mysql.exe' -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -ExpandProperty FullName)
    $candidates += (Get-ChildItem 'D:\Program Files\MySQL\MySQL Server *\bin\mysql.exe' -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -ExpandProperty FullName)
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c)) { return $c }
    }
    throw "找不到 mysql.exe，请用 -MySqlBin 指定它的所在目录（例如 `"C:\Program Files\MySQL\MySQL Server 8.0\bin`"）。"
}

# 读取查询结果（制表符分隔，-N 去掉表头）
function Get-SqlRows {
    param([string]$Db, [string]$Query)
    $argList = @("-h$HostName", "-P$Port", "-u$User", '-N', '-B', '--default-character-set=utf8mb4')
    if ($Db) { $argList += $Db }
    $argList += @('-e', $Query)
    $out = & $global:mysqlExe @argList
    if ($LASTEXITCODE -ne 0) { throw "查询失败（退出码 $LASTEXITCODE）：$Query" }
    return @($out)
}

# 执行一条 SQL（不关心结果）
function Invoke-Sql {
    param([string]$Db, [string]$Sql)
    $null = Get-SqlRows -Db $Db -Query $Sql
}

# 取某张表的完整建表语句（--raw 保留换行，方便逐行解析列与索引定义）
function Get-CreateTable {
    param([string]$Db, [string]$Table)
    $bt = [char]96
    $argList = @("-h$HostName", "-P$Port", "-u$User", '-N', '-B', '--raw', '--default-character-set=utf8mb4',
        $Db, '-e', ("SHOW CREATE TABLE $bt$Db$bt.$bt$Table$bt;"))
    $out = & $global:mysqlExe @argList
    if ($LASTEXITCODE -ne 0) { throw "SHOW CREATE TABLE 失败：$Db.$Table" }
    $text = ($out -join "`n")
    $idx = $text.IndexOf('CREATE TABLE')
    if ($idx -lt 0) { throw "无法解析 $Db.$Table 的建表语句" }
    return $text.Substring($idx)
}

# 导入 SQL 文件到指定库
function Import-SqlFile {
    param([string]$Db, [string]$File)
    $argList = @("-h$HostName", "-P$Port", "-u$User", '--default-character-set=utf8mb4', $Db)
    $p = Start-Process -FilePath $global:mysqlExe -ArgumentList $argList -RedirectStandardInput $File -Wait -PassThru -NoNewWindow
    if ($p.ExitCode -ne 0) { throw "导入 SQL 失败（退出码 $($p.ExitCode)）：$File" }
}

# 拆分 TSV 行
function Split-Row {
    param([string]$Line)
    return @($Line -split "`t")
}

# ============================== 准备连接 ==============================

$global:mysqlExe = Resolve-MySql -BinDir $MySqlBin
Write-Step "使用 MySQL 客户端：$global:mysqlExe"

$oldPwd = $env:MYSQL_PWD
$env:MYSQL_PWD = $Password

$dumpPath = (Resolve-Path -LiteralPath $ServerDump).Path
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)   # tools/schema-diff -> 仓库根目录
$reportPath = "$dumpPath.diff.txt"
$scratchSql = Join-Path $env:TEMP ('bc_schema_diff_' + [Guid]::NewGuid().ToString('N') + '.sql')

try {
    # ---------- 1. 预处理 dump：去掉会误伤本地库的语句 ----------
    Write-Step "预处理服务器导出文件：$dumpPath"
    $raw = [System.IO.File]::ReadAllText($dumpPath)
    $lines = $raw -split "`r?`n"
    $cleaned = New-Object System.Collections.Generic.List[string]
    $removed = 0
    $hasData = $false
    foreach ($line in $lines) {
        $trim = $line.Trim()
        if ($trim -match '^\s*(CREATE|DROP)\s+DATABASE' -or $trim -match '^USE\s+' -or $trim -match '^\s*USE\s') {
            $removed++
            continue
        }
        if ($trim -match '^INSERT\s+INTO' -or $trim -match '^REPLACE\s+INTO') { $hasData = $true }
        $cleaned.Add($line)
    }
    $head = New-Object System.Collections.Generic.List[string]
    $head.Add('-- 由 schema-diff.ps1 生成：仅用于导入临时对比库，不作为升级脚本')
    $head.Add('SET NAMES utf8mb4;')
    $head.Add("USE ``$ScratchDb``;")
    $body = ($head + $cleaned) -join "`n"
    [System.IO.File]::WriteAllText($scratchSql, $body, (New-Object System.Text.UTF8Encoding($false)))
    Write-Ok "已剔除 $removed 条 CREATE/DROP DATABASE 或 USE 语句（防止误改本地库）"
    if ($hasData) { Write-Note "注意：该文件里含 INSERT 语句（带了数据），本次只对比结构，数据不会被使用" }

    # ---------- 2. 导入临时库 ----------
    Write-Step "重建临时库 $ScratchDb 并导入服务器结构"
    Invoke-Sql -Db '' -Sql "DROP DATABASE IF EXISTS ``$ScratchDb``; CREATE DATABASE ``$ScratchDb`` DEFAULT CHARACTER SET utf8mb4;"
    Import-SqlFile -Db $ScratchDb -File $scratchSql
    $scratchTables = @(Get-SqlRows -Db '' -Query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$ScratchDb';")
    Write-Ok "临时库导入完成，共 $($scratchTables[0]) 张表"

    # ---------- 3. 读取两边结构 ----------
    Write-Step "读取两边表结构（$ScratchDb 服务器侧  vs  $ReferenceDb 本地侧）"
    $SQL_TABLES = "SELECT table_name, IFNULL(engine,''), IFNULL(table_collation,''), IFNULL(table_comment,'') FROM information_schema.tables WHERE table_schema='{0}' AND table_type='BASE TABLE' ORDER BY table_name;"
    $SQL_COLUMNS = "SELECT table_name, column_name, ordinal_position, column_type, is_nullable, IFNULL(column_default,'~无默认值~'), IFNULL(extra,''), IFNULL(column_comment,''), IFNULL(collation_name,'') FROM information_schema.columns WHERE table_schema='{0}' ORDER BY table_name, ordinal_position;"
    $SQL_INDEXES = "SELECT table_name, index_name, IFNULL(non_unique,1), seq_in_index, IFNULL(column_name,''), index_type FROM information_schema.statistics WHERE table_schema='{0}' ORDER BY table_name, index_name, seq_in_index;"

    function Get-Meta {
        param([string]$Db)
        $result = @{ Tables = @{}; Columns = @{}; Indexes = @{} }
        foreach ($line in (Get-SqlRows -Db '' -Query ($SQL_TABLES -f $Db))) {
            $f = Split-Row $line
            $result.Tables[$f[0]] = [pscustomobject]@{ Engine = $f[1]; Collation = $f[2]; Comment = $f[3] }
        }
        foreach ($line in (Get-SqlRows -Db '' -Query ($SQL_COLUMNS -f $Db))) {
            $f = Split-Row $line
            $key = "$($f[0]).$($f[1])"
            $result.Columns[$key] = [pscustomobject]@{
                Table = $f[0]; Column = $f[1]; Ordinal = [int]$f[2]; Type = $f[3]
                Nullable = $f[4]; Default = $f[5]; Extra = $f[6]; Comment = $f[7]; Collation = $f[8]
            }
        }
        foreach ($line in (Get-SqlRows -Db '' -Query ($SQL_INDEXES -f $Db))) {
            $f = Split-Row $line
            $key = "$($f[0]).$($f[1]).$($f[3])"
            $result.Indexes[$key] = [pscustomobject]@{
                Table = $f[0]; Index = $f[1]; NonUnique = $f[2]; Seq = [int]$f[3]
                Column = $f[4]; IndexType = $f[5]
            }
        }
        return $result
    }

    $server = Get-Meta -Db $ScratchDb
    $local = Get-Meta -Db $ReferenceDb

    # ---------- 4. 对比 ----------
    Write-Step '对比结果'
    $report = New-Object System.Collections.Generic.List[string]
    $report.Add("# 结构差异报告")
    $report.Add("")
    $report.Add("- 服务器结构来源：$dumpPath（导入临时库 $ScratchDb）")
    $report.Add("- 本地目标库：$ReferenceDb")
    $report.Add("- 生成时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
    $report.Add("")

    $missingTables = @($local.Tables.Keys | Where-Object { -not $server.Tables.ContainsKey($_) } | Sort-Object)
    $extraTables = @($server.Tables.Keys | Where-Object { -not $local.Tables.ContainsKey($_) } | Sort-Object)

    # 4.1 表
    $report.Add("## 一、表（本地有、服务器没有 → 需要升级脚本新增）共 $($missingTables.Count) 张")
    $report.Add("")
    if ($missingTables.Count -eq 0) { $report.Add("（无，服务器表已齐全）") }
    foreach ($t in $missingTables) { $report.Add("- $t") }
    $report.Add("")
    if ($extraTables.Count -gt 0) {
        $report.Add("## 一之二、表（服务器有、本地没有）共 $($extraTables.Count) 张")
        $report.Add("")
        $report.Add("需人工确认：可能是过期表（可在服务器删除），也可能是本地漏了定义。")
        $report.Add("")
        foreach ($t in $extraTables) { $report.Add("- $t") }
        $report.Add("")
    }

    # 4.2 字段
    $missingCols = @()
    $extraCols = @()
    $changedCols = @()
    foreach ($key in ($local.Columns.Keys | Sort-Object)) {
        $lc = $local.Columns[$key]
        if (-not $server.Tables.ContainsKey($lc.Table)) { continue }   # 整张表都缺，归到表那一节
        if (-not $server.Columns.ContainsKey($key)) {
            $missingCols += [pscustomobject]@{ Table = $lc.Table; Column = $lc.Column; Ordinal = $lc.Ordinal; Type = $lc.Type; Comment = $lc.Comment }
            continue
        }
        $sc = $server.Columns[$key]
        $diffs = @()
        if ($sc.Type -ne $lc.Type) { $diffs += "类型：$($sc.Type) → $($lc.Type)" }
        if ($sc.Nullable -ne $lc.Nullable) { $diffs += "可空：$($sc.Nullable) → $($lc.Nullable)" }
        if ($sc.Default -ne $lc.Default) { $diffs += "默认值：$($sc.Default) → $($lc.Default)" }
        if ($sc.Extra -ne $lc.Extra) { $diffs += "额外属性：$($sc.Extra) → $($lc.Extra)" }
        if ($sc.Comment -ne $lc.Comment) { $diffs += "注释：$($sc.Comment) → $($lc.Comment)" }
        if ($sc.Collation -ne $lc.Collation) { $diffs += "排序规则：$($sc.Collation) → $($lc.Collation)" }
        if ($diffs.Count -gt 0) {
            $changedCols += [pscustomobject]@{ Table = $lc.Table; Column = $lc.Column; Diffs = ($diffs -join '；') }
        }
    }
    foreach ($key in ($server.Columns.Keys | Sort-Object)) {
        $sc = $server.Columns[$key]
        if (-not $local.Tables.ContainsKey($sc.Table)) { continue }
        if (-not $local.Columns.ContainsKey($key)) {
            $extraCols += [pscustomobject]@{ Table = $sc.Table; Column = $sc.Column; Type = $sc.Type }
        }
    }

    $report.Add("## 二、字段（本地有、服务器没有 → 需要升级脚本新增）共 $($missingCols.Count) 个")
    $report.Add("")
    if ($missingCols.Count -eq 0) { $report.Add("（无）") }
    foreach ($c in ($missingCols | Sort-Object Table, Ordinal)) {
        $report.Add("- $($c.Table).$($c.Column)  ($($c.Type))")
    }
    $report.Add("")
    $report.Add("## 三、字段（两边都有但定义不同 → 需要人工确认）共 $($changedCols.Count) 个")
    $report.Add("")
    if ($changedCols.Count -eq 0) { $report.Add("（无）") }
    foreach ($c in $changedCols) { $report.Add("- $($c.Table).$($c.Column)：$($c.Diffs)") }
    $report.Add("")
    if ($extraCols.Count -gt 0) {
        $report.Add("## 三之二、字段（服务器有、本地没有）共 $($extraCols.Count) 个")
        $report.Add("")
        $report.Add("需人工确认：本地模型可能已经不用这些字段了。")
        $report.Add("")
        foreach ($c in $extraCols) { $report.Add("- $($c.Table).$($c.Column)  ($($c.Type))") }
        $report.Add("")
    }

    # 4.3 索引
    $missingIdx = @()
    $extraIdx = @()
    foreach ($key in ($local.Indexes.Keys | Sort-Object)) {
        $li = $local.Indexes[$key]
        if ($li.Index -eq 'PRIMARY') { continue }
        if (-not $server.Tables.ContainsKey($li.Table)) { continue }
        if (-not $server.Indexes.ContainsKey($key)) {
            $missingIdx += $li
        }
    }
    foreach ($key in ($server.Indexes.Keys | Sort-Object)) {
        $si = $server.Indexes[$key]
        if ($si.Index -eq 'PRIMARY') { continue }
        if (-not $local.Tables.ContainsKey($si.Table)) { continue }
        if (-not $local.Indexes.ContainsKey($key)) { $extraIdx += $si }
    }
    # 索引名去重（多列索引会有多行）
    $missingIdxNames = @($missingIdx | Sort-Object Table, Index -Unique)
    $extraIdxNames = @($extraIdx | Sort-Object Table, Index -Unique)

    $report.Add("## 四、索引（本地有、服务器没有 → 需要升级脚本新增）共 $($missingIdxNames.Count) 个")
    $report.Add("")
    if ($missingIdxNames.Count -eq 0) { $report.Add("（无）") }
    foreach ($i in $missingIdxNames) { $report.Add("- $($i.Table).$($i.Index)") }
    $report.Add("")
    if ($extraIdxNames.Count -gt 0) {
        $report.Add("## 四之二、索引（服务器有、本地没有）共 $($extraIdxNames.Count) 个")
        $report.Add("")
        foreach ($i in $extraIdxNames) { $report.Add("- $($i.Table).$($i.Index)") }
        $report.Add("")
    }

    $hasDiff = ($missingTables.Count + $extraTables.Count + $missingCols.Count + $changedCols.Count +
        $extraCols.Count + $missingIdxNames.Count + $extraIdxNames.Count) -gt 0

    # 控制台输出（简版）
    Write-Host ""
    Write-Host ("    表：需要新增 {0} 张；服务器多出 {1} 张" -f $missingTables.Count, $extraTables.Count)
    Write-Host ("    字段：需要新增 {0} 个；定义不同 {1} 个；服务器多出 {2} 个" -f $missingCols.Count, $changedCols.Count, $extraCols.Count)
    Write-Host ("    索引：需要新增 {0} 个；服务器多出 {1} 个" -f $missingIdxNames.Count, $extraIdxNames.Count)
    if ($missingTables.Count -gt 0) { Write-Host ("    新增表：" + (($missingTables | Select-Object -First 8) -join '、') + $(if ($missingTables.Count -gt 8) { ' …' } else { '' })) }

    # ---------- 5. 生成升级 SQL 草稿（可选） ----------
    $skeletonFile = ''
    if ($EmitSkeleton) {
        Write-Step '生成升级 SQL 草稿'
        if (-not $SkeletonPath) {
            $SkeletonPath = Join-Path $repoRoot ("docs\sql\upgrade_draft_" + (Get-Date -Format 'yyyyMMdd_HHmm') + '.sql')
        }
        $sql = New-Object System.Collections.Generic.List[string]
        $sql.Add('-- ============================================================================')
        $sql.Add('-- 升级 SQL 草稿（由 tools/schema-diff/schema-diff.ps1 生成）')
        $sql.Add('-- ----------------------------------------------------------------------------')
        $sql.Add("-- 服务器结构：$dumpPath")
        $sql.Add("-- 本地目标库：$ReferenceDb")
        $sql.Add("-- 生成时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
        $sql.Add('--')
        $sql.Add('-- 【重要】这是草稿，只覆盖「表 / 字段 / 索引」三种结构差异：')
        $sql.Add('--   1. 字段定义不同的（报告第三节）不会自动生成，需要人工确认后手写 MODIFY；')
        $sql.Add('--   2. 表里的默认配置项（sys_config）、默认数据（如默认歌曲、邮件模板）不会包含；')
        $sql.Add('--   3. 合入正式升级脚本前，请补上脚本头部的说明与末尾的自检 SELECT；')
        $sql.Add('--   4. 建议先在本地临时库上跑一遍，再上服务器执行。')
        $sql.Add('-- ============================================================================')
        $sql.Add('')
        $sql.Add('SET NAMES utf8mb4;')
        $sql.Add("USE ``$ReferenceDb``;")
        $sql.Add('')
        $sql.Add('-- 幂等辅助过程：已存在则跳过')
        $sql.Add('DROP PROCEDURE IF EXISTS `bcblog_add_col`;')
        $sql.Add('DROP PROCEDURE IF EXISTS `bcblog_add_idx`;')
        $sql.Add('DELIMITER //')
        $sql.Add('CREATE PROCEDURE `bcblog_add_col`(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_def TEXT)')
        $sql.Add('BEGIN')
        $sql.Add('    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS')
        $sql.Add('                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN')
        $sql.Add('        SET @ddl = CONCAT(''ALTER TABLE `'', p_table, ''` ADD COLUMN `'', p_col, ''` '', p_def);')
        $sql.Add('        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;')
        $sql.Add('    END IF;')
        $sql.Add('END //')
        $sql.Add('CREATE PROCEDURE `bcblog_add_idx`(IN p_table VARCHAR(64), IN p_idx VARCHAR(64), IN p_def TEXT)')
        $sql.Add('BEGIN')
        $sql.Add('    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS')
        $sql.Add('                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_idx) THEN')
        $sql.Add('        SET @ddl = CONCAT(''ALTER TABLE `'', p_table, ''` ADD '', p_def);')
        $sql.Add('        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;')
        $sql.Add('    END IF;')
        $sql.Add('END //')
        $sql.Add('DELIMITER ;')
        $sql.Add('')

        if ($missingTables.Count -gt 0) {
            $sql.Add('-- ----------------------------------------------------------------------------')
            $sql.Add("-- 1. 新增表（$($missingTables.Count) 张）")
            $sql.Add('-- ----------------------------------------------------------------------------')
            foreach ($t in $missingTables) {
                $ddl = Get-CreateTable -Db $ReferenceDb -Table $t
                # 幂等化：CREATE TABLE -> CREATE TABLE IF NOT EXISTS
                $ddl = $ddl -replace '(?m)^CREATE TABLE ', 'CREATE TABLE IF NOT EXISTS '
                $sql.Add('')
                $sql.Add($ddl + ';')
            }
            $sql.Add('')
        }

        if ($missingCols.Count -gt 0) {
            $sql.Add('-- ----------------------------------------------------------------------------')
            $sql.Add("-- 2. 新增字段（$($missingCols.Count) 个）")
            $sql.Add('-- ----------------------------------------------------------------------------')
            $groupedCol = $missingCols | Group-Object Table
            foreach ($g in $groupedCol) {
                $ddl = Get-CreateTable -Db $ReferenceDb -Table $g.Name
                $bt = [char]96
                $ddlLines = @($ddl -split "`n")
                foreach ($c in ($g.Group | Sort-Object Ordinal)) {
                    $pattern = '^\s*' + $bt + [regex]::Escape($c.Column) + $bt + '\s+(?<def>.+?)(,)?\s*$'
                    $def = ''
                    foreach ($line in $ddlLines) {
                        $probe = $line.TrimEnd()
                        if ($probe -match $pattern) {
                            $def = $matches['def'].TrimEnd().TrimEnd(',')
                            break
                        }
                    }
                    if (-not $def) {
                        $sql.Add("-- 未能自动解析字段定义，请手工补：$($g.Name).$($c.Column)")
                        continue
                    }
                    # 位置：第一列用 FIRST，否则挂在参考库里的前一列后面
                    $previous = @($local.Columns.Values | Where-Object { $_.Table -eq $g.Name -and $_.Ordinal -eq ($c.Ordinal - 1) })
                    $position = 'FIRST'
                    if ($previous.Count -gt 0) { $position = "AFTER $bt$($previous[0].Column)$bt" }
                    $escaped = ($def + ' ' + $position).Replace("'", "''")
                    $sql.Add("CALL bcblog_add_col('$($g.Name)', '$($c.Column)', '$escaped');")
                }
            }
            $sql.Add('')
        }

        # 索引必须排在字段之后：新建的索引可能正好建在这次新加的字段上（例如 sys_user.uk_email）
        if ($missingIdxNames.Count -gt 0) {
            $sql.Add('-- ----------------------------------------------------------------------------')
            $sql.Add("-- 3. 新增索引（$($missingIdxNames.Count) 个）")
            $sql.Add('-- ----------------------------------------------------------------------------')
            $sql.Add('-- 注意：UNIQUE 索引要求数据里没有重复值，若服务器上存在重复数据会执行失败，请先清理')
            $groupedIdx = $missingIdxNames | Group-Object Table
            foreach ($g in $groupedIdx) {
                $ddl = Get-CreateTable -Db $ReferenceDb -Table $g.Name
                $bt = [char]96
                foreach ($i in ($g.Group | Sort-Object Index)) {
                    $pattern = '^\s*((UNIQUE|FULLTEXT|SPATIAL)\s+)?KEY\s+' + $bt + [regex]::Escape($i.Index) + $bt + '\s+\(.*$'
                    $def = ''
                    foreach ($line in ($ddl -split "`n")) {
                        $probe = $line.TrimEnd().TrimEnd(',')
                        if ($probe -match $pattern) { $def = $probe; break }
                    }
                    if ($def) {
                        $escaped = $def.Replace("'", "''")
                        $sql.Add("CALL bcblog_add_idx('$($g.Name)', '$($i.Index)', '$escaped');")
                    } else {
                        $sql.Add("-- 未能自动解析索引定义，请手工补：$($g.Name).$($i.Index)")
                    }
                }
            }
            $sql.Add('')
        }

        if ($changedCols.Count -gt 0) {
            $sql.Add('-- ----------------------------------------------------------------------------')
            $sql.Add("-- 4. 字段定义不同（$($changedCols.Count) 个）：需人工确认后手写 ALTER ... MODIFY")
            $sql.Add('-- ----------------------------------------------------------------------------')
            foreach ($c in $changedCols) { $sql.Add("-- $($c.Table).$($c.Column)：$($c.Diffs)") }
            $sql.Add('')
        }

        $sql.Add('-- 收尾：清理临时存储过程')
        $sql.Add('DROP PROCEDURE IF EXISTS `bcblog_add_col`;')
        $sql.Add('DROP PROCEDURE IF EXISTS `bcblog_add_idx`;')
        $sql.Add('')

        $skeletonDir = Split-Path -Parent $SkeletonPath
        if ($skeletonDir -and -not (Test-Path $skeletonDir)) { $null = New-Item -ItemType Directory -Path $skeletonDir -Force }
        [System.IO.File]::WriteAllText($SkeletonPath, (($sql -join "`n") + "`n"), (New-Object System.Text.UTF8Encoding($false)))
        $skeletonFile = (Resolve-Path -LiteralPath $SkeletonPath).Path
        Write-Ok "升级草稿已生成：$skeletonFile"
    }

    # ---------- 6. 写报告 ----------
    $report.Add('## 五、结论')
    $report.Add('')
    if ($hasDiff) {
        $report.Add('存在差异：按上面的「需要新增」清单补一份升级脚本即可（字段定义不同与服务器多出的对象需要人工确认）。')
    } else {
        $report.Add('结构与本地目标库完全一致，本次上线不需要新的数据库脚本。')
    }
    $report.Add('')
    $reportText = ($report -join "`n") + "`n"
    [System.IO.File]::WriteAllText($reportPath, $reportText, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host ""
    Write-Host "    差异报告已保存：$reportPath" -ForegroundColor Green
    if ($skeletonFile) { Write-Host "    升级草稿已保存：$skeletonFile" -ForegroundColor Green }

    if ($hasDiff) { Write-Note '结论：存在差异，需要生成/更新升级脚本' } else { Write-Ok '结论：结构一致，无需新的升级脚本' }

    exit $(if ($hasDiff) { 1 } else { 0 })
} catch {
    Write-Host "`n[出错] $($_.Exception.Message)" -ForegroundColor Red
    exit 2
} finally {
    $env:MYSQL_PWD = $oldPwd
    if (Test-Path $scratchSql) { Remove-Item -LiteralPath $scratchSql -Force -ErrorAction SilentlyContinue }
    if (-not $KeepScratch) {
        try {
            $env:MYSQL_PWD = $Password
            Invoke-Sql -Db '' -Sql "DROP DATABASE IF EXISTS ``$ScratchDb``;"
        } catch { }
        $env:MYSQL_PWD = $oldPwd
    } else {
        Write-Host "`n    已保留临时库 $ScratchDb（可用 Navicat 查看，不需要时自行删除）" -ForegroundColor Yellow
    }
}

# bcBlog 服务器部署指南

> 如果你使用宝塔 Linux 面板（腾讯云专享版 / OpenCloudOS 9），请看更具体的 [宝塔面板部署指南](deploy-bt-panel.md)。

本文档以「一台 Linux 服务器 + Nginx + Spring Boot jar + MySQL + 本地文件上传」为例，适合个人博客和低并发场景。

## 一、部署文件说明

| 文件 | 用途 |
| --- | --- |
| `docs/sql/bc_blog_full.sql` | 完整建表脚本（38 张表，仅结构，不含任何密钥数据） |
| `src/main/resources/application-prod.yml` | 生产环境配置，敏感信息通过环境变量注入 |
| `deploy/nginx.conf` | Nginx 站点配置示例 |
| `deploy/bcblog.service` | systemd 服务示例 |
| `deploy/bcblog.env.example` | 环境变量示例，复制成 `/etc/bcblog/bcblog.env` 使用 |

> 注意：`docs/sql` 下旧的 `bc_blog.sql` + `upgrade_*.sql` 只适合已有数据库的增量升级。全新服务器请直接使用 `bc_blog_full.sql`。

### 已有数据库的增量升级

`docs/sql/upgrade_*.sql` 是给已经跑起来的库用的，**按编号从小到大执行**即可。
每一个脚本都是幂等的（新增列前会先查 `information_schema`，配置项用 `INSERT IGNORE`），
所以"已经执行过的再跑一遍"不会出错，升级时不必逐个确认跑到哪了：

```bash
for f in docs/sql/upgrade_0{53..68}_*.sql; do
  echo "== $f"
  mysql -u bcblog -p bc_blog < "$f"
done
```

最近几个版本的增量（沙盒模块为主）：

| 脚本 | 内容 |
| --- | --- |
| `upgrade_053 ~ 056` | 角色「此刻的样子」、地区执行锁 |
| `upgrade_057 ~ 062` | 旅人委托板、委托/集市/纪闻自动刷新、单步进度按难度分档 |
| `upgrade_063 ~ 065` | 装备栏（物品槽位与战力加成）、集市商品装备字段、提示词完整动作 |
| `upgrade_066` | 免遭遇地点、遭遇对手记录、每步自查开关 |
| `upgrade_067` | 完成委托的战力成长表 + 闲置修行上限 |
| `upgrade_068` | 世界级「魔力条名称」（修仙世界可改成「灵力」，留空即没有这条属性） |

> 升级完记得**重启后端**；前端有改动时要重新构建 `dist`（或上传本地构建好的 `frontend/dist`）。
> 另外，后台很多开关与文案存在数据库里，不随代码走，升级后在「系统设置 / 沙盒」里核对一遍更稳妥。

## 二、服务器要求

- Linux（Ubuntu 20.04+/22.04、CentOS 7+/Rocky Linux 均可）
- JDK 8（Spring Boot 2.6.13 兼容 Java 8 ~ 17）
- MySQL 8
- Nginx
- Node.js 18+（仅在服务器上构建前端时需要，也可本地构建后上传 `dist`）
- 建议配置：2 核 2G 起步；有视频背景和图片时建议 2 核 4G + 独立数据盘

## 三、目录规划

```text
/data/bcblog/
├── bcBlog.jar          # 后端 jar
├── frontend/           # 前端 dist 内容
├── uploads/            # 上传的图片、背景、Logo
└── logs/               # 后端日志
```

## 四、安装基础环境（Ubuntu 示例）

```bash
sudo apt update
sudo apt install -y openjdk-8-jdk mysql-server nginx unzip

# 设置时区
sudo timedatectl set-timezone Asia/Shanghai
```

如果系统软件源没有 JDK 8，可以安装 Temurin 8 或使用 `apt install openjdk-17-jdk`（Spring Boot 2.6 也支持 Java 17）。

## 五、创建数据库

```sql
CREATE DATABASE bc_blog DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER 'bcblog'@'localhost' IDENTIFIED BY '换成你的强密码';
GRANT ALL PRIVILEGES ON bc_blog.* TO 'bcblog'@'localhost';
FLUSH PRIVILEGES;
```

导入完整表结构：

```bash
mysql -u bcblog -p bc_blog < docs/sql/bc_blog_full.sql
```

如果要把本地数据一起迁移，用 Navicat 从本地库导出「仅数据」再导入服务器即可。

## 六、打包

后端（本地或服务器执行，需 JDK 8）：

```bash
mvn clean package -DskipTests
# 产物：target/bcBlog-0.0.1-SNAPSHOT.jar
```

> 打包前请确认 `src/main/resources/application.yml` 中的数据库账号密码为空（或只使用 `application-prod.yml` + 环境变量）。

前端：

```bash
cd frontend
npm ci
npm run build
# 产物：frontend/dist
```

## 七、上传文件

```bash
sudo mkdir -p /data/bcblog/{frontend,uploads,logs}
sudo useradd -r -s /sbin/nologin bcblog
sudo chown -R bcblog:bcblog /data/bcblog

# 上传 jar 和前端
sudo cp target/bcBlog-0.0.1-SNAPSHOT.jar /data/bcblog/bcBlog.jar
sudo cp -r frontend/dist/* /data/bcblog/frontend/
```

## 八、配置 systemd

```bash
sudo mkdir -p /etc/bcblog
sudo cp deploy/bcblog.env.example /etc/bcblog/bcblog.env
sudo vi /etc/bcblog/bcblog.env          # 填入数据库密码
sudo chmod 600 /etc/bcblog/bcblog.env

sudo cp deploy/bcblog.service /etc/systemd/system/bcblog.service
sudo systemctl daemon-reload
sudo systemctl enable --now bcblog
sudo systemctl status bcblog
```

查看日志：

```bash
journalctl -u bcblog -f
```

## 九、配置 Nginx

```bash
sudo cp deploy/nginx.conf /etc/nginx/conf.d/bcblog.conf
sudo vi /etc/nginx/conf.d/bcblog.conf   # 修改域名和路径
sudo nginx -t
sudo systemctl reload nginx
```

HTTPS：

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

## 十、上线后需要配置的内容

以下配置保存在数据库里，不随代码走，需要在后台重新配置：

- 后台「系统设置」：站点名称、Logo、备案、SEO、首页标语、首页轮播
- 后台「接口管理 → AI 服务商」：SiliconFlow / DeepSeek 等 API Key
- 后台「接口管理 → 第三方接口」：天气、一言、IP 定位（百度/高德）、ACG Token
- 后台「接口管理 → 第三方接口 → Gitalk」：Client ID / Secret / 仓库 / 管理员
- 后台「站点管理 → 歌单管理」：网易云歌单 ID
- 后台「系统安全 → 管理员管理」：注册下级管理员

## 十一、Gitalk 线上配置

- OAuth App 的 `Authorization callback URL` 改成线上域名，例如 `https://your-domain.com/`
- `gitalk-comments` 仓库开启 Issues，并保持公开
- 后台配置好 Client ID / Secret / repo / owner / admin
- Gitalk 需要浏览器能访问 GitHub；服务器访问 GitHub 不稳定时，最近评论可能较慢

## 十二、外部接口网络要求

服务器需要能访问：

- GitHub API / OAuth
- SiliconFlow / DeepSeek
- 百度地图 / 高德地图
- UAPIS、ALAPI

浏览器需要能访问：

- UAPIS 天气、一言
- Meting 音乐接口
- ACG 图片、Live2D CDN

## 十三、备份建议

- 每天备份数据库：`mysqldump -u bcblog -p bc_blog > bc_blog_$(date +%F).sql`
- 每周备份 `/data/bcblog/uploads`
- 保留最近 7 天数据库备份、最近 2 周上传文件备份
- 关键配置 `/etc/bcblog/bcblog.env` 单独保存

## 十四、上线验证清单

- [ ] 前台首页、文章详情、流光忆庭、智库可访问
- [ ] 后台登录成功，默认密码已修改
- [ ] 新增文章、上传封面、正文图片显示正常
- [ ] 后台上传背景、看板娘、照片、资源正常
- [ ] Gitalk 能登录并发表评论，首页最近评论显示正常
- [ ] 后台仪表盘访问量、性能监控、系统信息显示正常
- [ ] 天气、音乐、一言等外部组件正常

## 十五、服务器与数据库服务商参考

> 价格随活动和地区变化较大，下面只是常见区间，购买前请以官网最新活动为准。

### 云服务器（VPS / 轻量应用服务器）

| 服务商 | 适合场景 | 大致价格 | 说明 |
| --- | --- | --- | --- |
| 腾讯云轻量应用服务器 | 国内个人博客首选 | 新用户约 50~150 元/年，常规约 50~100 元/月 | 活动多，国内访问快，需要备案 |
| 阿里云轻量应用服务器 / ECS 经济型 e | 国内个人博客 | 新用户约 99 元/年（2核2G），常规约 60~120 元/月 | 生态完善，备案流程成熟 |
| 华为云云耀云服务器 | 国内 | 与腾讯/阿里相近 | 活动期性价比不错 |
| 京东云轻量云主机 | 国内 | 常有低价活动 | 适合预算有限的个人项目 |
| 火山引擎云服务器 | 国内 | 新用户活动较多 | 字节系，国内访问快 |
| UCloud 快杰云主机 | 国内 | 约 50~120 元/月 | 稳定，适合中小项目 |
| 雨云（RainYun）/ 慈云数据 | 国内 / 香港 | 约 20~60 元/月 | 低价个人机，适合练手和小流量博客 |
| 搬瓦工（BandwagonHost） | 海外 | 约 $50~120/年 | CN2 线路，国内访问较稳，无需备案 |
| Vultr / DigitalOcean / Linode | 海外 | 约 $5~12/月 | 按小时计费，全球机房，无需备案 |
| Hetzner | 欧洲 | 约 €4~10/月 | 性价比很高，但国内访问延迟较高 |
| Oracle Cloud Free Tier | 海外 | 免费 | ARM 4C24G 免费额度，但申请和保号有难度 |

### 数据库服务

| 服务商 / 方案 | 适合场景 | 大致价格 | 说明 |
| --- | --- | --- | --- |
| 服务器自建 MySQL | 个人博客、低并发 | 0（含在服务器内） | 最省钱，注意备份和内存占用 |
| 阿里云 RDS MySQL 基础版 | 想省运维 | 约 10~60 元/月（1核1G~2G） | 自动备份、监控完善 |
| 腾讯云 TencentDB for MySQL | 国内 | 与阿里云相近 | 和腾讯云服务器同区内网互通 |
| 华为云 RDS for MySQL | 国内 | 与阿里云相近 | 适合已有华为云生态 |
| 火山引擎 RDS MySQL | 国内 | 活动期较低 | 新用户优惠多 |
| 京东云 RDS MySQL | 国内 | 活动期较低 | 适合预算有限的国内项目 |
| 阿里云 PolarDB MySQL Serverless | 流量波动大 | 按量计费 | 低流量时成本低，冷启动稍慢 |
| 腾讯云 TDSQL-C Serverless | 流量波动大 | 按量计费 | 同理，适合小站 |
| Aiven MySQL | 海外 | 有免费/低价档 | 免运维，国内访问可能偏慢 |

### 给 bcBlog 的推荐

- 预算最低：一台 2核2G 国内轻量服务器，自建 MySQL + Nginx + jar，活动价一年几十到一百多。
- 兼顾稳定：2核4G 轻量服务器 + 同区 RDS MySQL 基础版，数据库独立备份更安心。
- 不想备案：香港或海外 VPS（如搬瓦工、Vultr），但国内访问速度和 Gitalk 体验可能受影响。
- 流量很小、想先跑起来：2核2G 服务器完全够用，等访问量上来再升级 CPU/内存或迁到独立数据库。

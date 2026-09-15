# bcBlog 宝塔 Linux 面板部署指南（腾讯云专享版 / OpenCloudOS 9）

适用镜像：宝塔 Linux 面板 11.8.0 腾讯云专享版，系统 OpenCloudOS 9。

这套方案基于 Linux，和仓库里的 Linux 部署文件一致，比 Windows 方案更省资源、更省心。

## 一、目录规划

按宝塔面板的习惯使用 `/www/wwwroot`：

```text
/www/wwwroot/
├── bcblog/                 # 网站根目录（前端 dist + uploads）
│   ├── index.html          # 前端构建产物
│   ├── assets/
│   └── uploads/            # 上传的图片、背景、Logo
└── bcblog-api/             # 后端目录
    ├── bcBlog.jar
    └── logs/
```

## 二、面板初始化与安全

1. 登录宝塔面板后，先完成推荐的安全设置：
   - 修改面板默认端口（不要用 8888）
   - 设置强密码，开启面板 SSL
   - 开启「面板操作日志」「SSH 登录告警」
   - 能固定 IP 的话，给面板设置 IP 白名单
2. 腾讯云安全组：
   - 放行 `80`、`443`
   - 宝塔面板端口只对你的 IP 开放
   - `3306`、`8080` 不要对公网开放
3. OpenCloudOS 9 的防火墙由宝塔「安全」页面统一管理，一般不需要手动改 firewalld。

## 三、安装软件

在宝塔「软件商店」安装：

- Nginx（推荐 1.24+）
- MySQL 8.0
- Java 项目管理器（用于安装 JDK 并托管 jar）
- 可选：phpMyAdmin（导入 SQL 方便）
- 可选：腾讯云 COSFS（备份上传目录）

JDK 建议选 1.8；如果面板里装不了 JDK 8，也可以选 JDK 17，Spring Boot 2.6.13 支持 Java 8 ~ 17。

## 四、前端部署

1. 本地执行：

```bash
cd frontend
npm ci
npm run build
```

2. 在宝塔「网站」中新建站点：
   - 域名：你的域名
   - 根目录：`/www/wwwroot/bcblog`
   - PHP 版本：纯静态
3. 把 `frontend/dist` 里的内容上传到 `/www/wwwroot/bcblog`。
4. 在站点根目录下创建 `uploads` 目录：

```bash
mkdir -p /www/wwwroot/bcblog/uploads
chown -R www:www /www/wwwroot/bcblog
```

> 后端会往这个目录写上传文件，所以目录要对 Java 进程可写；宝塔里可以在「文件」中把权限设为 755，属主设为 www，或设置为 777（不推荐长期使用）。

## 五、Nginx 配置

把 `deploy/bt-nginx.conf` 的内容复制到：网站 → 设置 → 配置文件。

要点：

- `root` 指向 `/www/wwwroot/bcblog`
- `/api` 反代到 `127.0.0.1:8080`
- `/uploads` 直接指向 `/www/wwwroot/bcblog/uploads/`
- `client_max_body_size 200M`
- SPA 回退 `try_files $uri $uri/ /index.html;`

如果站点已经开启了 SSL，不要整段覆盖，只把 `location` 片段追加进去，避免覆盖宝塔自动生成的证书配置。

## 六、数据库

1. 宝塔「数据库」中新建数据库：
   - 数据库名：`bc_blog`
   - 字符集：`utf8mb4`
2. 新建一个业务账号（不要用 root），并只授权 `bc_blog` 库。
3. 导入完整表结构：

```bash
mysql -u bcblog -p bc_blog < docs/sql/bc_blog_full.sql
```

也可以用宝塔的 phpMyAdmin 或 Navicat 导入。

> `bc_blog_full.sql` 是完整的 37 张表结构，不需要再执行 `upgrade_*.sql`。
> 已经有数据的旧库请改用 `upgrade_20260916_batch.sql` 增量升级（一份脚本涵盖 001~033 全部改动，幂等可重复执行）。

## 七、部署后端 Java 项目

1. 本地打包：

```bash
mvn clean package -DskipTests
# 产物：target/bcBlog-0.0.1-SNAPSHOT.jar
```

2. 上传到服务器：

```text
/www/wwwroot/bcblog-api/bcBlog.jar
```

3. 在宝塔「网站 → Java项目」或「软件商店 → Java项目管理器」中新增项目：

| 配置项 | 填写内容 |
| --- | --- |
| 项目名称 | bcblog |
| 项目路径 | /www/wwwroot/bcblog-api |
| jar 文件 | bcBlog.jar |
| JDK | 1.8（或 17） |
| 端口 | 8080 |
| 启动参数 | `--spring.profiles.active=prod` |
| JVM 参数 | `-Xms256m -Xmx512m -Duser.timezone=Asia/Shanghai` |
| 开机自启 | 开启 |

环境变量（如果面板支持填写）：

```text
DB_URL=jdbc:mysql://127.0.0.1:3306/bc_blog?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
DB_USERNAME=bcblog
DB_PASSWORD=你的数据库密码
BCBLOG_UPLOAD_DIR=/www/wwwroot/bcblog/uploads
BCBLOG_LOG_FILE=/www/wwwroot/bcblog-api/logs/bcblog.log
SERVER_PORT=8080
```

如果面板不支持环境变量，可以把 `application-prod.yml` 里对应的值改成实际值，但不要把数据库密码提交到 Git。

4. 启动项目，查看日志确认没有报错。

## 八、HTTPS

在「网站 → SSL」中申请 Let's Encrypt 免费证书，或者上传腾讯云 SSL 证书，开启强制 HTTPS。

开启后记得更新 Gitalk OAuth App 的回调地址为线上域名，例如：

```text
https://your-domain.com/
```

## 九、Gitalk 与外部接口配置

以下配置保存在数据库里，需要登录后台重新配置：

- 系统设置：站点名称、Logo、备案、SEO、首页标语、首页轮播
- 接口管理 → AI 服务商：SiliconFlow / DeepSeek 等 API Key
- 接口管理 → 第三方接口：天气、一言、IP 定位（百度/高德）、ACG 封面 Token
- 接口管理 → 第三方接口 → Gitalk：Client ID / Secret / 仓库 / 管理员
- 站点管理 → 歌单管理：网易云歌单 ID

服务器需要能访问 GitHub、SiliconFlow/DeepSeek、百度/高德、UAPIS、ALAPI；浏览器需要能访问 UAPIS 天气、一言、Meting 音乐、ACG 图片和 Live2D CDN。

## 十、备份

推荐在宝塔「计划任务」中配置：

- 每天备份数据库 `bc_blog`
- 每周备份 `/www/wwwroot/bcblog/uploads`
- 保留最近 7 天数据库备份、最近 2 周上传文件备份

腾讯云专享版自带 COSFS 插件，可以把备份同步到 COS；也可以插件里配置 EdgeOne 做 CDN 加速。

## 十一、上线验证清单

- [ ] 前台首页、文章详情、流光忆庭、智库可访问
- [ ] 后台登录成功，默认密码已修改
- [ ] 新增文章、上传封面、正文图片显示正常
- [ ] 后台上传背景、看板娘、照片、资源正常
- [ ] Gitalk 能登录并发表评论，首页最近评论显示正常
- [ ] 后台仪表盘访问量、性能监控、系统信息显示正常
- [ ] 天气、音乐、一言等外部组件正常

## 十二、OpenCloudOS 9 注意事项

- 包管理使用 `dnf/yum`，不是 `apt`；不过用宝塔面板基本不需要手敲命令。
- JDK 8 可能不在默认源里，优先用宝塔「Java项目管理器」安装；装不了就用 JDK 17。
- OpenCloudOS 默认可能启用 SELinux，如果 Nginx 读不到前端文件或 Java 写不了上传目录，先执行 `getenforce` 查看；必要时把 SELinux 设为 permissive。
- 时间时区建议在面板里设置为 `Asia/Shanghai`，Java 启动参数里也带了 `-Duser.timezone=Asia/Shanghai`。

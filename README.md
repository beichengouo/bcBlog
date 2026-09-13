# bcBlog

前后端分离的个人博客项目基础骨架。

## 技术栈

- 后端：Spring Boot 2.6.13 / Java 8 / MyBatis-Plus 3.5.7 / Sa-Token / MySQL 8
- 前端：Vue 3 / Vite / Element Plus / Pinia / Axios

## 目录结构

```
bcBlog/
├── pom.xml                     # 后端 Maven 工程（仓库根目录即后端）
├── src/main/java/com/bc/bcblog/
│   ├── common/                 # 统一返回、分页、异常处理
│   ├── config/                 # Sa-Token、MyBatis-Plus、CORS 配置
│   ├── controller/             # 接口（admin 后台 / portal 前台）
│   ├── entity/                 # 实体
│   ├── mapper/                 # MyBatis-Plus Mapper
│   ├── service/                # 业务逻辑
│   └── init/                   # 启动初始化（默认管理员）
├── frontend/                   # Vue3 前端
└── docs/sql/bc_blog.sql        # 建库脚本
```

## 快速开始

1. 用 Navicat 执行 `docs/sql/bc_blog.sql` 创建数据库和表
2. 修改 `src/main/resources/application.yml` 里的数据库账号密码
3. 后端：IDEA 打开本项目，运行 `BcBlogApplication`
4. 前端：进入 `frontend/`，执行 `npm install` 后 `npm run dev`，访问 http://localhost:5173
5. 首次启动后端会自动创建管理员：`admin` / `Admin@123456`，登录后请尽快修改密码

## 接口约定

- 统一返回：`{ code, msg, data }`，`code=200` 表示成功
- 后台接口：`/api/admin/**`，需登录（Sa-Token，请求头 `Authorization` 携带令牌）
- 前台接口：`/api/portal/**`，游客可访问
- 登录加强：图形验证码、连续 5 次失败锁定 10 分钟、IP 频率限制、登录日志

# 个人博客系统

基于 **Vue 3 + Spring Boot 3.x** 的前后端分离个人博客系统，包含前台展示、后台管理、文章管理、分类标签、评论系统、友链管理等核心功能。

## 🚀 技术栈

### 前端
- **框架**: Vue 3.4 + Vite 5
- **语言**: TypeScript 5
- **路由**: Vue Router 4
- **状态管理**: Pinia 2
- **UI 组件库**: Element Plus 2.5
- **CSS**: UnoCSS (原子化 CSS)
- **HTTP**: Axios
- **Markdown**: markdown-it + highlight.js + DOMPurify
- **编辑器**: 自研 Markdown 编辑器（支持工具栏、预览、图片粘贴上传、全屏模式）
- **构建工具**: Vite

### 后端
- **框架**: Spring Boot 3.2 (JDK 17)
- **安全**: Spring Security + JWT
- **ORM**: MyBatis-Plus 3.5
- **数据库**: MySQL 8
- **缓存**: Redis 7 (Lettuce)
- **连接池**: Druid
- **API 文档**: Knife4j (Swagger 增强版)
- **工具库**: Hutool, Lombok, MapStruct
- **文件存储**: 本地存储 (可扩展至 OSS/MinIO)

### 部署
- **容器化**: Docker + Docker Compose
- **反向代理**: Nginx
- **数据库/缓存**: Docker 容器

## 📁 项目结构

```
个人博客/
├── blog-backend/                 # 后端项目
│   ├── src/main/java/com/blog/
│   │   ├── config/               # 配置类
│   │   ├── controller/           # 控制器层
│   │   │   ├── admin/            # 后台管理接口
│   │   │   └── front/            # 前台展示接口
│   │   ├── entity/               # 实体类
│   │   ├── mapper/               # MyBatis Mapper
│   │   ├── service/              # 业务接口
│   │   │   └── impl/             # 业务实现
│   │   ├── dto/                  # 数据传入对象
│   │   ├── vo/                   # 数据传出对象
│   │   ├── common/               # 通用类 (Result, 分页, 异常)
│   │   ├── interceptor/          # 拦截器
│   │   └── utils/                # 工具类
│   └── src/main/resources/
│       ├── application.yml       # 配置文件
│       └── db/init.sql           # 数据库初始化脚本
│
├── blog-frontend/                # 前端项目
│   ├── src/
│   │   ├── api/                  # API 接口封装
│   │   ├── components/           # 通用组件
│   │   │   ├── ArticleCard.vue   # 文章卡片
│   │   │   ├── CommentSection.vue # 评论区
│   │   │   ├── CommentItem.vue   # 评论项(支持嵌套回复)
│   │   │   ├── MarkdownEditor.vue # Markdown编辑器
│   │   │   └── Pagination.vue    # 分页组件
│   │   ├── layouts/              # 布局组件
│   │   │   ├── FrontLayout.vue   # 前台布局
│   │   │   └── AdminLayout.vue   # 后台布局
│   │   ├── views/                # 页面视图
│   │   │   ├── front/            # 前台页面
│   │   │   └── admin/            # 后台页面
│   │   ├── router/               # 路由配置
│   │   ├── stores/               # Pinia 状态管理
│   │   ├── utils/                # 工具函数
│   │   └── styles/               # 样式文件
│   └── public/                   # 静态资源
│
├── nginx/                        # Nginx 配置
├── docker-compose.yml            # Docker 编排
└── README.md                     # 项目文档
```

## ✨ 核心功能

### 前台 (读者端)
- 🏠 **首页**: 文章列表、轮播推荐、最新文章、热门文章、分类标签云
- 📖 **文章详情**: Markdown 渲染、代码高亮、目录导航、阅读量统计、上一篇/下一篇
- 🏷️ **分类/标签**: 按分类/标签筛选文章，支持分页
- 📅 **归档**: 按年份时间线展示所有文章
- 👤 **关于我**: 个人介绍、技能栈、博客统计
- 🔗 **友链**: 友情链接展示、申请指引
- 🔍 **搜索**: 关键词搜索、热门搜索推荐
- 🌙 **暗黑模式**: 支持手动切换/跟随系统
- 💬 **评论系统**: 支持二级回复、游客评论、邮箱通知(待集成)

### 后台 (管理端)
- 🔐 **JWT 认证**: 登录/登出、Token 刷新、记住我
- 📝 **文章管理**: 增删改查、草稿/发布、置顶、Markdown 编辑器、图片粘贴上传
- 📂 **分类管理**: 树形结构、排序、文章统计
- 🏷️ **标签管理**: 颜色标识、文章统计、标签云
- 💬 **评论管理**: 审核通过/拒绝、管理员回复、批量删除
- 🔗 **友链管理**: 增删改查、显示/隐藏、排序
- 👤 **个人中心**: 修改资料、上传头像、修改密码
- 📊 **数据统计**: 文章/分类/标签/评论总数、浏览趋势图、发布趋势图

## 🛠️ 本地开发

### 环境要求
- JDK 17+
- Node.js 20+
- MySQL 8+
- Redis 7+
- Maven 3.9+

### 后端启动
```bash
cd blog-backend

# 1. 创建数据库并执行初始化脚本
mysql -u root -p < src/main/resources/db/init.sql

# 2. 修改 application.yml 中的数据库/Redis 配置

# 3. 启动
mvn spring-boot:run
# 或使用 IDE 运行 BlogApplication.main()
```

### 前端启动
```bash
cd blog-frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 访问 http://localhost:3000
```

### Docker 一键启动 (推荐)
```bash
# 根目录下
docker-compose up -d

# 服务地址:
# 前端: http://localhost:3000
# 后端 API: http://localhost:8080/api
# API 文档: http://localhost:8080/api/doc.html
# 数据库: localhost:3306
# Redis: localhost:6379
```

## 📦 生产环境部署

### 1. 打包
```bash
# 后端
cd blog-backend && mvn clean package -DskipTests

# 前端
cd blog-frontend && npm run build
```

### 2. Docker 镜像构建
```bash
docker-compose build
docker-compose up -d
```

### 3. Nginx 配置 (生产环境)
- 配置 SSL 证书 (Let's Encrypt)
- 开启 Gzip 压缩
- 配置静态资源缓存
- 限制上传文件大小

### 4. 环境变量配置
```yaml
# 后端 application-prod.yml
spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST}:3306/blog_db
    username: ${MYSQL_USER}
    password: ${MYSQL_PASSWORD}
  redis:
    host: ${REDIS_HOST}
    password: ${REDIS_PASSWORD}
jwt:
  secret: ${JWT_SECRET}  # 必须修改为强随机字符串
file:
  upload:
    domain: https://your-domain.com
```

## 🔧 关键设计

### 统一返回格式
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

### JWT 认证流程
1. 登录 → 后端签发 Token (HS512)
2. 前端存 localStorage + Pinia
3. 请求拦截器携带 `Authorization: Bearer xxx`
4. 后端拦截器校验、解析 userId 存 ThreadLocal
5. Redis 存 Token 实现踢下线/续期

### 文章浏览量优化
- Redis 计数 (`article:view:{id}`)
- 定时任务/阈值触发同步到 MySQL
- 详情页合并显示: `DB计数 + Redis计数`

### 评论防刷
- 图形验证码 (待集成)
- Redis + 注解限流
- 敏感词过滤 (待集成)

### XSS 防护
- 前端 DOMPurify 过滤 Markdown 渲染 HTML
- 后端输入校验 + 输出转义

## 📚 API 文档

启动后端后访问: `http://localhost:8080/api/doc.html`

主要接口分组:
- `auth` - 认证授权
- `front/*` - 前台展示接口
- `admin/*` - 后台管理接口
- `files` - 文件上传

## 🤝 贡献指南

1. Fork 本仓库
2. 创建特性分支: `git checkout -b feat/xxx`
3. 提交更改: `git commit -m 'feat: add xxx'`
4. 推送分支: `git push origin feat/xxx`
5. 提交 Pull Request

## 📄 许可证

MIT License

## 🙏 致谢

- [Vue.js](https://vuejs.org/)
- [Spring Boot](https://spring.io/projects/spring-boot)
- [Element Plus](https://element-plus.org/)
- [UnoCSS](https://unocss.dev/)
- [MyBatis-Plus](https://baomidou.com/)
- [Knife4j](https://doc.xiaominfo.com/)
- [highlight.js](https://highlightjs.org/)
- [markdown-it](https://github.com/markdown-it/markdown-it)
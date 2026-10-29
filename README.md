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
│   ├── src/test/java/            # 单元测试
│   └── src/main/resources/
│       ├── application.yml       # 主配置
│       ├── application-dev.yml   # 本地开发配置
│       ├── application-prod.yml  # 生产环境配置（Docker 部署时激活）
│       └── db/blog_db.sql        # 数据库初始化脚本（7 张表 + 种子数据）
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
│   ├── src/vite-env.d.ts         # Vite 客户端类型声明
│   ├── .eslintrc.cjs             # ESLint 配置
│   └── public/                   # 静态资源
│
├── .github/workflows/ci.yml      # CI：后端测试 + 前端类型检查/lint/构建
├── nginx/                        # Nginx 配置
├── docker-compose.yml            # Docker 编排
└── README.md                     # 项目文档
```

## ✨ 核心功能

### 前台 (读者端)
- 🏠 **首页**: 文章列表、轮播推荐、最新文章、热门文章、分类标签云
- 📖 **文章详情**: Markdown 渲染、代码高亮、目录导航、阅读量统计、上一篇/下一篇（从标签/分类页进入时在该集合内切换）
- 🏷️ **分类/标签**: 按分类/标签筛选文章，支持分页
- 📅 **归档**: 按年份时间线展示所有文章
- 👤 **关于我**: 个人介绍、技能栈、博客统计（需登录访问）
- 🔗 **友链**: 友情链接展示、申请指引
- 🔍 **搜索**: 关键词搜索、热门搜索推荐
- 🌙 **暗黑模式**: 支持手动切换/跟随系统
- 💬 **评论系统**: 支持二级回复、游客评论、算术验证码 + Redis IP 限流、邮箱通知(待集成)

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
mysql -u root -p < src/main/resources/db/blog_db.sql

# 2. 数据库/Redis 连接可用环境变量覆盖（默认 dev profile 连 localhost）
#    SPRING_DATASOURCE_URL / SPRING_DATASOURCE_USERNAME / SPRING_DATASOURCE_PASSWORD
#    SPRING_DATA_REDIS_HOST / SPRING_DATA_REDIS_PORT

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

### 代码质量检查
```bash
# 前端（均在 blog-frontend 目录）
npm run type-check   # vue-tsc 类型检查，0 错误为通过
npm run lint:check   # ESLint 检查
npm run lint         # ESLint 检查并自动修复
npm run build        # 构建

# 后端（在 blog-backend 目录）
mvn test             # 单元测试
mvn package          # 打包（默认执行测试）

# Docker 构建时也会跑测试，测试失败则镜像构建失败
docker compose build backend
# 需要跳过测试的快速重建：
docker compose build --build-arg SKIP_TESTS=true backend
```

### Docker 一键启动 (推荐)
```bash
# 根目录下
docker-compose up -d

# 服务地址:
# 前端: http://localhost:3000
# 后端 API: http://localhost:8080/api
# API 文档: http://localhost:8080/api/doc.html
# 数据库: localhost:3307   (宿主机 3306 已被本机 MySQL 5.7 占用，容器内仍为 3306)
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

`application-prod.yml` 中所有生产配置均支持环境变量覆盖（优先级：**环境变量 > application-prod.yml > application.yml**）。
`docker-compose.yml` 已注入下列默认环境变量，通常无需额外配置：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | 激活 prod profile；本地默认 `dev` |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://mysql:3306/blog_db?...` | 数据库连接串 |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `root` / `123456` | 数据库账号 |
| `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` | `redis` / `6379` | Redis 地址（容器内为服务名 `redis`） |
| `SPRING_DATA_REDIS_PASSWORD` | 空 | Redis 密码 |
| `FILE_UPLOAD_PATH` | `/app/uploads/` | 上传目录（容器内） |
| `FILE_UPLOAD_DOMAIN` | `http://localhost:8080/api` | 上传文件访问域名前缀 |
| `JWT_SECRET` | 见 `application.yml` | **生产必须覆盖**，至少 32 字节 |
| `JWT_EXPIRATION` | `86400000`（24h） | Token 有效期（毫秒） |
| `MYBATIS_LOG_IMPL` | `NoLoggingImpl` | 生产默认不打印 SQL；排查时设为 `org.apache.ibatis.logging.stdout.StdOutImpl` |
| `KNIFE4J_ENABLE` | `true` | 是否开启 API 文档 |
| `LOG_LEVEL_APP` / `LOG_LEVEL_SECURITY` | `info` | 日志级别 |

> 未设置 `SPRING_PROFILES_ACTIVE` 时默认使用 `dev` profile（本地开发，连 `localhost` 的 MySQL/Redis）。

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
1. 登录 → 后端签发 Token（基于 JJWT，签名密钥需 ≥32 字节，生产环境强制覆盖 `JWT_SECRET`）
2. 前端存 localStorage + Pinia
3. 请求拦截器携带 `Authorization: Bearer xxx`
4. 后端拦截器校验、解析 userId 存 ThreadLocal
5. Redis 存 Token 实现踢下线/续期

### 文章浏览量优化
- 数据库原子自增 (`article.view_count`)，SQL 层直接读取排序，避免只存 Redis 导致 ORDER BY 失效
- Redis 仅用于每日趋势统计 (`view:daily:{date}`)，仪表盘浏览趋势图数据来源

### 评论防刷
- 算术验证码（一次性，5 分钟过期，由 CaptchaUtil 生成与校验）
- Redis + IP 频率限制（同一 IP 10 分钟内最多 5 条游客评论）
- 敏感词过滤 (待集成)

### XSS 防护
- 前端 DOMPurify 过滤 Markdown 渲染 HTML
- 后端输入校验 + 输出转义

## 🤖 AI 站内问答（RAG）

基于本博客已发布文章构建的检索增强问答，入口在顶部导航「AI 问答」（`/ask`）：

- **索引构建**：文章发布/编辑/下架/删除后自动切块并重建索引（Markdown 按标题层级切段、记录标题路径、相邻块重叠）；索引钩子用「事务提交后 + 异步事件」实现，避免事务回滚后索引里残留未发布内容
- **检索**：内存 BM25 关键词检索（倒排表 + 词频预计算），中文用字符 bigram 分词，零第三方分词依赖
- **检索质量优化**：技术领域同义词扩展缓解「用户问法 ≠ 文章写法」的词汇鸿沟；相关性阈值剔除弱相关块，降低拼接式幻觉
- **生成**：检索结果拼成带编号的上下文喂给大模型，答案中标注 `[n]` 出处，前端可点击定位到原文
- **防幻觉**：Prompt 四层约束（角色限定 / 资料边界 / 无答案固定话术 / 引用格式）+ 规则「严禁编造资料中未出现的类名、依赖或行为」
- **模型可切换**：`rag.provider=mock` 用本地模拟实现（无 API Key 也能跑通全链路），`rag.provider=openai` 接 MiniMax / DeepSeek / 通义兼容模式 / Ollama 等任意 OpenAI 兼容服务
- **可靠性**：外部模型调用带 3 次重试；状态码细分（限流 429 / 参数 400 / 模型不可用 503 / 未知 500）
- **成本控制**：单 IP 每分钟 20 次、每天 500 次双层限流，上下文按字符预算截断；**已登录的管理员豁免限流**
- **对话留存**：问答记录存 sessionStorage，刷新页面可恢复；关标签页自动清空

检索层已抽象为 `ChunkRetriever` 接口，第二阶段替换为向量检索（pgvector / Milvus + Embedding）时，Prompt、编排逻辑与前端均无需改动。

> **模型接入记录**：已实测接入 MiniMax（`MiniMax-M3.1-Flash`，OpenAI 兼容协议）。
> 带推理能力的模型有两种响应形态 —— 答案与思考过程分离、或思考过程混在 `content` 里，
> 客户端做了兼容处理。另有 `GET /api/admin/rag/models` 与 `POST /api/admin/rag/test-llm`
> 两个接口用于确认模型可用性与连通性。

## 🗺️ 规划中 / 毕设扩展

### 第二阶段：向量检索与混合检索

- **索引构建**：在现有切块基础上加 Embedding 向量化，存入向量数据库（Milvus、pgvector）
- **混合检索**：BM25 关键词召回 + 向量召回，用 RRF 融合后重排
- **注意事项**：控制 LLM API 调用成本、处理幻觉（通过检索源限制回答范围）、数据隐私（文章内容上传第三方模型前需评估）

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
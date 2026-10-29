# 个人博客系统 · 开发细节 & 面试 Q&A

> 配套文档：`开发文档.md`（架构与接口契约）、`README.md`（功能概览）
> 用途：① 复盘本项目的技术决策与踩坑；② 面试前的应答准备
> 所有数据均取自代码库实测，非估算

---

## 目录

- [零、项目速览（30 秒版）](#零项目速览30-秒版)
- [一、开发细节](#一开发细节)
  - [1.1 整体架构](#11-整体架构)
  - [1.2 鉴权体系（最核心的自研部分）](#12-鉴权体系最核心的自研部分)
  - [1.3 数据层与计数口径](#13-数据层与计数口径)
  - [1.4 性能设计](#14-性能设计)
  - [1.5 安全设计](#15-安全设计)
  - [1.6 前端工程](#16-前端工程)
  - [1.7 部署与运维](#17-部署与运维)
- [二、Bug 复盘（面试讲"踩过坑"比讲"做了什么"更显经验）](#二bug-复盘面试讲踩过坑比讲做了什么更显经验)
- [三、Q&A](#三qa)
  - [A. 架构与设计](#a-架构与设计)
  - [B. Spring Boot / Spring](#b-spring-boot--spring)
  - [C. MyBatis-Plus 与数据库](#c-mybatis-plus-与数据库)
  - [D. 安全](#d-安全)
  - [E. 性能](#e-性能)
  - [F. 前端](#f-前端)
  - [G. Docker 与部署](#g-docker-与部署)
  - [H. 工程化与测试](#h-工程化与测试)
  - [I. AI 辅助开发（必问）](#i-ai-辅助开发必问)
  - [J. 取舍与不足（必问）](#j-取舍与不足必问)
- [四、附录](#四附录)

---

## 零、项目速览（30 秒版）

**一句话**：面向单博主的个人博客系统，前后端分离，读者端与博主端双 API 命名空间隔离。

| 项 | 数据 |
|---|---|
| 技术栈 | Vue 3.4 + TS 5.9 + Vite 5 + Element Plus 2.5 + UnoCSS / Spring Boot 3.2.5 + MyBatis-Plus 3.5.9 + MySQL 8 + Redis 7 |
| 运行环境 | JDK 17+（Framework 6.1）、Nginx、MySQL、Redis、MiniMax（OpenAI 兼容） |
| 代码规模 | 后端 103 个 Java 文件 / 8580 行；测试 12 个文件 / 1652 行；前端 51 个文件 / 11353 行 |
| 功能 | 16 个控制器、**55 条路径 / 67 个接口**、20 个前端页面、5 个公共组件、11 个 API 模块 |
| 附加模块 | 站内 AI 问答（RAG）：切块 + BM25 检索 + 上下文拼装 + 大模型生成 + 带出处返回 |
| 数据 | 7 张表（article / article_tag / category / comment / friend_link / tag / user） |
| 测试 | 57 个 JUnit 用例（含 21 个文章服务用例） |
| 部署 | Docker Compose 五服务 + GitHub Actions CI |

**最有讲头的三个点**（面试时优先展开）：

1. **自研 JWT + Redis 双因子鉴权**，并修复了百分号编码的角色绕过漏洞
2. **N+1 查询重构**（逐条查 → 3 次批量）与**浏览量存库而非存 Redis** 的取舍
3. **纵深防御**：双层 XSS 净化、文件魔数校验、验证码 + IP 限流组合

---

## 一、开发细节

### 1.1 整体架构

```
浏览器
  │  :80
  ▼
Nginx（网关层）
  ├── /            → frontend 容器（其内置 Nginx 提供镜像内 dist）
  ├── /api/**      → backend 容器（Spring Boot 内嵌 Tomcat，context-path=/api）
  └── /uploads/**  → 直接读 backend_uploads 卷
                          │
              ┌───────────┴───────────┐
        MySQL 8                    Redis 7
   （业务数据，逻辑删除）    （Token/验证码/限流/趋势计数）
```

**API 命名空间隔离**是刻意设计：

| 前缀 | 面向 | 鉴权 |
|---|---|---|
| `/front/**` | 读者 | 公开（评论接口为"可选鉴权"：游客可评论，带 token 时识别管理员） |
| `/admin/**` | 博主 | 强制 `ROLE_ADMIN`，普通用户 403 |
| `/auth/**` | 登录/验证码/登出 | 公开 |

**为什么不用 Spring Security 的 `authorizeHttpRequests`**：前台需要"同一路径、两种身份"——游客提交评论走待审核，管理员提交直接通过并标记博主。静态授权规则表达不了这种语义，所以自研拦截器。

### 1.2 鉴权体系（最核心的自研部分）

#### 登录流程

```
POST /api/auth/login { username, password, rememberMe }
  ├─ checkLoginRateLimit(username)      失败 5 次 → 锁定 15 分钟（滑动窗口）
  ├─ BCrypt 校验密码
  │    └─ 用户不存在与密码错误返回同一提示，防用户名枚举
  ├─ status != 1 → 403「账号已被禁用」
  ├─ JwtUtil.generateToken(userId, username, role)
  │    └─ 密钥长度 < 32 字节 → 启动直接失败（JJWT 强校验 HMAC 密钥长度）
  ├─ Redis 写 token:{token}，TTL 24h（记住我则 30 天）
  └─ Redis 集合 user:tokens:{userId} 记录该用户全部 token（TTL 30 天）
```

#### 请求鉴权流程

```
OPTIONS 预检                → 直接放行
无 token                     → 401
JWT 签名无效 / 已过期        → 401
Redis 无 token:{token}       → 401（已登出 / 被踢）
解析 userId → 写入 ThreadLocal
token 剩余有效期 < 1h        → Redis TTL 续期 24h
路径命中 /admin/** 且 role != ROLE_ADMIN → 403
```

#### 三个关键设计

**① 双路径判定（安全修复）**

Spring 路由匹配使用**解码后**的路径，而 `request.getRequestURI()` 返回**未解码**的原始值。若直接用原始 URI 做前缀判断，攻击者可用 `%61` 编码让 `/api/%61dmin/articles` 同时满足「路由命中后台」与「前缀不匹配」，从而绕过角色校验。

修复：`isAdminPath` 对原始路径与解码后路径**各判定一次**，任一命中 `/admin/` 前缀即要求 `ROLE_ADMIN`。`SecurityConfig.JwtFilterWrapper.shouldNotFilter()` 同样处理。

回归测试：`JwtAuthenticationInterceptorAdminPathTest` 覆盖 普通用户/管理员 × 原始路径/编码路径 四种组合。

**② ThreadLocal 必须清理**

`afterCompletion` 清理 `USER_ID_THREAD_LOCAL`。但 `JwtFilterWrapper.doFilterInternal` 里校验失败时 `preHandle` 提前返回，`afterCompletion` **不会执行** —— 所以在 `finally` 中强制清理，否则 Tomcat 复用线程时身份会串到下一个请求。

**③ Token 失效机制**

| 场景 | 处理 |
|---|---|
| 主动登出 | 删 `token:{token}` + 从 `user:tokens:{userId}` 移除 |
| 修改密码 / 修改密保 / 密保重置 | 遍历集合删除该用户全部 token |
| 账号被禁用 | `status` 校验在登录时，存量 token 需等自然过期（已知不足） |

> `user:tokens:{userId}` 集合**必须设置 TTL**，否则自然过期的 token 不会自动移出，集合无限增长。

### 1.3 数据层与计数口径

#### 核心口径：计数只统计"已发布"

| 计数器 | 口径 | 维护时机 |
|---|---|---|
| `category.article_count` | 仅 `status = 1` | 文章增删改、上下架 |
| `tag.article_count` | 仅 `status = 1` | 同上 |
| `article.comment_count` | 仅 `status = 1` 且 `deleted = 0` 的评论（**含回复**） | 审核状态变化时重算 |

`syncTags(articleId, oldTagIds, newTagIds, oldPublished, newPublished)` 把关联表维护与计数维护**分开**：

- **关联表**始终按实际标签维护，与发布状态无关
- **计数**只受发布状态影响 —— 草稿文章即使挂了标签也不计入 `tag.article_count`

#### 发布时间状态机

`publish_time` 用 `updateStrategy = FieldStrategy.ALWAYS` 标注。原因：MyBatis-Plus 默认的 `NOT_NULL` 更新策略会跳过 null 字段，导致下架时无法把 `publish_time` 清空。

| 状态迁移 | publish_time |
|---|---|
| 草稿 → 发布 | 写入当前时间 |
| 发布 → 草稿 | **置 NULL** |
| 发布 → 发布 | 保持原值（编辑内容、批量发布都不重置） |

#### 编辑时的 null 语义

`BeanUtils.copyProperties` 会把 DTO 中的 null 覆盖进实体，因此显式区分：

| 字段 | 未传（null） | 传空数组 / 空串 |
|---|---|---|
| `status` | 保持原状态 | 视为切换到该状态 |
| `tagIds` | 保持原有标签关联 | 明确清空所有标签 |

不处理的后果：编辑文章未传 status 会导致 `publish_time` 被清空（ALWAYS 策略），且分类/标签计数按"变为草稿"错误地 -1。

#### 逻辑删除

所有表带 `deleted` 字段 + `@TableLogic`，删除转为 `deleted = 1`，查询自动追加 `deleted = 0`。需要查已删除数据时用手写 SQL（如 `selectByIdsIncludeDeleted`）。

### 1.4 性能设计

#### ① N+1 查询重构

**问题**：文章列表每篇文章要查 1 次分类 + 1 次标签关联 + N 次标签实体。

**解决**：`convertToVOList()` 压缩为 3 次批量查询

```
1. selectActiveByIds(categoryIds)                  → 分类名
2. selectTagIdsByArticleIds(articleIds)            → 标签关联
3. selectBatchIds(tagIds)                          → 标签实体
```

#### ② 浏览量存库而非存 Redis

**决策**：浏览量直接原子写库 `UPDATE article SET view_count = view_count + 1 WHERE id = ? AND deleted = 0`。

**理由**：若只累加 Redis，`article.view_count` 永远为 0，而 `ORDER BY view_count` 发生在 SQL 层，读不到 Redis 值，**热门排序会完全失效**。Redis 只留作每日趋势统计（`view:daily:{yyyy-MM-dd}`），供仪表盘图表使用。

**代价**：写库频率高于读缓存，高并发浏览时数据库成为瓶颈。适用于单博主场景（浏览量级不大）；若要支撑高并发，需改为主从分离 + Redis 批量回写。

**写入失败被捕获**，不阻断页面访问 —— 浏览量统计属于非关键路径。

#### ③ 归档不走分页

`GET /front/articles/archive` 走专用方法 `getArchiveArticles()`。分页接口的 `pageSize` 上限是 100，用分页取归档会在文章超过 100 篇时**静默丢数据**。

排序只按 `publish_time`，**不加 `is_top`**：否则置顶的旧文章会排到最前，导致按首次出现顺序建立的年份分组顺序错乱。

#### ④ 评论树缓存

评论树以 JSON 存入 Redis，TTL 10 分钟；任何评论变更（新增/审核/删除）主动删除缓存键 `comment:article:{articleId}`。

#### ⑤ 分页参数归一化

`PageQuery` 的 getter 做归一化：`pageNum <= 0` 回落 1，`pageSize <= 0` 回落 10，上限 100。防止 `?limit=-1` 造成 SQL 异常，也避免单次拉取过多数据。

#### ⑥ 前端构建分包

`vite.config.ts` 手动分包：

```ts
manualChunks: {
  'vue-vendor': ['vue', 'vue-router', 'pinia'],
  'element-plus': ['element-plus'],
  'markdown': ['markdown-it', 'highlight.js', 'dompurify']
}
```

所有页面组件**懒加载**（`() => import(...)`）。产物按需加载，首屏不必下载 markdown 编辑器相关的 1MB+ chunk。

### 1.5 安全设计

#### 双层 XSS 防护

| 层 | 实现 | 位置 |
|---|---|---|
| 服务端（入库前） | Jsoup 白名单净化 | `HtmlSanitizer.sanitize()`，评论入库时调用 |
| 客户端（渲染前） | DOMPurify 白名单净化 | `safeHtml()`，全站 `v-html` 统一入口 |

**服务端不可省略**：前端净化只发生在浏览器里，绕过前端直接调 API 的客户端拿到的是未净化内容；将来新增导出、邮件通知等功能也会暴露未净化数据。

**服务端白名单**：`Safelist.relaxed()` + `img/pre/code/blockquote/hr/del/s/mark`；`img[src]` 允许 `http/https/data`；`a[href]` 移除 `javascript/vbscript/data` 协议。

**客户端白名单**：显式列举允许的标签与属性，`FORBID_ATTR: ['style']`（DOMPurify 默认允许 style，可被用于 `position:fixed` 全屏覆盖钓鱼），`ALLOW_DATA_ATTR: false`。

#### 文件上传：按文件头魔数判定

```
jpg/jpeg: FF D8 FF
png:      89 50 4E 47 0D 0A 1A 0A
gif:      47 49 46 38
bmp:      42 4D
webp:     偏移 0 处 RIFF + 偏移 8 处 WEBP（需读满 12 字节）
```

**刻意不看客户端扩展名**：PNG 内容存成 `.jpg` 极常见（网页另存、表情包），按扩展名比对会误判拒掉正常图片。存盘扩展名由**检测结果**决定，永远是安全值。

文件名用 UUID + 日期目录（`yyyy/MM/dd`），杜绝路径穿越与同名冲突。

#### 限流与验证码

| 场景 | 键 | 规则 |
|---|---|---|
| 登录失败 | `login:fail:{username}` | 5 次 → 锁定 15 分钟（滑动窗口） |
| 密保重置 | `login:fail:forget:{username}` | 同上 |
| 游客评论 | `comment:rate:{ip}` | 10 分钟内最多 5 条 |

验证码：算术题（1~9 + 1~9），答案存 Redis（`captcha:{uuid}`，TTL 5 分钟），**无论对错都立即删除**（"先删后比对"，防止重放）。

> 算术验证码对自动化脚本几乎无防御力（脚本可自行求解），它的定位是"抬高随手灌水成本"，真正挡灌水的是 IP 限流。两者必须组合。

#### 客户端 IP 解析

服务在 Nginx 之后，`getRemoteAddr()` 拿到的是反向代理 IP，直接限流会误伤所有用户。`IpUtil` 优先级：`X-Forwarded-For` 首地址 → `X-Real-IP` → `getRemoteAddr()`。

#### CORS

`allowedOriginPatterns` + 显式白名单（`CORS_ALLOWED_ORIGINS` 环境变量），**不使用 `*`**。

之前是 `allowedOriginPatterns("*") + allowCredentials(true)`，会回显任意 Origin。当前 Token 存 `sessionStorage`（不随请求自动携带）危害有限，但一旦改用 Cookie 鉴权就会立刻变成账号接管。

#### 密码存储

密码与密保答案均 **BCrypt**（强度 10），`User` 实体中 `password` 不会出现在任何 VO。

#### 请求体容错

`spring.jackson.deserialization.fail-on-unknown-properties: false` —— 忽略请求体中的未知字段。历史上曾因前端多带一个已废弃的 `imageUrl` 字段导致评论提交全部失败。

### 1.6 前端工程

#### 登录态存储

Token 与用户信息存 `sessionStorage`（关闭标签页即失效），写入时**清理 `localStorage` 中的历史残留**，避免共享电脑自动登录。

`logout(notifyServer = true)` 的 `notifyServer = false` 用于 401 场景，避免二次请求再次 401 形成递归。

#### 路由守卫

`setupRouterGuard` 注册 `beforeEach`：

```
1. NProgress.start() + 设置 document.title
2. requiresAuth：
   ├─ 未登录 → userStore.init() 恢复 sessionStorage → fetchProfile()
   │           失败 → 重定向 /login?redirect=原路径
   ├─ 已登录但 userInfo 为空（登录后立刻 replace 的场景）→ 主动 fetchProfile()
   └─ roles 不匹配 → 提示「没有权限访问该页面」→ 跳 /home
3. to.name === 'Login' 且已登录 → 跳 Dashboard
```

> 角色不匹配时跳 `/home` 而非 `/admin/dashboard`：`Dashboard` 自身也带 `roles: ['ROLE_ADMIN']`，跳过去会再次触发同一分支，形成**无限重定向**。

#### 评论区实现

- **验证码共享**：`COMMENT_CAPTCHA_KEY` 通过 provide/inject 传递 `{ required, state, refresh }`，主表单与内联回复表单共用同一道验证码，保证内联回复不成为绕过验证码与限流的缺口。`CommentItem` 是递归组件，inject 沿组件树向上查找，**任意层级子评论**拿到的都是同一份上下文。
- **回复交互**：当前为"回复表单在对应评论正下方内联展开"，逻辑自治在 `CommentItem` 内部。
- **表单校验的两个易错点**：
  1. `content` 规则用 `trigger: 'change'` 而非 `'blur'` —— 主楼评论框为空时点击下方「回复」按钮会让 textarea 失焦，`blur` 会立刻误报「请输入评论内容」
  2. `captcha` 规则用**自定义 validator** 读取共享的 `captchaState.answer`，而非 `required` 规则校验 `model.captcha` —— 后者在 model 中无该字段，恒为 `undefined`，会导致无论是否填写都报「请输入验证码」，请求根本发不出去

#### Axios 封装

`utils/request.ts` 统一处理三件事：

- 请求拦截：附加 `Authorization: Bearer {token}`；`/auth/login` 显式**删除**可能残留的 Authorization 头（axios 复用 keep-alive 连接）
- 响应拦截：`code === 200` → 返回 `data.data`（业务对象）；非 200 → 统一弹提示
- 错误处理：按 HTTP 状态码分支（401 清登录态跳登录页、403/404/409/500 等）

**`logError` 只输出 `error.message`，不打印整个 AxiosError** —— 其 `config.headers` 含 `Authorization: Bearer <token>`，直接打印等于把登录凭据写进浏览器控制台。

**数组参数序列化**为逗号分隔：axios 默认序列化成 `data[]=1&data[]=2`，虽然 Tomcat 会剥离参数名里的 `[]` 让 `@RequestParam("data") List<Long>` 恰好接住，但那是容器实现细节而非 HTTP 规范，换 Undertow/Jetty 即失效。逗号分隔是 Spring 原生能力，跨容器稳定。

### 1.7 部署与运维

#### 多阶段构建

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS builder   # 带 JDK + Maven
RUN mvn clean package
FROM eclipse-temurin:17-jre-alpine            # 只有 JRE
COPY --from=builder /app/target/*.jar app.jar
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

构建镜像 775 MB，运行镜像只有 JRE + Alpine，**最终镜像不含编译工具链**。

BuildKit 缓存挂载（`--mount=type=cache,target=/root/.m2/repository`）让依赖缓存跨构建保留，改代码重建不必重下依赖。

#### Maven 镜像配置

```dockerfile
<mirrorOf>*</mirrorOf>   # 不能写 central
```

**`mirrorOf` 必须用 `*` 而非 `central`**：插件仓库（`pluginRepositories`）与依赖仓库是分开的，只镜像 `central` 会让插件仍走 `repo.maven.apache.org`，实测 `Connect timed out` 导致构建失败。

#### 单一数据源原则（本次修复的核心）

| 请求 | 由谁提供 | 关键点 |
|---|---|---|
| `/`、SPA 路由 | frontend 容器 → 镜像内 `dist` | nginx **不挂**宿主机的 dist，否则重建镜像后仍服务旧文件 |
| `/api/**` | backend | |
| `/uploads/**` | nginx 直读 `backend_uploads` 卷 | **必须与后端同一个 named volume**，否则新上传文件 404 |

#### 运维排障要点

- `docker compose up -d` **不会**重载 nginx 配置（bind mount 内容变化不触发容器重建），必须 `docker exec blog-nginx nginx -s reload`
- `docker compose up -d --build` 才是"重建镜像 + 重启"的一步式命令
- `JWT_SECRET` 缺失时 compose 直接报错（`${JWT_SECRET:?...}`），而不是静默使用弱密钥
- MySQL 宿主机端口映射 3307（宿主机已被 MySQL 5.7 占用 3306），容器内仍 3306

---

## 二、Bug 复盘

> 面试讲 Bug 复盘比讲功能清单更有说服力：它证明你能定位问题、找到根因、验证修复。

### 2.1 编码路径绕过角色校验（安全）

**现象**：普通用户访问 `/api/%61dmin/articles` 能拿到管理端数据。

**根因**：Spring 路由匹配用**解码后**路径，`request.getRequestURI()` 返回**未解码**原始值。`/api/%61dmin/articles` 解码后是 `/api/admin/articles`（路由命中后台），但原始 URI 不以 `/admin/` 开头（前缀判断落空）。

**修复**：对原始路径与解码后路径**各判定一次**，任一命中后台路径即要求 `ROLE_ADMIN`。

**验证**：新增 `JwtAuthenticationInterceptorAdminPathTest`，覆盖 普通用户/管理员 × 原始路径/编码路径 四种组合。

### 2.2 上下篇方向搞反

**现象**：最新发布的一篇没有「下一篇」；两三篇文章时看着像在两篇之间来回循环。

**根因**：前台列表按 `publish_time` **降序**（最新在最前），所以：

- 上一篇 = `publish_time` **大于**当前，在更大的那些里取**最小**（`orderByAsc`）
- 下一篇 = `publish_time` **小于**当前，在更小的那些里取**最大**（`orderByDesc`）

方向搞反后，"上一篇"会一路跳回最新的一篇。

**修复**：修正 ORDER BY 方向 + 补充边界用例（最新一篇无上一篇、最旧一篇无下一篇）。

### 2.3 上下篇作用域缺失（本次新增）

**现象**：从标签页进入文章，上下篇跳到不相干标签的内容。

**根因**：后端 `getPrevArticle(id)` / `getNextArticle(id)` 只按 `publish_time` 比较，无任何作用域过滤；前端也不传上下文。

**修复**：
- 后端加可选 `tagId` / `categoryId`，标签用**参数化子查询**（MyBatis-Plus `apply` 占位符，非字符串拼接），分类用 `category_id` 条件
- 前端作用域经 **URL query** 传递（`/article/12?tag=3`），而非路由 state —— 这样刷新页面、复制链接分享都能保持上下文
- 标签页/分类页给 `ArticleCard` 传 `scope` prop，封面链接、标题链接、整卡点击共用同一跳转目标

**验证**：新增 3 个单元测试覆盖 标签/分类/无作用域 三条分支；实测 `tagId=999`（不存在）返回 `null`。

### 2.4 浏览量只存 Redis 导致热门排序失效

**现象**：热门文章列表顺序与浏览量无关。

**根因**：浏览量只累加在 Redis，`article.view_count` 恒为 0，而 `ORDER BY view_count` 发生在 SQL 层，读不到 Redis 值。

**修复**：改为数据库原子自增；Redis 只留作每日趋势统计。

### 2.5 编辑文章导致计数错乱与发布时间被清空

**现象**：编辑未改状态的文章，分类/标签计数减少 1，`publish_time` 变空。

**根因**：`BeanUtils.copyProperties` 把 DTO 中的 null 覆盖进实体。`status` 未传 → null → 覆盖 → 视为下架 → 计数 -1；同时 `publish_time` 是 `ALWAYS` 更新策略，被显式置 null。

**修复**：显式区分 null（不修改）与空值（清空）的语义；`publish_time` 由 `resolvePublishTime` 统一推导，不直接取 DTO 值。

### 2.6 评论表单验证码永远校验失败

**现象**：无论是否填写验证码，点提交都提示「请输入验证码」，请求发不出去。

**根因**：`captcha` 规则用 `required` 校验 `model.captcha`，但该字段在 model 中根本不存在（验证码答案是共享的 `captchaState.answer`），`required` 对 `undefined` 恒为 false。

**修复**：改用自定义 validator 读取共享状态。

### 2.7 回复评论时主楼表单误报「请输入评论内容」

**现象**：点击某条评论的「回复」，主楼评论框变红报错。

**根因**：`content` 规则 `trigger: 'blur'`，点击「回复」按钮使主楼 textarea 失焦，触发校验。

**修复**：`trigger` 改为 `'change'`。

### 2.8 `vue-tsc` 报 `handleReply` 不存在（死事件）

**现象**：CI 的 `npm run type-check` 失败。

**根因**：`CommentSection.vue` 模板绑定了 `@reply="handleReply"`，但脚本中没有定义它。追查发现：`CommentItem` 声明了 `reply` 事件并做递归转发，但**全项目没有任何地方真正 `emit('reply')`** —— 这是早期交互（回复框统一显示在主楼）重构为内联展开后的残留。

**修复**：删除 3 处死代码（父组件绑定、子组件转发、事件声明）。

### 2.9 「改了前端代码没生效」

**现象**：`docker compose build frontend` 后访问 80 端口仍是旧版本。

**根因**：nginx 服务的是**宿主机的** `./blog-frontend/dist`，而 dist 被构建进 frontend 镜像 —— 存在**两份 dist**，重建镜像只更新了镜像里那份。

**修复**：nginx 不再挂宿主机 dist，改为 `proxy_pass http://frontend:80`，静态资源单一来源。

**验证**：临时把宿主机 `dist` 改名，网站仍 200，证明已完全解耦。

### 2.10 新上传的图片经 nginx 访问 404

**现象**：后台上传的图片，页面上显示不出来。

**根因**：后端写入 named volume `backend_uploads`，nginx 却 bind mount 宿主机的 `./blog-backend/uploads` —— **两份不同的存储**，nginx 看不到后端写的文件（实测 30 vs 11 个文件）。

**修复**：nginx 改挂同一个 named volume，并把宿主机遗留的 11 个文件迁入卷内。

### 2.11 nginx 配置改了不生效

**现象**：改完 `nginx/conf.d/default.conf`，reload 前行为无变化。

**根因**：`conf.d` 是 bind mount，`docker compose up -d nginx` 检测到挂载路径没变 → **不重建容器** → 内存里还是旧配置。实测容器启动时间早于文件修改时间才定位到。

**修复**：改配置后必须 `docker exec blog-nginx nginx -s reload`（已写入开发文档的排障提示）。

### 2.12 安全头重复

**现象**：响应里出现两份 `X-Frame-Options: SAMEORIGIN`。

**根因**：nginx 的 `add_header` 是"同层有定义就完全覆盖父级"而非叠加。早期 server 级与 frontend 容器的 server 级各加了一份。

**修复**：按路径分工 —— 静态资源由 frontend 加、API 由 Spring Security 加（`DENY` 更严格）、uploads 由网关加。每条响应恰好一份。

### 2.13 Docker 构建失败（插件下载超时）

**现象**：`docker compose build backend` 报 `PluginResolutionException`，`Connect to repo.maven.apache.org:443 ... Connect timed out`。

**根因**：Dockerfile 里 Maven 镜像配的是 `<mirrorOf>central</mirrorOf>`，插件仓库未被覆盖。

**修复**：改为 `<mirrorOf>*</mirrorOf>`。

---

## 三、Q&A

### A. 架构与设计

**Q1：介绍一下你的项目。**

> 面向单博主的个人博客系统，前端 Vue 3 + TypeScript + Vite，后端 Spring Boot 3.2 + MyBatis-Plus + MySQL + Redis。
>
> 架构上最核心的设计是**双 API 命名空间隔离**：读者端 `/front/**` 公开，博主端 `/admin/**` 强制 `ROLE_ADMIN`。
>
> 我自己动手的部分比较集中在三块：一是**自研 JWT + Redis 双因子鉴权**（Token 落 Redis 支持踢下线与改密后全量失效，并修复了百分号编码的角色绕过漏洞）；二是**查询性能重构**，把文章列表的 N+1 压成 3 次批量查询，并把浏览量从 Redis 改回数据库以保证 SQL 层排序可用；三是**纵深防御**，前后端双层 XSS 净化、文件按魔数校验真实格式、验证码与 IP 限流组合防灌水。
>
> 工程化上配了 Docker Compose 五服务一键部署、GitHub Actions CI（后端单测 + 前端类型检查/Lint/构建），写了 57 个单元测试，并输出了完整的开发文档。

**Q2：为什么前后端要分成 `/front` 和 `/admin` 两套？**

> 读者端和博主端的语义差别不只是"要不要鉴权"，还有**同一路径两种身份**：评论接口对游客开放（待审核），对管理员则直接通过并标记"博主"。如果只有一套接口，权限规则会散落在每个方法里，而且无法表达"可选鉴权"。
>
> 分成两套后，鉴权规则收敛到一处：`/admin/**` 在拦截器里统一判 `ROLE_ADMIN`，`/front/**` 默认放行、只对评论接口单独处理可选鉴权。

**Q3：这个项目是 Spring 还是 Spring Boot？**

> Spring Boot 3.2.5，底层 Spring Framework 6.1.x，JDK 17。
>
> 证据是 `spring-boot-starter-parent` 做 parent POM、`@SpringBootApplication` 启动类、`SpringApplication.run()`。业务代码里的 MVC、Security、事务、AOP 都是 Spring Framework 的能力，但都通过 `spring-boot-starter-*` 的自动装配来搭，没有手写 XML。
>
> 不过有一处我做了定制：没有走 Spring Security 默认的 `authorizeHttpRequests` 授权链，而是自己写了 JWT 拦截器 + 过滤器包装器，原因就是前面说的"可选鉴权"表达不了。

**Q4：是不是 jar 包运行的？**

> 是。`spring-boot-maven-plugin` 的 `repackage` 目标产出 **fat jar**（依赖打进同一个 jar，运行时不需要拼 classpath），Dockerfile 里 `COPY --from=builder /app/target/*.jar app.jar`，入口是 `java -Xms512m -Xmx1024m -jar app.jar`。
>
> 运行镜像用的是 `eclipse-temurin:17-jre-alpine` —— **只有 JRE 没有 JDK**，也不含编译工具链和源码。
>
> 注意 jar 里只有代码：MySQL、Redis 是运行时连的，配置文件在包内但支持环境变量覆盖，上传目录靠 volume 挂载。

**Q5：为什么用 MyBatis-Plus 而不是 JPA？**

> 三个原因：一是**可控**——博客的查询大多是"按发布时间倒序 + 若干条件"，用 LambdaQueryWrapper 拼条件比 JPA 的派生查询更直观，也能精确控制排序与 limit；二是**计数口径需要手工维护**（草稿不计入分类/标签文章数），JPA 的实体状态管理与这里的逻辑删除 + 显式计数同步反而更绕；三是**避免 N+1 意外**——JPA 的懒加载很容易在序列化时触发 N+1，而这里批量装配是显式写出来的。
>
> 代价是失去了自动的关联加载，批量装配要手写。

**Q6：单博主的系统为什么不做成多用户？**

> 项目的定位是个人博客，注册功能会带来一整套账号治理（邮箱验证、密码找回、封禁、内容审核），成本远大于收益。数据库里 `user` 表有 `role` 字段和唯一索引，架构上留了扩展口子，但当前流程刻意简化 —— 账号由种子数据初始化，不开放注册。

### B. Spring Boot / Spring

**Q7：为什么不用 Spring Security 的 `authorizeHttpRequests` 做鉴权？**

> 因为需要"可选鉴权"：前台评论接口，游客可以提交（进待审核），登录的管理员提交则直接通过并标记博主。`authorizeHttpRequests` 的静态规则表达不了"同一路径、两种身份"。
>
> 所以我的分工是：Spring Security 负责**框架层**（过滤器链、无状态会话、BCrypt 编码器、默认安全头），**业务鉴权**由自研的 `JwtAuthenticationInterceptor` + `JwtFilterWrapper` 完成。

**Q8：`@Transactional` 什么时候失效？**

> 几个常见陷阱我都遇到过：
>
> 1. **自调用** —— 同类内部方法直接调用，`代理` 不生效，事务不开启。项目的做法是把计数维护抽成独立的私有方法（`syncTags` / `syncCategoryCount`），但它们不涉及代理调用，因为事务边界在 public 方法上。
> 2. **异常被吞** —— 捕获异常后不抛出，事务会正常提交，不会回滚。
> 3. **非 public 方法** —— Spring AOP 只代理 public 方法。
>
> 另外要注意**回滚规则的默认行为**：默认只对 `RuntimeException` 和 `Error` 回滚，受检异常不回滚，需要 `@Transactional(rollbackFor = Exception.class)`。

**Q9：批量删除是原子的吗？**

> **不完全**，这正是项目里一个值得说的取舍：
>
> - 文章（`batchDeleteArticles` / `batchUpdateArticleStatus`）与评论（`batchDeleteComments`）的批量方法**有** `@Transactional`，中途失败整体回滚
> - 分类、标签、友链的批量删除**没有**事务（它们在删除前要先做引用校验，本身就是「查—写」两步，失败率低），中途失败可能部分成功部分失败
>
> 我认为对"批量删标签"这类操作，部分失败是可以接受的（用户能看出来哪些没删掉），但对"批量下架文章"必须原子 —— 因为它牵涉计数增减，部分成功会让计数永久错乱，所以那部分我加了事务。

**Q10：`BeanUtils.copyProperties` 有什么坑？**

> **它会用源对象的 null 覆盖目标对象的值**。DTO 里没传的字段（比如编辑文章时的 `status`）是 null，拷贝后实体就变成 null 了。
>
> 我的处理是分字段判断：需要区分"没传"和"清空"的字段（`status`、`tagIds`）单独处理，其余字段用 `!= null` 判断后再赋值。另外 `User` 实体的资料更新用**字段白名单**逐个 set，完全不走 copyProperties —— 因为那里必须防止越权改 `password` / `role` / `status`。

**Q11：为什么 `publish_time` 要用 `updateStrategy = ALWAYS`？**

> MyBatis-Plus 的默认更新策略是 `NOT_NULL`：字段为 null 时**跳过不更新**。而下架文章时我需要把 `publish_time` 显式置为 NULL（表示"未发布"），默认策略下这条 SQL 根本不会带上这个字段，数据库里就残留了旧值。
>
> 所以在这个字段上标 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`，强制 null 也参与更新。

**Q12：全局异常处理器是怎么设计的？**

> `GlobalExceptionHandler` 覆盖 11 类异常，分三类处理：
>
> - **客户端错误**（400/404/409/415）返回具体原因，便于前端直接提示用户
> - **业务异常**按其自带 code 映射 HTTP 状态码（用 `ResponseEntity` 动态推导，因为状态码取决于 code）
> - **未预期异常**统一返回 500 + 通用文案，**不暴露堆栈或 SQL**，完整异常在服务端 `log.error`
>
> 所有响应都是统一的 `Result{code, message, data}` 结构，前端拦截器可以无脑按 `code === 200` 判断。

**Q13：为什么前台接口的 `status` 是强制覆盖的？**

> 前台查询必须只返回已发布文章。控制器里直接 `queryDTO.setStatus(1)` 覆盖掉前端传来的值 —— 因为前端可能传 `status=0`（草稿）来试探。**服务端不能信任客户端的过滤条件**。

### C. MyBatis-Plus 与数据库

**Q14：逻辑删除有哪些要注意的？**

> 1. `selectById` 等方法会自动追加 `deleted = 0`，但**手写 SQL 不会** —— 需要查已删除数据时得单独写。
> 2. `updateById` 不会更新 `deleted` 字段本身。
> 3. **唯一索引会和逻辑删除冲突** —— 删除了同名分类后，`deleted=1` 的记录还在表里，再建同名分类会撞唯一索引。我的处理是：新建时先查有没有同名且未删除的（有则报冲突），有同名但已逻辑删除的则**先物理删除残留记录**再建。
> 4. 数据量大了以后 `deleted` 字段需要定期清理。

**Q15：`apply` 和 `inSql` 有什么区别？**

> `inSql` 是拼接 SQL 字符串，有注入风险；`apply` 支持 `{0}` 占位符，会参数化。
>
> 我在上下篇作用域那里用的是：
> ```java
> wrapper.apply("id IN (SELECT article_id FROM article_tag WHERE tag_id = {0})", tagId);
> ```
> `tagId` 是 Long 类型，参数化后完全不存在注入面。

**Q16：为什么分类和标签的计数不用触发器或数据库统计？**

> 触发器在这个场景不合适：计数口径是"仅已发布"，而"文章是否已发布"这个判断涉及应用层的状态机（下架时要清空 `publish_time`），触发器里很难保持一致；而且触发器在批量操作、事务回滚时容易出现计数漂移。
>
> 我的做法是在 Service 层显式维护，且把「关联表维护」和「计数维护」拆开 —— 关联表始终按实际标签更新，计数才看发布状态。这样草稿文章挂了标签也不影响计数。

**Q17：计数用"读-改-写"会有并发问题吗？**

> 会。所以用的是**原子 SQL**（`addArticleCount`），在数据库层 `article_count = article_count + ?`，不读出来在内存里改。

**Q18：文章表的索引是怎么设计的？**

> `idx_category_id`、`idx_status`、`idx_create_time`、`idx_is_top`。
>
> 思考点是：列表查询高频用 `status = 1 ORDER BY publish_time DESC`，严格说最理想是 `(status, publish_time)` 联合索引。当前数据量下（几十篇）单列索引够用，这也是一个**已知不足** —— 数据量上来后需要根据慢查询日志调整。

**Q19：为什么浏览量不直接放 Redis？**

> 前面说过：`ORDER BY view_count` 在 SQL 层执行，读不到 Redis 值，热门排序会失效。
>
> 更本质的取舍是：**浏览量是"写多读少且允许最终一致"的指标**，理论上适合放 Redis**批量回写**数据库，而不是完全不用 Redis。我当前是直接写库（简单、排序正确），高并发下会成瓶颈，更好的方案是 Redis 计数 + 定时批量刷库。这是我知道但没做的优化。

### D. 安全

**Q20：你这个系统有哪些安全措施？**

> 我按"认证 / 授权 / 注入 / XSS / 文件 / 刷量"六个方向做的：
>
> - **认证**：JWT（JJWT）+ Redis 存储，密钥长度启动时强校验，Token 可主动失效
> - **授权**：自研拦截器，`/admin/**` 强制 `ROLE_ADMIN`，并做了编码路径绕过防护
> - **注入**：MyBatis-Plus 参数化，无字符串拼接 SQL；排序字段走 switch 白名单而非直接拼接
> - **XSS**：服务端 Jsoup + 前端 DOMPurify 双层净化
> - **文件**：按文件头魔数判定真实格式，UUID 重命名 + 日期目录
> - **刷量**：验证码（一次性、5 分钟过期）+ Redis IP 限流 + 登录失败锁定

**Q21：JWT 存 localStorage 还是 sessionStorage？为什么？**

> 存 **sessionStorage**，关闭标签页即失效。
>
> localStorage 的风险是 XSS 一旦发生，token 会被持久化读取，且不会自动过期。我用 sessionStorage 配合"写入时清理 localStorage 中的历史残留"，避免共享电脑自动登录。
>
> 更彻底的做法是 HttpOnly Cookie（配合 CSRF token），但那会引入 CSRF 防护成本，当前 token 短时效 + 主动失效的组合够用。

**Q22：为什么不用 HttpOnly Cookie？**

> 因为 Cookie 会自动随请求携带，一旦发生 XSS 就可能被 CSRF 利用，而当前架构把风险控制在"token 需 JS 主动读取并附加到请求头"。
>
> 代价是**必须处理 CORS**（Cookie 场景下 CORS 更严格），以及不能用 `document.cookie` 读取 token 做细粒度判断。我的折中是：Token 存 sessionStorage + CORS 白名单不用 `*`。

**Q23：验证码为什么用算术题不用图形验证码？**

> 两个原因：一是**图形验证码体验差且不防脚本**（OCR 成本已经很低），二是图形验证码在移动端和无障碍场景下很难用。
>
> 我对它的定位很明确：**它挡的是随手灌水的人，不是脚本**。脚本能自己获取题目并求解，所以真正挡脚本的是 IP 限流 + 频率限制。验证码 + 限流组合才有意义。

**Q24：`add_header` 在 nginx 里有什么坑？**

> 它的语义是"**同一层级有定义就完全覆盖父级**"，而不是叠加。
>
> 我踩过两次：第一次是 `/uploads/` 里只写了 `add_header Cache-Control`，结果把父级的三个安全头全覆盖没了；第二次是 server 级和 upstream 各加了一份，浏览器收到两份 `X-Frame-Options`。
>
> 最终方案是**按路径分工**：静态资源由 frontend 容器加、API 由 Spring Security 加（它的 `DENY` 比网关的 `SAMEORIGIN` 更严格）、uploads 由网关自己加。

**Q25：文件上传为什么不用扩展名判断？**

> 因为**扩展名和 Content-Type 都可以伪造**。真正可靠的是文件头魔数。
>
> 我还刻意**不拿扩展名去比对文件头**：PNG 内容存成 `.jpg` 太常见了（网页另存、表情包），按扩展名比对会误判拒掉正常图片。存盘时用的扩展名由**检测结果**决定，永远是安全值。

**Q26：怎么防止 SQL 注入？**

> 主要靠 MyBatis-Plus 的参数化（`#{}` 而非 `${}`）。项目里**唯一一处需要动态 SQL 的地方是排序字段**，我用了 switch 白名单映射：

```java
switch (sortField) {
    case "viewCount": wrapper.orderByDesc(Article::getViewCount); break;
    case "title":     wrapper.orderByAsc(Article::getTitle);      break;
    default:           wrapper.orderByDesc(Article::getCreateTime);
}
```
> 未知字段回落到默认值，绝不把用户输入拼进 ORDER BY。

### E. 性能

**Q27：你的项目有哪些性能优化？**

> 按收益排序：
>
> 1. **N+1 消除** —— 文章列表从"1 + 1 + N" 次查询压成 3 次批量查询
> 2. **浏览量原子自增** —— 单条 UPDATE 代替"读-改-写"，且存库保证排序可用
> 3. **评论树缓存** —— Redis 缓存 10 分钟，变更时主动失效
> 4. **前端分包 + 路由懒加载** —— markdown 编辑器相关的 1MB+ chunk 不进首屏
> 5. **归档接口不走分页** —— 避免 `pageSize` 上限 100 导致静默丢数据
> 6. **分页参数归一化** —— `pageSize` 上限 100，防止单次拉取过多数据

**Q28：怎么发现 N+1 的？**

> 列表接口返回 10 篇文章时，SQL 日志里出现了 30+ 条查询。定位方式是看 MyBatis 的 SQL 日志（`mybatis-plus.configuration.log-impl`），发现同一时刻连续发出大量结构相同的单条查询。

**Q29：批量查询怎么保证顺序？**

> `convertToVOList` 里用 `Map` 把批量查回来的数据按 id 索引，再按原列表顺序遍历装配 —— **不用 SQL 的 ORDER BY 保证顺序**，因为最终顺序由查询条件（发布时间倒序）决定，批量查回来的顺序不可靠。

**Q30：如果文章量到 10 万篇会怎样？**

> 会先在**归档页**出问题（一次拉全量）。其次是 `ArticleServiceImpl` 超过 700 行、职责过多，需要拆分。索引也需要按慢查询日志调整为 `(status, publish_time)` 联合索引。
>
> 更根本的方案是引入 Elasticsearch 做全文检索、把归档改成按年分页。当前数据量（几十篇）下这些是过度设计。

### F. 前端

**Q31：前端为什么用 Pinia 而不是 Vuex？**

> Pinia 是 Vuex 的官方继任者，API 更简洁（去掉 mutations），对 TypeScript 的类型推导支持更好，devtools 集成也更自然。Vuex 在 Vue 3 里已进入维护状态。

**Q32：路由守卫怎么做的？**

> 全局 `beforeEach`，处理三件事：页面标题、登录态校验、角色校验。
>
> 有个坑：**角色不匹配时跳 `/home` 而不是 `/admin/dashboard`**，因为 `Dashboard` 自身也带 `roles: ['ROLE_ADMIN']`，跳过去会再次触发同一分支形成无限重定向。
>
> 另一个坑是**刷新页面后 userInfo 为空**：sessionStorage 里有 token 但没有用户信息，守卫读不到 `role` 会误判。所以守卫里会主动调 `fetchProfile()` 补齐。

**Q33：`v-html` 会不会有 XSS 风险？**

> 会，所以全站 `v-html` 统一走 `safeHtml()`，白名单显式列举允许的标签与属性，并额外 `FORBID_ATTR: ['style']`（DOMPurify 默认允许 style，可被用于 `position:fixed` 做全屏钓鱼覆盖）。
>
> 服务端还有一层 Jsoup 净化 —— **服务端那层不能省**，因为绕过前端直接调 API 拿到的就是未净化内容。

**Q34：为什么 `ArticleDetail` 用 `markdown-it` 而不是现成组件？**

> 因为需要**控制安全边界**：`markdown-it` 的 `html: true` 允许正文内嵌 HTML（有些技术博客需要），所以渲染结果必须自己管净化链路。用现成组件的话，净化策略是黑盒的，很难说清楚哪些标签被放行。

**Q35：Axios 封装里你踩了什么坑？**

> 三个：
>
> 1. **`/auth/login` 要显式删掉残留的 Authorization 头** —— axios 复用 keep-alive 连接，残留的头会被后端先判 401，前端看起来像"用户名或密码错误"。
> 2. **不能打印整个 AxiosError** —— 它的 `config.headers` 里有 `Authorization: Bearer <token>`，直接 `console.error(error)` 等于把登录凭据写进控制台。我的 `logError` 只取 `error.message`。
> 3. **数组参数要自己序列化** —— axios 默认序列化成 `data[]=1`，靠 Tomcat 剥离 `[]` 才能被 `@RequestParam("data") List<Long>` 接住，那是容器实现细节，换 Undertow 就失效。

**Q36：为什么 token 放 sessionStorage 还要清理 localStorage？**

> 因为早期版本用过 localStorage，升级后如果不清，浏览器里会残留一个旧的 token。用户 A 登出后，用户 B 打开同一个浏览器可能带着 A 的 token（如果那份没被清）。所以 `setToken` 时同时 `localStorage.removeItem('token')`。

### G. Docker 与部署

**Q37：Docker 多阶段构建是什么？**

> 把构建和运行拆成两个镜像阶段：
> ```
> FROM maven:3.9-eclipse-temurin-17 AS builder   # 带 JDK + Maven
> RUN mvn clean package
> FROM eclipse-temurin:17-jre-alpine            # 只有 JRE
> COPY --from=builder /app/target/*.jar app.jar
> ```
> 好处是**最终镜像不含编译工具链和源码**，体积小、攻击面小。构建镜像 775 MB，运行镜像只有 JRE + Alpine。

**Q38：为什么 Dockerfile 里 Maven 镜像要写 `mirrorOf: *`？**

> 因为**插件仓库（`pluginRepositories`）和依赖仓库是分开的**。写 `central` 只会镜像依赖仓库，插件仍走 `repo.maven.apache.org`，实测 `Connect timed out` 导致构建失败。

**Q39：镜像体积你做过优化吗？**

> 做过一轮：基础镜像从 `eclipse-temurin:17-jre`（约 450 MB）换成 `eclipse-temurin:17-jre-alpine`（约 180 MB），再叠加多阶段构建，运行镜像能压到 200 MB 以内。
>
> 前后端分开构建后，前端镜像就是 `nginx:alpine` + dist（约 50 MB），后端镜像只含 JRE + jar。

**Q40：`docker compose up` 和 `up --build` 有什么区别？**

> `up` 只**启动/重启**容器，用已有镜像；`up --build` 会先重建镜像。
>
> 我踩过这个坑：改完前端代码跑了 `docker compose build frontend`，但 nginx 服务的是宿主机的 dist（bind mount），重建镜像对它没影响，页面看起来"没生效"。修复后改成 nginx 代理 frontend 容器，静态资源单一来源。
>
> 还有一个更隐蔽的：**`nginx/conf.d` 是 bind mount，改配置后 `up -d` 不会重建容器，内存里还是旧配置**，必须额外 `nginx -s reload`。

**Q41：为什么 MySQL 映射到宿主机 3307？**

> 宿主机已装有 MySQL 5.7 占用了 3306。容器内仍用标准 3306，后端通过服务名 `mysql:3306` 连接，不受影响。只有从宿主机用客户端连时才需要 3307。

**Q42：JWT_SECRET 怎么管理？**

> 放在根目录 `.env`（已在 `.gitignore` 里），compose 里写 `JWT_SECRET: ${JWT_SECRET:?请在根目录 .env 中设置...}` —— **缺失时 compose 直接报错**，而不是静默使用弱密钥。
>
> 生成用 `openssl rand -base64 48`。后端还有一道防线：密钥长度不足 32 字节时 `JwtUtil.init()` 抛异常终止启动。

**Q43：日志怎么配置的？**

> 分两级：应用日志 `logging.level.com.blog` 和安全日志 `logging.level.org.springframework.security` 独立配置（`LOG_LEVEL_APP` / `LOG_LEVEL_SECURITY` 环境变量）。生产关掉 MyBatis 的 SQL 日志（`MYBATIS_LOG_IMPL=NoLoggingImpl`）。

### H. 工程化与测试

**Q44：你的测试怎么写的？**

> JUnit 5 + Mockito，6 个测试类 57 个用例，**主要不是测覆盖率，是测回归**。
>
> 每个测试的注释都写明"覆盖历史上多次出错的点"，比如：
> - `JwtAuthenticationInterceptorAdminPathTest` —— 覆盖百分号编码绕过
> - `ArticlePublishStateTest` —— 覆盖未传 status / tagIds 的 null 语义
> - `ArticleServiceImplTest` —— 覆盖上下篇方向与作用域分支
>
> 我认为对个人项目而言，**测试的价值在于把踩过的坑钉死**，而不是追求覆盖率数字。

**Q45：单元测试里为什么要手动初始化 TableInfo？**

> MyBatis-Plus 的 `LambdaQueryWrapper` 把 lambda 表达式解析成列名时依赖 `TableInfo` 缓存，而 `TableInfo` 是由 MyBatis 启动时扫描实体类建立的。单元测试没有 MyBatis 容器，所以要手动：
> ```java
> TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Article.class);
> ```
> 这是 MP 单元测试的一个常见坑。

**Q46：CI 做了什么？**

> GitHub Actions，两个 job：
>
> - **后端**：`mvn test` → `mvn package -DskipTests` → 起 MySQL 8 容器执行 `blog_db.sql`，校验 `article.content` 与 `comment.content` 列存在（确认脚本与实体定义没有漂移）
> - **前端**：`npm ci` → `npm run type-check`（vue-tsc）→ `npm run lint:check`（ESLint）→ `npm run build`
>
> 我特意让 CI 跑**真实的构建**而不只是 lint，因为前端曾出现过 `vue-tsc` 报错但 `vite build` 能过的情况 —— 那类问题只有类型检查能抓。

**Q47：为什么 `vue-tsc` 是必需的？**

> 因为 Vite 用 esbuild 转译，**只做类型擦除不做类型检查**。写错类型时 `npm run build` 能成功，但运行时才炸。
>
> 项目里就有真实案例：`CommentSection.vue` 绑定了 `@reply="handleReply"` 而脚本里没有这个函数，`vite build` 完全正常，只有 `vue-tsc` 报出来。

**Q48：ESLint 那 10 个 warning 你处理了吗？**

> 没有，刻意保留。分两类：
>
> - **8 个 `vue/no-v-html`**：我逐条核对过，7 处都经过 `safeHtml()` 净化，1 处（About 页的社交图标）是硬编码常量。ESLint 是静态扫描，看不到净化链路，属于误报。
> - **2 个 `vue/no-template-shadow`**：我用 `@vue/compiler-sfc` 实测过编译产物 —— v-for 作用域变量是 renderList 回调的**闭包参数**，优先级高于 setup 绑定，行为完全正确。
>
> 保留它们的价值是：**将来有人新写一个未经 `safeHtml` 的 `v-html`，行为不变但新行会被标出来**。如果为了消噪音全加 disable，反而掩盖了风险。

### I. AI 辅助开发（必问）

**Q49：这个项目你用了 AI 工具吗？**

> 用了。我主要用它做三类事：**跨文件的调用关系定位**（比如"上下篇的实现分散在哪几个文件"）、**把报错信息翻译成可读的原因**、以及**补写注释和文档**。
>
> 但方案设计和取舍是我做的。举个例子：文章上下篇的作用域缺陷是我在实际使用中发现的；确定要用"作用域参数 + URL query 传递"而不是路由 state，是因为我判断 URL 方案能扛住刷新和分享；标签用 `apply` 参数化子查询而不是 `inSql` 字符串拼接，是因为我知道 `inSql` 有注入面。
>
> 我会验证 AI 给的每一处改动，也有拒绝它的时候 —— 比如它一度把浏览量逻辑重构成函数抽取，我改回来了，因为那超出了当时"只加注释"的改动范围。

**Q50：AI 写的代码你怎么保证质量？**

> 靠**验证**而不是靠"看一眼"。具体是四层：
>
> 1. **编译/类型检查**：后端 `mvn test` 必须 BUILD SUCCESS，前端 `vue-tsc` 必须 0 错误
> 2. **自动化测试**：新增的功能要补用例，我改上下篇作用域时同步加了 3 个单元测试覆盖三条分支
> 3. **产物级验证**：像 `/deep/` 改 `:deep()` 这种改动，我对比的是**编译后 CSS 的选择器**是否与改前逐条一致，而不是"看着没问题"
> 4. **端到端实测**：起容器、用真实 HTTP 请求验证接口返回，包括边界值（`tagId=999` 返回 null）

**Q51：你觉得自己在这个项目里最大的收获是什么？**

> 反而是**"写测试的价值在于钉死坑"**这个认识。我一开始写测试是为了显得完整，后来发现真正有用的是：每次修 bug 都在同一个地方补一条用例，避免半年后改回去。这让我后面改上下篇作用域时敢直接动手 —— 因为我知道有测试兜着。

### J. RAG / AI 问答（新增模块，最可能被追问）

**Q52：你这个项目里 RAG 是怎么做的？**

> 一条完整链路：**切块 → 检索 → 拼上下文 → 调模型 → 带出处返回**。
>
> - **切块**：Markdown 按标题层级切段，记录标题路径（如「基础 > 鉴权」），相邻块保留 100 字重叠
> - **检索**：内存 BM25 关键词检索，倒排表 + 预计算词频；中文用**字符 bigram** 分词，零第三方分词依赖
> - **上下文**：去重、按字符预算截断、编号，回答里的 `[1]` 就对应资料块编号
> - **生成**：OpenAI 兼容协议，实测接的 MiniMax（`MiniMax-M3.1-Flash`）
> - **出处**：答案标 `[n]`，前端渲染成可点击上标，点开定位到原文卡片
>
> 检索层抽象成 `ChunkRetriever` 接口，第二阶段换向量检索时上层完全不用改。

**Q53：为什么第一阶段用 BM25 而不是直接上向量检索？**

> 两个原因。
>
> **技术上**：关键词检索能讲清楚每个环节——分词、BM25 公式（k1 词频饱和、b 长度惩罚）、倒排表结构。向量检索反而是"调个 API 存个库"，细节少。
>
> **工程上**：向量检索要引入 Embedding 模型 + 向量库 + 索引重建流程，链路长、依赖多。先把「切块→检索→上下文→生成→出处」这条主链路跑通，把 Prompt 模板、引用编号、前端渲染这些真正影响体验的部分稳定下来，第二阶段再换检索层——因为上层是抽象好的。
>
> 顺带一个实测收获：BM25 暴露的**词汇鸿沟**问题（用户问"鉴权"，文章里写的是"JWT/登录"），同义词扩展能缓解；向量检索从语义上就没这问题，这也是第二阶段的价值。

**Q54：BM25 和 TF-IDF 有什么区别？**

> 两点改进：
>
> 1. **词频饱和**：TF-IDF 里词出现 10 次比出现 2 次重要 5 倍；BM25 用 k1（我取 1.5）做饱和，出现次数越多边际收益越小，不会被单个高频词主导。
> 2. **文档长度归一化**：BM25 用 `|D|/avgdl` 惩罚长文档。不加这个的话，长文档天然包含更多词，TF-IDF 下得分虚高。
>
> 公式：
> ```
> score(D,Q) = Σ IDF(q) · (f(q,D)·(k1+1)) / (f(q,D) + k1·(1-b+b·|D|/avgdl))
> IDF(q) = ln(1 + (N-n(q)+0.5)/(n(q)+0.5))
> ```

**Q55：你怎么保证不产生幻觉？**

> 分两层，而且**第二层更关键**。
>
> **Prompt 层**：角色限定（站内知识助手）、资料边界（只用资料里的信息）、无答案兜底话术（固定一句"根据现有文章未找到相关内容。"）、引用格式，另外加了一条专门针对拼接式幻觉的规则——「严禁编造资料中未出现的类名、库名、配置项、依赖或行为」。
>
> **检索层**：这个是我实测才发现的。原来 BM25 只过滤"一分不得"的块，弱相关块照样进上下文。实测问「鉴权怎么做的」会混进《Pinia 状态管理》这类文章，模型据此把别处的细节（比如一个项目里根本不存在的持久化插件）拼进答案。所以加了**相关性阈值**：只保留得分 ≥ 最高分×50% 的块。召回数从 5 降到 1~3，幻觉明显减少，上下文也更短、省 token。
>
> 但要说清楚：**知识库内容和项目现状不一致时，模型无法知晓**。比如文章里写"用 localStorage 持久化"而项目实际用 sessionStorage，模型如实引用文章就是错的。这不是 RAG 的 bug，是知识库本身的问题——解法是让知识库内容与实际保持一致。

**Q56：怎么评估检索质量？**

> 我用**真实问题实测**，看三个指标：
>
> 1. **Top-1 是否正确** —— 召回的第一块是不是最该被引用的那篇
> 2. **无关问题是否被拒** —— 问"今天天气怎么样"必须返回「未找到」而不是硬凑答案
> 3. **召回数** —— 上下文里的块越少越好（token 成本 + 噪声）
>
> 实测暴露了三个问题：词汇鸿沟（用同义词扩展解决）、拼接式幻觉（用相关性阈值解决）、以及推荐问题与知识库不匹配——我原本的四个推荐问题里有三个命中不了资料，那是推荐问题写得不对，不是 RAG 的问题。

**Q57：索引更新怎么做的？这里有个坑。**

> 用「**事件 + 事务提交后监听 + 异步**」三件套，而不是在 Service 里直接调 `@Async` 方法。两个原因：
>
> 1. **`@Async` 自调用失效** —— 同类内部调用不经过 Spring 代理，异步根本不生效，索引会同步阻塞文章保存接口
> 2. **事务回滚后索引已建** —— 文章保存是 `@Transactional` 的，事务回滚后索引里会残留未发布的草稿内容
>
> ```java
> eventPublisher.publishEvent(new ArticleChangedEvent(articleId));
>
> @Async
> @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
> public void onArticleChanged(ArticleChangedEvent e) { ... }
> ```
>
> 索引以 `article_chunk` 表为真相来源，内存索引只是运行时投影，随时可全量重建。

**Q58：外部模型接口不稳定怎么处理？**

> 三件事：
>
> - **重试**：连接被拒、超时这类网络瞬时故障重试 3 次（间隔 600ms）；但"响应格式不对"不重试——那是请求本身有问题，重试只是白等
> - **状态码细分**：限流 429、参数 400、**模型不可用 503**、未知 500。让前端能区分"稍后重试"和"你的请求有问题"
> - **兼容推理模型的响应结构**：带 reasoning 的模型有两种形态——答案与思考过程分离（`content` 干净、`reasoning_content` 是思考），或思考混在 `content` 里（开头 `<think>...</think>`）。客户端两层兼容：content 空时回退 reasoning_content，再剥掉 `<think>` 标签
>
> 另外加了 `GET /admin/rag/models` 和 `POST /admin/rag/test-llm` 两个运维接口。实测很有用：MiniMax 的 `MiniMax-M2.1` 就出现在模型列表里，但调用返回 404「已下线」——靠列出来才知道该用哪个。

**Q59：RAG 问答有做限流吗？会不会被人刷爆？**

> 做了，双层：单 IP 每分钟 20 次、每天 500 次。但有个例外——**已登录的管理员完全豁免**。因为这是单用户博客，后台就是博主自己在用，调试和体验时反复提问是常态，按 IP 限流会把自己挡在门外。
>
> 这里踩过一个坑：限流异常原本继承 `IllegalStateException`，后来为了和别的异常区分改成了 `RateLimitException`，结果 `catch (IllegalStateException e)` 匹配不上了，异常落到兜底的 `catch (Exception e)` 被当成"Redis 故障"吞掉——**限流形同虚设**，而且只在日志里留一行 WARN，不写测试根本发现不了。现在用单元测试钉死了这条（catch 顺序必须 RateLimitException 在前）。

**Q60：前端刷新页面后对话会丢吗？**

> 不会，问答记录存 sessionStorage，刷新可恢复，关标签页自动清空（和项目 token 的存储策略一致）。
>
> 但要说清一个边界：这恢复的是**显示**，不是**上下文**。RAG 每次提问独立检索、独立拼上下文，所以刷新后你看到历史记录，但新问题不会自动接上文。要做真正的多轮追问，得服务端维护会话、把历史轮次一起拼进上下文——代价是检索范围会被历史污染。

---

### K. 取舍与不足（必问）

**Q61：这个项目最大的不足是什么？**

> 我能说出四个，且都清楚原因和改法：
>
> 1. **`ArticleServiceImpl` 超 700 行** —— 发布状态机、计数维护、查询构造混在一起。应该拆成三个协作类。
> 2. **分类/标签/友链的批量删除没有事务** —— 中途失败可能部分成功（文章的批量操作有事务，因为牵涉计数）。
> 3. **Redis 强依赖无熔断** —— 验证码、限流、Token 校验都依赖 Redis，Redis 挂了这些功能直接不可用。目前是"Redis 异常时 catch 住不阻断"式的降级，不是真正的熔断。
> 4. **搜索页与归档页的上下篇仍是全站范围** —— 作用域机制已就绪，但搜索需要按关键词过滤、归档需要按年份过滤，后端查询逻辑还没接。
> 5. **RAG 没有多轮对话** —— 每次提问独立检索，没有会话上下文。

**Q62：如果给你更多时间，你会先做哪个？**

> 先拆 `ArticleServiceImpl`。理由是它会持续放大其他所有问题：现在改一个计数逻辑要在 700 行里找，改动风险随代码量增长。而且拆分是纯重构，有测试兜着，风险低、收益立即可见。
>
> 功能上我会先做**搜索页的上下篇作用域** —— 那个是用户能感知的体验缺陷，而 Redis 熔断在单博主场景下几乎不会被触发。

**Q63：你怎么保证项目"能跑起来"？**

> 三层保障：
>
> - **CI**：每次 push 都跑后端单测 + 前端类型检查/Lint/构建
> - **Docker Compose**：五服务一键起，含健康检查（MySQL/Redis 的 `service_healthy` 依赖编排）
> - **冒烟验证**：改完我会实际起容器，用 HTTP 请求验证首页、SPA 路由、API、上传文件访问这几条路径
>
> 这套流程帮我发现了 nginx 静态资源双份、uploads 卷不一致、配置文件不 reload 这三个"代码没错但部署不对"的问题 —— 这类问题单测和 CI 都测不出来。

**Q64：这个项目对生产环境还差什么？**

> 按优先级：
>
> 1. **HTTPS** —— nginx 已预留 443 端口但没配证书
> 2. **邮箱通知** —— 评论回复、审核通过后通知博主（README 已标"待集成"）
> 3. **Redis 高可用** —— 当前单实例，挂了就是全挂
> 4. **日志与监控** —— 目前只有容器日志，没有集中化采集与告警
> 5. **备份策略** —— MySQL 有 named volume 但没有定时备份

---

## 四、附录

### 4.1 需要记住的数字（全部可当场核验）

| 数字 | 值 | 怎么核验 |
|---|---|---|
| REST 接口数 | **67** | 数 `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`；或访问 `/api/v3/api-docs` 数 `paths` × methods |
| 路径数 | 55 | OpenAPI `paths` 长度 |
| OpenAPI 模型数 | 39 | OpenAPI `components.schemas` 长度 |
| 控制器数 | 14 | `controller/` 目录 |
| 数据表 | 7 | `blog_db.sql` |
| 单元测试 | **101** | `mvn test` 输出（`Tests run: 101, Failures: 0`） |
| 文章服务测试 | 21 | `ArticleServiceImplTest` |
| RAG 模块测试 | 5 类 / 32 例 | `com.blog.rag` 包下的测试类 |
| 前端页面 | 19 | `views/` |
| 后端代码 | 103 文件 / 8580 行 | `find` + `wc -l` |
| 前端代码 | 51 文件 / 11353 行 | 同上 |

### 4.2 必须能讲清楚的 8 个技术点

按面试被问到的概率排序：

1. **浏览量为什么存 MySQL 不存 Redis** —— 排序在 SQL 层，读不到 Redis 值
2. **上下篇方向怎么定的** —— 降序列表里，"上一篇"是更大值中的最小
3. **为什么自研拦截器不用 `authorizeHttpRequests`** —— 可选鉴权表达不了
4. **草稿为什么不计入分类/标签计数** —— 口径统一为"仅已发布"
5. **`publish_time` 下架时怎么清空** —— MP 的 NOT_NULL 策略会跳过 null，需 ALWAYS
6. **文件上传怎么防伪装** —— 魔数判定，且不看客户端扩展名
7. **评论 XSS 怎么防** —— 服务端 Jsoup 不能省，绕过前端就是未净化数据
8. **编码路径绕过是什么** —— 原始 URI vs 解码后路径的判定差异

### 4.2 必须能讲清楚的 8 个技术点

按面试被问到的概率排序：

1. **浏览量为什么存 MySQL 不存 Redis** —— 排序在 SQL 层，读不到 Redis 值
2. **上下篇方向怎么定的** —— 降序列表里，"上一篇"是更大值中的最小
3. **为什么自研拦截器不用 `authorizeHttpRequests`** —— 可选鉴权表达不了
4. **草稿为什么不计入分类/标签计数** —— 口径统一为"仅已发布"
5. **`publish_time` 下架时怎么清空** —— MP 的 NOT_NULL 策略会跳过 null，需 ALWAYS
6. **文件上传怎么防伪装** —— 魔数判定，且不看客户端扩展名
7. **评论 XSS 怎么防** —— 服务端 Jsoup 不能省，绕过前端就是未净化数据
8. **编码路径绕过是什么** —— 原始 URI vs 解码后路径的判定差异

> 若简历里写了 RAG 模块，还要能接住 J 部分的 9 个问题（Q52–Q60）：
> BM25 vs TF-IDF、词汇鸿沟、拼接式幻觉、相关性阈值、事务提交后事件、
> 推理模型的响应结构差异、限流豁免、多轮对话的边界。

### 4.3 面试节奏建议

| 阶段 | 讲什么 |
|---|---|
| 自我介绍 30 秒 | 一句话定位 + 技术栈 + "最核心的是鉴权、性能和检索质量三块" |
| 展开 5 分钟 | 挑 **1 个安全设计**（编码绕过）+ **1 个性能决策**（浏览量存库）+ **1 个检索质量决策**（相关性阈值），每个讲"问题→判断→方案→代价" |
| 追问兜底 | 用 4.2 的 8 个点接住深挖；RAG 相关用 J 部分接住 |
| 收尾 | 主动说不足（5 个），展示自我认知 —— **这是加分项不是减分项** |
| AI 问题 | 用 I 部分的三个层次回答，重点在"我会验证也会拒绝" |

### 4.4 不要说的话

| 别说 | 改说 |
|---|---|
| "这个项目是 AI 帮我写的" | "用了 AI 辅助编码和文档，方案设计和取舍是我做的" |
| "我精通 Spring Boot" | "这个项目用的是 Spring Boot 3.2，我实际改动最多的是鉴权链路、数据装配和检索层" |
| "性能提升了 80%"（没有实测） | "把 N+1 从 30 次查询压到 3 次"（可现场验证） |
| "支持高并发" | "单博主场景够用，Redis 熔断和高并发浏览计数是我知道但没做的优化" |
| "RAG 效果很好" | "检索质量的三个指标我都实测过：Top-1 命中、无关问题能否被拒、召回数" |
| "用了向量检索"（实际是 BM25） | "第一阶段是 BM25 关键词检索，检索层抽象成接口，第二阶段换向量检索上层不用改" |

### 4.5 项目可演示的东西（面试时直接开）

| 演示 | 位置 |
|---|---|
| API 文档（Knife4j，可在线调试） | `http://localhost:8080/api/doc.html` |
| 前端站点 | `http://localhost/` |
| **AI 站内问答（可现场提问）** | `http://localhost/ask` |
| 后台仪表盘（ECharts 趋势图） | `http://localhost/admin/dashboard` |
| 数据库结构与种子数据 | `blog-backend/src/main/resources/db/blog_db.sql` |
| CI 流水线 | `.github/workflows/ci.yml` |
| 开发文档 | `开发文档.md` |

> 演示 API 文档时可以直接说："这是运行时生成的 OpenAPI，55 条路径、67 个操作、39 个模型，全部可在线调试。" —— 这比任何数字都有说服力。
>
> 演示 AI 问答时建议**先点第二组（知识库没写过的问题）**，展示它诚实回答「根据现有文章未找到相关内容。」，
> 再点第一组展示带出处引用的完整回答——一个诚实的系统比一个什么都敢答的系统更可信。

# 挑战杯法律咨询后端

## 项目简介

这是挑战杯法律咨询小程序的后端服务，基于 Spring Boot + PostgreSQL 构建，提供 AI 法律咨询、用户管理、合同审查、文书模板等功能。

## 技术栈

- **后端框架**: Spring Boot 2.7.18
- **数据库**: PostgreSQL
- **ORM**: Spring Data JPA
- **AI 服务**: DeepSeek API
- **部署平台**: Render
- **Java 版本**: 17

## 本地开发

### 前置要求

- JDK 17+
- Maven 3.6+
- PostgreSQL 12+

### 环境配置

1. 创建 PostgreSQL 数据库:
```sql
CREATE DATABASE tiaozhanbei;
```

2. 配置数据库连接（可选，默认使用 localhost:5432/tiaozhanbei）:

可以通过环境变量或修改 `application.yml` 配置:
```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/tiaozhanbei
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
export DEEPSEEK_API_KEY=your_api_key
```

### 运行项目

```bash
# 编译项目
mvn clean package -DskipTests

# 运行项目
mvn spring-boot:run
```

服务默认运行在 http://localhost:8080

## Render 部署

### 方法一：使用 render.yaml 自动部署（推荐）

1. 将代码推送到 GitHub/GitLab 仓库
2. 在 Render 控制台点击 "New +" -> "Blueprint"
3. 选择你的仓库
4. 在 "Environment Variables" 中设置 `DEEPSEEK_API_KEY`
5. 点击 "Apply" 完成部署

### 方法二：手动部署

1. **创建 Web Service**:
   - 选择你的代码仓库
   - Runtime: Docker
   - Region: 选择最近的区域
   - Plan: Starter 或更高

2. **创建 PostgreSQL Database**:
   - Name: tiaozhanbei-db
   - Database Name: tiaozhanbei
   - User: tiaozhanbei
   - Plan: Starter

3. **配置环境变量** (在 Web Service 中):
   ```
   PORT: 10000
   DATABASE_URL: (从 Database 页面复制 Connection String)
   DEEPSEEK_API_KEY: (你的 DeepSeek API Key)
   JPA_DDL: update
   ```

4. **部署完成后**:
   - 获取服务地址（如: https://your-service.onrender.com）
   - 更新前端 `utils/api.js` 中的 `BASE_URL`

## 项目结构

```
src/main/java/com/tiaozhanbei/
├── config/           # 配置类（CORS、DeepSeek）
├── controller/       # REST 控制器
├── dto/              # 数据传输对象
├── entity/           # JPA 实体类
├── repository/       # 数据访问层
└── service/          # 业务逻辑层
```

## API 接口

### 健康检查
- `GET /api/ai/health` - 检查服务状态

### AI 相关
- `POST /api/ai/chat` - AI 对话
- `POST /api/ai/law/search` - 法律条文搜索

### 用户管理
- `POST /api/user/login` - 用户登录
- `GET /api/user/info/{userId}` - 获取用户信息
- `PUT /api/user/update/{userId}` - 更新用户信息

### 收藏管理
- `GET /api/favorite/list/{userId}` - 获取收藏列表
- `POST /api/favorite/add/{userId}` - 添加收藏
- `DELETE /api/favorite/remove/{userId}/{favoriteId}` - 删除收藏

### 咨询管理
- `GET /api/consultation/list/{userId}` - 获取咨询列表
- `POST /api/consultation/create/{userId}` - 创建咨询
- `GET /api/consultation/detail/{userId}/{consultationId}` - 获取咨询详情
- `DELETE /api/consultation/delete/{userId}/{consultationId}` - 删除咨询

### 合同审查
- `GET /api/contract/list/{userId}` - 获取合同列表
- `GET /api/contract/list/{userId}/{type}` - 按类型获取合同
- `POST /api/contract/create/{userId}` - 创建合同审查
- `GET /api/contract/detail/{userId}/{contractId}` - 获取合同详情
- `DELETE /api/contract/delete/{userId}/{contractId}` - 删除合同

### 文书模板
- `GET /api/template/list` - 获取所有模板
- `GET /api/template/list/{category}` - 按分类获取模板
- `GET /api/template/detail/{templateId}` - 获取模板详情
- `POST /api/template/download/{templateId}` - 下载模板（增加下载计数）

## 注意事项

1. **DeepSeek API Key**: 需要在 [DeepSeek 官网](https://platform.deepseek.com) 申请 API Key
2. **数据库初始化**: JPA 会自动通过 `ddl-auto: update` 创建表结构
3. **Render 休眠**: 免费版 Render 服务会在 15 分钟无活动后休眠，首次访问可能较慢
4. **小程序域名白名单**: 需要在微信公众平台配置服务器域名白名单


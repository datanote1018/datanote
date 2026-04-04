# 贡献指南

感谢你对 DataNote 的关注！以下是参与贡献的流程。

## 开发环境

- JDK 8（不使用 9+ 语法）
- Maven 3.6+
- MySQL 8.0
- IDE 推荐：IntelliJ IDEA

## 技术约束

在提交代码前，请确保遵守以下规则：

### Java 后端
- **Java 8 语法**：不使用 `var`、text block、record、switch expression、`Stream.toList()`
- **Spring Boot 2.7**：使用 `javax` 而非 `jakarta` 命名空间
- **Lombok**：使用 `@Data`、`@RequiredArgsConstructor`，不手写 getter/setter
- **分层架构**：Controller 只做参数校验和调用 Service，业务逻辑在 Service 层
- **SQL 安全**：使用 MyBatis-Plus QueryWrapper 或 `#{}` 参数绑定，禁止字符串拼接 SQL
- **Hive 数据源**：不注册为 Spring Bean，通过 `HiveConfig.getConnection()` 获取

### 前端
- **vanilla JS**：不使用任何框架
- **变量声明**：统一使用 `var`，不使用 `const` / `let`
- **函数定义**：使用 `function` 关键字，不使用箭头函数
- **XSS 防护**：innerHTML 拼接用户输入时必须使用 `escapeHtml()`
- **兼容性**：不使用可选链 `?.`、空值合并 `??`

### 数仓命名
- 遵循 [数仓模型命名规范](docs/数仓模型命名规范.md)
- 表名 = 调度任务名 = 脚本文件名

## 提交流程

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/your-feature`
3. 提交改动：`git commit -m 'feat: 添加xxx功能'`
4. 推送分支：`git push origin feature/your-feature`
5. 创建 Pull Request

## Commit 规范

```
feat: 新功能
fix: 修复 Bug
refactor: 重构（不改变功能）
docs: 文档更新
test: 测试相关
chore: 构建/工具变更
```

## 数据库变更

- 每次表结构变更，在 `sql/` 目录下创建编号递增的 SQL 文件
- 格式：`{编号}_{描述}.sql`，如 `21_add_user_role.sql`

## 代码审查红线

以下问题会被直接拒绝：

- Java 9+ 语法
- 循环依赖（构造器注入成环）
- innerHTML 拼接未转义的用户输入
- SQL 字符串拼接
- 硬编码密码或密钥

# DataNote

**轻量级一站式数据开发平台** — 单 JAR 包即可运行，覆盖数据同步、SQL 开发、任务调度、数据地图、数据质量、AI 辅助全链路。

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-8+-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7-green.svg)](https://spring.io/projects/spring-boot)

---

## 为什么做 DataNote

市面上的数据开发平台要么太重（DataSphereStudio 需要 Linkis + 多个子项目），要么太散（DolphinScheduler 只管调度、SeaTunnel 只管同步）。

DataNote 的目标是：**一个 JAR 包 + 一个 HTML 文件，5 分钟跑起来一个完整的数据开发平台。**

## 核心功能

| 模块 | 功能 |
|------|------|
| **数据同步** | MySQL → Hive 全量/增量同步（DataX 引擎），字段映射，一键建表 |
| **SQL 开发** | Monaco Editor 在线 IDE，HiveSQL 执行，结果排序/筛选/导出 CSV |
| **任务调度** | Cron 定时调度、DAG 依赖管理、失败重试（指数退避）、超时告警 |
| **数据地图** | AI 智能搜索、表详情（字段/预览/探查/DDL）、收藏、评论 |
| **数据质量** | 规则配置、质量检查、检查历史 |
| **指标管理** | 指标定义 CRUD、分类管理 |
| **AI 辅助** | NL2SQL、SQL 解释/优化、AI 搜索、AI 生成表名/Cron、语音输入 |

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 8 + Spring Boot 2.7 + MyBatis-Plus 3.5 |
| 前端 | 单文件 SPA（vanilla JS，零框架依赖） |
| 数据库 | MySQL 8.0（元数据） + Apache Hive（数仓） |
| 数据同步 | DataX |
| AI | Claude API（可选，不配置不影响核心功能） |

## 快速开始

### 方式一：Docker 一键启动（推荐）

只需安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)，Windows / Mac / Linux 通用。

**完整版**（含 Hadoop + Hive，需要 8GB 内存）：

```bash
git clone https://github.com/datanote1018/datanote.git
cd datanote
docker-compose up -d
# 等待 2-3 分钟，访问 http://localhost:8099
```

**轻量版**（仅 MySQL，需要 1GB 内存，Hive 相关功能不可用）：

```bash
docker-compose -f docker-compose-lite.yml up -d
# 等待 1 分钟，访问 http://localhost:8099
```

### 方式二：本地开发部署

**环境要求**：JDK 8+ / Maven 3.6+ / MySQL 8.0 / （可选）Hive 3.x + DataX

```bash
# 1. 克隆
git clone https://github.com/datanote1018/datanote.git
cd datanote

# 2. 初始化数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS datanote DEFAULT CHARSET utf8mb4;"
mysql -u root -p datanote < sql/init-all.sql

# 3. 配置
cp src/main/resources/application-example.yml src/main/resources/application-local.yml
# 编辑 application-local.yml 填入你的数据库连接信息

# 4. 启动
export DB_PASSWORD=your_password
mvn spring-boot:run -DskipTests -Dspring.profiles.active=local

# 5. 访问 http://localhost:8099
```

## 配置说明

核心配置通过环境变量注入，无需修改配置文件：

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `DB_PASSWORD` | MySQL 密码 | （必填） |
| `DB_HOST` | MySQL 地址 | 127.0.0.1 |
| `DB_PORT` | MySQL 端口 | 3306 |
| `HIVE_URL` | Hive JDBC 地址 | jdbc:hive2://127.0.0.1:10000/default;auth=noSasl |
| `DATAX_HOME` | DataX 安装目录 | /opt/datax |
| `CLAUDE_API_KEY` | Claude API Key（AI 功能） | （可选） |

## 项目结构

```
datanote/
├── src/main/java/com/datanote/
│   ├── controller/    # REST API（18 个）
│   ├── service/       # 业务逻辑（13 个）
│   ├── mapper/        # MyBatis-Plus Mapper（22 个）
│   ├── model/         # 实体类（24 个）
│   ├── config/        # 配置类
│   └── util/          # 工具类
├── src/main/resources/
│   ├── static/workspace.html  # 前端 SPA
│   └── application.yml        # 配置文件
├── sql/               # 数据库迁移脚本（编号递增）
├── docs/              # 设计文档
│   ├── 数仓模型命名规范.md
│   ├── 电商行业数仓设计方案.md
│   └── ...
├── CLAUDE.md          # AI 辅助开发规则
├── LICENSE            # Apache 2.0
└── pom.xml
```

## 数仓命名规范

DataNote 内置了一套数仓模型命名规范，详见 [数仓模型命名规范](docs/数仓模型命名规范.md)。

核心原则：**表名 = 调度任务名 = 脚本文件名**，全链路统一命名。

```
ods_{库名}_{表名}_{频率}           # ODS 层
dwd_{主题}_{描述}_dtl_{频率}       # DWD 明细层
dws_{主题}_{描述}_agg_{粒度}_{频率} # DWS 汇总层
ads_{主题}_{描述}_{粒度}_{频率}     # ADS 应用层
```

## 与同类项目对比

| 特性 | DataNote | DataSphereStudio | Dinky | qData |
|------|----------|-----------------|-------|-------|
| 部署方式 | 单 JAR 包 | Linkis + 多子项目 | Docker | 微服务 |
| 前端 | 单 HTML 文件 | React 多模块 | Ant Design Pro | Vue 3 |
| 数据同步 | 内置 DataX | Exchangis | FlinkCDC | 多引擎 |
| AI 辅助 | 内置 NL2SQL/AI 搜索 | 无 | 无 | 无 |
| 数据质量 | 内置 | Qualitis（独立项目） | 无 | 有 |
| 上手时间 | 5 分钟 | 1-2 天 | 30 分钟 | 1 小时 |

## 参与贡献

欢迎提交 Issue 和 Pull Request！请阅读 [贡献指南](CONTRIBUTING.md) 了解详情。

## 许可证

[Apache License 2.0](LICENSE)

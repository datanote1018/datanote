# DataNote 数据开发平台 — 系统架构设计

> 设计日期：2026-03-25
> 运行环境：MacBook M5 / macOS 26.3.1 / 16GB 内存
> 开发语言：Java 8 + Spring Boot 2.7.18

---

## 一、技术选型

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 1.8（Zulu OpenJDK） | 全平台统一，已安装 |
| Spring Boot | 2.7.18 | 最后一个支持 JDK 8 的稳定版 |
| Maven | 3.9.x | 构建工具 |
| MyBatis-Plus | 3.5.x | ORM，简化 CRUD |
| Hive JDBC | 3.1.2 | 连 HiveServer2 执行 DDL 和 SQL |
| MySQL Connector | 8.0.33 | 连 MySQL 读元数据 |
| DolphinScheduler API | REST | 调度任务的创建和触发 |
| DataX | 3.0 | 数据同步，纯 Java 调用（不依赖 Python） |

---

## 二、功能模块

| 模块 | 说明 |
|------|------|
| 数据集成 | 可视化配置 MySQL → Hive 同步任务，自动建表 + DataX 同步 |
| 数据开发 | SQL 在线编辑器，连 HiveServer2 执行，分层管理（ODS/DWD/DWS/ADS） |
| 数据运维 | 任务运行监控、日志查看、重跑，封装 DolphinScheduler API |
| 数据治理 | 数据资产目录、质量规则管理、表标签 |
| 系统管理 | 用户/角色/权限、数据源管理 |

---

## 三、系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│  浏览器（前端页面，放在 Spring Boot static 目录）               │
│  index.html / dev.html（数据集成 + SQL 编辑器）                │
└──────────────────────────┬──────────────────────────────────┘
                           │ AJAX
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Spring Boot 后端（:8099）                                    │
│                                                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐      │
│  │ 数据集成  │ │ 数据开发  │ │ 数据运维  │ │ 数据治理   │      │
│  │Controller│ │Controller│ │Controller│ │ Controller │      │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └─────┬─────┘      │
│       │             │            │              │             │
│  ┌────▼─────────────▼────────────▼──────────────▼─────┐      │
│  │                  Service 层                         │      │
│  │  Metadata / Hive / HiveQuery / DataX / Scheduler   │      │
│  │  Asset / Quality / User / DataSource               │      │
│  └────┬──────────┬──────────┬──────────┬──────────────┘      │
│       │          │          │          │                      │
└───────┼──────────┼──────────┼──────────┼──────────────────────┘
        │          │          │          │
  ┌─────▼──┐ ┌────▼───┐ ┌───▼───┐ ┌───▼──────────────┐
  │ MySQL  │ │  Hive   │ │ DataX │ │ DolphinScheduler │
  │ 3306   │ │ 10000   │ │ (Java)│ │ REST API :12345  │
  └────────┘ └────────┘ └───────┘ └──────────────────┘
```

---

## 四、项目结构

```
datanote/
├── pom.xml
├── src/main/java/com/datanote/
│   ├── DataNoteApplication.java              ← 启动类
│   │
│   ├── config/
│   │   ├── DataSourceConfig.java             ← MySQL 数据源
│   │   ├── HiveConfig.java                   ← Hive JDBC 连接
│   │   ├── CorsConfig.java                   ← 跨域配置
│   │   └── SecurityConfig.java               ← JWT 认证过滤器
│   │
│   ├── controller/
│   │   ├── MetadataController.java           ← 数据集成：库/表/字段查询
│   │   ├── HiveDdlController.java            ← 数据集成：Hive 建表
│   │   ├── DataxController.java              ← 数据集成：DataX 配置生成 + 执行
│   │   ├── HiveQueryController.java          ← 数据开发：执行 SQL、返回结果
│   │   ├── SchedulerController.java          ← 数据运维：任务监控/重跑/日志
│   │   ├── AssetController.java              ← 数据治理：资产目录
│   │   ├── QualityController.java            ← 数据治理：质量规则
│   │   ├── UserController.java               ← 系统管理：用户/登录
│   │   └── DataSourceController.java         ← 系统管理：数据源管理
│   │
│   ├── service/
│   │   ├── MetadataService.java              ← 读 MySQL information_schema
│   │   ├── HiveService.java                  ← 拼 DDL + 执行建表
│   │   ├── HiveQueryService.java             ← 执行 HiveSQL + 分页返回结果
│   │   ├── DataxService.java                 ← 生成 DataX JSON + 调 Java 执行
│   │   ├── SchedulerService.java             ← 封装 DolphinScheduler REST API
│   │   ├── AssetService.java                 ← 读 Hive Metastore 构建资产目录
│   │   ├── QualityService.java               ← 质量规则引擎
│   │   ├── UserService.java                  ← 用户 CRUD + JWT 认证
│   │   └── DataSourceService.java            ← 多数据源管理
│   │
│   ├── model/
│   │   ├── ColumnInfo.java                   ← 字段元数据
│   │   ├── TableInfo.java                    ← 表元数据
│   │   ├── DataxJobConfig.java               ← DataX 任务配置
│   │   ├── HiveQueryResult.java              ← SQL 执行结果
│   │   ├── TaskInstance.java                 ← 调度任务实例
│   │   ├── AssetTable.java                   ← 数据资产表
│   │   ├── QualityRule.java                  ← 质量规则定义
│   │   ├── User.java                         ← 用户
│   │   └── DataSourceInfo.java               ← 数据源连接信息
│   │
│   ├── mapper/
│   │   ├── UserMapper.java
│   │   ├── DataSourceMapper.java
│   │   ├── SyncTaskMapper.java
│   │   ├── ScriptMapper.java
│   │   ├── TaskExecutionMapper.java
│   │   └── QualityRuleMapper.java
│   │
│   └── util/
│       ├── TypeMappingUtil.java              ← MySQL→Hive 类型映射（全部 string）
│       ├── ProcessUtil.java                  ← 命令行调用封装
│       └── JwtUtil.java                      ← JWT 工具
│
├── src/main/resources/
│   ├── application.yml                       ← 主配置文件
│   ├── templates/
│   │   └── mysql2hive.json                   ← DataX JSON 模板
│   └── static/                               ← 前端页面
│       ├── index.html
│       └── dev.html
```

---

## 五、核心 API 接口

### 数据集成

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/metadata/databases` | 查询 MySQL 所有库 |
| GET | `/api/metadata/tables?db=xxx` | 查询指定库的所有表 |
| GET | `/api/metadata/columns?db=xxx&table=xxx` | 查询字段名、类型、注释、是否主键 |
| POST | `/api/hive/create-table` | 拼 DDL，调 HiveServer2 建表 |
| POST | `/api/datax/generate-job` | 生成 DataX JSON 配置文件 |
| POST | `/api/datax/run` | 纯 Java 调用 DataX Engine 执行同步 |

### 数据开发

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/hive/execute-sql` | 执行 HiveSQL，返回结果集 |
| GET | `/api/scripts/tree` | 获取脚本目录树 |
| GET | `/api/scripts/{id}` | 获取脚本内容 |
| POST | `/api/scripts` | 创建脚本 |
| PUT | `/api/scripts/{id}` | 更新脚本内容 |
| GET | `/api/scripts/{id}/versions` | 获取脚本历史版本 |

### 数据运维

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tasks/executions` | 查询任务执行列表（分页） |
| GET | `/api/tasks/executions/{id}/log` | 查看执行日志 |
| POST | `/api/tasks/executions/{id}/rerun` | 重跑任务 |
| POST | `/api/scheduler/create` | 创建调度任务（调 DS API） |
| POST | `/api/scheduler/online/{id}` | 上线调度 |
| POST | `/api/scheduler/offline/{id}` | 下线调度 |

### 数据治理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/assets/tables` | 资产目录（读 Hive Metastore） |
| GET | `/api/assets/tables/{name}/detail` | 表详情（字段/分区/存储） |
| POST | `/api/quality/rules` | 创建质量规则 |
| GET | `/api/quality/rules` | 规则列表 |
| POST | `/api/quality/rules/{id}/run` | 执行质量检测 |
| GET | `/api/quality/logs` | 检测日志 |

### 系统管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录，返回 JWT |
| GET | `/api/users` | 用户列表 |
| POST | `/api/users` | 创建用户 |
| GET | `/api/datasources` | 数据源列表 |
| POST | `/api/datasources` | 创建数据源 |
| POST | `/api/datasources/test` | 测试数据源连接 |

---

## 六、数据库表设计（MySQL datanote 库，共 12 张表）

### 系统管理（3 张）

```sql
-- 用户表
CREATE TABLE dn_user (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  username     VARCHAR(50)  NOT NULL UNIQUE,
  password     VARCHAR(128) NOT NULL COMMENT '加密存储',
  real_name    VARCHAR(50)  DEFAULT '',
  email        VARCHAR(100) DEFAULT '',
  role_id      BIGINT       DEFAULT 0,
  status       TINYINT      DEFAULT 1 COMMENT '1启用 0禁用',
  created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 角色表
CREATE TABLE dn_role (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_name    VARCHAR(50)  NOT NULL UNIQUE,
  permissions  TEXT         COMMENT '权限列表 JSON',
  description  VARCHAR(200) DEFAULT '',
  created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP
);

-- 数据源配置表
CREATE TABLE dn_datasource (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  name         VARCHAR(100) NOT NULL COMMENT '数据源名称',
  type         VARCHAR(20)  NOT NULL COMMENT 'MySQL/Hive/PostgreSQL/Oracle',
  host         VARCHAR(200) NOT NULL,
  port         INT          NOT NULL,
  database_name VARCHAR(100) DEFAULT '',
  username     VARCHAR(100) DEFAULT '',
  password     VARCHAR(200) DEFAULT '' COMMENT '加密存储',
  extra_params VARCHAR(500) DEFAULT '' COMMENT '额外连接参数',
  status       TINYINT      DEFAULT 1 COMMENT '1可用 0不可用',
  created_by   VARCHAR(50)  DEFAULT '',
  created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 数据集成（2 张）

```sql
-- 同步任务定义
CREATE TABLE dn_sync_task (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_name       VARCHAR(200) NOT NULL COMMENT '任务名称',
  source_ds_id    BIGINT       NOT NULL COMMENT '源数据源ID',
  source_db       VARCHAR(100) NOT NULL COMMENT '源库',
  source_table    VARCHAR(200) NOT NULL COMMENT '源表',
  target_db       VARCHAR(100) NOT NULL DEFAULT 'ods' COMMENT '目标库',
  target_table    VARCHAR(200) NOT NULL COMMENT '目标表（如 ods_business_orders_full）',
  sync_mode       VARCHAR(20)  DEFAULT 'full' COMMENT 'full全量 / incr增量',
  partition_field VARCHAR(50)  DEFAULT 'dt' COMMENT '分区字段',
  status          TINYINT      DEFAULT 1 COMMENT '1启用 0禁用',
  created_by      VARCHAR(50)  DEFAULT '',
  created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 字段映射
CREATE TABLE dn_sync_field_mapping (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id         BIGINT       NOT NULL COMMENT '关联 dn_sync_task.id',
  source_field    VARCHAR(200) NOT NULL COMMENT '源字段名',
  source_type     VARCHAR(100) NOT NULL COMMENT '源字段类型',
  target_field    VARCHAR(200) NOT NULL COMMENT '目标字段名',
  target_type     VARCHAR(100) NOT NULL DEFAULT 'string' COMMENT '目标字段类型',
  field_comment   VARCHAR(500) DEFAULT '' COMMENT '字段注释',
  is_sync         TINYINT      DEFAULT 1 COMMENT '1同步 0跳过',
  sort_order      INT          DEFAULT 0 COMMENT '排序',
  INDEX idx_task_id (task_id)
);
```

### 数据开发（3 张）

```sql
-- 脚本目录
CREATE TABLE dn_script_folder (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  folder_name  VARCHAR(100) NOT NULL COMMENT '目录名称',
  parent_id    BIGINT       DEFAULT 0 COMMENT '父目录ID，0为根目录',
  layer        VARCHAR(20)  DEFAULT '' COMMENT '数仓层级：ODS/DWD/DWS/ADS/脚本/数据集成',
  sort_order   INT          DEFAULT 0,
  created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP
);

-- 脚本文件
CREATE TABLE dn_script (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  folder_id    BIGINT       NOT NULL COMMENT '所属目录ID',
  script_name  VARCHAR(200) NOT NULL COMMENT '脚本名称',
  script_type  VARCHAR(20)  NOT NULL COMMENT 'HiveSQL/Shell/Python/DataSync',
  content      LONGTEXT     COMMENT '脚本内容',
  description  VARCHAR(500) DEFAULT '',
  created_by   VARCHAR(50)  DEFAULT '',
  updated_by   VARCHAR(50)  DEFAULT '',
  created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_folder_id (folder_id)
);

-- 脚本版本
CREATE TABLE dn_script_version (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  script_id    BIGINT       NOT NULL COMMENT '关联 dn_script.id',
  version      INT          NOT NULL COMMENT '版本号',
  content      LONGTEXT     NOT NULL COMMENT '该版本内容',
  commit_msg   VARCHAR(200) DEFAULT '' COMMENT '提交备注',
  committed_by VARCHAR(50)  DEFAULT '',
  committed_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_script_id (script_id)
);
```

### 任务执行（1 张，统一存储所有类型任务的执行记录）

```sql
-- 任务执行记录（统一）
CREATE TABLE dn_task_execution (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  script_id       BIGINT       DEFAULT NULL COMMENT '关联 dn_script.id（SQL任务）',
  sync_task_id    BIGINT       DEFAULT NULL COMMENT '关联 dn_sync_task.id（同步任务）',
  task_type       VARCHAR(20)  NOT NULL COMMENT 'HiveSQL/Shell/DataSync',
  trigger_type    VARCHAR(20)  NOT NULL COMMENT 'manual手动 / schedule调度',
  ds_instance_id  BIGINT       DEFAULT NULL COMMENT 'DolphinScheduler 实例ID（调度触发时）',
  status          VARCHAR(20)  NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/RUNNING/SUCCESS/FAILED',
  start_time      DATETIME     DEFAULT NULL,
  end_time        DATETIME     DEFAULT NULL,
  duration        INT          DEFAULT 0 COMMENT '耗时（秒）',
  read_count      BIGINT       DEFAULT 0 COMMENT '读取条数（同步任务用）',
  write_count     BIGINT       DEFAULT 0 COMMENT '写入条数（同步任务用）',
  error_count     BIGINT       DEFAULT 0 COMMENT '错误条数',
  log             LONGTEXT     COMMENT '运行日志',
  executor        VARCHAR(50)  DEFAULT '' COMMENT '执行人',
  created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_script_id (script_id),
  INDEX idx_sync_task_id (sync_task_id),
  INDEX idx_status (status),
  INDEX idx_start_time (start_time)
);
```

### 数据治理（3 张）

```sql
-- 质量规则
CREATE TABLE dn_quality_rule (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_name    VARCHAR(200) NOT NULL COMMENT '规则名称',
  datasource_id BIGINT      DEFAULT NULL COMMENT '数据源ID',
  db_name      VARCHAR(100) NOT NULL,
  table_name   VARCHAR(200) NOT NULL,
  field_name   VARCHAR(200) DEFAULT '' COMMENT '字段（为空表示表级规则）',
  rule_type    VARCHAR(30)  NOT NULL COMMENT 'NOT_NULL/UNIQUE/RANGE/REGEX/CUSTOM_SQL',
  threshold    VARCHAR(100) DEFAULT '' COMMENT '阈值（如 >0.95 表示非空率需>95%）',
  custom_sql   TEXT         COMMENT '自定义检测SQL',
  status       TINYINT      DEFAULT 1 COMMENT '1启用 0禁用',
  created_by   VARCHAR(50)  DEFAULT '',
  created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 质量检测日志
CREATE TABLE dn_quality_log (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_id      BIGINT       NOT NULL COMMENT '关联 dn_quality_rule.id',
  check_time   DATETIME     NOT NULL COMMENT '检测时间',
  result       VARCHAR(20)  NOT NULL COMMENT 'PASS/WARN/FAIL',
  actual_value VARCHAR(200) DEFAULT '' COMMENT '实际检测值',
  detail       TEXT         COMMENT '详情',
  INDEX idx_rule_id (rule_id),
  INDEX idx_check_time (check_time)
);

-- 资产标签
CREATE TABLE dn_table_tag (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  db_name      VARCHAR(100) NOT NULL,
  table_name   VARCHAR(200) NOT NULL,
  tag          VARCHAR(50)  NOT NULL COMMENT '标签（如：核心表、日更新、周报表）',
  description  VARCHAR(500) DEFAULT '',
  owner        VARCHAR(50)  DEFAULT '' COMMENT '负责人',
  created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_table (db_name, table_name)
);
```

---

## 七、不需要建表的数据

| 数据 | 来源 | 说明 |
|------|------|------|
| MySQL 库/表/字段元数据 | `information_schema` | 实时查询，不落本地库 |
| Hive 表/分区/存储信息 | `hive_metastore` 库 | DolphinScheduler 安装时已有 |
| 调度工作流/实例 | DolphinScheduler REST API | 不重复存储 |

---

## 八、配置文件 application.yml

```yaml
server:
  port: 8099

spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/datanote?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis-plus:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true

# Hive 连接
hive:
  url: jdbc:hive2://127.0.0.1:10000/default
  username: ""
  password: ""

# DataX（纯 Java 调用，不依赖 Python）
datax:
  home: /opt/datax
  jvm: "-Xms1g -Xmx1g"
  job-dir: /tmp/datax_jobs

# DolphinScheduler API
dolphin:
  api-url: http://127.0.0.1:12345/dolphinscheduler
  token: ""
```

---

## 九、DataX 调用方式（纯 Java，不依赖 Python）

```bash
java -server -Xms1g -Xmx1g \
  -classpath /opt/datax/lib/* \
  com.alibaba.datax.core.Engine \
  -mode standalone \
  -jobid -1 \
  -job /tmp/datax_jobs/ods_business_orders_full.json
```

后端通过 `ProcessBuilder` 调用上述命令，解析退出码和输出日志。

退出码：0=成功，-1=失败，143=被 kill。

---

## 十、核心业务流程

### 流程 1：一键建表并同步

```
1. 前端 GET /api/metadata/columns?db=business&table=orders
   ← 返回字段列表 [{name:"order_id", type:"int(11)", comment:"订单ID"}, ...]

2. 前端展示字段映射（所有类型映射为 string），用户确认

3. POST /api/hive/create-table
   → 后端拼 DDL：CREATE EXTERNAL TABLE IF NOT EXISTS ods.ods_business_orders_full (...)
   → 通过 Hive JDBC 执行
   → 写入 dn_sync_task + dn_sync_field_mapping
   ← 返回 {success: true, taskId: 1}

4. POST /api/datax/generate-job
   → 用模板填充 mysqlreader + hdfswriter 配置
   → 写入 /tmp/datax_jobs/ods_business_orders_full.json
   ← 返回 {jobPath: "..."}

5. POST /api/datax/run
   → ProcessBuilder 调用 java ... com.alibaba.datax.core.Engine -job ...
   → 写入 dn_task_execution
   ← 返回 {exitCode: 0, readCount: 50000, writeCount: 50000}
```

### 流程 2：SQL 在线开发

```
1. 用户在编辑器写 HiveSQL，点「运行」

2. POST /api/hive/execute-sql
   body: {sql: "SELECT count(*) FROM ods.ods_business_orders_full WHERE dt='2026-03-25'"}
   → 后端通过 Hive JDBC 执行
   → 写入 dn_task_execution（trigger_type=manual）
   ← 返回 {columns: ["_c0"], rows: [[50000]], duration: 3}
```

### 流程 3：质量检测

```
1. 用户创建规则：ods_business_orders_full.order_id 非空率 > 95%

2. POST /api/quality/rules/{id}/run
   → 后端生成检测 SQL：
     SELECT count(CASE WHEN order_id IS NOT NULL THEN 1 END) / count(*) FROM ...
   → 执行并对比阈值
   → 写入 dn_quality_log
   ← 返回 {result: "PASS", actual: "1.0"}
```

---

## 十一、部署方式

```bash
# 开发阶段
cd datanote
mvn spring-boot:run

# 打包发布
mvn clean package -DskipTests
java -jar target/datanote-1.0.0.jar

# 访问地址
# 平台首页：http://localhost:8099/index.html
# 数据开发：http://localhost:8099/dev.html
# 后端 API：http://localhost:8099/api/...
```

前端页面放在 `src/main/resources/static/` 目录，Spring Boot 自动提供静态文件服务，无需 Nginx。

---

## 十二、开发计划

| 阶段 | 模块 | 内容 |
|------|------|------|
| **第一阶段** | 基础 + 数据集成 | 安装 Maven、项目骨架、建表、元数据接口、Hive 建表、DataX 同步、前端对接 |
| **第二阶段** | 数据开发 | HiveSQL 在线执行、脚本管理（CRUD + 版本）、结果展示 |
| **第三阶段** | 数据运维 | 对接 DolphinScheduler API、任务监控页面、日志查看、重跑 |
| **第四阶段** | 数据治理 + 系统管理 | 资产目录、质量规则、用户登录(JWT)、数据源管理 |

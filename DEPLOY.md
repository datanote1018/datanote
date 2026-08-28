# DataNote 部署指南

## 部署流程

```
datanote.conf                      ← 共享配置文件（端口、密码、模式等）
     ↓              ↓                    ↓
setup-hive.sh    setup-datax.sh     setup-datanote.sh
第一步：集群环境  第二步：DataX(可选)  第三步：DataNote
（必装）          （按需开关）         （必装）
```

三个脚本共享 `datanote.conf` 配置文件。端口冲突、DataX 模式切换等会自动写入配置，下游脚本自动读取。

---

## 第一步：安装 Hadoop + Hive 环境

> 如果你已有 Hadoop + Hive 环境，跳到第二步。

### 前提

- 已安装 [Docker Desktop](https://docs.docker.com/get-docker/)
- 内存建议 8GB+

### 执行

```bash
git clone https://github.com/datanote1018/datanote.git
cd datanote
./setup-hive.sh
```

等待 3-5 分钟，看到 `部署完成` 后验证：

```bash
./setup-hive.sh test
```

看到 `SHOW DATABASES` 输出和 `连接正常` 提示即表示成功。

### 脚本命令

| 命令 | 说明 |
|------|------|
| `./setup-hive.sh` | 安装并启动 |
| `./setup-hive.sh test` | 验证 Hive 是否可用 |
| `./setup-hive.sh stop` | 停止（保留数据） |
| `./setup-hive.sh clean` | 停止并删除所有数据 |

### 启动的服务

| 服务 | 容器名 | 宿主机端口 |
|------|--------|-----------|
| MySQL | datanote-mysql | 3306 |
| HDFS NameNode | datanote-namenode | 9870（Web UI）/ 8020（RPC） |
| HDFS DataNode | datanote-datanode | - |
| Hive Metastore | datanote-metastore | 9083 |
| HiveServer2 | datanote-hiveserver2 | 10800 |

---

## 第二步：安装 DataNote

### 前提

- Java 8+
- 第一步已完成，或已有可用的 Hadoop + Hive 环境

### 执行

```bash
./setup-datanote.sh
```

脚本会自动：检查 Java → 检查 MySQL → 编译 JAR（首次）→ 初始化数据库（首次）→ 启动

### 配置 Hive 连接

打开 http://localhost:8099 → **系统管理** → **数据源管理**：

**如果用的是 setup-hive.sh 安装的环境：**

| 配置项 | 值 |
|--------|-----|
| 主机地址 | 127.0.0.1 |
| 端口 | 10800 |
| 认证方式 | NOSASL |
| NameNode | hdfs://localhost:8020 |
| 仓库路径 | /user/hive/warehouse |

**如果用的是自己的 Hive 环境：**

| 配置项 | 在哪看 |
|--------|--------|
| 主机地址 | `hive-site.xml` → `hive.server2.thrift.bind.host` |
| 端口 | `hive-site.xml` → `hive.server2.thrift.port`（默认 10000） |
| 认证方式 | `hive-site.xml` → `hive.server2.authentication` |
| NameNode | `core-site.xml` → `fs.defaultFS` |

填完点 **测试连接** → 通过后点 **保存配置**。

### 脚本命令

| 命令 | 说明 |
|------|------|
| `./setup-datanote.sh` | 启动 DataNote |
| `./setup-datanote.sh stop` | 停止 DataNote |

### 文件说明

| 文件 | 路径 |
|------|------|
| JAR 包 | `./target/datanote-1.0.0.jar` |
| 运行日志 | `/tmp/datanote.log` |
| 初始化 SQL | `./sql/init-all.sql` |

---

## 第三步：配置 AI 服务（可选）

需求分析、AI 问答、知识库这几个功能依赖大模型 API。不用这些功能可以跳过整节。

### 配置 API Key

**API Key 只放在本地 `ai-service/.env`，不进数据库、也不进代码仓库。**

```bash
cd ai-service
cp env.example .env          # 从模板复制
vi .env                       # 填入自己的 Key
```

`.env` 内容：

```ini
QWEN_API_KEY=sk-xxxxxxxxxxxxxxxx
```

Key 在哪申请：<https://bailian.console.aliyun.com/> → API-KEY 管理。

> `.env` 已被 `.gitignore` 排除，不会被误提交。**不要把 Key 写进任何其他文件。**

### 启动

```bash
cd ai-service
pip install -r requirements.txt
./start-ai.sh                 # 启动
./start-ai.sh stop            # 停止
```

启动后两个服务：

| 服务 | 端口 | 用途 |
|------|------|------|
| 对话 Agent | 8000 | 需求管理页面 |
| RAG 管理 | 8001 | AI 智能页面 |

没建 `.env` 时脚本会直接报错提示，不会静默失败。

### 模型和地址在哪改

`QWEN_API_KEY` 之外的两项——**模型名**和 **Base URL**——在页面上改：
「系统管理 → AI 配置」。这两项不是机密，存在数据库里，改完即时生效，不用重启。

优先级：页面上配了就用页面的，没配则用默认值（`qwen-plus` + 百炼兼容端点）。

---

## 关于加密密钥 CRYPTO_KEY

`datanote.conf` 里的 `CRYPTO_KEY` 用于加密**数据源密码**等敏感字段。

**首次部署必须自己生成一把，不要使用固定值：**

```bash
LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32; echo
```

把结果填进 `datanote.conf` 的 `CRYPTO_KEY=`。

两个注意事项：

- **`datanote.conf` 不在代码仓库里**（含密钥）。首次部署从模板复制：
  `cp datanote.conf.example datanote.conf`
- **换密钥后，之前加密存的数据会解不开**，需要在页面上重新录入数据源密码。
  所以这把密钥装好就别再改。

---

## 常见问题

**Q: setup-hive.sh test 失败？**
检查容器是否都在运行：`docker ps`。查看 HiveServer2 日志：`docker logs datanote-hiveserver2`

**Q: DataNote 启动后页面打不开？**
查看日志：`tail -50 /tmp/datanote.log`，通常是 MySQL 连接问题。

**Q: 已有环境怎么知道 Hive 的连接信息？**
问集群管理员，或查看 HiveServer2 所在机器的 `/etc/hive/conf/hive-site.xml`。

**Q: Mac M 芯片 Docker 很慢？**
正常，x86 镜像需要模拟。建议 Docker Desktop 分配 8GB+ 内存。

**Q: 不需要数据同步功能？**
DataX 配置可以不填，SQL 开发、调度、数据地图等功能正常使用。

**Q: AI 功能报「缺少 API Key」？**
检查 `ai-service/.env` 是否存在、`QWEN_API_KEY` 是否填了值。
模板在 `ai-service/env.example`。

**Q: 页面上改了 AI 配置没生效？**
模型和 Base URL 改完即时生效。API Key 不在页面上配，它只读 `ai-service/.env`，
改完需要重启 AI 服务：`cd ai-service && ./start-ai.sh stop && ./start-ai.sh`

# ecom-agent · 电商智能客服多 Agent 系统

> 基于 Spring Boot 3 + AgentScope + Ollama 的 C 端电商智能客服演示项目：
> 商品咨询、下单购买、订单/物流查询、售后退换货、服务工单的全流程多 Agent 协作。
>
> 作者：章文杰 · 技术演示项目（面试作品集）

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-brightgreen)](https://spring.io/projects/spring-boot)
[![AgentScope](https://img.shields.io/badge/AgentScope-1.0.12-blue)](https://github.com/agentscope-ai/agentscope)
[![React](https://img.shields.io/badge/React-19-61dafb)](https://react.dev/)

---

## 一、项目简介

一个**全离线可运行**的电商智能客服系统：用户用自然语言描述购物需求，系统通过多层意图识别
路由到对应的专业子智能体（订单管理 / 服务工单 / 信息咨询），以流式（SSE）返回客服式回复。

核心设计取舍：

- **模型全本地**：所有 LLM 推理走 Ollama（qwen2.5:7b 轻任务 + qwen3.8:27b 主链路 + bge-m3 向量），
  不依赖任何云端 API Key，断网可演示
- **三层意图识别**：L1 关键词规则（<50ms）→ L2 向量语义（<100ms）→ L3 LLM 兜底，
  高频请求零 LLM 成本，歧义请求才付出推理代价
- **条件流水线**：仅当 L1/L2 未命中（存在指代歧义）时才触发问题改写，降低延迟与 Token 消耗
- **人设可控**：无关需求（如"帮我喝汽水"）友好挡答并引回业务范围，不假装能处理

> 本项目为**技术演示作品**：商品/订单/物流均为 mock 数据，LLM 为本地开源模型，
> 不构成可商用的客服系统。

## 二、功能特性

| 模块 | 能力 |
|---|---|
| 💬 智能对话 | 流式回复、思考过程可视化、追问交互（ask_user）、推荐问题 |
| 🛍️ 售前咨询 | 商品信息/价格/活动规则咨询（InfoAgent + RAG 知识库） |
| 📦 售中订单 | 订单查询（含物流明细）、下单、取消订单（BookingAgent） |
| 🛠️ 售后服务 | 退换货/退款/发票以服务工单受理：提交/查询/修改/取消全生命周期（ItineraryManageAgent） |
| ⭐ 个性化推荐 | 「为你推荐」：偏好召回 → LLM 重排 → 可解释推荐理由 |
| ⚙️ 偏好设置 | 4 大类 15 字段购物偏好，DB + 长期记忆双写，读优先 DB |
| 🖥️ 客服工作台 | 工单处理记录管理（处理中/已通过/已驳回） |

## 三、系统架构

```
用户 → React 前端（SSE）→ ChatController（Sa-Token 鉴权）
                              │
                    AgentPipelineService（条件流水线）
                      ① 原始问题 L1/L2 快速意图识别
                      ② 未命中 → QueryRewritingAgent 问题改写 → 重新识别（L1/L2/L3）
                              │
                        MasterAgent（路由 / 整合 / 长期记忆）
                              │
        ┌──────────┬──────────┼──────────┐
   BookingAgent   ItineraryManageAgent   InfoAgent
   （订单管理）     （服务工单/售后）      （商品/规则咨询）
```

### 3.1 三层意图识别

| 层 | 机制 | 延迟 | 说明 |
|---|---|---|---|
| L1 | 关键词/正则规则（`IntentRuleMatcher`） | <50ms | 单类命中即短路；跨子智能体多类命中判歧义，移交 L3 |
| L2 | 向量相似度（bge-m3 + `intent-seed.yml` 58 条口语种子） | <100ms | 命中即短路 |
| L3 | LLM 意图分类（`IntentRecognitionAgent`） | ~秒级 | 输出 JSON：intents / primary_intent / multi_intent |
| L0 | 结构启发（连词检测） | <1ms | 疑似多意图复合句直接送 L3，避免被 L1/L2 吞成单意图 |

`AgentPipelineService.normalizeIntentJson()` 对 L3 结果做归一化（去重 + 按目标 Agent 合并 +
重算 multi_intent），兜底 LLM 过度拆分单一意图的问题。

### 3.2 Agent 清单

| Agent | 职责 |
|---|---|
| QueryRewritingAgent | 多轮对话指代消解（"这个/那单"→ 明确对象） |
| IntentRecognitionAgent | L3 意图分类（12 个电商意图） |
| MasterAgent | 唯一总入口：路由、结果透传、无关需求挡答、长期记忆 |
| BookingAgent | 订单查询/下单/取消（底层类名保留自差旅项目） |
| ItineraryManageAgent | 服务工单全生命周期 + 售后受理（同上） |
| InfoAgent | 商品/活动/平台规则咨询（RAG 双通道：知识库 vs 规则工具） |
| ItineraryPlanAgent / ReimbursementAgent | 占位（P3 接线：商品选购方案 / 退款专员） |

> **命名说明**：底层类名与工具名（`ItineraryManageAgent`、`query_travel_orders` 等）保留自
> 模板项目（智能差旅 Agent），电商语义化在提示词与展示层完成，重构排期见路线图。
> Java 包名为 `com.ecom.cs`（历史命名）。

## 四、技术栈

- **后端**：Java 21 · Spring Boot 3.5.6 · AgentScope 1.0.12 · MyBatis-Plus 3.5.9 ·
  Sa-Token（Redis Token）· Druid · Caffeine · Redis · MySQL 8
- **模型**：Ollama 本地推理（qwen2.5:7b / qwen3.8:27b-mlx / bge-m3 embedding），
  预留 DashScope（百炼）云端回滚开关（`ModelConfig` 注释块）
- **前端**：React 19 · Vite · Ant Design 6 · Zustand · react-markdown
- **部署**：Docker（host 网络）+ Nginx 反代 + NAS 持久化

## 五、快速开始

### 5.1 环境要求

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 21+ | **必须 21**（Lombok 与 JDK 27 不兼容，见 8.2） |
| Maven | 3.9+ | |
| MySQL | 8.x | 库名 `ecom_cs` |
| Redis | 6+ | 会话/Token/中断广播/熔断计数 |
| Ollama | 最新 | 需拉取模型（见下） |
| Node.js | 18+ | 仅前端构建需要 |

拉取 Ollama 模型（embedding 为意图 L2 刚需，不可跳过）：

```bash
ollama pull bge-m3          # 向量（意图识别 + 知识库）
ollama pull qwen2.5:7b      # 轻任务（标题/偏好抽取/意图 L3）
ollama pull qwen3.8:27b-mlx # 主链路（显存不足可换更小模型，见 5.3）
```

### 5.2 数据库初始化

```bash
mysql -uroot -p -e "CREATE DATABASE ecom_cs DEFAULT CHARACTER SET utf8mb4;"
mysql -uroot -p ecom_cs --default-character-set=utf8mb4 < src/main/resources/db/schema.sql
# 演示数据（test 账号：3 条工单 + 4 条订单，可选）
mysql -uroot -p ecom_cs --default-character-set=utf8mb4 < deploy/demo_data_test_account.sql
```

> ⚠️ 灌库务必带 `--default-character-set=utf8mb4`，否则中文乱码
> （乱码修复脚本见 `deploy/fix_mojibake.py`）。

### 5.3 启动后端

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS；Linux 用 JDK 21 路径
mvn spring-boot:run
# 或打包后运行
mvn package -DskipTests && java -jar target/ecom-agent-1.0.0-SNAPSHOT.jar
```

默认配置（`src/main/resources/application.yml`，均可用环境变量覆盖）：

| 配置 | 默认值 | 环境变量 |
|---|---|---|
| 服务端口 | 8094 | `SERVER_PORT` |
| MySQL | `127.0.0.1:3306/ecom_cs`（root，密码经 `MYSQL_PASSWORD` 注入） | `SPRING_DATASOURCE_URL` / `MYSQL_USER` / `MYSQL_PASSWORD` |
| Redis | `127.0.0.1:6379` | `REDIS_HOST` / `REDIS_PORT` |
| Ollama | `http://127.0.0.1:11434` | `OLLAMA_BASE_URL` |
| 轻任务模型 | qwen2.5:7b | `OLLAMA_FAST_MODEL` |
| 主链路模型 | qwen3.8:27b-mlx | `OLLAMA_STRONG_MODEL` |

显存紧张时把主链路模型换成小模型（如 `qwen2.5:7b`）：`OLLAMA_STRONG_MODEL=qwen2.5:7b`。
上下文窗口用 `OLLAMA_STRONG_NUM_CTX` 调整（默认 16K）。

### 5.4 启动前端（C 端用户端）

```bash
cd frontend
npm ci
VITE_API_BASE=http://127.0.0.1:8094 npm run dev     # 开发
# 或构建
VITE_API_BASE=http://127.0.0.1:8094 npm run build
```

访问 http://127.0.0.1:5173（dev）/ 静态 dist（build）。

### 5.5 启动管理端（B 端客服工作台）

```bash
cd admin
npm ci
npm run dev      # 开发，Vite 代理 /api → 127.0.0.1:8094
# 或构建
npm run build
```

管理端为独立 SPA（默认 8097，可用 `-p` 改端口），登录页仅放行 ADMIN 角色账号。

### 5.6 演示账号与分端约定

| 账号 | 密码 | 端 | 说明 |
|---|---|---|---|
| `test` | `123456` | 用户端（frontend） | 演示账号（预置 3 条工单 + 4 条订单，含在途物流） |
| `admin` | `123456` | 管理端（admin） | 客服工作台：会话接管、人工对话、工单审批 |

**admin 登录用户端也能用**（方便演示者预览 C 端体验，登录后有管理端入口横幅），
但管理端接口（`/api/admin/**`）严格校验 `@SaCheckRole("ADMIN")`，非管理员访问返回 403。

登录后依次尝试：「你好」→「我想喝汽水」（看挡答人设）→「我的订单到哪了」→
「先列出我所有的工单」→「我要退货」→「转人工」（演示人工接管全链路）。

## 六、API 摘要

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录，返回裸 token（无 Bearer 前缀） |
| POST | `/api/chat/{sessionId}` | 对话主入口，SSE 流式返回 |
| GET | `/api/my-orders/bookings` | 当前用户订单平铺列表 |
| GET | `/api/recommendations` | 个性化推荐 |
| GET | `/api/preferences` | 偏好读取/保存 |
| GET | `/api/admin/conversations` | 【管理端】全部会话列表（ADMIN） |
| GET | `/api/admin/conversations/{id}/messages` | 【管理端】会话消息回放（ADMIN） |
| POST | `/api/admin/conversations/{id}/takeover` | 【管理端】接管会话 → HUMAN 模式（ADMIN） |
| POST | `/api/admin/conversations/{id}/release` | 【管理端】结束接管 → AI 模式（ADMIN） |
| POST | `/api/admin/conversations/{id}/human-message` | 【管理端】人工客服发言（ADMIN） |

SSE 事件类型：`progress`（步骤进度）/ `thinking`（思考流）/ `message`（最终回复）/
`message_id`（反馈用消息 ID）/ `suggestions`（推荐问题）/ `user_interaction`（追问交互）。

转人工说明：用户发送「转人工」等表达时，L1 规则直接命中（不调 LLM），会话切为 HUMAN
模式并下发系统提示；此后用户消息不再触发 Agent，直到管理端「结束接管」。

## 七、部署

NAS/Docker 部署（容器创建、jar 热更新、nginx 配置、灌库运维）见
[`deploy/README.md`](deploy/README.md)。核心拓扑：

```
NAS(host 网络)
├── llmentor-ecom-backend  (temurin:21-jre, 8094, 挂载 jar)
├── llmentor-ecom-frontend (nginx:alpine,   8095, 反代 /api + /logs，C 端用户端)
├── llmentor-ecom-admin    (nginx:alpine,   8097, 反代 /api，管理端客服工作台)
├── MySQL 3306 / Redis 6379
└── Ollama 11434（可同机或独立机器，OLLAMA_BASE_URL 指定）
```

管理端 nginx 配置见 `deploy/admin-nginx.conf`。注意：8096 在部分 NAS 上被
智能家居 host-network 服务占用，选端口前先 `ss -tln` 确认。

## 八、开发说明

### 8.1 项目结构

```
ecom-agent/
├── src/main/java/com/ecom/cs/
│   ├── agent/            # 10 个 Agent + 工具类（tools/）+ Hook（hook/）
│   ├── business/         # 业务实体与服务（chat/booking/travel/ecom/recommend/audit）
│   ├── config/           # 模型装配、RAG 知识库、Sa-Token、MCP 注册
│   ├── controller/       # REST + SSE 入口（含 /api/admin/** 管理端接口）
│   └── intent/           # L1 规则 / L2 向量 / L3 路由编排
├── src/main/resources/
│   ├── prompts/          # 9 个 Agent 系统提示词（人设在此维护）
│   ├── skills/           # 6 个外部 CLI 技能（tuniu-cli 等）
│   ├── dataset/          # RAG 知识库文档（历史遗留差旅内容，P4 替换）
│   ├── intent-seed.yml   # L2 向量种子语料
│   └── db/schema.sql     # 建表脚本
├── frontend/             # React 19 + Vite（C 端用户端，8095）
├── admin/                # React 19 + Vite + zustand（B 端管理端，8097）
└── deploy/               # NAS 部署配置 + 演示数据 + 乱码修复脚本
```

### 8.2 构建注意（血泪坑）

1. **JDK 版本**：Homebrew OpenJDK 27 下 Lombok 报
   `ExceptionInInitializerError: com.sun.tools.javac.tree.EndPosTable`，
   必须 `JAVA_HOME` 指向 JDK 21
2. **打包后验证 jar**：增量构建可能不拾取最新编辑，
   `unzip -p target/ecom-agent-1.0.0-SNAPSHOT.jar BOOT-INF/classes/.../Xxx.class | strings | grep 方法名`
3. 测试：`mvn test`（意图识别 4 个测试类 16 用例，位于 `com.gogo.travel` 测试包下——
   历史命名，未迁移）

### 8.3 可观测性

- 全链路日志：`com.ecom.cs: INFO`（排障改 `DEBUG`，含每层意图命中/耗时/工具调用参数）
- 在线日志：前端 `/logs/ecom.log`（nginx 反代）
- SSE `progress` 事件驱动前端「处理面板」：改写 → 意图识别 → 子 Agent → 工具调用逐步可见

## 九、路线图

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 | 骨架搭建 + NAS 部署 | ✅ |
| P1 | 基础设施（14 Hook / Pipeline / SSE / 认证）验证 | ✅ |
| P2 | Agent 电商化：9 提示词重写、意图体系、演示数据、人设修复 | ✅ |
| P3 | 商品搜索/选购方案（重接 ItineraryPlanAgent）、物流时间轴、转人工 | 🚧 |
| P4 | RAG 知识库替换为电商语料（商品/售后政策/SOP） | 📋 |
| P5 | 前端组件电商化收尾（TravelResultCard 等） | 📋 |
| P6 | 端到端联调、Token 优化、接入真实电商 MCP | 📋 |

## 十、已知限制

- **单轮响应慢**：本地 27B 模型一次完整对话（改写+识别+主 Agent+子 Agent）可达 1-3 分钟，
  属硬件约束；换小模型或上云端可解
- **RAG 内容仍是差旅语料**：`dataset/` 下三份文档为模板项目遗产，启动时正常加载，
  电商化替换排 P4
- **长期记忆读回偶发为空**：百炼 LTM 索引延迟；已用「DB 权威源 + LTM 双写」缓解
- **外部 MCP 依赖**：天气/签证 MCP 需要对应 API Key 与网络，未配置时优雅降级不阻断启动

## 十一、致谢

架构原型来自学习项目「智能差旅多 Agent」（gogo-agent），在其基础上完成电商化改造：
意图体系、提示词、演示数据、人设均为重写；横切基础设施（Pipeline/Hook/SSE/中断广播）保留。

---

© 2026 章文杰 · 技术演示项目

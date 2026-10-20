# ecom-agent NAS Docker 部署说明

本文档描述 ecom-agent 在 NAS（fnOS）上的 Docker 部署拓扑与运维流程。
示例环境为作者的家用 NAS（192.168.3.3），IP/账号按你的环境替换即可。

拓扑：NAS 上跑两个容器（均 **host 网络**）；MySQL/Redis 在 NAS 本机；Ollama 在 192.168.3.15。

| 容器 | 镜像 | 端口（host） | 说明 |
|---|---|---|---|
| llmentor-ecom-backend | eclipse-temurin:21-jre | **8094** | host 网络，挂载 jar(ro) + /app/logs(rw) |
| llmentor-ecom-frontend | nginx:alpine | **8095** | 挂载 frontend dist 与 nginx.conf，含 /logs/ 反代 |

前端构建时注入 `VITE_API_BASE=http://192.168.3.3:8094`，浏览器直连后端（后端已开 @CrossOrigin）。

访问入口：http://192.168.3.3:8095
后端 API：http://192.168.3.3:8094
在线日志：http://192.168.3.3:8095/logs/ecom.log

> 端口说明：8090/8092 在作者的 NAS 上已被其他 host 网络服务占用（连接挂起而非 HTTP 拒绝），
> 最终采用 **8094（后端）/ 8095（前端）**。换环境部署前先用
> `curl --connect-timeout 2 -o /dev/null -w %{http_code}; echo $?` 探测：
> exit 7=端口空闲，exit 28/56=被占用。

## 数据库

- 库名 `ecom_cs`（MySQL，应用账号/密码通过环境变量注入，勿写死）
- 表结构：`src/main/resources/db/schema.sql`（11 张表：user_account / user_profile /
  travel_order / approval_record / booking_record / ecom_product / user_preference /
  chat_conversation / chat_message / user_api_key / travel_policy_rule）
- 种子账号：admin(ADMIN) / test / alice / bob / charlie / david，密码 123456（schema.sql 内置）
- 演示数据（test 账号的工单/订单）：`demo_data_test_account.sql`，见下文灌库命令

## 关键环境变量（backend 容器）

```text
SERVER_PORT=8094
OLLAMA_BASE_URL=http://192.168.3.15:11434
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/ecom_cs?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
MYSQL_USER=your_db_user
MYSQL_PASSWORD=your_db_password
SPRING_DATA_REDIS_HOST=127.0.0.1
MINIO_ENDPOINT=http://127.0.0.1:9000
MINIO_ACCESS_KEY=your_minio_access_key
MINIO_SECRET_KEY=your_minio_secret_key
MINIO_BUCKET=ecom-cs
LOGGING_FILE_NAME=/app/logs/ecom.log
TZ=Asia/Shanghai
```

> **为什么 host 网络 + 127.0.0.1**：bridge 网络下容器访问宿主机 LAN IP 会被 fnOS 防火墙
> hairpin 阻断；host 网络下本机访问自身 LAN IP 也被 REJECT，同机服务一律 127.0.0.1。
> Ollama 在另一台机器（192.168.3.15），走 LAN IP 可达，不受此限。

## 创建容器（SSH 直连，等价 compose）

```bash
ssh chacone@192.168.3.3 "docker rm -f llmentor-ecom-backend 2>/dev/null; \
docker run -d --name llmentor-ecom-backend --restart always --network host \
  -v /vol1/llmmentor/ecom/backend/ecom-agent-1.0.0-SNAPSHOT.jar:/app/app.jar:ro \
  -v /vol1/llmmentor/ecom/backend/logs:/app/logs \
  -e SERVER_PORT=8094 \
  -e OLLAMA_BASE_URL=http://192.168.3.15:11434 \
  -e SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/ecom_cs?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false' \
  -e MYSQL_USER=your_db_user \
  -e MYSQL_PASSWORD=your_db_password \
  -e SPRING_DATA_REDIS_HOST=127.0.0.1 \
  -e MINIO_ENDPOINT=http://127.0.0.1:9000 \
  -e MINIO_BUCKET=ecom-cs \
  -e LOGGING_FILE_NAME=/app/logs/ecom.log \
  -e TZ=Asia/Shanghai \
  eclipse-temurin:21-jre \
  java -Xms512m -Xmx1536m -jar /app/app.jar"
```

前端容器：

```bash
ssh chacone@192.168.3.3 "docker rm -f llmentor-ecom-frontend 2>/dev/null; \
docker run -d --name llmentor-ecom-frontend --restart always -p 8095:80 \
  -v /vol1/llmmentor/ecom/frontend:/usr/share/nginx/html:ro \
  -v /vol1/llmmentor/ecom/frontend/nginx.conf:/etc/nginx/conf.d/default.conf:ro \
  -v /vol1/llmmentor/ecom/backend/logs:/logs:ro \
  nginx:alpine"
```

## 更新流程（jar 是挂载的，重启即生效）

```bash
# 1. 打包（JAVA_HOME 必须指到 JDK 21；homebrew openjdk 27 会因 Lombok 不兼容报 EndPosTable 错）
export JAVA_HOME=/Users/zhangwenjie/Library/Java/JavaVirtualMachines/corretto-21.0.12.1-1/Contents/Home
mvn package -DskipTests

# 2. 上传 jar（注意是容器挂载的文件名，不是 app.jar）
scp target/ecom-agent-1.0.0-SNAPSHOT.jar chacone@192.168.3.3:/vol1/llmmentor/ecom/backend/

# 3. 重启 + 验证（启动约 35-40s，含知识库构建；就绪前 curl 会拿到代理层 502）
ssh chacone@192.168.3.3 "docker restart llmentor-ecom-backend"
curl http://192.168.3.3:8094/api/auth/login -X POST -H 'Content-Type: application/json' \
  -d '{"username":"test","password":"123456"}'
```

前端更新：`VITE_API_BASE=http://192.168.3.3:8094 npm run build` → scp `dist/*` 到
`/vol1/llmmentor/ecom/frontend/` → 必要时修正权限（nginx 容器只读挂载，文件需可读）：

```bash
ssh chacone@192.168.3.3 "docker run --rm -v /vol1/llmmentor/ecom/frontend:/mnt eclipse-temurin:21-jre chmod -R 755 /mnt"
```

## 演示数据灌库（test 账号）

```bash
scp demo_data_test_account.sql chacone@192.168.3.3:/vol1/llmmentor/ecom/
ssh chacone@192.168.3.3 "docker exec -i \$(docker ps -q -f name=mysql) mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" \
  ecom_cs --default-character-set=utf8mb4 < /vol1/llmmentor/ecom/demo_data_test_account.sql"
```

数据内容：user_profile + 3 条服务工单（travel_order：处理中/已通过/已撤回）+
3 条处理记录（approval_record）+ 4 条商品订单（booking_record，biz_type=GOODS，
含顺丰在途物流 detail）。
**user_id 用 `u_test`**（= user_account.user_id 列，即 sa-token loginId），不是 id 列。

## 已知注意

- fat jar 资源读取必须用 `getInputStream()`；docx 类 reader 需先落临时文件
- agentscope OllamaChatModel 不容忍 null options（`stream(msgs,null,null)` 会 NPE）
- MCP 容错降级：容器无 npx 时，stdio 方式的外部 MCP 注册失败会被跳过，不阻断启动
- 首次对话构建知识库约需 15 秒（bge-m3 向量化）；27B 模型冷加载约 9 秒
- 登录认证用裸 token（`Authorization: <token>`，无 Bearer 前缀，Sa-Token 风格）
- Ollama 单轮生成 1-2 分钟，一次完整聊天可达 3 分钟；前端 SSE 无总超时（30 分钟），
  但反代/浏览器长连接需保持

## 种子数据乱码修复（fix_mojibake.py）

若灌库时客户端连接字符集为 latin1，全库中文会双重编码（显示为乱码，
可用 `HEX(title)` 验证：正确中文「北」应为 `E58C97`，异常值为 `C3A5C592`）。
修复脚本通过 HEX() 纯 ASCII 传输读取、本地还原 CP1252→UTF-8、UNHEX() 写回。
后续如重新灌库，务必确认客户端字符集为 utf8mb4（mysql 加
`--default-character-set=utf8mb4`）。

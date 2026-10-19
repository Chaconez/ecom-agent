# ecom-agent 前端

电商智能客服的 Web 前端：React 19 + TypeScript + Vite + Ant Design 6。

## 开发

```bash
npm ci
VITE_API_BASE=http://127.0.0.1:8094 npm run dev
```

`VITE_API_BASE` 指向后端地址（本地开发指向 `http://127.0.0.1:8094`，
部署时改为实际 NAS/服务器地址）。

## 构建

```bash
VITE_API_BASE=http://192.168.3.3:8094 npm run build
```

产物在 `dist/`，可交给任意静态服务器（本项目用 nginx，配置见 `../deploy/nginx.conf`）。

## 页面构成

| 组件 | 说明 |
|---|---|
| `LoginPage` | 登录（admin / test，密码 123456） |
| `ChatWindow` + `AgentMessageBlock` | 对话主界面：SSE 流式渲染 Markdown 回复 |
| `ProgressCard` / `ProcessingPanel` | 处理过程可视化（改写 → 意图识别 → 子 Agent → 工具调用） |
| `UserInteractionCard` | 追问交互卡片（select / confirm / form） |
| `MyOrdersPage` | 我的订单（状态筛选 + 物流/售后快捷动作） |
| `AdminApprovalPage` | 客服工作台（工单处理记录） |
| `PreferencePage` | 偏好设置（4 大类 15 字段） |
| `RecommendPage` | 为你推荐（个性化商品卡片） |

## 状态与数据

- 全局状态：Zustand（`src/store/`，含 uiStore 当前页签）
- API 封装：`src/api/`（axios，token 存 localStorage）
- 主题：Ant Design Token 定制（`src/App.css` / `index.css`）

## lint 与类型检查

```bash
npm run lint
```

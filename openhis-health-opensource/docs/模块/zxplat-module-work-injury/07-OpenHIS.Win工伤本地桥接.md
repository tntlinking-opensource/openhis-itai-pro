# OpenHIS.Win 工伤本地桥接（WORK_INJURY · P0 真源）

> **文档性质**：**JLSiInterface.dll** 在业务窗口 PC 上的实现真源 — 替代 legacy `unwisegs` / NEUSI 插件 DLL 出站  
> **工程**：`openhis-pro-win/`（与读卡 `OpenHIS.Win.Chs`、银联 `OpenHIS.Win.UnionPay` 同托盘）  
> **Whale 编排**：[01-接入架构决策.md](./01-接入架构决策.md) · [05-用户操作说明.md](./05-用户操作说明.md) · [14-院方确认决策.md](./14-院方确认决策.md)  
> **院点（H22010402403）**：[吉林朝阳中医院/00-上线前Checklist.md](../吉林朝阳中医院/00-上线前Checklist.md) §I  
> **版本**：v1.0 | **日期**：2026-07-01 | **实施状态**：待开发

---

## 1. 定位

| 项 | 说明 |
| --- | --- |
| **是什么** | 各 **业务窗口本机** `OpenHIS.Win.exe` 内的工伤子模块，通过 **DllImport 调 `JLSiInterface.dll`**（`Si_INIT` / `Si_Busi`），对外暴露 **JSON HTTP** |
| **替代谁** | legacy **NEUSI / unwisegs** 工伤插件（HIS 进程内嵌 DLL）；**不**再单独部署 `work-injury-bridge.exe` |
| **不做什么** | 不编排 2201→2207、不校验 `qualification_id`、不写 `yb_settlement_record`；只做 **签到会话 + DLL 透传** |
| **为何不放 Whale 8081** | 应用服务器 **可为 Linux**；工伤专线 DLL **必须在 Windows x86 本机**（与银联通道 C 同理由） |

### 1.1 与银联通道 C 对照

| 维度 | 银联 `OpenHIS.Win.UnionPay` | 工伤 `OpenHIS.Win.WorkInjury` |
| --- | --- | --- |
| 调用方 | web-ele → `127.0.0.1:5000` | **同左** |
| Whale 8081 | 编排 + 落库 | **同左** |
| native | `native/unionpay/` | `native/workinjury/` |
| 会话 | 无（定长 DLL 自带） | **9001 `sign_no` 进程内缓存** |

---

## 2. 端到端流程（P0 · 客户端 invoke）

```text
1. web-ele 发起工伤业务（挂号 9102 / 收费 2206 等）
2. POST health `/api/app/workInjuryInvoke/prepareInvoke`（→ Extension → FacadeImpl）
     ← 返回 sessionId、stepToken、infno、input、fixmedinsCode
3. web-ele POST http://127.0.0.1:5000/api/work-injury/invoke
     → OpenHIS.Win.WorkInjury
         · ensureSign（9001，本 Win 实例）
         · 组装 inputdata（infno/msgid/sign_no/recer_sys_code…）
         · Si_Busi → 中心 172.29.0.157:9021（专线由 DLL/环境决定）
4. web-ele POST health `/api/app/workInjuryInvoke/submitInvokeResult`
     · 携带 sessionId、stepToken + DLL 出参 JSON
5. Whale 更新 anchor / pre_settle、写 yb_invoke_log；必要时返回下一步 prepareInvoke
```

**多步链（2201→2203→(2205?)→2204→2206→2207）**：Whale 逐步 `prepare` → 前端逐步调 Win → `submit`；**禁止**前端跳过 Whale 直连多 infno 落库。

**半成功**：Win `invoke` 成功、Whale `submit` 失败 → invoke session 保持 ACTIVE，**重试 submit**（见 [11](./11-客户端invoke会话-详细设计.md) §6）。

---

## 3. 工程结构（目标）

```text
openhis-pro-win/
  OpenHIS.Win.WorkInjury/           ← 新建（对齐 OpenHIS.Win.UnionPay）
    JlSiInterfaceNative.cs           DllImport Si_INIT / Si_Busi
    WorkInjurySignSession.cs         9001/9002 + sign_no 缓存（按 fixmedinsCode）
    WorkInjuryMessageBuilder.cs      msgid、inputdata 外壳
    WorkInjuryDispatcher.cs          health / sign-in / invoke / sign-out
    WorkInjurySettings.cs            native 目录、dll 路径、recer_sys_code
    WorkInjuryLogger.cs
  OpenHIS.Win/
    Http/HttpServer.cs               注册 /api/work-injury/*
  native/workinjury/                 发布时拷贝（verify-native.ps1 校验）
    JLSiInterface.dll
    （院点依赖：config、专线环境文件 — 自老插件目录迁移）
```

| 项目 | 目标框架 | 平台 |
| --- | --- | --- |
| OpenHIS.Win.WorkInjury | .NET Framework 4.7.2 | **x86**（与 DLL 位宽一致） |

---

## 4. HTTP API（英文 · JSON）

基址：`http://127.0.0.1:5000`（与 [openhis-pro-win/README.md](../../openhis-pro-win/README.md) 一致；`PluginSettings.Http旧命名（已废止） 可配）。

### 4.1 `GET /api/work-injury/health`

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `enabled` | boolean | Win 侧工伤模块是否启用（读 `WorkInjurySettings.Enabled`） |
| `nativeReady` | boolean | `JLSiInterface.dll` 存在且 `Si_INIT`=0 |
| `signedIn` | boolean | 当前 `fixmedinsCode` 是否已有有效 `sign_no` |
| `fixmedinsCode` | string | 配置默认定点编码 |

web-ele / Whale capability 探测用；`enabled=false` 时其他路由返回 **404**。

### 4.2 `POST /api/work-injury/sign-in`

显式 9001（`invoke` 内也会 lazy sign-in；本接口供运维/日初）。

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `fixmedinsCode` | Y | 出站 **`JL2201992002`**（见 [14](./14-院方确认决策.md) Q2） |
| `operatorNo` | Y | 操作员编号（写入 9001 报文） |
| `operatorName` | N | 操作员姓名 |

**Response**：`success`、`signNo`、`errMsg`

### 4.3 `POST /api/work-injury/invoke`（核心）

Whale `prepare-invoke` 下发业务体；Win 负责 **公共头 + 签到 + Si_Busi**。

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `stepToken` | string | Y | Whale prepare 下发 |
| `infno` | string | Y | 如 `9102`、`2206` |
| `fixmedinsCode` | string | Y | 协议机构编号 |
| `operatorNo` | string | Y | 当前操作员 |
| `operatorName` | string | N | |
| `recerSysCode` | string | N | 默认 **`LNYTH`** |
| `infver` | string | N | 默认 `V1.0` |
| `input` | object | Y | **业务体**（不含 sign_no/msgid；Win 注入） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | boolean | DLL 调用是否成功（含平台 errflag 语义） |
| `infcode` | string | 平台返回码 |
| `errMsg` | string | 可读错误 |
| `output` | object | `Si_Busi` 出参 JSON（原样解析） |
| `signNo` | string | 本次使用的 sign_no（便于 Whale 日志） |
| `msgid` | string | 本次 msgid（对账） |
| `rawInput` | string | 可选，调试：完整 inputdata 字符串 |
| `rawOutput` | string | 可选，调试：DLL 原始出参 |

**9001 特例**：`infno=9001` 时 Win 走 sign-in 逻辑，不重复签到。

### 4.4 `POST /api/work-injury/sign-out`

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `fixmedinsCode` | Y | |
| `operatorNo` | Y | |

对应 9002；托盘退出或日终可调用。

### 4.5 能力探测（汇总）

`/api/hello` 或 `/api/data/capabilities` 增加：

```json
{
  "workInjury": true,
  "workInjuryNativeReady": true
}
```

web-ele 无 `workInjury` 时不展示工伤费用性质相关本地能力提示。

---

## 5. 配置分工

| 层 | 位置 | 内容 |
| --- | --- | --- |
| **DLL 运行时** | `native/workinjury/` | `JLSiInterface.dll`、院点专线配置（**院方维护**） |
| **Win 模块** | `OpenHIS.Win.WorkInjury/WorkInjurySettings.json` 或 `plugin.ini` | `Enabled`、`DefaultFixmedinsCode`、`RecerSysCode`、`DllFileName` |
| **Whale 业务** | `chs-contract.properties` | `scheme=WORK_INJURY`、`fixmedins_code`、`recer_sys_code`（**无 bridge_url**） |
| **L0 开关** | Whale `application.yml` | `zxplat.modules.work-injury.enabled` |
| **Win L0 开关** | `WorkInjurySettings.Enabled` | 未启用 → 404；与 Whale 开关 **独立**（Win 可先装 DLL 包、Whale 后开模块） |

朝阳院迁移：自 `project-H22010402403/老工伤插件/` 拷贝 DLL 及环境文件至 `native/workinjury/`。

---

## 6. Whale 侧配合（health + chs FacadeImpl）

| 组件 | P0 行为 |
| --- | --- |
| **health** `WorkInjuryInvokeAppService#prepareInvoke` | 经 Extension 调 FacadeImpl；组 `input`、生成 `stepToken` |
| **health** `WorkInjuryInvokeAppService#submitInvokeResult` | 经 Extension；解析出参、写 `yb_invoke_log`、推进 session |
| **chs** `WorkInjuryInvokeFacadeImpl` | 实际编排与 DomainService 持久化 |
| `InsuranceGatewayClient`（WORK_INJURY） | **服务端不 HTTP 调 DLL**；NoOp，防误用 |
| `WorkInjurySignSessionService` | **废弃于 JVM**；`sign_no` 真源在 **OpenHIS.Win 进程** |
| Extension 编排 | 2201→2207 多步由 health invoke API + frontend Win 循环驱动 |

**禁止**：Whale `health-application` / `zxplat-module-chs-jilin/chs-jilin-application` **LoadLibrary** 或配置 `bridge_url` 指向远端 PC。

### 6.1 前端约定（web-ele）

```typescript
// 伪代码 — 与 unionpay consumer-scan 同模式
const step = await api.workInjuryInvoke.prepareInvoke({ sessionId, phase, ... });
const winResult = await fetch('http://127.0.0.1:5000/api/work-injury/invoke', {
  method: 'POST',
  body: JSON.stringify({ stepToken: step.stepToken, infno: step.infno, input: step.input, ... }),
});
await api.workInjuryInvoke.submitInvokeResult({ sessionId: step.sessionId, stepToken: step.stepToken, ...winResult });
```

封装为 `invokeWorkInjuryPlugin(step)`，与现有 `invokeYbPlugin`（读卡）并列。

---

## 7. sign_no 与多窗口

| 规则 | 说明 |
| --- | --- |
| **一 Win 实例一 sign_no 域** | 每台业务 PC 的 OpenHIS.Win 独立 9001；NEUSI 日志「9002→9001 再业务」与此一致 |
| **禁止全院共享 sign_no** | Whale 不得缓存全局 sign_no 供多 PC 复用 |
| **三契约混窗** | 同一 Win 上省医保读卡（Chs）与工伤 invoke **并行**；工伤 sign_no **不与** 省医保 OAuth 混用 |
| **医生站 2301** | 该 PC **须安装 OpenHIS.Win**；否则只能走挂号员入口或院方集中 Windows 网关（二期可选，非 P0） |

---

## 8. 部署

| 项 | 要求 |
| --- | --- |
| 业务窗口 PC | Windows x86；**OpenHIS.Win.exe**（含 workinjury native） |
| 覆盖范围 | 挂号、收费、需 2301 的医生站（与 Checklist A-10 一致） |
| 自启动 | 托盘 + 开机启动（与读卡/打印/银联相同） |
| 网络 | 本机 → 工伤专线/中心（由 DLL 环境决定）；8081 服务器 **无需** 通达工伤专线 |
| 应用服务器 | **可为 Linux**；工伤 DLL **不在** 8081 服务器 |

---

## 9. 扩展组件隔离

| 开关 | Win 行为 | Whale 行为 |
| --- | --- | --- |
| Win `Enabled=false` | `/api/work-injury/*` → 404 | 可不展示工伤 |
| Whale `work-injury.enabled=false` | Win 仍可装 DLL（无害） | 无工伤 UI/API；NoOp Extension |
| 两者均 true + fin_contract 工伤行 | 全流程 | 全流程 |

见 [06-扩展组件与隔离策略.md](./06-扩展组件与隔离策略.md)。

---

## 10. 任务 ID

| ID | 任务 |
| --- | --- |
| WIN-WI-01 | 新建 `OpenHIS.Win.WorkInjury` 项目 |
| WIN-WI-02 | `JlSiInterfaceNative` + `Si_INIT` 健康检查 |
| WIN-WI-03 | `WorkInjurySignSession`（9001/9002） |
| WIN-WI-04 | `POST /api/work-injury/invoke` |
| WIN-WI-05 | `GET /api/work-injury/health` + capabilities 位 |
| WIN-WI-06 | `native/workinjury` + `verify-native.ps1` |
| WIN-WI-07 | 自 H22010402403 老插件迁移 DLL 验收 |
| WH-WI-01 | `WorkInjuryClientInvokeAppService` prepare/submit API |
| WH-WI-02 | web-ele `invokeWorkInjuryPlugin` + 9102/2206 联调 |
| WH-WI-03 | 移除/禁止 JVM `bridge_url` 直调路径 |

---

## 11. 验收要点

| # | 场景 | 期望 |
| --- | --- | --- |
| AC-WIN-WI-01 | 未启动 OpenHIS.Win | web-ele 提示安装本地客户端 |
| AC-WIN-WI-02 | `health.nativeReady=false` | 明确提示 DLL/专线未就绪 |
| AC-WIN-WI-03 | 9102 invoke 成功 → submit | Whale 写入 qualification 锚点 |
| AC-WIN-WI-04 | 无 qualification_id 点预结算 | Whale **阻断**，不调 Win 2206 |
| AC-WIN-WI-05 | 2206→2207 全链 | 每步经 prepare→Win→submit |
| AC-WIN-WI-06 | Win enabled=false | 404；省医保读卡/银联不受影响 |
| AC-WIN-WI-07 | Whale work-injury.enabled=false | 无工伤 UI；Win 路由可 404 |
| AC-WIN-WI-08 | 8081 在 Linux | 工伤仍可结算（浏览器调本机 Win） |

---

## 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-07-01 | 首版：并入 openhis-pro-win；客户端 invoke；废弃独立 bridge.exe |

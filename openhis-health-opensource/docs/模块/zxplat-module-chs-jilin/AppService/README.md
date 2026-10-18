# CHS · 应用服务（AS）文档索引

> **对齐 health**：切片规则与 `docs/领域/AppService/AS-*.md` 同源 — **按「页面边界」定 AS**，不是按 Vue 文件数或 Tab 数机械拆分。  
> **实现对照**：`zxplat-module-chs-jilin/java/com/openhis/chs/application/**/README.md`  
> **SDD**：「AppService-SDD/README.md」（类名、包结构、HTTP↔IF 映射）  
> **交互/API**： [02-交互说明.md](../02-交互说明.md) · 前端 `openhis-pro-ui/apps/web-ele/src/chs/api/`

---

## 切片规则（CHS 版）

| 规则 | 说明 |
|------|------|
| **页面边界** | 每份 AS 文档开头定义 **在内 / 在外**；对应 **一个** `Insurance*AppService`（或 `Insurance*AppService` 族内的唯一实现类） |
| **独立菜单页** | 通常 1 页 1 AS（可有多 public 方法，fat 正常） |
| **页内 Tab** | **筛选/查询子 Tab** → 同一 AS 内多方法；**独立业务闭环 Tab**（如护士站功能 Tab）→ 才拆 AS |
| **工作站** | 门诊/住院收银医保走 **Extension + bridge**，**不在**本目录 AS 内 |
| **HTTP** | `POST /api/app/{camelCaseService}/{method}`，`AppServiceControllerRegistrar` 自动注册 |
| **出站** | application 层仅 `*Invoker` 调 gateway；多步用 `support/*Workflow`；AppService 不直连 |

---

## AS 清单

| 编码 | 文档 | SDD | 前端路由/页面 | AppService（HTTP 前缀） | 平台 infno |
|------|------|-----|---------------|---------------------------|------------|
| **AS-CHS-CAT-001** | [国家目录管理](./AS-CHS-CAT-001-国家目录管理.md) | [SDD](../AppService-SDD/AS-CHS-CAT-001-国家目录管理-SDD.md) | `/chs/national-catalog` | `insuranceNationalCatalog` | 1312 |
| **AS-CHS-CAT-002** | [目录文件下载](./AS-CHS-CAT-002-目录文件下载.md) | [SDD](../AppService-SDD/AS-CHS-CAT-002-目录文件下载-SDD.md) | `/chs/catalog-download` | `insuranceCatalogDownload` | 130x、9102 |
| **AS-CHS-CAT-003** | [院内对码上传](./AS-CHS-CAT-003-院内对码上传.md) | [SDD](../AppService-SDD/AS-CHS-CAT-003-院内对码上传-SDD.md) | 对码维护页 | `insuranceCatalogMapping` | 3301、3302 |
| **AS-CHS-CAT-004** | [对码 Workbench](./AS-CHS-CAT-004-对码Workbench.md) | [SDD](../AppService-SDD/AS-CHS-CAT-004-对码Workbench-SDD.md) | `/chs/catalog-mapping-workbench` | `insuranceCatalogWorkbench` | 1317（本地待对码 + 平台已对照） |
| **AS-CHS-INV-001** | [进销存运维](./AS-CHS-INV-001-进销存运维.md) | [SDD](../AppService-SDD/AS-CHS-INV-001-进销存运维-SDD.md) | `/chs/inventory` | `insuranceInventory` | 3501–3513、3507 |
| **AS-CHS-REC-001** | [对账清算中心](./AS-CHS-REC-001-对账清算中心.md) | [SDD](../AppService-SDD/AS-CHS-REC-001-对账清算中心-SDD.md) | `/chs/reconciliation` | `insuranceReconciliation` | 3201–3209A、9101/9102 |
| **AS-CHS-MED-001** | [医保信息查询](./AS-CHS-MED-001-医保信息查询.md) | [SDD](../AppService-SDD/AS-CHS-MED-001-医保信息查询-SDD.md) | `/chs/medical-info-query` | `insuranceMedicalInfoQuery` | 5201–5206、5301 |
| **AS-CHS-AUD-001** | [事前提醒试调](./AS-CHS-AUD-001-事前提醒试调.md) | [SDD](../AppService-SDD/AS-CHS-AUD-001-事前提醒试调-SDD.md) | `/chs/detail-audit-try` | `insuranceDetailAuditTry` | 3101、3103 |
| **AS-CHS-AUD-002** | [事前提醒主路径门禁](./AS-CHS-AUD-002-事前提醒主路径门禁.md) | — | 嵌入对话框 | `insuranceDetailAuditGate` | 3101、3103 |
| **AS-CHS-AUD-003** | [事前提醒统计研讨](./AS-CHS-AUD-003-事前提醒统计研讨.md) | — | `/chs/detail-audit-stats` | `insuranceDetailAuditStats` | —（只读落库） |
| **AS-CHS-FND-001** | [人员读卡](./AS-CHS-FND-001-人员读卡.md) | [SDD](../AppService-SDD/AS-CHS-FND-001-人员读卡-SDD.md) | 嵌入读卡/挂号 | `insurancePerson` | 1101 |
| **AS-CHS-FND-002** | [医保签到](./AS-CHS-FND-002-医保签到.md) | [SDD](../AppService-SDD/AS-CHS-FND-002-医保签到-SDD.md) | 签到插件/运维 | `insuranceSign` | 9001 |
| **AS-CHS-FND-003** | [平台字典同步](./AS-CHS-FND-003-平台字典同步.md) | [SDD](../AppService-SDD/AS-CHS-FND-003-平台字典同步-SDD.md) | `/chs/platform-dict` | `insurancePlatformDict` | 1901 |

**兼容入口**：`insuranceSession/signIn` → 委托 **AS-CHS-FND-002**（deprecated 路由，不单独 AS）。

**不在本目录（bridge Extension）**：2201–2601 结算、3505/3506 发药异步上报、4401 病案等 — 见 [13-实现状态与文档对照.md](../13-实现状态与文档对照.md)。

---

## 与 health 对照

| health 模式 | CHS 对应 |
|-------------|----------|
| 药库进货单 1 AS、14 方法 | **AS-CHS-INV-001** 进销存运维台 1 AS、多 Tab 多方法 |
| 护士站 1 Vue → 多 AS | CHS 进销存/对账 **不拆 Tab**（Tab 为同一运维边界内子能力） |
| 52xx 查询 1 页多 infno | **AS-CHS-MED-001** |
| AS 文档 + SDD | AS 契约 + [AppService-SDD/](../AppService-SDD/README.md) 实现映射 |

---

## 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v0.1 | 2026-07-11 | 初稿：索引 + 11 份 AS 边界 |
| v0.2 | 2026-07-11 | 补 SDD 索引与交叉链接 |
| v0.3 | 2026-07-11 | P1–P3 落地：包路径、reconciliation request/response |
| v0.4 | 2026-07-11 | P7：取消强制 Orchestrator 层；application 用 `support/*Workflow` 或 AppService 直调 Invoker |
| v0.5 | 2026-07-11 | P8：去掉 `orchestration/` 目录段 → 页内 `invoker/` + `support/` |
| v0.7 | 2026-07-24 | 增 **AUD-002** 门禁、**AUD-003** 统计研讨 |
| v0.6 | 2026-07-23 | 增 **AS-CHS-AUD-001** 事前提醒试调（3101/3103） |

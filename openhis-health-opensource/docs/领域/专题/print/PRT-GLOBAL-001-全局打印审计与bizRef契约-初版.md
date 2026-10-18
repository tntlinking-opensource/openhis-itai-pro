# PRT-GLOBAL-001 · 全局打印审计与 bizRef 契约（初版）

> **状态**：草案 v0.2 · **日期**：2026-08-12  
> **关联**：`DM-PRT-002` PrintLog、`AS-BAS-PRT-001` 打印运行时与绑定时、`PRINT_SCENARIO`（前端 `apps/web-ele/src/utils/print/constants/print-scenario.ts`）  
> **目的**：在已有 `preparePrint → hiprint → confirmPrint → prt_print_event_log` 闭环上，统一业务引用（bizRef），支持「是否已打印」回查与全局追溯。  
> **非目标（本版）**：不改模板画布、不扩 EMR 预览引擎、不替换业务侧独立 `print_count`（如有）。

### 范围收窄（2026-08-12 产品口径）

| 范围 | 要求 |
| --- | --- |
| **非护士站** 其余打印 | **只做业务↔打印关联**（补齐/规范 `bizRef` 写入审计）；**页面暂不回显**「已打印」 |
| **护士站 · 医嘱执行摆药单**（`order_dispense_sheet` / `ip-orderDispenseSheet-print`） | **唯一需要页面回显**已打印/未打印的单据 |
| 明确不做（本迭代） | 不改发药/计费/医嘱执行主状态机；不给收费/处方/库存等列表加「已打印」列 |

> 影响判断：**不阻断主业务流程**。关联是打印成功后的附加写日志字段；回显仅挂在打印中心（及同封装抽屉）待打列表，失败可降级为「全部未打印」。

---

## 1. 结论摘要

1. **写审计已基本全局覆盖**：凡走前端 `#/utils/print` 的 `print()`，成功/失败都会 `confirmPrint` 落 `prt_print_event_log`。
2. **真正缺口在「可回查」**：多数入口虽有 `bizRef`，但键粒度过粗或未约定；读侧仅有管理台按 `busNo`/日期分页，**不能按业务主键批量判断是否已打印**。
3. **旁路未进审计**：体温单浏览器打印、EMR PDF `window.print`、设计器预览直打。
4. **本迭代落地（收窄后）**：非护士站只补 bizRef 关联；仅护士站摆药单做列表回显 + 必要读接口；其余 UI 回显延后。

---

## 2. 现状

### 2.1 写路径（已有）

```text
业务页 / 封装
  → print({ scenarioCode, source|printData, bizRef, ... })
  → preparePrintApi(scenarioCode)          // 取模板 + traceId
  → hiprint 渲染/静默打
  → confirmPrintApi({ traceId, busNo, status, bizRef, ... })
  → PrintJobAppService.confirmPrint
  → PrtPrintLogWriter.appendLog            // INSERT prt_print_event_log
```

| 字段 | 用途 |
| --- | --- |
| `bus_no` | 模板业务号 |
| `binding_code` | 场景码（confirm 时写入，多来自 scenarioCode） |
| `status` | `SUCCESS` / `FAILED` |
| `biz_ref_json` | 业务引用 JSON（**本规范约束对象**） |
| `trace_id` | prepare↔confirm 闭环 |

### 2.2 读路径（不足）

| 能力 | 现状 |
| --- | --- |
| 管理台打印日志列表 | `PrintLogAppService.queryPage`：租户 + busNo + status + 日期 |
| 按 biz 主键「打过没」 | **无** |
| 按 scenario / binding_code 筛 | **无稳定查询面**（binding 有落库，但 App 查询未暴露） |
| 索引 | `tenant+created_at`、`tenant+bus_no+created_at`、`trace_id`；**无 biz_ref 索引** |

### 2.3 前端场景清单规模

- 正式 `PRINT_SCENARIO`：**34** 个场景码  
- 库存等多单据共用 3 个场景（`inv-movement` / `inv-stocktake` / `inv-return`），业务入口约十几处  
- 住院护士站打印中心已接线单据：除体温单外均可走 `print()`

---

## 3. 目标与原则

### 3.1 目标

| 目标 | 说明 |
| --- | --- |
| G1 全局可追溯 | 任意正式打印可在审计中查到操作人、时间、场景、业务键 |
| G2 可回查已打印 | 业务列表可批量判断「本业务键是否有 SUCCESS」 |
| G3 契约稳定 | 同场景多入口（打印中心 / 抽屉 / 补打）写入同一 bizRef 形状 |
| G4 旁路收敛 | 正式打印禁止绕过 `confirmPrint` |

### 3.2 原则

1. **审计 append-only**：不 UPDATE 历史行；「已打印」= 存在至少一条 `status=SUCCESS` 且 biz 键命中。  
2. **bizRef 只放稳定业务主键**：禁止仅放展示文案、禁止仅 `rowCount`。  
3. **合打一次、多键一次写入**：一张多人合打单，`bizRef` 用数组承载全部业务键（如 `listRowKeys[]`）。  
4. **预览默认不写审计**（或写但不得计入已打印）；正式打必须写。  
5. **业务 `print_count` 可选**：需要高频列表时可由 confirm 成功后异步回写，但回查真源仍是审计（或同步维护的投影表）。

### 3.3 建议 bizRef 公共字段

所有场景建议至少包含：

```json
{
  "scenarioCode": "ip-orderDispenseSheet-print",
  "primaryKeys": ["..."],
  "...场景专有字段"
}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `scenarioCode` | 建议 | 与 `PRINT_SCENARIO` / binding 对齐，便于读侧过滤 |
| 场景主键 | 必填 | 见 §4；合打用数组 |
| `queryDate` / `dateFrom`/`dateTo` | 按场景 | 按日单据必填 |
| `encounterId` | 按场景 | 患者维单据建议带上 |

> 过渡期：允许缺 `scenarioCode`（可用日志 `binding_code` 回落），但新改入口必须带。

---

## 4. 场景 → 推荐 bizRef 主键表

> **粒度**：回查「已打印」时的最小业务单元。  
> **现状**：`OK` = 已有可用主键；`弱` = 有 bizRef 但不够回查；`旁路` = 未走 PRT。

### 4.1 门诊收费 / 挂号 / 日结

| scenarioCode | 中文 | 推荐主键 | 现状 | 主要入口 |
| --- | --- | --- | --- | --- |
| `op-charge-print` | 门诊收费小票 | `paymentId`（辅：`encounterId`） | OK | 收费页自动/补打 |
| `op-reg-print` | 门诊挂号小票 | `encounterId`（辅：`paymentId`） | OK | 挂号确认、历史补打 |
| `op-dailySettlement-print` | 费用日结单 | `settlementId` | OK | 门诊/住院日结、收银审批 |
| `ip-dailySettlement-patient-print` | 住院日结患者分项 | `settlementId` / 预览窗 | OK | 住院日结患者分项（与合计分别打） |

### 4.2 门诊医生站

| scenarioCode | 中文 | 推荐主键 | 现状 | 主要入口 |
| --- | --- | --- | --- | --- |
| `op-rx-print` | 门诊西药/中成药处方 | `prescriptionNo` + `encounterId` | OK | 医生站、处方笺弹窗、发药、护士站 |
| `op-tcm-rx-print` | 门诊中药处方 | 同上 | OK | 同上 |
| `examination-print` | 检查申请单 | `serviceRequestId[]`（辅：`encounterId`） | OK 偏弱需确认数组 | 申请单工作区/弹窗 |
| `test-print` | 检验申请单 | 同上 | 同上 | 同上 |

### 4.3 门诊护士站

| scenarioCode | 中文 | 推荐主键 | 现状 | 主要入口 |
| --- | --- | --- | --- | --- |
| `op-treatment-print` | 处置单 | `encounterId`（可选执行日） | 弱 | 门诊护士站 |
| `op-infusionLabel-print` | 输液瓶签 | `medicationRequestIds[]` + `groupId` + `dayIndex`/`timeIndex` | OK | 门诊护士站 |
| `op-specimenBarcode-print` | 采血贴 | `serviceRequestId` + `encounterId` | OK | 门诊护士站、住院打印中心复用 |

### 4.4 住院收费

| scenarioCode | 中文 | 推荐主键 | 现状 | 主要入口 |
| --- | --- | --- | --- | --- |
| `ip-prepayReceipt-print` | 预交金收据 | `paymentId` / 预交金单号 + `encounterId` | OK | 住院预交金 |

### 4.5 住院护士站 · 护理与标识

| scenarioCode | 中文 | 推荐主键 | 现状 | 主要入口 |
| --- | --- | --- | --- | --- |
| `ip-nursingRecord-print` | 护理记录单 | `encounterId` + `dateFrom`/`dateTo` | 弱 | 打印中心、护理记录页 |
| `ip-patrolCard-print` | 输液巡视卡 | `encounterId` + `queryDate` | 弱 | 打印中心 |
| `ip-wristband-print` | 腕带 | `encounterId` | 弱 | 打印中心、入院分床 |
| `ip-bedCard-print` | 床头卡 | `encounterId` | 弱 | 打印中心、入院分床 |
| `ip-oralMedLabel-print` | 口服药贴 | `orderId`/`listRowKey` + `encounterId` | 弱~OK | 打印中心 |
| `ip-infusionLabel-print` | 住院瓶签 | 同门诊瓶签结构 | OK | 打印中心、医嘱执行 |

### 4.6 住院护士站 · 医嘱与费用

| scenarioCode | 中文 | 推荐主键 | 现状 | 主要入口 |
| --- | --- | --- | --- | --- |
| `ip-longOrder-print` | 长期医嘱 | `encounterId` + 行键集合（或打印模式快照） | 弱 | 打印中心 |
| `ip-tempOrder-print` | 临时医嘱 | 同上 | 弱 | 打印中心 |
| `ip-feeList-print` | 费用清单 | `encounterId` + `dateFrom`/`dateTo` | 弱 | 打印中心、费用明细 |
| `ip-feeDetail-print` | 费用明细单 | 同上 | 弱 | 同上 |
| `ip-examApply-print` | 住院检查单 | `serviceRequestId` + `encounterId` | OK | 打印中心、医嘱执行 |
| `ip-labApply-print` | 住院检验单 | 同上 | OK | 同上 |

### 4.7 住院护士站 · 领药 / 摆药 / 用药单（本轮关注）

| scenarioCode | 中文 | 推荐主键 | 现状 | 主要入口 |
| --- | --- | --- | --- | --- |
| `ip-dispenseSummary-print` | 汇总领药单 | `summaryNo` | OK | 打印中心、汇总领药 |
| `ip-dispenseSheet-print` | 批次摆药单 | `summaryNo` | OK | 同上 |
| `ip-orderDispenseSheet-print` | 医嘱执行摆药单 | **`listRowKeys[]`** + `queryDate`（辅：`encounterIds[]`） | **弱**（仅 queryDate+rowCount） | 打印中心、医嘱执行抽屉 |
| `ip-oralMedSheet-print` | 患者用药单 | 同上 | **弱** | 打印中心 |

合打约定示例：

```json
{
  "scenarioCode": "ip-orderDispenseSheet-print",
  "queryDate": "2026-08-12",
  "listRowKeys": ["enc-1-ord-a", "enc-1-ord-b", "enc-2-ord-c"],
  "encounterIds": ["enc-1", "enc-2"]
}
```

回查：任一 `listRowKey` 出现在任意 SUCCESS 日志的 `listRowKeys` 中 → 该行「已打印」。

### 4.8 药房日结 / 财务 / 体检 / 库存

| scenarioCode | 中文 | 推荐主键 | 现状 | 主要入口 |
| --- | --- | --- | --- | --- |
| `pharmacy-dailySettlement-print` | 药房日结单 | `settlementId` | OK | 门诊/住院药房日结、审批 |
| `fin-price-adjustment-print` | 调价单 | `busNo` | OK | 调价单主窗体 |
| `peis-report-print` | 体检总检报告 | `encounterId`（辅：`diagnosticReportId`） | OK；另有业务 confirm | 体检打印/合打 |
| `inv-movement-print` | 库存计价流转单 | `busNo` + `docKind` | OK | 进货/出库/调拨/请领等 |
| `inv-stocktake-print` | 盘点盈亏单 | `busNo` + `docKind` + `variant` | OK | 盘点 |
| `inv-return-print` | 库存退货单 | `busNo` + `docKind` | OK | 退货类单据 |

### 4.9 未进 PRT / 旁路

| 能力 | 现状 | 处理建议 |
| --- | --- | --- |
| 体温单 | `window.print`；打印中心未接线 | 模板化后走 `print()`；或明确「不做审计」 |
| EMR PDF 预览/打印 | iframe / `window.print` | 正式打印：业务确认 + PRT；预览 `previewMode=true` 不写 |
| hiprint 设计器 PreviewDialog | `tpl.print` 直打 | 预览不写；正式打强制走 runtime |
| 体检业务 `peisReportPrint/confirmPrint` | 与 PRT 双写 | 约定：列表「已打印」以业务事件或 PRT 二选一，文档化避免双源 |

---

## 5. 业务入口清单（按封装归并）

> 改契约时优先改 **封装函数**，避免每个页面各写一套。

| 封装 / 模块 | 覆盖场景 | 需关注点 |
| --- | --- | --- |
| `utils/print/runtime/print()` | 全部 | 强制校验：正式打必须有 bizRef 主键（可渐进） |
| `print-charge-receipt` | 收费小票 | 已 OK |
| `print-registration-receipt` | 挂号小票 | 已 OK |
| `print-daily-settlement` | 日结 | settlementId |
| `print-pharmacy-daily-settlement` | 药房日结 | settlementId |
| `print-doctor-station-prescription` | 门诊处方 | 已 OK |
| `print-outpatient-tcm-prescription` | 中药处方 | 已 OK |
| `print-service-apply` | 检查/检验 | 确认是否数组主键 |
| `print-nurse-station` | 处置/瓶签/采血/护士处方 | 处置单可加日期 |
| `print-inpatient-prepay-receipt` | 预交金 | 已 OK |
| `print-inv-document` | 三类库存场景 | 已 OK；入口多但封装统一 |
| `print-price-adjustment` | 调价 | 已 OK |
| `print-peis-report` | 体检 | 双写口径 |
| `print-center/print-center-print` | 住院打印中心全集 | **P1 重点**：摆药单/用药单/长临/费用/护理 |
| `views/outpatient/dispense` | 直调 print | 与医生站处方契约一致 |
| `views/report/...prescription-view` | 报表补打 | 与处方契约一致 |
| 旁路：体温单 / EMR / 设计器 | — | P3 |

住院护士站复用 `printInpatientPrintCenter` 的页面：打印中心、医嘱执行抽屉、汇总领药、护理记录、费用明细、入院分床等——**改打印中心封装即可覆盖多数**。

---

## 6. 读侧设计（草案）

### 6.1 建议 API（应用层）

**名称（暂定）**：`PrintLogAppService.queryPrintedKeys`  
**用途**：业务列表 enrich「已打印」

**请求（示例）**：

```json
{
  "scenarioCode": "ip-orderDispenseSheet-print",
  "queryDate": "2026-08-12",
  "keys": ["listRowKey-1", "listRowKey-2"],
  "keyField": "listRowKeys"
}
```

**响应（示例）**：

```json
{
  "printedKeys": ["listRowKey-1"],
  "lastPrintedAtByKey": {
    "listRowKey-1": "2026-08-12 10:22:01"
  }
}
```

规则：

- 仅计 `status=SUCCESS`
- 默认当前租户；可按 `created_at` 收窄（如查询日 ±1 天，或业务传入日期窗）
- 批量上限建议 ≤ 500 keys / 次

### 6.2 实现选项

| 方案 | 做法 | 优点 | 风险 |
| --- | --- | --- | --- |
| A. JSON 包含查询 | PG `biz_ref_json @>` / `jsonb_path` | 少表 | 索引与写法要仔细；历史弱 bizRef 查不到 |
| B. 明细投影表 | confirm 时拆 `primaryKeys` → `prt_print_event_biz_key` | 查询稳、可索引 | 多一次写 |
| C. 业务字段回写 | confirm 后写 `print_count` | 列表最快 | 双源；需与审计一致 |

**推荐**：中长期 **B（投影表）+ 审计真源**；短期可 A 试点单场景（摆药单）。

### 6.3 前端消费

以医嘱执行摆药单为例：

1. `loadExecutedMedSheetPending` 拉已执行行  
2. 收集 `listRowKeys` → `queryPrintedKeys`  
3. `statusCode=printed` / `executable`，状态列「已打印 / 未打印」  
4. 「未打」筛选对摆药单生效  

---

## 7. 分期计划

### P0 · 契约与读能力（全局底座）

- [ ] 本文定稿：§4 主键表评审通过  
- [ ] 前端：公共类型 `PrintBizRef` / 各场景 builder（可选）  
- [ ] 后端：`queryPrintedKeys`（或等价）+ 文档进 AS/SDD  
- [ ] （推荐）confirm 写入时同步投影 `primaryKeys`  

### P1 · 弱 bizRef 加厚（阻塞「已打印」）

- [ ] `ip-orderDispenseSheet-print` / `ip-oralMedSheet-print` → `listRowKeys[]`  
- [ ] 长/临医嘱、护理记录、费用清单/明细 → 补日期与行键策略  
- [ ] 巡视卡 / 腕带 / 床头卡 → 至少 `encounterId` + `queryDate`（标识类）  
- [ ] 打印中心列表 enrich + 未打筛选  

### P2 · 入口对齐与存量

- [ ] 所有封装入口对照 §4 自检  
- [ ] 报表补打、发药、护士站与医生站处方键一致  
- [ ] 体检双写口径书面确认  

### P3 · 旁路

- [ ] 体温单接入或明确豁免  
- [ ] EMR 正式打印接入 PRT  
- [ ] 设计器正式打禁旁路  

---

## 8. 验收口径（DoD）

| 编号 | 验收项 |
| --- | --- |
| AC-1 | 新打印 SUCCESS 日志含约定主键字段 |
| AC-2 | 同场景多入口写入同一 bizRef 形状 |
| AC-3 | `queryPrintedKeys` 对合打多键能正确命中 |
| AC-4 | 摆药单列表可区分已打印/未打印（P1 后） |
| AC-5 | 预览不产生计入已打印的 SUCCESS（或明确不计） |
| AC-6 | 旁路清单有关闭计划或豁免签字 |

---

## 9. 待拍板问题

1. **已打印粒度**：摆药单按 `listRowKey`（建议）还是按患者+日？  
2. **历史数据**：P1 上线前已打单据是否要求可回查？（弱 bizRef 无法回填）  
3. **投影表 vs 纯 JSON**：是否接受 confirm 多写一张明细表？  
4. **体检**：列表状态以业务 `confirmPrint` 还是 PRT？  
5. **体温单 / EMR**：本迭代是否强制进审计？  
6. **长/临医嘱「未打」**：续打/未打是否改为审计驱动（今日前端注释已写尚无已打印字段）？

---

## 10. 参考代码锚点

| 侧 | 路径 |
| --- | --- |
| 前端场景码 | `apps/web-ele/src/utils/print/constants/print-scenario.ts` |
| 前端 runtime | `apps/web-ele/src/utils/print/runtime/index.ts` |
| 住院打印中心 | `apps/web-ele/src/views/inpatient/nurse-station/print-center/print-center-print.ts` |
| 写审计 App | `PrintJobAppService#confirmPrint` |
| 读日志 App | `PrintLogAppService#queryPage` |
| 表 | `prt_print_event_log`（Flyway V9） |
| 领域 | `DM-PRT-002` / SDD 同名 |

---

## 11. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v0.1 | 2026-08-12 | 初版：现状、34 场景主键表、入口清单、读侧草案、分期与待拍板 |
| v0.2 | 2026-08-12 | 收窄：非护士站仅关联不回显；仅摆药单页面回显；明确不进主业务流程 |

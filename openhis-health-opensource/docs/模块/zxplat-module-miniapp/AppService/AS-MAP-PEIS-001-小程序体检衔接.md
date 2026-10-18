# 小程序体检衔接 · 应用服务设计（AS）

> **编码**：AS-MAP-PEIS-001 · **版本**：v1.1 · **日期**：2026-09-24  
> **类名**：`MiniappPeisAppService` · **HTTP**：`POST /api/miniapp/miniappPeis/{methodName}`  
> **详细设计编号**：AS-MAP-PEIS-001-SDD
> **模式**：B（代码先行回写；方法名与 `MiniappPeisAppService` 对齐）

---

## 2. 接口总览

| IF | 方法（代码真源） | 说明 |
|----|------|------|
| IF-MAP-PEIS-01 | `lookupRosterForDesk` / `selectRosterPackage` / `changeRosterSelectedPackage` | 团体名册 |
| IF-MAP-PEIS-02 | `listContractTypes` / `listValidContractsForPatient` | 合同类型 / 有效合同 |
| IF-MAP-PEIS-03 | `listBookableDays` / `listBookableSlots` / `createBooking` | 体检号源预约 |

> 门面类：`MiniappPeisApiController`（`@RequestMapping("/api/miniapp/miniappPeis")`）。

## 4. 编排

经 Port 委托：`PeisGroupRosterPatientPort` / `PeisContractPatientPort` / `HealthCheckupSlotOccupyPort`；写路径经 `MiniappPatientAccessSupport` 校验会话就诊人。

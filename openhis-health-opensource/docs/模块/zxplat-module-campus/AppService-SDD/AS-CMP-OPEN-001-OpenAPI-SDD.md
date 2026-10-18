# AS-CMP-OPEN-001 OpenAPI · AS-SDD

> **对应 AS**：`../AppService/AS-CMP-OPEN-001-OpenAPI.md` · **日期**：2026-09-24  
> **类**：`CampusGfOpenApiAppService` · `POST /api/app/campusGfOpenApi/{method}`

## 方法白名单

| IF | 方法 |
| --- | --- |
| INT-01 | `createAdjustApproval`（委托 `CampusGfRequestRecordAppService.createAdjustRequest`） |
| INT-02 | `onApprovalCallback`（若已暴露；以源码为准） |

## 依赖

仅编排至申请记录 AppService；**不**直接写比例表。

本 SDD 不写 Gap。

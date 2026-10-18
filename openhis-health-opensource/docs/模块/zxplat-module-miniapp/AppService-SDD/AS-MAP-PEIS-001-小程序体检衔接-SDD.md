# AS-MAP-PEIS-001 小程序体检衔接 · SDD

> **包**：`com.zxplat.plugin.miniapp.application.peis.service` · **类**：`MiniappPeisAppService`  
> **HTTP**：`POST /api/miniapp/miniappPeis/{methodName}` · **版本**：v1.1 · **日期**：2026-09-24

## 方法白名单（与代码对齐）

| IF | 方法 |
| --- | --- |
| 01 | `lookupRosterForDesk` / `selectRosterPackage` / `changeRosterSelectedPackage` |
| 02 | `listContractTypes` / `listValidContractsForPatient` |
| 03 | `listBookableDays` / `listBookableSlots` / `createBooking` |

无本模块写 PEIS 表；全部经 `com.zxplat.domain.share.extension.peis|booking` Port。

本 SDD 不写 Gap。

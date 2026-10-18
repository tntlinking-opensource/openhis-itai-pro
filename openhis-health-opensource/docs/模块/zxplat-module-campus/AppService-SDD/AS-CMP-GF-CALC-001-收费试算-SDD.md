# AS-CMP-GF-CALC-001 收费试算 · AS-SDD

> **对应 AS**：`../AppService/AS-CMP-GF-CALC-001-收费试算.md` · **日期**：2026-09-24  
> **类**：`CampusGfChargeCalcAppService` · `POST /api/app/campusGfChargeCalc/{method}`

## 方法白名单

试算/预览接口（以源码为准）；编排公费资格校验 + 比例解析。

## 依赖

`PublicExpenseEligibilityDomainService`、比例目录/项目、计费端口 `chargecalc`。

本 SDD 不写 Gap。

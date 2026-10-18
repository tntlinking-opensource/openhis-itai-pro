# AS-CMP-GF-APR-001 比例审批 · AS-SDD

> **对应 AS**：`../AppService/AS-CMP-GF-APR-001-比例审批.md` · **日期**：2026-09-24  
> **类**：`CampusGfApprovalAppService` · `POST /api/app/campusGfApproval/{method}`

## 方法白名单

审批列表/通过/驳回（以源码为准）；回写现行比例须带 `chargeModeEnum`。

## 依赖

`gfratiorequest` 领域；现行比例写侧。本 SDD 不写 Gap。

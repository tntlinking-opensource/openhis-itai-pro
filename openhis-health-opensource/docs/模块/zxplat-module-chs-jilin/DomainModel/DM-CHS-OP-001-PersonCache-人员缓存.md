# DM-CHS-OP-001 PersonCache · 人员缓存

> **表名**：`yb_person_cache`（及关联预结算/就诊映射）  
> **包**：`com.zxplat.plugin.chs.domain.outpatient`  
> **DomainService**：`YbPersonCacheDomainService`  
> **详细设计编号**：SDD-CHS-OP-001

## 1. 概述

门诊侧人员信息（1101 等）本地缓存与就诊平台映射、预结算快照。待遇查询 Extension 读本聚合。

## 2. 关键 DomainService

| 类型 | 说明 |
|------|------|
| `YbPersonCacheDomainService` | 人员缓存写 |
| `YbEncounterPlatformMapDomainService` | 就诊平台映射 |
| `YbClinicPreSettleDomainService` | 门诊预结算 |
| `YbClinicFeeUploadDomainService` | 门诊费用上传 |

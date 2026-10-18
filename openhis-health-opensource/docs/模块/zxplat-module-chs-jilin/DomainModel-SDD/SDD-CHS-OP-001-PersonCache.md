# SDD-CHS-OP-001 PersonCache · 人员缓存详细设计

> **成稿编号**：DM-CHS-OP-001  
> **源码包**：`com.zxplat.plugin.chs.domain.outpatient`  
> **主表**：`yb_person_cache`（及预结算 / 就诊平台映射）

## 1. 边界

门诊侧人员信息（1101 等）本地缓存、就诊平台映射、预结算快照。  
待遇查询 Extension（`bridge.integration.insurance.benefit`）**读本聚合**，不经 application。

## 4. 写入口（DomainService）

| 类型 | 职责 |
|------|------|
| `YbPersonCacheDomainService` | 人员缓存写 |
| `YbEncounterPlatformMapDomainService` | 就诊平台映射 |
| `YbClinicPreSettleDomainService` | 门诊预结算 |
| `YbClinicFeeUploadDomainService` | 门诊费用上传 |

方法签名以 domain 源码为准；本 SDD 不重复逐字段列表。

## 5. Extension 读侧

`InsuranceRegisterBenefitQueryExtensionImpl` 装配在 `com.zxplat.plugin.chs.bridge.integration.insurance.benefit`，开关 `zxplat.modules.chs.enabled=true`。

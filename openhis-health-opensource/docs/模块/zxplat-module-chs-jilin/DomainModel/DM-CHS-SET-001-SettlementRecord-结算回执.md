# DM-CHS-SET-001 SettlementRecord · 结算回执

> **表名**：`yb_settlement_record`  
> **包**：`com.zxplat.plugin.chs.domain.settlement`  
> **DomainService**：`YbSettlementRecordDomainService`  
> **详细设计编号**：SDD-CHS-SET-001

## 1. 概述

医保正式结算回执真源。门诊 2207 / 住院对应接口成功后由 DomainService 持久化；支付单绑定可后续 `rebindPaymentId`。

## 2. 关键写方法（DomainService）

| 方法 | 说明 |
|------|------|
| `saveOutpatientConfirmed` | 门诊正式结算回执落库 |
| `saveInpatientConfirmed` | 住院正式结算回执落库 |
| `save` | 通用保存 |
| `rebindPaymentId` | 重绑支付单 |

## 3. 同目录类型卡

- `settlement/service/YbSettlementRecordDomainService.md`
- `settlement/entity/YbSettlementRecord.md`

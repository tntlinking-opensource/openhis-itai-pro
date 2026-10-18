# SDD-CHS-SET-001 SettlementRecord · 结算回执详细设计

> **成稿编号**：DM-CHS-SET-001  
> **源码**：`com.zxplat.plugin.chs.domain.settlement.service.YbSettlementRecordDomainService`  
> **表**：`yb_settlement_record`

## 1. 边界

医保正式结算回执真源。门诊 2207 / 住院对应接口成功后由 DomainService 落库；支付单可后续重绑。

## 4. 写方法

| 方法 | 前置 | 效果 |
|------|------|------|
| `saveOutpatientConfirmed` | 预结算快照 + 2207 setlinfo | 新增门诊结算回执 |
| `saveInpatientConfirmed` | 住院预结算 + 平台回执 | 新增住院结算回执 |
| `save` | 已组装实体 | 持久化 |
| `rebindPaymentId` | 记录存在 | 更新 `paymentId` |

## 5. 查询

| 方法 | 说明 |
|------|------|
| `findByIdAndTenant` | 按 id + 租户读取 |

禁止在 SDD 写 Gap；缺口仅 AS §3。

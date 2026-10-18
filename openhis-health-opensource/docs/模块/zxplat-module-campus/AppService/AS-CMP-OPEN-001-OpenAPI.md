# 公费 Open API · 应用服务设计（AS）

> **编码**：AS-CMP-OPEN-001 · **版本**：v1.1 · **日期**：2026-07-19  
> **类名**：`CampusGfOpenApiAppService`  
> **现状**：✅ nenu-vue-his `/gf/open-api`（遗留 JSP 发起审批）  
> **IF**：IF-CMP-INT-01 / INT-02  
> **双模式**：同 [AS-CMP-GF-APR-001](./AS-CMP-GF-APR-001-比例审批.md) §1.1

---

## 2. 接口（目标态）

| IF | 方法 | 说明 |
|----|------|------|
| INT-01 | `createAdjustApproval` | 外部发起比例调整；**必填** `chargeModeEnum` |
| INT-02 | `onApprovalCallback` | 审批结果回调；回写时带申请单上的模式 |

### `createAdjustApproval` Request（关键）

| 字段 | 必填 | 说明 |
|------|:--:|------|
| securityKey | 是 | |
| chargeModeEnum | **是** | `SELF_PAY_GF` / `AFTER_YB_GF` |
| itemCode / newRatio | 是 | |
| … | | 同 APR-002 |

兼容：旧调用未传模式时，院点参数 `campus.gf.open-api.default-charge-mode`（默认 `SELF_PAY_GF`），并打废弃日志。

安全：`securityKey`（CFG-04 / yml）；迁移期兼容旧 path。

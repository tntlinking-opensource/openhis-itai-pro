# DM-CMP-GF-002 GfRatio · 公费比例与配置

> **表族**：`cmp_gf_switch` / `cmp_gf_free_percent` / `cmp_gf_charge_ratio` / `cmp_gf_charge_ratio_item` / `cmp_gf_ratio_request`（审批申请）  
> **版本**：v1.1 · **日期**：2026-07-19  
> **遗留**：`his_gf_*`；`ClinicChargeGfZf` / `ClinicChargeGfYb`  
> **AS**：GF-CFG / GF-RATIO / GF-APR / OPEN / CALC

---

## 1. 概述

公费总开关、甲乙丙全局自付、目录/项目比例、比例调整申请。  
**比例与申请按收费模式分存**：自费公费（`SELF_PAY_GF`）与医保后公费（`AFTER_YB_GF`）两套，见 §2.0。

**审批**：首批 Campus 自建状态机（[ADR-CMP-001](../ADR-CMP-001-成码前决策.md) D8），**不接 Flowable**；`flow_instance_id` 可空预留。

### 2.0 收费模式（`charge_mode_enum`）

| 码 | 中文 | 收银 |
|----|------|------|
| `SELF_PAY_GF` | 自费公费比例 | CHG-01 · `ClinicChargeGfZf` |
| `AFTER_YB_GF` | 医保后公费比例 | CHG-02 · `ClinicChargeGfYb` |

申请、现行比例、甲乙丙、试算 **同码**，禁止混用。

---

## 2. 表与职责

| 表 | 聚合/实体 | 职责 |
|----|-----------|------|
| `cmp_gf_switch` | `GfSwitch` | 总开关（CFG-01）；CFG-02 弱化见 ADR D6 |
| `cmp_gf_free_percent` | `GfFreePercent` | 甲乙丙全局自付（CFG-03）；**按模式分存** |
| `cmp_gf_charge_ratio` | `GfChargeRatio` | 目录级现行比例（RATIO-01）；含 `charge_mode_enum` |
| `cmp_gf_charge_ratio_item` | `GfChargeRatioItem` | 项目级比例（RATIO-02/05）；含 `charge_mode_enum` |
| `cmp_gf_ratio_request` | `GfRatioRequest` | 调整申请单（RATIO-03/06）；含 `charge_mode_enum` |

### 2.1 `cmp_gf_switch`（关键列）

| 列 | 说明 |
|----|------|
| id | PK |
| switch_key | 如 `MASTER`（CFG-01）；`CALC_MODE` 仅兼容只读/可不实现写 |
| switch_value | 字符串/JSON |
| tenant_id + 审计软删 | |

### 2.2 `cmp_gf_free_percent`

| 列 | 说明 |
|----|------|
| class_enum | 甲/乙/丙 |
| self_pay_ratio | **0～1 BigDecimal** |
| charge_mode_enum | **必填**：`SELF_PAY_GF` / `AFTER_YB_GF` |
| tenant_id + 审计 | |

唯一（未删）：`(tenant_id, class_enum, charge_mode_enum)`。

### 2.3 `cmp_gf_charge_ratio` / `_item`

| 列 | 说明 |
|----|------|
| catalog_code / item_code | 目录或项目编码 |
| charge_mode_enum | **必填**：`SELF_PAY_GF` / `AFTER_YB_GF` |
| ratio | BigDecimal 0～1 |
| effective_from / effective_to | 可空=长期 |
| tenant_id + 审计 | |

唯一（未删）：目录 `(tenant_id, catalog_code, charge_mode_enum)`；项目 `(tenant_id, item_code, charge_mode_enum)`（有效期重叠校验由领域负责）。

### 2.4 `cmp_gf_ratio_request`

| 列 | 说明 |
|----|------|
| request_no | 申请单号 |
| charge_mode_enum | **必填** |
| item_code | |
| old_ratio / new_ratio | |
| status_enum | DRAFT/PENDING/APPROVED/REJECTED/CANCELLED |
| flow_instance_id | **可空**（Flowable 预留，首批不用） |
| applicant_id / approve_time 等 | |
| tenant_id + 审计 | |

---

## 6. 领域服务（摘要）

| 服务 | 方法 | IF |
|------|------|-----|
| `GfConfigDomainService` | `saveSwitch` / `saveFreePercent` | CFG-01～03 |
| `GfRatioDomainService` | `upsertCatalogRatio` / `upsertItemRatio`（**含 chargeMode**） | RATIO-01/02/05 |
| `GfRatioRequestDomainService` | `createRequest` / `markApproved` / `markRejected`（申请含模式） | RATIO-03～06 |
| `GfConfigQueryService` / `GfRatioQueryService` | 读（按模式取现行比例） | |
| `GfChargeCalcDomainService` | `previewSplit(mode, …)` | CHG-01/02/03/07 |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-CMP-GF-01** | ratio ∈ [0,1] |
| **BR-CMP-GF-02** | 取比例链：项目(模式) → 目录(模式) → 全局甲乙丙；**模式必须与收银路径一致** |
| **BR-CMP-GF-03** | 审批通过才回写 **同模式** `_item`（RATIO-05） |
| **BR-CMP-GF-04** | CFG-01=false → 收银不拆公费 |
| **BR-CMP-GF-05** | 申请/现行比例 `charge_mode_enum` 必填且仅 `SELF_PAY_GF`/`AFTER_YB_GF` |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-19 | 成码首版；表名 `cmp_gf_*` 替代 `his_gf_*` |
| v1.1 | 2026-07-19 | 双模式：自费公费 / 医保后公费（申请+现行比例） |
| v1.2 | 2026-07-19 | ADR-CMP-001：甲乙丙分模式必填；审批不接 Flowable；CFG-02 弱化 |

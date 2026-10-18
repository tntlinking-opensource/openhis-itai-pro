# 比例审批（待办/已办）· 应用服务设计（AS）

> **编码**：AS-CMP-GF-APR-001 · **版本**：v1.2 · **日期**：2026-07-19  
> **类名**：`CampusGfApprovalAppService` · **路由**：`/campus/gf/todo`、`/finished`  
> **现状**：nenu-vue-his `/gf/todo` 可参考 UI；**首批自建状态机，不接 Flowable**（[ADR-CMP-001](../ADR-CMP-001-成码前决策.md) D8）  
> **成稿编号**：DM-CMP-GF-002
> **遗留对照**：`ClinicChargeGfZf`（自费公费）/ `ClinicChargeGfYb`（医保后公费）

---

## 1. 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 比例调整申请的待办/已办；按 **收费模式** 审批通过/驳回；回写对应模式的项目比例 |
| **在外** | 发起申请表单（APR-002 / OPEN-001）；真实收银拆分（CALC / CHG，见 ADR D 预留） |

### 1.1 两种比例申请（必选其一）

| 码 `chargeModeEnum` | 中文 | 语义 | 收银消费 |
|---------------------|------|------|----------|
| **`SELF_PAY_GF`** | 自费公费比例 | 费用走自费账户后，按本比例拆公费/自付 | CHG-01 · 旧 `ClinicChargeGfZf` |
| **`AFTER_YB_GF`** | 医保后公费比例 | 医保结算完成后，对剩余自付段再按本比例拆公费 | CHG-02 · 旧 `ClinicChargeGfYb` |

> 同一项目可同时存在两套现行比例（按 `charge_mode` 分存）。审批单、待办、回写 **必须带模式**，禁止混用。

### 1.2 BR

| 编号 | 规则 |
|------|------|
| BR-APR-01 | 申请单 `chargeModeEnum` 必填，仅允许上表两码 |
| BR-APR-02 | 审批通过只回写 **同模式** 的 `cmp_gf_charge_ratio_item`（或目录表） |
| BR-APR-03 | 待办/已办默认可按模式 Tab 或筛选项切换；列表展示模式标签 |
| BR-APR-04 | 驳回不改现行比例 |
| BR-APR-05 | 首批无 Flowable：状态机 `PENDING→APPROVED|REJECTED`（可 CANCELLED） |

---

## 2. 接口总览

| IF | 方法 | 说明 |
|----|------|------|
| IF-CMP-RATIO-04 | `pageTodo` | 待办；入参可含 `chargeModeEnum?` |
| IF-CMP-RATIO-04 | `pageFinished` | 已办；同上 |
| IF-CMP-RATIO-04 | `getApprovalDetail` | 详情含模式、改前/改后比例 |
| IF-CMP-RATIO-04 | `approve` | 通过 → RATIO-05 回写 |
| IF-CMP-RATIO-04 | `reject` | 驳回 |

### `pageTodo` / `pageFinished`

**Request**：`chargeModeEnum?`（空=全部）、`keyword?`、分页  
**Response 行**：`requestNo`、`chargeModeEnum`、`chargeModeLabel`、`itemCode`、`oldRatio`、`newRatio`、`applicantName`、`applyTime`、`statusEnum`

### `approve`

**Request**：`requestId`、`comment?`  
**编排**：

1. 加载申请单，校验 `PENDING`  
2. 取 `chargeModeEnum` + `itemCode` + `newRatio`  
3. `GfRatioDomainService#upsertItemRatio(mode, itemCode, ratio, …)`（RATIO-05）  
4. 申请单 → `APPROVED`  
5. 审计 / 可选事件  

### `reject`

申请单 → `REJECTED`；不写比例表。

---

## 3. UI 建议

```text
/campus/gf/todo
  Tab 或 Segment：全部 | 自费公费 | 医保后公费
  列表列增加「比例类型」
/campus/gf/finished 同上
```

---

## 5. 异常码

| 码 | 场景 |
|----|------|
| `GF_REQUEST_NOT_FOUND` | |
| `GF_REQUEST_STATUS_INVALID` | 非待审不可批 |
| `GF_CHARGE_MODE_INVALID` | 模式非法或空 |
| `GF_RATIO_OUT_OF_RANGE` | 回写比例非法 |

---

## 迁移备注

- 遗留无模式字段时：洗数默认 `SELF_PAY_GF`（ADR D10）  
- URL：`/gf/*` → `/campus/gf/*`；表：`his_gf_*` → `cmp_gf_*`  
- Flowable 迁入为后续可选，非首批范围  

---

## 9. 关联

- 发起：[AS-CMP-GF-APR-002](./AS-CMP-GF-APR-002-申请记录.md) · [AS-CMP-OPEN-001](./AS-CMP-OPEN-001-OpenAPI.md)  
- 试算：[AS-CMP-GF-CALC-001](./AS-CMP-GF-CALC-001-收费试算.md)  
- 功能清单：RATIO-03～06、CHG-01/02  
- ADR：[ADR-CMP-001](../ADR-CMP-001-成码前决策.md)  

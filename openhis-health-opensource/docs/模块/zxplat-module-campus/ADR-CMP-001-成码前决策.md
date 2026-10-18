# ADR-CMP-001 · Campus 成码前决策记录

> **状态**：Accepted  
> **日期**：2026-07-19  
> **院点**：东北师范大学医院（NENU）  
> **范围**：首批成码与公费/预结算预留口径  
> **关联**：[07-成码前Checklist](./07-成码前Checklist.md) · [08-Contracts](./08-Contracts与外部依赖.md) · [AS-CMP-GF-CALC-001](./AppService/AS-CMP-GF-CALC-001-收费试算.md)

---

## 背景

`zxplat-module-campus` 成码前需锁定院点、批次、装配、公费双模式、审批、资格、洗数、降级及预结算（D）落账口径，避免实现期反复改契约。

---

## 决策一览

| ID | 主题 | 结论 |
|----|------|------|
| D1 | 院点与校区 | NENU；`BENBU`=自由校区（本部，**主校区**）；`JINGYUE`=净月 |
| D2 | 首批范围 | **C**：组织 + 档案 + 公费管理端（CFG/RATIO/APR，双模式）；**不含**收银改草稿；**D 预留 TODO** |
| D3 | Maven | 独立 `zxplat-module-campus` + `campus-starter`；`health-app` 经 `-Pwith-campus`；`zxplat.modules.campus.enabled` |
| D4 | Flyway | V1 **整包**建组织/档案/公费表 |
| D5 | 预结算路径 | 收银员**显式**选 `gfChargeMode`（`SELF_PAY_GF` \| `AFTER_YB_GF`）；**不选不拆公费** |
| D6 | CFG-02 | **弱化**：不作默认路径；以收银员选择为准；首批可不实现写或只读兼容 |
| D7 | 甲乙丙 | **按模式分存**；`charge_mode_enum` **必填** |
| D8 | 审批 | Campus **自建状态机**（PENDING→APPROVED/REJECTED）；**不接 Flowable**；`flow_instance_id` 可空预留 |
| D9 | 资格 | **严**规则 + **明确原因码/中文说明**（禁止笼统失败） |
| D10 | 遗留洗数 | `his_gf_*` → 一律 `SELF_PAY_GF`；医保后比例事后补录 |
| D11 | 未启用 | **严格降级**：无 Reader；Peis 学院空列表；收银不调公费；禁止回读 `peis_company_college` 当真源 |
| D12 | 支付落账 | 公费/自付 → **支付明细两行**；账户类型 `PUBLIC_EXPENSE_ACCOUNT` |
| D12b/c | 开户 | 挂号时：Campus 启用且有**学生档案** → 幂等开公费户；**教职工挂号不开**，收费选 mode 时再处理 |

---

## 详述

### D1 校区种子

| campus_code | 名称 | is_primary |
|-------------|------|:----------:|
| `BENBU` | 自由校区（本部） | ✅ |
| `JINGYUE` | 净月校区 | |

### D2 批次与 D 预留

首批交付管理端与 Reader 契约；health 侧：

- 预结算请求预留 `gfChargeMode`
- `CampusChargeCalcReader` 接口先落
- `OutpatientChargeAppService` / 挂号开户处写 **TODO(ADR-CMP-001 D5/D12)**，本迭代**不改**支付草稿金额

### D5 / D12 预结算目标态（D 迭代）

```text
收银员选择 gfChargeMode
  → ARCH-05 严校验（失败带 reasonCode）
  → CampusChargeCalcReader.previewSplit(mode, …)
  → 支付明细：
       PUBLIC_EXPENSE_ACCOUNT 行 = gfAmount
       PERSONAL_CASH_ACCOUNT 行 = selfAmount
  → 若同时有医保：YbSettlement 与上述明细并存，不互相替代
AFTER_YB_GF：公费行拆的是医保后的自付段
```

### D9 资格默认（NENU）

**学生**：CFG-01 开 + `gf_status=ELIGIBLE` + 在校 + 有效期内 + 体检合格 + 组织启用 +（身份证一致若已绑定）  

**教职工**：CFG-01 开 + `gf_status=ELIGIBLE` + 在职 + 有效期内 + 组织启用 + 有证则校验未过期  

失败须结构化：`reasonCode` + `reasonMessage`（如 `GF_STATUS_NOT_ELIGIBLE`、`PHYSICAL_EXAM_NOT_PASS`、`OUT_OF_VALID_PERIOD`、`ORG_DISABLED`、`SWITCH_OFF`）。

### D12 开户与 Campus 耦合

| 时机 | 条件 | 行为 |
|------|------|------|
| 挂号开户 | `campus.enabled` 且存在 `cmp_patient_student`（已绑 Patient） | 幂等开 `PUBLIC_EXPENSE_ACCOUNT` |
| 挂号开户 | 仅教职工 / 无档案 / Campus 关 | **不开**公费户 |
| 收费（D） | 选了 `gfChargeMode` 且无公费户 | 补开（幂等）或明确拦截——D 实现时二选一，默认建议**补开** |

需新增 health `AccountTypeCode.PUBLIC_EXPENSE_ACCOUNT`（display：公费账户）。

---

## 后果

| 正面 | 代价 |
|------|------|
| 路径由收银员显式控制，对齐旧 Zf/Yb | 首批不做真实拆账，收银仍无公费分摊 |
| 审批不绑 Flowable，交付快 | 以后若要 Flowable 需迁状态/待办 |
| 甲乙丙分模式，与比例一致 | 配置维护量 ×2；洗数不能「空=共用」 |
| 挂号为学生开公费户 | health 挂号依赖可选 Campus Reader |

---

## 文档同步清单

- [x] 本 ADR  
- [x] `flyway`：`cmp_gf_free_percent.charge_mode_enum` NOT NULL  
- [x] DM-GF-001/002、AS-CFG/APR/CALC、08、07、02、00、README、09  

---

## 修订

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-19 | 问答决策首版 Accepted |

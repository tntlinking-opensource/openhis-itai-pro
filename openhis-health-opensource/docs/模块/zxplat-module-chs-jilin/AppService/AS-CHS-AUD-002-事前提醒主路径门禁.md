# 事前提醒主路径门禁 · 应用服务设计（AS）

> **页面边界**：收费/医生站嵌入对话框调用 **3101 门禁** 与 **3103 反馈**。**在内** Gate IF；**在外** 试调页（AUD-001）、统计研讨页（W3）。
>
> **编码**：AS-CHS-AUD-002 · **版本**：v0.7 · **日期**：2026-10-10  
> **LLD**：[25-明细审核31xx-详细设计.md](../25-明细审核31xx-详细设计.md) §2.5.2 / §2.6

---

## 1. 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 门诊/住院 3101 门禁（装载 Snapshot → 2201/2401 → 3101）；3103 反馈 |
| **在外** | 试调页手工 Command；W3 统计 |
| **平台 infno** | 3101、3103（门诊：签发复用挂号 map(11)；结算可按 med_type 补 2201） |
| **路由前缀** | `/api/app/insuranceDetailAuditGate/*` |

## 2. 接口概览

| IF 编号 | 语义 | infno | HTTP 方法 |
|---------|------|-------|-----------|
| IF-CHS-AUD-G-01 | 门诊门禁（trig 2/7 等） | 3101 | `analyzeOpGate` |
| IF-CHS-AUD-G-02 | 住院门禁（trig 9） | 3101（可触发 2401） | `analyzeIpGate` |
| IF-CHS-AUD-G-03 | 主路径反馈 | 3103 | `feedback3103` |

## 3. 关键业务规则

| 规则 | 说明 |
|------|------|
| mdtrt | 门诊：**签发/挂号类（trig=1/2）**只查挂号 map(11)，缺则提示先医保挂号；**预结算/结算（trig=6/7）**按勾选明细 med_type 找/补 map（`resolveOrRegister`）。住院：仍 `Ip2401Invoker.resolveOrRegister` |
| syscode | 必填：入参或 `zxplat.chs.audit.syscode`（院点 yml；Q12=C 无代码默认） |
| chargeItemIds | **可空**（trig=2 签发）：Gate 按就诊装载 `PLANNED` 后再剔自费；trig=7 收费仍传勾选 ID |
| **自费明细不进 3101** | 与门诊预结算 `OutpatientInsuranceChargeGrouper` **同口径**：挂非医保账户（含收费台转自费、医生选自费合同、代煎）的明细**不参与**事前/事中判定；混合批次仅医保行出站；**全为自费**时 `skipped=true`（`skipReason=SKIP_REASON_ALL_SELF_PAY`），**不出站** 3101，前端按无违规继续 |
| 参保人补齐 | 签发常不传读卡字段：Gate 从本就诊预结算 / 人缓存回填 `psnNo`、`insuplcAdmdvs`、`insutype`；`brdy` 来自 Encounter；**禁止**契约 `adm_vs` 冒充 `poolarea` |
| **2201 就诊凭证** | 读卡原样（`01`/`02`/`03`）；无凭证提示读卡（`Op2201Invoker`；结算补建档时） |
| **挂号建档** | 医保挂号确认仅 **2201** 写 map(`med_type=11`，强制)；自费不调；2203 在结算链 |
| **事前 mdtrt（未结算）** | 有慢病医嘱时 3101 **仍用**挂号普门 map(11) |
| **结算 mdtrt** | 3101 用**本次结算类型**（如慢病）；同 med_type 不同 diseCodg 仍算一次就诊；跨 med_type 须分次结算 |
| 人缓存查找 | `findValidByContractIdAndIdCard`：优先 `02` 别名键；仅有 `03` 主行时按 `snapshot_json` 含身份证号回落 |
| 医师 | ContextLoader 优先就诊 `ADMITTER`；无接诊医生时回落当前登录医务人员（签发人）`yb_no` |
| warn_rslt_id | `jrd_id` |
| 平台失败 | 阻断；前端不得继续预结算/签发完成（**不含**全自费 skipped，skipped 视为通过） |
| 前端挂接 | 门诊收费 trig=7（传读卡凭证）；住院收费 trig=9；医生站签发后 trig=2（返回修改则自动签退）。trig=2 **不传**读卡字段 |
| 医护完整预结算 | 医/护站入口：Gate(9) 通过后调 `inpatientSettle/preSettleInpatientCharges`（2303），**仅预览不确认结算** |

## 4. 关联

- AppService：`InsuranceDetailAuditGateAppService`
- Invoker：`DetailAuditGateInvoker` / `DetailAuditGateInvokerImpl`；门诊建档 `Op2201Invoker`；自费剔除复用 `OutpatientInsuranceChargeGrouper#listInsuranceChargeItemIds`
- 前端：`chs/components/detail-audit-remind-dialog.vue` · `chs/composables/use-detail-audit-gate.ts`（`gateHasViolations` 对 `skipped` 视为无违规）
- 医护预结算：`chs/composables/use-inpatient-clinical-pre-settle.ts` · `inpatient-clinical-pre-settle-preview-dialog.vue`

## 5. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v0.7 | 2026-10-10 | 自费明细（转自费/代煎等）不进 3101；全自费 skipped；与 2204 Grouper 同口径 |
| v0.6 | 2026-07-29 | 结算 3101 按 med_type；签发仍 map(11)；挂号强制 11；凭证注释清理 |
| v0.5 | 2026-07-29 | 挂号仅 2201；事前只复用 map(11)；凭证读卡原样；分单分次结算 |
| v0.4 | 2026-07-29 | 2201 读卡原样（曾误写强制 02）；修复市医保 FMI「解密卡串失败」 |
| v0.3 | 2026-07-29 | （已废）2201 统一 02+身份证；签发不读卡；人缓存 03 主行回落；医师回落签发人 |
| v0.2 | 2026-07-24 | 初版门禁规则与挂接 |

# P0 infno 字段映射（WORK_INJURY）

> **文档性质**：LLD 附录 — P0 infno 入参/出参与 Whale DTO、HIS 字段对照  
> **规范真源**：[参考/吉林省工伤保险信息系统协议机构接口开发手册-V1.3.md](./参考/吉林省工伤保险信息系统协议机构接口开发手册-V1.3.md)  
> **院点日志**：NEUSI `INPUT FROM HIS` 外壳字段  
> **版本**：v1.1 · **日期**：2026-07-01

---

## 1. 公共报文外壳（Win 注入）

Whale `prepareInvoke` 只下发表内 **input** 业务体；以下由 **OpenHIS.Win** 在 `Si_Busi` 前注入（与 NEUSI 一致）：

| 字段 | 来源 | 说明 |
|------|------|------|
| `infno` | prepare | 交易号 |
| `msgid` | Win 生成 | `{fixmedins}{yyyyMMddHHmmss}{seq4}` |
| `recer_sys_code` | 配置 | **`LNYTH`**（院方确认 Q3） |
| `fixmedins_code` | 契约 / Win | **`JL2201992002`**（DLL 出站；院内 fin_contract 列仍 `H22010402403`） |
| `infver` | 配置 | `V1.0` |
| `opter_type` | 固定 | `1` |
| `opter` | 当前用户工号 | |
| `opter_name` | 当前用户姓名 | |
| `inf_time` | Win | `yyyyMMddHHmmss` |
| `sign_no` | Win 9001 | 除 9001 外必填 |
| `idfi_mode` | 读卡上下文 | 通常 `1` |
| `input` | Whale | 业务 JSON 对象 |
| `cardinfo` | Win 读卡缓存 | 1101 及后续交易可能需要 |

---

## 2. P0 infno 一览

| infno | 名称 | biz_type | MVP |
|-------|------|----------|:---:|
| 9001 | 签到 | —（Win 内部） | ✅ |
| 9002 | 签退 | — | ✅ |
| 1101 | 读卡 | — | ✅ |
| 9102 | 审批信息查询 | WI_REG_QUERY | ✅ |
| 2301 | 审批信息上传 | WI_REG_APPROVAL | ✅ |
| 2201 | 门诊/住院登记 | WI_OP_SETTLE | ✅ |
| 2203 | 就诊信息上传 | WI_OP_SETTLE | ✅ |
| 2205 | 费用明细撤销 | WI_OP_SETTLE | ✅ |
| 2204 | 费用明细上报 | WI_OP_SETTLE | ✅ |
| 2206 | 费用预结算 | WI_OP_SETTLE | ✅ |
| 2207 | 费用结算 | WI_OP_SETTLE | ✅ |
| 2202 | 登记撤销 | WI_OP_SETTLE_REVERSE | ✅ |
| 1320 | 总额对账 | WI_RECON | ✅ |
| 1322 | 对账确认 | WI_RECON | ✅ |
| 1321 | 明细对账 | WI_RECON | ✅ |
| 2208 | 结算撤销 | WI_OP_SETTLE_REVERSE | ✅ |

---

## 3. 1101 读卡

### 3.1 input（Whale 组装部分）

| 平台字段 | Java/DTO | HIS 来源 | 必填 |
|----------|----------|----------|:----:|
| （按手册读卡输入） | `Wi1101Input` | 读卡器 / 电子凭证 | Y |

### 3.2 output（submit 后解析）

| 平台字段 | 锚点/用途 | 说明 |
|----------|-----------|------|
| `psn_no` | `yb_wi_encounter_anchor.psn_no` | 人员编号 |
| `diagnosis[]` | UI 展示 | 认定结论；含 `injureAffirmID` |
| `qualification_id` | **勿直接用于登记** | 手册：仅一条时返回；**必须 9102 全量查** |

---

## 4. 9102 审批信息查询 ⚠️ 联调风险

院点 NEUSI **0 次**；Whale P0 仍实现。

### 4.1 input

| 平台字段 | DTO | 必填 | Whale 默认 |
|----------|-----|:----:|------------|
| `startTime` | `startTime` | N | 当天 -30d，`YYYYMMDD` |
| `endTime` | `endTime` | N | 当天 |
| `psn_no` | `psnNo` | Y | anchor / 1101 |

### 4.2 output（多行）

| 平台字段 | UI 展示 | 落库 |
|----------|---------|------|
| `qualification_id` | 选择键 | anchor |
| `approveType` | 审批类型 | 展示 |
| `injured_injured_conclusion` | 认定结论 | 展示 |
| `startDate` / `endDate` | 有效期 | 校验 |
| `trt_chk_rslt` | 待遇校验 | 无审批提示 |

---

## 5. 2301 审批信息上传

### 5.1 input

| 平台字段 | DTO | 必填 | 来源 |
|----------|-----|:----:|------|
| `psn_no` | `psnNo` | Y | 1101 |
| `approveType` | `approveType` | Y | 院点多为 **12**（日志） |
| `startDate` | `startDate` | Y | 表单 `YYYYMMDD` |
| `endDate` | `endDate` | N | 空则 +30 天 |
| `terminReason` | `terminReason` | Y | 表单 |
| `injureAffirmID` | `injureAffirmId` | Y | 1101 diagnosis |
| `orgCode` / `orgName` | | N | 转诊类必填 |

### 5.2 output

| 平台字段 | 用途 |
|----------|------|
| `qualification_id` | 若返回则写 anchor；**仍须再 9102**（BR-WI-APR-001） |

---

## 6. 2201 门诊/住院登记

### 6.1 input

| 平台字段 | DTO | HIS 来源 | 必填 |
|----------|-----|----------|:----:|
| `ipt_otp_no` | `iptOtpNo` | anchor 生成 | Y |
| `psn_no` | `psnNo` | anchor | Y |
| `qualification_id` | `qualificationId` | 9102 所选 | Y |
| `adm_diag_dscr` | `admDiagDscr` | 主诊断 ICD | Y |
| `med_type` | `medType` | 默认 **12** | Y |
| `dept_code` / `dept_name` | | 挂号科室 | Y |
| `atddr_no` / `dr_name` | | 医生 | 按手册 |

### 6.2 与省医保差异

| 项 | 省医保 CHS | 工伤 WI |
|----|------------|---------|
| 就诊 ID | `mdtrt_id`（2203 后） | **`ipt_otp_no`** 贯穿 |
| 审批 | 无 9102/2301 | `qualification_id` 必填 |
| 登记时机 | 可选 2201 | **收费链首步 2201** |

---

## 7. 2203 就诊信息上传

| 平台字段 | 来源 |
|----------|------|
| `ipt_otp_no` | anchor |
| 诊断列表 | 医生站主/次诊断 |
| `med_type` | 12 |

（字段细则见 V1.3 §2203；DTO 类 `Wi2203Input`。）

---

## 8. 2205 / 2204 费用明细

### 8.1 2205 撤销

| 平台字段 | 说明 |
|----------|------|
| `ipt_otp_no` | 同就诊 |
| 处方/明细标识 | 按手册；整单撤销时传上次 2204 相同批次 |

### 8.2 2204 上报

| 平台字段 | HIS 来源 |
|----------|----------|
| `ipt_otp_no` | anchor |
| 明细行 | chargeItem：项目码、单价、数量、金额 |
| 目录对照 | 省医保 `yb_no`（工伤无 3301；用院内已对照码） |

---

## 9. 2206 / 2207 结算

### 9.1 2206 input

| 平台字段 | 说明 |
|----------|------|
| `ipt_otp_no` | anchor |
| `medfee_sumamt` | 明细合计 |

### 9.2 2206 output（预结算展示）

| 平台字段 | 收银 UI |
|----------|---------|
| `fund_pay` / 统筹 | 统筹支付 |
| `psn_pay` / 个人 | 个人支付 |
| `fulamt_ownpay_amt` 等 | 自费段 |

（以 V1.3 出参为准；DTO `Wi2206Output`。）

### 9.3 2207

input 同 2206；output 含 `setl_id` → `yb_settlement_record` + anchor。

---

## 10. 2202 / 2208

| infno | 关键 input |
|-------|------------|
| 2202 | `ipt_otp_no` |
| 2208 | `ipt_otp_no`、`setl_id`（2207 出参） |

---

## 11. 1320 / 1322 对账

| infno | 关键字段 |
|-------|----------|
| 1320 | `stmt_begindate`、`stmt_enddate`、`social_pool_code`（院点 220100） |
| 1322 | `month` YYYYMM |

NEUSI：1320 与 1322 **同天成对**。

---

## 12. Java DTO 命名约定

```text
com.zxplat.plugin.chs.api.scheme.workinjury
  Wi{Infno}Input   — prepareInvoke 业务体
  Wi{Infno}Output  — submitInvokeResult 解析
```

中性 Extension Command **不** 带 `Wi` 前缀；adapter 内映射。

---

## 13. 待联调确认项

| # | 项 | 影响 |
|---|-----|------|
| F-01 | 9102 院点是否可用 | 产品链 P0 |
| F-02 | ~~`fixmedins_code` 传 H22010402403 还是 JL2201992002~~ | **已定：`JL2201992002`**（Q2） |
| F-03 | ~~`recer_sys_code` JLYTH vs LNYTH~~ | **已定：`LNYTH`**（Q3） |
| F-04 | ~~2205 无历史明细是否必调~~ | **已定：跳过 2205**（Q7） |
| F-05 | 2301 出参 qualification_id 是否即时有效 | 2301→9102 间隔 |

---

## 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-07-01 | 对齐 Q2/Q3/Q7/Q17：JL2201992002、LNYTH、2208/1321 P0 |
| v1.0 | 2026-07-01 | P0 14 infno 摘要 |

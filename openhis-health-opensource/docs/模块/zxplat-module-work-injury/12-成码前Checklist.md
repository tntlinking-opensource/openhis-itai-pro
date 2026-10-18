# 工伤模块 · 成码前 Checklist

> **版本**：v1.0 · **日期**：2026-07-01  
> **用途**：文档评审通过 → 代码会话 **门禁**  
> **规范**：[08-概要设计.md](./08-概要设计.md) · [06-扩展组件与隔离策略.md](./06-扩展组件与隔离策略.md) · **[15-编码规范与Cursor规则对照.md](./15-编码规范与Cursor规则对照.md)**

---

## 1. 文档真源（须齐）

| # | 文档 | 状态 |
|---|------|:----:|
| D-01 | [01-接入架构决策.md](./01-接入架构决策.md) | ✅ |
| D-02 | [08-概要设计.md](./08-概要设计.md) | ✅ 已定稿 |
| D-03 | [09-门诊工伤结算-详细设计.md](./09-门诊工伤结算-详细设计.md) | ✅ |
| D-04 | [10-P0-infno字段映射.md](./10-P0-infno字段映射.md) | ✅ |
| D-05 | [11-客户端invoke会话-详细设计.md](./11-客户端invoke会话-详细设计.md) | ✅ |
| D-06 | [07-OpenHIS.Win工伤本地桥接.md](./07-OpenHIS.Win工伤本地桥接.md) | ✅ |
| D-07 | [05-用户操作说明.md](./05-用户操作说明.md) | ✅ |
| D-08 | [03-fin_contract映射示例.md](./03-fin_contract映射示例.md) | ✅ |
| D-09 | [14-院方确认决策.md](./14-院方确认决策.md) | ✅ |
| D-10 | [15-编码规范与Cursor规则对照.md](./15-编码规范与Cursor规则对照.md) | ✅ |

---

## 2. 架构硬规则（成码必守）

> **Cursor 自动规则**：[`docs/模块/.cursor/rules/whale-chs-jilin-gs-codegen.mdc`](../.cursor/rules/whale-chs-jilin-gs-codegen.mdc)  
> **全框架 Java**：须 `@代码生成通用规范`（见 [15 §2](./15-编码规范与Cursor规则对照.md)）

| # | 规则 |
|---|------|
| R-01 | `health-application` **禁止** import `chs.application.workinjury` / Win |
| R-02 | 工伤 **禁止** 走 `ChsHttpAccessor` / `OutpatientChargeInsuranceExtension` |
| R-03 | DLL **仅** OpenHIS.Win；Whale **无** `bridge_url` |
| R-04 | `zxplat.modules.work-injury.enabled=false` 时 **NoOp**，现网不变 |
| R-05 | 每 infno：**prepare → Win → submit**；禁止前端跳过 submit |
| R-06 | `sign_no` **不**存 Whale；Win 进程真源 |
| R-07 | 350x **仍**省医保 default-contract-bus-no |
| R-08 | Extension 接口在 `health-infrastructure`；Impl 在 `wi-jilin-bridge` |

---

## 3. Wave 0 — OpenHIS.Win（可先行）

| # | 项 | 验证 |
|---|-----|------|
| W-01 | `OpenHIS.Win.WorkInjury` 项目 | 编译 x86 |
| W-02 | `native/workinjury/JLSiInterface.dll` | verify-native |
| W-03 | `GET /api/work-injury/health` | nativeReady=true |
| W-04 | `POST /api/work-injury/invoke` infno=9001/9102 | 院点 DLL |
| W-05 | capabilities 位 `workInjury` | hello JSON |

---

## 4. Wave 1 — Whale 后端

| # | 项 | 验证 |
|---|-----|------|
| J-01 | `health-infrastructure` WorkInjury*Extension | 接口 + NoOp |
| J-02 | `wi-jilin-starter` + Flyway anchor + **invoke_session** 表 | 启用开关 |
| J-03 | `WorkInjuryClientInvokeAppService` prepare/submit | 单测 |
| J-04 | `WorkInjuryRegisterExtensionImpl` + `ApprovalUploadExtensionImpl` 9102/2301 | WI-UC-01/02 |
| J-05 | `WorkInjuryOutpatientSettlementExtensionImpl` 2201→2207 | WI-UC-01 |
| J-06 | BR-WI-SET-001 阻断 2206 | WI-UC-03 |
| J-07 | BR-WI-REG-001 挂号费 **一律**自费 | WI-UC-04 |
| J-08 | `OutpatientInsuranceChargeGrouper` WORKER_INSURANCE | 分组 |
| J-09 | `yb_invoke_log` scheme=WORK_INJURY | 审计 |

---

## 5. Wave 1 — 前端

| # | 项 | 验证 |
|---|-----|------|
| F-01 | `invokeWorkInjuryPlugin` | 调 Win 5000 |
| F-02 | 挂号 9102/2301 UI | capability 门控 |
| F-03 | 收费预结算循环 | 2206 展示金额 |
| F-04 | 未装 Win 提示 | AC-WIN-WI-01 |
| F-05 | 工伤角标（医生站） | enabled 时 |
| F-06 | **全部医生站** 须装 OpenHIS.Win（Q15） | 2301 可用 |

---

## 6. 院点联调（H22010402403）

| # | 项 | 验证 |
|---|-----|------|
| Y-01 | 9102 **可用**（F-01 风险） | 至少 1 次成功 |
| Y-02 | 2301→9102 全链 | qualification 有效 |
| Y-03 | 单笔 2207 | yb_settlement_record |
| Y-04 | 1320+**1321**+1322 同月 | 医保办 |
| Y-05 | 省/市/工伤三契约切换 | WI-UC-06 |
| Y-06 | work-injury.enabled=false 回归 | R-04 |
| Y-07 | **2208** 退费 | WI-UC-07 |
| Y-08 | 首笔收费 **无 2205** | WI-UC-05 |

---

## 7. 评审签字（建议）

| 角色 | 确认项 | 签字 |
|------|--------|:----:|
| 产品 | 05 用户流程、BR-WI | ☐ |
| 架构 | 08 HLD、06 隔离 | ☐ |
| 后端 | 09/10/11 LLD | ☐ |
| 前端 | 09 §8、07 Win 联调 | ☐ |
| 院方医保办 | 9102/2301 流程 | ☐ |

**门禁**：D-02～D-05、D-10 评审通过 + R-01～R-08 无异议 + [15 §8](./15-编码规范与Cursor规则对照.md) checklist → 开 Wave 0/1 代码。

---

## 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-07-01 | 增加 D-10、15 规范门禁 |
| v1.0 | 2026-07-01 | 首版 |

# 客户端 invoke 会话 — 详细设计（WORK_INJURY）

> **文档性质**：LLD — 同步多步 invoke、会话状态、半成功补偿  
> **上游**：[08-概要设计.md](./08-概要设计.md) · [chs-jilin-domain README](../../../zxplat-plugin/zxplat-module-chs-jilin/chs-jilin-domain/README.md)（Wave 1 **无** `yb_coordination`）  
> **Win**：[07-OpenHIS.Win工伤本地桥接.md](./07-OpenHIS.Win工伤本地桥接.md)  
> **版本**：v1.1 · **日期**：2026-07-01

---

## 1. 不用协同单

| 项 | 结论 |
|----|------|
| `yb_coordination` / Worker | **不用**。库中 **无此表**；CHS Wave 1 亦为 Extension **同步**编排（见 `zxplat-module-chs-jilin/chs-jilin-domain` README §3.1） |
| 工伤 P0 | **同步** `prepare → Win → submit` 循环；**无**后台 Worker |
| 持久化 | `yb_wi_encounter_anchor` + **`yb_wi_invoke_session`（P0 必建）** + `yb_invoke_log` + `yb_settlement_record` / `yb_wi_pre_settle` |

> 原稿「协同单 + `yb_coordination_step`」**作废**；勿引用 [zxplat-module-chs-jilin/05-协同单与Worker](../zxplat-module-chs-jilin/05-协同单与Worker-详细设计.md) 作为工伤实现依据（该文档为 CHS 异步规划，且未落地表结构）。

---

## 2. 与省医保 CHS 对齐（架构对照 · 单次结算互斥）

> **互斥范围**：**同一就诊 / 同一笔医保收费** 只走 **一条** scheme——`WORKER_INSURANCE` **或** `PROVINCIAL_INSURANCE`（及市医保等 CHS 契约），**不会**在同一笔里既调 FSI 220x 又调工伤 DLL。  
> **不互斥**：院点可同时启用两模块；同一窗口 **先后** 办省医保患者与工伤患者（[05](./05-用户操作说明.md) §8 三契约混窗 = **串行切换**，非并行混用）。

**路由（互斥点）**：

```text
fin_account → fin_contract.category
  WORKER_INSURANCE     → WorkInjuryOutpatientSettlementExtension → Win invoke
  PROVINCIAL_INSURANCE → OutpatientChargeInsuranceExtension   → FSI HTTP
  自费                  → 无第三方 Extension
```

下表是 **两种 scheme 各自的技术实现对照**，不是同一次交易里的并行路径。

| 维度 | 省医保（CHS，已实现） | 工伤（WORK_INJURY） |
|------|----------------------|---------------------|
| 编排 Extension | `OutpatientChargeInsuranceExtension` | `WorkInjuryOutpatientSettlementExtension`（**独立**，不进入 CHS Extension） |
| 平台调用 | JVM → FSI HTTP | **浏览器 → OpenHIS.Win → DLL** |
| 就诊锚点 | `yb_encounter_platform_map` | **`yb_wi_encounter_anchor`** |
| 预结算 | `yb_clinic_pre_settle` | **`yb_wi_pre_settle`** |
| 结算凭证 | `yb_settlement_record` | **复用**（`scheme_code=WORK_INJURY`） |
| 审计 | `yb_invoke_log` | **复用** |
| 步骤链 | Java 内固定 2203→2204→2206 | Java 内固定 **2201→2203→(2205?)→2204→2206→2207**（**不**做 YAML 配置） |

---

## 3. 业务阶段（phase）

| phase | 场景 | infno 链（代码常量） |
|-------|------|----------------------|
| `REG_QUERY` | 9102 | 1101? → 9102 |
| `REG_APPROVAL` | 2301 | 2301 → **9102** |
| `OP_SETTLE` | 收费 | 2201→2203→2205→2204→2206→2207 |
| `OP_REVERSE` | 退费 P0 | 2208 → 2205（按需） |
| `RECON` | 对账 P0 | 1320 → **1321** → 1322 |

---

## 4. invoke 会话（轻量）

### 4.1 yb_wi_invoke_session（**P0 必建**，Q12）

一次「挂号 9102」或「收费预结算到 2207」对应 **一条 ACTIVE 会话**：

| 列 | 说明 |
|----|------|
| `session_id` | UUID，前端循环携带 |
| `encounter_id` | |
| `payment_draft_id` | 收费时必填 |
| `phase` | REG_QUERY / OP_SETTLE / … |
| `step_index` | 当前步序号（0-based） |
| `pending_step_token` | prepare 下发；submit 必带 |
| `status` | ACTIVE / COMPLETED / FAILED |

**不用**单独 `yb_coordination_step` 表；每步 submit 成功后 `step_index++`，并写 `yb_invoke_log`。

### 4.2 单步状态

```text
prepareInvoke
  → 校验 BR-WI、生成 step_token、组装 input
  → session.pending_step_token = token, status = ACTIVE

submitInvokeResult(step_token)
  → 校验 token
  → 写 yb_invoke_log
  → 更新 anchor / pre_settle
  → step_index++；若还有下一步 → nextAction=PREPARE_NEXT
  → 否则 nextAction=COMPLETED
```

---

## 5. prepareInvoke / submitInvokeResult

### 5.1 prepareInvoke

```java
public class PrepareWorkInjuryInvokeRequest {
    private String sessionId;       // 首次 null，服务端创建
    private String phase;           // REG_QUERY | OP_SETTLE | ...
    private Long encounterId;
    private Long contractId;
    private Long paymentDraftId;    // OP_SETTLE 必填
    private Long operatorId;
    private Map<String, Object> context;  // 2301 表单等
}

public class PrepareWorkInjuryInvokeResponse {
    private String sessionId;
    private String stepToken;
    private String infno;
    private String fixmedinsCode;
    private String operatorNo;
    private Map<String, Object> input;
    private String nextAction;      // INVOKE_WIN | COMPLETED | FAILED
}
```

`infno` 由 **`WorkInjurySettleStepPlanner`** 按 `phase + step_index` 从 **Java 常量数组** 取值，**不提供** `application.yml` 逐步配置。

### 5.2 submitInvokeResult

```java
public class SubmitWorkInjuryInvokeResultRequest {
    private String sessionId;
    private String stepToken;
    private boolean success;
    private String infcode;
    private String errMsg;
    private Map<String, Object> output;
    private String winMsgid;
}
```

---

## 6. 半成功与补偿

| 场景 | session 状态 | 处理 |
|------|--------------|------|
| Win 成功，submit 超时/失败 | ACTIVE，`pending_step_token` 仍有效 | 前端 **重试 submit**（同 token）；勿重新 2207 |
| Win 2207 成功，HIS settle 失败 | COMPLETED（平台）+ payment 悬挂 | 2208 补偿；禁止新 msgid 再 2207 |
| Win 失败 | FAILED | 用户重试 → **新 session** 或从失败步 prepare |

---

## 7. 前端循环

```typescript
async function runWorkInjuryPhase(req: PrepareWorkInjuryInvokeRequest) {
  let prep = await workInjuryInvoke.prepareInvoke(req);
  while (prep.nextAction === 'INVOKE_WIN') {
    const winRes = await invokeWorkInjuryPlugin({ stepToken: prep.stepToken, ...prep });
    const sub = await workInjuryInvoke.submitInvokeResult({
      sessionId: prep.sessionId,
      stepToken: prep.stepToken,
      ...winRes,
    });
    if (sub.nextAction === 'COMPLETED') return sub;
    if (sub.nextAction === 'FAILED') throw new Error(sub.errMsg);
    prep = await workInjuryInvoke.prepareInvoke({
      sessionId: prep.sessionId,
      phase: req.phase,
      encounterId: req.encounterId,
    });
  }
}
```

AppService Bean 名：**`workInjuryInvoke`**（**health** `WorkInjuryInvokeAppService`）。  
API 前缀：**`/api/app/workInjuryInvoke/*`**（经 Extension → chs `WorkInjuryInvokeFacadeImpl`）。

---

## 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-07-01 | **废除协同单**；改为 invoke 会话；对齐 CHS Wave 1 |
| v1.0 | 2026-07-01 | 初版（含 yb_coordination，已作废） |

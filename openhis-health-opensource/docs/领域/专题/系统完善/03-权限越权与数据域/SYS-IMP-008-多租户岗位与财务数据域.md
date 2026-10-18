# SYS-IMP-008 多租户、岗位与财务数据域边界

> **问题类型**：权限越权与数据域 · [本类索引](./README.md) · [总索引](../README.md)
>
> **来源**：2026-06 代码审查（`OutpatientChargeAppService`、`OutpatientRefundAppService`、`OutpatientRegistrationAppService`、`InpatientSettleAppService`、`InpatientPrepayAppService`、`NurseStationEncounterVisibilitysupport 对比）。
>
> **关联 AS**：`AS-OP-FIN-002/003/004`、`AS-IP-FIN-002/003/004`、`AS-IP-NUR-001`（护士站可见性）、`AS-OP-REG-001`
>
> **关联**：`SYS-IMP-002` GAP-FIN-006（挂号防重）；`SYS-IMP-003` GAP-BKG-001（预约 Gate 可选）
>
> **状态**：待实施

---

## 1. 背景与结论摘要

ZXPlat 在 **住院护士站** 已建立较完整的 **就诊可见性 Gate**（`NurseStationEncounterVisibilitysupport：护士站队列 + 管辖病区 + 患者卡片一致）。但 **门诊/住院财务写路径** 普遍只校验：

- `encounterId` 存在且 Charge 归属该就诊；
- 操作员已登录（`CurrentOperatorResolver`）。

**未见**与岗位、机构、收银窗口、费别账户一致的 **横向数据域 Gate**。

**结论**：

| 类型 | 护士站 | 财务/挂号 |
| --- | --- | --- |
| 就诊可见性（病区/队列） | **有** | **无**（财务不按病区收窄） |
| 机构/租户范围 | 部分（管辖病区） | **弱**（会话机构用于建档，收费不校验） |
| 岗位角色（收银员/挂号员） | 有（医务人员解析） | **无**（不校验是否有权操作该科室就诊） |
| 患者卡片一致性 | **有** | 部分（退款/结算校验 encounter↔patient） |

本文件聚焦 **越权与数据域**，与 IMP-001～007 的 **并发/Stale** 互补；二者叠加时风险更高（越权 + Stale 可对他人就诊收款）。

---

## 2. 问题清单总览

| ID | 标题 | 严重度 | 门/住 | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- |
| GAP-ACL-001 | 门诊收费预结算/正式结算无机构-岗位 Gate | **高危** | 门诊 | P0 | 待实施 |
| GAP-ACL-002 | 门诊退费/反结无收银窗口数据域 Gate | **高危** | 门诊 | P0 | 待实施 |
| GAP-ACL-003 | 住院结算/预交金无病区-财务岗位 Gate | 中危 | 住 | P1 | 待实施 |
| GAP-ACL-004 | 挂号写路径可指定任意 `patientId`（IDOR） | 中危 | 门+住 | P1 | 待实施 |
| GAP-ACL-005 | 财务列表 Query 与写路径 Gate 不一致 | 中危 | 门+住 | P2 | 待实施 |
| GAP-ACL-006 | Booking 模块未部署时预约 Gate 静默跳过 | 中危 | 门诊 | P2 | 文档化 |
| GAP-ACL-007 | 跨租户/跨机构 API 无统一 Facade | 低危～中危 | 全局 | P2 | 待评估 |

---

## 3. 分项：问题与修改方案

### GAP-ACL-001 门诊收费无机构-岗位 Gate

| 维度 | 说明 |
| --- | --- |
| **严重度** | **高危**（合规/等保类；知晓 encounterId 即可操作） |
| **关联 IF/BR** | IF-OPC-06/07；AS 隐含「当前收银岗位可见队列」 |
| **现象** | `preSettleOutpatientCharges`、`confirmOutpatientChargeSettlement`、`loadPendingChargeItems` 等：`mustGetEncounter` + `assertChargeItemsBelongEncounter`，**不**校验当前操作员机构/岗位是否覆盖该就诊科室。 |
| **根因** | 无 `EncounterFinancialAccesssupport 或等价组件；护士站 `assertVisible` 未复用到财务层。 |
| **业务影响** | 水平越权：A 科室收银员对 B 科室就诊预结算/收款；审计无法归因到正确窗口。 |

**修改方案（推荐）**

1. 新增 **`EncounterFinancialAccesssupport**（module-support）：
   - 输入：`encounterId`、`operatorId`、可选 `cashierWindowId`；
   - 校验：就诊 `organizationId` ∈ 操作员可管理科室/机构；可选校验费别账户可见性。
2. 在 **所有财务写入口** 首行调用：`OutpatientChargeAppService`、`OutpatientRefundAppService`、挂号 `payRegisterFee` 等。
3. Query 列表：`OutpatientChargeEncounterQueueLoader` 与写 Gate 共用同一收窄规则（防「列表看不到但能直调 API」）。
4. 失败码：统一 `FIN-ACL-403` / 复用 `EX-IP-NUR-102` 风格文案。

**涉及代码**

- 新建 `EncounterFinancialAccessSupport.java`
- `OutpatientChargeAppService.java`（preSettle / confirm / loadPending）
- `OutpatientChargeEncounterQueueLoader.java`

**验收用例**

- T1：操作员仅管辖科室 A，对科室 B 的 `encounterId` 预结算 → 403。
- T2：同机构多科室岗位，队列仅含授权科室就诊。
- T3：Supervisor 角色（若产品定义）可跨科室 — 按 RBAC 配置覆盖。

**工作量**：3～4 人天

---

### GAP-ACL-002 门诊退费/反结无数据域 Gate

| 维度 | 说明 |
| --- | --- |
| **严重度** | **高危** |
| **关联 IF/BR** | IF-OPC-08/09；**BR-REF-*** |
| **现象** | 退费受理、反结路径校验 Payment 归属 encounter，**不**校验是否原收银窗口/原操作员机构。 |
| **根因** | 与 GAP-ACL-001 同族。 |
| **业务影响** | 跨窗口对他人流水发起退费；日结对账责任不清。 |

**修改方案**

1. 复用 `EncounterFinancialAccesssupport；
2. 可选增强：退费须 `payment.cashierId == currentOperator` 或同组授权角色（产品定）；
3. 与 **GAP-FIN-005** 退款窗口并发修复一并上线。

**涉及代码**

- `OutpatientRefundAppService.java`

**验收用例**

- 非授权岗位对 SUCCESS Payment 发起退费 → 403。

**工作量**：1～2 人天（与 ACL-001 合并）

---

### GAP-ACL-003 住院结算/预交金无病区 Gate

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联 IF/BR** | IF-IP-STL-06；IF-IP-PREP-03 |
| **现象** | `InpatientSettleAppService`、`InpatientPrepayAppService` 校验 encounter 住院态与 Charge 归属，**不**校验财务岗位是否覆盖该就诊当前病区/科室。 |
| **根因** | 住院财务 Gate 与护士站可见性未共享。 |
| **业务影响** | 住院处可对非管辖病区患者结算/收退预交金（视院方岗位划分而定，多数医院应限制）。 |

**修改方案**

1. 扩展 `EncounterFinancialAccesssupport 住院分支：对齐 `managedWardIds` 或财务科「全院结算」白名单角色；
2. 列表与写路径一致：`InpatientSettleAppService` 待结队列 Query 同源收窄。

**涉及代码**

- `InpatientSettleAppService.java`
- `InpatientPrepayAppService.java`

**工作量**：2～3 人天

---

### GAP-ACL-004 挂号可指定任意 patientId

| 维度 | 说明 |
| --- | --- | --- |
| **严重度** | 中危 |
| **关联 IF/BR** | IF-OP-REG-01 |
| **现象** | `resolvePatientIdForPreRegister`：若 `request.getPatientId() != null` **直接复用**，不校验该患者是否属于当前机构/是否对操作员可见。 |
| **根因** | 信任前端 patientId；无 `PatientAccessGate`。 |
| **业务影响** | IDOR：为他人 patientId 挂号产生 encounter 与费用；与 GAP-FIN-006 防重（按 patientId）组合可能产生「挂错人」而非「双挂」。 |

**修改方案**

1. 指定 `patientId` 时：`patientQueryService` 加载 + 校验机构归属/状态 ACTIVE；
2. 身份证查重路径保留；新建患者仍走 `requireSessionOrganizationUnitId`；
3. 住院 `InpatientRegistrationAppService#resolvePatientIdForSubmit` 对称改造。

**涉及代码**

- `OutpatientRegistrationAppService#resolvePatientIdForPreRegister`
- `InpatientRegistrationAppService#resolvePatientIdForSubmit`

**验收用例**

- 传入其他机构 patientId → REG-PAT-403。

**工作量**：1～2 人天

---

### GAP-ACL-005 列表 Query 与写 Gate 不一致

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **现象** | 写路径加 Gate 后，若列表仍全量，攻击者仍可从别处获知 encounterId；正常用户也可能「列表无、API 有」。 |
| **修改方案** | 财务/挂号所有 `*QueueLoader`、`*PageQuery` 与 `EncounterFinancialAccesssupport 共用 Specification；集成测试「列表 ⊆ 写权限」。 |

**工作量**：2 人天（随 ACL-001 一并）

---

### GAP-ACL-006 Booking 模块可选导致 Gate 缺失

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危（部署配置） |
| **现象** | `OutpatientRegistrationAppService` 中多处 `if (bookingQueryPort == null) return/skip`；无 booking 模块时 **不**执行 `validateForRegistration`、不占号校验。 |
| **根因** | 可选 Client 设计；单模块部署简化。 |
| **业务影响** | 生产若误不配 booking 且仍启用预约入口 → 预约占号、时段校验全失效。 |

**修改方案**

1. **配置显式开关**：`whale.booking.required=true` 时 Client 缺失启动失败；
2. 或挂号入口检测 `appointmentId` 非空但 Client 为空 → 明确 `BKG-MOD-503`；
3. 运维检查清单写入部署文档。

**状态**：文档化 + 启动校验（可选）

**工作量**：0.5 人天

---

### GAP-ACL-007 跨模块无统一 Access Facade

| 维度 | 说明 |
| --- | --- |
| **严重度** | 低危～中危 |
| **现象** | 护士站、药房、财务、医生站各自实现可见性，规则易漂移。 |
| **修改方案** | 中长期：`EncounterAccessPolicy` 接口 + 各模块 Adapter；短期至少财务与护士站共享 org/ward 解析器。 |

**状态**：待评估

---

## 4. 边界问题（非越权但相关）

| 编号 | 主题 | 说明 | 建议 |
| --- | --- | --- | --- |
| BND-ACL-001 | Shiro/API 鉴权 vs 业务数据域 | 已登录即可调 AppService API | 业务 Gate 不可替代认证 |
| BND-ACL-002 | 超级管理员 | 运维账号是否 bypass | 产品明确并审计 |
| BND-ACL-003 | 多机构集团 | 同一用户多 org 切换 | Session org 与 encounter org 一致校验 |

---

## 5. 推荐实施顺序

| 顺序 | ID | 内容 | 人天 |
| --- | --- | --- | --- |
| 1 | GAP-ACL-001 + 005 | 门诊收费 AccessSupport + 队列一致 | 3～4 |
| 2 | GAP-ACL-002 | 退费/反结 | 1～2 |
| 3 | GAP-ACL-004 | 挂号 patientId Gate | 1～2 |
| 4 | GAP-ACL-003 | 住院财务 | 2～3 |
| 5 | GAP-ACL-006 | Booking 必选配置 | 0.5 |

---

## 6. 不建议的做法

| 做法 | 原因 |
| --- | --- |
| 仅在前端隐藏他人就诊 | 挡不住 API 直调 |
| 每个 AppService 复制 if 判断 | 规则漂移；应集中 Support |
| 用 encounterId  obscurity 代替 Gate | 不符合等保 |

---

## 7. 回归测试矩阵

| # | 场景 | 期望 |
| --- | --- | --- |
| T1 | 越权 encounterId 预结算 | 403 |
| T2 | 越权 encounterId 正式结算 | 403 |
| T3 | 越权 patientId 挂号 | 403 |
| T4 | 授权岗位正常收银 | 成功 |
| T5 | booking 关闭 + appointmentId 非空 | 明确失败 |
| T6 | 列表不含的 encounter 写操作 | 403 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 初稿：多租户/岗位/财务数据域边界审查 |

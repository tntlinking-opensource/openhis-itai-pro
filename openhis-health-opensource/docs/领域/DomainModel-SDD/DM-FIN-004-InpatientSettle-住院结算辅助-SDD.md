# DM-FIN-004 InpatientSettle 住院结算辅助 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-FIN-004-InpatientSettle-住院结算辅助.md`（minimal canonical，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Financial · 住院结算读侧上下文组装  
> **聚合**：InpatientSettle（无本包实体，读侧投影）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-FIN-004 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain` |
| 包规则 `com.zxplat.domain.financial.inpatientsettle` | 满足 | 全小写 |
| 固定技术口径 | 满足 | 见 §3、§9 |
| DM §6.1 写侧 | 满足 | 无写方法（0） |
| DM §6.2 读侧 | 满足 | `mustLoadSettleContext` |
| DM §7 BR | 满足 | §6 映射 BR-IPSET-001～002 |
| DM §5 事件 | 满足 | 无 |

**与 DM 显式差异（采纳依据：模式 B）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 服务类名 | 附录 `InpatientSettleContextQueryService` | 与源码一致（非泛称 QueryService） |
| 登记账户 | — | 缺失抛 `InpatientSettleConstants.EX_IP_STL_103` |
| Gate | DM §1 写应用编排 | 本方法不含患者/就诊状态 Gate（JavaDoc 明示） |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化住院结算 **L1 批量 hydrate**：就诊上下文 + 登记账户 + 待结/已结费用合计 + 反结待重结标记。
- **不写入**结算结果表、不执行 Payment/Account 写操作（BR-IPSET-001）。
- **单次成稿目标**：与 `InpatientSettleContextQueryService` 一致。

### 1.2 边界

- **在内**：`InpatientSettleContextQueryModel` 组装。
- **在外**：结算记账、支付流水写入、应用层 Gate。
- **跨域读**：`EncounterContextQueryService`、`AccountQueryService`、`ChargeItemQueryService`、`PaymentReconciliationQueryService`。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain`（`financial.inpatientsettle`）。

### 2.2 包结构（`com.zxplat.domain.financial.inpatientsettle`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | 无 |
| `command` | 无 |
| `repository` | 无 |
| `service` | `InpatientSettleContextQueryService` |
| `queries.model` | `InpatientSettleContextQueryModel` |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 金额 | `BigDecimal` |
| 嵌套模型 | `InpatientEncounterContextQueryModel`、`Account` 实体引用（读路径） |
| 事件 / 写库 | 无 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

无本包表。

### 4.2 聚合根

无 JPA 聚合根。

### 4.3 子实体

无。

### 4.5 仓储接口

无。

### 4.6 查询模型

#### 4.6.1 `InpatientSettleContextQueryModel`（对应 `mustLoadSettleContext`）

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| `inpatientContext` | `InpatientEncounterContextQueryModel` | Encounter 读服务 | 住院就诊展示上下文 |
| `registrationAccount` | `Account` | Account 读服务 | 个人现金登记账户 |
| `pendingSettlementTotal` | `BigDecimal` | ChargeItem 汇总 | 待结算合计 |
| `settledTotal` | `BigDecimal` | ChargeItem 汇总 | 已结算合计 |
| `reversedPendingResettle` | `boolean` | PaymentReconciliation | 是否反结待重结 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — （无 DomainService）

写方法 **0**（DM §6.1 N/A）。结算写能力在 Payment/ChargeItem 等聚合；**本节无 `#### 5.1.n` 块**。

### 5.2 读侧 — `InpatientSettleContextQueryService`

| 方法 | 入参 | 出参（§4.6.1） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | -------------- | -------- | ---------------- |
| `mustLoadSettleContext` | `Long encounterId`, `Long patientId` | `InpatientSettleContextQueryModel` | `mustGetInpatientContextByEncounterId`；`getRegistrationAccount`；两 sum；`filterReversedPendingResettleEncounterIds` | `TC-DM-FIN-004-R-001` |

**方法契约摘要**：登记账户 null 抛 `EX_IP_STL_103`「请先完成住院登记」；不捏造金额（BR-IPSET-002）。**注释锚点**：就诊上下文 → 账户 → 费用汇总 → 反结标记 → 装配 Model。

**读方法注释锚点（统一）**：跨 QueryService 只读调用 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-IPSET-001 | 本服务无 Repository 注入；不 save 任何结算表 |
| BR-IPSET-002 | 上游 null/空汇总保持 null/零；禁止伪造 pending/settled |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷 | 触发方法 |
| -------- | ------------ | -------- | -------- |
| （无） | — | — | 只读组装 |

**发布时机**：不适用。

---

## 8. 删除与可见性契约

- 不涉及本包软删；上游实体软删策略各自生效。
- `mustLoadSettleContext` 为强依赖读：账户缺失即失败。

---

## 9. 框架优先适配规则

- 跨域仅注入 QueryService，禁止 Repository 跨聚合（B.6.4）。
- 遵循项目级成码门禁。

---

## 10. 生成与验收清单

- [ ] 单读方法与 QueryModel 字段
- [ ] 错误码 EX_IP_STL_103
- [ ] 编译通过

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- DM-FIN-004 + 本 SDD。

### 11.2 DoD

- L1 上下文字段与 IF-IP-STL-03 消费方一致。

### 11.3 失败处理

- 新增汇总字段须先改 DM 再改 SDD。

---

## 定稿检查清单（可打勾）

- [x] 写 0 / 读 1 = DM 附录
- [x] §4.6 字段表完整
- [x] BR-IPSET 已映射

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 25 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 10 |
| **总分** | **100** | **100，可进入代码会话** |

---

**本 SDD 正文结束。**

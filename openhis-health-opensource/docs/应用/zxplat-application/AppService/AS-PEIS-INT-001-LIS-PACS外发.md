# 体检域 · LIS/PACS 外发 · 应用服务设计（AS）

> **编码**：AS-PEIS-INT-001 · **版本**：v1.1 · **日期**：2026-06-27  
> **触发**：无菜单；`PeisLisPacsDockAppService`（support）由 REG/DES 调用  
> **术语**：[`PEIS-LIS-PACS文档口径`](../../../../.cursor/skills/PEIS-LIS-PACS文档口径/SKILL.md) · [`whale-peis-codegen.mdc`](../../../../.cursor/rules/whale-peis-codegen.mdc)

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 按 SR **本院**外发 LIS/PACS（INT-01）；条码子能力；拉取结果（INT-03）；重置外发标志（INT-04） |
| **在外** | LIS/PACS 执行与报告；**金域 EXT** → [LisKingMed](../../../模块/zxplat-module-lis-kingmed/)；连接/路由页 → [INT-002](./AS-PEIS-INT-002-外发集成配置.md) |
| **涉及域** | ServiceRequest、`peis_service_request_ext`、LIS/PACS WorkItem、`cln_observation` |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **分科/登记** | 触发外发、刷新结果 |
| **管理员** | 误外发时重置标志（INT-04，P1） |
| **系统** | PROOF→LIS、TEST→PACS；金域项 INT-01 后由 LIS EXT 接续 |

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **INT-01 外发** | PEIS → **本院** LisWorkItem / PacsWorkItem（同 `activity_id`） |
| **外发 LIS（院点口语）** | **金域外送**：[LisKingMed](../../../模块/zxplat-module-lis-kingmed/)；**≠** 不发院内申请 |
| **无 map** | 同 `activity_id`；见 [Peis 04 §5](../PeisAppService/04-FHIR领域模型.md) |
| **条码** | 读 WorkItem，**不写** ext.barcode（D6） |

### 1.4 范围

| P0 | INT-01、INT-03 |
| P1 | INT-04 重置标志 |
| 非目标 | INT-06～08（**INT-002** 配置页）；peis-adapter 入站（INT-05） |

### 1.5 领域清单

| 实体 / 服务 | 说明 |
|-------------|------|
| `PeisServiceRequestExt` | `lis/pacs_dispatch_enum` |
| `LisResultProjectionService` | INT-03 / DES-08 投影 |
| `PeisServiceRequestExtDomainService` | dispatch 标志更新 |

### 1.6 业务规则（BR）

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-PEIS-INT-01** | PROOF→LIS；TEST→PACS DomainService | INT-01 |
| **BR-PEIS-INT-02** | 外发成功 → ext `*_dispatch_enum=sent` | INT-01 |
| **BR-PEIS-INT-03** | LIS 投影须 `source_observation_id` | INT-03 / DES-08 |
| **BR-PEIS-INT-04** | 条码：反查 WorkItem.barcode / accessionNo | INT-01 子能力 |
| **BR-PEIS-INT-05** | reset 仅改 ext 标志为 `notSent`；**不删** WorkItem/结果（P1） | INT-04 |
| **BR-PEIS-INT-06** | `resultReceived` 后 reset 须管理员权限（院点） | INT-04 |

---

## 2. 页面与接口总览（契约概览）

| IF | 用户动作 / 触发 | 接口语义 | 读写 | 关键规则 | 状态变化 | 涉及领域 | 查询服务 |
|----|-----------------|----------|:--:|----------|----------|----------|----------|
| **IF-PEIS-INT-01** | 外发按钮 | 创建 WorkItem + 条码 | 写 | BR-INT-01～04 | notSent→sent | LIS/PACS、ext | WorkItem Query |
| **IF-PEIS-INT-03** | 刷新结果（补偿） | DES-08 投影 CLN | 写 | BR-INT-03 | sent→resultReceived | cln_observation | LisResultProjection |
| **IF-PEIS-INT-04** | 重置标志 | ext dispatch 回退 | 写 | BR-INT-05/06 | sent/resultReceived→notSent | ext | — |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
|--------|----------|-------------|-------------------|----------|---------|--------|------|
| GAP-PEIS-INT-01 | **DM 能力** | [DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md) §6.3 | `LisResultProjectionService#projectFromLis` | 领域实现 + 单测 | INT-03 / DES-08 | P0 | 待建 |

---

## 4. 接口编排说明书（业务步骤）

### 4.1 IF-PEIS-INT-01 外发与条码

见 [04 §5.2](../PeisAppService/04-FHIR领域模型.md#52-des-08--int-01-时序p0)。

| 步骤 | 说明 |
|------|------|
| 1 | 读 SR + `ActivityDefinition.type_enum` |
| 2 | PROOF → `LisWorkItemDomainService`；TEST → `PacsWorkItemDomainService` |
| 3 | 更新 ext `*_dispatch_enum=sent` |
| 4 | 条码子能力：读 WorkItem，返回 UI |
| 5 | 金域项：LIS 侧 EXT 在 WorkItem 之后上传（非 PEIS map） |

**失败**：记录 failedItems；可经 INT-002/INT-08 重试（P2）。

### 4.2 IF-PEIS-INT-03 刷新结果（DES-08 **补偿**）

| 步骤 | 说明 |
|------|------|
| 1 | Pre：ext.dispatch=sent 或 partial；或 AUD-05 投影失败需重试 |
| 2 | `LisResultProjectionService#projectFromLis`（与 LIS AUD-05 同领域能力） |
| 3 | upsert `cln_observation` + `source_observation_id` |
| 4 | ext → `resultReceived` |

> **P0 主路径**为 LIS **AUD-05** 发布后自动投影（[DM-CLN-007 §6.3](./DM-CLN-007-Observation-临床观察实例.md)）；本 IF 用于漏投影、院点关闭自动投影或诊台主动刷新。

### 4.3 IF-PEIS-INT-04 重置外发/回写标志

| 维度 | 说明 |
|------|------|
| **触发** | 管理员工具 / 诊台「重置外发」（误操作纠正） |
| **主路径** | 选定 SR 列表 + 模块（LIS/PACS/both）→ `updateDispatchFlags(..., notSent)` |
| **约束** | **不删除** LisWorkItem/PacsWorkItem；**不删**已投影 `cln_observation`（BR-INT-05） |
| **权限** | `resultReceived` → reset 须管理员（BR-INT-06） |
| **后续** | 可再次 INT-01 外发（幂等：已有 WorkItem 则返条码） |

---

## 5. 业务异常与用户提示

| 码 | 场景 | 提示 | 可重试 |
|----|------|------|:--:|
| `DISPATCH_TARGET_UNKNOWN` | 无法路由 | 项目类型不支持外发 | 否 |
| `LIS_PACS_REJECT` | 下游拒绝 | 外发失败，见明细 | 是 |
| `RESET_NOT_ALLOWED` | 无权限 reset resultReceived | 请联系管理员 | 否 |
| `ALREADY_NOT_SENT` | 已是 notSent | 无需重置 | 是 |

---

## 6. 事件语义与消费方

| 事件 | 触发 | 消费方 |
|------|------|--------|
| `LisWorkItemCreatedEvent` / DR final（P1 可选） | LIS/PACS | 自动 INT-03 |
| dispatch 成功 | INT-01 | 采样/PACS 工作台 |

---

## 7. 非功能性需求

| 项 | 承诺 |
|----|------|
| **事务** | INT-01 WorkItem + ext 同事务（院点可配置最终一致） |
| **幂等** | 已 sent 且 WorkItem 存在 → 跳过创建，返条码 |
| **批量** | 单次 ≤50 SR（P0） |
| **审计** | dispatch / reset 记 operatorId |

---

## 8. 测试要点（业务视角）

- 登记后 INT-01 → 采样台可见 barcode
- DES-08 投影无 source → 拒绝
- INT-04 reset → ext notSent；WorkItem 仍在；再 INT-01 幂等
- 金域项：INT-01 后 LIS EXT 上传（联调 LisKingMed）
- PROOF/TEST 路由正确

---

## 9. 关联文档与变更历史

- SDD：[AS-PEIS-INT-001-SDD](../AppService-SDD/AS-PEIS-INT-001-LIS-PACS外发-SDD.md)
- 配置：[AS-PEIS-INT-002](./AS-PEIS-INT-002-外发集成配置.md)
- LIS/PACS INT：AS-LIS-INT-001、AS-PACS-INT-001
- DM：[DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md)、[DM-PEIS-001](./DM-PEIS-001-PeisServiceRequestExt-项目执行扩展.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-06-27 | §4.3 INT-04；BR-INT-05/06；Gap 表合规；链 skill/rule |
| v1.0 | 2026-06-27 | 首版 P0 外发 |

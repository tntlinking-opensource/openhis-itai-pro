---
name: peis-lis-pacs-doc-terminology
description: PEIS/LIS/PACS/金域外送文档与 AS-SDD 口径。写 docs/应用/zxplat-application/PeisAppService、AS-PEIS-*、DM-PEIS-*、DM-CLN-005/007/006/008、DM-CUS-013 时必读；避免「外发 LIS」与「院内不发申请」混淆。
---

# PEIS · LIS · PACS · 金域 文档口径

## 何时使用

- 新建或修订 **PEIS** AS / SDD / DM / 概要设计 / 功能清单
- 描述 PEIS 与 LIS/PACS/**LisKingMed** 协作
- 评审 Gap、院点排期、INT-01 链路

须与 `@PEIS-LIS-PACS文档口径`（`prompts/PEIS-LIS-PACS文档口径/SKILL.md`）及 AS/SDD 生成 SKILL 一并遵守。

## 术语对照（最高优先级）

| 用语 | 正确含义 | 错误含义（禁止） |
| --- | --- | --- |
| **外发 LIS**（院点口语） | **金域外送** → [LisKingMed](../../../zxplat-module-lis-kingmed/) EXT | 院内不发送 LIS 申请 |
| **INT-01 / PEIS 外发** | 创建 **本院** LisWorkItem / PacsWorkItem | 仅指金域 |
| **院内 LIS** | 常规采样/上机/审核；朝阳与师大均可 | 仅师大、或「无外发」 |

## 检验链路（写入 01 §5.2 / 04 §5 / INT AS §1.3）

```text
PEIS 登记/分科
  └─ INT-01（P0）→ 本院 LisWorkItem / PacsWorkItem
       └─ LIS AUD-05 发布 → projectFromLis → cln_observation（LIS 主路径）
       └─ PACS REP-07 审核通过 → projectFromPacsSummary → cln_observation（PACS 主路径）
       └─ 漏投影 → PEIS INT-03 / DES-08 补偿刷新
       └─ 项目标记外送金域
            └─ LisKingMed EXT → lis_observation → AUD-05 后同上投影
```

- PEIS **INT-01** 与金域 **不互斥**：先 WorkItem，再 EXT。
- **无** `peis_lis_project_map`；一体化同 **`activity_id`**。

## 观察与结论存储

| 事实 | 表 | 备注 |
| --- | --- | --- |
| LIS 检验真源 | `lis_observation` | 仅 LIS 写 |
| CLN 临床事实 | `cln_observation` | LIS：**AUD-05** `projectFromLis`；PACS：**REP-07** `projectFromPacsSummary`；INT-03/DES-08 补偿 |
| 分科结论 | `cln_observation` | OD.`categoryCode=dept_conclusion`；**禁止** `peis_dept_conclusion` |
| AD↔小项 OD | `cus_activity_observation_link` | [DM-CUS-013](../../health/DomainModel/DM-CUS-013-ActivityObservationLink-项目小项绑定.md) · `domain.custom.activityobservationlink` |
| 总检报告索引 | `cln_diagnostic_report_index` | MAS-04 final 时 upsert；事实表 `cln_diagnostic_report（DM-CLN-008） |

领域服务命名：**`ObservationDomainService`** / **`ObservationQueryService`**（无 `Cln` 前缀）；总检 DR：**`DiagnosticReportDomainService`**（`clinical.DiagnosticReport 包，与 LIS 同名类包隔离）。

## 院点 Profile（2026-06-27）

| Module | 首版院点 |
| --- | --- |
| LIS（本院） | 朝阳 + 师大 |
| 金域外送 | 师大 **9/1 前**；朝阳 **8 月二期**（无 PEIS） |
| PEIS / PACS / 小程序 / 自助机 | 师大 **2026-09-01** |

真源：[PeisAppService/01-概要设计.md §5](../../../health/PeisAppService/01-概要设计.md)

## AS / SDD 纪律

- **Gap 仅 AS §3**：QueryService 能力 / DM 能力 / 领域代码-文档不一致
- **禁止** Gap：「实现」「文档✅」「应用层未实现」「缺 SDD」
- SDD：**禁止 Gap 章**；HTTP **一律 POST** `/api/app/{camelCase}/{methodName}`
- AS 9 章 / SDD 8 章：见 `应用服务文档生成提示词模板` / `应用服务SDD文档生成提示词模板`

## 文档交叉引用

| 主题 | 路径 |
| --- | --- |
| PEIS 功能清单 | `docs/应用/zxplat-application/PeisAppService/00-功能清单.md` |
| 跨模块 D1～D9 | `01-概要设计.md` §7 |
| LIS 跨模块全文 | `docs/应用/zxplat-application/LisAppService/01-概要设计.md` §10 |
| 金域 EXT | `docs/zxplat-module-lis-kingmed/00-功能清单.md` |
| CLN 观察目录 | `DomainModel/DM-CLN-005-ObservationDefinition-临床观察定义.md` |
| CUS AD↔OD 链接 | `DomainModel/DM-CUS-013-ActivityObservationLink-项目小项绑定.md` |
| CLN 观察实例 + LIS 投影 | `DomainModel/DM-CLN-007-Observation-临床观察实例.md` |
| CLN 总检 DR 事实 | `DomainModel/DM-CLN-008-DiagnosticReport-临床总检报告.md` |
| DR 读侧索引 | `DomainModel/DM-CLN-006-DiagnosticReportIndex-报告读侧索引.md` |
| 登记编排 SDD | `AppService-SDD/AS-PEIS-REG-ORCH-登记编排-SDD.md` |
| PACS 审核投影 SDD | `AppService-SDD/AS-PACS-REP-002-报告审核-SDD.md` |
| Peis Extension（adapter 投影） | `PeisAppService/08-Extension与外部依赖.md` §3.2 |
| Flyway 索引 | `PeisAppService/06-Flyway-表结构.md` |

## 配置项措辞

`peis.reg.auto_dispatch`：**登记后自动触发 INT-01**（本院 WorkItem）；金域项由 LIS 侧 EXT 接续，**不是**「关闭 LIS 申请」。

**INT-002 外发集成配置页**：仅 **路由策略**（PROOF→LIS、TEST→PACS）与外购 **adapter 连接**；**禁止**项目对照 map；**金域外送**参数在 [LisKingMed](../../../zxplat-module-lis-kingmed/)，不在 PEIS 集成页重复。

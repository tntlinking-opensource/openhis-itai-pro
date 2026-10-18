# PeisAppService · 自研健康体检应用规划

> **定位**：ZXPlat 生态内 **健康体检管理（FR-PE / M-23）** 应用层真源；与 **booking H 线**、**LIS**、**PACS** 分 Module 协作。  
> **遗留参考**：`D:/1.project/sdpeis/src/server`（Spring Boot + MyBatis + Vue 管理端）  
> **目标代码**：`zxplat/health-application/peis` + `zxplat/zxplat-domain/peis`（CUS）  
> **说明**：编排为主，事实落在 health CLN/ADM/WKF；跨模块约定见 [01-概要设计 §7](./01-概要设计.md#7-lispacspeis-跨模块架构约定)  
> **编码**：按页面拆分 `AS-PEIS-*`（见 [03-AppService详细设计.md](./03-AppService详细设计.md)）

---

## 文档索引

| 序号 | 文档 | 用途 |
|------|------|------|
| **00** | [00-功能清单.md](./00-功能清单.md) | P0～P3 范围、IF 族 |
| **01** | [01-概要设计.md](./01-概要设计.md) | HLD、**§7 跨模块约定**、与 booking/LIS/Campus 边界 |
| **02** | [02-交互说明.md](./02-交互说明.md) | 菜单 ↔ AppService |
| **03** | [03-AppService详细设计.md](./03-AppService详细设计.md) | AS 索引 |
| **04** | [04-FHIR领域模型.md](./04-FHIR领域模型.md) | Encounter(PE)、`cln_observation`、领域 Gap |
| **05** | [05-遗留系统模块对照.md](./05-遗留系统模块对照.md) | sdpeis 对照 |
| **06** | [06-Flyway-表结构.md](./06-Flyway-表结构.md) | DDL 索引 → [zxplat-domain/migration/health](../../../zxplat/zxplat/zxplat-domain/src/main/resources/db/migration/health/) |
| **07** | [07-成码前Checklist.md](./07-成码前Checklist.md) | 文档→代码门禁 |
| **08** | [08-Extension与外部依赖.md](./08-Extension与外部依赖.md) | booking/收费/LIS 边界 |
| **10** | [10-团检单位树分组与名册选定方案.md](./10-团检单位树分组与名册选定方案.md) | 团检筹备定稿：单位树 / 分组多套餐 / 合同名册选定（待成码） |
| **booking·18** | [../../../模块/zxplat-module-booking/18-体检号源日历与预约-需求定稿.md](../../../模块/zxplat-module-booking/18-体检号源日历与预约-需求定稿.md) | 体检号源日历；**合同类型**字段与进池规则（peis 合同侧配合） |
| **迁移** | [PEIS迁移工具/](./PEIS迁移工具/) | old MySQL `peis` → ZXPlat PG（目录/客户） |
| **规范** | [PEIS 文档口径 SKILL](../../../../.cursor/skills/PEIS-LIS-PACS文档口径/SKILL.md) · [whale-peis-codegen rule](../../../../.cursor/rules/whale-peis-codegen.mdc) | 术语、Gap、成码 |
| **DM** | [docs/领域/DomainModel/DM-PEIS-*](./ | CUS 领域模型 |
| **DM-SDD** | [DomainModel-SDD/DM-PEIS-*](../DomainModel-SDD/) · [DM-CLN-005/007/006](../DomainModel-SDD/) | 领域成码契约 |
| **AS** | [AS-PEIS-INT-001](../AppService/AS-PEIS-INT-001-LIS-PACS外发.md) | P0 LIS/PACS 外发（无菜单） |
| **AS** | [AS-PEIS-REG-001](../AppService/AS-PEIS-REG-001-个检登记.md) | P0 个检登记 |
| **AS** | [AS-PEIS-CFG-001](../AppService/AS-PEIS-CFG-001-体检目录.md) | P0 体检目录 |
| **AS** | [AS-PEIS-DES-001](../AppService/AS-PEIS-DES-001-分科诊台.md) | P0 分科诊台 |
| **AS** | [AS-PEIS-MAS-001](../AppService/AS-PEIS-MAS-001-总检诊台.md) | P0 总检诊台 |
| **AS** | [AS-PEIS-REP-001](../AppService/AS-PEIS-REP-001-报告打印.md) | P0 报告打印 |
| [../LisAppService/](../LisAppService/) | 检验执行与报告（PEIS 外发，不重复实现） |
| [../PacsAppService/](../PacsAppService/) | 检查执行与报告（同上） |
| [../../../模块/zxplat-module-booking/00-功能清单.md](../../../模块/zxplat-module-booking/00-功能清单.md) | **H 线** 体检预约、到检签到、导检队列 |
| [../../../模块/zxplat-module-campus/](../../../模块/zxplat-module-campus/) | 校区/学院/部门、学生/教职工档案（PEIS 读 Reader） |
| [../../../openhis-pro-mini/](../../../openhis-pro-mini/) | 微信小程序患者端 |
| [../../../模块/zxplat-module-miniapp/](../../../模块/zxplat-module-miniapp/) | miniapp 后端 BFF |

---

## 一、遗留 PEIS 在做什么（摘要）

遗留系统是 **「前台登记 +（HIS 收费）+ 团检合同 + 分科/总检诊台 + LIS/PACS 对接 + 报告 + 微信」** 单体：

```text
预约/登记（个检/团检）
    │
    ▼
Encounter（class=PE，一次完整体检）
    │
    ├── ServiceRequest[]（套餐 ActivityDefinition 展开）
    │         ├── Observation[]（小项结果 → ObservationDefinition）
    │         └── LIS/PACS 外发 → LisWorkItem / PacsWorkItem
    │
    ├── HIS 收费/发票 ──► health ChargeItem（PEIS 只读 Reader）
    │
    ├── 分科诊台 ──► Observation + 分科结论（CUS / DR 章节）
    │
    ├── 总检诊台 ──► DiagnosticReport
    │
    └── 报告打印/发放 ReportController → PrintTemplate（AS-BAS-PRT）
```

**遗留 `peis_check_order*`** 迁移映射见 [04-FHIR领域模型.md](./04-FHIR领域模型.md) §8.4，**新开发不以 PeisCheckOrder 为聚合根**。

**核心表族**：`peis_check_order*`、`peis_customer*`、`sys_check_*`（项目/套餐/诊断）、`peis_lis/pacs_*`。  
遗留 `peis_charge_*` **不迁入** health-application/peis，收费事实用 health `ChargeItem`。

**部署裁剪**：东北师大院点前端大量菜单被注释（统计、主任查询、职业病、通用团检等），**ZXPlat 规划以全量遗留能力为母集**，按 P0～P3 分期，院点用 starter 开关裁剪。

---

## 二、ZXPlat 边界

### 2.1 与 booking（H 线）

| 能力 | booking | PEIS（health-application/peis） |
|------|---------|------------|
| 个检/团检 **预约占用**、时段容量 | ✅ | 只读衔接 |
| **到检签到**、导检队列 | ✅ 写侧 | 消费队列，创建/激活体检流水 |
| 套餐/项目目录（正式） | **`ActivityDefinition`** | booking 套餐行引用 AD |
| 导检路径、分科执行、总检、报告 | ❌ | ✅ FR-PE-003～006 |

### 2.2 与 LIS / PACS

| 能力 | LIS / PACS | PEIS（编排/外发） |
|------|------------|------------|
| 标本/检查 **执行态**、仪器结果、科室审核 | ✅ | ❌ |
| 体检项目 **INT-01 外发**（本院 WorkItem）、结果挂载体检单 | LIS/PACS Module | PEIS 编排；**金域外送**见 [LisKingMed](../../../模块/zxplat-module-lis-kingmed/) |
| 项目 **路由**（检验→LIS、检查→PACS、体格→分科） | LIS/PACS **Module 参数** + `ActivityDefinition.type_enum` | INT-01；**无** PEIS↔LIS 码表对照 |

### 2.3 与 health 核心

| 能力 | health | PEIS（AppService 编排） |
|------|--------|------------|
| 全院 `Patient` 主索引 | ✅ | Extension 引用 |
| **`Encounter(PE)` + `ServiceRequest`** | ✅ | 登记展开套餐/项目；诊台编排 |
| **`ActivityDefinition` / `ObservationDefinition`** | ✅ [DM-CLN-005](./DM-CLN-005-ObservationDefinition-临床观察定义.md) | 目录维护 AS-PEIS-CFG-001 |
| **`Observation` + `DiagnosticReport** | ✅ [DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md) / [DM-CLN-008](./DM-CLN-008-DiagnosticReport-临床总检报告.md) | 分科/总检 AppService |
| **`ChargeItem` 收费/退费/发票/折扣** | ✅ | **只读 Reader**；登记后跳转 health 收费 AS |
| **费用/营收/发票报表** | ✅ health + report | ❌ |
| 框架用户/权限/机构 | whale-framework | PEIS 业务菜单与数据 ACL |

---

## 三、领域锚点

详见 **[04-FHIR领域模型.md](./04-FHIR领域模型.md)**。摘要：

| 层级 | 模型 |
|------|------|
| **目录** | `ActivityDefinition`（套餐/项目）+ **[DM-CLN-005](./DM-CLN-005-ObservationDefinition-临床观察定义.md)**（小项 OD） |
| **一次体检** | **`Encounter(class=PE)`** + 多模型进度（不扩 status_enum） |
| **运行时** | `ServiceRequest` → [DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md) → [DM-CLN-008](./DM-CLN-008-DiagnosticReport-临床总检报告.md) |
| **PEIS CUS** | 项目执行扩展、常见结果、表达式 |
| **外发** | 同进程 **ServiceRequest → Lis/Pacs OrderIngress**；booking 团检/预约 |

---

## 四、AppService 与菜单（1:1 原则）

**不使用单一 `PeisAppService`**。核心 **14 个主页面** + 2 个无页面服务；**微信** 在 [openhis-pro-mini](../../../openhis-pro-mini/) + miniapp；**学生/教职工** 在 Campus；**收费** 归 health；**职业病** 暂不规划。

详见 [03-AppService详细设计.md](./03-AppService详细设计.md)、[00-功能清单.md](./00-功能清单.md)。

---

## 五、实施顺序建议

1. **文档**：01 功能清单 → 00 AS 清单 → **04 菜单映射** → 03 FHIR 领域模型  
2. **P0 AS**：REG-001、DES-001、MAS-001、INT-001、CFG-001/003  
3. **领域**：**DM-ADM-002 `Encounter(PE)` class**（**不**扩 status）+ **`PeisEncounterProgressQuery`** + [DM-CLN-005 OD](./DM-CLN-005-ObservationDefinition-临床观察定义.md)；见 [07-Checklist](./07-成码前Checklist.md)  
4. **P1**：GRP-001、REP-001/002、INT-002、CRT-001  
5. **P2+**：体检业务统计  
6. **并行**：health 体检收费 AS、Campus 档案 Reader（与 REG/GRP 联调）

---

## 六、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-19 | 首版：基于 sdpeis 遗留梳理；对齐 booking FR-PE 边界 |
| v1.1 | 2026-06-19 | 费用统一归 health 主程序；PEIS 不建 FIN AS |
| v1.2 | 2026-06-19 | [04-FHIR领域模型.md](./04-FHIR领域模型.md)：Encounter(PE) 中心化 |
| v1.4 | 2026-06-27 | 链 `.cursor` PEIS rule/skill；金域 vs INT-01 口径 |
| v1.3 | 2026-06-19 | 范围收敛：BAS→Campus；移动→openhis-pro-mini；多模型进度；核心 14 AS |

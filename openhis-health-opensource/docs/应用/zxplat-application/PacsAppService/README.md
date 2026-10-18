# PacsAppService · 自研 PACS 应用规划

> **定位**：在 ZXPlat 生态内 **自研影像信息系统（PACS/RIS）** 的应用层真源；与 **LIS** 分 Module 规划（见 [LisAppService](../LisAppService/)）。  
> **遗留参考**：`D:/0.product/pacs/src`（Spring MVC + MyBatis + `YGZZ_HZXX` 核心表）  
> **目标代码**：`zxplat/health-application/pacs` + `zxplat/zxplat-domain/pacs`  
> **跨模块约定**：[01-概要设计 §7](./01-概要设计.md#7-lispacspeis-跨模块架构约定)（LIS 全文见 [LisAppService/01 §10](../LisAppService/01-概要设计.md#10-lispacspeis-跨模块架构约定)）  
> **编码**：按页面拆分 `AS-PACS-*`（见 [03-AppService详细设计.md](./03-AppService详细设计.md)）

---

## 文档索引

| 文档 | 用途 |
|------|------|
| **[01-概要设计.md](./01-概要设计.md)** | HLD、**§7 跨模块约定** |
| **[05-遗留系统模块对照.md](./05-遗留系统模块对照.md)** | `pacs/src` Controller/Service → ZXPlat 分层对照 |
| **[03-AppService详细设计.md](./03-AppService详细设计.md)** | **10 个核心页面 AppService + INT**；扩展见 [zxplat-module-pacs-imaging](../../../模块/zxplat-module-pacs-imaging/) |
| **[AS-PACS-INT-001](../AppService/AS-PACS-INT-001-HIS接单入口.md)** | HIS 接单（与 LIS 对齐，直调 DomainService） |
| **[AS-PACS-REG-001](../AppService/AS-PACS-REG-001-登记工作台.md)** | P0 登记工作台 |
| **[AS-PACS-EXE-001](../AppService/AS-PACS-EXE-001-检查执行.md)** | P0 检查执行 |
| **[AS-PACS-REP-001](../AppService/AS-PACS-REP-001-报告书写.md)** / **[REP-002](../AppService/AS-PACS-REP-002-报告审核.md)** | P0 报告链路 |
| **[AS-PACS-CFG-001](../AppService/AS-PACS-CFG-001-模态与可执行项目.md)** | P0 模态能力（D3 无 map） |
| **[AS-PACS-INT-001-SDD](../AppService-SDD/AS-PACS-INT-001-HIS接单入口-SDD.md)** | INT 实现 SDD |
| **[02-交互说明.md](./02-交互说明.md)** | 核心 10 菜单 + 扩展 3 菜单 |
| **[../../../模块/zxplat-module-pacs-imaging/](../../../模块/zxplat-module-pacs-imaging/)** | **影像采集与阅片**增值 Module（3 菜单 / 3 AS） |
| [../../../模块/zxplat-module-pacs-imaging/04-第三方组件选型.md](../../../模块/zxplat-module-pacs-imaging/04-第三方组件选型.md) | OHIF / Orthanc / DICOMweb 选型真源 |
| **[00-功能清单.md](./00-功能清单.md)** | P0～P3 功能范围、优先级、与遗留模块映射 |
| [04-FHIR领域模型.md](./04-FHIR领域模型.md) | **`PacsWorkItem`** 聚合、影像、FHIR 映射 |
| [AS-OP-DOR-003](../AppService/AS-OP-DOR-003-门诊医生站医嘱开立.md) | HIS 侧签发、外发触发 |
| [模块化开发规范.md](../../01-架构/模块化开发规范.md) | `whale-{mod}` 六层 + starter |
| [LisAppService](../LisAppService/) | 检验 Module（共用 `ServiceRequest`） |

---

## 一、遗留 PACS 在做什么（摘要）

遗留系统是 **「HIS 接口 + RIS 工作台 + 工作站/阅片 + DICOM/PDF」** 单体：

```text
HIS ──WebService──► register / sendApply / concel / getInfo / reportPath / echPacs*
                         │
                         ▼
                    YGZZ_HZXX（患者检查主表，APPLYNO/JCZT/STUUID/XDID…）
                         │
        ┌────────────────┼────────────────┐
        ▼                ▼                ▼
   登记 list        待摄/已摄 dsplist   报告 Report.jsp
        │                │                │
        ▼                ▼                ▼
 WorkStationServer   （状态确认）        审核 passSh/backSh
        │                              │
        ▼                              ▼
   [zxplat-module-pacs-imaging 扩展]              PDF 发布
   DICOM 采集 / Web 阅片
```

**核心表**：`YGZZ_HZXX`、`WBS_LOG`、`ZL_DIC` / `ZL_ZX_DIC`、报告模板、设备参数。  
`SYS_DS_URL`、DICOM 索引归 **zxplat-module-pacs-imaging** 扩展。

**检查状态 `JCZT`**（简化）：`0` 登记 → `1` 待摄 → `2`/`4` 已检 → 报告书写/审核 → 发布。

---

## 二、ZXPlat 分层：domain 放什么、application 放什么

### 2.1 原则

| 层 | 放什么 | 不放什么 |
|----|--------|----------|
| **zxplat-domain**（开源核心） | `Encounter`、`Patient`、`ServiceRequest`、`ActivityDefinition`、`ChargeItem` | `YGZZ_HZXX` 检查执行态、DICOM 原始存储 |
| **pacs-domain**（可售 Module） | **`PacsWorkItem`**、**`ImagingStudy`**、**`DiagnosticReport**、**`Media`**、**`DocumentReference`** | 患者主索引、医嘱开立 |
| **health-application** | 医生站/护士站/收费 | 影像科工作台（归 pacs-application） |
| **pacs-application** | RIS 核心 AppService（见 03-AppService详细设计） | DICOM 协议（imaging-adapter） |
| **pacs-adapter** | `WorkStationServer` 报告/状态、`/webservice/*` | 影像采集、WebPacs |
| **PrintTemplate（AS-BAS-PRT）** | 报告 **版式** PDF | 影像语义、Study UID |

### 2.2 与 HIS 的衔接

```text
医生站 IF-DOR-15 签发检查
    │
    ▼
DoctorStationAdviceAppService.sign()
    │  注入 PacsWorkItemDomainService → createWorkItem()
    │  分组：PacsOrderIngressOrchestrator（support 类）
    │
    ▼
zxplat-domain/pacs：PacsWorkItem + orderLinks
    │
    ▼
登记 / 执行 / 报告 AppService…
    │
    ▼
报告 final → cln_diagnostic_report_index（DM-CLN-006）
```

**外购/现场 PACS（朝阳）**：上链 INT 之后由 **pacs-adapter** 对接现场系统；adapter 内私有码表，不进 health Flyway。

**关键**：`ServiceRequest` 仍是 HIS **医嘱事实**；**`PacsWorkItem`** 是 PACS **检查工单**（登记号/检查号维度），通过 `orderLinks` 关联 SR，**不复制**患者与收费。详见 [04-FHIR领域模型.md](./04-FHIR领域模型.md)。

### 2.3 建议 Maven 结构

```text
zxplat/
  zxplat-domain/pacs/          PacsWorkItem、ImagingStudy、pacs_diagnostic_report…
  zxplat-domain/clinical/      cln_diagnostic_report_index
  health-application/pacs/     Pacs*AppService
  pacs-adapter/                含现场 PACS 对接（朝阳首版）
  pacs-starter/
```

> 均在 **zxplat** 子包；**废止**独立 `whale-pacs` 根模块表述（见 [01 §7.4](./01-概要设计.md#74-maven-落点d4)）。

`health-app`：`pacs-starter` + 可选 `imaging-starter`。

---

## 三、AppService 与菜单（1:1）

**不使用单一 `PacsAppService`**。核心 **10 个主页面** + INT；**影像采集/阅片** 见 [zxplat-module-pacs-imaging](../../../模块/zxplat-module-pacs-imaging/)（+3 菜单）。不做会诊、医共体、外检、对账、统计、机构菜单。

详见 [02-交互说明.md](./02-交互说明.md)、[03-AppService详细设计.md](./03-AppService详细设计.md)。

| 菜单分组 | 页面数 | 代表 AS |
|----------|--------|---------|
| 检查业务 | 5 | REG-001、EXE-001、REP-001/002 |
| 检查配置 | 4 | CFG-001 项目、CFG-004 参数 |
| 运营 | 1 | LOG-001 |
| 影像扩展（可选） | 3 | IM-001 采集、IM-002 阅片 |

---

## 四、实施顺序建议

1. **文档**：04 菜单定稿 → **03-AppService详细设计** 逐份写 AS → SDD  
2. **P0 AS**：INT-001、REG-001、EXE-001、REP-001/002、CFG-001/004  
3. **领域**：`PacsWorkItem` + DiagnosticReport  
4. **可选**：[zxplat-module-pacs-imaging](../../../模块/zxplat-module-pacs-imaging/) IM-001～003  

---

## 五、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-18 | 首版：遗留分析、domain/application 边界、18 菜单 AS |
| v1.1 | 2026-06-18 | 移除会诊；**17** 菜单 AS |
| v1.2 | 2026-06-18 | 移除医共体 |
| v1.3 | 2026-06-18 | 核心 **10** 菜单；[zxplat-module-pacs-imaging](../../../模块/zxplat-module-pacs-imaging/) 扩展 |

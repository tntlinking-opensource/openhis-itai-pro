# LisAppService · 自研 LIS 应用规划

> **定位**：在 ZXPlat 生态内 **自研检验信息系统（LIS）** 的应用层真源；与 **PACS** 分模块规划（见文末）。  
> **遗留参考**：`D:/0.product/lis/src`（Spring MVC + MyBatis + `l_laborder` 核心表）  
> **目标代码**：`zxplat/health-application/lis` 子包 + `zxplat/zxplat-domain/lis` 子包
> **编码**：按页面拆分 `AS-LIS-*`（见 [03-AppService详细设计.md](./03-AppService详细设计.md)）· 本文档目录索引

---

## 文档索引

| 文档 | 用途 |
|------|------|
| **[00-功能清单.md](./00-功能清单.md)** | P0～P3 功能范围、优先级、与遗留模块映射 |
| **[01-概要设计.md](./01-概要设计.md)** | HLD、与 PACS/PEIS 边界、**§10 跨模块架构约定** |
| **[05-遗留系统模块对照.md](./05-遗留系统模块对照.md)** | `lis/src` → ZXPlat 分层对照 |
| **[03-AppService详细设计.md](./03-AppService详细设计.md)** | **21 个页面 AppService + INT** 契约索引 |
| **[02-交互说明.md](./02-交互说明.md)** | 18+2+1 菜单、路由、遗留 62 List 合并规则 |
| **[../../../模块/zxplat-module-lis-kingmed/](../../../模块/zxplat-module-lis-kingmed/)** | **金域外送**增值 Module（2 菜单 / 2 AS） |
| **[../../../模块/zxplat-module-lis-collector/](../../../模块/zxplat-module-lis-collector/)** | **仪器采集**增值 Module（C# Agent + 动态脚本） |
| **[../PacsAppService/](../PacsAppService/)** | **自研 PACS** 应用规划（与 LIS 分 Module） |
| [04-FHIR领域模型.md](./04-FHIR领域模型.md) | **`LisWorkItem`** 聚合、合管、FHIR 映射 |
| [../../../模块/zxplat-module-crb/05-whale-lis对接说明.md](../../../模块/zxplat-module-crb/05-whale-lis对接说明.md) | **CRB 传染病推送**（检验发布事件 + 读 Reader） |
| [AS-OP-DOR-003](../AppService/AS-OP-DOR-003-门诊医生站医嘱开立.md) | HIS 侧签发、外发触发 |
| [模块化开发规范.md](../../01-架构/模块化开发规范.md) | `whale-{mod}` 六层 + starter |
| [朝阳 Checklist](../../吉林朝阳中医院/00-上线前Checklist.md) | 院点验收 |

---

## 一、遗留 LIS 在做什么（摘要）

遗留系统是典型的 **「HIS 接口 + 检验科工作台 + 仪器 ingress + 报告审核打印」** 单体：

```text
HIS ──WebService──► register / sendApply / cancelApply / getInfo / PDF
                         │
                         ▼
                    l_laborder（检验申请主表，BARCODE/SAMPLENO/RECEIVEFLAG…）
                         │
        ┌────────────────┼────────────────┐
        ▼                ▼                ▼
   采样台 cxt      样本状态 sjgl     结果审核 dyyl
        │                │                │
        ▼                ▼                ▼
   仪器通道 yqtd   InstrumentWS     报告 PDF / 模板
        │                              │
        ▼                              ▼
   危急值 wjz                    历史报告 lsbg
```

**核心表**（从 MyBatis 推断）：`l_laborder`、`L_LABORDER`（明细）、`l_critical`、`L_BAOBIAO`（报告模板）、`ZL_DIC` / `ZL_ZX_DIC`（诊疗/子项字典）、`WBS_LOG`（接口日志）。

---

## 二、ZXPlat 分层：domain 放什么、application 放什么

### 2.1 原则

| 层 | 放什么 | 不放什么 |
|----|--------|----------|
| **zxplat-domain**（开源核心） | `Encounter`、`Patient`、`ServiceRequest`、`SpecimenDefinition`、`ActivityDefinition`、`ChargeItem` | `l_laborder` 检验执行态、仪器原始报文 |
| **zxplat-domain/lis** | **`LisWorkItem`**、**`Observation`**、**`DiagnosticReport**、**`Device`**、危急值、质控 | 患者主索引、医嘱开立逻辑 |
| **health-application** | 医生站/护士站/收费（已有） | 检验科大工作台（归 health-application/lis） |
| **health-application/lis** | **按页面拆分 AppService**（见 03-AppService详细设计） | HTTP 仪器协议（lis-adapter） |
| **lis-adapter** | 仪器 HL7/ASTM、旧版 WebService 兼容、Rabbit 消费者 | 业务状态机 |
| **PrintTemplate（AS-BAS-PRT）** | 报告 **版式** PDF（可逐步替代 xhtml 模板） | 结果医学语义 |

### 2.2 与 HIS 的衔接（自研 LIS 后）

```text
医生站 IF-DOR-15 签发检验
    │
    ▼
DoctorStationAdviceAppService.sign()
    │  注入 LisWorkItemDomainService → createWorkItem()
    │  合管分组：LisOrderIngressOrchestrator（support 类）
    │
    ▼
zxplat-domain/lis：LisWorkItem 创建（orderLinks 关联 serviceRequestId + encounterId）
    │
    ▼
检验科 LisAppService 工作台…
    │
    ▼
报告发布 → health 侧 DiagnosticReportIndex / DM-CLN-006 DiagnosticReportIndex

**朝阳院点（H22010402403）**：7/30 本期为 **本院 LIS Gateway + 报告回传**；**金域外检**（`zxplat-module-lis-kingmed`）**预计 2026-08 二期**，见 [朝阳 Checklist](../../吉林朝阳中医院/00-上线前Checklist.md)。
```

**关键**：`ServiceRequest` 仍是 HIS **医嘱事实**；**`LisWorkItem`** 是 LIS **检验工单**（对外 FHIR=`Specimen`），通过 `orderLinks` 关联 SR，**不复制**患者与收费。详见 [04-FHIR领域模型.md](./04-FHIR领域模型.md) §8.3。

### 2.3 建议 Maven 结构（与定稿一致）

```text
zxplat/
  zxplat-domain/lis/                     LisWorkItem、lis_observation、Device…
  zxplat-domain/clinical/                cln_diagnostic_report_index
  health-application/lis/                Lis*AppService（见 03-AppService详细设计）
  lis-adapter/
  lis-starter/
```

> LIS/PACS/PEIS 均在 **zxplat** 子包（见 [01-概要设计 §10](./01-概要设计.md#10-lispacspeis-跨模块架构约定)）。

`health-app` 装配：`lis-starter` + `zxplat.modules.lis.enabled=true`。

---

## 三、AppService 与菜单（1:1）

**不再使用单一 `LisAppService`**。核心 **18 个主页面**各 1 个 AppService；金域增值 **2 个**。（INT-01 接单不走独立 AppService，由医生站直接注入 `LisWorkItemDomainService`。）

详见 [02-交互说明.md](./02-交互说明.md)、[03-AppService详细设计.md](./03-AppService详细设计.md)。

| 菜单分组 | 页面数 | 代表 AS |
|----------|--------|---------|
| 检验业务 | 7 | SMP-001、AUD-001、**REP-001**（报告管理） |
| 检验配置 | 7 | CFG-003 仪器、CFG-004 合管 |
| 质控+运营 | 4 | QC-001、LOG-001、RPT-* |
| 金域外送（可选） | 2 | EXT-001/002 |

---

## 四、实施顺序建议

1. **文档**：04 菜单定稿 → **03-AppService详细设计** 逐份写 AS → SDD  
2. **P0 AS**：INT-001、SMP-001、AUD-001、**REP-001**、CFG-003/004/007  
3. **领域**：`LisWorkItem` + Observation + DiagnosticReport  
4. **金域**（可选，**朝阳 8 月二期**）：[zxplat-module-lis-kingmed](../../../模块/zxplat-module-lis-kingmed/) EXT P1  
5. **PACS**（并行）：[PacsAppService](../PacsAppService/) INT → REG → REP P0  

---

## 五、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-18 | 首版：遗留分析、domain/application 边界、Module 建议 |
| v1.1 | 2026-06-18 | 定稿 LisWorkItem 方案 B |
| v1.2 | 2026-06-18 | 多 AppService、金域 Module、REP 补齐 |
| v1.5 | 2026-06-19 | 朝阳院点：金域外检 8 月二期 |
| v1.4 | 2026-06-19 | 增加 CRB 对接文档链接 |
| v1.6 | 2026-07-11 | 金域文档目录迁至 `docs/模块/zxplat-module-lis-kingmed/`；代码目标 `zxplat/zxplat-module-lis-kingmed/` |
| v1.4 | 2026-07-11 | 金域文档路径统一为 `docs/模块/zxplat-module-lis-kingmed/` |
| v1.3 | 2026-06-18 | 金域路径迁至 `docs/模块/zxplat-module-lis-kingmed/`；增加 PacsAppService 链接 |

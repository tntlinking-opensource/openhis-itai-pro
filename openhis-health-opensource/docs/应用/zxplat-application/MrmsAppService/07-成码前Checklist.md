# MrmsAppService · 成码前 Checklist

> **版本**：v1.0 · **日期**：2026-06-27  
> **用途**：MRMS 文档 → 代码会话 **门禁索引**；成码前逐项勾选。  
> **规范**：[`zxplat（病案/MRMS）-codegen.mdc`](../../../../.cursor/rules/zxplat（病案/MRMS）-codegen.mdc) · [`代码生成通用规范`](../../../../.cursor/skills/代码生成通用规范/SKILL.md)

---

## 1. 文档真源（须齐）

| 类别 | 路径 | Wave |
|------|------|------|
| 功能清单 | [00-功能清单.md](./00-功能清单.md) | 全 Wave |
| 概要设计 | [01-概要设计.md](./01-概要设计.md) | 全 Wave |
| **交互说明** | [02-交互说明.md](./02-交互说明.md) | **成码主入口** |
| 首页 LLD | [03-病案首页-详细设计.md](./03-病案首页-详细设计.md) | 1 |
| 质控/归档 LLD | [04-归档与质控-详细设计.md](./04-归档与质控-详细设计.md) | 2 |
| Extension 索引 | [08-Extension与外部依赖.md](./08-Extension与外部依赖.md) | 2+ |
| 包结构 ADR | [模块化开发规范.md §7](../01-架构/模块化开发规范.md) | 全 Wave |
| 文档清单 | [09-文档清单.md](./09-文档清单.md) | 维护用 |

---

## 2. 领域 DM

| DM | 说明 | 成码包 |
|----|------|--------|
| [DM-MRMS-001](./DM-MRMS-001-CaseRecord-病案主档.md) | 主档 + 首页 + 子表 | `domain/mrms/caserecord/` |
| [DM-MRMS-002](./DM-MRMS-002-QcDefect-质控与缺陷.md) | 质控规则/缺陷/批次 | `domain/mrms/qc/` |

---

## 3. Wave 1 验收（已完成）

- [x] `domain/mrms/caserecord/` 聚合分包（无 `support/`）
- [x] 事件迁 `domain.share.events.mrms.caserecord`
- [x] `MrmsCaseRecordHomepage/Coding` AppService（列表/查档已并入 Homepage）
- [x] Flyway V77～V78
- [x] `mvn compile -pl zxplat/health-app -am -DskipTests`

---

## 4. Wave 2 验收（后端）

- [x] Flyway V80～V83（质控表 + 种子 + 逾期日志）
- [x] `domain/mrms/qc/` + `QcRuleExecutor` + 缺陷闭环
- [x] `MrmsCaseRecordQcAppService`（startQc / archive / listDefects …）
- [x] `EmrDocumentExtension` + `EmrDocumentExtensionImpl`
- [x] 归档 EMR 锁定：`MrmsCaseRecordQcAppService.archive()` + `IntegrationAfterCommitSupport`（已删 `CaseRecordArchivedEmrListener`）
- [x] `MrmsCaseRecordVolumeAppService` + `VolumePdfComposer`
- [x] `StaffNotificationExtension` + `CaseRecordOverdueScanJob`
- [x] 质控/卷宗 **前端页**（openhis-pro-ui）
- [x] `-Pwith-emr` 联调卷宗 PDF + 归档锁定（待环境验证）
- [x] 首页内 **质控 Tab**（医生标记已改 / 病案室只读）

---

## 4.1 2026-08-13 首页/登记/诊断后补（文档已对齐）

- [x] `in_way_code` 与医保 **adm_way** 同口径 **1/2/3/9**（Flyway **V28**；`MrmsConstants.mapInWayCodeToAdmWayCode`）
- [x] 列表 `insuranceTypeText`：合同名·险种短标（市医保·职工）
- [x] 首页诊断：`DiagnosisPickerDialog` **pickOnly**（院点 override 同步）；仅 `saveHomepage` 落库
- [x] **BR-IP-DOD-007** 修订：住院诊断允许出院后补（医嘱可写态门控不变）
- [x] 关联文档：AS-IP-DOR-002 / SDD、AS-IP-FIN-001 / SDD、DM-ADM-002、MrmsAppService 00/02/03/05/06

---

## 4. Wave 3 验收（借阅 / 4401）

- [x] Flyway V84～V86 + V25（借阅/上传表 + **菜单归拢**）
- [x] `domain/mrms/borrowrequest/` + `MrmsCaseRecordBorrowAppService`
- [x] `domain/mrms/upload/` + `CaseRecord4401Assembler` + `MrmsCaseRecordUploadAppService`
- [x] Extension：`EmrDocumentExtension` / `MedcasHomepageUploadExtension` / `StaffNotificationExtension` / `EmrAdmissionConditionExtension`
- [x] 4401 中性 DTO `MedcasHomepagePayload` + `MedcasInfCodes.MEDCAS_HOMEPAGE`（非 `chs-jilin-domain` 依赖）
- [x] **手动上传**：归档不自动写 `chs_upload_status_code`；`queryUploadPage(status=pending)` 含 null/pending/failed
- [x] `MrmsCaseRecordQcAppService.unarchive`（解档 + 可选 EMR 解锁）
- [x] 借阅 / 4401 **前端页**（`views/mrms/borrow` + `chs/medcas-ops`）
- [ ] 4401 现场联调（需 chs-jilin-starter + 有效 mdtrt_id / psn_no）

---

## 4.2 阶段收口（2026-08-13）

> **院内病案首页主路径已齐**（列表/建档/编辑/AutoFill/诊断 pickOnly/费用段/临床侧栏）。  
> **下一阶段全力上传**：见 [10-病案首页交付任务清单.md](./10-病案首页交付任务清单.md) §6～§8（隐性 Gate、4401 Diff/联调、4101A/4102/4103、一键编排）。  
> 不再在院内首页扩无关功能；字段补缺仅服务上传 Diff。

| 块 | 状态 |
|----|------|
| §2 建档列表 · §3 去 mock · §5 AutoFill | 已齐 |
| §4 表单存取主干 | 已齐；§4.3/4.4 上传对照项待 Diff |
| §6～§8 上报 | **进行中 / 下一阶段** |
| §0 DoD D4～D11 | 未完成（随上传） |

---

## 5. 成码硬规则（摘要）

| # | 规则 |
|---|------|
| R1 | 一页面一主 AppService（见 02-交互说明 R1） |
| R2 | 全部 `POST /api/app/{serviceName}/{methodName}` |
| R3 | AppService 禁直注 `ICurrentUser` → `MrmsOperatorResolver` |
| R4 | 读侧列表用 `*QueryModel`，AutoFill 中间结构用 `caserecord/vo/*Snapshot` |
| R5 | 跨 EMR/CHS 只经 Extension；禁止 application 直 import emr/chs entity（ExtensionImpl 除外） |
| R8 | 禁止 `health-bridge-*` / `*Bridge` 作为跨 Module 集成名；domain SPI 用 `*Adapter` |
| R6 | 字符串工具 `com.zxplat.framework.core.application.util.StringUtils` |
| R7 | 注释遵守 `comments-gate`；AppService public 方法 Gate/Pre/Cmd/Asm |

---

## 6. 编译

```powershell
cd D:\0.product\02.Whale\zxplat
mvn compile -pl zxplat/health-app -am -DskipTests
mvn compile -Pwith-emr -pl zxplat/health-app -am -DskipTests
```

---

## 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-06-27 | Wave 3 借阅/4401 验收项 |
| v1.2 | 2026-08-13 | 阶段收口：院内首页已齐；下一阶段全力上传（见 10 §6～§8） |

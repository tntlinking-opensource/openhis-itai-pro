# 学生健康档案 · SDD

> AS-PEIS-ARCH-001-SDD · v1.1 · 2026-07-25

| 项 | 约定 |
|----|------|
| 类名 | `PeisHealthArchiveAppService` / `PeisExamHistoryAppService` |
| HTTP | `POST /peisHealthArchive/{method}` · `POST /peisExamHistory/{method}` |
| IF-01 | `loadArchive`（`includeCompleteness` 可选） |
| IF-02 | `saveArchive` |
| IF-03 | `checkArchiveCompleteness` |
| HIST-01 | `listPatientExamHistory` |

通用字段存 `peis_patient_health_archive`；学籍/工号解析委托 `PeisCampusIdentityPort`（Campus 模块实现）；历年行来自 PE Encounter + ext.snapshot.examKind + DiagnosticReport.final。

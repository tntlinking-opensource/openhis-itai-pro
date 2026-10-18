# 体检域 · 业务统计 · 应用服务设计（AS）

> **编码**：AS-PEIS-RPT-001 · **版本**：v1.0 · **日期**：2026-06-27  
> **类名**：`PeisStatisticsAppService` · **路由**：`/peis/statistics`

---

## 1. 场景概述

科室/医生/总检工作量、阳性/疾病统计、历年对比；**不含**财务营收（归 health）。

### 1.6 BR

| BR-RPT-01 | 进度态走 PeisEncounterProgressQuery | RPT-01 |

---

## 2. 接口总览

| IF-PEIS-RPT-01～03 | P2 |

---

## 3. Gap

| GAP-RPT-01 | PeisEncounterProgressQuery | P2 |

---

## 9. 关联

- [04 §3.2](../PeisAppService/04-FHIR领域模型.md)

| v1.0 | 2026-06-27 | 首版 P2 |

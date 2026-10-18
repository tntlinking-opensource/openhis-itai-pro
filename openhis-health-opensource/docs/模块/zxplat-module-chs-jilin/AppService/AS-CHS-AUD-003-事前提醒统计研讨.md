# 事前提醒统计研讨 · 应用服务设计（AS）

> **页面边界**：医保办按医生/时间等条件只读查询已落库的 3101 违规，供质控研讨。**在内** 统计 IF；**在外** 试调（AUD-001）、主路径门禁（AUD-002）、平台 3101/3103 出站。
>
> **编码**：AS-CHS-AUD-003 · **版本**：v0.1 · **日期**：2026-07-24  
> **LLD**：[25-明细审核31xx-详细设计.md](../25-明细审核31xx-详细设计.md) §2.5.5

---

## 1. 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 分页查 `yb_detail_audit_hit`；汇总条；明细抽屉 `hit_line` |
| **在外** | 调 3101/3103；Excel 导出（可选后续）；收费主路径 |
| **数据源** | `yb_detail_audit_hit` / `hit_line` / `feedback` |
| **路由** | `/chs/detail-audit-stats` · `/api/app/insuranceDetailAuditStats/*` |

## 2. 接口概览

| IF 编号 | 语义 | HTTP 方法 |
|---------|------|-----------|
| IF-CHS-AUD-S-01 | 分页 + 汇总 | `pageHits` |
| IF-CHS-AUD-S-02 | 违规明细行 | `listHitLines` |

## 3. 关键业务规则

| 规则 | 说明 |
|------|------|
| 时间窗 | `createdFrom`/`createdTo` 必填；最长 93 天 |
| 筛选 | 医生编码/姓名、科室、trig_scen、sev_deg、末次 dspo_way、规则/内容关键字 |
| 只读 | 不调平台；不写反馈 |
| 末次处置 | 按 `feedback.jr_id` 最新一条回填 `latestDspoWay` |

## 4. 关联

- AppService：`InsuranceDetailAuditStatsAppService`
- Domain：`YbDetailAuditStatsQueryService`
- 前端：`/chs/detail-audit-stats` · 菜单 V19

# 医保文件通知 · 领域模型（DM）

> **编码**：DM-CHS-CAT-005 · **版本**：v0.1 · **日期**：2026-07-14  
> **状态**：目标态（支撑 AS-CHS-CAT-005；尚未成码）  
> **关联方案**：[24-医保文件与目录通知管理-方案.md](../24-医保文件与目录通知管理-方案.md)  
> **关联 AS**：AS-CHS-CAT-005

---

## 1. 聚合与边界

| 聚合/对象 | 职责 |
|-----------|------|
| CatalogNoticeDoc | 医保文件头：种类、品类、落地包、解析/应用状态 |
| CatalogNoticeLine | 明细行：动作、编码、限价、等级、payload |
| NationalCatalogNoticeOverlay | 镜像叠加：可用、终止日、patch |
| ServiceProjectMap | 01* ↔ 国家码映射 |
| NoticeComplianceReport | 最近一次合规校验结果 |
| CatalogNoticeApplyLog | 应用/回写审计（含未收费单据引用） |

**不在本域：** 13xx 镜像主表写入（CAT-002）、3301 上传、结算开单编排（仅提供门禁查询）。

---

## 3. 实体字段（摘要）

| 表/实体 | 关键字段 | 取值约束 |
|---------|----------|----------|
| yb_catalog_notice_doc | doc_kind | `CATALOG_DELTA` / `PAYMENT_MAP` |
|  | category | DRUG/SERVICE/SERVICE_F/DEVICE/FORMULA/HERBAL/PREP/OTHER |
|  | parse_status | PENDING/PARSED/PARSE_FAILED/PARTIAL |
|  | apply_status | NOT_APPLIED/APPLIED/REVOKED |
| yb_catalog_notice_line | action | ADD/STOP/MODIFY/PROVINCE_MODIFY/SET_PAY_CLASS/UNKNOWN |
|  | chrgitm_lv | `1`甲 / `2`乙 / `3`自费 |
|  | price_limit_amt | ≥0；空或≤0 表示无限价 |
|  | line_status | ACTIVE/SUPERSEDED |
| yb_national_catalog_notice_overlay | notice_avali_flag | `1`/`0` |
|  | notice_patch_json | 含 chrgitm_lv、hilist_pric_uplmt_amt、overlmt_dspo_way |
| yb_service_project_map | status | MAPPED/PENDING |
| yb_catalog_notice_todo | todo_type | STOP / PAY_CLASS |
|  | status | OPEN / CLOSED |

---

## 5. 不变式与事件

| ID | 规则 |
|----|------|
| INV-1 | 不因本域应用直接改写 `yb_national_catalog_item` 平台主列；叠加仅写 overlay |
| INV-2 | `01*` 不得写入假 hilist 冒充国家码 |
| INV-3 | STOP 生效日起对命中医保结算行硬阻断（门禁） |
| INV-4 | 有效限价下单价 `>` 限价硬阻断；无限价放行并标记 UNKNOWN |
| INV-5 | SET_PAY_CLASS 应用时同步改院内目录 lv 与未收费明细 lv；已收费明细不改 |

| 事件 | 触发 | 消费方语义 |
|------|------|------------|
| CatalogNoticeImported | 确认入库成功 | 可选通知/审计 |
| CatalogNoticeApplied | 应用成功 | 刷新选码可用；待办重算 |
| CatalogNoticeComplianceRan | 校验完成 | 报告页/下载页条 |

---

## 6. 领域服务方法（写侧）

| 方法 | 说明 |
|------|------|
| `CatalogNoticeImportDomainService#persistImportedDoc(PersistNoticeDocCommand)` | 落 doc+lines+标准包 URI；hash 幂等 |
| `CatalogNoticeApplyDomainService#applyDoc(ApplyNoticeDocCommand)` | 应用 overlay / map / 回写 lv / 未收费同步 / apply_log |
| `CatalogNoticeTodoDomainService#closeTodo(CloseNoticeTodoCommand)` | 关闭待办（不解除未满足门禁） |
| `CatalogNoticeComplianceDomainService#runCompliance(RunComplianceCommand)` | 生成/覆盖校验报告 |
| `CatalogNoticeComplianceGate#evaluate(ComplianceGateCommand): ComplianceGateResult` | 硬阻断判定（开单/上传消费） |

Port（领域端口，bridge 实现）：

| 方法 | 说明 |
|------|------|
| `CatalogNoticeHisProductPort#updateProductChrgitmLv(...)` | 回写 MD/AD/DD chrgitm_lv |
| `CatalogNoticeHisChargePort#listUnchargedFeeLines(...)` | 查未收费明细 |
| `CatalogNoticeHisChargePort#updateUnchargedFeeLineChrgitmLv(...)` | 同步未收费明细 lv |
| `CatalogNoticePackStorePort#storeCanonicalPack(...)` / `#loadCanonicalPack(...)` | 标准包存取 |

---

## 6.2 QueryService

| 接口签名 | 场景 |
|----------|------|
| `CatalogNoticeDocQueryService#pageDocs(PageNoticeDocQuery): PageResult<NoticeDocView>` | 文件列表 |
| `CatalogNoticeDocQueryService#getDocById(Long): NoticeDocView` | 详情 |
| `CatalogNoticeLineQueryService#pageLines(PageNoticeLineQuery): PageResult<NoticeLineView>` | 明细 |
| `CatalogNoticeComplianceQueryService#getLatestReport(GetComplianceReportQuery): ComplianceReportView` | 报告 |
| `CatalogNoticeTodoQueryService#summarizeTodos(SummarizeNoticeTodoQuery): NoticeTodoSummaryView` | 首页汇总 |
| `CatalogNoticeTodoQueryService#pageTodos(PageNoticeTodoQuery): PageResult<NoticeTodoView>` | 待办列表 |
| `CatalogNoticeApplyPreviewQueryService#previewApply(PreviewApplyQuery): ApplyPreviewView` | 应用差异预览 |
| `NationalCatalogOverlayQueryService#getOverlay(GetOverlayQuery): OverlayView` | 选码合成（CAT-001 可复用） |

---

## 7. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v0.1 | 2026-07-14 | 目标态初稿，供 AS/SDD 锚点 |

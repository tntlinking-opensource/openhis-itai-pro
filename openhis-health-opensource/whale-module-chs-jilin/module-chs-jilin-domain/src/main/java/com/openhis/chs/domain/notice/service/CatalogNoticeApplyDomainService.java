/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.openhis.chs.domain.catalog.repository.YbNationalCatalogItemRepository;
import com.openhis.chs.domain.notice.command.ApplyNoticeDocCommand;
import com.openhis.chs.domain.notice.entity.YbCatalogNoticeApplyLog;
import com.openhis.chs.domain.notice.entity.YbCatalogNoticeDoc;
import com.openhis.chs.domain.notice.entity.YbCatalogNoticeLine;
import com.openhis.chs.domain.notice.entity.YbNationalCatalogNoticeOverlay;
import com.openhis.chs.domain.notice.entity.YbServiceProjectMap;
import com.openhis.chs.domain.notice.model.ApplyNoticeDocResult;
import com.openhis.chs.domain.notice.model.HisProductLvChange;
import com.openhis.chs.domain.notice.model.HisUnchargedFeeLine;
import com.openhis.chs.domain.notice.port.CatalogNoticeHisChargePort;
import com.openhis.chs.domain.notice.port.CatalogNoticeHisProductPort;
import com.openhis.chs.domain.notice.repository.YbCatalogNoticeApplyLogRepository;
import com.openhis.chs.domain.notice.repository.YbCatalogNoticeDocRepository;
import com.openhis.chs.domain.notice.repository.YbCatalogNoticeLineRepository;
import com.openhis.chs.domain.notice.repository.YbNationalCatalogNoticeOverlayRepository;
import com.openhis.chs.domain.notice.repository.YbServiceProjectMapRepository;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 医保文件通知应用写服务（W-03）。
 * <p>只写 overlay / map / 院内 lv / 未收费明细 / apply_log；不改 13xx 镜像主列。</p>
 * <p>大文件按批短事务落库；批间失败保留已提交批次（{@code PARTIAL}），可重跑续处理未落结果的行。</p>
 */
@Service
@RequiredArgsConstructor
public class CatalogNoticeApplyDomainService {

    private static final String LINE_ACTIVE = "ACTIVE";
    private static final String APPLY_REVOKED = "REVOKED";
    private static final String APPLY_APPLIED = "APPLIED";
    private static final String APPLY_APPLYING = "APPLYING";
    private static final String APPLY_PARTIAL = "PARTIAL";
    private static final String APPLY_NOT_APPLIED = "NOT_APPLIED";
    private static final String MAP_PENDING = "PENDING";
    private static final String MAP_MAPPED = "MAPPED";
    private static final String FLAG_STOP = "0";
    private static final String FLAG_AVAILABLE = "1";
    private static final String RESULT_PATCHED = "PATCHED";
    private static final String RESULT_PENDING_MAP = "PENDING_MAP";
    private static final String RESULT_SKIPPED = "SKIPPED";
    private static final String RESULT_FAILED = "FAILED";
    private static final String RESULT_WRITEBACK = "WRITEBACK";
    private static final String OP_SET_UNAVAILABLE = "SET_UNAVAILABLE";
    private static final String OP_PATCH_FIELD = "PATCH_FIELD";
    private static final String OP_WRITEBACK = "WRITEBACK_CHRGITM_LV";
    private static final String ACTION_STOP = "STOP";
    private static final String ACTION_ADD = "ADD";
    private static final String ACTION_MODIFY = "MODIFY";
    private static final String ACTION_PROVINCE_MODIFY = "PROVINCE_MODIFY";
    private static final String ACTION_SET_PAY_CLASS = "SET_PAY_CLASS";
    private static final String EMPTY_REFS_JSON = "[]";
    private static final Long CONTRACT_FALLBACK = 0L;
    private static final int DEFAULT_BATCH_SIZE = 200;

    private final YbCatalogNoticeDocRepository docRepository;
    private final YbCatalogNoticeLineRepository lineRepository;
    private final YbNationalCatalogNoticeOverlayRepository overlayRepository;
    private final YbServiceProjectMapRepository projectMapRepository;
    private final YbCatalogNoticeApplyLogRepository applyLogRepository;
    private final YbNationalCatalogItemRepository nationalCatalogItemRepository;
    private final CatalogNoticeHisProductPort hisProductPort;
    private final CatalogNoticeHisChargePort hisChargePort;
    private final PlatformTransactionManager transactionManager;

    /**
     * 应用文件：按批短事务写 overlay / map / 回写院内 lv / 同步未收费 / apply_log。
     *
     * @param command docId + tenant + operator
     * @return 计数结果
     */
    public ApplyNoticeDocResult applyDoc(ApplyNoticeDocCommand command) {
        if (command == null || command.getDocId() == null || command.getTenantId() == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_DOC_NOT_FOUND, "应用命令缺少 docId/tenantId");
        }
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        YbCatalogNoticeDoc doc = tx.execute(status -> beginApply(command));
        if (doc == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_DOC_NOT_FOUND, "文件不存在，docId=" + command.getDocId());
        }

        List<Long> pendingIds = tx.execute(status -> loadPendingLineIds(doc.getId()));
        if (pendingIds == null) {
            pendingIds = List.of();
        }
        int batchSize = resolveBatchSize(command.getBatchSize());
        int progressTotal = command.getProgressTotalOverride() != null
            ? command.getProgressTotalOverride()
            : pendingIds.size();
        int progressBase = Math.max(0, command.getProgressBase());
        reportProgress(command, progressBase, progressTotal);

        int applied = 0;
        int failed = 0;
        int writeback = 0;
        int syncedUncharged = 0;
        int localProcessed = 0;
        boolean anyBatchCommitted = false;

        try {
            for (int from = 0; from < pendingIds.size(); from += batchSize) {
                int to = Math.min(from + batchSize, pendingIds.size());
                List<Long> chunkIds = pendingIds.subList(from, to);
                BatchCounts counts = tx.execute(status -> applyLineBatch(command, doc.getId(), chunkIds));
                if (counts == null) {
                    counts = BatchCounts.empty();
                }
                anyBatchCommitted = true;
                applied += counts.applied;
                failed += counts.failed;
                writeback += counts.writeback;
                syncedUncharged += counts.syncedUncharged;
                localProcessed += chunkIds.size();
                reportProgress(command, progressBase + localProcessed, progressTotal);
            }

            tx.executeWithoutResult(status -> markDocStatus(
                command.getDocId(),
                command.getTenantId(),
                APPLY_APPLIED,
                command.getOperatorId(),
                LocalDateTime.now()));
        } catch (BusinessException ex) {
            markFailureStatus(tx, command, anyBatchCommitted);
            throw ex;
        } catch (RuntimeException ex) {
            markFailureStatus(tx, command, anyBatchCommitted);
            throw new BusinessException(
                ChsErrorCodes.NOTICE_APPLY_FAILED,
                "应用失败：" + ex.getMessage(),
                ex);
        }

        ApplyNoticeDocResult result = new ApplyNoticeDocResult();
        result.setAppliedLineCount(applied);
        result.setFailedLineCount(failed);
        result.setWritebackCount(writeback);
        result.setSyncedUnchargedCount(syncedUncharged);
        return result;
    }

    /**
     * 统计文件尚未写出 apply_result 的 ACTIVE 行数（续跑/多文件进度分母）。
     */
    public int countPendingApplyLines(Long docId, Long tenantId) {
        if (docId == null || tenantId == null) {
            return 0;
        }
        YbCatalogNoticeDoc doc = docRepository
            .findByIdAndTenantIdAndIsDeletedFalse(docId, tenantId)
            .orElse(null);
        if (doc == null) {
            return 0;
        }
        long count = lineRepository.countPendingApplyLines(docId, LINE_ACTIVE);
        if (count > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) count;
    }

    private YbCatalogNoticeDoc beginApply(ApplyNoticeDocCommand command) {
        YbCatalogNoticeDoc doc = docRepository
            .findByIdAndTenantIdAndIsDeletedFalse(command.getDocId(), command.getTenantId())
            .orElseThrow(() -> new BusinessException(
                ChsErrorCodes.NOTICE_DOC_NOT_FOUND,
                "文件不存在，docId=" + command.getDocId()));
        if (APPLY_REVOKED.equals(doc.getApplyStatus())) {
            throw new BusinessException(ChsErrorCodes.NOTICE_APPLY_DENIED, "文件已撤销，禁止应用");
        }
        // APPLYING 残留允许续跑（进程中断后 DB 状态未落下）；真正并发由 Workflow 内存锁拦截
        doc.setApplyStatus(APPLY_APPLYING);
        return docRepository.save(doc);
    }

    private List<Long> loadPendingLineIds(Long docId) {
        return lineRepository.findPendingApplyLineIds(docId, LINE_ACTIVE);
    }

    private BatchCounts applyLineBatch(ApplyNoticeDocCommand command, Long docId, List<Long> lineIds) {
        YbCatalogNoticeDoc doc = docRepository
            .findByIdAndTenantIdAndIsDeletedFalse(docId, command.getTenantId())
            .orElseThrow(() -> new BusinessException(
                ChsErrorCodes.NOTICE_DOC_NOT_FOUND,
                "文件不存在，docId=" + docId));
        List<YbCatalogNoticeLine> lines = lineRepository.findAllById(lineIds);
        lines.sort(Comparator.comparing(YbCatalogNoticeLine::getId, Comparator.nullsLast(Long::compareTo)));
        LocalDateTime now = LocalDateTime.now();
        ApplyBatchContext ctx = preloadBatchContext(doc, lines);
        int applied = 0;
        int failed = 0;
        int writeback = 0;
        int syncedUncharged = 0;
        for (YbCatalogNoticeLine line : lines) {
            if (trimToNull(line.getApplyResult()) != null) {
                continue;
            }
            String action = normalizeAction(line.getAction());
            if (ACTION_STOP.equals(action)) {
                LineApplyOutcome outcome = applyStop(doc, line, now, command.getOperatorId(), ctx);
                applied += outcome.appliedDelta;
                failed += outcome.failedDelta;
            } else if (ACTION_ADD.equals(action)) {
                LineApplyOutcome outcome = applyAdd(doc, line, now, command.getOperatorId(), ctx);
                applied += outcome.appliedDelta;
                failed += outcome.failedDelta;
            } else if (ACTION_MODIFY.equals(action) || ACTION_PROVINCE_MODIFY.equals(action)) {
                LineApplyOutcome outcome = applyModify(doc, line, now, command.getOperatorId(), ctx);
                applied += outcome.appliedDelta;
                failed += outcome.failedDelta;
            } else if (ACTION_SET_PAY_CLASS.equals(action)) {
                PayClassOutcome outcome = applySetPayClass(doc, line, now, command.getOperatorId(), ctx);
                applied += outcome.appliedDelta;
                failed += outcome.failedDelta;
                writeback += outcome.writebackDelta;
                syncedUncharged += outcome.unchargedDelta;
            } else {
                line.setApplyResult(RESULT_SKIPPED);
                lineRepository.save(line);
                applied++;
            }
        }
        return new BatchCounts(applied, failed, writeback, syncedUncharged);
    }

    private ApplyBatchContext preloadBatchContext(YbCatalogNoticeDoc doc, List<YbCatalogNoticeLine> lines) {
        Long contractId = doc.getContractId() == null ? CONTRACT_FALLBACK : doc.getContractId();
        Set<String> hilists = new HashSet<>();
        Set<String> projects = new HashSet<>();
        for (YbCatalogNoticeLine line : lines) {
            String hilist = trimToNull(line.getHilistCode());
            if (hilist != null) {
                hilists.add(hilist);
            }
            String project = trimToNull(line.getProjectCode());
            if (project != null) {
                projects.add(project);
            }
        }
        Map<String, YbNationalCatalogNoticeOverlay> overlays = new HashMap<>();
        if (!hilists.isEmpty()) {
            for (YbNationalCatalogNoticeOverlay row : overlayRepository
                .findByTenantIdAndContractIdAndHilistCodeInAndIsDeletedFalse(
                    doc.getTenantId(), contractId, hilists)) {
                overlays.put(overlayKey(row.getListType(), row.getHilistCode()), row);
            }
        }
        Map<String, YbServiceProjectMap> maps = new HashMap<>();
        if (!projects.isEmpty()) {
            for (YbServiceProjectMap row : projectMapRepository
                .findByTenantIdAndProjectCodeInAndIsDeletedFalse(doc.getTenantId(), projects)) {
                maps.put(row.getProjectCode(), row);
            }
        }
        Map<String, String> catalogByProject = new HashMap<>();
        if (!projects.isEmpty()) {
            for (YbNationalCatalogItem item : nationalCatalogItemRepository
                .findByTenantIdAndHilistCodeInAndIsDeletedFalse(doc.getTenantId(), projects)) {
                if (doc.getContractId() != null && !doc.getContractId().equals(item.getContractId())) {
                    continue;
                }
                String code = trimToNull(item.getHilistCode());
                if (code != null && !catalogByProject.containsKey(code)) {
                    catalogByProject.put(code, code);
                }
            }
        }
        return new ApplyBatchContext(contractId, overlays, maps, catalogByProject);
    }

    private void markFailureStatus(
            TransactionTemplate tx,
            ApplyNoticeDocCommand command,
            boolean anyBatchCommitted) {
        String status = anyBatchCommitted ? APPLY_PARTIAL : APPLY_NOT_APPLIED;
        try {
            tx.executeWithoutResult(s -> markDocStatus(
                command.getDocId(),
                command.getTenantId(),
                status,
                null,
                null));
        } catch (RuntimeException ignored) {
            // 失败收尾尽力而为，不覆盖原异常
        }
    }

    private void markDocStatus(
            Long docId,
            Long tenantId,
            String applyStatus,
            Long operatorId,
            LocalDateTime appliedAt) {
        YbCatalogNoticeDoc doc = docRepository
            .findByIdAndTenantIdAndIsDeletedFalse(docId, tenantId)
            .orElse(null);
        if (doc == null) {
            return;
        }
        doc.setApplyStatus(applyStatus);
        if (APPLY_APPLIED.equals(applyStatus)) {
            doc.setAppliedAt(appliedAt != null ? appliedAt : LocalDateTime.now());
            doc.setAppliedBy(operatorId);
        }
        docRepository.save(doc);
    }

    private void reportProgress(ApplyNoticeDocCommand command, int processed, int total) {
        if (command == null || command.getProgressCallback() == null) {
            return;
        }
        command.getProgressCallback().onProgress(processed, total);
    }

    private static int resolveBatchSize(Integer batchSize) {
        if (batchSize == null || batchSize <= 0) {
            return DEFAULT_BATCH_SIZE;
        }
        if (batchSize > 2000) {
            return 2000;
        }
        return batchSize;
    }

    private LineApplyOutcome applyStop(
            YbCatalogNoticeDoc doc,
            YbCatalogNoticeLine line,
            LocalDateTime now,
            Long operatorId,
            ApplyBatchContext ctx) {
        String hilist = trimToNull(line.getHilistCode());
        if (hilist == null) {
            line.setMatchStatus("MISS");
            if (trimToNull(line.getProjectCode()) != null) {
                upsertPendingMap(doc, line, now, ctx);
                line.setApplyResult(RESULT_PENDING_MAP);
            } else {
                line.setApplyResult(RESULT_SKIPPED);
            }
            lineRepository.save(line);
            return LineApplyOutcome.ok();
        }
        YbNationalCatalogNoticeOverlay overlay = upsertOverlayShell(doc, line, hilist, now, ctx);
        overlay.setNoticeAvaliFlag(FLAG_STOP);
        overlay.setNoticeEndDate(line.getEndDate());
        overlayRepository.save(overlay);
        String endDateText = null;
        if (line.getEndDate() != null) {
            endDateText = line.getEndDate().toString();
        }
        writeApplyLog(
            doc,
            line.getId(),
            "yb_national_catalog_notice_overlay",
            overlay.getId(),
            null,
            patchSnapshot("notice_avali_flag", FLAG_STOP, "notice_end_date", endDateText),
            OP_SET_UNAVAILABLE,
            EMPTY_REFS_JSON,
            operatorId,
            now);
        line.setMatchStatus("HIT");
        line.setApplyResult(RESULT_PATCHED);
        lineRepository.save(line);
        return LineApplyOutcome.ok();
    }

    private LineApplyOutcome applyAdd(
            YbCatalogNoticeDoc doc,
            YbCatalogNoticeLine line,
            LocalDateTime now,
            Long operatorId,
            ApplyBatchContext ctx) {
        String hilist = trimToNull(line.getHilistCode());
        String project = trimToNull(line.getProjectCode());
        if (hilist == null) {
            if (project != null) {
                upsertPendingMap(doc, line, now, ctx);
                line.setMatchStatus("MISS");
                line.setApplyResult(RESULT_PENDING_MAP);
                lineRepository.save(line);
                return LineApplyOutcome.ok();
            }
            line.setApplyResult(RESULT_SKIPPED);
            lineRepository.save(line);
            return LineApplyOutcome.ok();
        }
        YbNationalCatalogNoticeOverlay overlay = upsertOverlayShell(doc, line, hilist, now, ctx);
        overlay.setNoticeAvaliFlag(FLAG_AVAILABLE);
        String patchJson = mergePatchJson(overlay.getNoticePatchJson(), buildPatchFromLine(line));
        overlay.setNoticePatchJson(patchJson);
        overlayRepository.save(overlay);
        writeApplyLog(
            doc,
            line.getId(),
            "yb_national_catalog_notice_overlay",
            overlay.getId(),
            null,
            patchJson,
            OP_PATCH_FIELD,
            EMPTY_REFS_JSON,
            operatorId,
            now);
        line.setMatchStatus("HIT");
        line.setApplyResult(RESULT_PATCHED);
        lineRepository.save(line);
        return LineApplyOutcome.ok();
    }

    private LineApplyOutcome applyModify(
            YbCatalogNoticeDoc doc,
            YbCatalogNoticeLine line,
            LocalDateTime now,
            Long operatorId,
            ApplyBatchContext ctx) {
        String hilist = trimToNull(line.getHilistCode());
        if (hilist == null) {
            line.setMatchStatus("MISS");
            line.setApplyResult(RESULT_SKIPPED);
            lineRepository.save(line);
            return LineApplyOutcome.ok();
        }
        YbNationalCatalogNoticeOverlay overlay = upsertOverlayShell(doc, line, hilist, now, ctx);
        String before = overlay.getNoticePatchJson();
        String patchJson = mergePatchJson(before, buildPatchFromLine(line));
        overlay.setNoticePatchJson(patchJson);
        if (overlay.getNoticeAvaliFlag() == null || overlay.getNoticeAvaliFlag().isBlank()) {
            overlay.setNoticeAvaliFlag(FLAG_AVAILABLE);
        }
        overlayRepository.save(overlay);
        writeApplyLog(
            doc,
            line.getId(),
            "yb_national_catalog_notice_overlay",
            overlay.getId(),
            before,
            patchJson,
            OP_PATCH_FIELD,
            EMPTY_REFS_JSON,
            operatorId,
            now);
        line.setMatchStatus("HIT");
        line.setApplyResult(RESULT_PATCHED);
        lineRepository.save(line);
        return LineApplyOutcome.ok();
    }

    private PayClassOutcome applySetPayClass(
            YbCatalogNoticeDoc doc,
            YbCatalogNoticeLine line,
            LocalDateTime now,
            Long operatorId,
            ApplyBatchContext ctx) {
        String project = trimToNull(line.getProjectCode());
        String lv = trimToNull(line.getChrgitmLv());
        if (project == null || lv == null) {
            line.setApplyResult(RESULT_FAILED);
            line.setWarningMessage("SET_PAY_CLASS 缺少 project_code 或 chrgitm_lv");
            lineRepository.save(line);
            return PayClassOutcome.failed();
        }

        String hilist = resolveHilistForPayClass(doc, line, project, now, ctx);
        if (hilist != null) {
            YbNationalCatalogNoticeOverlay overlay = upsertOverlayShell(doc, line, hilist, now, ctx);
            Map<String, String> patch = new LinkedHashMap<>();
            patch.put("chrgitm_lv", lv);
            String before = overlay.getNoticePatchJson();
            String after = mergePatchJson(before, patch);
            overlay.setNoticePatchJson(after);
            if (overlay.getNoticeAvaliFlag() == null || overlay.getNoticeAvaliFlag().isBlank()) {
                overlay.setNoticeAvaliFlag(FLAG_AVAILABLE);
            }
            overlayRepository.save(overlay);
            writeApplyLog(
                doc,
                line.getId(),
                "yb_national_catalog_notice_overlay",
                overlay.getId(),
                before,
                after,
                OP_PATCH_FIELD,
                EMPTY_REFS_JSON,
                operatorId,
                now);
            line.setMatchStatus("HIT");
        } else {
            upsertPendingMap(doc, line, now, ctx);
            line.setMatchStatus("MISS");
        }

        List<HisProductLvChange> changes = hisProductPort.updateProductChrgitmLv(
            doc.getTenantId(), project, hilist, lv);
        List<Long> productIds = new ArrayList<>();
        for (HisProductLvChange change : changes) {
            if (change.getTargetId() != null) {
                productIds.add(change.getTargetId());
            }
        }

        List<HisUnchargedFeeLine> uncharged = hisChargePort.listUnchargedFeeLines(
            doc.getTenantId(), project, hilist, productIds);
        List<Map<String, Object>> affectedRefs = new ArrayList<>();
        for (HisUnchargedFeeLine fee : uncharged) {
            if (fee.getItemLineId() == null) {
                continue;
            }
            hisChargePort.updateUnchargedFeeLineChrgitmLv(fee.getItemLineId(), lv);
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("billId", fee.getBillId());
            ref.put("billNo", fee.getBillNo());
            ref.put("encounterId", fee.getEncounterId());
            ref.put("itemLineId", fee.getItemLineId());
            ref.put("beforeChrgitmLv", fee.getBeforeChrgitmLv());
            ref.put("afterChrgitmLv", lv);
            affectedRefs.add(ref);
        }
        String affectedJson = EMPTY_REFS_JSON;
        if (!affectedRefs.isEmpty()) {
            affectedJson = JSON.toJSONString(affectedRefs);
        }

        for (HisProductLvChange change : changes) {
            writeApplyLog(
                doc,
                line.getId(),
                change.getTargetTable(),
                change.getTargetId(),
                patchSnapshot("chrgitm_lv", change.getBeforeLv()),
                patchSnapshot("chrgitm_lv", change.getAfterLv()),
                OP_WRITEBACK,
                affectedJson,
                operatorId,
                now);
        }
        if (changes.isEmpty()) {
            writeApplyLog(
                doc,
                line.getId(),
                "yb_service_project_map",
                null,
                patchSnapshot("chrgitm_lv", null),
                patchSnapshot("chrgitm_lv", lv),
                OP_WRITEBACK,
                affectedJson,
                operatorId,
                now);
        }

        if (hilist == null) {
            line.setApplyResult(RESULT_PENDING_MAP);
        } else if (changes.isEmpty()) {
            line.setApplyResult(RESULT_PATCHED);
        } else {
            line.setApplyResult(RESULT_WRITEBACK);
        }
        lineRepository.save(line);
        return PayClassOutcome.ok(changes.size(), affectedRefs.size());
    }

    private String resolveHilistForPayClass(
            YbCatalogNoticeDoc doc,
            YbCatalogNoticeLine line,
            String project,
            LocalDateTime now,
            ApplyBatchContext ctx) {
        String hilist = trimToNull(line.getHilistCode());
        if (hilist != null) {
            upsertMapped(doc, line, project, hilist, now, ctx);
            return hilist;
        }
        YbServiceProjectMap existing = ctx.mapsByProject.get(project);
        if (existing == null) {
            existing = projectMapRepository
                .findByTenantIdAndProjectCodeAndIsDeletedFalse(doc.getTenantId(), project)
                .orElse(null);
            if (existing != null) {
                ctx.mapsByProject.put(project, existing);
            }
        }
        if (existing != null) {
            String mapped = trimToNull(existing.getHilistCode());
            if (mapped != null) {
                return mapped;
            }
        }
        String catalogHit = ctx.catalogHilistByProject.get(project);
        if (catalogHit != null) {
            upsertMapped(doc, line, project, catalogHit, now, ctx);
            return catalogHit;
        }
        upsertPendingMap(doc, line, now, ctx);
        return null;
    }

    private YbNationalCatalogNoticeOverlay upsertOverlayShell(
            YbCatalogNoticeDoc doc,
            YbCatalogNoticeLine line,
            String hilist,
            LocalDateTime now,
            ApplyBatchContext ctx) {
        Long contractId = ctx.contractId;
        String listType = trimToNull(line.getListType());
        if (listType == null) {
            listType = normalizeListType(doc.getCategory());
        }
        String key = overlayKey(listType, hilist);
        YbNationalCatalogNoticeOverlay overlay = ctx.overlaysByKey.get(key);
        if (overlay == null) {
            Optional<YbNationalCatalogNoticeOverlay> existing = overlayRepository
                .findByTenantIdAndContractIdAndListTypeAndHilistCodeAndIsDeletedFalse(
                    doc.getTenantId(), contractId, listType, hilist);
            if (existing.isPresent()) {
                overlay = existing.get();
            } else {
                overlay = new YbNationalCatalogNoticeOverlay();
                overlay.setTenantId(doc.getTenantId());
                overlay.setContractId(contractId);
                overlay.setListType(listType);
                overlay.setHilistCode(hilist);
                overlay.setIsDeleted(false);
            }
            ctx.overlaysByKey.put(key, overlay);
        }
        overlay.setLastNoticeDocId(doc.getId());
        overlay.setLastNoticeLineId(line.getId());
        overlay.setEffectiveAt(now);
        return overlay;
    }

    private void upsertPendingMap(
            YbCatalogNoticeDoc doc,
            YbCatalogNoticeLine line,
            LocalDateTime now,
            ApplyBatchContext ctx) {
        String project = trimToNull(line.getProjectCode());
        if (project == null) {
            return;
        }
        YbServiceProjectMap map = ctx.mapsByProject.get(project);
        if (map == null) {
            map = projectMapRepository
                .findByTenantIdAndProjectCodeAndIsDeletedFalse(doc.getTenantId(), project)
                .orElse(null);
        }
        if (map != null) {
            if (MAP_MAPPED.equals(map.getStatus()) && trimToNull(map.getHilistCode()) != null) {
                ctx.mapsByProject.put(project, map);
                return;
            }
        } else {
            map = new YbServiceProjectMap();
            map.setTenantId(doc.getTenantId());
            map.setProjectCode(project);
            map.setIsDeleted(false);
        }
        map.setHilistCode(null);
        map.setStatus(MAP_PENDING);
        map.setNoticeDocId(doc.getId());
        map.setNoticeLineId(line.getId());
        map.setMappedAt(null);
        projectMapRepository.save(map);
        ctx.mapsByProject.put(project, map);
    }

    private void upsertMapped(
            YbCatalogNoticeDoc doc,
            YbCatalogNoticeLine line,
            String project,
            String hilist,
            LocalDateTime now,
            ApplyBatchContext ctx) {
        YbServiceProjectMap map = ctx.mapsByProject.get(project);
        if (map == null) {
            map = projectMapRepository
                .findByTenantIdAndProjectCodeAndIsDeletedFalse(doc.getTenantId(), project)
                .orElse(null);
        }
        if (map == null) {
            map = new YbServiceProjectMap();
            map.setTenantId(doc.getTenantId());
            map.setProjectCode(project);
            map.setIsDeleted(false);
        }
        map.setHilistCode(hilist);
        map.setStatus(MAP_MAPPED);
        map.setNoticeDocId(doc.getId());
        map.setNoticeLineId(line.getId());
        map.setMappedAt(now);
        projectMapRepository.save(map);
        ctx.mapsByProject.put(project, map);
    }

    private void writeApplyLog(
            YbCatalogNoticeDoc doc,
            Long lineId,
            String targetTable,
            Long targetId,
            String beforeJson,
            String afterJson,
            String op,
            String affectedRefsJson,
            Long operatorId,
            LocalDateTime operatedAt) {
        YbCatalogNoticeApplyLog log = new YbCatalogNoticeApplyLog();
        log.setTenantId(doc.getTenantId());
        log.setDocId(doc.getId());
        log.setLineId(lineId);
        log.setTargetTable(targetTable);
        log.setTargetId(targetId);
        log.setBeforeJson(beforeJson);
        log.setAfterJson(afterJson);
        log.setOp(op);
        if (affectedRefsJson == null || affectedRefsJson.isBlank()) {
            log.setAffectedRefsJson(EMPTY_REFS_JSON);
        } else {
            log.setAffectedRefsJson(affectedRefsJson);
        }
        log.setOperatorId(operatorId);
        log.setOperatedAt(operatedAt);
        log.setIsDeleted(false);
        applyLogRepository.save(log);
    }

    private Map<String, String> buildPatchFromLine(YbCatalogNoticeLine line) {
        Map<String, String> patch = new LinkedHashMap<>();
        putIfPresent(patch, "chrgitm_lv", line.getChrgitmLv());
        putIfPresent(patch, "change_fields", line.getChangeFields());
        putIfPresent(patch, "lmt_used_flag", line.getLmtUsedFlag());
        putIfPresent(patch, "lmt_scope", line.getLmtScope());
        putIfPresent(patch, "overlmt_dspo_way", line.getOverlmtDspoWay());
        putIfPresent(patch, "dual_channel_flag", line.getDualChannelFlag());
        putIfPresent(patch, "hilist_name", line.getHilistName());
        if (line.getPriceLimitAmt() != null) {
            patch.put("hilist_pric_uplmt_amt", line.getPriceLimitAmt().toPlainString());
        }
        if (line.getBeginDate() != null) {
            patch.put("begin_date", line.getBeginDate().toString());
        }
        if (line.getEndDate() != null) {
            patch.put("end_date", line.getEndDate().toString());
        }
        return patch;
    }

    private static void putIfPresent(Map<String, String> patch, String key, String value) {
        String trimmed = trimToNull(value);
        if (trimmed != null) {
            patch.put(key, trimmed);
        }
    }

    private static String mergePatchJson(String existingJson, Map<String, String> incoming) {
        JSONObject obj;
        if (existingJson == null || existingJson.isBlank()) {
            obj = new JSONObject();
        } else {
            obj = JSON.parseObject(existingJson);
            if (obj == null) {
                obj = new JSONObject();
            }
        }
        if (incoming != null) {
            for (Map.Entry<String, String> entry : incoming.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    obj.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return obj.toJSONString();
    }

    private static String patchSnapshot(String key, String value) {
        JSONObject obj = new JSONObject();
        obj.put(key, value);
        return obj.toJSONString();
    }

    private static String patchSnapshot(String k1, String v1, String k2, String v2) {
        JSONObject obj = new JSONObject();
        obj.put(k1, v1);
        obj.put(k2, v2);
        return obj.toJSONString();
    }

    private static String normalizeAction(String action) {
        String trimmed = trimToNull(action);
        if (trimmed == null) {
            return "";
        }
        return trimmed.toUpperCase();
    }

    private static String normalizeListType(String category) {
        String cat = trimToNull(category);
        if (cat == null) {
            return "UNKNOWN";
        }
        return cat;
    }

    private static String overlayKey(String listType, String hilist) {
        return (listType == null ? "" : listType) + "\0" + (hilist == null ? "" : hilist);
    }

    private static final class ApplyBatchContext {
        private final Long contractId;
        private final Map<String, YbNationalCatalogNoticeOverlay> overlaysByKey;
        private final Map<String, YbServiceProjectMap> mapsByProject;
        private final Map<String, String> catalogHilistByProject;

        private ApplyBatchContext(
                Long contractId,
                Map<String, YbNationalCatalogNoticeOverlay> overlaysByKey,
                Map<String, YbServiceProjectMap> mapsByProject,
                Map<String, String> catalogHilistByProject) {
            this.contractId = contractId;
            this.overlaysByKey = overlaysByKey;
            this.mapsByProject = mapsByProject;
            this.catalogHilistByProject = catalogHilistByProject;
        }
    }

    private static final class BatchCounts {
        private final int applied;
        private final int failed;
        private final int writeback;
        private final int syncedUncharged;

        private BatchCounts(int applied, int failed, int writeback, int syncedUncharged) {
            this.applied = applied;
            this.failed = failed;
            this.writeback = writeback;
            this.syncedUncharged = syncedUncharged;
        }

        private static BatchCounts empty() {
            return new BatchCounts(0, 0, 0, 0);
        }
    }

    private static final class LineApplyOutcome {
        private final int appliedDelta;
        private final int failedDelta;

        private LineApplyOutcome(int appliedDelta, int failedDelta) {
            this.appliedDelta = appliedDelta;
            this.failedDelta = failedDelta;
        }

        private static LineApplyOutcome ok() {
            return new LineApplyOutcome(1, 0);
        }
    }

    private static final class PayClassOutcome {
        private final int appliedDelta;
        private final int failedDelta;
        private final int writebackDelta;
        private final int unchargedDelta;

        private PayClassOutcome(int appliedDelta, int failedDelta, int writebackDelta, int unchargedDelta) {
            this.appliedDelta = appliedDelta;
            this.failedDelta = failedDelta;
            this.writebackDelta = writebackDelta;
            this.unchargedDelta = unchargedDelta;
        }

        private static PayClassOutcome ok(int writeback, int uncharged) {
            return new PayClassOutcome(1, 0, writeback, uncharged);
        }

        private static PayClassOutcome failed() {
            return new PayClassOutcome(0, 1, 0, 0);
        }
    }
}


/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.writeoff.service;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.einvoice.boss.application.document.request.DocumentIdRequest;
import com.openhis.whale.einvoice.boss.application.document.response.BossDocumentDetail;
import com.openhis.whale.einvoice.boss.application.document.service.EinvoiceBossDocumentAppService;
import com.openhis.whale.einvoice.boss.application.internal.issue.BossWriteOffService;
import com.openhis.whale.einvoice.boss.application.internal.issue.BossWriteOffStatusSupport;
import com.openhis.whale.einvoice.boss.application.list.response.BossDocumentListItem;
import com.openhis.whale.einvoice.boss.application.writeoff.request.ManualWriteOffRequest;
import com.openhis.whale.einvoice.boss.application.writeoff.request.PageWriteOffDocumentsRequest;
import com.openhis.whale.einvoice.boss.application.writeoff.response.ManualWriteOffResponse;
import com.openhis.whale.einvoice.boss.application.writeoff.response.PageWriteOffDocumentsResponse;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.enums.IssueKind;
import com.openhis.whale.einvoice.boss.domain.enums.WriteOffStatus;
import com.openhis.whale.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.openhis.whale.einvoice.boss.domain.writeoff.loader.EinvoiceInvoiceWritebackLoader;
import com.openhis.whale.einvoice.boss.gateway.config.EinvoiceBossGatewayProperties;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 手动冲红页（HTTP：{@code /api/app/einvoiceBossWriteOff/*}）。
 *
 * <p>待冲红：已开具蓝票且尚未冲红成功；冲红记录：本地已作废或博思冲红有结果，支持查看。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EinvoiceBossWriteOffAppService {

    private static final String SCOPE_CANDIDATE = "CANDIDATE";
    private static final String SCOPE_HISTORY = "HISTORY";

    private final EinvBossDocumentRepository documentRepository;
    private final EinvoiceIssueSnapshotContextLoader issueSnapshotContextLoader;
    private final BossWriteOffStatusSupport writeOffStatusSupport;
    private final BossWriteOffService writeOffService;
    private final EinvoiceInvoiceWritebackLoader invoiceWritebackLoader;
    private final EinvoiceBossDocumentAppService documentAppService;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final EinvoiceBossGatewayProperties properties;

    /**
     * 手动冲红列表（待冲红 / 冲红记录）。
     *
     * @param request 筛选与分页
     * @return 分页结果
     */
    public PageWriteOffDocumentsResponse pageWriteOffDocuments(PageWriteOffDocumentsRequest request) {
        PageWriteOffDocumentsRequest safe = request == null ? new PageWriteOffDocumentsRequest() : request;
        String scope = normalizeScope(safe.getScope());
        int pageNo = safe.getPageNo() == null || safe.getPageNo() < 1 ? 1 : safe.getPageNo();
        int pageSize = safe.getPageSize() == null || safe.getPageSize() < 1 ? 20 : safe.getPageSize();

        // 先多取再按健康状态过滤，避免 ISSUED/CANCELLED 只能靠快照判定
        int fetchSize = Math.min(Math.max(pageSize * 5, 50), properties.getList().getKeywordFilterLimit());
        Specification<EinvBossDocument> spec = buildSpec(safe, scope);
        Page<EinvBossDocument> page = documentRepository.findAll(
                spec, PageRequest.of(0, fetchSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<BossDocumentListItem> matched = new ArrayList<>();
        for (EinvBossDocument document : page.getContent()) {
            try {
                BossDocumentListItem item = toListItem(document);
                if (matchesScope(item, scope)) {
                    matched.add(item);
                }
            } catch (Exception ex) {
                log.warn("WriteOff list enrich failed, documentId={}", document.getId(), ex);
            }
        }

        PageWriteOffDocumentsResponse response = new PageWriteOffDocumentsResponse();
        response.setTotal(matched.size());
        response.setKeywordTruncated(page.getTotalElements() >= fetchSize);
        int from = (pageNo - 1) * pageSize;
        if (from >= matched.size()) {
            response.setRows(List.of());
            return response;
        }
        int to = Math.min(from + pageSize, matched.size());
        response.setRows(matched.subList(from, to));
        return response;
    }

    /**
     * 冲红详情（复用单据详情，供查看红票信息）。
     *
     * @param request 单据主键
     * @return 详情
     */
    public BossDocumentDetail getWriteOffDetail(DocumentIdRequest request) {
        if (request == null || request.getDocumentId() == null) {
            throw new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "单据主键不能为空");
        }
        return documentAppService.getByDocumentId(request.getDocumentId());
    }

    /**
     * 手动冲红：本地 ISSUED 先作废，再同步调博思 {@code writeOffEBill}。
     *
     * @param request 单据/票据与原因
     * @return 冲红结果
     */
    public ManualWriteOffResponse manualWriteOff(ManualWriteOffRequest request) {
        if (request == null) {
            throw new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "请求不能为空");
        }
        EinvBossDocument document = resolveDocument(request.getDocumentId(), request.getInvoiceId());
        if (!StringUtils.hasText(document.getBossBillNo())) {
            throw new BusinessException(EinvBossErrorCodes.EINV_BOSS_REJECT, "未开出蓝票，无法冲红");
        }
        if (IssueKind.COM.getCode().equals(document.getIssueKind())) {
            throw new BusinessException(EinvBossErrorCodes.EINV_BOSS_REJECT, "通用票请在查询页联系运维冲红，本页暂不支持");
        }
        if (WriteOffStatus.SUCCESS.getCode().equals(document.getWriteOffStatus())
                && StringUtils.hasText(document.getWriteOffBillNo())) {
            return toSuccessResponse(document, "已冲红成功（幂等）");
        }

        String reason = StringUtils.hasText(request.getReason()) ? request.getReason().trim() : "手动冲红";
        Long operatorId = currentOperatorResolver.requireOperatorId("AUTH-001", "当前登录用户不能为空");

        // 本地主档先 CANCELLED，再调博思；已作废则仅重试博思冲红
        invoiceWritebackLoader.writeoffLocalInvoice(document.getInvoiceId(), reason, operatorId);
        BossInvoiceIssueResult result = writeOffService.writeOff(document.getInvoiceId(), reason);

        EinvBossDocument latest = documentRepository.findById(document.getId()).orElse(document);
        ManualWriteOffResponse response = toSuccessResponse(latest, "冲红成功");
        if (result != null && StringUtils.hasText(result.getBillNo())) {
            response.setWriteOffBillNo(result.getBillNo());
            response.setWriteOffBatchCode(result.getBatchCode());
        }
        return response;
    }

    private EinvBossDocument resolveDocument(Long documentId, Long invoiceId) {
        if (documentId != null) {
            return documentRepository.findById(documentId)
                    .filter(doc -> !Boolean.TRUE.equals(doc.getIsDeleted()))
                    .orElseThrow(() -> new BusinessException(
                            EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "单据不存在"));
        }
        if (invoiceId != null) {
            return documentRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId)
                    .orElseThrow(() -> new BusinessException(
                            EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "单据不存在"));
        }
        throw new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "请指定 documentId 或 invoiceId");
    }

    private ManualWriteOffResponse toSuccessResponse(EinvBossDocument document, String message) {
        ManualWriteOffResponse response = new ManualWriteOffResponse();
        response.setSuccess(true);
        response.setMessage(message);
        response.setDocumentId(document.getId());
        response.setInvoiceId(document.getInvoiceId());
        response.setBusNo(document.getBusNo());
        response.setWriteOffBillNo(document.getWriteOffBillNo());
        response.setWriteOffBatchCode(document.getWriteOffBatchCode());
        return response;
    }

    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return SCOPE_CANDIDATE;
        }
        String normalized = scope.trim().toUpperCase(Locale.ROOT);
        if (SCOPE_HISTORY.equals(normalized)) {
            return SCOPE_HISTORY;
        }
        return SCOPE_CANDIDATE;
    }

    private Specification<EinvBossDocument> buildSpec(PageWriteOffDocumentsRequest filter, String scope) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.or(cb.isFalse(root.get("isDeleted")), cb.isNull(root.get("isDeleted"))));
            predicates.add(cb.isNotNull(root.get("bossBillNo")));
            predicates.add(cb.notEqual(root.get("bossBillNo"), ""));

            if (StringUtils.hasText(filter.getIssueKind())) {
                predicates.add(cb.equal(root.get("issueKind"), filter.getIssueKind().trim()));
            }
            if (StringUtils.hasText(filter.getBillNo())) {
                String billNo = filter.getBillNo().trim();
                predicates.add(cb.or(
                        cb.like(root.get("bossBillNo"), "%" + billNo + "%"),
                        cb.like(root.get("writeOffBillNo"), "%" + billNo + "%")));
            }
            if (filter.getDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getDateFrom().atStartOfDay()));
            }
            if (filter.getDateTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), filter.getDateTo().plusDays(1).atStartOfDay()));
            }
            if (SCOPE_HISTORY.equals(scope)) {
                predicates.add(cb.or(
                        cb.isNotNull(root.get("writeOffStatus")),
                        cb.equal(root.get("writeOffStatus"), WriteOffStatus.SUCCESS.getCode()),
                        cb.equal(root.get("writeOffStatus"), WriteOffStatus.FAILED.getCode()),
                        cb.equal(root.get("writeOffStatus"), WriteOffStatus.SKIPPED.getCode())));
            } else {
                predicates.add(cb.or(
                        cb.isNull(root.get("writeOffStatus")),
                        cb.notEqual(root.get("writeOffStatus"), WriteOffStatus.SUCCESS.getCode())));
            }
            if (StringUtils.hasText(filter.getKeyword())) {
                int limit = properties.getList().getKeywordFilterLimit();
                String invoiceStatusHint = SCOPE_HISTORY.equals(scope) ? "CANCELLED" : "ISSUED";
                List<Long> invoiceIds = issueSnapshotContextLoader.findInvoiceIdsByKeyword(
                        filter.getKeyword(), invoiceStatusHint, limit);
                if (invoiceIds.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("invoiceId").in(invoiceIds));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private boolean matchesScope(BossDocumentListItem item, String scope) {
        if (SCOPE_HISTORY.equals(scope)) {
            return "CANCELLED".equals(item.getInvoiceStatus())
                    || StringUtils.hasText(item.getWriteOffStatus());
        }
        // 待冲红：已开具，且尚未冲红成功（含冲红失败可重试）
        if (!"ISSUED".equals(item.getInvoiceStatus())) {
            // 本地已作废但博思冲红失败，也允许在「冲红记录」看；待冲红仅 ISSUED
            // 例外：CANCELLED + FAILED 放到 HISTORY；但用户可能想从候选重试 —— 见下
            return "CANCELLED".equals(item.getInvoiceStatus())
                    && WriteOffStatus.FAILED.getCode().equals(item.getWriteOffStatus());
        }
        return !WriteOffStatus.SUCCESS.getCode().equals(item.getWriteOffStatus());
    }

    private BossDocumentListItem toListItem(EinvBossDocument document) {
        BossDocumentListItem item = new BossDocumentListItem();
        item.setDocumentId(document.getId());
        item.setInvoiceId(document.getInvoiceId());
        item.setPaymentId(document.getPaymentId());
        item.setIssueKind(document.getIssueKind());
        item.setIssueKindLabel(resolveIssueKindLabel(document.getIssueKind()));
        item.setPaymentAmt(document.getPaymentAmt());
        item.setBossBillNo(document.getBossBillNo());
        item.setAmountStatus(document.getAmountStatus());
        item.setBusNo(document.getBusNo());
        item.setCreatedAt(document.getCreatedAt());
        if (IssueKind.COM.getCode().equals(document.getIssueKind())) {
            item.setPatientName(document.getPayerName());
            item.setInvoiceStatus(StringUtils.hasText(document.getBossBillNo()) ? "ISSUED" : "DRAFT");
        } else {
            BossIssueSnapshot snapshot = issueSnapshotContextLoader.loadForInvoice(document.getInvoiceId());
            item.setPatientName(snapshot.getPatientName());
            item.setInvoiceStatus(snapshot.getInvoiceStatus());
            item.setIssuedAt(snapshot.getIssuedAt());
        }
        BossWriteOffStatusSupport.WriteOffDisplay display =
                writeOffStatusSupport.resolve(document, item.getInvoiceStatus());
        item.setWriteOffStatus(display.getWriteOffStatus());
        item.setWriteOffStatusLabel(display.getWriteOffStatusLabel());
        item.setWriteOffBillNo(
                StringUtils.hasText(display.getWriteOffBillNo())
                        ? display.getWriteOffBillNo()
                        : document.getWriteOffBillNo());
        item.setWriteOffAt(display.getWriteOffAt() != null ? display.getWriteOffAt() : document.getWriteOffAt());
        item.setWriteOffMessage(
                StringUtils.hasText(display.getWriteOffMessage())
                        ? display.getWriteOffMessage()
                        : document.getWriteOffMessage());
        return item;
    }

    private String resolveIssueKindLabel(String issueKind) {
        if (IssueKind.REG.getCode().equals(issueKind)) {
            return "挂号";
        }
        if (IssueKind.IMP.getCode().equals(issueKind)) {
            return "住院";
        }
        if (IssueKind.COM.getCode().equals(issueKind)) {
            return "通用票";
        }
        return "门诊";
    }
}

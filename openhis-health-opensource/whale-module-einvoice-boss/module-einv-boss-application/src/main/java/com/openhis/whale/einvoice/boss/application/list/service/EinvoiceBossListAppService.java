/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.list.service;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.einvoice.boss.gateway.config.EinvoiceBossGatewayProperties;
import com.openhis.whale.einvoice.boss.application.list.response.AccountStatusResponse;
import com.openhis.whale.einvoice.boss.application.list.response.BossDocumentListItem;
import com.openhis.whale.einvoice.boss.application.list.response.PageDocumentsResponse;
import com.openhis.whale.einvoice.boss.application.list.request.AccountStatusRequest;
import com.openhis.whale.einvoice.boss.application.list.request.PageDocumentsRequest;
import com.openhis.whale.einvoice.boss.application.support.BossTenantSupport;
import com.openhis.whale.einvoice.boss.domain.list.access.BossAccountStatusQueryAccess;
import com.openhis.whale.einvoice.boss.domain.list.result.BossAccountStatusResult;
import com.openhis.whale.einvoice.boss.application.internal.issue.BossWriteOffStatusSupport;
import com.openhis.whale.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.enums.IssueKind;
import com.openhis.whale.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
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

/**
 * 电子发票列表页应用服务（HTTP：{@code /api/app/einvoiceBossList/*}）。
 *
 * <p>分页查询 boss 单据、关键词跨 health 检索、博思入账状态查询（委托 {@link BossAccountQueryService}）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EinvoiceBossListAppService {

    private final EinvBossDocumentRepository documentRepository;
    private final EinvoiceIssueSnapshotContextLoader issueSnapshotContextLoader;
    private final BossAccountStatusQueryAccess accountStatusQueryAccess;
    private final BossWriteOffStatusSupport writeOffStatusSupport;
    private final EinvoiceBossGatewayProperties properties;

    /**
     * 电子发票分页查询。
     *
     * @param request 筛选与分页
     * @return 分页结果
     */
    public PageDocumentsResponse pageDocuments(PageDocumentsRequest request) {
        PageDocumentsRequest safe = request == null ? new PageDocumentsRequest() : request;
        int pageNo = safe.getPageNo() == null || safe.getPageNo() < 1 ? 1 : safe.getPageNo();
        int pageSize = safe.getPageSize() == null || safe.getPageSize() < 1 ? 20 : safe.getPageSize();

        Specification<EinvBossDocument> spec = buildDocumentPageSpec(safe);
        Page<EinvBossDocument> page = documentRepository.findAll(
                spec,
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        PageDocumentsResponse response = new PageDocumentsResponse();
        response.setTotal(page.getTotalElements());
        List<BossDocumentListItem> rows = new ArrayList<>();
        for (EinvBossDocument document : page.getContent()) {
            try {
                rows.add(toListItem(document));
            } catch (Exception ex) {
                log.warn("List row enrich failed, documentId={}", document.getId(), ex);
            }
        }
        response.setRows(rows);
        response.setKeywordTruncated(resolveKeywordTruncated(safe));
        return response;
    }

    /**
     * 关键词检索是否因达到 {@code keywordFilterLimit} 而被截断（前端提示「结果不完整」）。
     *
     * @param filter 分页筛选条件
     * @return 命中数 ≥ limit 时为 true
     */
    private boolean resolveKeywordTruncated(PageDocumentsRequest filter) {
        if (filter == null || (!StringUtils.hasText(filter.getKeyword()) && !StringUtils.hasText(filter.getInvoiceStatus()))) {
            return false;
        }
        int limit = properties.getList().getKeywordFilterLimit();
        List<Long> invoiceIds = issueSnapshotContextLoader.findInvoiceIdsByKeyword(
                filter.getKeyword(), filter.getInvoiceStatus(), limit);
        return invoiceIds.size() >= limit;
    }

    /**
     * 查询博思入账状态。
     *
     * @param request 票面或单据主键
     * @return 入账状态
     */
    public AccountStatusResponse getAccountStatus(AccountStatusRequest request) {
        if (request == null) {
            throw new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "请求不能为空");
        }
        String billBatchCode = request.getBillBatchCode();
        String billNo = request.getBillNo();
        if (request.getDocumentId() != null) {
            EinvBossDocument document = documentRepository.findById(request.getDocumentId())
                    .filter(doc -> !Boolean.TRUE.equals(doc.getIsDeleted()))
                    .orElseThrow(() -> new BusinessException(
                            EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "单据不存在"));
            billBatchCode = document.getBossBatchCode();
            billNo = document.getBossBillNo();
        }
        if (!StringUtils.hasText(billBatchCode) || !StringUtils.hasText(billNo)) {
            throw new BusinessException(
                    EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "请先开具电子票后再查询入账状态");
        }
        BossAccountStatusResult boss = accountStatusQueryAccess.query(
                BossTenantSupport.requireCurrentTenantId(), billBatchCode, billNo);
        AccountStatusResponse response = new AccountStatusResponse();
        response.setBillBatchCode(billBatchCode);
        response.setBillNo(billNo);
        response.setBossSuccess(boss.isSuccess());
        response.setBossResultCode(boss.getResultCode());
        response.setBossMessage(boss.getRawMessage());
        response.setAccountStatus(boss.getAccountStatus());
        response.setAccountStatusLabel(resolveAccountStatusLabel(boss.getAccountStatus()));
        response.setAccountDate(boss.getAccountDate());
        return response;
    }

    /** 构建列表页 JPA Specification（issueKind、日期、关键词 in 子查询）。 */
    private Specification<EinvBossDocument> buildDocumentPageSpec(PageDocumentsRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.or(cb.isFalse(root.get("isDeleted")), cb.isNull(root.get("isDeleted"))));

            if (StringUtils.hasText(filter.getIssueKind())) {
                predicates.add(cb.equal(root.get("issueKind"), filter.getIssueKind().trim()));
            }
            if (StringUtils.hasText(filter.getBillNo())) {
                predicates.add(cb.like(root.get("bossBillNo"), "%" + filter.getBillNo().trim() + "%"));
            }
            if (filter.getDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getDateFrom().atStartOfDay()));
            }
            if (filter.getDateTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), filter.getDateTo().plusDays(1).atStartOfDay()));
            }
            if (StringUtils.hasText(filter.getKeyword()) || StringUtils.hasText(filter.getInvoiceStatus())) {
                int limit = properties.getList().getKeywordFilterLimit();
                List<Long> invoiceIds = issueSnapshotContextLoader.findInvoiceIdsByKeyword(
                        filter.getKeyword(), filter.getInvoiceStatus(), limit);
                if (invoiceIds.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("invoiceId").in(invoiceIds));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 单据 + health 快照 → 列表行（含冲红展示）。 */
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
            String invoiceStatus = StringUtils.hasText(document.getBossBillNo()) ? "ISSUED" : "DRAFT";
            item.setInvoiceStatus(invoiceStatus);
            applyWriteOffDisplay(item, document, invoiceStatus);
            return item;
        }
        BossIssueSnapshot snapshot = issueSnapshotContextLoader.loadForInvoice(document.getInvoiceId());
        item.setPatientName(snapshot.getPatientName());
        item.setInvoiceStatus(snapshot.getInvoiceStatus());
        item.setIssuedAt(snapshot.getIssuedAt());
        applyWriteOffDisplay(item, document, snapshot.getInvoiceStatus());
        return item;
    }

    /** 将冲红解析结果写入列表行 DTO。 */
    private void applyWriteOffDisplay(
            BossDocumentListItem item, EinvBossDocument document, String invoiceStatus) {
        BossWriteOffStatusSupport.WriteOffDisplay display = writeOffStatusSupport.resolve(document, invoiceStatus);
        item.setWriteOffStatus(display.getWriteOffStatus());
        item.setWriteOffStatusLabel(display.getWriteOffStatusLabel());
        item.setWriteOffBillNo(display.getWriteOffBillNo());
        item.setWriteOffAt(display.getWriteOffAt());
        item.setWriteOffMessage(display.getWriteOffMessage());
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

    /** 博思入账状态码 → 中文标签。 */
    private String resolveAccountStatusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "未知";
        }
        return switch (status) {
            case "0", "00" -> "未入账";
            case "1", "01" -> "已入账";
            case "2", "02" -> "入账失败";
            default -> status;
        };
    }
}

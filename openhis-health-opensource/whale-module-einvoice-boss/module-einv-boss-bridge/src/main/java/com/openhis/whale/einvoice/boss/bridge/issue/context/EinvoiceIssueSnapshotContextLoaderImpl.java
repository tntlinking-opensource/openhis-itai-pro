/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.issue.context;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.invoice.entity.Invoice;
import com.openhis.health.domain.administration.invoice.service.InvoiceQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.enums.InvoiceStatus;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossChargeLineSnapshot;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.reconcile.result.ReconcileLocalStats;
import com.openhis.whale.einvoice.boss.domain.enums.ReconcileDateMode;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.issue.repository.EinvBossIssueTaskRepository;
import com.openhis.whale.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link EinvoiceIssueSnapshotContextLoader} 的 bridge adapter 实现。
 *
 * <p>聚合 health 票据、支付、患者、就诊、收费明细、组织机构等读模型，供 Worker 组装博思报文、
 * 对账统计、补偿扫描。实现类标注 {@code @Primary}，为 domain 端口的默认 Bean。</p>
 */
@Component
@Primary
@RequiredArgsConstructor
public class EinvoiceIssueSnapshotContextLoaderImpl implements EinvoiceIssueSnapshotContextLoader {

    private final EinvoiceInvoiceContextLoader invoiceContextLoader;
    private final EinvoicePaymentContextLoader paymentContextLoader;
    private final EinvoicePatientEncounterContextLoader patientEncounterContextLoader;
    private final EinvoiceChargeItemContextLoader chargeItemContextLoader;
    private final OrganizationQueryService organizationQueryService;
    private final InvoiceQueryService invoiceQueryService;
    private final PatientQueryService patientQueryService;
    private final PaymentReconciliationQueryService paymentQueryService;
    private final EinvBossDocumentRepository documentRepository;
    private final EinvBossIssueTaskRepository issueTaskRepository;

    /**
     * {@inheritDoc}
     *
     * <p>加载顺序：invoice → payment → patient → encounter → 医保类型 → 科室 → 收费明细行。</p>
     */
    @Override
    public BossIssueSnapshot loadForInvoice(Long invoiceId) {
        // Pre：加载 health 票据、支付、患者、就诊
        Invoice invoice = invoiceContextLoader.requireById(invoiceId);
        PaymentReconciliation payment = paymentContextLoader.requireById(invoice.getPaymentId());
        Patient patient = patientEncounterContextLoader.requirePatient(invoice.getPatientId());
        Encounter encounter = patientEncounterContextLoader.requireEncounter(payment.getEncounterId());

        BossIssueSnapshot snapshot = new BossIssueSnapshot();
        snapshot.setInvoiceId(invoice.getId());
        snapshot.setPaymentId(payment.getId());
        snapshot.setPatientId(patient.getId());
        snapshot.setEncounterId(encounter.getId());
        snapshot.setTenantId(invoice.getTenantId());
        snapshot.setInvoiceTypeCode(invoice.getTypeCode());
        snapshot.setInvoiceStatus(invoice.getStatusEnum());
        snapshot.setBusNo(invoice.getBusNo());
        snapshot.setHealthBillNo(invoice.getBillNo());
        snapshot.setIssuedAt(invoice.getIssuedAt());
        snapshot.setTotalAmt(invoice.getAmount());
        snapshot.setPaymentAmt(payment.getDisplayAmount());
        snapshot.setPatientName(patient.getName());
        snapshot.setGenderDisplay(patientEncounterContextLoader.toGenderDisplay(patient.getGenderEnum()));
        snapshot.setAge(patientEncounterContextLoader.resolveAge(patient.getBirthDate()));
        snapshot.setIdCard(patient.getIdCard());
        snapshot.setPhone(patient.getPhone());
        snapshot.setEncounterBusNo(encounter.getBusNo());
        snapshot.setHospitalNo(encounter.getBusNo());
        snapshot.setVisitNo(encounter.getBusNo());
        snapshot.setBillDateTime(payment.getBillDate() != null ? payment.getBillDate() : payment.getCreatedAt());
        snapshot.setOperatorId(payment.getEntererId());

        BossMedicalCareMapping.MedCare medCare = BossMedicalCareMapping.resolve(
                encounter.getYbClassEnum() != null ? encounter.getYbClassEnum() : encounter.getYbClassText());
        snapshot.setMedCareTypeCode(medCare.typeCode());
        snapshot.setMedicalCareType(medCare.typeName());
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(encounter.getOrganizationId());
        if (organization != null) {
            snapshot.setPatientCategory(organization.getName());
            snapshot.setPatientCategoryCode(organization.getBusNo());
        }

        boolean registration = chargeItemContextLoader.isRegistrationInvoice(invoice.getTypeCode());
        List<Long> chargeItemIds = ChargeItemIdsMapping.parse(payment.getChargeItemIds());
        List<BossChargeLineSnapshot> lines = chargeItemContextLoader.loadByIds(chargeItemIds, registration);
        snapshot.setChargeLines(lines);
        return snapshot;
    }

    /** {@inheritDoc} — 事件丢失兜底：DRAFT 且无活跃 task。 */
    @Override
    public List<Long> findOrphanDraftInvoiceIds(int limit) {
        Specification<Invoice> spec = (root, query, cb) -> {
            query.orderBy(cb.asc(root.get("createdAt")));
            return cb.and(
                    cb.equal(root.get("statusEnum"), InvoiceStatus.DRAFT.getCode()),
                    cb.or(cb.isFalse(root.get("isDeleted")), cb.isNull(root.get("isDeleted")))
            );
        };
        List<Invoice> drafts = invoiceQueryService.pageInvoiceEntities(spec, PageRequest.of(0, Math.max(limit * 5, limit))).getContent();
        List<Long> result = new ArrayList<>();
        for (Invoice invoice : drafts) {
            if (result.size() >= limit) {
                break;
            }
            if (issueTaskRepository.findByInvoiceIdAndIsDeletedFalse(invoice.getId()).isEmpty()) {
                result.add(invoice.getId());
            }
        }
        return result;
    }

    /** {@inheritDoc} — 超时待查：创建时间早于阈值的 DRAFT。 */
    @Override
    public List<Long> findDraftInvoiceIdsBefore(LocalDateTime threshold, int limit) {
        Specification<Invoice> spec = (root, query, cb) -> {
            query.orderBy(cb.asc(root.get("createdAt")));
            return cb.and(
                    cb.equal(root.get("statusEnum"), InvoiceStatus.DRAFT.getCode()),
                    cb.lessThan(root.get("createdAt"), threshold),
                    cb.or(cb.isFalse(root.get("isDeleted")), cb.isNull(root.get("isDeleted")))
            );
        };
        List<Invoice> drafts = invoiceQueryService.pageInvoiceEntities(spec, PageRequest.of(0, limit)).getContent();
        List<Long> result = new ArrayList<>();
        for (Invoice invoice : drafts) {
            result.add(invoice.getId());
        }
        return result;
    }

    /** {@inheritDoc} — 列表页关键词（busNo/患者名）与票据状态组合筛选。 */
    @Override
    public List<Long> findInvoiceIdsByKeyword(String keyword, String invoiceStatus, int limit) {
        Set<Long> invoiceIds = new HashSet<>();
        String kw = keyword == null ? "" : keyword.trim();
        if (StringUtils.hasText(kw)) {
            Specification<Invoice> busSpec = (root, query, cb) -> cb.and(
                    cb.like(root.get("busNo"), "%" + kw + "%"),
                    cb.or(cb.isFalse(root.get("isDeleted")), cb.isNull(root.get("isDeleted")))
            );
            invoiceQueryService.pageInvoiceEntities(busSpec, PageRequest.of(0, limit)).forEach(inv -> invoiceIds.add(inv.getId()));

            List<Long> patientIds = patientQueryService.listPatientIdsByNameKeyword(kw, 50);
            if (!patientIds.isEmpty()) {
                Specification<Invoice> patientInvoiceSpec = (root, query, cb) -> cb.and(
                        root.get("patientId").in(patientIds),
                        cb.or(cb.isFalse(root.get("isDeleted")), cb.isNull(root.get("isDeleted")))
                );
                invoiceQueryService.pageInvoiceEntities(patientInvoiceSpec, PageRequest.of(0, limit))
                        .forEach(inv -> invoiceIds.add(inv.getId()));
            }
        }
        if (StringUtils.hasText(invoiceStatus)) {
            Specification<Invoice> statusSpec = (root, query, cb) -> cb.and(
                    cb.equal(root.get("statusEnum"), invoiceStatus),
                    cb.or(cb.isFalse(root.get("isDeleted")), cb.isNull(root.get("isDeleted")))
            );
            List<Long> statusIds = invoiceQueryService.pageInvoiceEntities(statusSpec, PageRequest.of(0, limit))
                    .stream().map(Invoice::getId).collect(Collectors.toList());
            if (StringUtils.hasText(kw) && !invoiceIds.isEmpty()) {
                invoiceIds.retainAll(new HashSet<>(statusIds));
            } else if (!StringUtils.hasText(kw)) {
                invoiceIds.addAll(statusIds);
            }
        }
        return invoiceIds.stream().limit(limit).collect(Collectors.toList());
    }

    /** {@inheritDoc} — 日结核对本地侧：按 BUS_DATE 或 IVC_DATE 统计已开具笔数/金额。 */
    @Override
    public ReconcileLocalStats countDailyReconcileStats(LocalDate checkDate, String dateMode, String issueKind) {
        ReconcileLocalStats stats = new ReconcileLocalStats();
        if (checkDate == null) {
            return stats;
        }
        LocalDateTime start = checkDate.atStartOfDay();
        LocalDateTime end = checkDate.plusDays(1).atStartOfDay().minusNanos(1);

        List<Invoice> invoices;
        if (ReconcileDateMode.IVC_DATE.getCode().equals(dateMode)) {
            Specification<Invoice> spec = (root, query, cb) -> cb.and(
                    cb.equal(root.get("statusEnum"), InvoiceStatus.ISSUED.getCode()),
                    cb.greaterThanOrEqualTo(root.get("issuedAt"), start),
                    cb.lessThanOrEqualTo(root.get("issuedAt"), end),
                    cb.or(cb.isFalse(root.get("isDeleted")), cb.isNull(root.get("isDeleted")))
            );
            invoices = invoiceQueryService.listInvoiceEntities(spec);
        } else {
            List<PaymentReconciliation> payments = paymentQueryService.listPaymentEntitiesByBillDateBetween(start, end);
            Set<Long> paymentIds = payments.stream().map(PaymentReconciliation::getId).collect(Collectors.toSet());
            if (paymentIds.isEmpty()) {
                return stats;
            }
            Specification<Invoice> spec = (root, query, cb) -> cb.and(
                    cb.equal(root.get("statusEnum"), InvoiceStatus.ISSUED.getCode()),
                    root.get("paymentId").in(paymentIds),
                    cb.or(cb.isFalse(root.get("isDeleted")), cb.isNull(root.get("isDeleted")))
            );
            invoices = invoiceQueryService.listInvoiceEntities(spec);
        }

        if (StringUtils.hasText(issueKind)) {
            Set<Long> invoiceIds = invoices.stream().map(Invoice::getId).collect(Collectors.toSet());
            if (invoiceIds.isEmpty()) {
                return stats;
            }
            Specification<EinvBossDocument> docSpec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(root.get("invoiceId").in(invoiceIds));
                predicates.add(cb.equal(root.get("issueKind"), issueKind.trim()));
                predicates.add(cb.or(cb.isFalse(root.get("isDeleted")), cb.isNull(root.get("isDeleted"))));
                return cb.and(predicates.toArray(new Predicate[0]));
            };
            Set<Long> matchedIds = documentRepository.findAll(docSpec).stream()
                    .map(EinvBossDocument::getInvoiceId)
                    .collect(Collectors.toSet());
            invoices = invoices.stream().filter(inv -> matchedIds.contains(inv.getId())).collect(Collectors.toList());
        }

        BigDecimal totalAmt = BigDecimal.ZERO;
        for (Invoice invoice : invoices) {
            if (invoice.getAmount() != null) {
                totalAmt = totalAmt.add(invoice.getAmount());
            }
        }
        stats.setIssuedCount(invoices.size());
        stats.setIssuedTotalAmt(totalAmt);
        return stats;
    }
}

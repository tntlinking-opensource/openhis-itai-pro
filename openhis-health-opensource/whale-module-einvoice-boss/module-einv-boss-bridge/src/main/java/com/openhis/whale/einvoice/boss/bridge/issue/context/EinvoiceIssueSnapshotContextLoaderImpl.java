/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.issue.context;

import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.invoice.entity.Invoice;
import com.openhis.health.domain.administration.invoice.service.InvoiceQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.PaymentYbSettlementStatus;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentYbSettlementQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.enums.InvoiceStatus;
import com.openhis.health.infrastructure.integration.insurance.settlement.InsuranceSettlementRecordLookupExtension;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionOut.InsuranceSettlementFundLookupExtensionOut;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossChargeLineSnapshot;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.reconcile.result.ReconcileLocalStats;
import com.openhis.whale.einvoice.boss.domain.enums.ReconcileDateMode;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.issue.repository.EinvBossIssueTaskRepository;
import com.openhis.whale.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader;
import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link EinvoiceIssueSnapshotContextLoader} 的 bridge adapter 实现。
 *
 * <p>聚合 health 票据、支付、患者、就诊、收费明细、组织机构、医保结算基金等读模型，供 Worker 组装博思报文、
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
    private final PractitionerQueryService practitionerQueryService;
    private final PaymentReconciliationQueryService paymentQueryService;
    private final AccountQueryService accountQueryService;
    private final ContractQueryService contractQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final InsuranceSettlementRecordLookupExtension settlementRecordLookupExtension;
    private final EinvBossDocumentRepository documentRepository;
    private final EinvBossIssueTaskRepository issueTaskRepository;
    private final TenantContext tenantContext;

    /**
     * {@inheritDoc}
     *
     * <p>加载顺序：invoice → payment → patient → encounter → 费用性质/基金 → 科室 → 收费明细行。</p>
     *
     * <p>异步 Worker（{@code @Async}）无线程租户时，用票据 {@code tenantId} 强制绑定，
     * 并以该租户显式查医保基金 SPI；否则 SPI 空返回并误走全自费回落，产品甲乙丙/药名也可能查空。</p>
     */
    @Override
    public BossIssueSnapshot loadForInvoice(Long invoiceId) {
        Invoice invoice = invoiceContextLoader.requireById(invoiceId);
        Long previousTenantId = TenantUtils.getCurrentTenantId();
        boolean tenantBound = bindInvoiceTenant(invoice.getTenantId(), previousTenantId);
        try {
            return loadSnapshot(invoice);
        } finally {
            if (tenantBound) {
                if (previousTenantId == null) {
                    tenantContext.clear();
                } else {
                    tenantContext.setCurrentTenantId(previousTenantId);
                }
            }
        }
    }

    private BossIssueSnapshot loadSnapshot(Invoice invoice) {
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
        // Pre：医保预交金冲抵后 payment.displayAmount 为少补(可为 0)；开票支付额取票面，避免 payment_amt=0
        BigDecimal paymentAmt = payment.getDisplayAmount();
        if (paymentAmt == null || paymentAmt.compareTo(BigDecimal.ZERO) <= 0) {
            if (invoice.getAmount() != null && invoice.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                paymentAmt = invoice.getAmount();
            }
        }
        snapshot.setPaymentAmt(paymentAmt);
        snapshot.setPatientName(patient.getName());
        snapshot.setGenderDisplay(patientEncounterContextLoader.toGenderDisplay(patient.getGenderEnum()));
        snapshot.setAge(patientEncounterContextLoader.resolveAge(patient.getBirthDate()));
        snapshot.setIdCard(patient.getIdCard());
        snapshot.setPhone(patient.getPhone());
        snapshot.setEncounterBusNo(encounter.getBusNo());
        snapshot.setCaseNumber(encounter.getBusNo());
        snapshot.setHospitalNo(encounter.getBusNo());
        snapshot.setVisitNo(encounter.getBusNo());
        snapshot.setBillDateTime(payment.getBillDate() != null ? payment.getBillDate() : payment.getCreatedAt());
        snapshot.setOperatorId(payment.getEntererId());
        snapshot.setOperatorName(resolveOperatorName(payment.getEntererId()));

        // Pre：显式传入票据租户，避免 @Async 无线程租户时基金 SPI 空返回
        Optional<InsuranceSettlementFundLookupExtensionOut> fundOpt =
            settlementRecordLookupExtension.findFundByPaymentId(payment.getId(), invoice.getTenantId());
        applyFundAndMedCare(snapshot, payment, fundOpt);

        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(encounter.getOrganizationId());
        if (organization != null) {
            snapshot.setPatientCategory(organization.getName());
            snapshot.setPatientCategoryCode(organization.getBusNo());
        }
        // Pre：住院票博思必填住院/出院日期（EYD-ESA-0109 inHospitalDate）
        snapshot.setInHospitalDateTime(encounter.getStartTime());
        LocalDateTime outAt = encounter.getEndTime();
        if (outAt == null) {
            outAt = snapshot.getBillDateTime();
        }
        snapshot.setOutHospitalDateTime(outAt);

        boolean registration = chargeItemContextLoader.isRegistrationInvoice(invoice.getTypeCode());
        List<Long> chargeItemIds = ChargeItemIdsMapping.parse(payment.getChargeItemIds());
        List<BossChargeLineSnapshot> lines = chargeItemContextLoader.loadByIds(chargeItemIds, registration);
        snapshot.setChargeLines(lines);
        return snapshot;
    }

    /**
     * 将当前线程租户强制对齐为票据租户（即便已是同一值也重新 set，避免线程池残留会话状态）。
     *
     * @param invoiceTenantId 票据租户；可空
     * @param previousTenantId 调用前线程租户；可空
     * @return true 表示本方法改写了租户，调用方须恢复 {@code previousTenantId} 或 clear
     */
    private boolean bindInvoiceTenant(Long invoiceTenantId, Long previousTenantId) {
        if (invoiceTenantId == null) {
            return false;
        }
        // 强制写入：异步线程「已是同租户」时仍可能未绑定 Hibernate/SPI 可见上下文
        tenantContext.setCurrentTenantId(invoiceTenantId);
        if (invoiceTenantId.equals(previousTenantId)) {
            return false;
        }
        return true;
    }

    /**
     * 写入基金分项与票头医保类型（省/市医保等）。
     *
     * @param snapshot 开票快照
     * @param payment  支付实体（含 contractNo，费用性质兜底）
     * @param fundOpt  结算基金；可空
     */
    private void applyFundAndMedCare(
            BossIssueSnapshot snapshot,
            PaymentReconciliation payment,
            Optional<InsuranceSettlementFundLookupExtensionOut> fundOpt) {
        BossContractCategoryMapping.MedCare medCare = resolveMedCare(payment, fundOpt);
        snapshot.setMedCareTypeCode(medCare.typeCode());
        snapshot.setMedicalCareType(medCare.typeName());

        if (fundOpt.isEmpty()) {
            snapshot.setInsuranceSettled(false);
            return;
        }
        InsuranceSettlementFundLookupExtensionOut fund = fundOpt.get();
        snapshot.setInsuranceSettled(true);
        snapshot.setMedicalInsuranceId(fund.getPsnNo());
        snapshot.setAccountPay(scale2(fund.getAcctPay()));
        BigDecimal otherFundPay = sumOtherFundPay(
            fund.getCvlservPay(),
            fund.getHifesPay(),
            fund.getHifmiPay(),
            fund.getHifobPay(),
            fund.getMafPay(),
            fund.getHospPartAmt(),
            fund.getOthPay());
        // 博思 fundPay = 基本统筹；2304 缺 hifp 时用基金总额 − 其它基金回落（对齐门诊开票）
        snapshot.setFundPay(scale2(resolveHifpPayForInvoice(
            fund.getHifpPay(), fund.getFundPaySumamt(), otherFundPay)));
        snapshot.setOtherfundPay(scale2(otherFundPay));
        snapshot.setOwnPay(scale2(fund.getFulamtOwnpayAmt()));
        snapshot.setSelfConceitedAmt(scale2(fund.getActPayDedc()));
        snapshot.setSelfPayAmt(scale2(fund.getPreselfpayAmt()));
        snapshot.setSelfCashPay(scale2(fund.getPsnCashPay()));
        snapshot.setReimbursementAmt(scale2(fund.getFundPaySumamt()));
        snapshot.setInscpScpAmt(scale2(fund.getInscpScpAmt()));
        snapshot.setOverlmtSelfpay(scale2(fund.getOverlmtSelfpay()));
        snapshot.setCvlservPay(scale2(fund.getCvlservPay()));
        snapshot.setHifesPay(scale2(fund.getHifesPay()));
        snapshot.setHifmiPay(scale2(fund.getHifmiPay()));
        snapshot.setHifobPay(scale2(fund.getHifobPay()));
        snapshot.setMafPay(scale2(fund.getMafPay()));
        snapshot.setHospPartAmt(scale2(fund.getHospPartAmt()));
        snapshot.setOthPay(scale2(fund.getOthPay()));
        snapshot.setPoolPropSelfpay(fund.getPoolPropSelfpay());
    }

    /**
     * 开票用基本统筹：优先 hifp_pay；缺省时基金总额减去其它基金。
     *
     * @param hifpPay       基本统筹
     * @param fundPaySumamt 基金支付总额
     * @param otherFundPay  其它基金合计
     * @return 统筹金额
     */
    private static BigDecimal resolveHifpPayForInvoice(
            BigDecimal hifpPay,
            BigDecimal fundPaySumamt,
            BigDecimal otherFundPay) {
        if (hifpPay != null) {
            return hifpPay;
        }
        if (fundPaySumamt == null) {
            return null;
        }
        BigDecimal other = otherFundPay == null ? BigDecimal.ZERO : otherFundPay;
        BigDecimal resolved = fundPaySumamt.subtract(other);
        if (resolved.compareTo(BigDecimal.ZERO) < 0) {
            return fundPaySumamt;
        }
        return resolved;
    }

    /**
     * 其它医保支付：对齐博思规范七项之和（不含基本统筹、不含伤残 hifdm）。
     */
    private static BigDecimal sumOtherFundPay(
            BigDecimal cvlservPay,
            BigDecimal hifesPay,
            BigDecimal hifmiPay,
            BigDecimal hifobPay,
            BigDecimal mafPay,
            BigDecimal hospPartAmt,
            BigDecimal othPay) {
        return nullToZero(cvlservPay)
            .add(nullToZero(hifesPay))
            .add(nullToZero(hifmiPay))
            .add(nullToZero(hifobPay))
            .add(nullToZero(mafPay))
            .add(nullToZero(hospPartAmt))
            .add(nullToZero(othPay));
    }

    /**
     * 费用性质：结算合同 → 支付单 contractNo → 支付桥表账户合同 → 收费行账户合同 → 自费。
     *
     * @param payment 支付实体
     * @param fundOpt 结算基金
     * @return 博思票头医保类型
     */
    private BossContractCategoryMapping.MedCare resolveMedCare(
            PaymentReconciliation payment,
            Optional<InsuranceSettlementFundLookupExtensionOut> fundOpt) {
        if (fundOpt.isPresent() && fundOpt.get().getContractId() != null) {
            Optional<Contract> byId = contractQueryService.findOptionalContractEntityById(fundOpt.get().getContractId());
            if (byId.isPresent()) {
                return BossContractCategoryMapping.resolve(byId.get().getCategoryEnum());
            }
        }
        // 支付单头常直接落市医保/省医保 contract_no，优先于账户回查（账户路径偶发空）
        if (payment != null && StringUtils.hasText(payment.getContractNo())) {
            Optional<Contract> byPaymentContract =
                contractQueryService.findOptionalContractEntityByBusNo(payment.getContractNo().trim());
            if (byPaymentContract.isPresent()) {
                return BossContractCategoryMapping.resolve(byPaymentContract.get().getCategoryEnum());
            }
        }
        if (payment == null || payment.getId() == null) {
            return BossContractCategoryMapping.resolve(null);
        }
        PaymentReconciliationQueryModel paymentModel = paymentQueryService.mustGetPayment(payment.getId());
        Long accountId = firstConfirmedAccountId(paymentModel);
        if (accountId == null) {
            accountId = firstChargeItemAccountId(paymentModel.getChargeItemIds());
        }
        if (accountId != null) {
            Map<Long, Account> accounts = accountQueryService.mapAccountsByIds(List.of(accountId));
            Account account = accounts.get(accountId);
            if (account != null && StringUtils.hasText(account.getContractNo())) {
                Optional<Contract> byBusNo =
                    contractQueryService.findOptionalContractEntityByBusNo(account.getContractNo());
                if (byBusNo.isPresent()) {
                    return BossContractCategoryMapping.resolve(byBusNo.get().getCategoryEnum());
                }
            }
        }
        return BossContractCategoryMapping.resolve(null);
    }

    private static Long firstConfirmedAccountId(PaymentReconciliationQueryModel paymentModel) {
        if (paymentModel == null || paymentModel.getYbSettlements() == null) {
            return null;
        }
        for (PaymentYbSettlementQueryModel row : paymentModel.getYbSettlements()) {
            if (row == null || PaymentYbSettlementStatus.REVOKED.equals(row.getStatus())) {
                continue;
            }
            if (row.getAccountId() != null) {
                return row.getAccountId();
            }
        }
        return null;
    }

    private Long firstChargeItemAccountId(String chargeItemIdsCsv) {
        List<Long> ids = ChargeItemIdsMapping.parse(chargeItemIdsCsv);
        if (ids.isEmpty()) {
            return null;
        }
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setIds(ids);
        List<ChargeItem> rows = chargeItemQueryService.loadChargeItemsByIds(query);
        for (ChargeItem row : rows) {
            if (row != null && row.getAccountId() != null) {
                return row.getAccountId();
            }
        }
        return null;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal scale2(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 解析收费员展示名（entererId 多为 practitionerId，存量可能为 userId）。
     *
     * @param entererId 支付录入人
     * @return 姓名；未命中返回 null
     */
    private String resolveOperatorName(Long entererId) {
        if (entererId == null) {
            return null;
        }
        return practitionerQueryService.mapDisplayNameByEntererIds(List.of(entererId)).get(entererId);
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

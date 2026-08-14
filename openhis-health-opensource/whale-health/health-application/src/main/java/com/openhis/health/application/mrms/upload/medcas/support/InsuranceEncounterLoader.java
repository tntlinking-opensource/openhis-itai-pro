/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.medcas.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.invoice.entity.Invoice;
import com.openhis.health.domain.administration.invoice.query.InvoiceQueryCommand;
import com.openhis.health.domain.administration.invoice.service.InvoiceQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentYbSettlementQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordHomepageDomainService;
import com.openhis.health.infrastructure.integration.insurance.benefit.InsuranceRegisterBenefitQueryExtension;
import com.openhis.health.infrastructure.integration.insurance.settlement.InsuranceSettlementRecordLookupExtension;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionOut.InsuranceSettlementFundLookupExtensionOut;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 医保上报上下文解析（支付流水 + 结算回执 + 票据 + 就诊 psn_no）。
 *
 * <p>供 4401/4101A/4102 共用；不经医保网关写交易。</p>
 */
@Component
@RequiredArgsConstructor
public class InsuranceEncounterLoader {

    private final AccountQueryService accountQueryService;
    private final ContractQueryService contractQueryService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final InvoiceQueryService invoiceQueryService;
    private final CaseRecordHomepageDomainService caseRecordHomepageDomainService;
    private final InsuranceRegisterBenefitQueryExtension insuranceRegisterBenefitQueryExtension;
    private final InsuranceSettlementRecordLookupExtension insuranceSettlementRecordLookupExtension;

    /**
     * 判断就诊是否为医保住院（存在有效医保合同账户）。
     *
     * @param encounterId 就诊主键
     * @return true 表示医保住院患者
     */
    public boolean isInsuranceInpatient(long encounterId) {
        Account ybAccount = accountQueryService.getYbAccount(encounterId);
        // 无医保账户或合同号时视为非医保住院
        if (ybAccount == null || trimToNull(ybAccount.getContractNo()) == null) {
            return false;
        }
        Optional<Contract> contract = contractQueryService.findOptionalContractEntityByBusNo(ybAccount.getContractNo());
        return contract.isPresent() && Boolean.TRUE.equals(contract.get().getYbFlag());
    }

    /**
     * 按合同业务号解析上报所需医保上下文（mdtrt_id/setl_id/psn_no/票据等）。
     *
     * @param encounterId 就诊主键
     * @param contractBusNo 合同业务号
     * @param caseRecordId 病案主档主键（读首页 extension 票据代码），可为 null
     * @return 上下文；非医保或无 mdtrt_id/setl_id 时 empty
     */
    public Optional<InsuranceEncounterContext> findContext(long encounterId, String contractBusNo, Long caseRecordId) {
        // contractBusNo 为空无法定位合同
        if (trimToNull(contractBusNo) == null) {
            return Optional.empty();
        }
        Optional<Contract> contractOpt = contractQueryService.findOptionalContractEntityByBusNo(contractBusNo);
        // 非医保合同不组装上下文
        if (contractOpt.isEmpty() || !Boolean.TRUE.equals(contractOpt.get().getYbFlag())) {
            return Optional.empty();
        }
        Contract contract = contractOpt.get();
        SettledYbSlice settled = resolveSettledYb(encounterId, contractBusNo);
        // 支付流水无 mdtrt_id/setl_id 时不返回上下文（未医保结算）
        if (settled == null
                || trimToNull(settled.mdtrtId) == null
                || trimToNull(settled.setlId) == null) {
            return Optional.empty();
        }
        InsuranceEncounterContext ctx = new InsuranceEncounterContext();
        ctx.setMdtrtId(settled.mdtrtId);
        ctx.setSetlId(settled.setlId);
        ctx.setPaymentId(settled.paymentId);
        ctx.setSettlementRecordId(settled.settlementRecordId);
        ctx.setFixmedinsCode(trimToNull(contract.getFixmedinsCode()));
        ctx.setFixmedinsName(trimToNull(contract.getFixmedinsName()));
        ctx.setInsuplcAdmdvs(trimToNull(contract.getAdmVs()));
        if (settled.billDate != null) {
            ctx.setSetlBegnDate(settled.billDate.toLocalDate());
            ctx.setSetlEndDate(settled.billDate.toLocalDate());
        }
        // Pre：psn_no 优先结算回执，其次就诊平台映射
        ctx.setPsnNo(resolvePsnNo(encounterId, settled.paymentId));
        // Pre：票据三要素
        fillInvoiceFields(ctx, settled.paymentId, caseRecordId, settled.chrgBchno);
        return Optional.of(ctx);
    }

    /**
     * 兼容旧调用：不读首页 extension 的票据代码。
     *
     * @param encounterId 就诊主键
     * @param contractBusNo 合同业务号
     * @return 上下文
     */
    public Optional<InsuranceEncounterContext> findContext(long encounterId, String contractBusNo) {
        return findContext(encounterId, contractBusNo, null);
    }

    /**
     * 从支付成功/退款中流水解析已结算医保切片（mdtrt_id/setl_id）。
     *
     * @param encounterId 就诊主键
     * @param contractBusNo 合同业务号
     * @return 结算切片；未找到时 null
     */
    private SettledYbSlice resolveSettledYb(long encounterId, String contractBusNo) {
        List<PaymentReconciliationQueryModel> payments =
                paymentReconciliationQueryService.findPaySuccessOrRefundingByEncounter(encounterId);
        for (PaymentReconciliationQueryModel payment : payments) {
            // 合同号不一致的流水跳过
            if (!contractBusNo.equals(trimToNull(payment.getContractNo()))) {
                continue;
            }
            if (payment.getYbSettlements() == null) {
                continue;
            }
            for (PaymentYbSettlementQueryModel yb : payment.getYbSettlements()) {
                // 取首个同时具备 mdtrt_id 与 setl_id 的确认结算
                if (trimToNull(yb.getMdtrtId()) == null || trimToNull(yb.getSetlId()) == null) {
                    continue;
                }
                SettledYbSlice slice = new SettledYbSlice();
                slice.mdtrtId = trimToNull(yb.getMdtrtId());
                slice.setlId = trimToNull(yb.getSetlId());
                slice.paymentId = payment.getId();
                slice.settlementRecordId = yb.getSettlementRecordId();
                slice.chrgBchno = trimToNull(yb.getChrgBchno());
                slice.billDate = payment.getBillDate();
                return slice;
            }
        }
        return null;
    }

    /**
     * 解析医保人员编号：结算基金回执 → 就诊平台映射。
     *
     * @param encounterId 就诊主键
     * @param paymentId 支付单主键
     * @return psn_no，可能为 null
     */
    private String resolvePsnNo(long encounterId, Long paymentId) {
        // 结算回执带 psn_no
        if (paymentId != null && insuranceSettlementRecordLookupExtension.isEnabled()) {
            Optional<InsuranceSettlementFundLookupExtensionOut> fund =
                    insuranceSettlementRecordLookupExtension.findFundByPaymentId(paymentId);
            if (fund.isPresent() && trimToNull(fund.get().getPsnNo()) != null) {
                return trimToNull(fund.get().getPsnNo());
            }
        }
        // 就诊级平台映射
        Map<Long, String> byEncounter = insuranceRegisterBenefitQueryExtension.mapPsnNoByEncounterIds(
                Collections.singletonList(encounterId));
        return trimToNull(byEncounter.get(encounterId));
    }

    /**
     * 填充 4101A 票据三要素（bill_code/bill_no/biz_sn）。
     *
     * @param ctx 上下文
     * @param paymentId 支付单
     * @param caseRecordId 病案（读 extension.bill_code）
     * @param chrgBchno 收费批次号（biz_sn 兜底）
     */
    private void fillInvoiceFields(
            InsuranceEncounterContext ctx, Long paymentId, Long caseRecordId, String chrgBchno) {
        // 首页扩展可手工维护票据代码（电子票批次未回写到 adm_invoice 时）
        String extBillCode = readHomepageExt(caseRecordId, "bill_code");
        if (trimToNull(extBillCode) != null) {
            ctx.setBillCode(trimToNull(extBillCode));
        }
        if (paymentId == null) {
            // 无支付单时仅用 extension / 收费批次兜底业务流水
            if (trimToNull(ctx.getBizSn()) == null) {
                ctx.setBizSn(chrgBchno);
            }
            return;
        }
        InvoiceQueryCommand cmd = new InvoiceQueryCommand();
        cmd.setPaymentId(paymentId);
        Invoice issued = invoiceQueryService.getLatestIssuedInvoiceByPaymentId(cmd);
        // 无已开具票时尝试任意票据
        if (issued == null) {
            issued = invoiceQueryService.getInvoiceByPaymentId(cmd);
        }
        if (issued != null) {
            ctx.setBillNo(trimToNull(issued.getBillNo()));
            ctx.setBizSn(trimToNull(issued.getBusNo()));
            // 无电子票批次列时：extension 优先；否则不造假，留给 Validator 报错
        }
        // biz_sn 仍空时用收费批次号
        if (trimToNull(ctx.getBizSn()) == null) {
            ctx.setBizSn(chrgBchno);
        }
    }

    /**
     * 读取首页 extension_json 字符串字段。
     *
     * @param caseRecordId 病案主键
     * @param key JSON 键
     * @return 字符串值，缺失时 null
     */
    private String readHomepageExt(Long caseRecordId, String key) {
        if (caseRecordId == null || trimToNull(key) == null) {
            return null;
        }
        Optional<CaseRecordHomepage> homepageOpt =
                caseRecordHomepageDomainService.findOptionalByCaseRecordId(caseRecordId);
        if (homepageOpt.isEmpty() || trimToNull(homepageOpt.get().getExtensionJson()) == null) {
            return null;
        }
        CaseRecordHomepage homepage = homepageOpt.get();
        try {
            JSONObject ext = JSON.parseObject(homepage.getExtensionJson());
            if (ext == null || !ext.containsKey(key)) {
                return null;
            }
            Object val = ext.get(key);
            if (val == null) {
                return null;
            }
            return String.valueOf(val);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 已结算医保支付切片（Loader 内部临时结构）。
     */
    private static final class SettledYbSlice {
        /** 医保就诊 ID。 */
        private String mdtrtId;
        /** 结算 ID。 */
        private String setlId;
        /** 支付单主键。 */
        private Long paymentId;
        /** CHS 结算回执主键。 */
        private Long settlementRecordId;
        /** 收费批次号。 */
        private String chrgBchno;
        /** 账单日期。 */
        private java.time.LocalDateTime billDate;
    }
}

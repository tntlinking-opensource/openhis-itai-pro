/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.medcas.support;

import com.zxplat.domain.administration.account.entity.Account;
import com.zxplat.domain.administration.account.service.AccountQueryService;
import com.zxplat.domain.administration.invoice.entity.Invoice;
import com.zxplat.domain.administration.invoice.query.InvoiceQueryCommand;
import com.zxplat.domain.administration.invoice.service.InvoiceQueryService;
import com.zxplat.domain.financial.contract.entity.Contract;
import com.zxplat.domain.financial.contract.service.ContractQueryService;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.PaymentYbSettlementQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.zxplat.domain.infrastructure.integration.einvoice.BossElectronicInvoiceExtension;
import com.zxplat.domain.infrastructure.integration.einvoice.ExtensionOut.BossElectronicInvoiceDocumentLookupExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.InsuranceRegisterBenefitQueryExtension;
import com.zxplat.domain.infrastructure.integration.insurance.contract.InsuranceContractTerminalExtension;
import com.zxplat.domain.infrastructure.integration.insurance.contract.ExtensionOut.InsuranceContractTerminalQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.settlement.InsuranceSettlementRecordLookupExtension;
import com.zxplat.domain.infrastructure.integration.insurance.settlement.ExtensionOut.InsuranceSettlementFundLookupExtensionOut;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 医保上报上下文解析（支付流水 + 结算回执 + 票据 + 就诊 psn_no + 契约终端参数）。
 *
 * <p>供 4401/4101A/4102 共用；不经医保网关写交易。
 * {@code bill_code} 优先博思 {@code boss_batch_code}，无批次码回落发票号（老 ifnull 口径）；
 * 住院号兜底由 4101A Mapper 补齐。
 * {@code fixmedins_code}/{@code fixmedins_name}/{@code adm_vs} 经
 * {@link InsuranceContractTerminalExtension} 读 {@code chs-contract.properties}，
 * <strong>不</strong>读 {@code fin_contract} 历史列。</p>
 */
@Component
@RequiredArgsConstructor
public class InsuranceEncounterLoader {

    private final AccountQueryService accountQueryService;
    private final ContractQueryService contractQueryService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final InvoiceQueryService invoiceQueryService;
    private final BossElectronicInvoiceExtension bossElectronicInvoiceExtension;
    private final InsuranceRegisterBenefitQueryExtension insuranceRegisterBenefitQueryExtension;
    private final InsuranceSettlementRecordLookupExtension insuranceSettlementRecordLookupExtension;
    private final InsuranceContractTerminalExtension insuranceContractTerminalExtension;

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
     * 按合同业务号解析上报所需医保上下文（mdtrt_id/setl_id/psn_no/票据/定点机构等）。
     *
     * @param encounterId   就诊主键
     * @param contractBusNo 合同业务号
     * @return 上下文；非医保或无 mdtrt_id/setl_id 时 empty
     */
    public Optional<InsuranceEncounterContext> findContext(long encounterId, String contractBusNo) {
        // contractBusNo 为空无法定位合同
        if (trimToNull(contractBusNo) == null) {
            return Optional.empty();
        }
        Optional<Contract> contractOpt = contractQueryService.findOptionalContractEntityByBusNo(contractBusNo);
        // 非医保合同不组装上下文
        if (contractOpt.isEmpty() || !Boolean.TRUE.equals(contractOpt.get().getYbFlag())) {
            return Optional.empty();
        }
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
        // Pre：定点机构/区划仅来自 chs-contract（经 SPI），禁止回落 fin_contract 空列
        fillTerminalFromChsContract(ctx, contractBusNo);
        if (settled.billDate != null) {
            ctx.setSetlBegnDate(settled.billDate.toLocalDate());
            ctx.setSetlEndDate(settled.billDate.toLocalDate());
        }
        // Pre：psn_no 优先结算回执，其次就诊平台映射
        ctx.setPsnNo(resolvePsnNo(encounterId, settled.paymentId));
        // Pre：票据三要素（bill_code←博思批次码→发票号；bill_no/biz_sn←adm_invoice+博思）
        fillInvoiceFields(ctx, settled.paymentId, settled.chrgBchno);
        return Optional.of(ctx);
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
     * 经 SPI 填充定点机构编码/名称/区划（chs-contract.properties）。
     *
     * @param ctx           上报上下文
     * @param contractBusNo 契约业务号
     */
    private void fillTerminalFromChsContract(InsuranceEncounterContext ctx, String contractBusNo) {
        Optional<InsuranceContractTerminalQueryExtensionOut> terminal =
                insuranceContractTerminalExtension.findByContractBusNo(contractBusNo);
        // 未装配 CHS 或 properties 缺段时保持 null，由上传校验报「定点医药机构编号缺失」
        if (terminal.isEmpty()) {
            return;
        }
        InsuranceContractTerminalQueryExtensionOut row = terminal.get();
        ctx.setFixmedinsCode(trimToNull(row.getFixmedinsCode()));
        ctx.setFixmedinsName(trimToNull(row.getFixmedinsName()));
        ctx.setInsuplcAdmdvs(trimToNull(row.getAdmVs()));
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
     * 填充 4101A 票据要素（bill_code / bill_no / biz_sn）。
     *
     * <p>对齐老系统 {@code ifnull(ifnull(billBatchCode, INVOICE_NO), ZY_NO)}：
     * {@code bill_code} 优先博思 {@code boss_batch_code}；无批次码时回落已解析的
     * {@code bill_no}（发票号）。住院号兜底由 {@code CaseRecord4101AMapper} 用
     * {@code ipt_no}/{@code medcasno} 补齐。</p>
     *
     * @param ctx 上下文
     * @param paymentId 支付单
     * @param chrgBchno 收费批次号（biz_sn 兜底）
     */
    private void fillInvoiceFields(InsuranceEncounterContext ctx, Long paymentId, String chrgBchno) {
        if (paymentId == null) {
            // 无支付单时仅用收费批次兜底业务流水
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
        }
        // Pre：博思电子票回写的票据代码（与门诊收银台同路）
        Optional<BossElectronicInvoiceDocumentLookupExtensionOut> bossDoc =
                bossElectronicInvoiceExtension.findDocumentByPaymentId(paymentId);
        if (bossDoc.isPresent()) {
            BossElectronicInvoiceDocumentLookupExtensionOut doc = bossDoc.get();
            ctx.setBillCode(trimToNull(doc.getBillBatchCode()));
            // adm_invoice 无票号时用博思票号补齐
            if (trimToNull(ctx.getBillNo()) == null) {
                ctx.setBillNo(trimToNull(doc.getBossBillNo()));
            }
            // adm_invoice 无流水时用博思 bus_no 补齐
            if (trimToNull(ctx.getBizSn()) == null) {
                ctx.setBizSn(trimToNull(doc.getBusNo()));
            }
        }
        // 老口径：无 billBatchCode 时用发票号顶 bill_code（后续 Mapper 再兜住院号）
        if (trimToNull(ctx.getBillCode()) == null) {
            ctx.setBillCode(trimToNull(ctx.getBillNo()));
        }
        // biz_sn 仍空时用收费批次号
        if (trimToNull(ctx.getBizSn()) == null) {
            ctx.setBizSn(chrgBchno);
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

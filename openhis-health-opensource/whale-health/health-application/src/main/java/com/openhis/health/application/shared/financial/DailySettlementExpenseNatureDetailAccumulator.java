/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.shared.financial.DailySettlementExpenseNatureSummarySupport.InsutypeBucket;
import com.openhis.health.application.shared.financial.DailySettlementExpenseNatureSummarySupport.NatureBucket;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.share.constants.DailySettlementSharedConstants;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 日结费用性质累加（门急诊/住院共用）：仅修正「自费+医保混结」分桶。
 *
 * <p><b>账户真源</b>：账单挂自费账户 → 自费；挂医保账户（含基金不够的现金补差）→ 医保现金/统筹/个账。
 * <b>非混结</b>（纯自费或纯医保）：仍走 payment.contract_no 整笔归集，不改动原「医保现金」口径。
 * <b>混结</b>：按 {@code PaymentRecDetail.account_id→contract_no} 拆开，避免自费现金误进医保现金。</p>
 */
@Component
@RequiredArgsConstructor
public class DailySettlementExpenseNatureDetailAccumulator {

    /** 账户读侧：detail.account_id → contract_no。 */
    private final AccountQueryService accountQueryService;

    /** 合同是否医保费用性质。 */
    private final ContractExpenseNatureAssembler contractExpenseNatureAssembler;

    /** 合同业务号 → 展示名。 */
    private final ContractQueryService contractQueryService;

    /**
     * 预载账户合同与名称（一次 IO，供整页 buildSummary 复用）。
     *
     * @param paymentContractNos      展示行上的 payment.contract_no
     * @param recDetailsByPaymentId   paymentId → 子明细
     * @return 预载映射；永不为 null
     */
    public PreparedMaps prepare(
        Collection<String> paymentContractNos,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId
    ) {
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        if (recDetailsByPaymentId != null) {
            for (List<PaymentRecDetailQueryModel> details : recDetailsByPaymentId.values()) {
                if (details == null) {
                    continue;
                }
                for (PaymentRecDetailQueryModel detail : details) {
                    if (detail != null && detail.getAccountId() != null) {
                        accountIds.add(detail.getAccountId());
                    }
                }
            }
        }
        Map<Long, Account> accountById = this.accountQueryService.mapAccountsByIds(accountIds);
        Map<Long, String> contractNoByAccountId = new HashMap<>(accountById.size() * 2);
        LinkedHashSet<String> allContractNos = new LinkedHashSet<>();
        if (paymentContractNos != null) {
            for (String busNo : paymentContractNos) {
                String normalized = trimToNull(busNo);
                if (normalized != null) {
                    allContractNos.add(normalized);
                }
            }
        }
        for (Map.Entry<Long, Account> entry : accountById.entrySet()) {
            Account account = entry.getValue();
            if (account == null) {
                continue;
            }
            String contractNo = trimToNull(account.getContractNo());
            if (contractNo == null) {
                continue;
            }
            contractNoByAccountId.put(entry.getKey(), contractNo);
            allContractNos.add(contractNo);
        }
        Map<String, Boolean> insuranceByContractNo =
            this.contractExpenseNatureAssembler.mapInsuranceFlagByContractBusNoIn(allContractNos);
        Map<String, Boolean> workInjuryByContractNo =
            this.contractExpenseNatureAssembler.mapWorkInjuryFlagByContractBusNoIn(allContractNos);
        Map<String, String> contractNameByBusNo =
            this.contractQueryService.mapContractNameByBusNoIn(allContractNos);
        return new PreparedMaps(
            contractNoByAccountId, insuranceByContractNo, workInjuryByContractNo, contractNameByBusNo);
    }

    /**
     * 是否自费+医保混结（明细账户合同既有医保又有非医保）。
     *
     * <p>仅此时才走账户分桶；纯医保/纯自费返回 false，调用方保持原 payment 级归集。</p>
     *
     * @param details                   本 payment 子明细
     * @param maps                      {@link #prepare} 预载
     * @param paymentContractNoFallback payment.contract_no（明细无账户时参与判定）
     * @return true=混结需按账户拆
     */
    public boolean isMixedSelfPayAndInsurancePayment(
        List<PaymentRecDetailQueryModel> details,
        PreparedMaps maps,
        String paymentContractNoFallback
    ) {
        if (details == null || details.isEmpty() || maps == null) {
            return false;
        }
        boolean sawInsurance = false;
        boolean sawSelfPay = false;
        for (PaymentRecDetailQueryModel detail : details) {
            if (detail == null) {
                continue;
            }
            String contractNo = this.resolveDetailContractNo(
                detail.getAccountId(), paymentContractNoFallback, maps.contractNoByAccountId);
            PaymentChannel channel = PaymentChannel.fromCode(detail.getPayEnum());
            boolean insurance = this.resolveInsuranceFlag(contractNo, channel, maps.insuranceByContractNo);
            if (insurance) {
                sawInsurance = true;
            } else {
                // 能解析到合同或账户的非医保明细才计自费侧（避免空明细误判混结）
                if (StringUtils.isNotEmpty(contractNo) || detail.getAccountId() != null) {
                    sawSelfPay = true;
                }
            }
            if (sawInsurance && sawSelfPay) {
                return true;
            }
        }
        return false;
    }

    /**
     * 混结专用：按明细账户合同分桶累加费用性质金额与人次。
     *
     * @param natureByContractNo          费用性质累加桶
     * @param maps                        {@link #prepare} 预载
     * @param details                     本 payment 子明细；空则由调用方自行 payment 级回落
     * @param signedLineAmountByDetailId  明细主键 → 已带符号净额
     * @param paymentContractNoFallback   payment.contract_no；明细无账户时回落
     * @param personKey                   人次去重键；可空
     * @param activeInsuranceSettlement   是否存在有效医保结算（人次/险种）
     * @param insutype                    平台险种码；可空
     * @param insutypeName                险种展示名；可空
     * @param shouldCountInsuranceDetail  统筹/个账是否计入（桥表撤销规则）
     * @return true 表示已按明细累加；false 表示无有效明细，调用方应 payment 级回落
     */
    public boolean accumulateMixedPaymentByDetailAccounts(
        Map<String, NatureBucket> natureByContractNo,
        PreparedMaps maps,
        List<PaymentRecDetailQueryModel> details,
        Map<Long, BigDecimal> signedLineAmountByDetailId,
        String paymentContractNoFallback,
        Long personKey,
        boolean activeInsuranceSettlement,
        String insutype,
        String insutypeName,
        Predicate<PaymentRecDetailQueryModel> shouldCountInsuranceDetail
    ) {
        if (natureByContractNo == null || maps == null || details == null || details.isEmpty()) {
            return false;
        }
        if (signedLineAmountByDetailId == null || signedLineAmountByDetailId.isEmpty()) {
            return false;
        }
        Set<String> personCountedNatures = new HashSet<>();
        boolean anyAccumulated = false;
        for (PaymentRecDetailQueryModel detail : details) {
            if (detail == null || detail.getId() == null) {
                continue;
            }
            BigDecimal lineAmount = signedLineAmountByDetailId.get(detail.getId());
            if (lineAmount == null) {
                continue;
            }
            PaymentChannel channel = PaymentChannel.fromCode(detail.getPayEnum());
            if (channel == null) {
                continue;
            }
            String contractNo = this.resolveDetailContractNo(
                detail.getAccountId(), paymentContractNoFallback, maps.contractNoByAccountId);
            // 工伤合同：不进费用性质块、不进医保统筹公共头
            if (this.isWorkInjuryContractNo(contractNo, maps)) {
                continue;
            }
            boolean insurance = this.resolveInsuranceFlag(contractNo, channel, maps.insuranceByContractNo);
            String contractName = null;
            if (StringUtils.isNotEmpty(contractNo)) {
                contractName = maps.contractNameByBusNo.get(contractNo);
            }
            NatureBucket natureBucket = DailySettlementExpenseNatureSummarySupport.ensureNature(
                natureByContractNo, contractNo, contractName, insurance);
            // 人次：同一 payment 对同一费用性质只计一次
            if (personKey != null && personCountedNatures.add(natureBucket.getContractNo())) {
                if (insurance && activeInsuranceSettlement) {
                    natureBucket.getPersonKeys().add(personKey);
                } else if (!insurance) {
                    natureBucket.getPersonKeys().add(personKey);
                }
            }
            InsutypeBucket insutypeBucket = null;
            if (insurance && activeInsuranceSettlement) {
                insutypeBucket = natureBucket.ensureInsutype(insutype, insutypeName);
                if (personKey != null) {
                    insutypeBucket.getPersonKeys().add(personKey);
                }
            }
            this.applyChannelToBuckets(
                natureBucket,
                insutypeBucket,
                channel,
                lineAmount,
                insurance,
                shouldCountInsuranceDetail != null && shouldCountInsuranceDetail.test(detail));
            anyAccumulated = true;
        }
        return anyAccumulated;
    }

    /**
     * 非混结：按 payment.contract_no 整笔归集（保持原医保现金口径）。
     *
     * @param natureByContractNo            费用性质桶
     * @param maps                          预载
     * @param paymentContractNo             payment.contract_no
     * @param paymentHasInsurance           payment 级医保标记
     * @param netAmount                     payment 净额（自费总额用）
     * @param personKey                     人次键
     * @param activeInsuranceSettlement     有效医保结算
     * @param insutype                      险种码
     * @param insutypeName                  险种名
     * @param details                       子明细（医保现金/统筹/个账仍从明细累加）
     * @param signedLineAmountByDetailId    明细带符号净额
     * @param shouldCountInsuranceDetail    统筹/个账计入门禁
     */
    public void accumulateSingleNaturePayment(
        Map<String, NatureBucket> natureByContractNo,
        PreparedMaps maps,
        String paymentContractNo,
        boolean paymentHasInsurance,
        BigDecimal netAmount,
        Long personKey,
        boolean activeInsuranceSettlement,
        String insutype,
        String insutypeName,
        List<PaymentRecDetailQueryModel> details,
        Map<Long, BigDecimal> signedLineAmountByDetailId,
        Predicate<PaymentRecDetailQueryModel> shouldCountInsuranceDetail
    ) {
        if (natureByContractNo == null || maps == null) {
            return;
        }
        String contractNo = trimToNull(paymentContractNo);
        if (contractNo == null) {
            contractNo = "";
        }
        // 工伤合同：费用性质块与医保统筹公共头均不展示/不计入
        if (this.isWorkInjuryContractNo(contractNo, maps)) {
            return;
        }
        String contractName = maps.contractNameByBusNo.get(contractNo);
        NatureBucket natureBucket = DailySettlementExpenseNatureSummarySupport.ensureNature(
            natureByContractNo, contractNo, contractName, paymentHasInsurance);
        if (personKey != null) {
            if (paymentHasInsurance && activeInsuranceSettlement) {
                natureBucket.getPersonKeys().add(personKey);
            } else if (!paymentHasInsurance) {
                natureBucket.getPersonKeys().add(personKey);
            }
        }
        if (!paymentHasInsurance) {
            // 纯自费：整笔净额进自费总额（与改前一致）
            natureBucket.addNetTotal(netAmount);
            return;
        }
        InsutypeBucket insutypeBucket = null;
        if (activeInsuranceSettlement) {
            insutypeBucket = natureBucket.ensureInsutype(insutype, insutypeName);
            if (personKey != null) {
                insutypeBucket.getPersonKeys().add(personKey);
            }
        }
        // 纯医保：现金/统筹/个账仍整笔进该医保桶（原医保现金口径不动）
        if (details == null || signedLineAmountByDetailId == null) {
            return;
        }
        for (PaymentRecDetailQueryModel detail : details) {
            if (detail == null || detail.getId() == null) {
                continue;
            }
            BigDecimal lineAmount = signedLineAmountByDetailId.get(detail.getId());
            if (lineAmount == null) {
                continue;
            }
            PaymentChannel channel = PaymentChannel.fromCode(detail.getPayEnum());
            if (channel == null) {
                continue;
            }
            if (PaymentChannel.DEPOSIT_OFFSET.equals(channel)) {
                // 冲抵占用不计入日结现金/渠道小计。
                continue;
            }
            if (PaymentChannel.CASH.equals(channel) || PaymentChannel.CASHIER.equals(channel)) {
                natureBucket.addCash(lineAmount);
                if (insutypeBucket != null) {
                    insutypeBucket.addCash(lineAmount);
                }
                continue;
            }
            boolean countInsurance = shouldCountInsuranceDetail != null
                && shouldCountInsuranceDetail.test(detail);
            if (PaymentChannel.INSURANCE_FUND.equals(channel)) {
                if (countInsurance) {
                    natureBucket.addFund(lineAmount);
                    if (insutypeBucket != null) {
                        insutypeBucket.addFund(lineAmount);
                    }
                }
                continue;
            }
            if (PaymentChannel.INSURANCE_ACCT.equals(channel) && countInsurance) {
                natureBucket.addAccount(lineAmount);
                if (insutypeBucket != null) {
                    insutypeBucket.addAccount(lineAmount);
                }
            }
        }
    }

    /**
     * 工伤合同专用：按个人自付 / 统筹 / 个账口径累加（不跳过工伤；写入统一「工伤保险」桶）。
     *
     * <p>仅供日结工伤独立统计块使用；禁止并入主费用性质块 / 医保统筹公共头。
     * 个人自付含现金/微信/支付宝/银行卡等自付渠道（工伤已被主渠道区剔除，须在本块回收）。</p>
     *
     * @param natureByContractNo         累加桶（调用方单独持有，勿与主块共用）
     * @param maps                       {@link #prepare} 预载
     * @param paymentContractNo          payment.contract_no（取展示名）
     * @param personKey                  人次键；空则不计人次
     * @param details                    子明细
     * @param signedLineAmountByDetailId 明细带符号净额
     * @param shouldCountInsuranceDetail 统筹/个账计入门禁
     */
    public void accumulateWorkInjuryNaturePayment(
        Map<String, NatureBucket> natureByContractNo,
        PreparedMaps maps,
        String paymentContractNo,
        Long personKey,
        List<PaymentRecDetailQueryModel> details,
        Map<Long, BigDecimal> signedLineAmountByDetailId,
        Predicate<PaymentRecDetailQueryModel> shouldCountInsuranceDetail
    ) {
        if (natureByContractNo == null || maps == null) {
            return;
        }
        String displayName = DailySettlementSharedConstants.WORK_INJURY_NATURE_DISPLAY_NAME;
        String normalizedContract = trimToNull(paymentContractNo);
        if (normalizedContract != null) {
            String contractName = maps.contractNameByBusNo.get(normalizedContract);
            if (StringUtils.isNotEmpty(contractName)) {
                displayName = contractName.trim();
            }
        }
        // Asm：多工伤合同并入同一展示桶，避免独立块拆多行
        NatureBucket natureBucket = DailySettlementExpenseNatureSummarySupport.ensureNature(
            natureByContractNo,
            DailySettlementSharedConstants.WORK_INJURY_NATURE_BUCKET_KEY,
            displayName,
            true);
        if (personKey != null) {
            // 工伤独立块：有结算或仅账单均计人次（patientId 去重）
            natureBucket.getPersonKeys().add(personKey);
        }
        if (details == null || signedLineAmountByDetailId == null) {
            return;
        }
        for (PaymentRecDetailQueryModel detail : details) {
            if (detail == null || detail.getId() == null) {
                continue;
            }
            BigDecimal lineAmount = signedLineAmountByDetailId.get(detail.getId());
            if (lineAmount == null) {
                continue;
            }
            PaymentChannel channel = PaymentChannel.fromCode(detail.getPayEnum());
            if (channel == null) {
                continue;
            }
            if (PaymentChannel.DEPOSIT_OFFSET.equals(channel)) {
                // 预交金冲抵不计入个人自付
                continue;
            }
            if (PaymentChannel.INSURANCE_FUND.equals(channel)) {
                boolean countInsurance = false;
                if (shouldCountInsuranceDetail != null) {
                    countInsurance = shouldCountInsuranceDetail.test(detail);
                }
                if (countInsurance) {
                    natureBucket.addFund(lineAmount);
                }
                continue;
            }
            if (PaymentChannel.INSURANCE_ACCT.equals(channel)) {
                boolean countInsurance = false;
                if (shouldCountInsuranceDetail != null) {
                    countInsurance = shouldCountInsuranceDetail.test(detail);
                }
                if (countInsurance) {
                    natureBucket.addAccount(lineAmount);
                }
                continue;
            }
            // 现金/微信/支付宝/银行卡/收银台等自付渠道 → 个人自付
            natureBucket.addCash(lineAmount);
        }
    }

    /**
     * 渠道金额写入费用性质 / 险种桶。
     *
     * @param natureBucket              费用性质桶
     * @param insutypeBucket            险种子桶；非医保可空
     * @param channel                   支付渠道
     * @param lineAmount                带符号净额
     * @param insurance                 本明细是否医保费用性质
     * @param countInsuranceFundOrAcct  统筹/个账是否计入
     */
    private void applyChannelToBuckets(
        NatureBucket natureBucket,
        InsutypeBucket insutypeBucket,
        PaymentChannel channel,
        BigDecimal lineAmount,
        boolean insurance,
        boolean countInsuranceFundOrAcct
    ) {
        if (natureBucket == null || channel == null) {
            return;
        }
        if (PaymentChannel.DEPOSIT_OFFSET.equals(channel)) {
            // 预交金冲抵不计入费用性质自付/医保现金小计。
            return;
        }
        if (PaymentChannel.INSURANCE_FUND.equals(channel)) {
            // 统筹：仅医保账户且桥表允许计入
            if (insurance && countInsuranceFundOrAcct) {
                natureBucket.addFund(lineAmount);
                if (insutypeBucket != null) {
                    insutypeBucket.addFund(lineAmount);
                }
            }
            return;
        }
        if (PaymentChannel.INSURANCE_ACCT.equals(channel)) {
            // 个账：仅医保账户且桥表允许计入
            if (insurance && countInsuranceFundOrAcct) {
                natureBucket.addAccount(lineAmount);
                if (insutypeBucket != null) {
                    insutypeBucket.addAccount(lineAmount);
                }
            }
            return;
        }
        if (insurance) {
            // 医保现金：现金补差 + 内部 CASHIER 占位（落库常为 CASHIER，展示回落现金）
            if (PaymentChannel.CASH.equals(channel) || PaymentChannel.CASHIER.equals(channel)) {
                natureBucket.addCash(lineAmount);
                if (insutypeBucket != null) {
                    insutypeBucket.addCash(lineAmount);
                }
            }
            return;
        }
        // 自费：本账户各渠道净额计入自费总额（转自费/自费账单，非医保补差）
        natureBucket.addNetTotal(lineAmount);
    }

    /**
     * 解析明细费用性质合同号：账户优先，回落 payment.contract_no。
     *
     * @param accountId                 明细账户
     * @param paymentContractNoFallback payment 合同号
     * @param contractNoByAccountId     账户 → 合同号
     * @return 合同 bus_no；可空串
     */
    private String resolveDetailContractNo(
        Long accountId,
        String paymentContractNoFallback,
        Map<Long, String> contractNoByAccountId
    ) {
        if (accountId != null && contractNoByAccountId != null) {
            String byAccount = trimToNull(contractNoByAccountId.get(accountId));
            if (byAccount != null) {
                return byAccount;
            }
        }
        String fallback = trimToNull(paymentContractNoFallback);
        if (fallback != null) {
            return fallback;
        }
        return "";
    }

    /**
     * 预载映射中合同是否工伤。
     *
     * @param contractNo 合同 bus_no
     * @param maps       {@link #prepare} 预载
     * @return true=工伤，跳过费用性质累加
     */
    private boolean isWorkInjuryContractNo(String contractNo, PreparedMaps maps) {
        if (maps == null || StringUtils.isEmpty(contractNo)) {
            return false;
        }
        return Boolean.TRUE.equals(maps.workInjuryByContractNo.get(contractNo));
    }

    /**
     * 是否医保费用性质：合同 yb 标志；统筹/个账渠道在无合同标志时仍按医保处理。
     *
     * @param contractNo            合同 bus_no
     * @param channel               支付渠道
     * @param insuranceByContractNo 合同 → 是否医保
     * @return true=医保桶
     */
    private boolean resolveInsuranceFlag(
        String contractNo,
        PaymentChannel channel,
        Map<String, Boolean> insuranceByContractNo
    ) {
        if (PaymentChannel.INSURANCE_FUND.equals(channel) || PaymentChannel.INSURANCE_ACCT.equals(channel)) {
            // 基金/个账渠道本身即医保路径
            if (StringUtils.isEmpty(contractNo)) {
                return true;
            }
        }
        if (StringUtils.isEmpty(contractNo) || insuranceByContractNo == null) {
            return false;
        }
        Boolean flag = insuranceByContractNo.get(contractNo);
        if (flag == null) {
            return false;
        }
        return flag;
    }

    /**
     * 预载映射。
     */
    public static final class PreparedMaps {
        private final Map<Long, String> contractNoByAccountId;
        private final Map<String, Boolean> insuranceByContractNo;
        /** 合同 bus_no → 是否工伤（日结费用性质/医保统筹排除）。 */
        private final Map<String, Boolean> workInjuryByContractNo;
        private final Map<String, String> contractNameByBusNo;

        /**
         * @param contractNoByAccountId  账户 → 合同
         * @param insuranceByContractNo  合同 → 医保标志
         * @param workInjuryByContractNo 合同 → 工伤标志
         * @param contractNameByBusNo    合同 → 名称
         */
        public PreparedMaps(
            Map<Long, String> contractNoByAccountId,
            Map<String, Boolean> insuranceByContractNo,
            Map<String, Boolean> workInjuryByContractNo,
            Map<String, String> contractNameByBusNo
        ) {
            this.contractNoByAccountId =
                contractNoByAccountId == null ? Collections.emptyMap() : contractNoByAccountId;
            this.insuranceByContractNo =
                insuranceByContractNo == null ? Collections.emptyMap() : insuranceByContractNo;
            this.workInjuryByContractNo =
                workInjuryByContractNo == null ? Collections.emptyMap() : workInjuryByContractNo;
            this.contractNameByBusNo =
                contractNameByBusNo == null ? Collections.emptyMap() : contractNameByBusNo;
        }

        /**
         * 合同是否工伤（供 Mapper 跳过上传差额等勾稽项）。
         *
         * @param contractBusNo 合同 bus_no
         * @return true=工伤
         */
        public boolean isWorkInjuryContractNo(String contractBusNo) {
            String normalized = trimToNull(contractBusNo);
            if (normalized == null) {
                return false;
            }
            return Boolean.TRUE.equals(this.workInjuryByContractNo.get(normalized));
        }
    }
}

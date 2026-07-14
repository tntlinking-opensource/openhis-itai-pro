/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.support;

import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentYbSettlementQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * MRMS 4401 医保就诊上下文解析（upload IF {@code support}）。
 *
 * <p>读 HIS 账户、合同与支付流水（B.6.4 QueryService），不经 Insurance Extension；
 * 供 {@link com.openhis.health.application.mrms.upload.mapper.CaseRecord4401Mapper} 填充 mdtrt_id 等字段。</p>
 */
@Component
@RequiredArgsConstructor
public class MrmsInsuranceEncounterLoader {

    private final AccountQueryService accountQueryService;
    private final ContractQueryService contractQueryService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

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
     * 按合同业务号解析 4401 所需医保上下文（mdtrt_id、定点机构等）。
     *
     * @param encounterId 就诊主键
     * @param contractBusNo 合同业务号
     * @return 上下文；非医保或无 mdtrt_id 时 empty
     */
    public Optional<MrmsInsuranceEncounterContext> findContext(long encounterId, String contractBusNo) {
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
        String mdtrtId = resolveMdtrtId(encounterId, contractBusNo);
        // 支付流水无 mdtrt_id 时不返回上下文
        if (trimToNull(mdtrtId) == null) {
            return Optional.empty();
        }
        MrmsInsuranceEncounterContext ctx = new MrmsInsuranceEncounterContext();
        ctx.setMdtrtId(mdtrtId);
        ctx.setFixmedinsCode(trimToNull(contract.getFixmedinsCode()));
        ctx.setFixmedinsName(trimToNull(contract.getFixmedinsName()));
        ctx.setInsuplcAdmdvs(trimToNull(contract.getAdmVs()));
        return Optional.of(ctx);
    }

    /**
     * 从支付成功/退款中流水解析医保 mdtrt_id。
     *
     * @param encounterId 就诊主键
     * @param contractBusNo 合同业务号
     * @return mdtrt_id，未找到时 null
     */
    private String resolveMdtrtId(long encounterId, String contractBusNo) {
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
                // 取首个非空 mdtrt_id
                if (trimToNull(yb.getMdtrtId()) != null) {
                    return yb.getMdtrtId();
                }
            }
        }
        return null;
    }
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.reconciliation.support;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.openhis.chs.domain.catalog.access.ChsContractPropertiesAccess;
import com.openhis.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.openhis.chs.domain.contract.ChsContractSnapshot;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.session.ChsSignCredentialResolver;
import com.openhis.chs.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 对账清算出站公共上下文（契约解析、操作员、9001 签到凭证）。
 * <p>供 {@code Recon*Invoker} 与 {@link com.openhis.chs.bridge.reconciliation.workflow.Recon3202Workflow} 复用。</p>
 */
@Component
@RequiredArgsConstructor
public class ReconPlatformSupport {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ChsInsuranceContractReader contractReader;
    private final ChsContractPropertiesAccess contractPropertiesAccess;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final ChsSignCredentialResolver signCredentialResolver;

    /** 解析契约快照、业务编号与定点机构编码。 */
    public ReconContractContext resolveContract(String contractBusNo, Long contractId) {
        ChsContractSnapshot contract = contractReader.requireContract(contractBusNo, contractId);
        String busNo = contractReader.requireContractBusNo(contract);
        String fixmedinsCode = contractPropertiesAccess.findFixmedinsCode(busNo).orElse("").trim();
        return new ReconContractContext(contract, busNo, fixmedinsCode);
    }

    /** 组装 gateway {@link InvokeContext}。 */
    public InvokeContext buildInvokeContext() {
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录后再操作医保对账");
        InvokeContext partial = InvokeContext.builder().operatorId(String.valueOf(operatorId)).build();
        return InvokeContext.builder()
            .operatorId(signCredentialResolver.resolveOperatorId(partial))
            .operatorName(signCredentialResolver.resolveOperatorName(partial))
            .build();
    }

    /** 解析 {@code yyyy-MM-dd} 日期字符串，空值抛业务异常。 */
    public LocalDate parseDate(String text, String label) {
        if (isEmpty(text)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, label + "不能为空");
        }
        return LocalDate.parse(text.trim(), DATE_FMT);
    }

    public record ReconContractContext(ChsContractSnapshot contract, String busNo, String fixmedinsCode) {
    }
}

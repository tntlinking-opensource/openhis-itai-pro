/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.catalog.support;

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

/**
 * 医保目录出站公共支撑：契约解析、签到上下文组装与终端配置读取。
 * <p>供 {@code Cat1312Invoker}、{@code Cat3301Invoker}、
 * {@link com.openhis.chs.bridge.catalog.workflow.CatalogDownloadWorkflow} 等复用。</p>
 */
@Component
@RequiredArgsConstructor
public class CatalogPlatformSupport {

    private final ChsInsuranceContractReader contractReader;
    private final ChsContractPropertiesAccess contractPropertiesAccess;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final ChsSignCredentialResolver signCredentialResolver;

    /** 解析契约 bus_no。 */
    public String resolveBusNo(String contractBusNo, Long contractId) {
        ChsContractSnapshot contract = contractReader.requireContract(contractBusNo, contractId);
        return contractReader.requireContractBusNo(contract);
    }

    /** 要求当前登录操作员 ID。 */
    public Long requireOperatorId(String message) {
        return currentOperatorResolver.requireOperatorId(message);
    }

    /** 获取当前操作员 ID，未登录时返回 null。 */
    public Long findOperatorIdOrNull() {
        return currentOperatorResolver.findOperatorIdOrNull();
    }

    /** 组装含签到凭证的操作员出站上下文。 */
    public InvokeContext buildInvokeContext() {
        return buildInvokeContext(requireOperatorId("请先登录后再操作医保目录"));
    }

    /** 按指定操作员 ID 组装含签到凭证的出站上下文。 */
    public InvokeContext buildInvokeContext(Long operatorId) {
        String operatorIdText = operatorId == null ? null : String.valueOf(operatorId);
        InvokeContext partial = InvokeContext.builder().operatorId(operatorIdText).build();
        return InvokeContext.builder()
            .operatorId(signCredentialResolver.resolveOperatorId(partial))
            .operatorName(signCredentialResolver.resolveOperatorName(partial))
            .build();
    }

    /** 读取参保地行政区划 adm_vs。 */
    public String requireAdmVs(String contractBusNo) {
        return contractPropertiesAccess.findAdmVs(contractBusNo).orElse("").trim();
    }

    /** 读取定点机构编码 fixmedins_code。 */
    public String requireFixmedinsCode(String contractBusNo) {
        String value = contractPropertiesAccess.findFixmedinsCode(contractBusNo).orElse("");
        if (isEmpty(value)) {
            throw new BusinessException(
                ChsErrorCodes.PLATFORM_FAILED,
                "chs-contract.properties 未配置 contract." + contractBusNo + ".fixmedins_code，无法执行目录出站");
        }
        return value.trim();
    }
}

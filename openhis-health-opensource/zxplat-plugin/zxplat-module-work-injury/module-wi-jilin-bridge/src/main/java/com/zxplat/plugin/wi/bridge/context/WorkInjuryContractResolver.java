/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.bridge.context;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.wi.gateway.scheme.shared.model.WiBusConstants;
import com.zxplat.domain.financial.contract.entity.Contract;
import com.zxplat.domain.financial.contract.service.ContractQueryService;
import com.zxplat.domain.share.enums.ContractCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 工伤费用性质判定（WORKER_INSURANCE / H22010402403-GS）。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.work-injury.enabled", havingValue = "true", matchIfMissing = true)
public class WorkInjuryContractResolver {

    private final ContractQueryService contractQueryService;

    /**
     * 契约是否走工伤结算。
     *
     * @param contractId fin_contract.id
     * @return true 时分组到工伤 Extension
     */
    public boolean isWorkInjuryContract(Long contractId) {
        if (contractId == null) {
            return false;
        }
        return contractQueryService.findOptionalContractEntityById(contractId)
                .map(this::isWorkInjuryContract)
                .orElse(false);
    }

    /**
     * 契约是否走工伤结算。
     *
     * @param contract fin_contract
     * @return true 时走 WORK_INJURY
     */
    public boolean isWorkInjuryContract(Contract contract) {
        if (contract == null) {
            return false;
        }
        if (ContractCategory.WORKER_INSURANCE.getCode().equals(contract.getCategoryEnum())) {
            return true;
        }
        if (isNotEmpty(contract.getBusNo())) {
            String busNo = contract.getBusNo().trim();
            return WiBusConstants.CONTRACT_BUS_NO.equals(busNo) || busNo.endsWith("-GS");
        }
        return false;
    }
}

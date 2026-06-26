/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.support;

import com.openhis.chs.domain.inventory.config.ChsInventoryProperties;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.ContractCategory;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 医保目录等场景：按契约业务编码（bus_no）解析 fin_contract，兼容历史 contractId 入参。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceContractResolver {

    private final ContractQueryService contractQueryService;
    private final ChsInventoryProperties inventoryProperties;

    public Contract requireContract(String contractBusNo, Long contractId) {
        if (StringUtils.isNotBlank(contractBusNo)) {
            String busNo = contractBusNo.trim();
            return contractQueryService.findOptionalContractEntityByBusNo(busNo)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCodeConstants.CONTRACT_NOT_FOUND, "医保契约不存在: " + busNo));
        }
        if (contractId != null) {
            return contractQueryService.findOptionalContractEntityById(contractId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCodeConstants.CONTRACT_NOT_FOUND, "费用性质不存在: " + contractId));
        }
        throw new BusinessException(ErrorCodeConstants.CONTRACT_INVALID_ARGUMENT, "contractBusNo 不能为空");
    }

    /**
     * 解析契约：显式入参优先，否则配置 default-contract-bus-no，再回落租户首条非自费医保契约。
     */
    public Contract resolveOrDefault(String contractBusNo, Long contractId) {
        if (StringUtils.isNotBlank(contractBusNo) || contractId != null) {
            return requireContract(contractBusNo, contractId);
        }
        if (StringUtils.isNotBlank(inventoryProperties.getDefaultContractBusNo())) {
            return requireContract(inventoryProperties.getDefaultContractBusNo().trim(), null);
        }
        Contract fallback = findFirstInsuranceContract();
        if (fallback != null) {
            return fallback;
        }
        throw new BusinessException(
            ErrorCodeConstants.CONTRACT_NOT_FOUND,
            "未配置医保契约，请指定 contractBusNo 或 openhis.chs.inventory.default-contract-bus-no");
    }

    public String requireContractBusNo(Contract contract) {
        if (contract == null || StringUtils.isBlank(contract.getBusNo())) {
            throw new BusinessException(
                    ErrorCodeConstants.CONTRACT_INVALID_ARGUMENT, "医保契约未配置 bus_no");
        }
        return contract.getBusNo().trim();
    }

    private Contract findFirstInsuranceContract() {
        List<ContractListItemQueryModel> contracts = contractQueryService.listAllContracts();
        for (ContractListItemQueryModel item : contracts) {
            if (item == null || StringUtils.isBlank(item.getBusNo())) {
                continue;
            }
            if (ContractCategory.SELF_CHARGE.getCode().equals(item.getCategoryEnum())) {
                continue;
            }
            if ("1".equals(item.getCategoryEnum())) {
                continue;
            }
            return contractQueryService.findOptionalContractEntityByBusNo(item.getBusNo().trim())
                .orElse(null);
        }
        return null;
    }
}

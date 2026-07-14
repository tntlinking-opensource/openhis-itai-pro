/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.catalog;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.openhis.chs.domain.contract.ChsContractSnapshot;
import com.openhis.chs.domain.inventory.config.ChsInventoryProperties;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.ContractCategory;
import com.openhis.health.domain.share.utils.InsuranceContractCategorySupport;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 将 HIS {@code fin_contract} 适配为医保契约关联键（domain {@link ChsInsuranceContractReader} 实现）。
 *
 * <p>仅使用 {@link Contract#getId()}、{@link Contract#getBusNo()} 暴露 {@link ChsContractSnapshot}；
 * {@code fixmedins_code}、{@code adm_vs}、OAuth 等终端参数由 {@code chs-contract.properties} 提供。</p>
 *
 * <p>bridge 内 {@link #requireContractEntity}、{@link #resolveOrDefault} 供 ContextLoader 等读取完整实体。</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsInsuranceContractReaderImpl implements ChsInsuranceContractReader {

    private final ContractQueryService contractQueryService;
    private final ChsInventoryProperties inventoryProperties;

    /** 按 bus_no 或 id 加载契约并返回不可变快照（仅 id + busNo）。 */
    @Override
    public ChsContractSnapshot requireContract(String contractBusNo, Long contractId) {
        Contract contract = requireContractEntity(contractBusNo, contractId);
        String busNo = trimToEmpty(contract.getBusNo());
        if (busNo.isEmpty()) {
            throw new BusinessException(
                ChsErrorCodes.CONTRACT_INVALID, "费用性质未配置 bus_no，无法对接医保");
        }
        return new ChsContractSnapshot(contract.getId(), busNo);
    }

    /** 从快照提取非空 contractBusNo，供 gateway 出站使用。 */
    @Override
    public String requireContractBusNo(ChsContractSnapshot contract) {
        if (contract == null || contract.busNo() == null || contract.busNo().isBlank()) {
            throw new BusinessException(
                ChsErrorCodes.CONTRACT_INVALID, "医保契约未配置 bus_no");
        }
        return contract.busNo().trim();
    }

    /** 判断费用性质是否为医保契约（非自费）。 */
    @Override
    public boolean isInsuranceContract(Long contractId) {
        if (contractId == null) {
            return false;
        }
        Contract contract = requireContractEntity(null, contractId);
        return InsuranceContractCategorySupport.isInsuranceContract(
            contract.getYbFlag(), contract.getCategoryEnum());
    }

    /** bridge 内：按 bus_no 或 id 加载 {@code fin_contract} 实体 */
    public Contract requireContractEntity(String contractBusNo, Long contractId) {
        if (isNotEmpty(contractBusNo)) {
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
        if (isNotEmpty(contractBusNo) || contractId != null) {
            return requireContractEntity(contractBusNo, contractId);
        }
        if (isNotEmpty(inventoryProperties.getDefaultContractBusNo())) {
            return requireContractEntity(inventoryProperties.getDefaultContractBusNo().trim(), null);
        }
        Contract fallback = findFirstInsuranceContract();
        if (fallback != null) {
            return fallback;
        }
        throw new BusinessException(
            ErrorCodeConstants.CONTRACT_NOT_FOUND,
            "未配置医保契约，请指定 contractBusNo 或 openhis.chs.inventory.default-contract-bus-no");
    }

    /** bridge 内：从实体取非空 bus_no */
    public String requireContractBusNo(Contract contract) {
        if (contract == null || isEmpty(contract.getBusNo())) {
            throw new BusinessException(
                ErrorCodeConstants.CONTRACT_INVALID_ARGUMENT, "医保契约未配置 bus_no");
        }
        return contract.getBusNo().trim();
    }

    /**
     * 查找租户首条非自费医保契约实体。
     *
     * @return 首条符合条件的契约实体；未找到时返回 {@code null}
     */
    private Contract findFirstInsuranceContract() {
        List<ContractListItemQueryModel> contracts = contractQueryService.listAllContracts();
        for (ContractListItemQueryModel item : contracts) {
            if (item == null || isEmpty(item.getBusNo())) {
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

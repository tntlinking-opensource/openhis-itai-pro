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
import com.openhis.health.domain.share.enums.ChargeTypeCategory;
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
     * 解析库房/运维进销存契约：显式入参优先，否则配置 default-contract-bus-no，
     * 再回落 {@link #findFirstInsuranceContract}（市优先，仅一则用该一）。
     *
     * @param contractBusNo 显式契约 bus_no；可空
     * @param contractId    显式契约主键；可空
     * @return 非空契约实体
     */
    public Contract resolveOrDefault(String contractBusNo, Long contractId) {
        // Gate：调用方已指定契约 → 不再走市优先默认
        if (isNotEmpty(contractBusNo) || contractId != null) {
            return requireContractEntity(contractBusNo, contractId);
        }
        // Pre：配置项可强制指定默认 bus_no（运维覆盖市优先）
        if (isNotEmpty(inventoryProperties.getDefaultContractBusNo())) {
            return requireContractEntity(inventoryProperties.getDefaultContractBusNo().trim(), null);
        }
        // Cmd：库房默认 — 有市用市，仅省用省，都无则其他医保
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
     * 查找库房进销存默认医保契约：有市医保优先市，仅省则省（区划跟契约 admVs）。
     *
     * <p>仅一条非自费契约时直接用该条；省市都有时取市；都无则回落其他医保类型。</p>
     *
     * @return 优先市医保、否则省医保、再否则首条非自费契约；未找到 null
     */
    private Contract findFirstInsuranceContract() {
        List<ContractListItemQueryModel> contracts = contractQueryService.listAllContracts();
        Contract provincial = null;
        Contract municipal = null;
        Contract otherInsurance = null;
        for (ContractListItemQueryModel item : contracts) {
            if (item == null || isEmpty(item.getBusNo())) {
                continue;
            }
            String category = item.getCategoryEnum();
            // 自费不参与进销存默认契约
            if (ContractCategory.SELF_CHARGE.getCode().equals(category)
                || ChargeTypeCategory.SELF_PAY.getCode().equals(category)) {
                continue;
            }
            Contract entity = contractQueryService.findOptionalContractEntityByBusNo(item.getBusNo().trim())
                .orElse(null);
            if (entity == null) {
                continue;
            }
            // 分类收集：省 / 市 / 其他医保（工伤等）
            if (isProvincialCategory(category)) {
                if (provincial == null) {
                    provincial = entity;
                }
                continue;
            }
            if (isMunicipalCategory(category)) {
                if (municipal == null) {
                    municipal = entity;
                }
                continue;
            }
            if (otherInsurance == null
                && InsuranceContractCategorySupport.isInsuranceContract(entity.getYbFlag(), category)) {
                otherInsurance = entity;
            }
        }
        // 库房默认：省市都有 → 市；仅一则用该一；都无 → 其他医保
        if (municipal != null) {
            return municipal;
        }
        if (provincial != null) {
            return provincial;
        }
        return otherInsurance;
    }

    /**
     * 是否省医保费用性质（含 ChargeTypeCategory 镜像码）。
     *
     * @param categoryEnum 契约/费用性质 category 码
     * @return 省医保 true
     */
    private static boolean isProvincialCategory(String categoryEnum) {
        return ContractCategory.PROVINCIAL_INSURANCE.getCode().equals(categoryEnum)
            || ChargeTypeCategory.PROVINCIAL_INSURANCE.getCode().equals(categoryEnum);
    }

    /**
     * 是否市医保费用性质（含 ChargeTypeCategory 镜像码）。
     *
     * @param categoryEnum 契约/费用性质 category 码
     * @return 市医保 true
     */
    private static boolean isMunicipalCategory(String categoryEnum) {
        return ContractCategory.MUNICIPAL_INSURANCE.getCode().equals(categoryEnum)
            || ChargeTypeCategory.MUNICIPAL_INSURANCE.getCode().equals(categoryEnum);
    }
}

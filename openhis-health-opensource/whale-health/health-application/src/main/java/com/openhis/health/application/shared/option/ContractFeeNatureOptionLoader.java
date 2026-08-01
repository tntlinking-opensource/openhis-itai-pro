/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.option;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.share.enums.ChargeTypeCategory;
import com.openhis.whale.core.application.dto.OptionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 费用性质（合同）下拉：按就诊优先费用性质分类收窄可选范围，供医嘱 init 复用。
 *
 * <p>规则：始终可选「自费」分类；若就诊优先合同（有医保账户则用医保户，否则登记个人现金户）非自费，
 * 另可选与该合同同 {@code category_enum} 的合同。下拉默认项为优先合同本身（排在首位），
 * 避免按 displayOrder 升序把自费顶到首项导致新开医嘱误默认自费。</p>
 *
 * <p>{@code value} 为合同主键字符串（落库 {@code contract_id}），{@code label} 为合同名称。</p>
 */
@Component
@RequiredArgsConstructor
public class ContractFeeNatureOptionLoader {

    /** 就诊账户读侧：解析优先合同号（医保户优先）。 */
    private final AccountQueryService accountQueryService;
    /** 合同读侧：租户费用性质主数据。 */
    private final ContractQueryService contractQueryService;

    /**
     * 按就诊优先费用性质加载医嘱可选下拉（自费 + 优先合同同分类；优先合同排首位）。
     *
     * @param patientId   患者标识（与挂号账户门控一致）
     * @param encounterId 就诊标识
     * @return 非 {@code null}；无有效合同时为空列表
     */
    public List<OptionDto> loadOptionsForEncounter(Long patientId, Long encounterId) {
        PreferredContractContext preferred = resolvePreferredContractContext(patientId, encounterId);
        String preferredCategoryCode = preferred.categoryCode;
        String selfPayCategoryCode = ChargeTypeCategory.SELF_PAY.getCode();
        List<ContractListItemQueryModel> selectableRows = new ArrayList<>();
        // 遍历租户费用性质主数据，仅保留启用态且分类命中「自费或优先合同分类」的行。
        for (ContractListItemQueryModel row : contractQueryService.listAllContracts()) {
            if (row == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(row.getActiveFlag())) {
                continue;
            }
            String categoryCode = trimToNull(row.getCategoryEnum());
            if (categoryCode == null) {
                continue;
            }
            if (selfPayCategoryCode.equals(categoryCode)) {
                selectableRows.add(row);
                continue;
            }
            if (preferredCategoryCode != null && preferredCategoryCode.equals(categoryCode)) {
                selectableRows.add(row);
            }
        }
        List<OptionDto> options = mapContractRowsToOptions(selectableRows);
        return prioritizePreferredContract(options, preferred.contractId);
    }

    /**
     * 解析就诊优先费用性质：有医保账户且合同号有效则用医保户，否则用登记个人现金户。
     *
     * @param patientId   患者标识
     * @param encounterId 就诊标识
     * @return 优先合同上下文；缺失时回落自费分类
     */
    private PreferredContractContext resolvePreferredContractContext(Long patientId, Long encounterId) {
        String selfPayCategoryCode = ChargeTypeCategory.SELF_PAY.getCode();
        if (patientId == null || encounterId == null) {
            return PreferredContractContext.selfPayFallback(selfPayCategoryCode);
        }
        // 有医保账户优先：与患者卡片 contractDisplay / 医嘱默认费用性质同口径
        Account insuranceAccount = accountQueryService.getYbAccount(encounterId);
        PreferredContractContext fromInsurance =
            resolveContextFromAccount(insuranceAccount, selfPayCategoryCode);
        if (fromInsurance != null) {
            return fromInsurance;
        }
        Account registrationAccount = accountQueryService.getRegistrationAccount(patientId, encounterId);
        PreferredContractContext fromRegistration =
            resolveContextFromAccount(registrationAccount, selfPayCategoryCode);
        if (fromRegistration != null) {
            return fromRegistration;
        }
        return PreferredContractContext.selfPayFallback(selfPayCategoryCode);
    }

    /**
     * 由账户 {@code contract_no} 解析合同主键与分类；合同号无效时返回 {@code null}。
     *
     * @param account              账户行
     * @param selfPayCategoryCode  自费分类码（回落用）
     * @return 上下文；无法解析时 {@code null}
     */
    private PreferredContractContext resolveContextFromAccount(Account account, String selfPayCategoryCode) {
        if (account == null) {
            return null;
        }
        String contractBusNo = trimToNull(account.getContractNo());
        if (contractBusNo == null) {
            return null;
        }
        Optional<Contract> contractOpt = contractQueryService.findOptionalContractEntityByBusNo(contractBusNo);
        if (contractOpt.isEmpty()) {
            return null;
        }
        Contract contract = contractOpt.get();
        String categoryCode = trimToNull(contract.getCategoryEnum());
        if (categoryCode == null) {
            categoryCode = selfPayCategoryCode;
        }
        return new PreferredContractContext(contract.getId(), categoryCode);
    }

    /**
     * 将优先合同排到下拉首位，其余按展示序升序，保证前端取首项即为业务默认。
     *
     * @param options            已映射选项
     * @param preferredContractId 优先合同主键；可空
     * @return 排序后的新列表
     */
    private static List<OptionDto> prioritizePreferredContract(
        List<OptionDto> options, Long preferredContractId) {
        if (options == null || options.isEmpty()) {
            return Collections.emptyList();
        }
        if (preferredContractId == null) {
            return OptionDtoAssembler.sortBySortAsc(options);
        }
        String preferredValue = String.valueOf(preferredContractId);
        List<OptionDto> preferred = new ArrayList<>(1);
        List<OptionDto> others = new ArrayList<>(options.size());
        for (OptionDto option : options) {
            if (option == null) {
                continue;
            }
            if (preferredValue.equals(trimToNull(option.getValue()))) {
                preferred.add(option);
            } else {
                others.add(option);
            }
        }
        List<OptionDto> sortedOthers = OptionDtoAssembler.sortBySortAsc(others);
        List<OptionDto> result = new ArrayList<>(options.size());
        result.addAll(preferred);
        result.addAll(sortedOthers);
        // 若优先合同未出现在可选集（主数据停用等），仍返回其余项按序结果
        if (result.isEmpty()) {
            return OptionDtoAssembler.sortBySortAsc(options);
        }
        return result;
    }

    /**
     * 将合同简项映射为 {@link OptionDto}（B.10.5 统一下拉契约）。
     *
     * @param rows 合同查询行，允许为 {@code null} 或空
     * @return 非 {@code null}
     */
    private static List<OptionDto> mapContractRowsToOptions(List<ContractListItemQueryModel> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<OptionDto> options = new ArrayList<>();
        for (ContractListItemQueryModel row : rows) {
            if (row == null) {
                continue;
            }
            Long id = row.getId();
            if (id == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            Integer displayOrder = row.getDisplayOrder();
            if (displayOrder != null) {
                option.setSort(displayOrder.longValue());
            } else {
                // 主数据未填展示序时，用主键保证前端稳定排序。
                option.setSort(id);
            }
            option.setValue(String.valueOf(id));
            option.setLabel(row.getName());
            option.setBusNo(row.getBusNo());
            options.add(option);
        }
        return options;
    }

    /**
     * 就诊优先合同上下文（合同主键 + 分类码）。
     */
    private static final class PreferredContractContext {
        private final Long contractId;
        private final String categoryCode;

        private PreferredContractContext(Long contractId, String categoryCode) {
            this.contractId = contractId;
            this.categoryCode = categoryCode;
        }

        private static PreferredContractContext selfPayFallback(String selfPayCategoryCode) {
            return new PreferredContractContext(null, selfPayCategoryCode);
        }
    }
}

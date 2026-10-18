/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.financial;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.domain.financial.contract.entity.Contract;
import com.zxplat.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.zxplat.domain.financial.contract.service.ContractQueryService;
import com.zxplat.domain.share.constants.OutpatientPharmacyPrescriptionQueryConstants;
import com.zxplat.domain.share.enums.ChargeTypeCategory;
import com.zxplat.domain.share.enums.ContractCategory;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 费用性质（合同）展示判定与分类文案：按 {@code fin_contract} 解析。
 *
 * <p>日结交账 UI「自费/医保」筛选仅影响展示（BR-OP-DCL-009 / BR-IP-DCL-005），判定以合同主数据为准，
 * 不再依赖 {@code fin_payment_yb_settlement} 是否存在。</p>
 *
 * <p>处方笺 / 发药 slip / 医嘱列表「费用性质」分类文案与 AS-OP-PHA-005 口径一致
 * （{@link ContractCategory} → 自费/公费/省医保/市医保/工伤保险）。</p>
 */
@Component
@RequiredArgsConstructor
public class ContractExpenseNatureAssembler {

    /** 合同读侧：按 bus_no / 全量简表解析分类与医保标志。 */
    private final ContractQueryService contractQueryService;

    /**
     * 按合同主键批量解析费用性质分类展示文案。
     *
     * @param contractIds {@code med_medication_request.contract_id} 等；允许含 null
     * @return 合同主键 → 分类文案；未命中或未知分类时不在 Map 中
     */
    public Map<Long, String> mapCategoryTextByContractIdIn(Collection<Long> contractIds) {
        if (contractIds == null || contractIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> unique = new HashSet<>();
        // Loop：去重有效合同主键，避免全量简表扫描后的空匹配
        for (Long contractId : contractIds) {
            if (contractId != null) {
                unique.add(contractId);
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> result = new HashMap<>(unique.size() * 2);
        // Loop：全量有效合同主数据中裁剪本批 id，解析 category_enum → 展示文案
        for (ContractListItemQueryModel contract : this.contractQueryService.listAllContracts()) {
            if (contract == null || contract.getId() == null || !unique.contains(contract.getId())) {
                continue;
            }
            String categoryText = this.resolveCategoryText(contract.getCategoryEnum());
            if (StringUtils.isNotEmpty(categoryText)) {
                result.put(contract.getId(), categoryText);
            }
        }
        return result;
    }

    /**
     * 合同 {@code category_enum} → 费用性质分类中文文案（与门诊处方查询同表）。
     *
     * <p>主数据常见两套码并存：{@link ChargeTypeCategory}（{@code 1}～{@code 4}）与
     * {@link ContractCategory}（{@code SELF_CHARGE} 等）；须同时识别，否则医嘱列表
     * {@code feeNatureText} 恒空。</p>
     *
     * @param categoryCode 合同 {@code category_enum}；空或未知返回 {@code null}
     * @return 分类文案（自费/公费/省医保/市医保/工伤保险）
     */
    public String resolveCategoryText(String categoryCode) {
        if (StringUtils.isEmpty(categoryCode)) {
            return null;
        }
        // ChargeTypeCategory：财务合约维护页/库内常见数字码
        if (ChargeTypeCategory.SELF_PAY.getCode().equals(categoryCode)) {
            return OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_SELF_CHARGE_LABEL;
        }
        if (ChargeTypeCategory.PUBLIC_PAY.getCode().equals(categoryCode)) {
            return OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_PUBLIC_CHARGE_LABEL;
        }
        if (ChargeTypeCategory.PROVINCIAL_INSURANCE.getCode().equals(categoryCode)) {
            return OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_PROVINCIAL_INSURANCE_LABEL;
        }
        if (ChargeTypeCategory.MUNICIPAL_INSURANCE.getCode().equals(categoryCode)) {
            return OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_MUNICIPAL_INSURANCE_LABEL;
        }
        // ContractCategory：历史/部分域仍用枚举名 code
        if (ContractCategory.SELF_CHARGE.getCode().equals(categoryCode)) {
            return OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_SELF_CHARGE_LABEL;
        }
        if (ContractCategory.PUBLIC_CHARGE.getCode().equals(categoryCode)) {
            return OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_PUBLIC_CHARGE_LABEL;
        }
        if (ContractCategory.PROVINCIAL_INSURANCE.getCode().equals(categoryCode)) {
            return OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_PROVINCIAL_INSURANCE_LABEL;
        }
        if (ContractCategory.MUNICIPAL_INSURANCE.getCode().equals(categoryCode)) {
            return OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_MUNICIPAL_INSURANCE_LABEL;
        }
        if (ContractCategory.WORKER_INSURANCE.getCode().equals(categoryCode)) {
            return OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_WORKER_INSURANCE_LABEL;
        }
        return null;
    }

    /**
     * 批量解析合同业务号是否医保费用性质。
     *
     * @param contractBusNos payment {@code contract_no} 集合，允许含空白
     * @return 业务号 → 是否医保；未命中主数据时不在 Map 中（调用方按自费处理）
     */
    public Map<String, Boolean> mapInsuranceFlagByContractBusNoIn(Collection<String> contractBusNos) {
        if (contractBusNos == null || contractBusNos.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String busNo : contractBusNos) {
            String normalized = trimToNull(busNo);
            if (normalized != null) {
                unique.add(normalized);
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Boolean> result = new HashMap<>(unique.size() * 2);
        for (Contract contract : this.contractQueryService.findAllContractEntitiesByBusNoIn(unique)) {
            if (contract == null || StringUtils.isEmpty(contract.getBusNo())) {
                continue;
            }
            result.put(contract.getBusNo().trim(), this.isInsuranceContract(contract));
        }
        return result;
    }

    /**
     * 判定单条合同是否医保费用性质。
     *
     * @param contractBusNo payment {@code contract_no}；空白时按自费
     * @return 是否医保
     */
    public boolean isInsuranceContractBusNo(String contractBusNo) {
        String normalized = trimToNull(contractBusNo);
        if (normalized == null) {
            // 无合同号时回落自费口径，避免误划入医保汇总
            return false;
        }
        Map<String, Boolean> flags = this.mapInsuranceFlagByContractBusNoIn(Collections.singletonList(normalized));
        Boolean flag = flags.get(normalized);
        if (flag == null) {
            return false;
        }
        return flag;
    }

    /**
     * 批量解析合同业务号是否工伤费用性质。
     *
     * @param contractBusNos payment {@code contract_no} 集合，允许含空白
     * @return 业务号 → 是否工伤；未命中主数据时不在 Map 中
     */
    public Map<String, Boolean> mapWorkInjuryFlagByContractBusNoIn(Collection<String> contractBusNos) {
        if (contractBusNos == null || contractBusNos.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String busNo : contractBusNos) {
            String normalized = trimToNull(busNo);
            if (normalized != null) {
                unique.add(normalized);
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Boolean> result = new HashMap<>(unique.size() * 2);
        for (Contract contract : this.contractQueryService.findAllContractEntitiesByBusNoIn(unique)) {
            if (contract == null || StringUtils.isEmpty(contract.getBusNo())) {
                continue;
            }
            result.put(contract.getBusNo().trim(), this.isWorkInjuryContract(contract));
        }
        return result;
    }

    /**
     * 判定单条合同是否工伤费用性质。
     *
     * @param contractBusNo payment {@code contract_no}；空白时非工伤
     * @return 是否工伤
     */
    public boolean isWorkInjuryContractBusNo(String contractBusNo) {
        String normalized = trimToNull(contractBusNo);
        if (normalized == null) {
            return false;
        }
        Map<String, Boolean> flags = this.mapWorkInjuryFlagByContractBusNoIn(Collections.singletonList(normalized));
        Boolean flag = flags.get(normalized);
        return Boolean.TRUE.equals(flag);
    }

    /**
     * 合同 {@code category_enum} 是否工伤（含历史数字码 {@code 5}）。
     *
     * @param categoryCode 合同分类码；空时 false
     * @return 是否工伤
     */
    public boolean isWorkInjuryCategoryEnum(String categoryCode) {
        String normalized = trimToNull(categoryCode);
        if (normalized == null) {
            return false;
        }
        if (ContractCategory.WORKER_INSURANCE.getCode().equals(normalized)) {
            return true;
        }
        // 与博思票面 / 开户路径历史码对齐
        return "5".equals(normalized);
    }

    /**
     * 判定合同实体是否工伤费用性质（按 category，不依赖 yb_flag）。
     *
     * @param contract 合同实体
     * @return 是否工伤
     */
    public boolean isWorkInjuryContract(Contract contract) {
        if (contract == null) {
            return false;
        }
        return this.isWorkInjuryCategoryEnum(contract.getCategoryEnum());
    }

    /**
     * 按 {@code fin_contract.yb_flag} 与 {@code category_enum} 判定是否医保。
     *
     * @param contract 合同实体
     * @return 是否医保
     */
    public boolean isInsuranceContract(Contract contract) {
        if (contract == null) {
            return false;
        }
        if (Boolean.TRUE.equals(contract.getYbFlag())) {
            return true;
        }
        String categoryCode = trimToNull(contract.getCategoryEnum());
        if (categoryCode == null) {
            return false;
        }
        if (ChargeTypeCategory.SELF_PAY.getCode().equals(categoryCode)) {
            return false;
        }
        if (ChargeTypeCategory.PUBLIC_PAY.getCode().equals(categoryCode)) {
            return false;
        }
        if (ContractCategory.SELF_CHARGE.getCode().equals(categoryCode)) {
            return false;
        }
        if (ContractCategory.PUBLIC_CHARGE.getCode().equals(categoryCode)) {
            return false;
        }
        if (ChargeTypeCategory.PROVINCIAL_INSURANCE.getCode().equals(categoryCode)) {
            return true;
        }
        if (ChargeTypeCategory.MUNICIPAL_INSURANCE.getCode().equals(categoryCode)) {
            return true;
        }
        if (ContractCategory.PROVINCIAL_INSURANCE.getCode().equals(categoryCode)) {
            return true;
        }
        if (ContractCategory.MUNICIPAL_INSURANCE.getCode().equals(categoryCode)) {
            return true;
        }
        if (ContractCategory.WORKER_INSURANCE.getCode().equals(categoryCode)) {
            return true;
        }
        // 未识别分类码时保守按非医保处理，避免自费流水被误隐藏
        return false;
    }
}

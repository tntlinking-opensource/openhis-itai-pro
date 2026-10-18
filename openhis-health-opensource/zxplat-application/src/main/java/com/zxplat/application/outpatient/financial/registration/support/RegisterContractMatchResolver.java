/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.support;

import com.zxplat.application.outpatient.financial.registration.dto.RegisterContractMatchDto;
import com.zxplat.application.outpatient.financial.registration.dto.YbRegisterBenefitAreaHintDto;
import com.zxplat.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.zxplat.domain.share.enums.ChargeTypeCategory;
import com.zxplat.domain.share.utils.InsuranceContractCategorySupport;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.InsuranceRegisterBenefitQueryExtension;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 参保地（1101）与费用性质医保通道（adm_vs）匹配与推荐。
 *
 * <p>规则：2201→市医保(4)/220199；其余有效参保地（省内异地/省本级/省外）→省医保(3)/229900。</p>
 */
@Component
@RequiredArgsConstructor
public class RegisterContractMatchResolver {

    private final InsuranceRegisterBenefitQueryExtension registerBenefitQueryExtension;

    public RegisterContractMatchDto evaluate(
        String insuplcAdmdvs,
        Long selectedContractId,
        String selectedTreatmentAreaCode,
        List<ContractListItemQueryModel> availableContracts) {
        RegisterContractMatchDto dto = new RegisterContractMatchDto();
        dto.setPatientInsuplcAdmdvs(StringUtils.trimToNull(insuplcAdmdvs));
        if (selectedContractId != null) {
            dto.setSelectedContractId(String.valueOf(selectedContractId));
        } else {
            dto.setSelectedContractId(null);
        }
        dto.setSelectedTreatmentAreaCode(StringUtils.trimToNull(selectedTreatmentAreaCode));
        dto.setBlockRegister(Boolean.FALSE);

        YbRegisterBenefitAreaHintDto areaHint = RegisterBenefitAreaHintHelper.build(
            dto.getPatientInsuplcAdmdvs(), dto.getSelectedTreatmentAreaCode());
        dto.setMatchStatus(mapAreaKindToMatchStatus(areaHint.getAreaKind()));
        dto.setHintMessage(areaHint.getHintMessage());

        Long recommendedId = recommendContractId(dto.getPatientInsuplcAdmdvs(), availableContracts);
        if (recommendedId != null) {
            dto.setRecommendedContractId(String.valueOf(recommendedId));
            for (ContractListItemQueryModel row : availableContracts) {
                if (row != null && recommendedId.equals(row.getId())) {
                    dto.setRecommendedContractName(row.getName());
                    dto.setRecommendedCategoryCode(row.getCategoryEnum());
                    break;
                }
            }
        }
        // 推荐费用性质与当前不同：提示聚焦「通道选错」，避免被理解成「把参保人识别成省医保」
        if (recommendedId != null
            && selectedContractId != null
            && !recommendedId.equals(selectedContractId)
            && StringUtils.isNotEmpty(dto.getRecommendedContractName())) {
            dto.setHintMessage(buildContractMismatchHint(
                dto.getPatientInsuplcAdmdvs(),
                dto.getSelectedTreatmentAreaCode(),
                dto.getRecommendedContractName()));
        }
        return dto;
    }

    /**
     * 费用性质通道与参保地不匹配时的窗口文案。
     *
     * @param insuplcAdmdvs           患者参保地
     * @param selectedTreatmentArea   当前费用性质就医地/统筹区
     * @param recommendedContractName 推荐费用性质名称
     * @return 提示文案
     */
    private static String buildContractMismatchHint(
        String insuplcAdmdvs,
        String selectedTreatmentArea,
        String recommendedContractName) {
        return "费用性质与参保地不匹配：参保地 "
            + nullToDash(insuplcAdmdvs)
            + "，当前通道就医地 "
            + nullToDash(selectedTreatmentArea)
            + "；建议改用「"
            + recommendedContractName
            + "」";
    }

    private static String nullToDash(String value) {
        // 空参保地/通道文案用破折号占位，避免拼接出「null」
        if (StringUtils.isEmpty(value)) {
            return "—";
        }
        return value.trim();
    }

    public Long recommendContractId(String insuplcAdmdvs, List<ContractListItemQueryModel> availableContracts) {
        if (availableContracts == null || availableContracts.isEmpty()) {
            return null;
        }
        String insu = normalizeAdmdvs(insuplcAdmdvs);
        if (insu.length() < 4) {
            return null;
        }
        String preferredCategory = inferPreferredCategory(insu);
        String preferredAdmVs = inferPreferredAdmVs(insu);
        if (preferredCategory != null) {
            return findContract(availableContracts, preferredCategory, preferredAdmVs);
        }
        return findByLongestTreatmentAreaPrefix(insu, availableContracts);
    }

    /**
     * 解析费用性质就医地/通道区划：优先 chs 扩展，否则回退 {@code contractAdmVsFallback}（列表项 adm_vs）。
     *
     * @param contractId              费用性质主键
     * @param contractAdmVsFallback   合同实体/列表项上的 adm_vs；扩展未命中时使用，避免再查库
     * @return 通道区划；皆空则 null
     */
    public String resolveTreatmentAreaCode(Long contractId, String contractAdmVsFallback) {
        // Pre：扩展启用时先问 chs 通道；未装配或空串则用列表已带的 adm_vs
        if (contractId != null
            && registerBenefitQueryExtension.isEnabled()) {
            String fromChs = registerBenefitQueryExtension.resolveTreatmentAreaCode(contractId);
            if (StringUtils.isNotEmpty(fromChs)) {
                return fromChs.trim();
            }
        }
        return StringUtils.trimToNull(contractAdmVsFallback);
    }

    /**
     * 按类别与首选 adm_vs 匹配启用中医保合同（displayOrder 升序取首条）。
     *
     * @param rows             可用合同列表
     * @param categoryCode     费用类别 code
     * @param preferredAdmVs   首选统筹区划；可空则仅按类别
     * @return 合同主键；无匹配则 null
     */
    private Long findContract(
        List<ContractListItemQueryModel> rows,
        String categoryCode,
        String preferredAdmVs) {
        return rows.stream()
            .filter(row -> row != null && Boolean.TRUE.equals(row.getActiveFlag()))
            .filter(row -> categoryCode.equals(StringUtils.trimToNull(row.getCategoryEnum())))
            .filter(row -> InsuranceContractCategorySupport.isInsuranceCategoryEnum(row.getCategoryEnum()))
            .filter(row -> preferredAdmVs == null
                || preferredAdmVs.equals(normalizeAdmdvs(resolveTreatmentAreaCode(row.getId(), row.getAdmVs()))))
            .sorted(Comparator.comparing(row -> {
                if (row.getDisplayOrder() != null) {
                    return row.getDisplayOrder();
                }
                return Integer.MAX_VALUE;
            }))
            .map(ContractListItemQueryModel::getId)
            .findFirst()
            .orElse(null);
    }

    /**
     * 按参保地与合同 treatmentArea 最长公共前缀推荐合同（前缀长度 ≥4）。
     *
     * @param insu 归一化参保地编码
     * @param rows 可用合同列表
     * @return 合同主键；无足够前缀匹配则 null
     */
    private Long findByLongestTreatmentAreaPrefix(
        String insu,
        List<ContractListItemQueryModel> rows) {
        Long bestId = null;
        int bestLen = -1;
        for (ContractListItemQueryModel row : rows) {
            if (row == null || !Boolean.TRUE.equals(row.getActiveFlag())) {
                continue;
            }
            if (!InsuranceContractCategorySupport.isInsuranceCategoryEnum(row.getCategoryEnum())) {
                continue;
            }
            String treatment = normalizeAdmdvs(resolveTreatmentAreaCode(row.getId(), row.getAdmVs()));
            if (treatment.isEmpty()) {
                continue;
            }
            int len = commonPrefixLength(insu, treatment);
            if (len > bestLen) {
                bestLen = len;
                bestId = row.getId();
            }
        }
        if (bestLen >= 4) {
            return bestId;
        }
        return null;
    }

    /**
     * 按参保地前缀推断首选费用类别。
     *
     * <p>2201→市医保；其余有效参保地（含省内异地、省外）→省医保通道。</p>
     *
     * @param insu 归一化参保地编码（调用方已保证长度 ≥4）
     * @return 类别 code
     */
    private static String inferPreferredCategory(String insu) {
        if (insu.startsWith("2201")) {
            return ChargeTypeCategory.MUNICIPAL_INSURANCE.getCode();
        }
        return ChargeTypeCategory.PROVINCIAL_INSURANCE.getCode();
    }

    /**
     * 按参保地前缀推断首选 adm_vs（220199 / 229900）。
     *
     * @param insu 归一化参保地编码（调用方已保证长度 ≥4）
     * @return adm_vs
     */
    private static String inferPreferredAdmVs(String insu) {
        if (insu.startsWith("2201")) {
            return "220199";
        }
        return "229900";
    }

    /**
     * 参保地提示 areaKind → 前端 matchStatus 文案码。
     *
     * @param areaKind RegisterBenefitAreaHintHelper 区域种类
     * @return MATCH / IN_PROVINCE / CROSS_PROVINCE / UNKNOWN
     */
    private static String mapAreaKindToMatchStatus(String areaKind) {
        if (RegisterBenefitAreaHintHelper.LOCAL.equals(areaKind)) {
            return "MATCH";
        }
        if (RegisterBenefitAreaHintHelper.IN_PROVINCE.equals(areaKind)) {
            return "IN_PROVINCE";
        }
        if (RegisterBenefitAreaHintHelper.CROSS_PROVINCE.equals(areaKind)) {
            return "CROSS_PROVINCE";
        }
        return "UNKNOWN";
    }

    /**
     * 统筹区划归一化：移除非数字字符。
     *
     * @param admdvs 原始区划字符串
     * @return 仅数字；空入参返回空串
     */
    private static String normalizeAdmdvs(String admdvs) {
        if (StringUtils.isEmpty(admdvs)) {
            return "";
        }
        return admdvs.replaceAll("\\D", "");
    }

    /**
     * 计算两字符串最长公共前缀长度。
     *
     * @param left  左串
     * @param right 右串
     * @return 公共前缀字符数
     */
    private static int commonPrefixLength(String left, String right) {
        int max = Math.min(left.length(), right.length());
        int len = 0;
        while (len < max && left.charAt(len) == right.charAt(len)) {
            len++;
        }
        return len;
    }
}

/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.registration.support;

import com.openhis.health.application.outpatient.financial.registration.dto.RegisterContractMatchDto;
import com.openhis.health.application.outpatient.financial.registration.dto.YbRegisterBenefitAreaHintDto;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.share.enums.ChargeTypeCategory;
import com.openhis.health.domain.share.utils.InsuranceContractCategorySupport;
import com.openhis.health.integration.insurance.benefit.InsuranceRegisterBenefitQueryExtension;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 参保地（1101）与费用性质医保通道（adm_vs）匹配与推荐。
 *
 * <p>吉林规则：2299→省医保(3)/229900；2201→市医保(4)/220199。</p>
 */
@Component
@RequiredArgsConstructor
public class RegisterContractMatchSupport {

    private final InsuranceRegisterBenefitQueryExtension registerBenefitQueryExtension;

    public RegisterContractMatchDto evaluate(
        String insuplcAdmdvs,
        Long selectedContractId,
        String selectedTreatmentAreaCode,
        List<ContractListItemQueryModel> availableContracts) {
        RegisterContractMatchDto dto = new RegisterContractMatchDto();
        dto.setPatientInsuplcAdmdvs(StringUtils.trimToNull(insuplcAdmdvs));
        dto.setSelectedContractId(selectedContractId != null ? String.valueOf(selectedContractId) : null);
        dto.setSelectedTreatmentAreaCode(StringUtils.trimToNull(selectedTreatmentAreaCode));
        dto.setBlockRegister(Boolean.FALSE);

        YbRegisterBenefitAreaHintDto areaHint = RegisterBenefitAreaHintSupport.build(
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
        return dto;
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

    public String resolveTreatmentAreaCode(Long contractId, String contractAdmVsFallback) {
        if (contractId != null
            && registerBenefitQueryExtension.isEnabled()) {
            String fromChs = registerBenefitQueryExtension.resolveTreatmentAreaCode(contractId);
            if (StringUtils.isNotBlank(fromChs)) {
                return fromChs.trim();
            }
        }
        return StringUtils.trimToNull(contractAdmVsFallback);
    }

    private Long findContract(
        List<ContractListItemQueryModel> rows,
        String categoryCode,
        String preferredAdmVs) {
        return rows.stream()
            .filter(row -> row != null && Boolean.TRUE.equals(row.getActiveFlag()))
            .filter(row -> categoryCode.equals(StringUtils.trimToNull(row.getCategoryEnum())))
            .filter(row -> InsuranceContractCategorySupport.isInsuranceCategoryEnum(row.getCategoryEnum()))
            .filter(row -> preferredAdmVs == null
                || preferredAdmVs.equals(normalizeAdmdvs(resolveTreatmentAreaCode(row.getId(), null))))
            .sorted(Comparator.comparing(
                row -> row.getDisplayOrder() != null ? row.getDisplayOrder() : Integer.MAX_VALUE))
            .map(ContractListItemQueryModel::getId)
            .findFirst()
            .orElse(null);
    }

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
            String treatment = normalizeAdmdvs(resolveTreatmentAreaCode(row.getId(), null));
            if (treatment.isEmpty()) {
                continue;
            }
            int len = commonPrefixLength(insu, treatment);
            if (len > bestLen) {
                bestLen = len;
                bestId = row.getId();
            }
        }
        return bestLen >= 4 ? bestId : null;
    }

    private static String inferPreferredCategory(String insu) {
        if (insu.startsWith("2299")) {
            return ChargeTypeCategory.PROVINCIAL_INSURANCE.getCode();
        }
        if (insu.startsWith("2201")) {
            return ChargeTypeCategory.MUNICIPAL_INSURANCE.getCode();
        }
        return null;
    }

    private static String inferPreferredAdmVs(String insu) {
        if (insu.startsWith("2299")) {
            return "229900";
        }
        if (insu.startsWith("2201")) {
            return "220199";
        }
        return null;
    }

    private static String mapAreaKindToMatchStatus(String areaKind) {
        if (RegisterBenefitAreaHintSupport.LOCAL.equals(areaKind)) {
            return "MATCH";
        }
        if (RegisterBenefitAreaHintSupport.IN_PROVINCE.equals(areaKind)) {
            return "IN_PROVINCE";
        }
        if (RegisterBenefitAreaHintSupport.CROSS_PROVINCE.equals(areaKind)) {
            return "CROSS_PROVINCE";
        }
        return "UNKNOWN";
    }

    private static String normalizeAdmdvs(String admdvs) {
        if (StringUtils.isBlank(admdvs)) {
            return "";
        }
        return admdvs.replaceAll("\\D", "");
    }

    private static int commonPrefixLength(String left, String right) {
        int max = Math.min(left.length(), right.length());
        int len = 0;
        while (len < max && left.charAt(len) == right.charAt(len)) {
            len++;
        }
        return len;
    }
}

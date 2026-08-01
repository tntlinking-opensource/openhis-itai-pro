/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionquery.support;

import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.share.constants.OutpatientPharmacyPrescriptionQueryConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.ContractCategory;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 门诊处方查询筛选项装配与就诊范围预解析（AS-OP-PHA-005 / BR-OPQ-011）。
 *
 * <p>职责边界：枚举与固定三态下拉；将患者关键字或全院门诊 AMB 范围写入
 * {@link PatientScope#resolvedEncounterIds}，供领域层 IN 收窄（B.6.4，禁止 EXISTS 就诊表）。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientPharmacyPrescriptionQuerySupport {

    /** 就诊读侧：busNo 关键字、AMB 过滤、无关键字时的全院 AMB 窗。 */
    private final EncounterQueryService encounterQueryService;

    /** 患者读侧：列表关键字 → patientId。 */
    private final PatientQueryService patientQueryService;

    /** 费用性质读侧：校验合同分类是否有主数据可展示。 */
    private final ContractQueryService contractQueryService;

    /** 组织读侧：开单科室下拉（门诊启用科室）。 */
    private final OrganizationQueryService organizationQueryService;

    /**
     * 就诊范围预解析结果：须写入领域 {@code resolvedEncounterIds}（可为空列表表示拒查）。
     */
    @Getter
    @Setter
    public static class PatientScope {

        /**
         * 预解析 AMB 就诊主键；空列表表示无命中须拒查。
         * <p>AS-SDD §3.3 步骤 3：有无患者关键字均须写入本字段，不得为 {@code null}。</p>
         */
        private List<Long> resolvedEncounterIds;
    }

    /**
     * 收费状态下拉：待收费 / 已结算 / 退款中 / 已退款（AS §4 IF-OPQ-01）。
     *
     * @return 非空选项列表
     */
    public List<OptionDto> buildChargeStatusOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.toOption(ChargeItemStatus.PLANNED.getCode(), ChargeItemStatus.PLANNED.getDisplay()));
        options.add(this.toOption(ChargeItemStatus.SETTLED.getCode(), ChargeItemStatus.SETTLED.getDisplay()));
        options.add(this.toOption(ChargeItemStatus.REFUNDING.getCode(), ChargeItemStatus.REFUNDING.getDisplay()));
        options.add(this.toOption(ChargeItemStatus.REFUNDED.getCode(), ChargeItemStatus.REFUNDED.getDisplay()));
        return options;
    }

    /**
     * 发药状态下拉：覆盖未发与已发常用态（AS §4 IF-OPQ-01）。
     *
     * @return 非空选项列表
     */
    public List<OptionDto> buildDispenseStatusOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.toOption(DispenseStatus.PREPARATION.getCode(), DispenseStatus.PREPARATION.getDisplay()));
        options.add(this.toOption(DispenseStatus.PREPARED.getCode(), DispenseStatus.PREPARED.getDisplay()));
        options.add(this.toOption(DispenseStatus.IN_PROGRESS.getCode(), DispenseStatus.IN_PROGRESS.getDisplay()));
        options.add(this.toOption(DispenseStatus.COMPLETED.getCode(), DispenseStatus.COMPLETED.getDisplay()));
        options.add(this.toOption(DispenseStatus.PENDING_REFUND.getCode(), DispenseStatus.PENDING_REFUND.getDisplay()));
        options.add(this.toOption(DispenseStatus.REFUNDED.getCode(), DispenseStatus.REFUNDED.getDisplay()));
        return options;
    }

    /**
     * 费用性质分类下拉：仅自费 / 省医保 / 市医保三档（产品口径）；主数据为空时仍返回三档全集。
     *
     * @return 非空选项列表
     */
    public List<OptionDto> buildContractCategoryOptions() {
        List<ContractListItemQueryModel> contracts = this.contractQueryService.listAllContracts();
        LinkedHashSet<String> presentCategories = new LinkedHashSet<>();
        // Loop：收集合同主数据中实际出现过的分类，优先只展示院内已配置类
        if (contracts != null) {
            for (ContractListItemQueryModel contract : contracts) {
                if (contract == null || StringUtils.isEmpty(contract.getCategoryEnum())) {
                    continue;
                }
                presentCategories.add(contract.getCategoryEnum().trim());
            }
        }
        List<OptionDto> options = new ArrayList<>();
        this.appendContractCategoryIfNeeded(options, presentCategories, ContractCategory.SELF_CHARGE,
            OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_SELF_CHARGE_LABEL);
        this.appendContractCategoryIfNeeded(options, presentCategories, ContractCategory.PROVINCIAL_INSURANCE,
            OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_PROVINCIAL_INSURANCE_LABEL);
        this.appendContractCategoryIfNeeded(options, presentCategories, ContractCategory.MUNICIPAL_INSURANCE,
            OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_MUNICIPAL_INSURANCE_LABEL);
        // 主数据无任何合同时回落三档枚举，避免空下拉阻断进页
        if (options.isEmpty()) {
            options.add(this.toOption(ContractCategory.SELF_CHARGE.getCode(),
                OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_SELF_CHARGE_LABEL));
            options.add(this.toOption(ContractCategory.PROVINCIAL_INSURANCE.getCode(),
                OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_PROVINCIAL_INSURANCE_LABEL));
            options.add(this.toOption(ContractCategory.MUNICIPAL_INSURANCE.getCode(),
                OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_MUNICIPAL_INSURANCE_LABEL));
        }
        return options;
    }

    /**
     * 医保类别下拉：门诊向 {@link YbMedType}（普通门诊 / 急诊 / 慢特病 / 慢病 / 特病等）。
     *
     * @return 非空选项列表
     */
    public List<OptionDto> buildMedTypeOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.toOption(YbMedType.GENERAL_OUTPATIENT.getCode(), YbMedType.GENERAL_OUTPATIENT.getDisplay()));
        options.add(this.toOption(YbMedType.EMERGENCY.getCode(), YbMedType.EMERGENCY.getDisplay()));
        options.add(this.toOption(
            YbMedType.CHRONIC_SPECIAL_DISEASE_OUTPATIENT.getCode(),
            YbMedType.CHRONIC_SPECIAL_DISEASE_OUTPATIENT.getDisplay()));
        options.add(this.toOption(
            YbMedType.CHRONIC_DISEASE_OUTPATIENT.getCode(),
            YbMedType.CHRONIC_DISEASE_OUTPATIENT.getDisplay()));
        options.add(this.toOption(
            YbMedType.SPECIAL_DISEASE_OUTPATIENT.getCode(),
            YbMedType.SPECIAL_DISEASE_OUTPATIENT.getDisplay()));
        options.add(this.toOption(
            YbMedType.TRADITIONAL_CHINESE_MEDICINE_OUTPATIENT.getCode(),
            YbMedType.TRADITIONAL_CHINESE_MEDICINE_OUTPATIENT.getDisplay()));
        options.add(this.toOption(
            YbMedType.SPECIAL_MEDICATION.getCode(),
            YbMedType.SPECIAL_MEDICATION.getDisplay()));
        options.add(this.toOption(
            YbMedType.MATERNITY_OUTPATIENT.getCode(),
            YbMedType.MATERNITY_OUTPATIENT.getDisplay()));
        options.add(this.toOption(
            YbMedType.ACCIDENTAL_INJURY_OUTPATIENT.getCode(),
            YbMedType.ACCIDENTAL_INJURY_OUTPATIENT.getDisplay()));
        options.add(this.toOption(
            YbMedType.LOW_COPAY_OUTPATIENT.getCode(),
            YbMedType.LOW_COPAY_OUTPATIENT.getDisplay()));
        return options;
    }

    /**
     * 含输液固定三态：全部 / 是 / 否（BR-OPQ-007）。
     *
     * @return 非空选项列表
     */
    public List<OptionDto> buildInfusionFilterOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.toOption(
            OutpatientPharmacyPrescriptionQueryConstants.INFUSION_FILTER_ALL,
            OutpatientPharmacyPrescriptionQueryConstants.INFUSION_FILTER_ALL_LABEL));
        options.add(this.toOption(
            OutpatientPharmacyPrescriptionQueryConstants.INFUSION_FILTER_YES,
            OutpatientPharmacyPrescriptionQueryConstants.INFUSION_FILTER_YES_LABEL));
        options.add(this.toOption(
            OutpatientPharmacyPrescriptionQueryConstants.INFUSION_FILTER_NO,
            OutpatientPharmacyPrescriptionQueryConstants.INFUSION_FILTER_NO_LABEL));
        return options;
    }

    /**
     * 预写 AMB {@code resolvedEncounterIds}（AS-SDD §3.3 步骤 3 / BR-OPQ-002、011）。
     *
     * <p>有关键字：门诊号 + 患者列表关键字并集后再 AMB 过滤。
     * 无关键字：Loader 策略为按开方时间窗对齐就诊 {@code start_time} 拉取全院 AMB 主键
     * （{@link EncounterQueryService#listOutpatientAmbEncounterIdsForCashierScope}），不得留空 {@code null}。</p>
     *
     * @param patientKeyword     患者姓名/门诊号等；可空
     * @param authoredStartInclusive 开方时间窗起点（日初）；无关键字时用于 AMB 窗
     * @param authoredEndInclusive   开方时间窗终点（日末）；无关键字时用于 AMB 窗
     * @return 患者范围；{@code resolvedEncounterIds} 恒非 null（可为空列表）
     */
    public PatientScope enrichPatientKeywordFilters(
        String patientKeyword,
        LocalDateTime authoredStartInclusive,
        LocalDateTime authoredEndInclusive) {
        PatientScope scope = new PatientScope();
        // 无患者关键字：仍须写入 AMB 范围，防止住院已签发行混入（BR-OPQ-002）
        if (StringUtils.isEmpty(patientKeyword)) {
            List<Long> ambIds = this.encounterQueryService.listOutpatientAmbEncounterIdsForCashierScope(
                null,
                authoredStartInclusive,
                authoredEndInclusive);
            scope.setResolvedEncounterIds(ambIds);
            return scope;
        }
        // 有关键字：门诊号命中 ∪ 患者姓名关键字命中，再二次过滤为 AMB
        String keyword = patientKeyword.trim();
        LinkedHashSet<Long> union = new LinkedHashSet<>();
        union.addAll(this.encounterQueryService.filterOutpatientAmbEncounterIds(
            this.encounterQueryService.listEncounterIdsByBusNoKeyword(keyword)));
        List<Long> patientIds = this.patientQueryService.listPatientIdsByEncounterListKeyword(keyword);
        union.addAll(this.encounterQueryService.filterOutpatientAmbEncounterIds(
            this.encounterQueryService.listEncounterIdsByPatientIds(patientIds)));
        scope.setResolvedEncounterIds(new ArrayList<>(union));
        return scope;
    }

    /**
     * 开单科室下拉：门诊启用科室（与挂号科室同源）；无主数据时返回空列表。
     *
     * @return 科室选项（value=组织主键字符串，label=名称）
     */
    public List<OptionDto> buildOrganizationOptions() {
        List<OptionDto> options = new ArrayList<>();
        List<Organization> organizations =
            this.organizationQueryService.listOrganizationsForOutpatientRegistration();
        // 无科室主数据：返回空下拉，前端仍可开页
        if (organizations == null) {
            return options;
        }
        // 逐条装配 value=id、label=名称
        for (Organization organization : organizations) {
            // 跳过无主键脏行
            if (organization == null || organization.getId() == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(organization.getId()));
            // 名称为空时仅回填 value，避免 NPE
            if (organization.getName() != null) {
                option.setLabel(organization.getName().trim());
            }
            options.add(option);
        }
        return options;
    }

    /**
     * 费用性质分类文案（列表行展示）；未知码返回 {@code null}。
     *
     * @param categoryCode {@link ContractCategory} code
     * @return 中文文案
     */
    public String resolveContractCategoryText(String categoryCode) {
        if (StringUtils.isEmpty(categoryCode)) {
            return null;
        }
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
     * 当主数据已出现该分类、或尚无任何分类命中时，追加一类选项。
     *
     * @param options           输出列表
     * @param presentCategories 合同主数据中已出现的分类
     * @param category          枚举常量
     * @param label             展示文案
     */
    private void appendContractCategoryIfNeeded(
        List<OptionDto> options,
        LinkedHashSet<String> presentCategories,
        ContractCategory category,
        String label) {
        if (presentCategories.isEmpty() || presentCategories.contains(category.getCode())) {
            options.add(this.toOption(category.getCode(), label));
        }
    }

    /**
     * 组装下拉 OptionDto。
     *
     * @param value 选项值
     * @param label 展示文案
     * @return OptionDto
     */
    private OptionDto toOption(String value, String label) {
        OptionDto option = new OptionDto();
        option.setValue(value);
        option.setLabel(label);
        return option;
    }
}

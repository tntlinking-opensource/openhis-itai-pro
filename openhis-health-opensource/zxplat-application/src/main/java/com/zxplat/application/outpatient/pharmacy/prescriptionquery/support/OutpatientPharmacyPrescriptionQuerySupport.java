/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.prescriptionquery.support;

import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.zxplat.domain.financial.contract.service.ContractQueryService;
import com.zxplat.domain.share.constants.OutpatientPharmacyPrescriptionQueryConstants;
import com.zxplat.domain.share.constants.OutpatientPharmacyPrescriptionReviewConstants;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.ChargeTypeCategory;
import com.zxplat.domain.share.enums.ContractCategory;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.share.enums.PrescriptionReviewStatus;
import com.zxplat.domain.share.enums.YbMedType;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.util.StringUtils;
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
     * @param patientKeyword     患者姓名/身份证/门诊号等；可空；身份证用于重名区分
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
        // 有关键字：门诊号命中 ∪ 患者列表关键字命中（姓名/身份证/拼音/五笔等同族），再二次过滤为 AMB
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
     * 点评状态下拉：待点评 / 已点评（与点评记录页同码）。
     *
     * @return 非空选项列表
     */
    public List<OptionDto> buildReviewStatusOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.toOption(
            PrescriptionReviewStatus.PENDING.getCode(),
            PrescriptionReviewStatus.PENDING.getDisplay()));
        options.add(this.toOption(
            PrescriptionReviewStatus.REVIEWED.getCode(),
            PrescriptionReviewStatus.REVIEWED.getDisplay()));
        return options;
    }

    /**
     * 是否合理下拉：合理 / 不合理（value 为字符串化布尔，与点评记录页同口径）。
     *
     * @return 非空选项列表
     */
    public List<OptionDto> buildReasonableOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.toOption(
            OutpatientPharmacyPrescriptionReviewConstants.REASONABLE_TRUE_VALUE,
            OutpatientPharmacyPrescriptionReviewConstants.REASONABLE_TRUE_LABEL));
        options.add(this.toOption(
            OutpatientPharmacyPrescriptionReviewConstants.REASONABLE_FALSE_VALUE,
            OutpatientPharmacyPrescriptionReviewConstants.REASONABLE_FALSE_LABEL));
        return options;
    }

    /**
     * 费用性质分类文案（列表行展示）；未知码返回 {@code null}。
     *
     * <p>兼容 {@link ContractCategory} 枚举名与 {@link ChargeTypeCategory} 数字码（库内常见 1～4）。</p>
     *
     * @param categoryCode 合同 {@code category_enum}
     * @return 中文文案
     */
    public String resolveContractCategoryText(String categoryCode) {
        if (StringUtils.isEmpty(categoryCode)) {
            return null;
        }
        String code = categoryCode.trim();
        // ChargeTypeCategory：财务维护页/种子数据数字码
        if (ChargeTypeCategory.SELF_PAY.getCode().equals(code)) {
            return OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_SELF_CHARGE_LABEL;
        }
        if (ChargeTypeCategory.PUBLIC_PAY.getCode().equals(code)) {
            return OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_PUBLIC_CHARGE_LABEL;
        }
        if (ChargeTypeCategory.PROVINCIAL_INSURANCE.getCode().equals(code)) {
            return OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_PROVINCIAL_INSURANCE_LABEL;
        }
        if (ChargeTypeCategory.MUNICIPAL_INSURANCE.getCode().equals(code)) {
            return OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_MUNICIPAL_INSURANCE_LABEL;
        }
        // ContractCategory：接口筛选项 / 部分域枚举名
        if (ContractCategory.SELF_CHARGE.getCode().equals(code)) {
            return OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_SELF_CHARGE_LABEL;
        }
        if (ContractCategory.PUBLIC_CHARGE.getCode().equals(code)) {
            return OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_PUBLIC_CHARGE_LABEL;
        }
        if (ContractCategory.PROVINCIAL_INSURANCE.getCode().equals(code)) {
            return OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_PROVINCIAL_INSURANCE_LABEL;
        }
        if (ContractCategory.MUNICIPAL_INSURANCE.getCode().equals(code)) {
            return OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_MUNICIPAL_INSURANCE_LABEL;
        }
        if (ContractCategory.WORKER_INSURANCE.getCode().equals(code) || "5".equals(code)) {
            return OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_WORKER_INSURANCE_LABEL;
        }
        return null;
    }

    /**
     * 当主数据已出现该分类（含数字码别名）、或尚无任何分类命中时，追加一类选项。
     *
     * @param options           输出列表
     * @param presentCategories 合同主数据中已出现的分类
     * @param category          枚举常量（下拉 value 仍用 ContractCategory code）
     * @param label             展示文案
     */
    private void appendContractCategoryIfNeeded(
        List<OptionDto> options,
        LinkedHashSet<String> presentCategories,
        ContractCategory category,
        String label) {
        if (presentCategories.isEmpty() || this.isCategoryPresent(presentCategories, category)) {
            options.add(this.toOption(category.getCode(), label));
        }
    }

    /**
     * 主数据是否已配置该 UI 分类（ContractCategory 与 ChargeTypeCategory 双码视为同一档）。
     *
     * @param presentCategories 库内已出现的 category_enum
     * @param category          UI 分类
     * @return 是否出现过
     */
    private boolean isCategoryPresent(LinkedHashSet<String> presentCategories, ContractCategory category) {
        if (presentCategories.contains(category.getCode())) {
            return true;
        }
        if (ContractCategory.SELF_CHARGE.equals(category)) {
            return presentCategories.contains(ChargeTypeCategory.SELF_PAY.getCode());
        }
        if (ContractCategory.PUBLIC_CHARGE.equals(category)) {
            return presentCategories.contains(ChargeTypeCategory.PUBLIC_PAY.getCode());
        }
        if (ContractCategory.PROVINCIAL_INSURANCE.equals(category)) {
            return presentCategories.contains(ChargeTypeCategory.PROVINCIAL_INSURANCE.getCode());
        }
        if (ContractCategory.MUNICIPAL_INSURANCE.equals(category)) {
            return presentCategories.contains(ChargeTypeCategory.MUNICIPAL_INSURANCE.getCode());
        }
        if (ContractCategory.WORKER_INSURANCE.equals(category)) {
            return presentCategories.contains("5");
        }
        return false;
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

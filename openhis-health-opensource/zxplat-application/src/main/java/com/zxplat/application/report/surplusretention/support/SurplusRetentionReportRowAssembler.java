/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.surplusretention.support;

import com.zxplat.application.shared.pharmacy.PharmacyDispenseRecordChargePricingAssembler;
import com.zxplat.application.report.surplusretention.dto.SurplusRetentionReportRowDto;
import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.domain.administration.account.entity.Account;
import com.zxplat.domain.administration.account.service.AccountQueryService;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.encounter.entity.Encounter;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.domain.custom.supplier.service.SupplierQueryService;
import com.zxplat.domain.financial.contract.service.ContractQueryService;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.medication.medicationdispense.entity.MedicationDispense;
import com.zxplat.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.zxplat.domain.medication.medicationrequest.entity.MedicationRequest;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.SurplusRetentionReportConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.enums.ChargeTypeCategory;
import com.zxplat.domain.share.enums.ContractCategory;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.share.enums.EncounterClass;
import com.zxplat.domain.share.enums.MedicationDispenseType;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeAdmissionPlatformQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargeAdmissionPlatformQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.InpatientChargeInsuranceExtension;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientRegistrationPlatformQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientRegistrationPlatformQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.OutpatientRegistrationInsuranceExtension;
import com.zxplat.domain.infrastructure.integration.insurance.platformdict.InsurancePlatformDictDisplayExtension;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 药品结余留用报表行装配：页内主键批量装载后映射 19 列（AS-RPT-015；#4M-RFND 退药售价补价；
 * BR-RPT-SRB-007 参保类型=合同三值、参保地区自费写「自费」/医保写参保地；BR-RPT-SRB-008 门诊入出院=处方日期）。
 */
@Component
@RequiredArgsConstructor
public class SurplusRetentionReportRowAssembler {

    /** 药品申请读侧（签发时间与退药关联药嘱）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /** 药品目录读侧（品名/规格/剂型/包装/集采/厂家）。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;

    /** 发放读侧（实体 → slip，供 #4M-RFND 补价）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 就诊读侧（门诊住院号、入出院时刻）。 */
    private final EncounterQueryService encounterQueryService;

    /** 患者读侧（姓名批量）。 */
    private final PatientQueryService patientQueryService;

    /** 供应商读侧（生产企业名称）。 */
    private final SupplierQueryService supplierQueryService;

    /** 收费明细读侧（发放锚点售价与挂账账户）。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 记账账户读侧（账户 → 合同号）。 */
    private final AccountQueryService accountQueryService;

    /** 费用性质合同读侧（合同号 → 分类码）。 */
    private final ContractQueryService contractQueryService;

    /** 退药售价补价（#4M-RFND，写正额绝对值）。 */
    private final PharmacyDispenseRecordChargePricingAssembler pharmacyDispenseRecordChargePricingAssembler;

    /** 术语标签批量翻译（类别/剂型/单位）。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 门诊平台挂号快照（参保地区划）。 */
    private final OutpatientRegistrationInsuranceExtension outpatientRegistrationInsuranceExtension;

    /** 住院平台入院快照（参保地区划，来自预结算）。 */
    private final InpatientChargeInsuranceExtension inpatientChargeInsuranceExtension;

    /** 1901 参保地区划码 → 展示名。 */
    private final InsurancePlatformDictDisplayExtension platformDictDisplayExtension;

    /**
     * 将患者发退药实体装配为结余留用 19 列行。
     *
     * @param dispenses 发放实体页内容
     * @return 展示行；入参空则空列表
     */
    public List<SurplusRetentionReportRowDto> assembleRows(List<MedicationDispense> dispenses) {
        // 无明细：直接空
        if (dispenses == null || dispenses.isEmpty()) {
            return List.of();
        }
        EnrichmentContext ctx = this.buildEnrichmentContext(dispenses);
        List<SurplusRetentionReportRowDto> rows = new ArrayList<>(dispenses.size());
        // Loop：逐发放行映射 19 列
        for (MedicationDispense dispense : dispenses) {
            // 跳过空行
            if (dispense == null) {
                continue;
            }
            SurplusRetentionReportRowDto row = new SurplusRetentionReportRowDto();
            MedicationRequest request = null;
            if (dispense.getMedReqId() != null) {
                request = ctx.requestById.get(dispense.getMedReqId());
            }
            MedicationDefinition def = null;
            if (dispense.getMedicationId() != null) {
                def = ctx.medicationById.get(dispense.getMedicationId());
            }
            Encounter encounter = null;
            if (dispense.getEncounterId() != null) {
                encounter = ctx.encounterById.get(dispense.getEncounterId());
            }
            // 门诊住院号 ← 就诊 busNo
            if (encounter != null) {
                row.setOutpatientInpatientNo(StringUtils.trimToNull(encounter.getBusNo()));
            }
            // 患者姓名
            if (dispense.getPatientId() != null) {
                row.setPatientName(ctx.patientNameById.get(dispense.getPatientId()));
            }
            // 处方日期 ← 申请签发时间（门诊入出院回落依赖此值，须先写）
            LocalDateTime prescriptionDate = null;
            if (request != null) {
                prescriptionDate = request.getReqAuthoredTime();
                row.setPrescriptionDate(prescriptionDate);
            }
            // 参保类型 ← 发放挂账合同分类（省医保/市医保/自费）；无挂账默认自费
            String insuredCategoryText = SurplusRetentionReportConstants.INSURED_CATEGORY_SELF_PAY;
            if (dispense.getId() != null) {
                String resolved = ctx.insuredCategoryTextByDispenseId.get(dispense.getId());
                if (resolved != null) {
                    insuredCategoryText = resolved;
                }
            }
            row.setInsuredType(insuredCategoryText);
            // 参保地区：自费写「自费」；医保写就诊平台参保地展示名
            row.setInsuredRegion(this.resolveInsuredRegionText(
                insuredCategoryText,
                dispense.getEncounterId(),
                ctx.insuredRegionTextByEncounterId));
            // 入出院：住院取 Encounter；门诊无时刻时与处方日期一致（BR-RPT-SRB-008）
            this.fillAdmissionDischargeTimes(row, encounter, prescriptionDate);
            // 目录列
            if (def != null) {
                row.setDrugGenericName(StringUtils.trimToNull(def.getName()));
                // 产品名称 ← 目录商品名（必填字段原样输出，不做占位替换）
                row.setProductName(StringUtils.trimToNull(def.getMerchandiseName()));
                row.setDrugCategoryText(this.labelOf(ctx.categoryLabelByCode, def.getCategoryCode()));
                row.setDoseFormText(this.labelOf(ctx.doseFormLabelByCode, def.getDoseFormCode()));
                row.setSpecification(StringUtils.trimToNull(def.getTotalVolume()));
                String packageUnitText = this.labelOf(ctx.unitLabelByCode, def.getPackageUnitCode());
                row.setPackageUnitText(packageUnitText);
                // 计价单位与包装同口径（售药单位）
                row.setPricingUnitText(packageUnitText);
                // 是否中选 ← centralPurchaseFlag
                if (Boolean.TRUE.equals(def.getCentralPurchaseFlag())) {
                    row.setSelectedProductText(SurplusRetentionReportConstants.YES_TEXT);
                } else {
                    row.setSelectedProductText(SurplusRetentionReportConstants.NO_TEXT);
                }
                if (def.getManufacturerId() != null) {
                    row.setManufacturerName(ctx.manufacturerNameById.get(def.getManufacturerId()));
                }
            } else {
                // 无目录：中选回落否
                row.setSelectedProductText(SurplusRetentionReportConstants.NO_TEXT);
            }
            // 数量 / 售价（退药负号）
            BigDecimal quantity = dispense.getDispenseQuantity();
            if (quantity == null) {
                quantity = dispense.getQuantity();
            }
            if (quantity == null) {
                quantity = BigDecimal.ZERO;
            }
            boolean refundRow = this.isRefundDispense(dispense);
            BigDecimal unitPrice = null;
            BigDecimal totalAmount = null;
            if (dispense.getId() != null) {
                unitPrice = ctx.unitPriceByDispenseId.get(dispense.getId());
                totalAmount = ctx.totalPriceByDispenseId.get(dispense.getId());
            }
            // 退药：数量与金额取负；单价保持正额（补价写入为正额绝对值）
            if (refundRow) {
                if (quantity.signum() > 0) {
                    quantity = quantity.negate();
                }
                if (totalAmount != null) {
                    totalAmount = MonetaryAmountSupport.applySignAtCalcScale(
                        totalAmount.abs(),
                        BigDecimal.valueOf(-1L));
                }
            }
            // 缺价保持 null，禁止 scaleDisplay(null)→0 冒充免费药
            if (unitPrice != null) {
                row.setUnitPrice(MonetaryAmountSupport.scaleDisplay(unitPrice));
            }
            row.setUsageQuantity(quantity);
            if (totalAmount != null) {
                row.setUsageAmount(MonetaryAmountSupport.scaleDisplay(totalAmount));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 批量装载申请/目录/就诊/患者/厂家/售价上下文。
     *
     * @param dispenses 本页发放行
     * @return 富集上下文
     */
    private EnrichmentContext buildEnrichmentContext(List<MedicationDispense> dispenses) {
        EnrichmentContext ctx = new EnrichmentContext();
        LinkedHashSet<Long> medReqIds = new LinkedHashSet<>();
        LinkedHashSet<Long> medicationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> patientIds = new LinkedHashSet<>();
        LinkedHashSet<Long> dispenseIds = new LinkedHashSet<>();
        List<MedicationDispenseSlipQueryModel> slipRows = new ArrayList<>(dispenses.size());
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null) {
                continue;
            }
            if (dispense.getMedReqId() != null) {
                medReqIds.add(dispense.getMedReqId());
            }
            if (dispense.getMedicationId() != null) {
                medicationIds.add(dispense.getMedicationId());
            }
            if (dispense.getEncounterId() != null) {
                encounterIds.add(dispense.getEncounterId());
            }
            if (dispense.getPatientId() != null) {
                patientIds.add(dispense.getPatientId());
            }
            if (dispense.getId() != null) {
                dispenseIds.add(dispense.getId());
            }
            slipRows.add(this.medicationDispenseQueryService.toSlipQueryModel(dispense));
        }
        // Pre：申请实体
        Map<Long, MedicationRequest> requestById =
            this.medicationRequestQueryService.mapEntitiesByIds(medReqIds);
        ctx.requestById = requestById;
        // Pre：目录
        Map<Long, MedicationDefinition> medicationById = new HashMap<>();
        LinkedHashSet<Long> manufacturerIds = new LinkedHashSet<>();
        for (MedicationDefinition def : this.medicationDefinitionQueryService.findAllByIds(medicationIds)) {
            if (def == null || def.getId() == null) {
                continue;
            }
            medicationById.put(def.getId(), def);
            if (def.getManufacturerId() != null) {
                manufacturerIds.add(def.getManufacturerId());
            }
        }
        ctx.medicationById = medicationById;
        // Pre：就诊
        Map<Long, Encounter> encounterById = new HashMap<>();
        for (Encounter encounter : this.encounterQueryService.listEncounterEntitiesByIds(encounterIds)) {
            if (encounter != null && encounter.getId() != null) {
                encounterById.put(encounter.getId(), encounter);
            }
        }
        ctx.encounterById = encounterById;
        // Pre：患者姓名
        ctx.patientNameById = this.patientQueryService.findPatientNamesByIds(patientIds);
        // Pre：厂家名
        ctx.manufacturerNameById = this.supplierQueryService.mapNamesByIds(manufacturerIds);
        // Pre：术语标签
        ctx.categoryLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE);
        ctx.doseFormLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_FORM_CODE);
        ctx.unitLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        // Pre：ChargeItem 售价（按发放 id）
        Map<Long, BigDecimal> unitPriceByDispenseId = new HashMap<>();
        Map<Long, BigDecimal> totalPriceByDispenseId = new HashMap<>();
        List<ChargeItem> chargeRows =
            this.chargeItemQueryService.listChargeItemsForMedicationDispensePricingByDispenseIds(
                dispenseIds, false);
        this.mergeChargePricingFromChargeItems(chargeRows, unitPriceByDispenseId, totalPriceByDispenseId);
        // Pre：发放挂账合同 → 参保分类文案（省医保/市医保/自费）
        ctx.insuredCategoryTextByDispenseId = this.buildInsuredCategoryTextByDispenseId(chargeRows);
        // Pre：就诊 → 参保地展示名（门诊平台挂号 / 住院预结算区划 + 1901 字典）
        ctx.insuredRegionTextByEncounterId = this.buildInsuredRegionTextByEncounterId(encounterById);
        // Pre：#4M-RFND 退药补原正向售价（写入为正额，行装配再取负）
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId =
            this.indexMedicationRequestsForRefundSupplement(slipRows);
        this.pharmacyDispenseRecordChargePricingAssembler.supplementRefundPricingFromOriginMedicationRequests(
            slipRows,
            medicationRequestByMedReqId,
            unitPriceByDispenseId,
            totalPriceByDispenseId);
        ctx.unitPriceByDispenseId = unitPriceByDispenseId;
        ctx.totalPriceByDispenseId = totalPriceByDispenseId;
        return ctx;
    }

    /**
     * 解析参保地区展示文案：自费固定「自费」；医保取就诊平台参保地（缺省空串）。
     *
     * @param insuredCategoryText          参保类型三值
     * @param encounterId                  就诊主键；可空
     * @param insuredRegionTextByEncounter 就诊 → 参保地展示名
     * @return 非空文案或空串
     */
    private String resolveInsuredRegionText(
        String insuredCategoryText,
        Long encounterId,
        Map<Long, String> insuredRegionTextByEncounter) {
        // 自费：参保地与类型同写「自费」
        if (SurplusRetentionReportConstants.INSURED_CATEGORY_SELF_PAY.equals(insuredCategoryText)) {
            return SurplusRetentionReportConstants.INSURED_CATEGORY_SELF_PAY;
        }
        if (encounterId == null || insuredRegionTextByEncounter == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        String region = insuredRegionTextByEncounter.get(encounterId);
        if (region == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return region;
    }

    /**
     * 按就诊批量解析参保地展示名（门诊挂号快照 / 住院入院快照 → 区划码 → 1901 标签）。
     *
     * @param encounterById 页内就诊实体
     * @return encounterId → 展示名（字典未命中时回落区划码）
     */
    private Map<Long, String> buildInsuredRegionTextByEncounterId(Map<Long, Encounter> encounterById) {
        if (encounterById == null || encounterById.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<Long> ambulatoryEncounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> inpatientEncounterIds = new LinkedHashSet<>();
        for (Encounter encounter : encounterById.values()) {
            if (encounter == null || encounter.getId() == null) {
                continue;
            }
            if (EncounterClass.IMP.getCode().equals(encounter.getClassEnum())) {
                inpatientEncounterIds.add(encounter.getId());
            } else {
                ambulatoryEncounterIds.add(encounter.getId());
            }
        }
        Map<Long, String> codeByEncounterId = new HashMap<>();
        // Pre：门诊平台挂号快照参保地
        if (!ambulatoryEncounterIds.isEmpty()) {
            OutpatientRegistrationPlatformQueryExtensionIn opQuery =
                new OutpatientRegistrationPlatformQueryExtensionIn();
            opQuery.setEncounterIds(new ArrayList<>(ambulatoryEncounterIds));
            opQuery.setPaymentDraftIdByEncounter(Map.of());
            for (OutpatientRegistrationPlatformQueryExtensionOut snap
                : this.outpatientRegistrationInsuranceExtension.findPlatformSnapshots(opQuery)) {
                if (snap == null || snap.getEncounterId() == null) {
                    continue;
                }
                String code = StringUtils.trimToNull(snap.getInsuplcAdmdvs());
                if (code != null) {
                    codeByEncounterId.putIfAbsent(snap.getEncounterId(), code);
                }
            }
        }
        // Pre：住院平台入院快照参保地（预结算）
        if (!inpatientEncounterIds.isEmpty()) {
            InpatientChargeAdmissionPlatformQueryExtensionIn ipQuery =
                new InpatientChargeAdmissionPlatformQueryExtensionIn();
            ipQuery.setEncounterIds(new ArrayList<>(inpatientEncounterIds));
            for (InpatientChargeAdmissionPlatformQueryExtensionOut snap
                : this.inpatientChargeInsuranceExtension.findAdmissionPlatformSnapshots(ipQuery)) {
                if (snap == null || snap.getEncounterId() == null) {
                    continue;
                }
                String code = StringUtils.trimToNull(snap.getInsuplcAdmdvs());
                if (code != null) {
                    codeByEncounterId.putIfAbsent(snap.getEncounterId(), code);
                }
            }
        }
        if (codeByEncounterId.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<String> admdvsCodes = new LinkedHashSet<>(codeByEncounterId.values());
        Map<String, String> labelByCode = Map.of();
        // Pre：仅 Module 启用时查 1901；未装配/未命中走吉林前缀回落（同挂号历史）
        if (this.platformDictDisplayExtension.isEnabled()) {
            Map<String, String> lookedUp =
                this.platformDictDisplayExtension.lookupInsuplcAdmdvsLabels(admdvsCodes);
            if (lookedUp != null) {
                labelByCode = lookedUp;
            }
        }
        Map<Long, String> out = new HashMap<>();
        for (Map.Entry<Long, String> entry : codeByEncounterId.entrySet()) {
            String code = entry.getValue();
            String dictLabel = labelByCode.get(code);
            out.put(entry.getKey(), InsuplcAdmdvsDisplaySupport.formatDisplay(code, dictLabel));
        }
        return out;
    }

    /**
     * 按发放挂账账户解析参保分类展示文案（省医保 / 市医保 / 自费）。
     *
     * @param chargeRows 发放锚点收费行
     * @return dispenseId → 分类文案；无挂账合同的发放不含键
     */
    private Map<Long, String> buildInsuredCategoryTextByDispenseId(List<ChargeItem> chargeRows) {
        if (chargeRows == null || chargeRows.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow != null && chargeRow.getAccountId() != null) {
                accountIds.add(chargeRow.getAccountId());
            }
        }
        Map<Long, Account> accountById = this.accountQueryService.mapAccountsByIds(accountIds);
        LinkedHashSet<String> contractNos = new LinkedHashSet<>();
        for (Account account : accountById.values()) {
            if (account == null) {
                continue;
            }
            String contractNo = StringUtils.trimToNull(account.getContractNo());
            if (contractNo != null) {
                contractNos.add(contractNo);
            }
        }
        Map<String, String> categoryEnumByBusNo =
            this.contractQueryService.mapCategoryEnumByBusNoIn(contractNos);
        Map<Long, String> out = new HashMap<>();
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || chargeRow.getAccountId() == null) {
                continue;
            }
            Long dispenseKey = chargeRow.getDispenseId();
            // 药发放也可能锚在 service_id
            if (dispenseKey == null
                && TableNameConstants.MED_MEDICATION_DISPENSE.equals(chargeRow.getServiceTable())) {
                dispenseKey = chargeRow.getServiceId();
            }
            if (dispenseKey == null) {
                continue;
            }
            // 同发放多段费用取首个账户分类
            if (out.containsKey(dispenseKey)) {
                continue;
            }
            Account account = accountById.get(chargeRow.getAccountId());
            if (account == null) {
                continue;
            }
            String contractNo = StringUtils.trimToNull(account.getContractNo());
            String categoryEnum = null;
            if (contractNo != null) {
                categoryEnum = categoryEnumByBusNo.get(contractNo);
            }
            out.put(dispenseKey, this.resolveInsuredCategoryText(categoryEnum));
        }
        return out;
    }

    /**
     * 合同 category_enum → 参保展示三值（省医保 / 市医保 / 自费）。
     *
     * <p>兼容 {@link ContractCategory} 字母码与 {@link ChargeTypeCategory} 数字码；公费/工伤等非三值回落自费。</p>
     *
     * @param categoryEnum 合同分类码；可空
     * @return 非空三值之一
     */
    private String resolveInsuredCategoryText(String categoryEnum) {
        String code = StringUtils.trimToNull(categoryEnum);
        // 无分类：按自费
        if (code == null) {
            return SurplusRetentionReportConstants.INSURED_CATEGORY_SELF_PAY;
        }
        ChargeTypeCategory chargeType = EnumUtil.fromCode(ChargeTypeCategory.class, code);
        if (chargeType == ChargeTypeCategory.PROVINCIAL_INSURANCE) {
            return SurplusRetentionReportConstants.INSURED_CATEGORY_PROVINCIAL;
        }
        if (chargeType == ChargeTypeCategory.MUNICIPAL_INSURANCE) {
            return SurplusRetentionReportConstants.INSURED_CATEGORY_MUNICIPAL;
        }
        if (chargeType == ChargeTypeCategory.SELF_PAY) {
            return SurplusRetentionReportConstants.INSURED_CATEGORY_SELF_PAY;
        }
        ContractCategory contractCategory = EnumUtil.fromCode(ContractCategory.class, code);
        if (contractCategory == ContractCategory.PROVINCIAL_INSURANCE) {
            return SurplusRetentionReportConstants.INSURED_CATEGORY_PROVINCIAL;
        }
        if (contractCategory == ContractCategory.MUNICIPAL_INSURANCE) {
            return SurplusRetentionReportConstants.INSURED_CATEGORY_MUNICIPAL;
        }
        if (contractCategory == ContractCategory.SELF_CHARGE) {
            return SurplusRetentionReportConstants.INSURED_CATEGORY_SELF_PAY;
        }
        // 公费/工伤等：本报表仅三值口径，回落自费
        return SurplusRetentionReportConstants.INSURED_CATEGORY_SELF_PAY;
    }

    /**
     * 填充入院/出院时间：住院取就诊起止；门诊无时刻时与处方日期一致。
     *
     * @param row              目标行
     * @param encounter        就诊；可空
     * @param prescriptionDate 处方（签发）时刻；可空
     */
    private void fillAdmissionDischargeTimes(
        SurplusRetentionReportRowDto row,
        Encounter encounter,
        LocalDateTime prescriptionDate) {
        // 住院：优先就诊入/出院时刻
        if (encounter != null && EncounterClass.IMP.getCode().equals(encounter.getClassEnum())) {
            LocalDateTime admission = encounter.getStartTime();
            LocalDateTime discharge = encounter.getEndTime();
            // 住院缺入院时刻：回落处方日期，避免导出/列表出现空占位
            if (admission == null) {
                admission = prescriptionDate;
            }
            // 住院缺出院时刻：回落处方日期
            if (discharge == null) {
                discharge = prescriptionDate;
            }
            row.setAdmissionTime(admission);
            row.setDischargeTime(discharge);
            return;
        }
        // 门诊（及非住院）：无入出院业务时刻，与处方日期一致
        row.setAdmissionTime(prescriptionDate);
        row.setDischargeTime(prescriptionDate);
    }

    /**
     * 仅索引退药行药嘱（正向行不参与补价索引）。
     *
     * @param slipRows 发放事实
     * @return 退药相关 medReqId → 药嘱读模型
     */
    private Map<Long, MedicationRequestListQueryModel> indexMedicationRequestsForRefundSupplement(
        List<MedicationDispenseSlipQueryModel> slipRows) {
        LinkedHashSet<Long> medReqIds = new LinkedHashSet<>();
        if (slipRows == null) {
            return Map.of();
        }
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            // Gate：非退药行无需药嘱补价索引
            if (slip == null || !this.isRefundSlipRow(slip) || slip.getMedReqId() == null) {
                continue;
            }
            medReqIds.add(slip.getMedReqId());
        }
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId = new HashMap<>();
        if (medReqIds.isEmpty()) {
            return medicationRequestByMedReqId;
        }
        for (MedicationRequestListQueryModel req
            : this.medicationRequestQueryService.listMedicationRequestListModelsByIds(medReqIds)) {
            if (req != null && req.getId() != null) {
                medicationRequestByMedReqId.put(req.getId(), req);
            }
        }
        return medicationRequestByMedReqId;
    }

    /**
     * 合并发放锚点收费行售价（同 dispense 多行加总后反推单价）。
     *
     * @param chargeRows    收费行
     * @param unitPriceOut  dispenseId → 单价
     * @param totalPriceOut dispenseId → 行额合计
     */
    private void mergeChargePricingFromChargeItems(
        List<ChargeItem> chargeRows,
        Map<Long, BigDecimal> unitPriceOut,
        Map<Long, BigDecimal> totalPriceOut) {
        if (chargeRows == null || chargeRows.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> totalByDispense = new HashMap<>();
        Map<Long, BigDecimal> qtyByDispense = new HashMap<>();
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || chargeRow.getDispenseId() == null) {
                continue;
            }
            Long dispenseKey = chargeRow.getDispenseId();
            if (chargeRow.getTotalPrice() != null) {
                BigDecimal existingTotal = totalByDispense.get(dispenseKey);
                if (existingTotal == null) {
                    totalByDispense.put(dispenseKey, chargeRow.getTotalPrice());
                } else {
                    totalByDispense.put(dispenseKey, existingTotal.add(chargeRow.getTotalPrice()));
                }
            }
            if (chargeRow.getQuantityValue() != null) {
                BigDecimal existingQty = qtyByDispense.get(dispenseKey);
                if (existingQty == null) {
                    qtyByDispense.put(dispenseKey, chargeRow.getQuantityValue());
                } else {
                    qtyByDispense.put(dispenseKey, existingQty.add(chargeRow.getQuantityValue()));
                }
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : totalByDispense.entrySet()) {
            Long dispenseKey = entry.getKey();
            BigDecimal totalVal = entry.getValue();
            totalPriceOut.put(dispenseKey, totalVal);
            BigDecimal qtyVal = qtyByDispense.get(dispenseKey);
            if (qtyVal != null && qtyVal.compareTo(BigDecimal.ZERO) > 0) {
                unitPriceOut.put(dispenseKey, MonetaryAmountSupport.unitPriceFromLineTotal(totalVal, qtyVal));
            }
        }
    }

    /**
     * 判定发放实体是否退药行（类型 REFUND 或状态 REFUNDED）。
     *
     * @param dispense 发放实体
     * @return 退药为 true
     */
    private boolean isRefundDispense(MedicationDispense dispense) {
        if (dispense == null) {
            return false;
        }
        if (dispense.getDispenseEnum() == MedicationDispenseType.REFUND) {
            return true;
        }
        return DispenseStatus.REFUNDED.equals(dispense.getStatusEnum());
    }

    /**
     * 判定 slip 是否退药行。
     *
     * @param slip 发放事实
     * @return 退药为 true
     */
    private boolean isRefundSlipRow(MedicationDispenseSlipQueryModel slip) {
        if (slip == null) {
            return false;
        }
        if (slip.getDispenseEnum() == MedicationDispenseType.REFUND) {
            return true;
        }
        return DispenseStatus.REFUNDED.equals(slip.getStatusEnum());
    }

    /**
     * 术语 code → 展示文案；缺省空串。
     *
     * @param labelByCode 标签图
     * @param code        术语码
     * @return 文案或空串
     */
    private String labelOf(Map<String, String> labelByCode, String code) {
        String normalized = StringUtils.trimToNull(code);
        if (normalized == null || labelByCode == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        String label = labelByCode.get(normalized);
        if (label == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return label;
    }

    /**
     * 页内富集上下文。
     */
    private static final class EnrichmentContext {

        /** 申请 id → 实体。 */
        private Map<Long, MedicationRequest> requestById = Map.of();

        /** 目录 id → 实体。 */
        private Map<Long, MedicationDefinition> medicationById = Map.of();

        /** 就诊 id → 实体。 */
        private Map<Long, Encounter> encounterById = Map.of();

        /** 患者 id → 姓名。 */
        private Map<Long, String> patientNameById = Map.of();

        /** 厂家 id → 名称。 */
        private Map<Long, String> manufacturerNameById = Map.of();

        /** 药品类别 code → 文案。 */
        private Map<String, String> categoryLabelByCode = Map.of();

        /** 剂型 code → 文案。 */
        private Map<String, String> doseFormLabelByCode = Map.of();

        /** 单位 code → 文案。 */
        private Map<String, String> unitLabelByCode = Map.of();

        /** 发放 id → 售价单价。 */
        private Map<Long, BigDecimal> unitPriceByDispenseId = Map.of();

        /** 发放 id → 售价行额。 */
        private Map<Long, BigDecimal> totalPriceByDispenseId = Map.of();

        /** 发放 id → 参保分类文案（省医保/市医保/自费）。 */
        private Map<Long, String> insuredCategoryTextByDispenseId = Map.of();

        /** 就诊 id → 参保地展示名（医保行用；自费行不读此图）。 */
        private Map<Long, String> insuredRegionTextByEncounterId = Map.of();
    }
}

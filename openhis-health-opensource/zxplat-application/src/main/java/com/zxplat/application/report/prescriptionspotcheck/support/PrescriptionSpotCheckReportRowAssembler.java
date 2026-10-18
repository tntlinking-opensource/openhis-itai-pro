/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.prescriptionspotcheck.support;

import com.zxplat.application.report.prescriptionspotcheck.dto.PrescriptionSpotCheckHerbalRowDto;
import com.zxplat.application.report.prescriptionspotcheck.dto.PrescriptionSpotCheckWesternRowDto;
import com.zxplat.application.shared.advice.TcmMethodCodeDisplayAssembler;
import com.zxplat.application.shared.option.FrequencyOptionLoader;
import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.application.shared.product.MedicationPackageSplitQuantityDisplayAssembler;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.encounter.entity.EncounterDiagnosis;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.patient.queries.model.BatchPatientDetailQueryModel;
import com.zxplat.domain.administration.patient.queries.model.PatientCoreQueryModel;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.zxplat.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.medication.medicationdispense.entity.MedicationDispense;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.constants.DoctorStationDiagnosisConstants;
import com.zxplat.domain.share.constants.PrescriptionSpotCheckReportConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.enums.AdministrativeGender;
import com.zxplat.domain.share.utils.EncounterDiagnosisIdsSupport;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 门诊处方抽查报表行装配：页内主键批量装载后映射西药/饮片列（AS-RPT-014）。
 */
@Component
@RequiredArgsConstructor
public class PrescriptionSpotCheckReportRowAssembler {

    /** 医院/科室。 */
    private final OrganizationQueryService organizationQueryService;

    /** 患者。 */
    private final PatientQueryService patientQueryService;

    /** 医师。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 就诊诊断实体。 */
    private final EncounterQueryService encounterQueryService;

    /** 诊断定义。 */
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;

    /** 药品目录。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;

    /** 药品申请读侧：按方号补齐整方 medReq（方金额须含非本页明细行）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /** 收费行。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 发放行。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 术语标签。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 频次标签。 */
    private final FrequencyOptionLoader frequencyOptionLoader;

    /**
     * 将已签发药品行装配为西药及中成药导出行。
     *
     * @param requestRows 领域列表读模型
     * @return 西药行；入参空则空列表
     */
    public List<PrescriptionSpotCheckWesternRowDto> assembleWesternRows(
        List<MedicationRequestListQueryModel> requestRows) {
        // 无明细：直接空
        if (requestRows == null || requestRows.isEmpty()) {
            return List.of();
        }
        EnrichmentContext ctx = this.buildEnrichmentContext(requestRows);
        List<PrescriptionSpotCheckWesternRowDto> rows = new ArrayList<>(requestRows.size());
        // 同方药品顺序计数（方号 → 已分配最大序）
        Map<String, Integer> orderByPrescription = new HashMap<>();
        // Loop：逐药品申请行映射西药列
        for (MedicationRequestListQueryModel line : requestRows) {
            // 跳过空行
            if (line == null) {
                continue;
            }
            PrescriptionSpotCheckWesternRowDto row = new PrescriptionSpotCheckWesternRowDto();
            this.fillCommonHeader(row, line, ctx);
            DiagnosisBundle diagnosis = this.resolveWesternDiagnosis(line, ctx);
            row.setDiagnosisName(diagnosis.namesText());
            row.setDiagnosisCode(diagnosis.codesText());
            MedicationDefinition def = null;
            // 目录命中才填药品列
            if (line.getMedicationId() != null) {
                def = ctx.medicationById.get(line.getMedicationId());
            }
            this.fillWesternDrugColumns(row, line, def, ctx);
            row.setDrugOrder(this.resolveDrugOrder(line, orderByPrescription));
            rows.add(row);
        }
        return rows;
    }

    /**
     * 将已签发药品行装配为中药饮片导出行。
     *
     * @param requestRows 领域列表读模型
     * @return 饮片行；入参空则空列表
     */
    public List<PrescriptionSpotCheckHerbalRowDto> assembleHerbalRows(
        List<MedicationRequestListQueryModel> requestRows) {
        // 无明细：直接空
        if (requestRows == null || requestRows.isEmpty()) {
            return List.of();
        }
        EnrichmentContext ctx = this.buildEnrichmentContext(requestRows);
        List<PrescriptionSpotCheckHerbalRowDto> rows = new ArrayList<>(requestRows.size());
        // 同方药品顺序计数（方号 → 已分配最大序）
        Map<String, Integer> orderByPrescription = new HashMap<>();
        // Loop：逐药品申请行映射饮片列
        for (MedicationRequestListQueryModel line : requestRows) {
            // 跳过空行
            if (line == null) {
                continue;
            }
            PrescriptionSpotCheckHerbalRowDto row = new PrescriptionSpotCheckHerbalRowDto();
            this.fillCommonHeader(row, line, ctx);
            DiagnosisBundle tcmDisease = this.resolveTcmDiagnosisBySource(
                line, ctx, DoctorStationDiagnosisConstants.SOURCE_TCM_DX);
            DiagnosisBundle tcmSyndrome = this.resolveTcmDiagnosisBySource(
                line, ctx, DoctorStationDiagnosisConstants.SOURCE_TCM_SYNDROME);
            row.setTcmDiseaseName(tcmDisease.namesText());
            row.setTcmDiseaseCode(tcmDisease.codesText());
            row.setTcmSyndromeName(tcmSyndrome.namesText());
            row.setTcmSyndromeCode(tcmSyndrome.codesText());
            MedicationDefinition def = null;
            // 目录命中才填药品列
            if (line.getMedicationId() != null) {
                def = ctx.medicationById.get(line.getMedicationId());
            }
            this.fillHerbalDrugColumns(row, line, def, ctx);
            row.setDrugOrder(this.resolveDrugOrder(line, orderByPrescription));
            rows.add(row);
        }
        return rows;
    }

    /**
     * 填充西药/饮片共用表头字段（机构～医师）。
     *
     * @param target 行 DTO（西药或饮片）
     * @param line   药品申请行
     * @param ctx    批量上下文
     */
    private void fillCommonHeader(
        Object target,
        MedicationRequestListQueryModel line,
        EnrichmentContext ctx) {
        String prescriptionNo = StringUtils.trimToNull(line.getPrescriptionNo());
        BigDecimal amount = null;
        // 同方金额：页内同 prescription_no 药品申请收费合计后结算刻度
        if (prescriptionNo != null) {
            amount = ctx.prescriptionAmountByNo.get(prescriptionNo);
        }
        PatientCoreQueryModel patient = null;
        // 患者详情
        if (line.getPatientId() != null) {
            BatchPatientDetailQueryModel batch = ctx.patientById.get(line.getPatientId());
            if (batch != null && batch.getPatientDetail() != null) {
                patient = batch.getPatientDetail().getPatient();
            }
        }
        Organization dept = null;
        // 开单科室
        if (line.getOrgId() != null) {
            dept = ctx.organizationById.get(line.getOrgId());
        }
        Practitioner practitioner = null;
        Long practitionerId = line.getRequesterId();
        // 开方人空时回落记录人
        if (practitionerId == null) {
            practitionerId = line.getRecorderId();
        }
        if (practitionerId != null) {
            practitioner = ctx.practitionerById.get(practitionerId);
        }
        LocalDateTime openedAt = line.getReqOpenedTime();
        // 开立轴回落签发时刻
        if (openedAt == null) {
            openedAt = line.getReqAuthoredTime();
        }
        // 西药行
        if (target instanceof PrescriptionSpotCheckWesternRowDto western) {
            western.setOrgName(ctx.hospitalName);
            western.setOrgLicenseNo(ctx.hospitalYbNo);
            western.setPrescriptionNo(prescriptionNo);
            western.setPrescriptionAmount(amount);
            western.setVisitTypeText(PrescriptionSpotCheckReportConstants.VISIT_TYPE_OUTPATIENT);
            western.setOpenedAt(openedAt);
            this.fillPatientFields(western, patient);
            this.fillDeptFields(western, dept);
            this.fillPractitionerYbNo(western, practitioner);
            return;
        }
        // 饮片行
        if (target instanceof PrescriptionSpotCheckHerbalRowDto herbal) {
            herbal.setOrgName(ctx.hospitalName);
            herbal.setOrgLicenseNo(ctx.hospitalYbNo);
            herbal.setPrescriptionNo(prescriptionNo);
            herbal.setPrescriptionAmount(amount);
            herbal.setVisitTypeText(PrescriptionSpotCheckReportConstants.VISIT_TYPE_OUTPATIENT);
            herbal.setOpenedAt(openedAt);
            this.fillPatientFields(herbal, patient);
            this.fillDeptFields(herbal, dept);
            this.fillPractitionerYbNo(herbal, practitioner);
        }
    }

    /**
     * 写入患者 busNo / 性别 / 出生日期（西药）。
     *
     * @param western 西药行
     * @param patient 患者核心
     */
    private void fillPatientFields(PrescriptionSpotCheckWesternRowDto western, PatientCoreQueryModel patient) {
        // 无患者：患者列空
        if (patient == null) {
            return;
        }
        western.setPatientBusNo(StringUtils.trimToNull(patient.getBusNo()));
        western.setGenderText(this.resolveGenderShort(patient.getGenderEnum()));
        western.setBirthDate(patient.getBirthDate());
    }

    /**
     * 写入患者 busNo / 性别 / 出生日期（饮片）。
     *
     * @param herbal  饮片行
     * @param patient 患者核心
     */
    private void fillPatientFields(PrescriptionSpotCheckHerbalRowDto herbal, PatientCoreQueryModel patient) {
        // 无患者：患者列空
        if (patient == null) {
            return;
        }
        herbal.setPatientBusNo(StringUtils.trimToNull(patient.getBusNo()));
        herbal.setGenderText(this.resolveGenderShort(patient.getGenderEnum()));
        herbal.setBirthDate(patient.getBirthDate());
    }

    /**
     * 写入科室名称与 yb_no（西药）。
     *
     * @param western 西药行
     * @param dept    科室组织
     */
    private void fillDeptFields(PrescriptionSpotCheckWesternRowDto western, Organization dept) {
        // 无科室：科室列空
        if (dept == null) {
            return;
        }
        western.setDeptName(StringUtils.trimToNull(dept.getName()));
        western.setDeptYbNo(StringUtils.trimToNull(dept.getYbNo()));
    }

    /**
     * 写入科室名称与 yb_no（饮片）。
     *
     * @param herbal 饮片行
     * @param dept   科室组织
     */
    private void fillDeptFields(PrescriptionSpotCheckHerbalRowDto herbal, Organization dept) {
        // 无科室：科室列空
        if (dept == null) {
            return;
        }
        herbal.setDeptName(StringUtils.trimToNull(dept.getName()));
        herbal.setDeptYbNo(StringUtils.trimToNull(dept.getYbNo()));
    }

    /**
     * 写入医师 yb_no（西药）。
     *
     * @param western      西药行
     * @param practitioner 医师
     */
    private void fillPractitionerYbNo(
        PrescriptionSpotCheckWesternRowDto western,
        Practitioner practitioner) {
        // 无医师：医师代码空
        if (practitioner == null) {
            return;
        }
        western.setPractitionerYbNo(StringUtils.trimToNull(practitioner.getYbNo()));
    }

    /**
     * 写入医师 yb_no（饮片）。
     *
     * @param herbal       饮片行
     * @param practitioner 医师
     */
    private void fillPractitionerYbNo(
        PrescriptionSpotCheckHerbalRowDto herbal,
        Practitioner practitioner) {
        // 无医师：医师代码空
        if (practitioner == null) {
            return;
        }
        herbal.setPractitionerYbNo(StringUtils.trimToNull(practitioner.getYbNo()));
    }

    /**
     * 填充西药药品明细列。
     *
     * @param row  西药行
     * @param line 药品申请
     * @param def  目录；可空
     * @param ctx  上下文
     */
    private void fillWesternDrugColumns(
        PrescriptionSpotCheckWesternRowDto row,
        MedicationRequestListQueryModel line,
        MedicationDefinition def,
        EnrichmentContext ctx) {
        // 目录存在：编码/医保名/剂型/规格/包装
        if (def != null) {
            row.setDrugBusNo(StringUtils.trimToNull(def.getBusNo()));
            row.setDrugYbNo(StringUtils.trimToNull(def.getYbNo()));
            row.setDrugYbName(StringUtils.trimToNull(def.getName()));
            row.setDoseFormText(this.labelOf(ctx.doseFormLabelByCode, def.getDoseFormCode()));
            row.setSpecification(StringUtils.trimToNull(def.getTotalVolume()));
            row.setPackagingText(this.buildPackagingText(def, ctx));
        }
        row.setMethodText(this.labelOf(ctx.methodLabelByCode, line.getMethodCode()));
        row.setRateText(this.labelOf(ctx.rateLabelByCode, line.getRateCode()));
        row.setDoseQuantity(line.getDose());
        row.setDoseUnitText(this.resolveUnitLabel(ctx, line.getDoseUnitCode()));
        MedicationDispense dispense = null;
        // 发放数量：优先 dispenseQuantity，否则 quantity
        if (line.getId() != null) {
            dispense = ctx.dispenseByMedReqId.get(line.getId());
        }
        if (dispense != null) {
            BigDecimal qty = dispense.getDispenseQuantity();
            // 实发空则回落开立数量
            if (qty == null) {
                qty = dispense.getQuantity();
            }
            row.setDispenseQuantity(qty);
            row.setDispenseUnitText(this.resolveUnitLabel(ctx, dispense.getUnitCode()));
        }
    }

    /**
     * 填充饮片药品明细列。
     *
     * @param row  饮片行
     * @param line 药品申请
     * @param def  目录；可空
     * @param ctx  上下文
     */
    private void fillHerbalDrugColumns(
        PrescriptionSpotCheckHerbalRowDto row,
        MedicationRequestListQueryModel line,
        MedicationDefinition def,
        EnrichmentContext ctx) {
        // 目录存在：名称/编码/医保码
        if (def != null) {
            row.setDrugName(StringUtils.trimToNull(def.getName()));
            row.setDrugBusNo(StringUtils.trimToNull(def.getBusNo()));
            row.setDrugYbNo(StringUtils.trimToNull(def.getYbNo()));
        }
        row.setDoseCount(line.getChineseHerbsDoseQuantity());
        // 中药用法：优先 tcm_method_code（如 T01→水煎服），未命中回落 method_code
        String methodText = TcmMethodCodeDisplayAssembler.resolveMethodDisplayName(
            ctx.tcmMethodLabelByCode,
            ctx.methodLabelByCode,
            Boolean.TRUE,
            line.getMethodCode());
        // 术语未命中时仍回落原码，避免空列（与西药 labelOf 口径一致）
        if (StringUtils.isEmpty(methodText)) {
            methodText = this.labelOf(ctx.methodLabelByCode, line.getMethodCode());
        }
        // 制备方法与给药途径均取方头用法文案（BR-RPT-PSC-009）
        row.setPrepareMethodText(methodText);
        row.setMethodText(methodText);
        row.setDosePerUnit(line.getDose());
        row.setDoseUnitText(this.resolveUnitLabel(ctx, line.getDoseUnitCode()));
        row.setFootnoteText(this.labelOf(ctx.dosageInstructionLabelByCode, line.getDosageInstruction()));
    }

    /**
     * 处方抽查专用「药品顺序」：按本批同方出现序从 1 起编（每张方独立，如 1,2,1,1）。
     * 与医嘱库 {@code sort_number} 无关，不读写该字段。
     *
     * @param line                药品申请
     * @param orderByPrescription 方号 → 已分配最大序
     * @return 方内顺序号；无方号则 null
     */
    private Integer resolveDrugOrder(
        MedicationRequestListQueryModel line,
        Map<String, Integer> orderByPrescription) {
        String prescriptionNo = StringUtils.trimToNull(line.getPrescriptionNo());
        // 无方号：无法按方编号
        if (prescriptionNo == null) {
            return null;
        }
        Integer current = orderByPrescription.get(prescriptionNo);
        // 同方首行：从 1 起
        if (current == null) {
            orderByPrescription.put(prescriptionNo, 1);
            return 1;
        }
        int next = current + 1;
        orderByPrescription.put(prescriptionNo, next);
        return next;
    }

    /**
     * 性别 code → 男/女短文案。
     *
     * @param genderEnum AdministrativeGender code
     * @return 短文案；未知空
     */
    private String resolveGenderShort(String genderEnum) {
        String code = StringUtils.trimToNull(genderEnum);
        // 空性别
        if (code == null) {
            return null;
        }
        // 男
        if (AdministrativeGender.MALE.getCode().equals(code)) {
            return PrescriptionSpotCheckReportConstants.GENDER_MALE_SHORT;
        }
        // 女
        if (AdministrativeGender.FEMALE.getCode().equals(code)) {
            return PrescriptionSpotCheckReportConstants.GENDER_FEMALE_SHORT;
        }
        return null;
    }

    /**
     * 解析西医诊断名称/编码（排除中医病/证来源）。
     *
     * @param line 药品申请
     * @param ctx  上下文
     * @return 诊断拼装
     */
    private DiagnosisBundle resolveWesternDiagnosis(
        MedicationRequestListQueryModel line,
        EnrichmentContext ctx) {
        List<String> names = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        List<Long> edIds = EncounterDiagnosisIdsSupport.parseOrderedIds(line.getEncounterDiagnosisIds(), true);
        for (Long edId : edIds) {
            ConditionDefinitionBatchItemQueryModel condition = this.resolveCondition(edId, ctx);
            // 无定义：跳过
            if (condition == null) {
                continue;
            }
            String source = StringUtils.trimToNull(condition.getSourceEnum());
            // 中医病/证不入西药诊断列
            if (DoctorStationDiagnosisConstants.SOURCE_TCM_DX.equals(source)
                || DoctorStationDiagnosisConstants.SOURCE_TCM_SYNDROME.equals(source)) {
                continue;
            }
            String name = StringUtils.trimToNull(condition.getName());
            String code = this.resolveDiagnosisCode(condition);
            // 名称非空才拼入
            if (name != null) {
                names.add(name);
            }
            // 编码非空才拼入
            if (code != null) {
                codes.add(code);
            }
        }
        return new DiagnosisBundle(this.joinByDunhao(names), this.joinByDunhao(codes));
    }

    /**
     * 按中医来源解析病/证名称与编码。
     *
     * @param line       药品申请
     * @param ctx        上下文
     * @param sourceEnum 目标来源（tcm_dx / tcm_syndrome）
     * @return 诊断拼装
     */
    private DiagnosisBundle resolveTcmDiagnosisBySource(
        MedicationRequestListQueryModel line,
        EnrichmentContext ctx,
        String sourceEnum) {
        List<String> names = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        List<Long> edIds = EncounterDiagnosisIdsSupport.parseOrderedIds(line.getEncounterDiagnosisIds(), true);
        for (Long edId : edIds) {
            ConditionDefinitionBatchItemQueryModel condition = this.resolveCondition(edId, ctx);
            // 无定义：跳过
            if (condition == null) {
                continue;
            }
            String source = StringUtils.trimToNull(condition.getSourceEnum());
            // 来源不匹配：跳过
            if (!sourceEnum.equals(source)) {
                continue;
            }
            String name = StringUtils.trimToNull(condition.getName());
            String code = this.resolveDiagnosisCode(condition);
            // 名称非空才拼入
            if (name != null) {
                names.add(name);
            }
            // 编码非空才拼入
            if (code != null) {
                codes.add(code);
            }
        }
        return new DiagnosisBundle(this.joinByDunhao(names), this.joinByDunhao(codes));
    }

    /**
     * 就诊诊断 id → 诊断定义。
     *
     * @param encounterDiagnosisId 就诊诊断主键
     * @param ctx                  上下文
     * @return 定义；未命中 null
     */
    private ConditionDefinitionBatchItemQueryModel resolveCondition(
        Long encounterDiagnosisId,
        EnrichmentContext ctx) {
        // 空 id
        if (encounterDiagnosisId == null) {
            return null;
        }
        Long definitionId = ctx.conditionDefinitionIdByEdId.get(encounterDiagnosisId);
        // 无定义绑定
        if (definitionId == null) {
            return null;
        }
        return ctx.conditionById.get(definitionId);
    }

    /**
     * 诊断编码：优先 ybNo，回落 conditionCode。
     *
     * @param condition 诊断定义
     * @return 编码；可空
     */
    private String resolveDiagnosisCode(ConditionDefinitionBatchItemQueryModel condition) {
        String ybNo = StringUtils.trimToNull(condition.getYbNo());
        // 有医保码优先
        if (ybNo != null) {
            return ybNo;
        }
        return StringUtils.trimToNull(condition.getConditionCode());
    }

    /**
     * 顿号拼接。
     *
     * @param parts 片段
     * @return 拼接文案；空则 null
     */
    private String joinByDunhao(List<String> parts) {
        // 无片段
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            // 跳过空片段
            if (StringUtils.isEmpty(part)) {
                continue;
            }
            // 非首项加顿号
            if (builder.length() > 0) {
                builder.append('、');
            }
            builder.append(part);
        }
        // 全空
        if (builder.length() == 0) {
            return null;
        }
        return builder.toString();
    }

    /**
     * 术语/频次标签回落：码表优先，否则原码。
     *
     * @param labelByCode 标签 Map
     * @param code        码
     * @return 展示文案
     */
    private String labelOf(Map<String, String> labelByCode, String code) {
        String trimmed = StringUtils.trimToNull(code);
        // 空码
        if (trimmed == null) {
            return null;
        }
        // Map 空：回落码
        if (labelByCode == null || labelByCode.isEmpty()) {
            return trimmed;
        }
        String label = labelByCode.get(trimmed);
        // 无标签：回落码
        if (StringUtils.isEmpty(label)) {
            return trimmed;
        }
        return label;
    }

    /**
     * 单位文案：剂量码表优先，计量单位码表回落。
     *
     * @param ctx      上下文
     * @param unitCode 单位码
     * @return 展示文案
     */
    private String resolveUnitLabel(EnrichmentContext ctx, String unitCode) {
        String trimmed = StringUtils.trimToNull(unitCode);
        // 空单位
        if (trimmed == null) {
            return null;
        }
        String doseUnitLabel = null;
        // 剂量单位码表
        if (ctx.doseUnitLabelByCode != null) {
            doseUnitLabel = ctx.doseUnitLabelByCode.get(trimmed);
        }
        // 剂量码表命中
        if (StringUtils.isNotEmpty(doseUnitLabel)) {
            return doseUnitLabel;
        }
        return this.labelOf(ctx.unitLabelByCode, trimmed);
    }

    /**
     * 拼装包装文案（库房口径 1盒/36片）。
     *
     * @param def 目录
     * @param ctx 上下文
     * @return 包装文案
     */
    private String buildPackagingText(MedicationDefinition def, EnrichmentContext ctx) {
        String packageLabel = this.resolveUnitLabel(ctx, def.getPackageUnitCode());
        String splitLabel = this.resolveUnitLabel(ctx, def.getSplitUnitCode());
        return MedicationPackageSplitQuantityDisplayAssembler.buildInventoryPackageSplitRatioText(
            def.getPackageUnitRatio(),
            packageLabel,
            splitLabel);
    }

    /**
     * 页内批量装载 enrichment 上下文。
     *
     * @param requestRows 药品申请行
     * @return 上下文
     */
    private EnrichmentContext buildEnrichmentContext(List<MedicationRequestListQueryModel> requestRows) {
        EnrichmentContext ctx = new EnrichmentContext();
        this.fillHospital(ctx);
        LinkedHashSet<Long> patientIds = new LinkedHashSet<>();
        LinkedHashSet<Long> orgIds = new LinkedHashSet<>();
        LinkedHashSet<Long> practitionerIds = new LinkedHashSet<>();
        LinkedHashSet<Long> medicationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> medReqIds = new LinkedHashSet<>();
        LinkedHashSet<Long> encounterDiagnosisIds = new LinkedHashSet<>();
        LinkedHashSet<String> prescriptionNos = new LinkedHashSet<>();
        for (MedicationRequestListQueryModel line : requestRows) {
            // 跳过空行
            if (line == null) {
                continue;
            }
            if (line.getPatientId() != null) {
                patientIds.add(line.getPatientId());
            }
            if (line.getOrgId() != null) {
                orgIds.add(line.getOrgId());
            }
            Long practitionerId = line.getRequesterId();
            // 开方人空回落记录人
            if (practitionerId == null) {
                practitionerId = line.getRecorderId();
            }
            if (practitionerId != null) {
                practitionerIds.add(practitionerId);
            }
            if (line.getMedicationId() != null) {
                medicationIds.add(line.getMedicationId());
            }
            if (line.getId() != null) {
                medReqIds.add(line.getId());
            }
            String prescriptionNo = StringUtils.trimToNull(line.getPrescriptionNo());
            // 收集方号：方金额须按整方汇总，不能只加本页行
            if (prescriptionNo != null) {
                prescriptionNos.add(prescriptionNo);
            }
            encounterDiagnosisIds.addAll(
                EncounterDiagnosisIdsSupport.parseOrderedIds(line.getEncounterDiagnosisIds(), true));
        }
        // Pre：按页内方号回查整方 medReq，避免分页切断导致处方金额偏小
        Map<String, LinkedHashSet<Long>> medReqIdsByPrescription =
            this.indexWholePrescriptionMedReqIds(prescriptionNos);
        LinkedHashSet<Long> chargeMedReqIds = new LinkedHashSet<>(medReqIds);
        for (LinkedHashSet<Long> bucket : medReqIdsByPrescription.values()) {
            chargeMedReqIds.addAll(bucket);
        }
        ctx.patientById = this.indexPatients(patientIds);
        ctx.organizationById = this.organizationQueryService.mapByIds(orgIds);
        ctx.practitionerById = this.indexPractitioners(practitionerIds);
        ctx.medicationById = this.indexMedications(medicationIds);
        ctx.dispenseByMedReqId = this.indexDispenses(medReqIds);
        Map<Long, BigDecimal> chargeTotalByMedReqId = this.indexChargeTotals(chargeMedReqIds);
        ctx.prescriptionAmountByNo = this.sumPrescriptionAmounts(medReqIdsByPrescription, chargeTotalByMedReqId);
        this.indexDiagnoses(ctx, encounterDiagnosisIds);
        // 中药用法术语（饮片方头 T01 等）与西医给药途径分表
        ctx.tcmMethodLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.TCM_METHOD_CODE);
        ctx.methodLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE);
        ctx.doseFormLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_FORM_CODE);
        ctx.unitLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        ctx.doseUnitLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_UNIT);
        ctx.dosageInstructionLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSAGE_INSTRUCTION);
        ctx.rateLabelByCode = this.frequencyOptionLoader.buildRateLabelMap();
        return ctx;
    }

    /**
     * 写入当前租户启用医院名称与 yb_no（取首家）。
     *
     * @param ctx 上下文
     */
    private void fillHospital(EnrichmentContext ctx) {
        List<Organization> hospitals = this.organizationQueryService.listActiveHospitals();
        // 无医院主数据：机构列空
        if (hospitals == null || hospitals.isEmpty()) {
            return;
        }
        Organization first = hospitals.get(0);
        // 首条空：跳过
        if (first == null) {
            return;
        }
        ctx.hospitalName = StringUtils.trimToNull(first.getName());
        ctx.hospitalYbNo = StringUtils.trimToNull(first.getYbNo());
    }

    /**
     * 患者批量索引。
     *
     * @param patientIds 患者主键
     * @return id → 批量详情
     */
    private Map<Long, BatchPatientDetailQueryModel> indexPatients(LinkedHashSet<Long> patientIds) {
        Map<Long, BatchPatientDetailQueryModel> map = new HashMap<>();
        // 无患者
        if (patientIds == null || patientIds.isEmpty()) {
            return map;
        }
        Map<Long, BatchPatientDetailQueryModel> loaded =
            this.patientQueryService.batchLoadPatientDetails(new ArrayList<>(patientIds));
        // 批量结果空
        if (loaded == null || loaded.isEmpty()) {
            return map;
        }
        map.putAll(loaded);
        return map;
    }

    /**
     * 医师批量索引。
     *
     * @param practitionerIds 医师主键
     * @return id → 实体
     */
    private Map<Long, Practitioner> indexPractitioners(LinkedHashSet<Long> practitionerIds) {
        Map<Long, Practitioner> map = new HashMap<>();
        // 无医师
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return map;
        }
        List<Practitioner> rows = this.practitionerQueryService.findAllByIds(practitionerIds);
        for (Practitioner practitioner : rows) {
            // 跳过无主键
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            map.put(practitioner.getId(), practitioner);
        }
        return map;
    }

    /**
     * 药品目录批量索引。
     *
     * @param medicationIds 目录主键
     * @return id → 实体
     */
    private Map<Long, MedicationDefinition> indexMedications(LinkedHashSet<Long> medicationIds) {
        Map<Long, MedicationDefinition> map = new HashMap<>();
        // 无目录
        if (medicationIds == null || medicationIds.isEmpty()) {
            return map;
        }
        List<MedicationDefinition> rows = this.medicationDefinitionQueryService.findAllByIds(medicationIds);
        for (MedicationDefinition def : rows) {
            // 跳过无主键
            if (def == null || def.getId() == null) {
                continue;
            }
            map.put(def.getId(), def);
        }
        return map;
    }

    /**
     * 发放批量索引（同 medReq 取首条）。
     *
     * @param medReqIds 药品申请主键
     * @return medReqId → 发放
     */
    private Map<Long, MedicationDispense> indexDispenses(LinkedHashSet<Long> medReqIds) {
        Map<Long, MedicationDispense> map = new HashMap<>();
        // 无药嘱
        if (medReqIds == null || medReqIds.isEmpty()) {
            return map;
        }
        List<MedicationDispense> rows =
            this.medicationDispenseQueryService.listMedicationDispensesByMedReqIds(medReqIds);
        for (MedicationDispense dispense : rows) {
            // 缺 medReq：跳过
            if (dispense == null || dispense.getMedReqId() == null) {
                continue;
            }
            // 已有则保留首条，避免覆盖
            if (map.containsKey(dispense.getMedReqId())) {
                continue;
            }
            map.put(dispense.getMedReqId(), dispense);
        }
        return map;
    }

    /**
     * 按页内方号批量回查整方药品申请主键（含未落在当前页的同方行）。
     *
     * @param prescriptionNos 页内处方号
     * @return 方号 → 整方 medReq 主键集合
     */
    private Map<String, LinkedHashSet<Long>> indexWholePrescriptionMedReqIds(
        LinkedHashSet<String> prescriptionNos) {
        Map<String, LinkedHashSet<Long>> medReqIdsByPrescription = new LinkedHashMap<>();
        // 无方号：无法按方汇总
        if (prescriptionNos == null || prescriptionNos.isEmpty()) {
            return medReqIdsByPrescription;
        }
        List<MedicationRequestListQueryModel> wholeLines =
            this.medicationRequestQueryService.listMedicationRequestListModelsByPrescriptionNos(prescriptionNos);
        for (MedicationRequestListQueryModel line : wholeLines) {
            // 跳过空行或缺主键
            if (line == null || line.getId() == null) {
                continue;
            }
            String prescriptionNo = StringUtils.trimToNull(line.getPrescriptionNo());
            // 无方号：跳过
            if (prescriptionNo == null) {
                continue;
            }
            LinkedHashSet<Long> bucket = medReqIdsByPrescription.get(prescriptionNo);
            if (bucket == null) {
                bucket = new LinkedHashSet<>();
                medReqIdsByPrescription.put(prescriptionNo, bucket);
            }
            bucket.add(line.getId());
        }
        return medReqIdsByPrescription;
    }

    /**
     * 按药品申请汇总收费 totalPrice。
     *
     * @param medReqIds 药品申请主键
     * @return medReqId → 行额合计（未结算刻度）
     */
    private Map<Long, BigDecimal> indexChargeTotals(LinkedHashSet<Long> medReqIds) {
        Map<Long, BigDecimal> map = new HashMap<>();
        // 无药嘱：无收费
        if (medReqIds == null || medReqIds.isEmpty()) {
            return map;
        }
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setServiceIds(medReqIds);
        List<ChargeItem> chargeItems = this.chargeItemQueryService.listChargeItemsByServiceIds(cmd);
        for (ChargeItem item : chargeItems) {
            // 缺 serviceId 或金额则跳过
            if (item == null || item.getServiceId() == null || item.getTotalPrice() == null) {
                continue;
            }
            BigDecimal current = map.get(item.getServiceId());
            // 同药嘱多收费行累加
            if (current == null) {
                map.put(item.getServiceId(), item.getTotalPrice());
            } else {
                map.put(item.getServiceId(), current.add(item.getTotalPrice()));
            }
        }
        return map;
    }

    /**
     * 同方 medReq 收费合计 → 结算刻度方金额。
     *
     * @param medReqIdsByPrescription 方号 → medReq 集合
     * @param chargeTotalByMedReqId   medReq → 收费合计
     * @return 方号 → 结算 2 位金额
     */
    private Map<String, BigDecimal> sumPrescriptionAmounts(
        Map<String, LinkedHashSet<Long>> medReqIdsByPrescription,
        Map<Long, BigDecimal> chargeTotalByMedReqId) {
        Map<String, BigDecimal> result = new HashMap<>();
        for (Map.Entry<String, LinkedHashSet<Long>> entry : medReqIdsByPrescription.entrySet()) {
            BigDecimal sum = BigDecimal.ZERO;
            boolean any = false;
            for (Long medReqId : entry.getValue()) {
                BigDecimal line = chargeTotalByMedReqId.get(medReqId);
                // 无收费行：跳过
                if (line == null) {
                    continue;
                }
                sum = sum.add(line);
                any = true;
            }
            // 全无收费：不写方金额
            if (!any) {
                continue;
            }
            result.put(entry.getKey(), MonetaryAmountSupport.scaleSettle(sum));
        }
        return result;
    }

    /**
     * 装载就诊诊断 → 定义索引。
     *
     * @param ctx                    上下文
     * @param encounterDiagnosisIds  就诊诊断主键
     */
    private void indexDiagnoses(EnrichmentContext ctx, LinkedHashSet<Long> encounterDiagnosisIds) {
        ctx.conditionDefinitionIdByEdId = new HashMap<>();
        ctx.conditionById = new HashMap<>();
        // 无诊断绑定
        if (encounterDiagnosisIds == null || encounterDiagnosisIds.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> definitionIds = new LinkedHashSet<>();
        for (EncounterDiagnosis ed :
            this.encounterQueryService.listEncounterDiagnosisEntitiesByIds(encounterDiagnosisIds)) {
            // 跳过无绑定
            if (ed == null || ed.getId() == null || ed.getConditionDefinitionId() == null) {
                continue;
            }
            ctx.conditionDefinitionIdByEdId.put(ed.getId(), ed.getConditionDefinitionId());
            definitionIds.add(ed.getConditionDefinitionId());
        }
        // 无定义 id
        if (definitionIds.isEmpty()) {
            return;
        }
        List<ConditionDefinitionBatchItemQueryModel> conditions =
            this.conditionDefinitionQueryService.getConditionDefinitionListByIds(new ArrayList<>(definitionIds));
        for (ConditionDefinitionBatchItemQueryModel item : conditions) {
            // 跳过无主键
            if (item == null || item.getId() == null) {
                continue;
            }
            ctx.conditionById.put(item.getId(), item);
        }
    }

    /**
     * 诊断名称/编码拼装结果。
     *
     * @param namesText 顿号名称
     * @param codesText 顿号编码
     */
    private record DiagnosisBundle(String namesText, String codesText) {
    }

    /**
     * 页内批量装载上下文。
     */
    private static final class EnrichmentContext {
        /** 医院名称。 */
        private String hospitalName;
        /** 医院执业许可证登记号。 */
        private String hospitalYbNo;
        /** 患者。 */
        private Map<Long, BatchPatientDetailQueryModel> patientById = Map.of();
        /** 科室。 */
        private Map<Long, Organization> organizationById = Map.of();
        /** 医师。 */
        private Map<Long, Practitioner> practitionerById = Map.of();
        /** 药品目录。 */
        private Map<Long, MedicationDefinition> medicationById = Map.of();
        /** 发放。 */
        private Map<Long, MedicationDispense> dispenseByMedReqId = Map.of();
        /** 方金额。 */
        private Map<String, BigDecimal> prescriptionAmountByNo = Map.of();
        /** 就诊诊断 → 定义 id。 */
        private Map<Long, Long> conditionDefinitionIdByEdId = Map.of();
        /** 诊断定义。 */
        private Map<Long, ConditionDefinitionBatchItemQueryModel> conditionById = Map.of();
        /** 用法标签（西医 method_code）。 */
        private Map<String, String> methodLabelByCode = Map.of();
        /** 中药用法标签（tcm_method_code）。 */
        private Map<String, String> tcmMethodLabelByCode = Map.of();
        /** 剂型标签。 */
        private Map<String, String> doseFormLabelByCode = Map.of();
        /** 计量单位标签。 */
        private Map<String, String> unitLabelByCode = Map.of();
        /** 剂量单位标签。 */
        private Map<String, String> doseUnitLabelByCode = Map.of();
        /** 特殊煎法标签。 */
        private Map<String, String> dosageInstructionLabelByCode = Map.of();
        /** 频次标签。 */
        private Map<String, String> rateLabelByCode = Map.of();
    }
}

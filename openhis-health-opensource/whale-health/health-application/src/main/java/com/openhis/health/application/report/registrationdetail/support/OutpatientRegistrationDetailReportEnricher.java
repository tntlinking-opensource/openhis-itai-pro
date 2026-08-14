/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.registrationdetail.support;

import com.openhis.health.application.report.registrationdetail.dto.OutpatientRegistrationDetailRowDto;
import com.openhis.health.application.report.registrationdetail.dto.OutpatientRegistrationDetailSummaryRowDto;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientRegistrationReportLineQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientRegistrationReportSummaryRow;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.share.constants.OutpatientRegistrationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ContractCategory;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 门诊挂号明细报表展示 enrich（号源/科室/挂号医生/接诊医生/费用性质/挂号费；AS-RPT-007 BR-RPT-ORG-004）。
 *
 * <p>禁止写入 Request/Response 包依赖；金额经 {@link MonetaryAmountSupport} 结算刻度。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientRegistrationDetailReportEnricher {

    /** 患者姓名批量。 */
    private final PatientQueryService patientQueryService;

    /** 科室名称批量。 */
    private final OrganizationQueryService organizationQueryService;

    /** 医生姓名批量。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 就诊读侧：活动接诊医生（ADMITTER）批量映射。 */
    private final EncounterQueryService encounterQueryService;

    /** 号源类型名称批量。 */
    private final HealthcareServiceQueryService healthcareServiceQueryService;

    /** 账户读侧：就诊优先合同（挂号身份费用性质，与患者卡片同口径）。 */
    private final AccountQueryService accountQueryService;

    /** 费用性质合同主数据。 */
    private final ContractQueryService contractQueryService;

    /** 挂号费 chargeItem 批量。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 领域汇总行 → 带展示名与净挂号费的 DTO 列表。
     *
     * @param rows 领域净汇总行（含计入净额的就诊主键）
     * @return 展示行；入参 null 时空列表
     */
    public List<OutpatientRegistrationDetailSummaryRowDto> enrichSummaryRows(
        List<OutpatientRegistrationReportSummaryRow> rows) {
        List<OutpatientRegistrationDetailSummaryRowDto> result = new ArrayList<>();
        // 空入参：直接空列表，避免无意义批量读
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> organizationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> practitionerIds = new LinkedHashSet<>();
        LinkedHashSet<Long> serviceTypeIds = new LinkedHashSet<>();
        LinkedHashSet<Long> feeEncounterIds = new LinkedHashSet<>();
        // 第一遍：收集批量 enrich 主键（含净额就诊，供挂号费）
        for (OutpatientRegistrationReportSummaryRow row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getOrganizationId() != null) {
                organizationIds.add(row.getOrganizationId());
            }
            if (row.getPractitionerId() != null) {
                practitionerIds.add(row.getPractitionerId());
            }
            if (row.getServiceTypeId() != null) {
                serviceTypeIds.add(row.getServiceTypeId());
            }
            if (row.getContributingEncounterIds() != null) {
                for (Long encounterId : row.getContributingEncounterIds()) {
                    if (encounterId != null) {
                        feeEncounterIds.add(encounterId);
                    }
                }
            }
        }
        Map<Long, String> orgNameById = this.organizationQueryService.mapNamesByIds(organizationIds);
        Map<Long, String> doctorNameById = this.mapPractitionerNames(practitionerIds);
        Map<Long, HealthcareService> serviceById =
            this.healthcareServiceQueryService.loadHealthcareServiceMapByIds(serviceTypeIds);
        // Pre：号源锚点挂号费（与明细列同口径）
        Map<Long, BigDecimal> feeByEncounter = this.mapRegistrationFeeByEncounterIds(feeEncounterIds);
        // 第二遍：装配展示名 + 分桶净挂号费
        for (OutpatientRegistrationReportSummaryRow row : rows) {
            if (row == null) {
                continue;
            }
            OutpatientRegistrationDetailSummaryRowDto dto = new OutpatientRegistrationDetailSummaryRowDto();
            dto.setDimensionKey(row.getDimensionKey());
            dto.setSummaryDimension(row.getSummaryDimension());
            dto.setOrganizationId(row.getOrganizationId());
            dto.setPractitionerId(row.getPractitionerId());
            dto.setServiceTypeId(row.getServiceTypeId());
            dto.setNetRegistrationCount(row.getNetRegistrationCount());
            // 科室维回填科室名
            if (row.getOrganizationId() != null) {
                dto.setOrganizationName(orgNameById.get(row.getOrganizationId()));
            }
            // 医生维回填医生名
            if (row.getPractitionerId() != null) {
                dto.setPractitionerName(doctorNameById.get(row.getPractitionerId()));
            }
            // 号源类型名（便民/普通/专家等）
            if (row.getServiceTypeId() != null) {
                HealthcareService service = serviceById.get(row.getServiceTypeId());
                if (service != null) {
                    dto.setServiceTypeName(StringUtils.trimToNull(service.getName()));
                }
            }
            dto.setRegistrationFeeAmount(
                MonetaryAmountSupport.scaleSettle(
                    this.sumRegistrationFeeForEncounters(row.getContributingEncounterIds(), feeByEncounter)));
            result.add(dto);
        }
        return result;
    }

    /**
     * 累加若干就诊的号源挂号费（缺费按 0）。
     *
     * @param encounterIds   计入净额的就诊主键
     * @param feeByEncounter 就诊 → 挂号费
     * @return 代数和；无就诊时为 {@code null}（出参再经结算刻度）
     */
    private BigDecimal sumRegistrationFeeForEncounters(
        List<Long> encounterIds,
        Map<Long, BigDecimal> feeByEncounter) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (Long encounterId : encounterIds) {
            if (encounterId == null) {
                continue;
            }
            any = true;
            BigDecimal fee = null;
            if (feeByEncounter != null) {
                fee = feeByEncounter.get(encounterId);
            }
            if (fee != null) {
                sum = sum.add(fee);
            }
        }
        if (!any) {
            return null;
        }
        return sum;
    }

    /**
     * 领域明细行 → 带患者/费用性质/挂号费的 DTO 列表。
     *
     * @param lines 领域明细行
     * @return 展示行
     */
    public List<OutpatientRegistrationDetailRowDto> enrichDetailRows(
        List<OutpatientRegistrationReportLineQueryModel> lines) {
        List<OutpatientRegistrationDetailRowDto> result = new ArrayList<>();
        // 空页：直接空列表
        if (lines == null || lines.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> patientIds = new LinkedHashSet<>();
        LinkedHashSet<Long> organizationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> doctorIds = new LinkedHashSet<>();
        LinkedHashSet<Long> serviceTypeIds = new LinkedHashSet<>();
        // 第一遍：收集页内主键供批量读
        for (OutpatientRegistrationReportLineQueryModel line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getEncounterId() != null) {
                encounterIds.add(line.getEncounterId());
            }
            if (line.getPatientId() != null) {
                patientIds.add(line.getPatientId());
            }
            if (line.getOrganizationId() != null) {
                organizationIds.add(line.getOrganizationId());
            }
            if (line.getRegistrationDoctorId() != null) {
                doctorIds.add(line.getRegistrationDoctorId());
            }
            if (line.getServiceTypeId() != null) {
                serviceTypeIds.add(line.getServiceTypeId());
            }
        }
        // Pre：接诊医生（活动 ADMITTER）按就诊批量映射；姓名与挂号医生共用执业者字典
        Map<Long, Long> admitterIdByEncounter =
            this.encounterQueryService.mapActiveAdmitterPractitionerIdByEncounterIds(encounterIds);
        for (Long admitterId : admitterIdByEncounter.values()) {
            if (admitterId != null) {
                doctorIds.add(admitterId);
            }
        }
        // Pre：患者/科室/医生/号源/费用性质合同/挂号费批量装载（A.9.1）
        Map<Long, String> patientNameById = this.patientQueryService.findPatientNamesByIds(patientIds);
        Map<Long, String> orgNameById = this.organizationQueryService.mapNamesByIds(organizationIds);
        Map<Long, String> doctorNameById = this.mapPractitionerNames(doctorIds);
        Map<Long, HealthcareService> serviceById =
            this.healthcareServiceQueryService.loadHealthcareServiceMapByIds(serviceTypeIds);
        // Pre：挂号费金额走号源 ChargeItem；费用性质走就诊优先账户（用什么挂号就是什么）
        Map<Long, BigDecimal> feeByEncounter = this.mapRegistrationFeeByEncounterIds(encounterIds);
        Map<Long, String> contractNoByEncounter =
            this.accountQueryService.mapPreferredContractNoByEncounterIds(encounterIds);
        Map<String, Contract> contractByBusNo = this.loadContractsByBusNos(contractNoByEncounter);

        // 第二遍：行字段 + BR-RPT-ORG-004 费用性质/医保类型
        for (OutpatientRegistrationReportLineQueryModel line : lines) {
            if (line == null) {
                continue;
            }
            OutpatientRegistrationDetailRowDto dto = new OutpatientRegistrationDetailRowDto();
            dto.setEncounterId(line.getEncounterId());
            dto.setPatientId(line.getPatientId());
            dto.setBusNo(line.getBusNo());
            dto.setOrganizationId(line.getOrganizationId());
            dto.setRegistrationDoctorId(line.getRegistrationDoctorId());
            dto.setServiceTypeId(line.getServiceTypeId());
            dto.setStatusEnum(line.getStatusEnum());
            dto.setRegistrationTime(line.getRegistrationTime());
            if (line.getPatientId() != null) {
                dto.setPatientName(patientNameById.get(line.getPatientId()));
            }
            if (line.getOrganizationId() != null) {
                dto.setOrganizationName(orgNameById.get(line.getOrganizationId()));
            }
            if (line.getRegistrationDoctorId() != null) {
                dto.setRegistrationDoctorName(doctorNameById.get(line.getRegistrationDoctorId()));
            }
            // 接诊医生：未接诊（无活动 ADMITTER）时留空
            if (line.getEncounterId() != null) {
                Long admitterId = admitterIdByEncounter.get(line.getEncounterId());
                dto.setAdmitterDoctorId(admitterId);
                if (admitterId != null) {
                    dto.setAdmitterDoctorName(doctorNameById.get(admitterId));
                }
            }
            if (line.getServiceTypeId() != null) {
                HealthcareService service = serviceById.get(line.getServiceTypeId());
                if (service != null) {
                    dto.setServiceTypeName(StringUtils.trimToNull(service.getName()));
                }
            }
            dto.setStatusEnum_enumText(this.resolveStatusDisplay(line.getStatusEnum()));
            this.applyFeeNature(dto, line.getEncounterId(), contractNoByEncounter, contractByBusNo);
            // 挂号费结算刻度出参
            if (line.getEncounterId() != null) {
                dto.setRegistrationFeeAmount(
                    MonetaryAmountSupport.scaleSettle(feeByEncounter.get(line.getEncounterId())));
            }
            result.add(dto);
        }
        return result;
    }

    /**
     * 回填费用性质与医保类型列（就诊优先账户：挂号身份，非账单行账户）。
     *
     * @param dto                   明细行
     * @param encounterId           就诊主键
     * @param contractNoByEncounter 就诊 → 合同业务号
     * @param contractByBusNo       合同业务号 → 合同实体
     */
    private void applyFeeNature(
        OutpatientRegistrationDetailRowDto dto,
        Long encounterId,
        Map<Long, String> contractNoByEncounter,
        Map<String, Contract> contractByBusNo) {
        // 无就诊：无法解析费用性质
        if (encounterId == null) {
            return;
        }
        String contractNo = contractNoByEncounter.get(encounterId);
        // 未开账户或无合同号：费用性质列留空
        if (StringUtils.isEmpty(contractNo)) {
            return;
        }
        dto.setFeeNatureContractNo(contractNo);
        Contract contract = contractByBusNo.get(contractNo);
        if (contract == null) {
            return;
        }
        String contractName = StringUtils.trimToNull(contract.getName());
        dto.setFeeNatureDisplay(contractName);
        String categoryCode = StringUtils.trimToNull(contract.getCategoryEnum());
        dto.setFeeNatureCategoryCode(categoryCode);
        // 自费：不展示医保类型列
        if (categoryCode == null || ContractCategory.SELF_CHARGE.getCode().equals(categoryCode)) {
            return;
        }
        ContractCategory category = EnumUtil.fromCode(ContractCategory.class, categoryCode);
        String insuranceTypeDisplay = null;
        if (category != null) {
            insuranceTypeDisplay = StringUtils.trimToNull(category.getDisplay());
        }
        // 分类 display 为空时回落合同名
        if (insuranceTypeDisplay == null) {
            insuranceTypeDisplay = contractName;
        }
        dto.setInsuranceTypeDisplay(insuranceTypeDisplay);
    }

    /**
     * 就诊状态展示：退号 / 未收费取消用窗口文案，其余走枚举 display。
     *
     * @param statusEnum 就诊状态码
     * @return 展示文案；空入参返回 null
     */
    private String resolveStatusDisplay(String statusEnum) {
        if (StringUtils.isEmpty(statusEnum)) {
            return null;
        }
        // 已收费后退号：统一「退号」
        if (EncounterStatus.CANCELLED.getCode().equals(statusEnum)) {
            return OutpatientRegistrationConstants.CLINIC_REGISTER_STATUS_TEXT_REFUNDED;
        }
        // 未收费取消：统一「未收费取消」（库内仍为 ENTERED_IN_ERROR）
        if (EncounterStatus.ENTERED_IN_ERROR.getCode().equals(statusEnum)) {
            return OutpatientRegistrationConstants.CLINIC_REGISTER_STATUS_TEXT_ENTERED_IN_ERROR;
        }
        EncounterStatus status = EnumUtil.fromCode(EncounterStatus.class, statusEnum);
        if (status != null) {
            return status.getDisplay();
        }
        return statusEnum;
    }

    /**
     * 批量解析执业者姓名。
     */
    private Map<Long, String> mapPractitionerNames(LinkedHashSet<Long> practitionerIds) {
        Map<Long, String> nameById = new HashMap<>();
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return nameById;
        }
        List<Practitioner> practitioners = this.practitionerQueryService.findAllByIds(practitionerIds);
        if (practitioners == null) {
            return nameById;
        }
        for (Practitioner practitioner : practitioners) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            nameById.put(practitioner.getId(), StringUtils.trimToNull(practitioner.getName()));
        }
        return nameById;
    }

    /**
     * 按合同业务号批量加载合同实体（去重后逐条 Optional 查询）。
     */
    private Map<String, Contract> loadContractsByBusNos(Map<Long, String> contractNoByEncounter) {
        Map<String, Contract> contractByBusNo = new HashMap<>();
        if (contractNoByEncounter == null || contractNoByEncounter.isEmpty()) {
            return contractByBusNo;
        }
        LinkedHashSet<String> busNos = new LinkedHashSet<>();
        for (String busNo : contractNoByEncounter.values()) {
            if (StringUtils.isNotEmpty(busNo)) {
                busNos.add(busNo.trim());
            }
        }
        for (String busNo : busNos) {
            this.contractQueryService.findOptionalContractEntityByBusNo(busNo)
                .ifPresent(contract -> contractByBusNo.put(busNo, contract));
        }
        return contractByBusNo;
    }

    /**
     * 批量汇总各就诊挂号费（号源锚点 {@code service_table = adm_healthcare_service} 行 total_price 代数和）。
     *
     * @param encounterIds 就诊主键
     * @return 就诊 → 金额代数和
     */
    private Map<Long, BigDecimal> mapRegistrationFeeByEncounterIds(LinkedHashSet<Long> encounterIds) {
        Map<Long, BigDecimal> feeByEncounter = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return feeByEncounter;
        }
        ChargeItemQueryCommand command = new ChargeItemQueryCommand();
        command.setEncounterIds(new ArrayList<>(encounterIds));
        List<ChargeItem> chargeLines = this.chargeItemQueryService.listChargeItemsByEncounterIds(command);
        if (chargeLines == null || chargeLines.isEmpty()) {
            return feeByEncounter;
        }
        String healthcareServiceSlot = TableNameConstants.ADM_HEALTHCARE_SERVICE;
        for (ChargeItem line : chargeLines) {
            if (line == null || line.getEncounterId() == null || line.getTotalPrice() == null) {
                continue;
            }
            if (!healthcareServiceSlot.equals(line.getServiceTable())) {
                continue;
            }
            BigDecimal current = feeByEncounter.get(line.getEncounterId());
            if (current == null) {
                feeByEncounter.put(line.getEncounterId(), line.getTotalPrice());
            } else {
                feeByEncounter.put(line.getEncounterId(), current.add(line.getTotalPrice()));
            }
        }
        return feeByEncounter;
    }
}


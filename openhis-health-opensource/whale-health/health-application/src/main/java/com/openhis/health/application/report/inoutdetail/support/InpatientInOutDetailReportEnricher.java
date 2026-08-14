/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.inoutdetail.support;

import com.openhis.health.application.report.inoutdetail.dto.InpatientInOutDetailRowDto;
import com.openhis.health.application.shared.report.PatientSettlementContextRow;
import com.openhis.health.application.shared.report.ReportPatientSettlementContextAssembler;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientInOutDetailReportLineQueryModel;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.queries.model.PatientInpatientProjectionSnapshot;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院患者入出明细报表展示 enrich（患者/科室/病区床位/转科/住院天数/入院身份费用性质/费用空值；AS-RPT-010）。
 */
@Component
@RequiredArgsConstructor
public class InpatientInOutDetailReportEnricher {

    /** 患者人口学批量。 */
    private final PatientQueryService patientQueryService;

    /** 科室名称批量。 */
    private final OrganizationQueryService organizationQueryService;

    /** 位置实体/名称批量。 */
    private final LocationQueryService locationQueryService;

    /** 入院身份费用性质（就诊优先账户，与挂号明细/患者卡片同口径）。 */
    private final ReportPatientSettlementContextAssembler reportPatientSettlementContextAssembler;

    /**
     * 领域行 → 展示行；待重结或无净额 → settledFeeAmount 置 null。
     *
     * @param lines 领域投影行
     * @param settledNetByEncounter 已结净额 Map（缺省键=无有效净额）
     * @param reversedPendingEncounterIds 待重结就诊集合
     * @return 展示行
     */
    public List<InpatientInOutDetailRowDto> enrichRows(
        List<InpatientInOutDetailReportLineQueryModel> lines,
        Map<Long, BigDecimal> settledNetByEncounter,
        Set<Long> reversedPendingEncounterIds) {
        List<InpatientInOutDetailRowDto> result = new ArrayList<>();
        // 空入参：直接空列表
        if (lines == null || lines.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> patientIds = new LinkedHashSet<>();
        LinkedHashSet<Long> locationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> organizationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        // 第一遍：收集批量 enrich 主键
        for (InpatientInOutDetailReportLineQueryModel line : lines) {
            // 跳过空投影行
            if (line == null) {
                continue;
            }
            // 有患者主键才参与人口学批量
            if (line.getPatientId() != null) {
                patientIds.add(line.getPatientId());
            }
            if (line.getEncounterId() != null) {
                encounterIds.add(line.getEncounterId());
            }
            this.collectLocationId(locationIds, line.getAdmissionWardLocationId());
            this.collectLocationId(locationIds, line.getCurrentWardLocationId());
            this.collectLocationId(locationIds, line.getCurrentHouseLocationId());
            this.collectLocationId(locationIds, line.getCurrentBedLocationId());
            // 转科后病区一并装载，供转往科室名拼接
            if (line.getTransferTargetWardLocationIds() != null) {
                for (Long transferWardId : line.getTransferTargetWardLocationIds()) {
                    this.collectLocationId(locationIds, transferWardId);
                }
            }
            // 领域兜底入院科室 id（未挂病区时展示用）
            if (line.getAdmissionOrganizationId() != null) {
                organizationIds.add(line.getAdmissionOrganizationId());
            }
        }
        Map<Long, PatientInpatientProjectionSnapshot> patientById =
            this.patientQueryService.mapInpatientProjectionSnapshotsByIds(patientIds);
        // Pre：入院身份费用性质（优先账户合同名，不跟账单行）
        Map<Long, PatientSettlementContextRow> feeNatureByEncounterId =
            this.reportPatientSettlementContextAssembler.mapContextsByEncounterIds(encounterIds);
        Map<Long, Location> locationById = this.locationQueryService.loadLocationEntityMapByIds(locationIds);
        // 入院病区/转往病区 → 科室 id（登记时科室以最早病区挂靠为准）
        for (Location location : locationById.values()) {
            // 位置挂靠科室写入批量名册
            if (location != null && location.getOrganizationId() != null) {
                organizationIds.add(location.getOrganizationId());
            }
        }
        Map<Long, String> organizationNameById = this.organizationQueryService.mapNamesByIds(organizationIds);
        Map<Long, String> locationNameById = this.locationQueryService.mapNamesByIds(locationIds);
        Set<Long> pendingSet = reversedPendingEncounterIds;
        // 待重结集合空入参按空集处理，避免 contains NPE
        if (pendingSet == null) {
            pendingSet = new HashSet<>();
        }
        // 第二遍：装配展示字段
        for (InpatientInOutDetailReportLineQueryModel line : lines) {
            // 跳过空投影行
            if (line == null) {
                continue;
            }
            InpatientInOutDetailRowDto row = new InpatientInOutDetailRowDto();
            row.setEncounterId(line.getEncounterId());
            row.setBusNo(line.getBusNo());
            row.setPatientId(line.getPatientId());
            row.setStartTime(line.getStartTime());
            row.setEndTime(line.getEndTime());
            // Asm：住院天数（入院日计第 1 天；无出院日用当天）
            row.setInpatientDays(this.resolveInpatientDays(line.getStartTime(), line.getEndTime()));
            row.setTransferred(line.isTransferred());
            PatientInpatientProjectionSnapshot patient = patientById.get(line.getPatientId());
            // 人口学：有快照则回填姓名性别年龄
            if (patient != null) {
                row.setPatientName(patient.getPatientName());
                row.setAgeText(patient.getAgeText());
                row.setGenderText(this.resolveGenderText(patient.getGenderEnum()));
            }
            Long admissionOrgId = this.resolveAdmissionOrganizationId(line, locationById);
            // 能解析入院科室才写名称（优先最早病区挂靠）
            if (admissionOrgId != null) {
                row.setAdmissionOrganizationName(organizationNameById.get(admissionOrgId));
            }
            row.setAdmissionWardName(locationNameById.get(line.getAdmissionWardLocationId()));
            row.setWardName(locationNameById.get(line.getCurrentWardLocationId()));
            row.setHouseName(locationNameById.get(line.getCurrentHouseLocationId()));
            row.setBedName(locationNameById.get(line.getCurrentBedLocationId()));
            row.setTransferTargetText(
                this.buildTransferTargetText(line.getTransferTargetWardLocationIds(), locationById, organizationNameById));
            // Asm：费用性质 = 入院身份（用什么入院就是什么）
            if (line.getEncounterId() != null) {
                PatientSettlementContextRow feeNature = feeNatureByEncounterId.get(line.getEncounterId());
                if (feeNature != null) {
                    row.setFeeNatureContractNo(feeNature.getFeeNatureContractNo());
                    row.setFeeNatureDisplay(feeNature.getFeeNatureDisplay());
                }
            }
            // 待重结 → 费用列强制空（禁止写 0）
            if (line.getEncounterId() != null && pendingSet.contains(line.getEncounterId())) {
                row.setSettledFeeAmount(null);
            } else if (settledNetByEncounter == null || line.getEncounterId() == null) {
                // 无净额 Map 或无就诊主键 → 费用空
                row.setSettledFeeAmount(null);
            } else {
                // Map 缺键表示净额为 0/无有效结算，get 得 null 即空列
                row.setSettledFeeAmount(settledNetByEncounter.get(line.getEncounterId()));
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 当前页（或导出行）是否存在转科（最早入院 WARD 之后仍有其它 WARD 轨迹）。
     *
     * @param rows 展示行
     * @return true 表示应展示转科列
     */
    public boolean resolveShowTransferColumn(List<InpatientInOutDetailRowDto> rows) {
        // 空结果集：不展示转科列
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        for (InpatientInOutDetailRowDto row : rows) {
            // 任一行已转科即打开动态列
            if (row != null && row.isTransferred()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 收集非空位置主键。
     *
     * @param sink 目标集合
     * @param locationId 位置主键
     */
    private void collectLocationId(Set<Long> sink, Long locationId) {
        // 空 id 不入批量集合
        if (locationId != null) {
            sink.add(locationId);
        }
    }

    /**
     * 入院科室：优先最早病区挂靠科室；否则回落主档 organization_id。
     *
     * @param line 领域行
     * @param locationById 位置实体 Map
     * @return 入院科室 id；可空
     */
    private Long resolveAdmissionOrganizationId(
        InpatientInOutDetailReportLineQueryModel line,
        Map<Long, Location> locationById) {
        if (line.getAdmissionWardLocationId() != null) {
            Location admissionWard = locationById.get(line.getAdmissionWardLocationId());
            // 病区挂靠科室存在则用作入院科室展示
            if (admissionWard != null && admissionWard.getOrganizationId() != null) {
                return admissionWard.getOrganizationId();
            }
        }
        return line.getAdmissionOrganizationId();
    }

    /**
     * 转往文案：按转科后病区顺序拼接挂靠科室名（去重保序）；无名则跳过。
     *
     * @param transferWardIds 转科后病区 id
     * @param locationById 位置实体
     * @param organizationNameById 科室名
     * @return 拼接文案；无则 null
     */
    private String buildTransferTargetText(
        List<Long> transferWardIds,
        Map<Long, Location> locationById,
        Map<Long, String> organizationNameById) {
        // 无转科后病区 → 转往列空
        if (transferWardIds == null || transferWardIds.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Long wardId : transferWardIds) {
            // 跳过空病区 id
            if (wardId == null) {
                continue;
            }
            Location ward = locationById.get(wardId);
            // 无病区实体则无法解析科室名
            if (ward == null || ward.getOrganizationId() == null) {
                continue;
            }
            String orgName = organizationNameById.get(ward.getOrganizationId());
            // 有科室名才入去重序列
            if (StringUtils.isNotEmpty(orgName)) {
                names.add(orgName);
            }
        }
        // 无任何可展示名 → 空列
        if (names.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String name : names) {
            // 多目标用顿号拼接
            if (!first) {
                builder.append('、');
            }
            builder.append(name);
            first = false;
        }
        return builder.toString();
    }

    /**
     * 住院天数：自然日差 + 1（入院日计第 1 天）；有出院日截至出院日，否则截至当天；至少 1 天。
     *
     * @param startTime 入院时间
     * @param endTime 出院时间；可空
     * @return 住院天数；无入院时间则 null
     */
    private Integer resolveInpatientDays(LocalDateTime startTime, LocalDateTime endTime) {
        // 无入院时间无法计日
        if (startTime == null) {
            return null;
        }
        LocalDate startDate = startTime.toLocalDate();
        LocalDate endDate;
        // 已出院用出院日，在院用当天
        if (endTime != null) {
            endDate = endTime.toLocalDate();
        } else {
            endDate = LocalDate.now();
        }
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1L;
        // 异常时间序仍展示至少 1 天
        if (days < 1L) {
            return 1;
        }
        return (int) days;
    }

    /**
     * 性别枚举 code → 展示文案；未知回落原 code。
     *
     * @param genderEnum 性别 code
     * @return 展示文案
     */
    private String resolveGenderText(String genderEnum) {
        // 无性别码 → 空展示
        if (StringUtils.isEmpty(genderEnum)) {
            return null;
        }
        AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, genderEnum);
        // 能解析枚举则用中文 display
        if (gender != null) {
            return gender.getDisplay();
        }
        return genderEnum;
    }
}

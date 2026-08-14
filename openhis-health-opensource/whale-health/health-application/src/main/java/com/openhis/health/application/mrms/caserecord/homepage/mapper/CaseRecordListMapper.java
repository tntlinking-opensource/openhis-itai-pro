/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.caserecord.homepage.mapper;

import com.openhis.health.application.mrms.caserecord.homepage.dto.CaseRecordListItemDto;
import com.openhis.health.application.shared.inpatient.AdmissionPrimaryDiagnosisAssembler;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.queries.model.CaseRecordListItemQueryModel;
import com.openhis.health.domain.share.constants.MrmsConstants;
import com.openhis.health.domain.share.enums.CaseRecordStatus;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import org.springframework.stereotype.Component;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案列表行 DTO 映射（list IF {@code mapper}）。
 *
 * <p>支持住院就诊上下文（主数据源）、{@link CaseRecordListItemQueryModel} 与 {@link CaseRecord} 实体入参。</p>
 */
@Component
public class CaseRecordListMapper {

    /**
     * 列表 QueryModel 转 DTO（历史病案主档分页路径）。
     *
     * @param row 列表 QueryModel
     * @return 列表行 DTO
     */
    public CaseRecordListItemDto toListItem(CaseRecordListItemQueryModel row) {
        CaseRecordListItemDto dto = new CaseRecordListItemDto();
        dto.setCaseRecordId(row.getId());
        dto.setMedcasno(row.getMedcasno());
        dto.setPsnName(row.getPsnName());
        dto.setIptNo(row.getIptNo());
        dto.setGendCode(row.getGendCode());
        dto.setCertno(row.getCertno());
        dto.setAdmTime(row.getAdmTime());
        dto.setDscgTime(row.getDscgTime());
        dto.setAdmCatyName(row.getAdmCatyName());
        dto.setDscgCatyName(row.getDscgCatyName());
        dto.setChfdrName(row.getChfdrName());
        dto.setMainDiagName(row.getMainDiagName());
        dto.setStatusCode(row.getStatusCode());
        dto.setChsUploadStatusCode(row.getChsUploadStatusCode());
        // 主档分页无就诊 status_enum：结算态留空，前端按未结算展示
        dto.setSettledCode(null);
        dto.setSettleUploadStatusCode(row.getSettleUploadStatusCode());
        dto.setOrderUploadStatusCode(null);
        CaseRecordStatus status = EnumUtil.fromCode(CaseRecordStatus.class, row.getStatusCode());
        // 状态码可解析时填充展示名
        if (status != null) {
            dto.setStatusLabel(status.getDisplay());
        }
        return dto;
    }

    /**
     * 主档实体转列表行 DTO。
     *
     * @param record 病案主档
     * @return 列表行 DTO
     */
    public CaseRecordListItemDto toListItem(CaseRecord record) {
        CaseRecordListItemDto dto = new CaseRecordListItemDto();
        dto.setCaseRecordId(record.getId());
        dto.setEncounterId(record.getEncounterId());
        dto.setPatientId(record.getPatientId());
        dto.setMedcasno(record.getMedcasno());
        dto.setPsnName(record.getPsnName());
        dto.setIptNo(record.getIptNo());
        dto.setGendCode(record.getGendCode());
        dto.setCertno(record.getCertno());
        dto.setAdmTime(record.getAdmTime());
        dto.setDscgTime(record.getDscgTime());
        dto.setAdmCatyName(record.getAdmCatyName());
        dto.setDscgCatyName(record.getDscgCatyName());
        dto.setChfdrName(record.getChfdrName());
        dto.setMainDiagName(record.getMainDiagName());
        dto.setStatusCode(record.getStatusCode());
        dto.setChsUploadStatusCode(record.getChsUploadStatusCode());
        // 无就诊上下文时无法判定结算态
        dto.setSettledCode(null);
        dto.setSettleUploadStatusCode(record.getSettleUploadStatusCode());
        dto.setOrderUploadStatusCode(null);
        CaseRecordStatus status = record.getStatusEnum();
        // 状态码可解析时填充展示名
        if (status != null) {
            dto.setStatusLabel(status.getDisplay());
        }
        return dto;
    }

    /**
     * 住院就诊上下文 + 可选主档 + 患者证件 → 列表行 DTO（首页工作台主路径）。
     *
     * @param context          住院就诊上下文（含诊断快照）
     * @param caseRecord       已建档主档；未建档时 null
     * @param patient          患者实体（取证件号）；可 null
     * @param primaryDiagnosis 主诊断展示（由 {@link AdmissionPrimaryDiagnosisAssembler} 解析）
     * @return 列表行 DTO
     */
    public CaseRecordListItemDto fromInpatientContext(
            InpatientEncounterContextQueryModel context,
            CaseRecord caseRecord,
            Patient patient,
            AdmissionPrimaryDiagnosisAssembler.PrimaryDiagnosisDisplay primaryDiagnosis) {
        return this.assembleFromInpatientContext(context, caseRecord, patient, primaryDiagnosis);
    }

    /**
     * 组装住院就诊列表行：就诊侧字段必填，主档侧可空并 left join 回填。
     *
     * @param context          住院就诊上下文
     * @param caseRecord       可选主档
     * @param patient          可选患者（证件号）
     * @param primaryDiagnosis 主诊断展示
     * @return 列表行 DTO
     */
    private CaseRecordListItemDto assembleFromInpatientContext(
            InpatientEncounterContextQueryModel context,
            CaseRecord caseRecord,
            Patient patient,
            AdmissionPrimaryDiagnosisAssembler.PrimaryDiagnosisDisplay primaryDiagnosis) {
        CaseRecordListItemDto dto = new CaseRecordListItemDto();
        dto.setEncounterId(context.getEncounterId());
        dto.setPatientId(context.getPatientId());
        dto.setPsnName(context.getPatientName());
        dto.setIptNo(context.getEncounterBusNo());
        dto.setGendCode(context.getGenderEnum());
        dto.setAdmTime(context.getStartTime());
        dto.setDscgTime(context.getEndTime());
        dto.setAdmCatyName(context.getOrganizationName());
        // 入院科室主键：临床门禁「本科室大夫可写」
        dto.setOrganizationId(context.getOrganizationId());
        dto.setChfdrName(context.getAttendingDoctorName());
        // 主诊断：入院主诊断快照名称
        if (primaryDiagnosis != null) {
            dto.setMainDiagName(primaryDiagnosis.getPrimaryConditionDefinitionName());
        }
        // 证件号：默认取患者档案；主档已有 certno 时优先主档冗余
        if (patient != null) {
            dto.setCertno(patient.getIdCard());
        }
        // 已建档：回填病案号/状态/上传态，证件号优先主档
        if (caseRecord != null) {
            dto.setCaseRecordId(caseRecord.getId());
            dto.setMedcasno(caseRecord.getMedcasno());
            dto.setStatusCode(caseRecord.getStatusCode());
            dto.setChsUploadStatusCode(caseRecord.getChsUploadStatusCode());
            dto.setSettleUploadStatusCode(caseRecord.getSettleUploadStatusCode());
            String recordCertno = trimToNull(caseRecord.getCertno());
            if (recordCertno != null) {
                dto.setCertno(recordCertno);
            }
            // 主档冗余科室/出院科室有值时补全（就诊上下文无出院科室名）
            if (caseRecord.getDscgCatyName() != null) {
                dto.setDscgCatyName(caseRecord.getDscgCatyName());
            }
            CaseRecordStatus status = caseRecord.getStatusEnum();
            if (status != null) {
                dto.setStatusLabel(status.getDisplay());
            }
        } else {
            // 未建档：状态占位 none，上传态留空
            dto.setStatusCode(MrmsConstants.LIST_STATUS_CODE_NONE);
            dto.setChsUploadStatusCode(null);
            dto.setSettleUploadStatusCode(null);
        }
        // 结算态：对齐护士站/医生站（COMPLETED=已结算，DISCHARGED=已离院亦已结过）
        dto.setSettledCode(this.resolveSettledCode(context.getStatusEnum()));
        // 医嘱上传仍无可靠数据源，保持未上传占位
        dto.setOrderUploadStatusCode(null);
        return dto;
    }

    /**
     * 就诊 status_enum → 列表 settledCode（1=已结算，0=未结算）。
     *
     * @param statusEnum 就诊状态 code；可空
     * @return {@code "1"} / {@code "0"}
     */
    private String resolveSettledCode(String statusEnum) {
        String code = trimToNull(statusEnum);
        if (code == null) {
            return MrmsConstants.LIST_FLAG_NO;
        }
        // 已结算待清床
        if (EncounterStatus.COMPLETED.getCode().equals(code)) {
            return MrmsConstants.LIST_FLAG_YES;
        }
        // 清床离院：结算完成后的终态，仍视为已结算
        if (EncounterStatus.DISCHARGED.getCode().equals(code)) {
            return MrmsConstants.LIST_FLAG_YES;
        }
        return MrmsConstants.LIST_FLAG_NO;
    }
}


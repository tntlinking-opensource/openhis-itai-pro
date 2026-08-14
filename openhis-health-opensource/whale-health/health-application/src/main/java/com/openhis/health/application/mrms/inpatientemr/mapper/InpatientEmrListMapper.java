/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.inpatientemr.mapper;

import com.openhis.health.application.mrms.inpatientemr.dto.InpatientEmrListItemDto;
import com.openhis.health.application.shared.inpatient.AdmissionPrimaryDiagnosisAssembler;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.share.constants.MrmsConstants;
import com.openhis.health.domain.share.enums.CaseRecordStatus;
import com.openhis.health.domain.share.enums.EncounterStatus;
import org.springframework.stereotype.Component;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 住院病例查询列表行映射。
 *
 * <p>就诊上下文为主数据源；主档可空并 left join 回填病案号/状态/上传态。</p>
 */
@Component
public class InpatientEmrListMapper {

    /**
     * 住院就诊上下文 + 可选主档 + 患者证件 → 列表行 DTO。
     *
     * @param context          住院就诊上下文（含诊断快照）
     * @param caseRecord       已建档主档；未建档时 null
     * @param patient          患者实体（取证件号）；可 null
     * @param primaryDiagnosis 主诊断展示（由 {@link AdmissionPrimaryDiagnosisAssembler} 解析）
     * @return 列表行 DTO
     */
    public InpatientEmrListItemDto fromInpatientContext(
            InpatientEncounterContextQueryModel context,
            CaseRecord caseRecord,
            Patient patient,
            AdmissionPrimaryDiagnosisAssembler.PrimaryDiagnosisDisplay primaryDiagnosis) {
        InpatientEmrListItemDto dto = new InpatientEmrListItemDto();
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
            // 主档冗余出院科室有值时补全（就诊上下文无出院科室名）
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

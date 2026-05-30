package com.openhis.whale.mrms.application.assembler;

import com.openhis.whale.mrms.application.dto.CaseRecordDetailDto;
import com.openhis.whale.mrms.application.dto.CaseRecordSummaryDto;
import com.openhis.whale.mrms.domain.entity.CaseRecord;
import org.springframework.stereotype.Component;

@Component
public class CaseRecordAssembler {

    public CaseRecordSummaryDto toSummary(CaseRecord entity) {
        CaseRecordSummaryDto dto = new CaseRecordSummaryDto();
        dto.setId(entity.getId());
        dto.setEncounterId(entity.getEncounterId());
        dto.setPatientId(entity.getPatientId());
        dto.setMedcasno(entity.getMedcasno());
        dto.setStatusCode(entity.getStatusCode());
        dto.setPatientName(entity.getPatientName());
        dto.setGenderCode(entity.getGenderCode());
        dto.setInpatientNo(entity.getInpatientNo());
        dto.setDeptName(entity.getDeptName());
        dto.setAdmissionAt(entity.getAdmissionAt());
        dto.setDischargeAt(entity.getDischargeAt());
        dto.setPrimaryDiagnosisText(entity.getPrimaryDiagnosisText());
        return dto;
    }

    public CaseRecordDetailDto toDetail(CaseRecord entity) {
        CaseRecordDetailDto dto = new CaseRecordDetailDto();
        CaseRecordSummaryDto summary = toSummary(entity);
        dto.setId(summary.getId());
        dto.setEncounterId(summary.getEncounterId());
        dto.setPatientId(summary.getPatientId());
        dto.setMedcasno(summary.getMedcasno());
        dto.setStatusCode(summary.getStatusCode());
        dto.setPatientName(summary.getPatientName());
        dto.setGenderCode(summary.getGenderCode());
        dto.setInpatientNo(summary.getInpatientNo());
        dto.setDeptName(summary.getDeptName());
        dto.setAdmissionAt(summary.getAdmissionAt());
        dto.setDischargeAt(summary.getDischargeAt());
        dto.setPrimaryDiagnosisText(summary.getPrimaryDiagnosisText());
        dto.setPatnIptCnt(entity.getPatnIptCnt());
        dto.setDeptCode(entity.getDeptCode());
        dto.setAttendingPractitionerId(entity.getAttendingPractitionerId());
        dto.setAttendingPractitionerName(entity.getAttendingPractitionerName());
        dto.setReturnReason(entity.getReturnReason());
        dto.setQualityGradeCode(entity.getQualityGradeCode());
        return dto;
    }
}

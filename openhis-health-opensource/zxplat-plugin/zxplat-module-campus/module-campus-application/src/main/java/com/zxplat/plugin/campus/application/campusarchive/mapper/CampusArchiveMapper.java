/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusarchive.mapper;

import com.zxplat.plugin.campus.application.campusarchive.response.ArchiveChangeLogDto;
import com.zxplat.plugin.campus.application.campusarchive.response.ArchiveTransferDto;
import com.zxplat.plugin.campus.application.campusarchive.response.CampusProfileResponse;
import com.zxplat.plugin.campus.application.campusarchive.response.EligibilityCheckResponse;
import com.zxplat.plugin.campus.application.campusarchive.response.OrphanArchiveDto;
import com.zxplat.plugin.campus.application.campusarchive.response.PublicExpenseProfileDto;
import com.zxplat.plugin.campus.domain.archive.view.ArchiveTransferView;
import com.zxplat.plugin.campus.domain.archive.view.CampusProfileView;
import com.zxplat.plugin.campus.domain.archivechangelog.entity.ArchiveChangeLog;
import com.zxplat.plugin.campus.domain.archivetransfer.entity.ArchiveTransfer;
import com.zxplat.plugin.campus.domain.patientstaff.entity.PatientStaff;
import com.zxplat.plugin.campus.domain.patientstudent.entity.PatientStudent;
import com.zxplat.plugin.campus.domain.publicexpenseprofile.entity.PublicExpenseProfile;
import com.zxplat.plugin.campus.domain.publicexpenseprofile.model.EligibilityResult;
import com.zxplat.plugin.campus.domain.publicexpenseprofile.view.EligibilityView;
import com.zxplat.plugin.campus.domain.shared.CampusDomainConstants;
import org.springframework.stereotype.Component;

@Component
public class CampusArchiveMapper {

    public ArchiveChangeLogDto toChangeLogDto(ArchiveChangeLog entity) {
        ArchiveChangeLogDto dto = new ArchiveChangeLogDto();
        dto.setId(entity.getId());
        dto.setSubjectTypeEnum(entity.getSubjectTypeEnum());
        dto.setSubjectId(entity.getSubjectId());
        dto.setChangeTypeEnum(entity.getChangeTypeEnum());
        dto.setFieldName(entity.getFieldName());
        dto.setOldValue(entity.getOldValue());
        dto.setNewValue(entity.getNewValue());
        dto.setOperatorId(entity.getOperatorId());
        dto.setOperatorName(entity.getOperatorName());
        dto.setChangeTime(entity.getChangeTime());
        dto.setChangeSourceEnum(entity.getChangeSourceEnum());
        dto.setCampusCode(entity.getCampusCode());
        dto.setBatchId(entity.getBatchId());
        dto.setRemark(entity.getRemark());
        return dto;
    }

    public ArchiveTransferDto toTransferDto(ArchiveTransfer entity) {
        ArchiveTransferDto dto = new ArchiveTransferDto();
        dto.setId(entity.getId());
        dto.setSubjectTypeEnum(entity.getSubjectTypeEnum());
        dto.setSubjectId(entity.getSubjectId());
        dto.setPatientId(entity.getPatientId());
        dto.setTransferTypeEnum(entity.getTransferTypeEnum());
        dto.setEffectiveDate(entity.getEffectiveDate());
        dto.setFromCampusCode(entity.getFromCampusCode());
        dto.setToCampusCode(entity.getToCampusCode());
        dto.setFromCollegeId(entity.getFromCollegeId());
        dto.setToCollegeId(entity.getToCollegeId());
        dto.setFromDepartmentId(entity.getFromDepartmentId());
        dto.setToDepartmentId(entity.getToDepartmentId());
        dto.setReason(entity.getReason());
        dto.setExternalRef(entity.getExternalRef());
        dto.setTransferSourceEnum(entity.getTransferSourceEnum());
        dto.setOperatorName(entity.getOperatorName());
        dto.setOperateTime(entity.getOperateTime());
        dto.setChangeLogBatchId(entity.getChangeLogBatchId());
        return dto;
    }

    public ArchiveTransferView toTransferView(ArchiveTransfer entity) {
        ArchiveTransferView view = new ArchiveTransferView();
        view.setId(entity.getId());
        view.setTransferType(entity.getTransferTypeEnum());
        view.setEffectiveDate(entity.getEffectiveDate());
        view.setFromCampusCode(entity.getFromCampusCode());
        view.setToCampusCode(entity.getToCampusCode());
        view.setFromCollegeId(entity.getFromCollegeId());
        view.setToCollegeId(entity.getToCollegeId());
        view.setFromDepartmentId(entity.getFromDepartmentId());
        view.setToDepartmentId(entity.getToDepartmentId());
        view.setReason(entity.getReason());
        view.setOperatorName(entity.getOperatorName());
        view.setOperateTime(entity.getOperateTime());
        view.setExternalRef(entity.getExternalRef());
        return view;
    }

    public PublicExpenseProfileDto toPeProfileDto(PublicExpenseProfile entity) {
        PublicExpenseProfileDto dto = new PublicExpenseProfileDto();
        dto.setId(entity.getId());
        dto.setSubjectTypeEnum(entity.getSubjectTypeEnum());
        dto.setSubjectId(entity.getSubjectId());
        dto.setPatientId(entity.getPatientId());
        dto.setCardNo(entity.getCardNo());
        dto.setMedicalCode(entity.getMedicalCode());
        dto.setGfStatusEnum(entity.getGfStatusEnum());
        dto.setCategoryEnum(entity.getCategoryEnum());
        dto.setValidFrom(entity.getValidFrom());
        dto.setValidTo(entity.getValidTo());
        dto.setEligibleFlag(entity.getEligibleFlag());
        dto.setRemark(entity.getRemark());
        return dto;
    }

    public PublicExpenseProfile toPeProfileEntity(
        Long tenantId,
        String subjectTypeEnum,
        Long subjectId,
        Long patientId,
        String cardNo,
        String medicalCode,
        String gfStatusEnum,
        String categoryEnum,
        Boolean eligibleFlag,
        String remark) {
        PublicExpenseProfile profile = new PublicExpenseProfile();
        profile.setTenantId(tenantId);
        profile.setSubjectTypeEnum(subjectTypeEnum);
        profile.setSubjectId(subjectId);
        profile.setPatientId(patientId);
        profile.setCardNo(cardNo);
        profile.setMedicalCode(medicalCode);
        profile.setGfStatusEnum(gfStatusEnum);
        profile.setCategoryEnum(categoryEnum);
        profile.setEligibleFlag(eligibleFlag);
        profile.setRemark(remark);
        return profile;
    }

    public EligibilityCheckResponse toEligibilityResponse(EligibilityResult result) {
        EligibilityCheckResponse response = new EligibilityCheckResponse();
        response.setPassed(result.isPassed());
        response.setReasonCode(result.getReasonCode());
        response.setReasonMessage(result.getReasonMessage());
        response.setGfStatusEnum(result.getGfStatusEnum());
        response.setCardNo(result.getCardNo());
        response.setMedicalCode(result.getMedicalCode());
        response.setCampusCode(result.getCampusCode());
        return response;
    }

    public EligibilityView toEligibilityView(EligibilityResult result) {
        EligibilityView view = new EligibilityView();
        view.setPassed(result.isPassed());
        view.setReasonCode(result.getReasonCode());
        view.setReasonMessage(result.getReasonMessage());
        view.setGfStatusEnum(result.getGfStatusEnum());
        view.setCardNo(result.getCardNo());
        view.setMedicalCode(result.getMedicalCode());
        view.setCampusCode(result.getCampusCode());
        return view;
    }

    public CampusProfileResponse toCampusProfileResponse(CampusProfileView view) {
        CampusProfileResponse response = new CampusProfileResponse();
        response.setSubjectType(view.getSubjectType());
        response.setSubjectId(view.getSubjectId());
        response.setCardNo(view.getCardNo());
        response.setGfStatusEnum(view.getGfStatusEnum());
        response.setMedicalCode(view.getMedicalCode());
        response.setCampusCode(view.getCampusCode());
        response.setDepartmentId(view.getDepartmentId());
        response.setCollegeId(view.getCollegeId());
        return response;
    }

    public OrphanArchiveDto toOrphanDto(PatientStudent student) {
        OrphanArchiveDto dto = new OrphanArchiveDto();
        dto.setSubjectTypeEnum(CampusDomainConstants.SUBJECT_STUDENT);
        dto.setSubjectId(student.getId());
        dto.setName(student.getName());
        dto.setIdCard(student.getIdCard());
        dto.setStudentNo(student.getStudentNo());
        dto.setCampusCode(student.getCampusCode());
        dto.setCollegeId(student.getCollegeId());
        return dto;
    }

    public OrphanArchiveDto toOrphanDto(PatientStaff staff, String campusCode) {
        OrphanArchiveDto dto = new OrphanArchiveDto();
        dto.setSubjectTypeEnum(CampusDomainConstants.SUBJECT_STAFF);
        dto.setSubjectId(staff.getId());
        dto.setName(staff.getName());
        dto.setIdCard(staff.getIdCard());
        dto.setEmployeeNo(staff.getEmployeeNo());
        dto.setCampusCode(campusCode);
        dto.setDepartmentId(staff.getDepartmentId());
        return dto;
    }
}

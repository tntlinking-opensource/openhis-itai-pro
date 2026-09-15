/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusstudent;

import com.openhis.campus.application.campusarchive.mapper.CampusArchiveMapper;
import com.openhis.campus.application.campusarchive.response.ListChangeBatchResponse;
import com.openhis.campus.application.campusstudent.mapper.CampusStudentMapper;
import com.openhis.campus.application.campusstudent.request.BindStudentPatientRequest;
import com.openhis.campus.application.campusstudent.request.CreateStudentRequest;
import com.openhis.campus.application.campusstudent.request.DisableStudentRequest;
import com.openhis.campus.application.campusstudent.request.GetStudentDetailRequest;
import com.openhis.campus.application.campusstudent.request.ListChangeBatchRequest;
import com.openhis.campus.application.campusstudent.request.PageStudentChangesRequest;
import com.openhis.campus.application.campusstudent.request.PageStudentTransfersRequest;
import com.openhis.campus.application.campusstudent.request.PageStudentsRequest;
import com.openhis.campus.application.campusstudent.request.ResolveStudentCampusRequest;
import com.openhis.campus.application.campusstudent.request.SaveStudentPeProfileRequest;
import com.openhis.campus.application.campusstudent.request.TransferStudentRequest;
import com.openhis.campus.application.campusstudent.request.UpdateStudentRequest;
import com.openhis.campus.application.campusstudent.response.CampusStudentDto;
import com.openhis.campus.application.campusstudent.response.PageStudentChangesResponse;
import com.openhis.campus.application.campusstudent.response.PageStudentTransfersResponse;
import com.openhis.campus.application.campusstudent.response.PageStudentsResponse;
import com.openhis.campus.application.campusstudent.response.SaveCampusStudentResponse;
import com.openhis.campus.application.campusstudent.response.StudentCampusResponse;
import com.openhis.campus.application.campusstudent.response.StudentDetailResponse;
import com.openhis.campus.application.share.CampusArchiveApplicationConstants;
import com.openhis.campus.application.share.CampusArchiveChangeLogSupport;
import com.openhis.campus.application.share.CampusOperatorResolver;
import com.openhis.campus.application.share.CampusPagedResultBuilder;
import com.openhis.campus.domain.archive.CampusArchiveQueryReader;
import com.openhis.campus.domain.archive.view.StudentCampusView;
import com.openhis.campus.domain.archivechangelog.entity.ArchiveChangeLog;
import com.openhis.campus.domain.archivechangelog.service.ArchiveChangeLogQueryService;
import com.openhis.campus.domain.archivetransfer.entity.ArchiveTransfer;
import com.openhis.campus.domain.archivetransfer.service.ArchiveTransferDomainService;
import com.openhis.campus.domain.archivetransfer.service.ArchiveTransferQueryService;
import com.openhis.campus.domain.patientstudent.entity.PatientStudent;
import com.openhis.campus.domain.patientstudent.service.PatientStudentDomainService;
import com.openhis.campus.domain.patientstudent.service.PatientStudentQueryService;
import com.openhis.campus.domain.patientstudent.service.PatientStudentUpdateResult;
import com.openhis.campus.domain.publicexpenseprofile.entity.PublicExpenseProfile;
import com.openhis.campus.domain.publicexpenseprofile.service.PublicExpenseProfileDomainService;
import com.openhis.campus.domain.publicexpenseprofile.service.PublicExpenseProfileQueryService;
import com.openhis.campus.domain.shared.CampusDomainConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 学生档案应用服务。
 *
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/campusStudent/{methodName}}。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class CampusStudentAppService {

    private final PatientStudentQueryService patientStudentQueryService;
    private final PatientStudentDomainService patientStudentDomainService;
    private final PublicExpenseProfileQueryService publicExpenseProfileQueryService;
    private final PublicExpenseProfileDomainService publicExpenseProfileDomainService;
    private final ArchiveChangeLogQueryService archiveChangeLogQueryService;
    private final ArchiveTransferDomainService archiveTransferDomainService;
    private final ArchiveTransferQueryService archiveTransferQueryService;
    private final CampusArchiveQueryReader campusArchiveQueryReader;
    private final CampusStudentMapper campusStudentMapper;
    private final CampusArchiveMapper campusArchiveMapper;
    private final CampusArchiveChangeLogSupport changeLogSupport;
    private final CampusOperatorResolver campusOperatorResolver;

    public PageStudentsResponse pageStudents(@Valid PageStudentsRequest request) {
        Page<PatientStudent> page = patientStudentQueryService.page(
            request.getTenantId(),
            request.getKeyword(),
            request.getCollegeId(),
            request.getCampusCode(),
            request.getSchoolStatus(),
            request.getActiveFlag(),
            request.getClassName(),
            request.getEnrollTimeFrom(),
            request.getEnrollTimeTo(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<CampusStudentDto> rows = new ArrayList<>();
        for (PatientStudent entity : page.getContent()) {
            rows.add(campusStudentMapper.toDto(entity));
        }
        return CampusPagedResultBuilder.build(new PageStudentsResponse(), rows, page);
    }

    @Transactional
    public SaveCampusStudentResponse createStudent(@Valid CreateStudentRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        PatientStudent saved = patientStudentDomainService.create(campusStudentMapper.toEntity(request), operatorId);
        if (hasPeProfileInput(request)) {
            upsertStudentPeProfile(saved, request.getPeCardNo(), request.getPeMedicalCode(),
                request.getPeGfStatusEnum(), request.getPeCategoryEnum(), request.getPeEligibleFlag(),
                request.getPeRemark(), operatorId);
        }
        changeLogSupport.appendCreateBatch(
            saved.getTenantId(),
            CampusDomainConstants.SUBJECT_STUDENT,
            saved.getId(),
            saved.getCampusCode(),
            operatorId);
        SaveCampusStudentResponse response = new SaveCampusStudentResponse();
        response.setId(saved.getId());
        return response;
    }

    @Transactional
    public SaveCampusStudentResponse updateStudent(@Valid UpdateStudentRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        PatientStudent existing = patientStudentQueryService.getById(request.getTenantId(), request.getId());
        PatientStudentUpdateResult result = patientStudentDomainService.updateReturningOrgChange(
            campusStudentMapper.toEntity(request), operatorId);
        PatientStudent saved = result.getStudent();
        List<CampusArchiveChangeLogSupport.FieldChange> changes = buildStudentFieldChanges(existing, saved);
        String batchId = null;
        if (!changes.isEmpty()) {
            List<ArchiveChangeLog> logs = changeLogSupport.appendFieldUpdates(
                saved.getTenantId(),
                CampusDomainConstants.SUBJECT_STUDENT,
                saved.getId(),
                saved.getCampusCode(),
                CampusArchiveApplicationConstants.CHANGE_SOURCE_MANUAL,
                changes,
                operatorId);
            batchId = logs.isEmpty() ? null : logs.get(0).getBatchId();
        }
        if (result.isOrgChanged()) {
            recordDerivedStudentTransfer(existing, saved, batchId, operatorId);
        }
        SaveCampusStudentResponse response = new SaveCampusStudentResponse();
        response.setId(saved.getId());
        return response;
    }

    public StudentDetailResponse getStudentDetail(@Valid GetStudentDetailRequest request) {
        PatientStudent student = patientStudentQueryService.getById(request.getTenantId(), request.getId());
        StudentDetailResponse response = new StudentDetailResponse();
        response.setStudent(campusStudentMapper.toDto(student));
        publicExpenseProfileQueryService.getBySubject(
                student.getTenantId(), CampusDomainConstants.SUBJECT_STUDENT, student.getId())
            .ifPresent(profile -> response.setPeProfile(campusArchiveMapper.toPeProfileDto(profile)));
        return response;
    }

    @Transactional
    public SaveCampusStudentResponse disableStudent(@Valid DisableStudentRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        PatientStudent saved = patientStudentDomainService.disable(
            request.getTenantId(), request.getId(), operatorId);
        SaveCampusStudentResponse response = new SaveCampusStudentResponse();
        response.setId(saved.getId());
        return response;
    }

    @Transactional
    public SaveCampusStudentResponse bindStudentPatient(@Valid BindStudentPatientRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        PatientStudent saved = patientStudentDomainService.bindPatient(
            request.getTenantId(), request.getStudentId(), request.getPatientId(), operatorId);
        SaveCampusStudentResponse response = new SaveCampusStudentResponse();
        response.setId(saved.getId());
        return response;
    }

    @Transactional
    public SaveCampusStudentResponse saveStudentPeProfile(@Valid SaveStudentPeProfileRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        PatientStudent student = patientStudentQueryService.getById(request.getTenantId(), request.getStudentId());
        upsertStudentPeProfile(student, request.getCardNo(), request.getMedicalCode(), request.getGfStatusEnum(),
            request.getCategoryEnum(), request.getEligibleFlag(), request.getRemark(), operatorId);
        SaveCampusStudentResponse response = new SaveCampusStudentResponse();
        response.setId(student.getId());
        return response;
    }

    public StudentCampusResponse resolveStudentCampus(@Valid ResolveStudentCampusRequest request) {
        Optional<StudentCampusView> view = Optional.empty();
        if (request.getPatientId() != null) {
            view = campusArchiveQueryReader.resolveStudentCampus(request.getTenantId(), request.getPatientId());
        } else if (request.getStudentNo() != null && !request.getStudentNo().isBlank()) {
            view = campusArchiveQueryReader.resolveStudentCampusByStudentNo(
                request.getTenantId(), request.getStudentNo());
        }
        return view.map(campusStudentMapper::toCampusResponse)
            .orElseThrow(() -> new BusinessException("STUDENT_NOT_FOUND", "学生档案不存在"));
    }

    public PageStudentChangesResponse pageStudentChanges(@Valid PageStudentChangesRequest request) {
        Page<ArchiveChangeLog> page = archiveChangeLogQueryService.pageBySubject(
            request.getTenantId(),
            CampusDomainConstants.SUBJECT_STUDENT,
            request.getStudentId(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<com.openhis.campus.application.campusarchive.response.ArchiveChangeLogDto> rows = new ArrayList<>();
        for (ArchiveChangeLog entity : page.getContent()) {
            rows.add(campusArchiveMapper.toChangeLogDto(entity));
        }
        return CampusPagedResultBuilder.build(new PageStudentChangesResponse(), rows, page);
    }

    public ListChangeBatchResponse listChangeBatch(@Valid ListChangeBatchRequest request) {
        ListChangeBatchResponse response = new ListChangeBatchResponse();
        response.setBatchId(request.getBatchId());
        for (ArchiveChangeLog entity : archiveChangeLogQueryService.listByBatchId(
            request.getTenantId(), request.getBatchId())) {
            response.getRecords().add(campusArchiveMapper.toChangeLogDto(entity));
        }
        return response;
    }

    @Transactional
    public SaveCampusStudentResponse transferStudent(@Valid TransferStudentRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        PatientStudent existing = patientStudentQueryService.getById(request.getTenantId(), request.getStudentId());
        UpdateStudentRequest updateRequest = new UpdateStudentRequest();
        updateRequest.setTenantId(existing.getTenantId());
        updateRequest.setId(existing.getId());
        updateRequest.setName(existing.getName());
        updateRequest.setStudentNo(existing.getStudentNo());
        updateRequest.setCollegeId(request.getToCollegeId());
        updateRequest.setCampusCode(request.getToCampusCode());
        updateRequest.setPyStr(existing.getPyStr());
        updateRequest.setWbStr(existing.getWbStr());
        updateRequest.setGenderEnum(existing.getGenderEnum());
        updateRequest.setAge(existing.getAge());
        updateRequest.setBirthday(existing.getBirthday());
        updateRequest.setIdCard(existing.getIdCard());
        updateRequest.setPhone(existing.getPhone());
        updateRequest.setMajor(existing.getMajor());
        updateRequest.setMajorCode(existing.getMajorCode());
        updateRequest.setEduLevelEnum(existing.getEduLevelEnum());
        updateRequest.setEnrollTime(existing.getEnrollTime());
        updateRequest.setLeaveTime(existing.getLeaveTime());
        updateRequest.setGrade(existing.getGrade());
        updateRequest.setSchoolYear(existing.getSchoolYear());
        updateRequest.setStudyTypeEnum(existing.getStudyTypeEnum());
        updateRequest.setSchoolStatusEnum(existing.getSchoolStatusEnum());
        updateRequest.setPhysicalExamResultEnum(existing.getPhysicalExamResultEnum());
        updateRequest.setNativePlace(existing.getNativePlace());
        updateRequest.setCounselorName(existing.getCounselorName());
        updateRequest.setCounselorPhone(existing.getCounselorPhone());
        updateRequest.setAddress(existing.getAddress());
        updateRequest.setDoorplate(existing.getDoorplate());
        updateRequest.setActiveFlag(existing.getActiveFlag());

        PatientStudent saved = patientStudentDomainService.updateReturningOrgChange(
            campusStudentMapper.toEntity(updateRequest), operatorId).getStudent();
        List<CampusArchiveChangeLogSupport.FieldChange> changes = CampusArchiveChangeLogSupport.collect(
            CampusArchiveChangeLogSupport.diffLong("collegeId", existing.getCollegeId(), saved.getCollegeId()),
            CampusArchiveChangeLogSupport.diffString("campusCode", existing.getCampusCode(), saved.getCampusCode()));
        List<ArchiveChangeLog> logs = changeLogSupport.appendFieldUpdates(
            saved.getTenantId(),
            CampusDomainConstants.SUBJECT_STUDENT,
            saved.getId(),
            saved.getCampusCode(),
            CampusArchiveApplicationConstants.CHANGE_SOURCE_MANUAL,
            changes,
            operatorId);
        String batchId = logs.isEmpty() ? null : logs.get(0).getBatchId();

        ArchiveTransfer transfer = new ArchiveTransfer();
        transfer.setTenantId(saved.getTenantId());
        transfer.setSubjectId(saved.getId());
        transfer.setPatientId(saved.getPatientId());
        transfer.setTransferTypeEnum(CampusArchiveChangeLogSupport.resolveStudentTransferType(
            existing.getCampusCode(), saved.getCampusCode(), existing.getCollegeId(), saved.getCollegeId()));
        transfer.setEffectiveDate(request.getEffectiveDate());
        transfer.setFromCampusCode(existing.getCampusCode());
        transfer.setToCampusCode(saved.getCampusCode());
        transfer.setFromCollegeId(existing.getCollegeId());
        transfer.setToCollegeId(saved.getCollegeId());
        transfer.setReason(request.getReason());
        transfer.setExternalRef(request.getExternalRef());
        transfer.setTransferSourceEnum(CampusArchiveApplicationConstants.TRANSFER_SOURCE_MANUAL);
        transfer.setChangeLogBatchId(batchId);
        archiveTransferDomainService.recordStudentTransfer(transfer, operatorId);

        SaveCampusStudentResponse response = new SaveCampusStudentResponse();
        response.setId(saved.getId());
        return response;
    }

    public PageStudentTransfersResponse pageStudentTransfers(@Valid PageStudentTransfersRequest request) {
        Page<ArchiveTransfer> page = archiveTransferQueryService.pageBySubject(
            request.getTenantId(),
            CampusDomainConstants.SUBJECT_STUDENT,
            request.getStudentId(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<com.openhis.campus.application.campusarchive.response.ArchiveTransferDto> rows = new ArrayList<>();
        for (ArchiveTransfer entity : page.getContent()) {
            rows.add(campusArchiveMapper.toTransferDto(entity));
        }
        return CampusPagedResultBuilder.build(new PageStudentTransfersResponse(), rows, page);
    }

    private void upsertStudentPeProfile(
        PatientStudent student,
        String cardNo,
        String medicalCode,
        String gfStatusEnum,
        String categoryEnum,
        Boolean eligibleFlag,
        String remark,
        Long operatorId) {
        PublicExpenseProfile profile = campusArchiveMapper.toPeProfileEntity(
            student.getTenantId(),
            CampusDomainConstants.SUBJECT_STUDENT,
            student.getId(),
            student.getPatientId(),
            cardNo,
            medicalCode,
            gfStatusEnum,
            categoryEnum,
            eligibleFlag,
            remark);
        publicExpenseProfileDomainService.upsert(profile, operatorId);
    }

    private boolean hasPeProfileInput(CreateStudentRequest request) {
        return request.getPeCardNo() != null || request.getPeMedicalCode() != null
            || request.getPeGfStatusEnum() != null || request.getPeCategoryEnum() != null
            || request.getPeEligibleFlag() != null || request.getPeRemark() != null;
    }

    private void recordDerivedStudentTransfer(
        PatientStudent before,
        PatientStudent after,
        String changeLogBatchId,
        Long operatorId) {
        ArchiveTransfer transfer = new ArchiveTransfer();
        transfer.setTenantId(after.getTenantId());
        transfer.setSubjectId(after.getId());
        transfer.setPatientId(after.getPatientId());
        transfer.setTransferTypeEnum(CampusArchiveChangeLogSupport.resolveStudentTransferType(
            before.getCampusCode(), after.getCampusCode(), before.getCollegeId(), after.getCollegeId()));
        transfer.setFromCampusCode(before.getCampusCode());
        transfer.setToCampusCode(after.getCampusCode());
        transfer.setFromCollegeId(before.getCollegeId());
        transfer.setToCollegeId(after.getCollegeId());
        transfer.setTransferSourceEnum(CampusDomainConstants.TRANSFER_SOURCE_UPDATE_DERIVED);
        transfer.setChangeLogBatchId(changeLogBatchId);
        archiveTransferDomainService.recordDerivedFromUpdate(transfer, operatorId);
    }

    private List<CampusArchiveChangeLogSupport.FieldChange> buildStudentFieldChanges(
        PatientStudent before,
        PatientStudent after) {
        return CampusArchiveChangeLogSupport.collect(
            CampusArchiveChangeLogSupport.diffString("name", before.getName(), after.getName()),
            CampusArchiveChangeLogSupport.diffString("studentNo", before.getStudentNo(), after.getStudentNo()),
            CampusArchiveChangeLogSupport.diffLong("collegeId", before.getCollegeId(), after.getCollegeId()),
            CampusArchiveChangeLogSupport.diffString("campusCode", before.getCampusCode(), after.getCampusCode()),
            CampusArchiveChangeLogSupport.diffString("idCard", before.getIdCard(), after.getIdCard()),
            CampusArchiveChangeLogSupport.diffString("phone", before.getPhone(), after.getPhone()),
            CampusArchiveChangeLogSupport.diffString("schoolStatusEnum", before.getSchoolStatusEnum(), after.getSchoolStatusEnum()),
            CampusArchiveChangeLogSupport.diffString("physicalExamResultEnum", before.getPhysicalExamResultEnum(), after.getPhysicalExamResultEnum()),
            CampusArchiveChangeLogSupport.diffString("major", before.getMajor(), after.getMajor()));
    }
}

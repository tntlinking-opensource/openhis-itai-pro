/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusstaff;

import com.openhis.campus.application.campusarchive.mapper.CampusArchiveMapper;
import com.openhis.campus.application.campusstaff.mapper.CampusStaffMapper;
import com.openhis.campus.application.campusstaff.request.ImportStaffBatchRequest;
import com.openhis.campus.application.campusstaff.response.ImportStaffBatchResponse;
import com.openhis.campus.application.campusstaff.request.BindStaffPatientRequest;
import com.openhis.campus.application.campusstaff.request.CreateStaffRequest;
import com.openhis.campus.application.campusstaff.request.DisableStaffRequest;
import com.openhis.campus.application.campusstaff.request.GetStaffDetailRequest;
import com.openhis.campus.application.campusstaff.request.PageStaffChangesRequest;
import com.openhis.campus.application.campusstaff.request.PageStaffRequest;
import com.openhis.campus.application.campusstaff.request.PageStaffTransfersRequest;
import com.openhis.campus.application.campusstaff.request.SaveStaffPeProfileRequest;
import com.openhis.campus.application.campusstaff.request.TransferStaffRequest;
import com.openhis.campus.application.campusstaff.request.UpdateStaffRequest;
import com.openhis.campus.application.campusstaff.response.CampusStaffDto;
import com.openhis.campus.application.campusstaff.response.PageStaffChangesResponse;
import com.openhis.campus.application.campusstaff.response.PageStaffResponse;
import com.openhis.campus.application.campusstaff.response.PageStaffTransfersResponse;
import com.openhis.campus.application.campusstaff.response.SaveCampusStaffResponse;
import com.openhis.campus.application.campusstaff.response.StaffDetailResponse;
import com.openhis.campus.application.share.CampusArchiveApplicationConstants;
import com.openhis.campus.application.share.CampusArchiveChangeLogSupport;
import com.openhis.campus.application.share.CampusOperatorResolver;
import com.openhis.campus.application.share.CampusPagedResultBuilder;
import com.openhis.campus.domain.archivechangelog.entity.ArchiveChangeLog;
import com.openhis.campus.domain.archivechangelog.service.ArchiveChangeLogQueryService;
import com.openhis.campus.domain.archivetransfer.entity.ArchiveTransfer;
import com.openhis.campus.domain.archivetransfer.service.ArchiveTransferDomainService;
import com.openhis.campus.domain.archivetransfer.service.ArchiveTransferQueryService;
import com.openhis.campus.domain.campussite.entity.CampusSite;
import com.openhis.campus.domain.campussite.repository.CampusSiteRepository;
import com.openhis.campus.domain.patientstaff.entity.PatientStaff;
import com.openhis.campus.domain.patientstaff.service.PatientStaffDomainService;
import com.openhis.campus.domain.patientstaff.service.PatientStaffQueryService;
import com.openhis.campus.domain.publicexpenseprofile.entity.PublicExpenseProfile;
import com.openhis.campus.domain.publicexpenseprofile.service.PublicExpenseProfileDomainService;
import com.openhis.campus.domain.publicexpenseprofile.service.PublicExpenseProfileQueryService;
import com.openhis.campus.domain.shared.CampusDomainConstants;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 教职工档案应用服务。
 *
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/campusStaff/{methodName}}。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class CampusStaffAppService {

    private final PatientStaffQueryService patientStaffQueryService;
    private final PatientStaffDomainService patientStaffDomainService;
    private final PublicExpenseProfileQueryService publicExpenseProfileQueryService;
    private final PublicExpenseProfileDomainService publicExpenseProfileDomainService;
    private final ArchiveChangeLogQueryService archiveChangeLogQueryService;
    private final ArchiveTransferDomainService archiveTransferDomainService;
    private final ArchiveTransferQueryService archiveTransferQueryService;
    private final CampusSiteRepository campusSiteRepository;
    private final CampusStaffMapper campusStaffMapper;
    private final CampusArchiveMapper campusArchiveMapper;
    private final CampusArchiveChangeLogSupport changeLogSupport;
    private final CampusOperatorResolver campusOperatorResolver;

    public PageStaffResponse pageStaff(@Valid PageStaffRequest request) {
        Page<PatientStaff> page = patientStaffQueryService.page(
            request.getTenantId(),
            request.getKeyword(),
            request.getDepartmentId(),
            request.getCampusSiteId(),
            request.getJobStatus(),
            request.getActiveFlag(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<CampusStaffDto> rows = new ArrayList<>();
        for (PatientStaff entity : page.getContent()) {
            rows.add(campusStaffMapper.toDto(entity));
        }
        return CampusPagedResultBuilder.build(new PageStaffResponse(), rows, page);
    }

    @Transactional
    public SaveCampusStaffResponse createStaff(@Valid CreateStaffRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        PatientStaff saved = patientStaffDomainService.create(campusStaffMapper.toEntity(request), operatorId);
        if (hasPeProfileInput(request)) {
            upsertStaffPeProfile(saved, request.getPeCardNo(), request.getPeMedicalCode(),
                request.getPeGfStatusEnum(), request.getPeCategoryEnum(), request.getPeEligibleFlag(),
                request.getPeRemark(), operatorId);
        }
        changeLogSupport.appendCreateBatch(
            saved.getTenantId(),
            CampusDomainConstants.SUBJECT_STAFF,
            saved.getId(),
            resolveCampusCode(saved.getTenantId(), saved.getCampusSiteId()),
            operatorId);
        SaveCampusStaffResponse response = new SaveCampusStaffResponse();
        response.setId(saved.getId());
        return response;
    }

    @Transactional
    public SaveCampusStaffResponse updateStaff(@Valid UpdateStaffRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        PatientStaff existing = patientStaffQueryService.getById(request.getTenantId(), request.getId());
        PatientStaff saved = patientStaffDomainService.update(campusStaffMapper.toEntity(request), operatorId);
        boolean orgChanged = !Objects.equals(existing.getDepartmentId(), saved.getDepartmentId())
            || !Objects.equals(existing.getCampusSiteId(), saved.getCampusSiteId());
        List<CampusArchiveChangeLogSupport.FieldChange> changes = buildStaffFieldChanges(existing, saved);
        String batchId = null;
        if (!changes.isEmpty()) {
            List<ArchiveChangeLog> logs = changeLogSupport.appendFieldUpdates(
                saved.getTenantId(),
                CampusDomainConstants.SUBJECT_STAFF,
                saved.getId(),
                resolveCampusCode(saved.getTenantId(), saved.getCampusSiteId()),
                CampusArchiveApplicationConstants.CHANGE_SOURCE_MANUAL,
                changes,
                operatorId);
            batchId = logs.isEmpty() ? null : logs.get(0).getBatchId();
        }
        if (orgChanged) {
            recordDerivedStaffTransfer(existing, saved, batchId, operatorId);
        }
        SaveCampusStaffResponse response = new SaveCampusStaffResponse();
        response.setId(saved.getId());
        return response;
    }

    public StaffDetailResponse getStaffDetail(@Valid GetStaffDetailRequest request) {
        PatientStaff staff = patientStaffQueryService.getById(request.getTenantId(), request.getId());
        StaffDetailResponse response = new StaffDetailResponse();
        response.setStaff(campusStaffMapper.toDto(staff));
        publicExpenseProfileQueryService.getBySubject(
                staff.getTenantId(), CampusDomainConstants.SUBJECT_STAFF, staff.getId())
            .ifPresent(profile -> response.setPeProfile(campusArchiveMapper.toPeProfileDto(profile)));
        return response;
    }

    @Transactional
    public SaveCampusStaffResponse disableStaff(@Valid DisableStaffRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        PatientStaff saved = patientStaffDomainService.disable(request.getTenantId(), request.getId(), operatorId);
        SaveCampusStaffResponse response = new SaveCampusStaffResponse();
        response.setId(saved.getId());
        return response;
    }

    @Transactional
    public SaveCampusStaffResponse bindStaffPatient(@Valid BindStaffPatientRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        PatientStaff saved = patientStaffDomainService.bindPatient(
            request.getTenantId(), request.getStaffId(), request.getPatientId(), operatorId);
        SaveCampusStaffResponse response = new SaveCampusStaffResponse();
        response.setId(saved.getId());
        return response;
    }

    @Transactional
    public SaveCampusStaffResponse saveStaffPeProfile(@Valid SaveStaffPeProfileRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        PatientStaff staff = patientStaffQueryService.getById(request.getTenantId(), request.getStaffId());
        upsertStaffPeProfile(staff, request.getCardNo(), request.getMedicalCode(), request.getGfStatusEnum(),
            request.getCategoryEnum(), request.getEligibleFlag(), request.getRemark(), operatorId);
        SaveCampusStaffResponse response = new SaveCampusStaffResponse();
        response.setId(staff.getId());
        return response;
    }

    public PageStaffChangesResponse pageStaffChanges(@Valid PageStaffChangesRequest request) {
        Page<ArchiveChangeLog> page = archiveChangeLogQueryService.pageBySubject(
            request.getTenantId(),
            CampusDomainConstants.SUBJECT_STAFF,
            request.getStaffId(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<com.openhis.campus.application.campusarchive.response.ArchiveChangeLogDto> rows = new ArrayList<>();
        for (ArchiveChangeLog entity : page.getContent()) {
            rows.add(campusArchiveMapper.toChangeLogDto(entity));
        }
        return CampusPagedResultBuilder.build(new PageStaffChangesResponse(), rows, page);
    }

    @Transactional
    public SaveCampusStaffResponse transferStaff(@Valid TransferStaffRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        PatientStaff existing = patientStaffQueryService.getById(request.getTenantId(), request.getStaffId());
        UpdateStaffRequest updateRequest = new UpdateStaffRequest();
        updateRequest.setTenantId(existing.getTenantId());
        updateRequest.setId(existing.getId());
        updateRequest.setName(existing.getName());
        updateRequest.setEmployeeNo(existing.getEmployeeNo());
        updateRequest.setDepartmentId(request.getToDepartmentId());
        updateRequest.setCampusSiteId(request.getToCampusSiteId());
        updateRequest.setPyStr(existing.getPyStr());
        updateRequest.setWbStr(existing.getWbStr());
        updateRequest.setGenderEnum(existing.getGenderEnum());
        updateRequest.setIdCard(existing.getIdCard());
        updateRequest.setPhone(existing.getPhone());
        updateRequest.setMedicalCode(existing.getMedicalCode());
        updateRequest.setStaffTypeEnum(existing.getStaffTypeEnum());
        updateRequest.setValidFrom(existing.getValidFrom());
        updateRequest.setValidTo(existing.getValidTo());
        updateRequest.setJobStatusEnum(existing.getJobStatusEnum());
        updateRequest.setActiveFlag(existing.getActiveFlag());

        PatientStaff saved = patientStaffDomainService.update(campusStaffMapper.toEntity(updateRequest), operatorId);
        List<CampusArchiveChangeLogSupport.FieldChange> changes = CampusArchiveChangeLogSupport.collect(
            CampusArchiveChangeLogSupport.diffLong("departmentId", existing.getDepartmentId(), saved.getDepartmentId()),
            CampusArchiveChangeLogSupport.diffLong("campusSiteId", existing.getCampusSiteId(), saved.getCampusSiteId()));
        List<ArchiveChangeLog> logs = changeLogSupport.appendFieldUpdates(
            saved.getTenantId(),
            CampusDomainConstants.SUBJECT_STAFF,
            saved.getId(),
            resolveCampusCode(saved.getTenantId(), saved.getCampusSiteId()),
            CampusArchiveApplicationConstants.CHANGE_SOURCE_MANUAL,
            changes,
            operatorId);
        String batchId = logs.isEmpty() ? null : logs.get(0).getBatchId();

        ArchiveTransfer transfer = new ArchiveTransfer();
        transfer.setTenantId(saved.getTenantId());
        transfer.setSubjectId(saved.getId());
        transfer.setPatientId(saved.getPatientId());
        transfer.setTransferTypeEnum(CampusArchiveChangeLogSupport.resolveStaffTransferType(
            existing.getCampusSiteId(), saved.getCampusSiteId(), existing.getDepartmentId(), saved.getDepartmentId()));
        transfer.setEffectiveDate(request.getEffectiveDate());
        transfer.setFromCampusCode(resolveCampusCode(existing.getTenantId(), existing.getCampusSiteId()));
        transfer.setToCampusCode(resolveCampusCode(saved.getTenantId(), saved.getCampusSiteId()));
        transfer.setFromDepartmentId(existing.getDepartmentId());
        transfer.setToDepartmentId(saved.getDepartmentId());
        transfer.setReason(request.getReason());
        transfer.setExternalRef(request.getExternalRef());
        transfer.setTransferSourceEnum(CampusArchiveApplicationConstants.TRANSFER_SOURCE_MANUAL);
        transfer.setChangeLogBatchId(batchId);
        archiveTransferDomainService.recordStaffTransfer(transfer, operatorId);

        SaveCampusStaffResponse response = new SaveCampusStaffResponse();
        response.setId(saved.getId());
        return response;
    }

    public PageStaffTransfersResponse pageStaffTransfers(@Valid PageStaffTransfersRequest request) {
        Page<ArchiveTransfer> page = archiveTransferQueryService.pageBySubject(
            request.getTenantId(),
            CampusDomainConstants.SUBJECT_STAFF,
            request.getStaffId(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<com.openhis.campus.application.campusarchive.response.ArchiveTransferDto> rows = new ArrayList<>();
        for (ArchiveTransfer entity : page.getContent()) {
            rows.add(campusArchiveMapper.toTransferDto(entity));
        }
        return CampusPagedResultBuilder.build(new PageStaffTransfersResponse(), rows, page);
    }

    /**
     * 教职工批量导入（IF-CMP-STF-14）：按工号幂等，已存在则跳过。
     */
    @Transactional
    public ImportStaffBatchResponse importStaffBatch(@Valid ImportStaffBatchRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        ImportStaffBatchResponse response = new ImportStaffBatchResponse();
        int rowNo = 0;
        for (ImportStaffBatchRequest.ImportStaffRow row : request.getRows()) {
            rowNo++;
            try {
                if (row.getName() == null || row.getName().isBlank()
                    || row.getEmployeeNo() == null || row.getEmployeeNo().isBlank()) {
                    response.setFailCount(response.getFailCount() + 1);
                    response.getErrors().add("第" + rowNo + "行：姓名与工号不能为空");
                    continue;
                }
                if (patientStaffQueryService.findByEmployeeNo(request.getTenantId(), row.getEmployeeNo()).isPresent()) {
                    response.setSkipCount(response.getSkipCount() + 1);
                    continue;
                }
                CreateStaffRequest create = new CreateStaffRequest();
                create.setTenantId(request.getTenantId());
                create.setName(row.getName().trim());
                create.setEmployeeNo(row.getEmployeeNo().trim());
                create.setIdCard(row.getIdCard());
                create.setPhone(row.getPhone());
                create.setGenderEnum(row.getGenderEnum());
                create.setMedicalCode(row.getMedicalCode());
                create.setPatientId(row.getPatientId());
                create.setDepartmentId(
                    row.getDepartmentId() != null ? row.getDepartmentId() : request.getDefaultDepartmentId());
                create.setCampusSiteId(
                    row.getCampusSiteId() != null ? row.getCampusSiteId() : request.getDefaultCampusSiteId());
                PatientStaff saved = patientStaffDomainService.create(campusStaffMapper.toEntity(create), operatorId);
                changeLogSupport.appendCreateBatch(
                    saved.getTenantId(),
                    CampusDomainConstants.SUBJECT_STAFF,
                    saved.getId(),
                    resolveCampusCode(saved.getTenantId(), saved.getCampusSiteId()),
                    operatorId);
                response.setSuccessCount(response.getSuccessCount() + 1);
                response.getStaffIds().add(saved.getId());
            } catch (Exception ex) {
                response.setFailCount(response.getFailCount() + 1);
                response.getErrors().add("第" + rowNo + "行：" + ex.getMessage());
            }
        }
        return response;
    }

    private void upsertStaffPeProfile(
        PatientStaff staff,
        String cardNo,
        String medicalCode,
        String gfStatusEnum,
        String categoryEnum,
        Boolean eligibleFlag,
        String remark,
        Long operatorId) {
        PublicExpenseProfile profile = campusArchiveMapper.toPeProfileEntity(
            staff.getTenantId(),
            CampusDomainConstants.SUBJECT_STAFF,
            staff.getId(),
            staff.getPatientId(),
            cardNo,
            medicalCode,
            gfStatusEnum,
            categoryEnum,
            eligibleFlag,
            remark);
        publicExpenseProfileDomainService.upsert(profile, operatorId);
    }

    private boolean hasPeProfileInput(CreateStaffRequest request) {
        return request.getPeCardNo() != null || request.getPeMedicalCode() != null
            || request.getPeGfStatusEnum() != null || request.getPeCategoryEnum() != null
            || request.getPeEligibleFlag() != null || request.getPeRemark() != null;
    }

    private void recordDerivedStaffTransfer(
        PatientStaff before,
        PatientStaff after,
        String changeLogBatchId,
        Long operatorId) {
        ArchiveTransfer transfer = new ArchiveTransfer();
        transfer.setTenantId(after.getTenantId());
        transfer.setSubjectId(after.getId());
        transfer.setPatientId(after.getPatientId());
        transfer.setTransferTypeEnum(CampusArchiveChangeLogSupport.resolveStaffTransferType(
            before.getCampusSiteId(), after.getCampusSiteId(), before.getDepartmentId(), after.getDepartmentId()));
        transfer.setFromCampusCode(resolveCampusCode(before.getTenantId(), before.getCampusSiteId()));
        transfer.setToCampusCode(resolveCampusCode(after.getTenantId(), after.getCampusSiteId()));
        transfer.setFromDepartmentId(before.getDepartmentId());
        transfer.setToDepartmentId(after.getDepartmentId());
        transfer.setTransferSourceEnum(CampusDomainConstants.TRANSFER_SOURCE_UPDATE_DERIVED);
        transfer.setChangeLogBatchId(changeLogBatchId);
        archiveTransferDomainService.recordDerivedFromUpdate(transfer, operatorId);
    }

    private List<CampusArchiveChangeLogSupport.FieldChange> buildStaffFieldChanges(
        PatientStaff before,
        PatientStaff after) {
        return CampusArchiveChangeLogSupport.collect(
            CampusArchiveChangeLogSupport.diffString("name", before.getName(), after.getName()),
            CampusArchiveChangeLogSupport.diffString("employeeNo", before.getEmployeeNo(), after.getEmployeeNo()),
            CampusArchiveChangeLogSupport.diffLong("departmentId", before.getDepartmentId(), after.getDepartmentId()),
            CampusArchiveChangeLogSupport.diffLong("campusSiteId", before.getCampusSiteId(), after.getCampusSiteId()),
            CampusArchiveChangeLogSupport.diffString("medicalCode", before.getMedicalCode(), after.getMedicalCode()),
            CampusArchiveChangeLogSupport.diffString("jobStatusEnum", before.getJobStatusEnum(), after.getJobStatusEnum()),
            CampusArchiveChangeLogSupport.diffString("idCard", before.getIdCard(), after.getIdCard()),
            CampusArchiveChangeLogSupport.diffString("phone", before.getPhone(), after.getPhone()));
    }

    private String resolveCampusCode(Long tenantId, Long campusSiteId) {
        if (campusSiteId == null) {
            return null;
        }
        Long resolvedTenantId = tenantId != null ? tenantId : TenantUtils.getCurrentTenantId();
        return campusSiteRepository.findByIdAndTenantIdAndIsDeletedFalse(campusSiteId, resolvedTenantId)
            .map(CampusSite::getCampusCode)
            .orElse(null);
    }
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusarchive;

import com.openhis.campus.application.campusarchive.mapper.CampusArchiveMapper;
import com.openhis.campus.application.campusarchive.request.BindArchiveByIdCardRequest;
import com.openhis.campus.application.campusarchive.request.CheckPublicExpenseEligibilityRequest;
import com.openhis.campus.application.campusarchive.request.ListArchiveChangesRequest;
import com.openhis.campus.application.campusarchive.request.ListArchiveTransfersRequest;
import com.openhis.campus.application.campusarchive.request.PageOrphanArchivesRequest;
import com.openhis.campus.application.campusarchive.request.ResolveCampusProfileByPatientIdRequest;
import com.openhis.campus.application.campusarchive.request.SyncPeProfileFromHisRequest;
import com.openhis.campus.application.campusarchive.response.BindArchiveResponse;
import com.openhis.campus.application.campusarchive.response.CampusProfileResponse;
import com.openhis.campus.application.campusarchive.response.EligibilityCheckResponse;
import com.openhis.campus.application.campusarchive.response.OrphanArchiveDto;
import com.openhis.campus.application.campusarchive.response.PageArchiveChangesResponse;
import com.openhis.campus.application.campusarchive.response.PageArchiveTransfersResponse;
import com.openhis.campus.application.campusarchive.response.PageOrphanArchivesResponse;
import com.openhis.campus.application.campusarchive.response.SyncPeProfileResponse;
import com.openhis.campus.application.share.CampusArchiveChangeLogSupport;
import com.openhis.campus.application.share.CampusOperatorResolver;
import com.openhis.campus.application.share.CampusPagedResultBuilder;
import com.openhis.campus.domain.archive.CampusArchiveQueryReader;
import com.openhis.campus.domain.archive.view.CampusProfileView;
import com.openhis.campus.domain.archivechangelog.entity.ArchiveChangeLog;
import com.openhis.campus.domain.archivechangelog.service.ArchiveChangeLogQueryService;
import com.openhis.campus.domain.archivetransfer.entity.ArchiveTransfer;
import com.openhis.campus.domain.archivetransfer.service.ArchiveTransferQueryService;
import com.openhis.campus.domain.campussite.entity.CampusSite;
import com.openhis.campus.domain.campussite.repository.CampusSiteRepository;
import com.openhis.campus.domain.patientstaff.entity.PatientStaff;
import com.openhis.campus.domain.patientstaff.service.PatientStaffDomainService;
import com.openhis.campus.domain.patientstaff.service.PatientStaffQueryService;
import com.openhis.campus.domain.patientstudent.entity.PatientStudent;
import com.openhis.campus.domain.patientstudent.service.PatientStudentDomainService;
import com.openhis.campus.domain.patientstudent.service.PatientStudentQueryService;
import com.openhis.campus.domain.publicexpenseprofile.entity.PublicExpenseProfile;
import com.openhis.campus.domain.publicexpenseprofile.service.PublicExpenseEligibilityDomainService;
import com.openhis.campus.domain.publicexpenseprofile.service.PublicExpenseProfileDomainService;
import com.openhis.campus.domain.publicexpenseprofile.service.PublicExpenseProfileQueryService;
import com.openhis.campus.domain.shared.CampusDomainConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 档案共通查询应用服务。
 *
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/campusArchiveQuery/{methodName}}。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class CampusArchiveQueryAppService {

    private final PatientStudentQueryService patientStudentQueryService;
    private final PatientStaffQueryService patientStaffQueryService;
    private final PatientStudentDomainService patientStudentDomainService;
    private final PatientStaffDomainService patientStaffDomainService;
    private final PublicExpenseProfileQueryService publicExpenseProfileQueryService;
    private final PublicExpenseProfileDomainService publicExpenseProfileDomainService;
    private final PublicExpenseEligibilityDomainService publicExpenseEligibilityDomainService;
    private final ArchiveChangeLogQueryService archiveChangeLogQueryService;
    private final ArchiveTransferQueryService archiveTransferQueryService;
    private final CampusArchiveQueryReader campusArchiveQueryReader;
    private final CampusSiteRepository campusSiteRepository;
    private final CampusArchiveMapper campusArchiveMapper;
    private final CampusArchiveChangeLogSupport changeLogSupport;
    private final CampusOperatorResolver campusOperatorResolver;

    @Transactional
    public BindArchiveResponse bindArchiveByIdCard(@Valid BindArchiveByIdCardRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        Optional<PatientStudent> student = findStudentByIdCard(request.getTenantId(), request.getIdCard());
        if (student.isPresent()) {
            PatientStudent saved = patientStudentDomainService.bindPatient(
                request.getTenantId(), student.get().getId(), request.getPatientId(), operatorId);
            BindArchiveResponse response = new BindArchiveResponse();
            response.setSubjectTypeEnum(CampusDomainConstants.SUBJECT_STUDENT);
            response.setSubjectId(saved.getId());
            response.setPatientId(saved.getPatientId());
            return response;
        }
        Optional<PatientStaff> staff = findStaffByIdCard(request.getTenantId(), request.getIdCard());
        if (staff.isPresent()) {
            PatientStaff saved = patientStaffDomainService.bindPatient(
                request.getTenantId(), staff.get().getId(), request.getPatientId(), operatorId);
            BindArchiveResponse response = new BindArchiveResponse();
            response.setSubjectTypeEnum(CampusDomainConstants.SUBJECT_STAFF);
            response.setSubjectId(saved.getId());
            response.setPatientId(saved.getPatientId());
            return response;
        }
        throw new BusinessException("ARCHIVE_NOT_FOUND", "未找到匹配身份证的档案");
    }

    public PageOrphanArchivesResponse pageOrphanArchives(@Valid PageOrphanArchivesRequest request) {
        List<OrphanArchiveDto> all = new ArrayList<>();
        for (PatientStudent student : patientStudentQueryService.listOrphans(request.getTenantId())) {
            all.add(campusArchiveMapper.toOrphanDto(student));
        }
        for (PatientStaff staff : patientStaffQueryService.listOrphans(request.getTenantId())) {
            all.add(campusArchiveMapper.toOrphanDto(staff, resolveCampusCode(staff.getTenantId(), staff.getCampusSiteId())));
        }
        int pageNo = request.getPageNo();
        int pageSize = request.getPageSize();
        int fromIndex = Math.max(0, (pageNo - 1) * pageSize);
        int toIndex = Math.min(all.size(), fromIndex + pageSize);
        List<OrphanArchiveDto> pageContent = fromIndex >= all.size() ? List.of() : all.subList(fromIndex, toIndex);
        Page<OrphanArchiveDto> page = new PageImpl<>(pageContent, PageRequest.of(pageNo - 1, pageSize), all.size());
        return CampusPagedResultBuilder.build(new PageOrphanArchivesResponse(), pageContent, page);
    }

    public EligibilityCheckResponse checkPublicExpenseEligibility(@Valid CheckPublicExpenseEligibilityRequest request) {
        if (request.getPatientId() != null) {
            return campusArchiveMapper.toEligibilityResponse(
                publicExpenseEligibilityDomainService.check(request.getTenantId(), request.getPatientId()));
        }
        if (request.getSubjectTypeEnum() != null && request.getSubjectId() != null) {
            return campusArchiveMapper.toEligibilityResponse(publicExpenseEligibilityDomainService.checkBySubject(
                request.getTenantId(), request.getSubjectTypeEnum(), request.getSubjectId()));
        }
        throw new BusinessException("SUBJECT_NOT_FOUND", "请提供 patientId 或 subjectType/subjectId");
    }

    public CampusProfileResponse resolveCampusProfileByPatientId(
        @Valid ResolveCampusProfileByPatientIdRequest request) {
        CampusProfileView view = campusArchiveQueryReader.resolveCampusProfileByPatientId(
                request.getTenantId(), request.getPatientId())
            .orElseThrow(() -> new BusinessException("ARCHIVE_NOT_FOUND", "未找到 Campus 档案"));
        return campusArchiveMapper.toCampusProfileResponse(view);
    }

    public PageArchiveChangesResponse listArchiveChanges(@Valid ListArchiveChangesRequest request) {
        Page<ArchiveChangeLog> page = archiveChangeLogQueryService.pageBySubject(
            request.getTenantId(),
            request.getSubjectTypeEnum(),
            request.getSubjectId(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<com.openhis.campus.application.campusarchive.response.ArchiveChangeLogDto> rows = new ArrayList<>();
        for (ArchiveChangeLog entity : page.getContent()) {
            rows.add(campusArchiveMapper.toChangeLogDto(entity));
        }
        return CampusPagedResultBuilder.build(new PageArchiveChangesResponse(), rows, page);
    }

    public PageArchiveTransfersResponse listArchiveTransfers(@Valid ListArchiveTransfersRequest request) {
        Page<ArchiveTransfer> page = archiveTransferQueryService.pageBySubject(
            request.getTenantId(),
            request.getSubjectTypeEnum(),
            request.getSubjectId(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<com.openhis.campus.application.campusarchive.response.ArchiveTransferDto> rows = new ArrayList<>();
        for (ArchiveTransfer entity : page.getContent()) {
            rows.add(campusArchiveMapper.toTransferDto(entity));
        }
        return CampusPagedResultBuilder.build(new PageArchiveTransfersResponse(), rows, page);
    }

    @Transactional
    public SyncPeProfileResponse syncPeProfileFromHis(@Valid SyncPeProfileFromHisRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        PatientStudent student = patientStudentQueryService.findByStudentNo(request.getTenantId(), request.getStudentNo())
            .orElseThrow(() -> new BusinessException("STUDENT_NOT_FOUND", "学生档案不存在"));
        if (request.getIdCard() != null && !request.getIdCard().isBlank()
            && student.getIdCard() != null && !Objects.equals(student.getIdCard(), request.getIdCard())) {
            throw new BusinessException("ARCHIVE_NOT_FOUND", "学号与身份证不匹配");
        }
        PublicExpenseProfile before = publicExpenseProfileQueryService.getBySubject(
            student.getTenantId(), CampusDomainConstants.SUBJECT_STUDENT, student.getId()).orElse(null);
        PublicExpenseProfile saved = publicExpenseProfileDomainService.syncFromHis(
            student.getTenantId(),
            CampusDomainConstants.SUBJECT_STUDENT,
            student.getId(),
            request.getCardNo(),
            request.getGfStatus(),
            operatorId);
        List<CampusArchiveChangeLogSupport.FieldChange> changes = CampusArchiveChangeLogSupport.collect(
            CampusArchiveChangeLogSupport.diffString(
                "cardNo", before != null ? before.getCardNo() : null, saved.getCardNo()),
            CampusArchiveChangeLogSupport.diffString(
                "gfStatusEnum", before != null ? before.getGfStatusEnum() : null, saved.getGfStatusEnum()));
        changeLogSupport.appendSyncBatch(
            student.getTenantId(),
            CampusDomainConstants.SUBJECT_STUDENT,
            student.getId(),
            student.getCampusCode(),
            changes,
            operatorId);
        SyncPeProfileResponse response = new SyncPeProfileResponse();
        response.setSubjectId(student.getId());
        response.setSubjectTypeEnum(CampusDomainConstants.SUBJECT_STUDENT);
        response.setProfileId(saved.getId());
        return response;
    }

    private Optional<PatientStudent> findStudentByIdCard(Long tenantId, String idCard) {
        return patientStudentQueryService.page(tenantId, idCard, null, null, null, null, null, null, null, PageRequest.of(0, 20))
            .getContent().stream()
            .filter(student -> idCard.equals(student.getIdCard()))
            .findFirst();
    }

    private Optional<PatientStaff> findStaffByIdCard(Long tenantId, String idCard) {
        return patientStaffQueryService.page(tenantId, idCard, null, null, null, null, PageRequest.of(0, 20))
            .getContent().stream()
            .filter(staff -> idCard.equals(staff.getIdCard()))
            .findFirst();
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

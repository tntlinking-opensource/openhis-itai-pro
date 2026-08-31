/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.archive.service;

import com.openhis.health.application.peis.archive.dto.ArchiveCompletenessDto;
import com.openhis.health.application.peis.archive.dto.ArchiveMissingItemDto;
import com.openhis.health.application.peis.archive.request.CheckArchiveCompletenessRequest;
import com.openhis.health.application.peis.archive.request.LoadArchiveRequest;
import com.openhis.health.application.peis.archive.request.SaveArchiveRequest;
import com.openhis.health.application.peis.archive.response.CheckArchiveCompletenessResponse;
import com.openhis.health.application.peis.archive.response.LoadArchiveResponse;
import com.openhis.health.application.peis.archive.response.SaveArchiveResponse;
import com.openhis.health.application.peis.history.dto.ExamHistoryRowDto;
import com.openhis.health.application.peis.history.request.ListPatientExamHistoryRequest;
import com.openhis.health.application.peis.history.response.ListPatientExamHistoryResponse;
import com.openhis.health.application.peis.history.service.PeisExamHistoryAppService;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.peis.group.repository.PeisGroupRosterRepository;
import com.openhis.health.domain.peis.healtharchive.entity.PeisPatientHealthArchive;
import com.openhis.health.domain.peis.healtharchive.service.PeisHealthArchiveDomainService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PeisArchiveCompletenessConstants;
import com.openhis.health.domain.share.constants.PeisExamKindConstants;
import com.openhis.health.domain.share.extension.campus.PeisCampusIdentityPort;
import com.openhis.health.domain.share.extension.campus.PeisCampusIdentityView;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 学生/体检健康档案（AS-PEIS-ARCH-001 / BAS-05）。
 */
@Service
@Validated
@RequiredArgsConstructor
public class PeisHealthArchiveAppService {

    private static final String SCHOOL_IN_SCHOOL = "IN_SCHOOL";
    private static final String SCHOOL_GRADUATED = "GRADUATED";
    private static final String SUBJECT_STUDENT = "STUDENT";

    private final PeisHealthArchiveDomainService peisHealthArchiveDomainService;
    private final PatientQueryService patientQueryService;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final PeisExamHistoryAppService peisExamHistoryAppService;
    private final PeisGroupRosterRepository peisGroupRosterRepository;
    private final ObjectProvider<PeisCampusIdentityPort> peisCampusIdentityPort;

    public LoadArchiveResponse loadArchive(@Valid LoadArchiveRequest request) {
        ResolvedPatient resolved = resolvePatient(request.getPatientId(), request.getStudentNo());
        LoadArchiveResponse response = new LoadArchiveResponse();
        response.setPatientId(resolved.patientId());
        response.setStudentNo(resolved.studentNo());
        peisHealthArchiveDomainService.findByPatientId(resolved.patientId()).ifPresent(archive -> {
            response.setArchiveId(archive.getId());
            response.setPastHistory(archive.getPastHistory());
            response.setFamilyHistory(archive.getFamilyHistory());
            response.setHabitSummary(archive.getHabitSummary());
            response.setAllergySummary(archive.getAllergySummary());
            response.setTagCode(archive.getTagCode());
            response.setRemark(archive.getRemark());
        });
        if (Boolean.TRUE.equals(request.getIncludeCompleteness())) {
            response.setCompleteness(buildCompleteness(resolved.patientId(), resolved.studentNo()).getCompleteness());
        }
        return response;
    }

    @Transactional
    public SaveArchiveResponse saveArchive(@Valid SaveArchiveRequest request) {
        if (request.getPatientId() == null) {
            throw new ValidationException("患者不能为空");
        }
        if (patientQueryService.findOptionalPatientEntityById(request.getPatientId()).isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PEIS_REG_PATIENT_NOT_FOUND, "患者不存在");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录");
        PeisPatientHealthArchive incoming = new PeisPatientHealthArchive();
        incoming.setPatientId(request.getPatientId());
        incoming.setOrgId(request.getOrgId());
        incoming.setPastHistory(request.getPastHistory());
        incoming.setFamilyHistory(request.getFamilyHistory());
        incoming.setHabitSummary(request.getHabitSummary());
        incoming.setAllergySummary(request.getAllergySummary());
        incoming.setTagCode(request.getTagCode());
        incoming.setRemark(request.getRemark());
        PeisPatientHealthArchive saved = peisHealthArchiveDomainService.upsert(incoming, operatorId);
        SaveArchiveResponse response = new SaveArchiveResponse();
        response.setArchiveId(saved.getId());
        response.setPatientId(saved.getPatientId());
        return response;
    }

    /**
     * IF-PEIS-ARCH-03：学生体检档案完整性提示。
     */
    public CheckArchiveCompletenessResponse checkArchiveCompleteness(
            @Valid CheckArchiveCompletenessRequest request) {
        if (request == null) {
            throw new ValidationException("请提供 patientId 或 studentNo");
        }
        return buildCompleteness(request.getPatientId(), request.getStudentNo());
    }

    private CheckArchiveCompletenessResponse buildCompleteness(Long patientId, String studentNo) {
        ResolvedPatient resolved = resolvePatient(patientId, studentNo);
        Optional<PeisPatientHealthArchive> archiveOpt =
            peisHealthArchiveDomainService.findByPatientId(resolved.patientId());
        ListPatientExamHistoryRequest historyReq = new ListPatientExamHistoryRequest();
        historyReq.setPatientId(resolved.patientId());
        ListPatientExamHistoryResponse history = peisExamHistoryAppService.listPatientExamHistory(historyReq);

        Optional<PeisCampusIdentityView> campus = resolveCampus(resolved.patientId(), resolved.studentNo());
        String subjectType = history.getSubjectType();
        String schoolStatus = null;
        if (campus.isPresent()) {
            subjectType = campus.get().getSubjectType();
            schoolStatus = campus.get().getSchoolStatusEnum();
            if (resolved.studentNo() == null) {
                // keep studentNo from campus when only patientId provided
            }
        }

        List<ArchiveMissingItemDto> missing = new ArrayList<>();
        String past = archiveOpt.map(PeisPatientHealthArchive::getPastHistory).orElse(null);
        String allergy = archiveOpt.map(PeisPatientHealthArchive::getAllergySummary).orElse(null);
        boolean graduated = isGraduated(schoolStatus, campus);

        if (isBlank(past)) {
            missing.add(item(
                PeisArchiveCompletenessConstants.MISSING_PAST_HISTORY,
                graduated
                    ? PeisArchiveCompletenessConstants.LEVEL_BLOCK
                    : PeisArchiveCompletenessConstants.LEVEL_WARN,
                "既往史未填写"));
        }
        if (isBlank(allergy)) {
            missing.add(item(
                PeisArchiveCompletenessConstants.MISSING_ALLERGY,
                PeisArchiveCompletenessConstants.LEVEL_WARN,
                "过敏史未填写"));
        }

        List<ExamHistoryRowDto> rows = history.getRows() == null ? List.of() : history.getRows();
        boolean isStudent = SUBJECT_STUDENT.equals(subjectType)
            || (resolved.studentNo() != null && !resolved.studentNo().isBlank())
            || campus.map(v -> SUBJECT_STUDENT.equals(v.getSubjectType())).orElse(false);

        if (isStudent) {
            boolean hasFreshman = rows.stream()
                .anyMatch(r -> PeisExamKindConstants.FRESHMAN.equals(r.getExamKind()));
            if (!hasFreshman) {
                missing.add(item(
                    PeisArchiveCompletenessConstants.MISSING_FRESHMAN,
                    graduated
                        ? PeisArchiveCompletenessConstants.LEVEL_WARN
                        : PeisArchiveCompletenessConstants.LEVEL_INFO,
                    "未见新生体检记录"));
            }
            int year = Year.now().getValue();
            boolean hasYearExam = rows.stream()
                .anyMatch(r -> r.getCalendarYear() != null && r.getCalendarYear() == year);
            if (!graduated && !hasYearExam) {
                ArchiveMissingItemDto annual = item(
                    PeisArchiveCompletenessConstants.MISSING_ANNUAL_YEAR,
                    PeisArchiveCompletenessConstants.LEVEL_WARN,
                    year + " 年尚未参加在校体检");
                missing.add(annual);
            }
            boolean hasFinal = rows.stream().anyMatch(r -> Boolean.TRUE.equals(r.getFinalReport()));
            if (graduated && !hasFinal) {
                missing.add(item(
                    PeisArchiveCompletenessConstants.MISSING_FINAL_REPORT,
                    PeisArchiveCompletenessConstants.LEVEL_BLOCK,
                    "毕业档案缺少已定稿总检报告"));
            }
            if (rows.isEmpty()) {
                missing.add(item(
                    PeisArchiveCompletenessConstants.MISSING_ANY_EXAM,
                    graduated
                        ? PeisArchiveCompletenessConstants.LEVEL_BLOCK
                        : PeisArchiveCompletenessConstants.LEVEL_WARN,
                    "无任何有效体检记录"));
            }
        }

        ArchiveCompletenessDto completeness = toCompleteness(missing);
        CheckArchiveCompletenessResponse response = new CheckArchiveCompletenessResponse();
        response.setPatientId(resolved.patientId());
        response.setStudentNo(
            resolved.studentNo() != null
                ? resolved.studentNo()
                : campus.map(PeisCampusIdentityView::getStudentNo).orElse(null));
        response.setSubjectType(subjectType);
        response.setSchoolStatusEnum(schoolStatus);
        response.setCompleteness(completeness);
        return response;
    }

    private static ArchiveCompletenessDto toCompleteness(List<ArchiveMissingItemDto> missing) {
        ArchiveCompletenessDto dto = new ArchiveCompletenessDto();
        dto.setMissingItems(missing);
        String level = "ok";
        for (ArchiveMissingItemDto item : missing) {
            level = maxLevel(level, item.getLevel());
        }
        dto.setLevel(level);
        dto.setComplete(!PeisArchiveCompletenessConstants.LEVEL_BLOCK.equals(level));
        if (missing.isEmpty()) {
            dto.setSummary("体检档案完整");
        } else if (PeisArchiveCompletenessConstants.LEVEL_BLOCK.equals(level)) {
            dto.setSummary("体检档案不完整，存在阻断项");
        } else if (PeisArchiveCompletenessConstants.LEVEL_WARN.equals(level)) {
            dto.setSummary("体检档案有待补齐项");
        } else {
            dto.setSummary("体检档案基本可用，有提示项");
        }
        return dto;
    }

    private static String maxLevel(String a, String b) {
        return levelRank(b) > levelRank(a) ? b : a;
    }

    private static int levelRank(String level) {
        if (PeisArchiveCompletenessConstants.LEVEL_BLOCK.equals(level)) {
            return 3;
        }
        if (PeisArchiveCompletenessConstants.LEVEL_WARN.equals(level)) {
            return 2;
        }
        if (PeisArchiveCompletenessConstants.LEVEL_INFO.equals(level)) {
            return 1;
        }
        return 0;
    }

    private static ArchiveMissingItemDto item(String code, String level, String message) {
        ArchiveMissingItemDto dto = new ArchiveMissingItemDto();
        dto.setCode(code);
        dto.setLevel(level);
        dto.setMessage(message);
        return dto;
    }

    private static boolean isGraduated(String schoolStatus, Optional<PeisCampusIdentityView> campus) {
        if (SCHOOL_GRADUATED.equals(schoolStatus)) {
            return true;
        }
        if (schoolStatus != null && !schoolStatus.isBlank() && !SCHOOL_IN_SCHOOL.equals(schoolStatus)) {
            return true;
        }
        return campus.map(v -> v.getLeaveTime() != null).orElse(false);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResolvedPatient resolvePatient(Long patientId, String studentNo) {
        if (patientId != null) {
            if (patientQueryService.findOptionalPatientEntityById(patientId).isEmpty()) {
                throw new ValidationException("患者不存在");
            }
            String sn = studentNo;
            if (sn == null || sn.isBlank()) {
                sn = resolveCampus(patientId, null).map(PeisCampusIdentityView::getStudentNo).orElse(null);
            }
            return new ResolvedPatient(patientId, sn);
        }
        if (studentNo == null || studentNo.isBlank()) {
            throw new ValidationException("请提供 patientId 或 studentNo");
        }
        String sn = studentNo.trim();
        Optional<PeisCampusIdentityView> campus = resolveCampus(null, sn);
        if (campus.isPresent() && campus.get().getPatientId() != null) {
            return new ResolvedPatient(campus.get().getPatientId(), sn);
        }
        Long fromRoster = peisGroupRosterRepository.findAll((Specification<PeisGroupRoster>) (root, q, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isFalse(root.get("isDeleted")));
            preds.add(cb.equal(root.get("studentNo"), sn));
            preds.add(cb.isNotNull(root.get("patientId")));
            return cb.and(preds.toArray(new Predicate[0]));
        }).stream()
            .map(PeisGroupRoster::getPatientId)
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow(() -> new ValidationException("未找到该学号对应患者，请先维护 Campus 学籍或团检名册"));
        return new ResolvedPatient(fromRoster, sn);
    }

    private Optional<PeisCampusIdentityView> resolveCampus(Long patientId, String studentNo) {
        PeisCampusIdentityPort port = peisCampusIdentityPort.getIfAvailable();
        if (port == null) {
            return Optional.empty();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (patientId != null) {
            return port.resolveByPatientId(tenantId, patientId);
        }
        if (studentNo != null && !studentNo.isBlank()) {
            return port.resolveByStudentNo(tenantId, studentNo.trim());
        }
        return Optional.empty();
    }

    private record ResolvedPatient(Long patientId, String studentNo) {
    }
}

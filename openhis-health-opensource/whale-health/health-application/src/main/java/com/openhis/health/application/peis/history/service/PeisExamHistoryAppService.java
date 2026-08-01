/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.history.service;

import com.openhis.health.application.peis.history.dto.ExamHistoryRowDto;
import com.openhis.health.application.peis.history.request.ListPatientExamHistoryRequest;
import com.openhis.health.application.peis.history.response.ListPatientExamHistoryResponse;
import com.openhis.health.application.peis.history.support.PeisPackageSnapshotSupport;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.clinical.diagnosticreport.queries.model.DiagnosticReportModel;
import com.openhis.health.domain.clinical.diagnosticreport.service.DiagnosticReportQueryService;
import com.openhis.health.domain.peis.encounterext.queries.model.PeisEncounterExtModel;
import com.openhis.health.domain.peis.encounterext.service.PeisEncounterExtQueryService;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.peis.group.repository.PeisGroupRosterRepository;
import com.openhis.health.domain.share.constants.DiagnosticReportStatusConstants;
import com.openhis.health.domain.share.constants.PeisExamKindConstants;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.extension.campus.PeisCampusIdentityPort;
import com.openhis.health.domain.share.extension.campus.PeisCampusIdentityView;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 学生/教职工体检历史（历年 PE Encounter）。
 */
@Service
@Validated
@RequiredArgsConstructor
public class PeisExamHistoryAppService {

    private final EncounterQueryService encounterQueryService;
    private final PeisEncounterExtQueryService peisEncounterExtQueryService;
    private final DiagnosticReportQueryService diagnosticReportQueryService;
    private final PatientQueryService patientQueryService;
    private final PeisGroupRosterRepository peisGroupRosterRepository;
    private final ObjectProvider<PeisCampusIdentityPort> peisCampusIdentityPort;

    /**
     * 按 patientId / 学号 / 工号列历年体检。
     */
    public ListPatientExamHistoryResponse listPatientExamHistory(@Valid ListPatientExamHistoryRequest request) {
        ResolvedIdentity identity = resolveIdentity(request);
        ListPatientExamHistoryResponse response = new ListPatientExamHistoryResponse();
        response.setPatientId(identity.patientId());
        response.setStudentNo(identity.studentNo());
        response.setEmployeeNo(identity.employeeNo());
        response.setSubjectType(identity.subjectType());
        response.setSubjectName(identity.subjectName());

        Set<String> kindFilter = toKindFilter(request.getExamKinds());
        boolean onlyFinal = Boolean.TRUE.equals(request.getOnlyFinal());
        List<ExamHistoryRowDto> rows = new ArrayList<>();
        for (Encounter encounter : encounterQueryService.listPeEncounterEntitiesByPatientId(identity.patientId())) {
            if (isCancelled(encounter)) {
                continue;
            }
            ExamHistoryRowDto row = toRow(encounter);
            if (!kindFilter.isEmpty()
                && (row.getExamKind() == null || !kindFilter.contains(row.getExamKind()))) {
                continue;
            }
            if (onlyFinal && !Boolean.TRUE.equals(row.getFinalReport())) {
                continue;
            }
            rows.add(row);
        }
        rows.sort(Comparator.comparing(ExamHistoryRowDto::getCreatedAt,
            Comparator.nullsLast(Comparator.reverseOrder())));
        response.setRows(rows);
        return response;
    }

    private ExamHistoryRowDto toRow(Encounter encounter) {
        ExamHistoryRowDto row = new ExamHistoryRowDto();
        row.setEncounterId(encounter.getId());
        row.setStatusEnum(encounter.getStatusEnum());
        row.setCreatedAt(encounter.getCreatedAt());
        if (encounter.getCreatedAt() != null) {
            row.setCalendarYear(encounter.getCreatedAt().getYear());
        }
        Optional<PeisEncounterExtModel> extOpt =
            peisEncounterExtQueryService.findOptionalByEncounterId(encounter.getId());
        if (extOpt.isPresent()) {
            PeisEncounterExtModel ext = extOpt.get();
            row.setBusNo(ext.getBusNo());
            row.setPackageActivityId(ext.getPackageActivityId());
            row.setGroupFlag(ext.getGroupFlag());
            String snapshot = ext.getPackageSnapshotJson();
            row.setExamKind(PeisPackageSnapshotSupport.extractExamKind(snapshot));
            row.setPackageName(PeisPackageSnapshotSupport.extractPackageName(snapshot));
        }
        Optional<DiagnosticReportModel> reportOpt =
            diagnosticReportQueryService.getByEncounterId(encounter.getId());
        if (reportOpt.isPresent()) {
            DiagnosticReportModel report = reportOpt.get();
            row.setDiagnosticReportId(report.getId());
            row.setFinalReport(DiagnosticReportStatusConstants.STATUS_FINAL.equals(report.getStatusEnum()));
        } else {
            row.setFinalReport(Boolean.FALSE);
        }
        return row;
    }

    private ResolvedIdentity resolveIdentity(ListPatientExamHistoryRequest request) {
        if (request == null) {
            throw new ValidationException("请提供 patientId、studentNo 或 employeeNo");
        }
        if (request.getPatientId() != null) {
            if (patientQueryService.findOptionalPatientEntityById(request.getPatientId()).isEmpty()) {
                throw new ValidationException("患者不存在");
            }
            Optional<PeisCampusIdentityView> campus = resolveCampusByPatient(request.getPatientId());
            return campus.map(ResolvedIdentity::fromCampus)
                .orElseGet(() -> new ResolvedIdentity(
                    request.getPatientId(), request.getStudentNo(), request.getEmployeeNo(), null, null));
        }
        if (request.getStudentNo() != null && !request.getStudentNo().isBlank()) {
            String studentNo = request.getStudentNo().trim();
            PeisCampusIdentityPort port = peisCampusIdentityPort.getIfAvailable();
            Optional<PeisCampusIdentityView> campus = port == null
                ? Optional.empty()
                : port.resolveByStudentNo(TenantUtils.getCurrentTenantId(), studentNo);
            if (campus.isPresent() && campus.get().getPatientId() != null) {
                return ResolvedIdentity.fromCampus(campus.get());
            }
            Long fromRoster = resolvePatientIdFromRoster(studentNo);
            return new ResolvedIdentity(fromRoster, studentNo, null, "STUDENT", null);
        }
        if (request.getEmployeeNo() != null && !request.getEmployeeNo().isBlank()) {
            String employeeNo = request.getEmployeeNo().trim();
            PeisCampusIdentityPort port = peisCampusIdentityPort.getIfAvailable();
            if (port == null) {
                throw new ValidationException("未启用 Campus，无法按工号解析教职工");
            }
            PeisCampusIdentityView view = port.resolveByEmployeeNo(TenantUtils.getCurrentTenantId(), employeeNo)
                .orElseThrow(() -> new ValidationException("未找到该工号对应教职工"));
            if (view.getPatientId() == null) {
                throw new ValidationException("教职工未绑定患者");
            }
            return ResolvedIdentity.fromCampus(view);
        }
        throw new ValidationException("请提供 patientId、studentNo 或 employeeNo");
    }

    private Optional<PeisCampusIdentityView> resolveCampusByPatient(Long patientId) {
        PeisCampusIdentityPort port = peisCampusIdentityPort.getIfAvailable();
        if (port == null) {
            return Optional.empty();
        }
        return port.resolveByPatientId(TenantUtils.getCurrentTenantId(), patientId);
    }

    private Long resolvePatientIdFromRoster(String studentNo) {
        return peisGroupRosterRepository.findAll((Specification<PeisGroupRoster>) (root, q, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isFalse(root.get("isDeleted")));
            preds.add(cb.equal(root.get("studentNo"), studentNo));
            preds.add(cb.isNotNull(root.get("patientId")));
            return cb.and(preds.toArray(new Predicate[0]));
        }).stream()
            .map(PeisGroupRoster::getPatientId)
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow(() -> new ValidationException("未找到该学号对应患者，请先维护 Campus 学籍或团检名册"));
    }

    private static Set<String> toKindFilter(List<String> examKinds) {
        Set<String> set = new HashSet<>();
        if (examKinds == null) {
            return set;
        }
        for (String kind : examKinds) {
            String n = PeisExamKindConstants.normalizeOrNull(kind);
            if (n != null) {
                set.add(n);
            }
        }
        return set;
    }

    private static boolean isCancelled(Encounter encounter) {
        String status = encounter.getStatusEnum();
        return EncounterStatus.CANCELLED.getCode().equals(status)
            || EncounterStatus.ENTERED_IN_ERROR.getCode().equals(status);
    }

    private record ResolvedIdentity(
        Long patientId,
        String studentNo,
        String employeeNo,
        String subjectType,
        String subjectName
    ) {
        static ResolvedIdentity fromCampus(PeisCampusIdentityView view) {
            return new ResolvedIdentity(
                view.getPatientId(),
                view.getStudentNo(),
                view.getEmployeeNo(),
                view.getSubjectType(),
                view.getName());
        }
    }
}

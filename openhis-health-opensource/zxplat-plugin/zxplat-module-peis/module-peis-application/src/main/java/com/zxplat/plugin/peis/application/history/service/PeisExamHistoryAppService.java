/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.history.service;

import com.zxplat.plugin.peis.application.history.dto.ExamHistoryRowDto;
import com.zxplat.plugin.peis.application.history.request.CancelPhysicalExamRequest;
import com.zxplat.plugin.peis.application.history.request.ListPatientExamHistoryRequest;
import com.zxplat.plugin.peis.application.history.request.PageExamRecordsRequest;
import com.zxplat.plugin.peis.application.history.response.CancelPhysicalExamResponse;
import com.zxplat.plugin.peis.application.history.response.ListPatientExamHistoryResponse;
import com.zxplat.plugin.peis.application.history.response.PageExamRecordsResponse;
import com.zxplat.plugin.peis.application.history.support.PeisPackageSnapshotSupport;
import com.zxplat.plugin.peis.application.history.support.PhysicalExamHistoryCancelExecutor;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.framework.core.application.paging.PagedResultBuilder;
import com.zxplat.plugin.peis.application.share.PeisCurrentPractitionerResolver;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.encounter.entity.Encounter;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.patient.queries.PatientPagedQuery;
import com.zxplat.domain.administration.patient.queries.model.BatchPatientDetailQueryModel;
import com.zxplat.domain.administration.patient.queries.model.PatientCoreQueryModel;
import com.zxplat.domain.administration.patient.queries.model.PatientPageItemQueryModel;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.domain.clinical.diagnosticreport.queries.model.DiagnosticReportModel;
import com.zxplat.domain.clinical.diagnosticreport.service.DiagnosticReportQueryService;
import com.zxplat.plugin.peis.domain.encounterext.entity.PeisEncounterExt;
import com.zxplat.plugin.peis.domain.encounterext.queries.model.PeisEncounterExtModel;
import com.zxplat.plugin.peis.domain.encounterext.service.PeisEncounterExtQueryService;
import com.zxplat.plugin.peis.domain.group.entity.PeisGroupRoster;
import com.zxplat.plugin.peis.domain.group.repository.PeisGroupRosterRepository;
import com.zxplat.domain.share.constants.DiagnosticReportStatusConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.constants.PeisExamHistoryConstants;
import com.zxplat.domain.share.constants.PeisExamKindConstants;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.EncounterClass;
import com.zxplat.domain.share.enums.EncounterStatus;
import com.zxplat.domain.share.extension.campus.PeisCampusIdentityPort;
import com.zxplat.domain.share.extension.campus.PeisCampusIdentityView;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 学生/教职工体检历史与体检记录分页（历年 PE Encounter）。
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
    private final ChargeItemQueryService chargeItemQueryService;
    private final PeisCurrentPractitionerResolver currentPractitionerResolver;
    private final PhysicalExamHistoryCancelExecutor physicalExamHistoryCancelExecutor;
    /** 分页窗口入站门控（B.10.11）。 */
    private final PageWindowGate pageWindowGate;

    /**
     * 体检记录分页（以体检单 Encounter 为主列表）。
     */
    public PageExamRecordsResponse pageExamRecords(PageExamRecordsRequest request) {
        // Gate：分页窗口合法（B.10.11）
        pageWindowGate.requirePositivePageWindow(
            request, ErrorCodeConstants.PEIS_HIST_INVALID_ARGUMENT, "体检记录分页参数非法");
        LocalDate dateFrom = request.getDateFrom();
        LocalDate dateTo = request.getDateTo();
        Long filterPatientId = request.getPatientId();
        String keyword = request.getKeyword() == null
            ? "" : request.getKeyword().trim();
        String groupScope = request.getGroupScope() == null
            ? "" : request.getGroupScope().trim();

        Set<Long> keywordPatientIds = null;
        if (StringUtils.isNotEmpty(keyword) && filterPatientId == null) {
            keywordPatientIds = resolvePatientIdsByKeyword(keyword);
        }
        Set<Long> finalKeywordPatientIds = keywordPatientIds;

        Specification<Encounter> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.PE.getCode()));
            predicates.add(cb.equal(root.get(FieldNameConstants.IS_DELETED), Boolean.FALSE));
            predicates.add(cb.notEqual(
                root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.CANCELLED.getCode()));
            predicates.add(cb.notEqual(
                root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.ENTERED_IN_ERROR.getCode()));
            if (dateFrom != null) {
                LocalDateTime rangeStart = dateFrom.atStartOfDay();
                predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.START_TIME), rangeStart));
            }
            if (dateTo != null) {
                LocalDateTime rangeEnd = dateTo.atTime(LocalTime.MAX);
                predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.START_TIME), rangeEnd));
            }
            if (filterPatientId != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), filterPatientId));
            }
            if (StringUtils.isNotEmpty(keyword)) {
                List<Predicate> keywordOr = new ArrayList<>();
                keywordOr.add(cb.like(root.get(FieldNameConstants.BUS_NO), "%" + keyword + "%"));
                if (finalKeywordPatientIds != null && !finalKeywordPatientIds.isEmpty()) {
                    keywordOr.add(root.get(FieldNameConstants.PATIENT_ID).in(finalKeywordPatientIds));
                }
                predicates.add(cb.or(keywordOr.toArray(new Predicate[0])));
            }
            if ("group".equals(groupScope)) {
                Subquery<Long> groupSub = query.subquery(Long.class);
                Root<PeisEncounterExt> extRoot = groupSub.from(PeisEncounterExt.class);
                groupSub.select(extRoot.get(FieldNameConstants.ENCOUNTER_ID));
                groupSub.where(
                    cb.equal(extRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
                    cb.equal(extRoot.get(FieldNameConstants.IS_DELETED), Boolean.FALSE),
                    cb.isTrue(extRoot.get("groupFlag")));
                predicates.add(cb.exists(groupSub));
            } else if ("personal".equals(groupScope)) {
                Subquery<Long> personalSub = query.subquery(Long.class);
                Root<PeisEncounterExt> extRoot = personalSub.from(PeisEncounterExt.class);
                personalSub.select(extRoot.get(FieldNameConstants.ENCOUNTER_ID));
                personalSub.where(
                    cb.equal(extRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
                    cb.equal(extRoot.get(FieldNameConstants.IS_DELETED), Boolean.FALSE),
                    cb.or(
                        cb.isFalse(extRoot.get("groupFlag")),
                        cb.isNull(extRoot.get("groupFlag"))));
                predicates.add(cb.exists(personalSub));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        PageRequest pageable = PageRequest.of(
            request.springPageIndex(),
            request.getBoundedPageSize(),
            Sort.by(Sort.Direction.DESC, FieldNameConstants.START_TIME));
        Page<Encounter> page = encounterQueryService.pageEncounterEntities(spec, pageable);

        List<ExamHistoryRowDto> rows = new ArrayList<>();
        List<Long> patientIds = new ArrayList<>();
        for (Encounter encounter : page.getContent()) {
            ExamHistoryRowDto row = toRow(encounter);
            row.setPatientId(encounter.getPatientId());
            if (encounter.getPatientId() != null) {
                patientIds.add(encounter.getPatientId());
            }
            rows.add(row);
        }
        if (!patientIds.isEmpty()) {
            Map<Long, BatchPatientDetailQueryModel> patientById =
                patientQueryService.batchLoadPatientDetails(patientIds);
            for (ExamHistoryRowDto row : rows) {
                if (row == null || row.getPatientId() == null) {
                    continue;
                }
                BatchPatientDetailQueryModel batch = patientById.get(row.getPatientId());
                if (batch == null
                    || batch.getPatientDetail() == null
                    || batch.getPatientDetail().getPatient() == null) {
                    continue;
                }
                PatientCoreQueryModel core = batch.getPatientDetail().getPatient();
                row.setPatientName(core.getName());
                row.setIdCardNo(core.getIdCard());
                row.setPhone(core.getPhone());
            }
        }
        enrichChargeAndCancelable(rows);
        return PagedResultBuilder.build(new PageExamRecordsResponse(), rows, page);
    }

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
        enrichChargeAndCancelable(rows);
        response.setRows(rows);
        return response;
    }

    /**
     * 取消未收费体检（对齐挂号历史未收费取消：作废就诊后列表不再展示）。
     */
    @Transactional(rollbackFor = Exception.class)
    public CancelPhysicalExamResponse cancelPhysicalExam(@Valid CancelPhysicalExamRequest request) {
        // Gate
        if (request == null || request.getEncounterId() == null) {
            throw new ValidationException(PeisExamHistoryConstants.MSG_ENCOUNTER_REQUIRED);
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_HIST_INVALID_ARGUMENT, PeisExamHistoryConstants.MSG_LOGIN);
        // Cmd
        physicalExamHistoryCancelExecutor.cancel(request.getEncounterId(), operatorId);
        // Asm
        CancelPhysicalExamResponse response = new CancelPhysicalExamResponse();
        response.setEncounterId(request.getEncounterId());
        response.setCancelled(Boolean.TRUE);
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
            var patientOpt = patientQueryService.findOptionalPatientEntityById(request.getPatientId());
            if (patientOpt.isEmpty()) {
                throw new ValidationException("患者不存在");
            }
            Optional<PeisCampusIdentityView> campus = resolveCampusByPatient(request.getPatientId());
            if (campus.isPresent()) {
                return ResolvedIdentity.fromCampus(campus.get());
            }
            String patientName = patientOpt.get().getName();
            return new ResolvedIdentity(
                request.getPatientId(), request.getStudentNo(), request.getEmployeeNo(), "PATIENT", patientName);
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

    private void enrichChargeAndCancelable(List<ExamHistoryRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> encounterIds = new ArrayList<>();
        for (ExamHistoryRowDto row : rows) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        Set<Long> paidEncounterIds = new HashSet<>();
        Set<Long> blockingEncounterIds = new HashSet<>();
        if (!encounterIds.isEmpty()) {
            ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
            cmd.setEncounterIds(encounterIds);
            List<ChargeItem> items = chargeItemQueryService.listChargeItemsByEncounterIds(cmd);
            for (ChargeItem item : items) {
                if (item == null || item.getEncounterId() == null) {
                    continue;
                }
                String status = item.getStatusEnum();
                if (ChargeItemStatus.SETTLED.getCode().equals(status)) {
                    paidEncounterIds.add(item.getEncounterId());
                    blockingEncounterIds.add(item.getEncounterId());
                }
                if (ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(status)
                    || ChargeItemStatus.REFUNDING.getCode().equals(status)) {
                    blockingEncounterIds.add(item.getEncounterId());
                }
            }
        }
        for (ExamHistoryRowDto row : rows) {
            if (row == null) {
                continue;
            }
            boolean paid = paidEncounterIds.contains(row.getEncounterId());
            boolean blocking = blockingEncounterIds.contains(row.getEncounterId());
            row.setPaid(Boolean.valueOf(paid));
            row.setStatusText(toStatusText(row.getStatusEnum()));
            boolean inProgress = EncounterStatus.IN_PROGRESS.getCode().equals(row.getStatusEnum());
            boolean notFinal = !Boolean.TRUE.equals(row.getFinalReport());
            if (inProgress && !blocking && notFinal) {
                row.setCancelable(Boolean.TRUE);
            } else {
                row.setCancelable(Boolean.FALSE);
            }
        }
    }

    private static String toStatusText(String statusEnum) {
        if (statusEnum == null || statusEnum.isEmpty()) {
            return "";
        }
        if (EncounterStatus.IN_PROGRESS.getCode().equals(statusEnum)) {
            return PeisExamHistoryConstants.STATUS_TEXT_IN_PROGRESS;
        }
        EncounterStatus status = EnumUtil.fromCode(EncounterStatus.class, statusEnum);
        if (status == null) {
            return statusEnum;
        }
        return status.getDisplay();
    }

    private static boolean isCancelled(Encounter encounter) {
        String status = encounter.getStatusEnum();
        return EncounterStatus.CANCELLED.getCode().equals(status)
            || EncounterStatus.ENTERED_IN_ERROR.getCode().equals(status);
    }

    private Set<Long> resolvePatientIdsByKeyword(String keyword) {
        PatientPagedQuery query = new PatientPagedQuery();
        query.setKeyword(keyword);
        query.setPageNo(1);
        query.setPageSize(100);
        Page<PatientPageItemQueryModel> page = patientQueryService.searchPatientsPaged(query);
        Set<Long> ids = new HashSet<>();
        for (PatientPageItemQueryModel row : page.getContent()) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
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

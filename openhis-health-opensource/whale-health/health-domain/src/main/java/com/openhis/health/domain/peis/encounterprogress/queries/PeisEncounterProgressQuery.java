/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.encounterprogress.queries;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.clinical.diagnosticreport.entity.DiagnosticReport;
import com.openhis.health.domain.peis.encounterext.entity.PeisEncounterExt;
import com.openhis.health.domain.peis.encounterprogress.command.SignalProgressChangedCommand;
import com.openhis.health.domain.peis.encounterprogress.queries.model.DeptDeskRowModel;
import com.openhis.health.domain.peis.encounterprogress.queries.model.MasterReviewRowModel;
import com.openhis.health.domain.peis.encounterprogress.queries.model.PrintableReportRowModel;
import com.openhis.health.domain.peis.encounterprogress.queries.query.DeptDeskQuery;
import com.openhis.health.domain.peis.encounterprogress.queries.query.MasterReviewQuery;
import com.openhis.health.domain.peis.encounterprogress.queries.query.ReportPrintQuery;
import com.openhis.health.domain.peis.servicerequestext.entity.PeisServiceRequestExt;
import com.openhis.health.domain.share.constants.DiagnosticReportStatusConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.PeisExecStatusConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.events.custom.peisencounterprogress.PeisEncounterProgressChangedEvent;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 体检进度只读查询与变更信号（DM-PEIS-008）。
 *
 * <p>无本聚合持久化表；跨表只读 join 经 {@link EntityManager} Criteria 实现 DES-01 / MAS-01 子集。</p>
 */
@Service
@RequiredArgsConstructor
public class PeisEncounterProgressQuery {

    private final EntityManager entityManager;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 分科诊台待检列表（DES-01）。
     *
     * @param query 过滤与分页条件
     * @return 分页待检行
     */
    public Page<DeptDeskRowModel> searchPendingDeptDesk(DeptDeskQuery query) {
        // 构建 Criteria/SQL（含软删）→ 分页 count+content → 组装 Model
        DeptDeskQuery safeQuery = query;
        if (safeQuery == null) {
            safeQuery = new DeptDeskQuery();
        }
        int pageIndex = safeQuery.springPageIndex();
        int pageSize = safeQuery.getBoundedPageSize();
        Pageable pageable = PageRequest.of(pageIndex, pageSize);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Encounter> countEnc = countQuery.from(Encounter.class);
        Root<ServiceRequest> countSr = countQuery.from(ServiceRequest.class);
        Root<PeisServiceRequestExt> countExt = countQuery.from(PeisServiceRequestExt.class);
        Root<PeisEncounterExt> countPee = countQuery.from(PeisEncounterExt.class);
        Root<Patient> countPat = countQuery.from(Patient.class);
        Root<ActivityDefinition> countAd = countQuery.from(ActivityDefinition.class);
        List<Predicate> countPredicates = buildDeptDeskPredicates(
            cb, safeQuery, countEnc, countSr, countExt, countPee, countPat, countAd);
        countQuery.select(cb.countDistinct(countSr.get("id")));
        countQuery.where(countPredicates.toArray(Predicate[]::new));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        CriteriaQuery<Object[]> dataQuery = cb.createQuery(Object[].class);
        Root<Encounter> enc = dataQuery.from(Encounter.class);
        Root<ServiceRequest> sr = dataQuery.from(ServiceRequest.class);
        Root<PeisServiceRequestExt> ext = dataQuery.from(PeisServiceRequestExt.class);
        Root<PeisEncounterExt> pee = dataQuery.from(PeisEncounterExt.class);
        Root<Patient> pat = dataQuery.from(Patient.class);
        Root<ActivityDefinition> ad = dataQuery.from(ActivityDefinition.class);

        List<Predicate> dataPredicates = buildDeptDeskPredicates(cb, safeQuery, enc, sr, ext, pee, pat, ad);
        dataQuery.multiselect(
            enc.get("id"),
            enc.get("patientId"),
            pat.get("name"),
            pee.get("busNo"),
            sr.get("id"),
            ad.get("name"),
            ext.get("execStatusEnum"),
            ext.get("lisDispatchEnum"));
        dataQuery.where(dataPredicates.toArray(Predicate[]::new));
        dataQuery.orderBy(cb.asc(pat.get("name")), cb.asc(sr.get("id")));

        TypedQuery<Object[]> typedQuery = entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        List<Object[]> rows = typedQuery.getResultList();
        List<DeptDeskRowModel> models = mapDeptDeskRows(rows);
        return new PageImpl<>(models, pageable, total);
    }

    /**
     * 总检诊台待总检列表（MAS-01 / BR-MAS-01）。
     *
     * <p>条件：PE 在检；存在未取消 SR；全部 SR ext 为 checked/abandoned；尚无 final 总检报告。</p>
     *
     * @param query 过滤与分页条件
     * @return 分页待总检行
     */
    public Page<MasterReviewRowModel> searchPendingMasterReview(MasterReviewQuery query) {
        // 构建 Criteria（分科完成 + 未定稿）→ 分页 → 组装完成比
        MasterReviewQuery safeQuery = query;
        if (safeQuery == null) {
            safeQuery = new MasterReviewQuery();
        }
        int pageIndex = safeQuery.springPageIndex();
        int pageSize = safeQuery.getBoundedPageSize();
        Pageable pageable = PageRequest.of(pageIndex, pageSize);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Encounter> countEnc = countQuery.from(Encounter.class);
        Root<PeisEncounterExt> countPee = countQuery.from(PeisEncounterExt.class);
        Root<Patient> countPat = countQuery.from(Patient.class);
        List<Predicate> countPredicates = buildMasterReviewPredicates(cb, safeQuery, countEnc, countPee, countPat);
        countQuery.select(cb.countDistinct(countEnc.get("id")));
        countQuery.where(countPredicates.toArray(Predicate[]::new));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        CriteriaQuery<Object[]> dataQuery = cb.createQuery(Object[].class);
        Root<Encounter> enc = dataQuery.from(Encounter.class);
        Root<PeisEncounterExt> pee = dataQuery.from(PeisEncounterExt.class);
        Root<Patient> pat = dataQuery.from(Patient.class);
        List<Predicate> dataPredicates = buildMasterReviewPredicates(cb, safeQuery, enc, pee, pat);
        dataQuery.multiselect(
            enc.get("id"),
            pat.get("name"),
            pee.get("busNo"),
            enc.get("startTime"));
        dataQuery.where(dataPredicates.toArray(Predicate[]::new));
        dataQuery.orderBy(cb.asc(enc.get("startTime")), cb.asc(enc.get("id")));

        TypedQuery<Object[]> typedQuery = entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        List<Object[]> rows = typedQuery.getResultList();

        List<Long> encounterIds = new ArrayList<>();
        for (Object[] row : rows) {
            encounterIds.add((Long) row[0]);
        }
        Map<Long, String> ratioByEncounter = loadDeptCompleteRatios(encounterIds);
        List<MasterReviewRowModel> models = new ArrayList<>();
        for (Object[] row : rows) {
            MasterReviewRowModel model = new MasterReviewRowModel();
            Long encounterId = (Long) row[0];
            model.setEncounterId(encounterId);
            model.setPatientName((String) row[1]);
            model.setBusNo((String) row[2]);
            model.setCheckInTime((LocalDateTime) row[3]);
            String ratio = ratioByEncounter.get(encounterId);
            if (ratio == null) {
                model.setDeptCompleteRatio("0/0");
            } else {
                model.setDeptCompleteRatio(ratio);
            }
            models.add(model);
        }
        return new PageImpl<>(models, pageable, total);
    }

    /**
     * 可打印报告列表（REP-01）：DR.status=final，join PE Encounter + ext + Patient。
     *
     * @param query    过滤与分页条件
     * @param pageable 分页（可为 null，回落 query 内分页键）
     * @return 分页可打印行
     */
    public Page<PrintableReportRowModel> searchPrintableReports(ReportPrintQuery query, Pageable pageable) {
        // 构建 Criteria（final DR + PE）→ 分页 count+content → 组装 Model
        ReportPrintQuery safeQuery = query;
        if (safeQuery == null) {
            safeQuery = new ReportPrintQuery();
        }
        Pageable effectivePageable = pageable;
        if (effectivePageable == null) {
            effectivePageable = PageRequest.of(safeQuery.springPageIndex(), safeQuery.getBoundedPageSize());
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<DiagnosticReport> countDr = countQuery.from(DiagnosticReport.class);
        Root<Encounter> countEnc = countQuery.from(Encounter.class);
        Root<PeisEncounterExt> countPee = countQuery.from(PeisEncounterExt.class);
        Root<Patient> countPat = countQuery.from(Patient.class);
        List<Predicate> countPredicates = buildPrintableReportPredicates(
            cb, safeQuery, countDr, countEnc, countPee, countPat);
        countQuery.select(cb.countDistinct(countDr.get("id")));
        countQuery.where(countPredicates.toArray(Predicate[]::new));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        CriteriaQuery<Object[]> dataQuery = cb.createQuery(Object[].class);
        Root<DiagnosticReport> dr = dataQuery.from(DiagnosticReport.class);
        Root<Encounter> enc = dataQuery.from(Encounter.class);
        Root<PeisEncounterExt> pee = dataQuery.from(PeisEncounterExt.class);
        Root<Patient> pat = dataQuery.from(Patient.class);
        List<Predicate> dataPredicates = buildPrintableReportPredicates(cb, safeQuery, dr, enc, pee, pat);
        dataQuery.multiselect(
            dr.get("id"),
            enc.get("id"),
            enc.get("patientId"),
            pat.get("name"),
            pee.get("busNo"),
            dr.get("finalizedAt"),
            dr.get("pdfStorageKey"));
        dataQuery.where(dataPredicates.toArray(Predicate[]::new));
        dataQuery.orderBy(cb.desc(dr.get("finalizedAt")), cb.desc(dr.get("id")));

        TypedQuery<Object[]> typedQuery = entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult((int) effectivePageable.getOffset());
        typedQuery.setMaxResults(effectivePageable.getPageSize());
        List<Object[]> rows = typedQuery.getResultList();
        List<PrintableReportRowModel> models = mapPrintableReportRows(rows);
        return new PageImpl<>(models, effectivePageable, total);
    }

    /**
     * 发布进度变更信号（不写库；MAS 订阅待扩展）。
     *
     * @param cmd 信号命令
     */
    public void signalProgressChanged(SignalProgressChangedCommand cmd) {
        // 待 MAS 消费方完善：当前仅发布事件供后续缓存/队列订阅
        if (cmd == null || cmd.getEncounterId() == null) {
            return;
        }
        domainEventPublisher.publish(new PeisEncounterProgressChangedEvent(
            cmd.getEncounterId(), cmd.getProgressCode(), cmd.getTriggerSource()));
    }

    private List<PrintableReportRowModel> mapPrintableReportRows(List<Object[]> rows) {
        List<PrintableReportRowModel> models = new ArrayList<>();
        for (Object[] row : rows) {
            PrintableReportRowModel model = new PrintableReportRowModel();
            model.setDiagnosticReportId((Long) row[0]);
            model.setEncounterId((Long) row[1]);
            model.setPatientId((Long) row[2]);
            model.setPatientName((String) row[3]);
            model.setBusNo((String) row[4]);
            model.setFinalizedAt((LocalDateTime) row[5]);
            model.setPdfStorageKey((String) row[6]);
            models.add(model);
        }
        return models;
    }

    private List<Predicate> buildPrintableReportPredicates(
        CriteriaBuilder cb,
        ReportPrintQuery query,
        Root<DiagnosticReport> dr,
        Root<Encounter> enc,
        Root<PeisEncounterExt> pee,
        Root<Patient> pat) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(dr.get("encounterId"), enc.get("id")));
        predicates.add(cb.equal(pee.get("encounterId"), enc.get("id")));
        predicates.add(cb.equal(pat.get("id"), enc.get("patientId")));
        predicates.add(cb.isFalse(dr.get(FieldNameConstants.IS_DELETED)));
        predicates.add(cb.isFalse(enc.get(FieldNameConstants.IS_DELETED)));
        predicates.add(cb.isFalse(pee.get(FieldNameConstants.IS_DELETED)));
        predicates.add(cb.isFalse(pat.get(FieldNameConstants.IS_DELETED)));
        predicates.add(cb.equal(enc.get("classEnum"), EncounterClass.PE.getCode()));
        predicates.add(cb.equal(dr.get("statusEnum"), DiagnosticReportStatusConstants.STATUS_FINAL));

        if (query != null) {
            if (query.getOrgId() != null) {
                predicates.add(cb.equal(dr.get("orgId"), query.getOrgId()));
            }
            LocalDateTime start = query.getStartDate();
            LocalDateTime end = query.getEndDate();
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(dr.get("finalizedAt"), start));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(dr.get("finalizedAt"), end));
            }
            if (StringUtils.isNotEmpty(query.getKeyword())) {
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
                predicates.add(cb.or(
                    cb.like(cb.lower(pat.get("name")), like),
                    cb.like(cb.lower(pee.get("busNo")), like)));
            }
        }
        return predicates;
    }

    private List<DeptDeskRowModel> mapDeptDeskRows(List<Object[]> rows) {
        List<DeptDeskRowModel> models = new ArrayList<>();
        for (Object[] row : rows) {
            DeptDeskRowModel model = new DeptDeskRowModel();
            model.setEncounterId((Long) row[0]);
            model.setPatientId((Long) row[1]);
            model.setPatientName((String) row[2]);
            model.setBusNo((String) row[3]);
            model.setServiceRequestId((Long) row[4]);
            model.setActivityDisplay((String) row[5]);
            model.setExecStatusEnum((String) row[6]);
            model.setLisDispatchEnum((String) row[7]);
            models.add(model);
        }
        return models;
    }

    private List<Predicate> buildDeptDeskPredicates(
        CriteriaBuilder cb,
        DeptDeskQuery query,
        Root<Encounter> enc,
        Root<ServiceRequest> sr,
        Root<PeisServiceRequestExt> ext,
        Root<PeisEncounterExt> pee,
        Root<Patient> pat,
        Root<ActivityDefinition> ad) {
        List<Predicate> predicates = new ArrayList<>();
        if (enc == null || sr == null || ext == null || pee == null || pat == null || ad == null) {
            return predicates;
        }

        predicates.add(cb.equal(sr.get("encounterId"), enc.get("id")));
        predicates.add(cb.equal(ext.get("serviceRequestId"), sr.get("id")));
        predicates.add(cb.equal(pee.get("encounterId"), enc.get("id")));
        predicates.add(cb.equal(pat.get("id"), enc.get("patientId")));
        predicates.add(cb.equal(ad.get("id"), sr.get("activityId")));

        predicates.add(cb.isFalse(enc.get(FieldNameConstants.IS_DELETED)));
        predicates.add(cb.isFalse(sr.get(FieldNameConstants.IS_DELETED)));
        predicates.add(cb.isFalse(ext.get(FieldNameConstants.IS_DELETED)));
        predicates.add(cb.isFalse(pee.get(FieldNameConstants.IS_DELETED)));
        predicates.add(cb.isFalse(pat.get(FieldNameConstants.IS_DELETED)));
        predicates.add(cb.isFalse(ad.get(FieldNameConstants.IS_DELETED)));

        predicates.add(cb.equal(enc.get("classEnum"), EncounterClass.PE.getCode()));
        predicates.add(cb.notEqual(sr.get("statusEnum"), RequestStatus.CANCELLED.getCode()));
        predicates.add(cb.equal(ext.get("execStatusEnum"), PeisExecStatusConstants.PENDING));

        if (query != null) {
            if (query.getDeptOrgId() != null) {
                predicates.add(cb.equal(sr.get("orgId"), query.getDeptOrgId()));
            }
            LocalDateTime start = query.getStartDate();
            LocalDateTime end = query.getEndDate();
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(enc.get("startTime"), start));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(enc.get("startTime"), end));
            }
            if (StringUtils.isNotEmpty(query.getKeyword())) {
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
                predicates.add(cb.or(
                    cb.like(cb.lower(pat.get("name")), like),
                    cb.like(cb.lower(pee.get("busNo")), like),
                    cb.like(cb.lower(ad.get("name")), like)));
            }
        }
        return predicates;
    }

    private List<Predicate> buildMasterReviewPredicates(
        CriteriaBuilder cb,
        MasterReviewQuery query,
        Root<Encounter> enc,
        Root<PeisEncounterExt> pee,
        Root<Patient> pat) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(pee.get("encounterId"), enc.get("id")));
        predicates.add(cb.equal(pat.get("id"), enc.get("patientId")));
        predicates.add(cb.isFalse(enc.get(FieldNameConstants.IS_DELETED)));
        predicates.add(cb.isFalse(pee.get(FieldNameConstants.IS_DELETED)));
        predicates.add(cb.isFalse(pat.get(FieldNameConstants.IS_DELETED)));
        predicates.add(cb.equal(enc.get("classEnum"), EncounterClass.PE.getCode()));
        predicates.add(cb.equal(enc.get("statusEnum"), EncounterStatus.IN_PROGRESS.getCode()));

        // 至少存在一条未取消 SR
        Subquery<Long> hasSr = cb.createQuery().subquery(Long.class);
        Root<ServiceRequest> srExists = hasSr.from(ServiceRequest.class);
        hasSr.select(srExists.get("id"));
        hasSr.where(
            cb.equal(srExists.get("encounterId"), enc.get("id")),
            cb.isFalse(srExists.get(FieldNameConstants.IS_DELETED)),
            cb.notEqual(srExists.get("statusEnum"), RequestStatus.CANCELLED.getCode()));
        predicates.add(cb.exists(hasSr));

        // 不存在 pending/deferred 的未取消 SR（BR-MAS-01）
        Subquery<Long> incomplete = cb.createQuery().subquery(Long.class);
        Root<ServiceRequest> srInc = incomplete.from(ServiceRequest.class);
        Root<PeisServiceRequestExt> extInc = incomplete.from(PeisServiceRequestExt.class);
        incomplete.select(srInc.get("id"));
        incomplete.where(
            cb.equal(srInc.get("encounterId"), enc.get("id")),
            cb.equal(extInc.get("serviceRequestId"), srInc.get("id")),
            cb.isFalse(srInc.get(FieldNameConstants.IS_DELETED)),
            cb.isFalse(extInc.get(FieldNameConstants.IS_DELETED)),
            cb.notEqual(srInc.get("statusEnum"), RequestStatus.CANCELLED.getCode()),
            extInc.get("execStatusEnum").in(
                PeisExecStatusConstants.PENDING, PeisExecStatusConstants.DEFERRED));
        predicates.add(cb.not(cb.exists(incomplete)));

        // 尚无 final 总检报告
        Subquery<Long> finalDr = cb.createQuery().subquery(Long.class);
        Root<DiagnosticReport> dr = finalDr.from(DiagnosticReport.class);
        finalDr.select(dr.get("id"));
        finalDr.where(
            cb.equal(dr.get("encounterId"), enc.get("id")),
            cb.isFalse(dr.get(FieldNameConstants.IS_DELETED)),
            cb.equal(dr.get("statusEnum"), DiagnosticReportStatusConstants.STATUS_FINAL));
        predicates.add(cb.not(cb.exists(finalDr)));

        if (query != null) {
            LocalDateTime start = query.getStartDate();
            LocalDateTime end = query.getEndDate();
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(enc.get("startTime"), start));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(enc.get("startTime"), end));
            }
            if (StringUtils.isNotEmpty(query.getKeyword())) {
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
                predicates.add(cb.or(
                    cb.like(cb.lower(pat.get("name")), like),
                    cb.like(cb.lower(pee.get("busNo")), like)));
            }
        }
        return predicates;
    }

    private Map<Long, String> loadDeptCompleteRatios(List<Long> encounterIds) {
        Map<Long, String> ratios = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return ratios;
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<ServiceRequest> sr = query.from(ServiceRequest.class);
        Root<PeisServiceRequestExt> ext = query.from(PeisServiceRequestExt.class);
        query.multiselect(sr.get("encounterId"), ext.get("execStatusEnum"));
        query.where(
            cb.equal(ext.get("serviceRequestId"), sr.get("id")),
            sr.get("encounterId").in(encounterIds),
            cb.isFalse(sr.get(FieldNameConstants.IS_DELETED)),
            cb.isFalse(ext.get(FieldNameConstants.IS_DELETED)),
            cb.notEqual(sr.get("statusEnum"), RequestStatus.CANCELLED.getCode()));
        List<Object[]> rows = entityManager.createQuery(query).getResultList();

        Map<Long, Integer> totalMap = new HashMap<>();
        Map<Long, Integer> doneMap = new HashMap<>();
        for (Object[] row : rows) {
            Long encounterId = (Long) row[0];
            String exec = (String) row[1];
            Integer total = totalMap.get(encounterId);
            if (total == null) {
                total = 0;
            }
            totalMap.put(encounterId, total + 1);
            boolean done = PeisExecStatusConstants.CHECKED.equals(exec)
                || PeisExecStatusConstants.ABANDONED.equals(exec);
            if (done) {
                Integer doneCount = doneMap.get(encounterId);
                if (doneCount == null) {
                    doneCount = 0;
                }
                doneMap.put(encounterId, doneCount + 1);
            }
        }
        for (Long encounterId : encounterIds) {
            Integer total = totalMap.get(encounterId);
            Integer done = doneMap.get(encounterId);
            if (total == null) {
                total = 0;
            }
            if (done == null) {
                done = 0;
            }
            ratios.put(encounterId, done + "/" + total);
        }
        return ratios;
    }
}

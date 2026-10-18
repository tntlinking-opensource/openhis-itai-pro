/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.procedure.support;

import com.zxplat.domain.clinical.procedure.entity.Procedure;
import com.zxplat.domain.clinical.procedure.entity.ProcedurePerformer;
import com.zxplat.domain.clinical.procedure.queries.NurseWorkloadPageQuery;
import com.zxplat.domain.share.constants.ClinicalProcedureConstants;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.EventStatus;
import com.zxplat.domain.share.utils.IdInQueryChunkSupport;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 护士诊疗执行工作量报表（AS-RPT-002）Procedure 根规约：净 COMPLETED + 时间窗 + 可选主执行人收窄。
 *
 * <p>对冲规则与 {@link ProcedureNetExecutionFilterSupport} 一致：存在同表 CANCEL/ABORTED 且
 * {@code refund_id} 指向本行则剔除。汇总、页脚、明细分页<strong>必须</strong>共用本规约（禁止 Java/SQL 双轨）。</p>
 */
public final class NurseWorkloadProcedureReportCriteriaSupport {

    /** JPA 属性：记录时间（{@code cln_procedure.recorded_time}）。 */
    private static final String RECORDED_TIME = "recordedTime";

    /** JPA 属性：申请表物理名（{@code cln_procedure.request_table}）。 */
    private static final String REQUEST_TABLE = "requestTable";

    /**
     * 禁止实例化。
     */
    private NurseWorkloadProcedureReportCriteriaSupport() {
    }

    /**
     * 构建护士净 COMPLETED 执行记录规约（诊疗 {@code wkf_service_request} 锚点）。
     *
     * @param query 预解析 scope + 日期 + 可选护士收窄；无效时返回恒 false 规约
     * @return JPA 规约；{@code query} 为 null 时恒 false
     */
    public static Specification<Procedure> buildNetCompletedSpecification(NurseWorkloadPageQuery query) {
        return (root, criteriaQuery, cb) -> {
            if (query == null
                || query.getDateFrom() == null
                || query.getDateTo() == null
                || query.getResolvedEncounterScope() == null
                || query.getResolvedEncounterScope().isEmpty()) {
                return cb.disjunction();
            }
            // Gate：科室下无护士须空结果，禁止回落全院
            if (query.getPractitionerId() == null
                && query.getPractitionerIds() != null
                && query.getPractitionerIds().isEmpty()) {
                return cb.disjunction();
            }
            LocalDateTime recordedFrom = query.getDateFrom().atStartOfDay();
            LocalDateTime recordedTo = LocalDateTime.of(query.getDateTo(), LocalTime.of(23, 59, 59, 999_999_999));
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), EventStatus.COMPLETED.getCode()));
            predicates.add(cb.equal(root.get(REQUEST_TABLE), TableNameConstants.WKF_SERVICE_REQUEST));
            predicates.add(cb.greaterThanOrEqualTo(root.get(RECORDED_TIME), recordedFrom));
            predicates.add(cb.lessThanOrEqualTo(root.get(RECORDED_TIME), recordedTo));
            predicates.add(buildEncounterScopePredicate(root, cb, query.getResolvedEncounterScope()));
            predicates.add(buildNotOffsetByCancelPredicate(root, criteriaQuery, cb));
            Predicate performerPredicate = buildAttributedPerformerPredicate(root, criteriaQuery, cb, query);
            if (performerPredicate != null) {
                predicates.add(performerPredicate);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 就诊 scope IN 谓词（超长按 {@link GeneralSharedConstants#ID_IN_QUERY_CHUNK_SIZE} 拆 OR）。
     *
     * @param root           Procedure 根
     * @param cb             CriteriaBuilder
     * @param encounterScope 预解析就诊主键
     * @return encounter_id 命中谓词
     */
    private static Predicate buildEncounterScopePredicate(
        Root<Procedure> root,
        CriteriaBuilder cb,
        Collection<Long> encounterScope) {
        List<Long> encounterIds = IdInQueryChunkSupport.distinctNonNullIds(encounterScope);
        if (encounterIds.isEmpty()) {
            return cb.disjunction();
        }
        int chunkSize = GeneralSharedConstants.ID_IN_QUERY_CHUNK_SIZE;
        if (encounterIds.size() <= chunkSize) {
            return root.get(FieldNameConstants.ENCOUNTER_ID).in(encounterIds);
        }
        List<Predicate> chunkPredicates = new ArrayList<>();
        for (int fromIndex = 0; fromIndex < encounterIds.size(); fromIndex += chunkSize) {
            int toIndex = Math.min(fromIndex + chunkSize, encounterIds.size());
            chunkPredicates.add(root.get(FieldNameConstants.ENCOUNTER_ID).in(encounterIds.subList(fromIndex, toIndex)));
        }
        return cb.or(chunkPredicates.toArray(new Predicate[0]));
    }

    /**
     * 净 COMPLETED：不存在同表 CANCEL/ABORTED 且 refund_id 指向本行的对冲行。
     *
     * @param root          COMPLETED 根
     * @param criteriaQuery 外层查询
     * @param cb            CriteriaBuilder
     * @return NOT EXISTS 谓词
     */
    private static Predicate buildNotOffsetByCancelPredicate(
        Root<Procedure> root,
        CriteriaQuery<?> criteriaQuery,
        CriteriaBuilder cb) {
        Subquery<Long> cancelSubquery = criteriaQuery.subquery(Long.class);
        Root<Procedure> cancelRoot = cancelSubquery.from(Procedure.class);
        cancelSubquery.select(cancelRoot.get(FieldNameConstants.ID));
        List<Predicate> cancelPredicates = new ArrayList<>();
        cancelPredicates.add(cb.isFalse(cancelRoot.get(FieldNameConstants.IS_DELETED)));
        cancelPredicates.add(cb.equal(cancelRoot.get(REQUEST_TABLE), root.get(REQUEST_TABLE)));
        cancelPredicates.add(cb.equal(cancelRoot.get(FieldNameConstants.REFUND_ID), root.get(FieldNameConstants.ID)));
        Predicate cancelStatus = cb.or(
            cb.equal(cancelRoot.get(FieldNameConstants.STATUS_ENUM), EventStatus.CANCEL.getCode()),
            cb.equal(
                cancelRoot.get(FieldNameConstants.STATUS_ENUM),
                GeneralSharedConstants.EVENT_STATUS_LEGACY_ABORTED_CODE));
        cancelPredicates.add(cancelStatus);
        cancelSubquery.where(cancelPredicates.toArray(new Predicate[0]));
        return cb.not(cb.exists(cancelSubquery));
    }

    /**
     * 主执行人收窄（PRIMARY 优先，否则首条执行人；与 {@code mapPrimaryPractitionerIdByProcedureIds} 同口径）。
     *
     * @param root          Procedure 根
     * @param criteriaQuery 外层查询
     * @param cb            CriteriaBuilder
     * @param query         护士筛选；未设护士时返回 null（不过滤）
     * @return 执行人谓词；无护士筛选时 null
     */
    private static Predicate buildAttributedPerformerPredicate(
        Root<Procedure> root,
        CriteriaQuery<?> criteriaQuery,
        CriteriaBuilder cb,
        NurseWorkloadPageQuery query) {
        if (query.getPractitionerId() == null && query.getPractitionerIds() == null) {
            return null;
        }
        Set<Long> allowedPractitionerIds = resolveAllowedPractitionerIds(query);
        if (allowedPractitionerIds.isEmpty()) {
            return cb.disjunction();
        }
        Subquery<Long> primarySubquery = criteriaQuery.subquery(Long.class);
        Root<ProcedurePerformer> primaryRoot = primarySubquery.from(ProcedurePerformer.class);
        primarySubquery.select(primaryRoot.get(FieldNameConstants.ID));
        primarySubquery.where(
            cb.isFalse(primaryRoot.get(FieldNameConstants.IS_DELETED)),
            cb.equal(primaryRoot.get("procedureId"), root.get(FieldNameConstants.ID)),
            cb.equal(
                primaryRoot.get("functionCode"),
                ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_PRIMARY),
            primaryRoot.get("practitionerId").in(allowedPractitionerIds));
        Subquery<Long> anyPrimarySubquery = criteriaQuery.subquery(Long.class);
        Root<ProcedurePerformer> anyPrimaryRoot = anyPrimarySubquery.from(ProcedurePerformer.class);
        anyPrimarySubquery.select(anyPrimaryRoot.get(FieldNameConstants.ID));
        anyPrimarySubquery.where(
            cb.isFalse(anyPrimaryRoot.get(FieldNameConstants.IS_DELETED)),
            cb.equal(anyPrimaryRoot.get("procedureId"), root.get(FieldNameConstants.ID)),
            cb.equal(
                anyPrimaryRoot.get("functionCode"),
                ClinicalProcedureConstants.PROCEDURE_PERFORMER_FUNCTION_PRIMARY));
        Subquery<Long> minPerformerIdSubquery = criteriaQuery.subquery(Long.class);
        Root<ProcedurePerformer> minPerformerRoot = minPerformerIdSubquery.from(ProcedurePerformer.class);
        minPerformerIdSubquery.select(cb.min(minPerformerRoot.get(FieldNameConstants.ID)));
        minPerformerIdSubquery.where(
            cb.isFalse(minPerformerRoot.get(FieldNameConstants.IS_DELETED)),
            cb.equal(minPerformerRoot.get("procedureId"), root.get(FieldNameConstants.ID)));
        Subquery<Long> fallbackSubquery = criteriaQuery.subquery(Long.class);
        Root<ProcedurePerformer> fallbackRoot = fallbackSubquery.from(ProcedurePerformer.class);
        fallbackSubquery.select(fallbackRoot.get(FieldNameConstants.ID));
        fallbackSubquery.where(
            cb.isFalse(fallbackRoot.get(FieldNameConstants.IS_DELETED)),
            cb.equal(fallbackRoot.get("procedureId"), root.get(FieldNameConstants.ID)),
            fallbackRoot.get("practitionerId").in(allowedPractitionerIds),
            cb.equal(fallbackRoot.get(FieldNameConstants.ID), minPerformerIdSubquery));
        Predicate primaryHit = cb.exists(primarySubquery);
        Predicate noPrimary = cb.not(cb.exists(anyPrimarySubquery));
        Predicate fallbackHit = cb.exists(fallbackSubquery);
        return cb.or(primaryHit, cb.and(noPrimary, fallbackHit));
    }

    /**
     * 解析允许计入的护士主键集合（单护士优先；否则科室护士集合）。
     *
     * @param query 护士工作量查询
     * @return 非空允许集合；无收窄时空集
     */
    private static Set<Long> resolveAllowedPractitionerIds(NurseWorkloadPageQuery query) {
        if (query.getPractitionerId() != null) {
            return Collections.singleton(query.getPractitionerId());
        }
        Set<Long> allowed = new HashSet<>();
        if (query.getPractitionerIds() != null) {
            for (Long practitionerId : query.getPractitionerIds()) {
                if (practitionerId != null) {
                    allowed.add(practitionerId);
                }
            }
        }
        return allowed;
    }
}

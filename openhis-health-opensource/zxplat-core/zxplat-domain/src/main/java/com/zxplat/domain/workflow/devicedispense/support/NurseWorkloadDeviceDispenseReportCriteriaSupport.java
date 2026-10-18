/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.support;

import com.zxplat.domain.clinical.procedure.queries.NurseWorkloadPageQuery;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.share.utils.IdInQueryChunkSupport;
import com.zxplat.domain.workflow.devicedispense.entity.DeviceDispense;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
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
 * 护士耗材使用量报表（AS-RPT-002）DeviceDispense 根规约：状态 + 业务时间窗 + 归属护士收窄。
 *
 * <p>业务时间与归属护士规则对齐 {@code DeviceDispenseQueryService#resolveNurseConsumableBusinessTime}
 * / {@code resolveNurseConsumableAttributedPractitionerId}。汇总、页脚、明细分页<strong>必须</strong>共用本规约。</p>
 */
public final class NurseWorkloadDeviceDispenseReportCriteriaSupport {

    /** JPA 属性：备药时间。 */
    private static final String PREPARE_TIME = "prepareTime";

    /** JPA 属性：发药时间。 */
    private static final String DISPENSE_TIME = "dispenseTime";

    /** JPA 属性：备药人。 */
    private static final String PREPARER_ID = "preparerId";

    /** JPA 属性：发药执行人。 */
    private static final String PERFORMER_ID = "performerId";

    /**
     * 禁止实例化。
     */
    private NurseWorkloadDeviceDispenseReportCriteriaSupport() {
    }

    /**
     * 构建护士耗材候选发放规约。
     *
     * @param query 预解析 scope + 日期 + 扣库开关 + 可选护士收窄
     * @return JPA 规约；无效 scope 时恒 false
     */
    public static Specification<DeviceDispense> buildCandidateSpecification(NurseWorkloadPageQuery query) {
        return (root, criteriaQuery, cb) -> {
            if (query == null
                || query.getDateFrom() == null
                || query.getDateTo() == null
                || query.getResolvedEncounterScope() == null
                || query.getResolvedEncounterScope().isEmpty()) {
                return cb.disjunction();
            }
            if (query.getPractitionerId() == null
                && query.getPractitionerIds() != null
                && query.getPractitionerIds().isEmpty()) {
                return cb.disjunction();
            }
            LocalDateTime windowFrom = query.getDateFrom().atStartOfDay();
            LocalDateTime windowTo = LocalDateTime.of(query.getDateTo(), LocalTime.of(23, 59, 59, 999_999_999));
            Boolean deductEnabled = query.getDeviceAutoInventoryDeductEnabled();
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(
                root.get(FieldNameConstants.STATUS_ENUM),
                resolveIncludedStatusCode(deductEnabled)));
            predicates.add(buildEncounterScopePredicate(root, cb, query.getResolvedEncounterScope()));
            Expression<LocalDateTime> businessTime = resolveBusinessTimeExpression(root, cb, deductEnabled);
            predicates.add(cb.isNotNull(businessTime));
            predicates.add(cb.greaterThanOrEqualTo(businessTime, windowFrom));
            predicates.add(cb.lessThanOrEqualTo(businessTime, windowTo));
            Predicate performerPredicate = buildAttributedPractitionerPredicate(root, cb, query, deductEnabled);
            if (performerPredicate != null) {
                predicates.add(performerPredicate);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 解析业务时间表达式（扣库开：dispense_time 优先，否则 prepare_time；关：prepare_time）。
     *
     * @param root           DeviceDispense 根
     * @param cb             CriteriaBuilder
     * @param deductEnabled  扣库开关
     * @return 业务时间表达式
     */
    public static Expression<LocalDateTime> resolveBusinessTimeExpression(
        Root<DeviceDispense> root,
        CriteriaBuilder cb,
        Boolean deductEnabled) {
        if (Boolean.TRUE.equals(deductEnabled)) {
            return cb.coalesce(root.get(DISPENSE_TIME), root.get(PREPARE_TIME));
        }
        return root.get(PREPARE_TIME);
    }

    /**
     * 解析纳入状态码（扣库开=COMPLETED；关=PREPARED）。
     *
     * @param deductEnabled 扣库开关
     * @return 状态 code
     */
    public static String resolveIncludedStatusCode(Boolean deductEnabled) {
        if (Boolean.TRUE.equals(deductEnabled)) {
            return DispenseStatus.COMPLETED.getCode();
        }
        return DispenseStatus.PREPARED.getCode();
    }

    /**
     * 就诊 scope IN 谓词（超长按分批 OR）。
     *
     * @param root           DeviceDispense 根
     * @param cb             CriteriaBuilder
     * @param encounterScope 预解析就诊主键
     * @return encounter_id 命中谓词
     */
    private static Predicate buildEncounterScopePredicate(
        Root<DeviceDispense> root,
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
     * 归属护士收窄（扣库开=performer_id；关=preparer_id）。
     *
     * @param root          DeviceDispense 根
     * @param cb            CriteriaBuilder
     * @param query         护士筛选
     * @param deductEnabled 扣库开关
     * @return 归属护士谓词；无护士筛选时 null
     */
    private static Predicate buildAttributedPractitionerPredicate(
        Root<DeviceDispense> root,
        CriteriaBuilder cb,
        NurseWorkloadPageQuery query,
        Boolean deductEnabled) {
        if (query.getPractitionerId() == null && query.getPractitionerIds() == null) {
            return null;
        }
        Set<Long> allowedPractitionerIds = resolveAllowedPractitionerIds(query);
        if (allowedPractitionerIds.isEmpty()) {
            return cb.disjunction();
        }
        if (Boolean.TRUE.equals(deductEnabled)) {
            return root.get(PERFORMER_ID).in(allowedPractitionerIds);
        }
        return root.get(PREPARER_ID).in(allowedPractitionerIds);
    }

    /**
     * 解析允许计入的护士主键集合。
     *
     * @param query 护士工作量查询
     * @return 允许集合
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

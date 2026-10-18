/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.audit.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.chs.domain.audit.entity.YbDetailAuditFeedback;
import com.zxplat.plugin.chs.domain.audit.entity.YbDetailAuditHit;
import com.zxplat.plugin.chs.domain.audit.entity.YbDetailAuditHitLine;
import com.zxplat.plugin.chs.domain.audit.queries.DetailAuditStatsPageQuery;
import com.zxplat.plugin.chs.domain.audit.repository.YbDetailAuditFeedbackRepository;
import com.zxplat.plugin.chs.domain.audit.repository.YbDetailAuditHitLineRepository;
import com.zxplat.plugin.chs.domain.audit.repository.YbDetailAuditHitRepository;
import com.zxplat.plugin.chs.domain.audit.result.DetailAuditStatsSummary;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 明细审核违规统计查询（只读；不调平台）。
 */
@Service
@RequiredArgsConstructor
public class YbDetailAuditStatsQueryService {

    private final YbDetailAuditHitRepository hitRepository;
    private final YbDetailAuditHitLineRepository hitLineRepository;
    private final YbDetailAuditFeedbackRepository feedbackRepository;

    /**
     * 分页查询违规头（创建时间倒序）。
     *
     * @param tenantId 租户
     * @param query    筛选条件
     * @return 分页结果
     */
    public Page<YbDetailAuditHit> page(Long tenantId, DetailAuditStatsPageQuery query) {
        DetailAuditStatsPageQuery safe = query == null ? new DetailAuditStatsPageQuery() : query;
        int pageNo = safe.getPageNo() != null && safe.getPageNo() > 0 ? safe.getPageNo() : 1;
        int pageSize = safe.getPageSize() != null && safe.getPageSize() > 0 ? safe.getPageSize() : 20;
        Pageable pageable = PageRequest.of(
            Math.max(pageNo - 1, 0),
            Math.min(Math.max(pageSize, 1), 200),
            Sort.by(Sort.Order.desc("createdAt")));
        return hitRepository.findAll(buildSpec(tenantId, safe), pageable);
    }

    /**
     * 同筛选条件下的汇总条。
     *
     * @param tenantId 租户
     * @param query    筛选条件
     * @return 汇总
     */
    public DetailAuditStatsSummary summarize(Long tenantId, DetailAuditStatsPageQuery query) {
        DetailAuditStatsPageQuery safe = query == null ? new DetailAuditStatsPageQuery() : query;
        List<YbDetailAuditHit> rows = hitRepository.findAll(buildSpec(tenantId, safe));
        Set<String> mdtrtIds = new HashSet<>();
        Set<String> doctors = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (YbDetailAuditHit hit : rows) {
            if (hit == null) {
                continue;
            }
            String mdtrt = trimToNull(hit.getMdtrtId());
            if (mdtrt != null) {
                mdtrtIds.add(mdtrt);
            }
            String dr = trimToNull(hit.getDrCodg());
            if (dr == null) {
                dr = trimToNull(hit.getDrName());
            }
            if (dr != null) {
                doctors.add(dr);
            }
            if (hit.getVolaAmt() != null) {
                sum = sum.add(hit.getVolaAmt());
            }
        }
        return new DetailAuditStatsSummary()
            .setHitCount(rows.size())
            .setMdtrtCount(mdtrtIds.size())
            .setDoctorCount(doctors.size())
            .setVolaAmtSum(sum);
    }

    /**
     * 违规明细行（抽屉）。
     *
     * @param tenantId 租户
     * @param jrId     违规头 ID
     * @return 明细
     */
    public List<YbDetailAuditHitLine> listLines(Long tenantId, String jrId) {
        String id = trimToNull(jrId);
        if (id == null) {
            return List.of();
        }
        return hitLineRepository.findByTenantIdAndJrIdAndIsDeletedFalseOrderByIdAsc(tenantId, id);
    }

    /**
     * 批量取各违规头末次反馈处置方式。
     *
     * @param tenantId 租户
     * @param jrIds    违规头 ID
     * @return jrId → 末次 dspoWay
     */
    public Map<String, String> latestDspoWayByJrId(Long tenantId, Collection<String> jrIds) {
        Map<String, String> map = new HashMap<>();
        if (jrIds == null || jrIds.isEmpty()) {
            return map;
        }
        List<YbDetailAuditFeedback> rows =
            feedbackRepository.findByTenantIdAndJrIdInAndIsDeletedFalseOrderByCreatedAtDesc(
                tenantId, jrIds);
        for (YbDetailAuditFeedback row : rows) {
            if (row == null) {
                continue;
            }
            String jrId = trimToNull(row.getJrId());
            String way = trimToNull(row.getDspoWay());
            if (jrId == null || way == null || map.containsKey(jrId)) {
                continue;
            }
            map.put(jrId, way);
        }
        return map;
    }

    /**
     * 组装未删除 + 租户 + 筛选条件的 JPA Specification（创建时间/医生/科室/场景/严重度/关键字/末次处置）。
     *
     * @param tenantId 租户
     * @param safe     已非空的查询条件
     * @return Specification
     */
    private Specification<YbDetailAuditHit> buildSpec(Long tenantId, DetailAuditStatsPageQuery safe) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 租户隔离 + 软删
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            // 创建时间窗（落库时刻，非就诊时间）
            if (safe.getCreatedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), safe.getCreatedFrom()));
            }
            if (safe.getCreatedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), safe.getCreatedTo()));
            }
            String drCodg = trimToNull(safe.getDrCodg());
            if (drCodg != null) {
                predicates.add(cb.equal(root.get("drCodg"), drCodg));
            }
            String drNameKw = trimToNull(safe.getDrNameKeyword());
            if (drNameKw != null) {
                predicates.add(cb.like(root.get("drName"), "%" + drNameKw + "%"));
            }
            String deptCodg = trimToNull(safe.getDeptCodg());
            if (deptCodg != null) {
                predicates.add(cb.equal(root.get("deptCodg"), deptCodg));
            }
            String trigScen = trimToNull(safe.getTrigScen());
            if (trigScen != null) {
                predicates.add(cb.equal(root.get("trigScen"), trigScen));
            }
            String sevDeg = trimToNull(safe.getSevDeg());
            if (sevDeg != null) {
                predicates.add(cb.equal(root.get("sevDeg"), sevDeg));
            }
            String keyword = trimToNull(safe.getKeyword());
            if (keyword != null) {
                // 规则名或违规内容模糊
                String pattern = "%" + keyword + "%";
                predicates.add(cb.or(
                    cb.like(root.get("ruleName"), pattern),
                    cb.like(root.get("volaCont"), pattern)));
            }
            String dspoWay = trimToNull(safe.getDspoWay());
            if (dspoWay != null && criteriaQuery != null) {
                // 存在同 jr_id 且处置方式匹配的反馈行（按 jr_id 关联；未写 jr_id 的反馈筛不到）
                Subquery<Integer> exists = criteriaQuery.subquery(Integer.class);
                Root<YbDetailAuditFeedback> fb = exists.from(YbDetailAuditFeedback.class);
                exists.select(cb.literal(1));
                exists.where(
                    cb.equal(fb.get("tenantId"), tenantId),
                    cb.isFalse(fb.get("isDeleted")),
                    cb.equal(fb.get("dspoWay"), dspoWay),
                    cb.equal(fb.get("jrId"), root.get("jrId")));
                predicates.add(cb.exists(exists));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

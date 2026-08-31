/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.prescriptionreview.service;

import com.openhis.health.domain.custom.prescriptionreview.entity.PrescriptionReview;
import com.openhis.health.domain.custom.prescriptionreview.queries.PrescriptionReviewPageQuery;
import com.openhis.health.domain.custom.prescriptionreview.queries.model.PrescriptionReviewQueryModel;
import com.openhis.health.domain.custom.prescriptionreview.repository.PrescriptionReviewRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.PrescriptionReviewStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 处方点评读侧查询服务（DM-CUS-015）。
 *
 * <p>职责：按处方号单条/批量取有效点评、主键强加载、点评记录分页筛选。</p>
 * <p>聚合边界：仅本聚合 {@link PrescriptionReviewRepository}；默认过滤软删。</p>
 */
@Service
@RequiredArgsConstructor
public class PrescriptionReviewQueryService {

    private final PrescriptionReviewRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 按处方号查询有效点评。
     *
     * @param prescriptionNo 处方号
     * @return 有则读模型，无则 empty
     */
    public Optional<PrescriptionReviewQueryModel> findActiveByPrescriptionNo(String prescriptionNo) {
        // 空处方号无法定位，直接 empty 避免误查
        if (StringUtils.isBlank(prescriptionNo)) {
            return Optional.empty();
        }
        return repository.findByPrescriptionNoAndIsDeletedFalse(prescriptionNo.trim()).map(this::toModel);
    }

    /**
     * 列出有效点评处方号（供门诊处方查看点评态筛 Pre 预写，B.6.4）。
     *
     * <p>{@code reasonableFlag} 为空时不过滤是否合理；非空时精确匹配。</p>
     *
     * @param reasonableFlag 是否合理；null=不限
     * @return 去重后的处方号列表；无命中为空列表（非 null）
     */
    public List<String> listActivePrescriptionNos(Boolean reasonableFlag) {
        List<PrescriptionReview> rows;
        // 按是否合理收窄有效行；空则拉全部有效点评
        if (reasonableFlag == null) {
            rows = repository.findByIsDeletedFalse();
        } else {
            rows = repository.findByIsDeletedFalseAndReasonableFlag(reasonableFlag);
        }
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> prescriptionNos = new ArrayList<>();
        // 收集非空白处方号，供 MedicationRequest 方号 IN/NOT IN
        for (PrescriptionReview row : rows) {
            if (row == null || StringUtils.isBlank(row.getPrescriptionNo())) {
                continue;
            }
            prescriptionNos.add(row.getPrescriptionNo().trim());
        }
        return prescriptionNos;
    }

    /**
     * 批量按处方号映射有效点评（列表 enrichment）。
     *
     * @param prescriptionNos 处方号列表
     * @return 处方号 → 读模型；空入参返回空 Map
     */
    public Map<String, PrescriptionReviewQueryModel> mapActiveByPrescriptionNos(List<String> prescriptionNos) {
        // 空入参：列表 enrichment 无键可映射，返回空 Map
        if (prescriptionNos == null || prescriptionNos.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> cleaned = new ArrayList<>();
        for (String no : prescriptionNos) {
            // 跳过空白号，避免 IN 条件含空串
            if (StringUtils.isNotEmpty(no)) {
                cleaned.add(no.trim());
            }
        }
        // 清洗后仍空：等同无候选
        if (cleaned.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, PrescriptionReviewQueryModel> map = new HashMap<>();
        int chunkSize = com.openhis.health.domain.share.constants.GeneralSharedConstants.ID_IN_QUERY_CHUNK_SIZE;
        // 方号过多时分批 IN，避免单次超长
        for (int fromIndex = 0; fromIndex < cleaned.size(); fromIndex += chunkSize) {
            int toIndex = Math.min(fromIndex + chunkSize, cleaned.size());
            List<PrescriptionReview> rows =
                repository.findByPrescriptionNoInAndIsDeletedFalse(cleaned.subList(fromIndex, toIndex));
            for (PrescriptionReview row : rows) {
                map.put(row.getPrescriptionNo(), this.toModel(row));
            }
        }
        return map;
    }

    /**
     * 按主键强加载有效点评。
     *
     * @param id 点评主键
     * @return 读模型
     * @throws BusinessException 不存在时
     */
    public PrescriptionReviewQueryModel getById(Long id) {
        // 主键空无法强加载
        if (id == null) {
            throw new BusinessException(ErrorCodeConstants.PRESCRIPTION_REVIEW_INVALID_ARGUMENT, "点评标识不能为空");
        }
        PrescriptionReview row = repository.findByIdAndIsDeletedFalse(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.PRESCRIPTION_REVIEW_NOT_FOUND, "有效点评不存在"));
        return this.toModel(row);
    }

    /**
     * 有效点评分页（点评记录页 / 导出同源）。
     *
     * @param query    筛选条件；可为 null
     * @param pageable 分页；null 时默认第 0 页 size 50、按点评时间倒序
     * @return 分页读模型
     */
    public Page<PrescriptionReviewQueryModel> pageReviews(PrescriptionReviewPageQuery query, Pageable pageable) {
        Pageable effective = pageable;
        // 未传分页：默认第 0 页 size 50、按点评时间倒序，保证导出/列表同源稳定
        if (effective == null || effective.isUnpaged()) {
            effective = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, FieldNameConstants.REVIEWED_AT));
        } else if (effective.getSort().isUnsorted()) {
            // 有页窗无排序：补点评时间倒序，避免库默认序漂移
            effective = PageRequest.of(
                effective.getPageNumber(),
                effective.getPageSize(),
                Sort.by(Sort.Direction.DESC, FieldNameConstants.REVIEWED_AT));
        }

        // 待点评无落库行：筛 PENDING 直接空页
        if (query != null
            && PrescriptionReviewStatus.PENDING.getCode().equals(query.getStatusEnum())) {
            return new PageImpl<>(Collections.emptyList(), effective, 0L);
        }

        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<PrescriptionReview> cq = cb.createQuery(PrescriptionReview.class);
        Root<PrescriptionReview> root = cq.from(PrescriptionReview.class);
        List<Predicate> predicates = this.buildPredicates(root, cb, query);
        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(cb.desc(root.get(FieldNameConstants.REVIEWED_AT)));

        TypedQuery<PrescriptionReview> typedQuery = this.entityManager.createQuery(cq);
        typedQuery.setFirstResult((int) effective.getOffset());
        typedQuery.setMaxResults(effective.getPageSize());
        List<PrescriptionReview> content = typedQuery.getResultList();

        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<PrescriptionReview> countRoot = countCq.from(PrescriptionReview.class);
        countCq.select(cb.count(countRoot));
        countCq.where(this.buildPredicates(countRoot, cb, query).toArray(new Predicate[0]));
        Long total = this.entityManager.createQuery(countCq).getSingleResult();
        long totalCount = 0L;
        // count 结果非空时回填 total，避免 NPE
        if (total != null) {
            totalCount = total.longValue();
        }

        List<PrescriptionReviewQueryModel> models = new ArrayList<>();
        for (PrescriptionReview row : content) {
            models.add(this.toModel(row));
        }
        return new PageImpl<>(models, effective, totalCount);
    }

    /**
     * 组装分页动态条件（含软删过滤）。
     *
     * @param root  根实体
     * @param cb    CriteriaBuilder
     * @param query 筛选；null 仅软删
     * @return 谓词列表
     */
    private List<Predicate> buildPredicates(
        Root<PrescriptionReview> root,
        CriteriaBuilder cb,
        PrescriptionReviewPageQuery query) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get(FieldNameConstants.IS_DELETED), Boolean.FALSE));
        // 无筛选：仅软删过滤，返回有效行全集
        if (query == null) {
            return predicates;
        }
        // 已点评：与有效行 status 对齐；空则不过滤状态
        if (StringUtils.isNotEmpty(query.getStatusEnum())
            && PrescriptionReviewStatus.REVIEWED.getCode().equals(query.getStatusEnum())) {
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum()));
        }
        // 点评日起：闭区间下界
        if (query.getReviewedAtFrom() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.REVIEWED_AT), query.getReviewedAtFrom()));
        }
        // 点评日止：闭区间上界
        if (query.getReviewedAtTo() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.REVIEWED_AT), query.getReviewedAtTo()));
        }
        // 是否合理精确过滤
        if (query.getReasonableFlag() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.REASONABLE_FLAG), query.getReasonableFlag()));
        }
        // 点评人精确过滤
        if (query.getReviewerId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.REVIEWER_ID), query.getReviewerId()));
        }
        // 处方号模糊
        if (StringUtils.isNotEmpty(query.getPrescriptionNoKeyword())) {
            String like = "%" + query.getPrescriptionNoKeyword().trim() + "%";
            predicates.add(cb.like(root.get(FieldNameConstants.PRESCRIPTION_NO), like));
        }
        // 业务号模糊
        if (StringUtils.isNotEmpty(query.getBusNoKeyword())) {
            String like = "%" + query.getBusNoKeyword().trim() + "%";
            predicates.add(cb.like(root.get(FieldNameConstants.BUS_NO), like));
        }
        return predicates;
    }

    /**
     * 实体转读模型。
     *
     * @param row 实体
     * @return 读模型
     */
    private PrescriptionReviewQueryModel toModel(PrescriptionReview row) {
        PrescriptionReviewQueryModel model = new PrescriptionReviewQueryModel();
        model.setId(row.getId());
        model.setBusNo(row.getBusNo());
        model.setPrescriptionNo(row.getPrescriptionNo());
        model.setReviewerId(row.getReviewerId());
        model.setReviewedAt(row.getReviewedAt());
        model.setReasonableFlag(row.getReasonableFlag());
        model.setStatusEnum(row.getStatusEnum());
        model.setReason(row.getReason());
        model.setRemark(row.getRemark());
        return model;
    }
}

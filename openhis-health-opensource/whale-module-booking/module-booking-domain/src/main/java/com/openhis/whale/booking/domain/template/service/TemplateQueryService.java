/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.template.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.utils.KeywordExactFirstOrderSupport;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.booking.domain.template.entity.ScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.SlotProvisionTemplate;
import com.openhis.whale.booking.domain.template.repository.ScheduleTemplateRepository;
import com.openhis.whale.booking.domain.template.repository.SlotProvisionTemplateRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 排班模板读侧查询（T1/T2）。
 *
 * <p>仅本聚合模板 Repository；关键字模糊须先精确后模糊（B.2.1）。</p>
 */
@Service
@RequiredArgsConstructor
public class TemplateQueryService {

    private final SlotProvisionTemplateRepository slotProvisionTemplateRepository;
    private final ScheduleTemplateRepository scheduleTemplateRepository;

    /**
     * 分页查询 T1 放号时段模板。
     *
     * @param organizationId 机构主键
     * @param keyword        模板编码/名称关键字
     * @param pageable       分页；关键字非空时剥掉 Sort
     * @return 分页实体
     */
    public Page<SlotProvisionTemplate> pageSlotProvisionTemplates(
        Long organizationId, String keyword, Pageable pageable) {
        String trimmed = this.trimKeyword(keyword);
        return slotProvisionTemplateRepository.findAll(
            this.buildSlotProvisionSpec(organizationId, trimmed),
            this.unsortPageableIfKeyword(StringUtils.isNotEmpty(trimmed), pageable));
    }

    /**
     * 按主键加载 T1 模板。
     *
     * @param id 模板主键
     * @return 实体
     */
    public SlotProvisionTemplate loadSlotProvisionTemplateById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "id 不能为空");
        }
        var optional = slotProvisionTemplateRepository.findById(id);
        var row = optional.orElse(null);
        if (row != null && Boolean.TRUE.equals(row.getIsDeleted())) {
            throw new BusinessException(
                ErrorCodeConstants.SCHEDULE_NOT_FOUND, "放号时段模板已删除，请刷新后重新选择");
        }
        return optional.orElseThrow(() -> new BusinessException(
            ErrorCodeConstants.SCHEDULE_NOT_FOUND, "放号时段模板不存在"));
    }

    /**
     * 分页查询 T2 日程模板。
     *
     * @param organizationId 机构主键
     * @param keyword        模板编码/名称关键字
     * @param pageable       分页；关键字非空时剥掉 Sort
     * @return 分页实体
     */
    public Page<ScheduleTemplate> pageScheduleTemplates(
        Long organizationId, String keyword, Pageable pageable) {
        String trimmed = this.trimKeyword(keyword);
        return scheduleTemplateRepository.findAll(
            this.buildScheduleTemplateSpec(organizationId, trimmed),
            this.unsortPageableIfKeyword(StringUtils.isNotEmpty(trimmed), pageable));
    }

    /**
     * 按主键加载 T2 模板。
     *
     * @param id 模板主键
     * @return 实体
     */
    public ScheduleTemplate loadScheduleTemplateById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "id 不能为空");
        }
        return scheduleTemplateRepository.findById(id).filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SCHEDULE_NOT_FOUND, "日程模板不存在"));
    }

    /**
     * 科室下启用的 T1 下拉（生成/编辑 T2 时）。
     *
     * @param organizationId 机构主键
     * @return 启用模板列表
     */
    public List<SlotProvisionTemplate> listEnabledSlotProvisionTemplates(Long organizationId) {
        Specification<SlotProvisionTemplate> spec = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.isTrue(root.get("enabled")));
            if (organizationId != null) {
                jakarta.persistence.criteria.Predicate orgMatch = cb.equal(root.get("organizationId"), organizationId);
                jakarta.persistence.criteria.Predicate global = cb.isNull(root.get("organizationId"));
                predicates.add(cb.or(orgMatch, global));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return slotProvisionTemplateRepository.findAll(spec);
    }

    private Specification<SlotProvisionTemplate> buildSlotProvisionSpec(Long organizationId, String trimmedKeyword) {
        return (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (organizationId != null) {
                predicates.add(cb.equal(root.get("organizationId"), organizationId));
            }
            // 编码/名称模糊；全等须排在仅子串命中之前（B.2.1）
            if (StringUtils.isNotEmpty(trimmedKeyword)) {
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmedKeyword);
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("templateCode")), like),
                    cb.like(cb.lower(root.get("templateName")), like)));
                KeywordExactFirstOrderSupport.applyExactFirstOrder(
                    cq,
                    cb,
                    trimmedKeyword,
                    List.of(cb.desc(root.get(FieldNameConstants.ID))),
                    root.get("templateCode"),
                    root.get("templateName"));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Specification<ScheduleTemplate> buildScheduleTemplateSpec(Long organizationId, String trimmedKeyword) {
        return (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (organizationId != null) {
                predicates.add(cb.equal(root.get("organizationId"), organizationId));
            }
            // 编码/名称模糊；全等须排在仅子串命中之前（B.2.1）
            if (StringUtils.isNotEmpty(trimmedKeyword)) {
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmedKeyword);
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("templateCode")), like),
                    cb.like(cb.lower(root.get("templateName")), like)));
                KeywordExactFirstOrderSupport.applyExactFirstOrder(
                    cq,
                    cb,
                    trimmedKeyword,
                    List.of(cb.desc(root.get(FieldNameConstants.ID))),
                    root.get("templateCode"),
                    root.get("templateName"));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /**
     * 关键字非空且 Pageable 带 Sort 时剥掉 Sort，避免覆盖 Specification 精确优先 ORDER BY。
     *
     * @param keywordPresent 是否带非空关键字
     * @param pageable       原始分页
     * @return 无 Sort 的 Pageable，或原 pageable
     */
    private Pageable unsortPageableIfKeyword(boolean keywordPresent, Pageable pageable) {
        if (!keywordPresent || pageable == null) {
            return pageable;
        }
        if (!pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    /**
     * trim 关键字；空白返回 null。
     *
     * @param keyword 用户输入
     * @return trim 后关键字或 null
     */
    private String trimKeyword(String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        if (StringUtils.isEmpty(trimmed)) {
            return null;
        }
        return trimmed;
    }
}

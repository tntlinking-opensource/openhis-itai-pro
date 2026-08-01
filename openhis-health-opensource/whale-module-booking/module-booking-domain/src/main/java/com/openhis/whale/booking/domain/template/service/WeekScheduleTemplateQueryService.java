/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.template.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.utils.KeywordExactFirstOrderSupport;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplateItem;
import com.openhis.whale.booking.domain.template.repository.WeekScheduleTemplateItemRepository;
import com.openhis.whale.booking.domain.template.repository.WeekScheduleTemplateRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * TW 周排班模板读侧。
 *
 * <p>约束：只读；默认过滤软删；关键字模糊时精确全等置顶（B.2.1）。</p>
 */
@Service
@RequiredArgsConstructor
public class WeekScheduleTemplateQueryService {

    private final WeekScheduleTemplateRepository weekScheduleTemplateRepository;
    private final WeekScheduleTemplateItemRepository weekScheduleTemplateItemRepository;

    /**
     * 分页查询周排班模板。
     *
     * @param organizationId 机构主键；可空表示不按机构收窄
     * @param keyword        模板编码/名称关键字；空白时不模糊
     * @param pageable       分页；关键字非空时剥掉 Sort
     * @return TW 模板分页
     */
    public Page<WeekScheduleTemplate> pageWeekScheduleTemplates(
        Long organizationId, String keyword, Pageable pageable) {
        String trimmedKeyword = this.trimKeyword(keyword);
        // B.2.1：有关键字时剥掉 Sort，保留 Spec 精确优先 ORDER BY
        Pageable effective = this.unsortPageableIfKeyword(StringUtils.isNotEmpty(trimmedKeyword), pageable);
        return weekScheduleTemplateRepository.findAll(this.buildSpec(organizationId, trimmedKeyword), effective);
    }

    /**
     * 按主键加载周排班模板。
     *
     * @param id 模板主键
     * @return TW 实体
     */
    public WeekScheduleTemplate loadWeekScheduleTemplateById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "id 不能为空");
        }
        return weekScheduleTemplateRepository.findById(id).filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SCHEDULE_NOT_FOUND, "周排班模板不存在"));
    }

    /**
     * 列出周模板下全部明细行。
     *
     * @param weekScheduleTemplateId 周模板主键
     * @return 按星期、排序号升序的明细
     */
    public List<WeekScheduleTemplateItem> listItemsByWeekTemplateId(Long weekScheduleTemplateId) {
        return weekScheduleTemplateItemRepository.findByWeekScheduleTemplateIdAndIsDeletedFalseOrderByWeekdayAscSortNoAsc(
            weekScheduleTemplateId);
    }

    /**
     * 列出指定星期且启用的明细行。
     *
     * @param weekScheduleTemplateId 周模板主键
     * @param weekday                星期（业务约定取值）
     * @return 启用明细
     */
    public List<WeekScheduleTemplateItem> listEnabledItemsForWeekday(Long weekScheduleTemplateId, int weekday) {
        return weekScheduleTemplateItemRepository
            .findByWeekScheduleTemplateIdAndWeekdayAndIsDeletedFalseAndEnabledTrueOrderBySortNoAsc(
                weekScheduleTemplateId, weekday);
    }

    /**
     * 科室默认周模板（enabled + is_default）。
     *
     * @param organizationId 机构主键
     * @return 默认模板；无则 null
     */
    public WeekScheduleTemplate findDefaultForOrganization(Long organizationId) {
        if (organizationId == null) {
            return null;
        }
        return weekScheduleTemplateRepository
            .findFirstByOrganizationIdAndIsDefaultTrueAndIsDeletedFalseAndEnabledTrue(organizationId)
            .orElse(null);
    }

    private Specification<WeekScheduleTemplate> buildSpec(Long organizationId, String trimmedKeyword) {
        return (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (organizationId != null) {
                predicates.add(cb.equal(root.get("organizationId"), organizationId));
            }
            // 编码/名称模糊；全等编码或名称须排在仅子串命中之前
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
     * @return 关键字场景下的无 Sort Pageable；否则原 pageable
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

    private String trimKeyword(String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return null;
        }
        return keyword.trim();
    }
}

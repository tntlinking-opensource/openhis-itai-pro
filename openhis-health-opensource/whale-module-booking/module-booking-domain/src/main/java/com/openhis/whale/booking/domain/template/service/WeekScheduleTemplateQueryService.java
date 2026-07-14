/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.template.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplateItem;
import com.openhis.whale.booking.domain.template.repository.WeekScheduleTemplateItemRepository;
import com.openhis.whale.booking.domain.template.repository.WeekScheduleTemplateRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * TW 周排班模板读侧。
 */
@Service
@RequiredArgsConstructor
public class WeekScheduleTemplateQueryService {

    private final WeekScheduleTemplateRepository weekScheduleTemplateRepository;
    private final WeekScheduleTemplateItemRepository weekScheduleTemplateItemRepository;

    public Page<WeekScheduleTemplate> pageWeekScheduleTemplates(
        Long organizationId, String keyword, Pageable pageable) {
        return weekScheduleTemplateRepository.findAll(this.buildSpec(organizationId, keyword), pageable);
    }

    public WeekScheduleTemplate loadWeekScheduleTemplateById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "id 不能为空");
        }
        return weekScheduleTemplateRepository.findById(id).filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SCHEDULE_NOT_FOUND, "周排班模板不存在"));
    }

    public List<WeekScheduleTemplateItem> listItemsByWeekTemplateId(Long weekScheduleTemplateId) {
        return weekScheduleTemplateItemRepository.findByWeekScheduleTemplateIdAndIsDeletedFalseOrderByWeekdayAscSortNoAsc(
            weekScheduleTemplateId);
    }

    public List<WeekScheduleTemplateItem> listEnabledItemsForWeekday(Long weekScheduleTemplateId, int weekday) {
        return weekScheduleTemplateItemRepository
            .findByWeekScheduleTemplateIdAndWeekdayAndIsDeletedFalseAndEnabledTrueOrderBySortNoAsc(
                weekScheduleTemplateId, weekday);
    }

    /**
     * 科室默认周模板（enabled + is_default）。
     */
    public WeekScheduleTemplate findDefaultForOrganization(Long organizationId) {
        if (organizationId == null) {
            return null;
        }
        return weekScheduleTemplateRepository
            .findFirstByOrganizationIdAndIsDefaultTrueAndIsDeletedFalseAndEnabledTrue(organizationId)
            .orElse(null);
    }

    private Specification<WeekScheduleTemplate> buildSpec(Long organizationId, String keyword) {
        return (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (organizationId != null) {
                predicates.add(cb.equal(root.get("organizationId"), organizationId));
            }
            if (StringUtils.isNotBlank(keyword)) {
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(keyword.trim());
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("templateCode")), like),
                    cb.like(cb.lower(root.get("templateName")), like)));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}

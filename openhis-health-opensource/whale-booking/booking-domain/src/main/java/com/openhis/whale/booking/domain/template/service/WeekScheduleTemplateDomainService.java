package com.openhis.whale.booking.domain.template.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplateItem;
import com.openhis.whale.booking.domain.template.repository.WeekScheduleTemplateItemRepository;
import com.openhis.whale.booking.domain.template.repository.WeekScheduleTemplateRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * TW 周排班模板写侧。
 */
@Service
@RequiredArgsConstructor
public class WeekScheduleTemplateDomainService {

    private final WeekScheduleTemplateRepository weekScheduleTemplateRepository;
    private final WeekScheduleTemplateItemRepository weekScheduleTemplateItemRepository;
    private final TemplateQueryService templateQueryService;

    /**
     * 保存周模板头 + 全量替换明细。
     */
    public WeekScheduleTemplate saveWeekScheduleTemplate(
        WeekScheduleTemplate header, List<WeekScheduleTemplateItem> items, Long operatorId) {
        this.assertHeaderIdentity(header.getTemplateCode(), header.getTemplateName());
        this.assertUniqueCode(header);
        if (Boolean.TRUE.equals(header.getIsDefault()) && header.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "默认周模板须指定科室");
        }
        LocalDateTime now = LocalDateTime.now();
        if (header.getId() == null) {
            header.setCreatedAt(now);
            header.setCreatedBy(operatorId);
            header.setIsDeleted(Boolean.FALSE);
        }
        if (header.getEnabled() == null) {
            header.setEnabled(Boolean.TRUE);
        }
        if (header.getFallbackBookable() == null) {
            header.setFallbackBookable(Boolean.FALSE);
        }
        if (header.getIsDefault() == null) {
            header.setIsDefault(Boolean.FALSE);
        }
        header.setUpdatedAt(now);
        header.setUpdatedBy(operatorId);

        if (Boolean.TRUE.equals(header.getIsDefault())) {
            this.clearOtherDefaults(header.getOrganizationId(), header.getId());
        }

        WeekScheduleTemplate saved = weekScheduleTemplateRepository.save(header);
        this.replaceItems(saved.getId(), items, operatorId, now);
        return saved;
    }

    public void deleteWeekScheduleTemplate(Long id, Long operatorId) {
        weekScheduleTemplateRepository.findById(id).ifPresent(header -> {
            LocalDateTime now = LocalDateTime.now();
            header.setIsDeleted(Boolean.TRUE);
            header.setDeletedAt(now);
            header.setDeletedBy(operatorId);
            weekScheduleTemplateRepository.save(header);
            for (WeekScheduleTemplateItem item : weekScheduleTemplateItemRepository
                .findByWeekScheduleTemplateIdAndIsDeletedFalseOrderByWeekdayAscSortNoAsc(id)) {
                item.setIsDeleted(Boolean.TRUE);
                item.setDeletedAt(now);
                item.setDeletedBy(operatorId);
                weekScheduleTemplateItemRepository.save(item);
            }
        });
    }

    private void replaceItems(Long weekTemplateId, List<WeekScheduleTemplateItem> items, Long operatorId, LocalDateTime now) {
        for (WeekScheduleTemplateItem existing : weekScheduleTemplateItemRepository
            .findByWeekScheduleTemplateIdAndIsDeletedFalseOrderByWeekdayAscSortNoAsc(weekTemplateId)) {
            existing.setIsDeleted(Boolean.TRUE);
            existing.setDeletedAt(now);
            existing.setDeletedBy(operatorId);
            weekScheduleTemplateItemRepository.save(existing);
        }
        if (items == null || items.isEmpty()) {
            return;
        }
        int index = 0;
        for (WeekScheduleTemplateItem item : items) {
            if (item.getWeekday() == null || item.getWeekday() < 1 || item.getWeekday() > 7) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "weekday 须为 1～7");
            }
            if (item.getDayScheduleTemplateId() == null) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "dayScheduleTemplateId 不能为空");
            }
            templateQueryService.loadScheduleTemplateById(item.getDayScheduleTemplateId());
            item.setId(null);
            item.setWeekScheduleTemplateId(weekTemplateId);
            if (item.getSortNo() == null) {
                item.setSortNo(index);
            }
            if (item.getEnabled() == null) {
                item.setEnabled(Boolean.TRUE);
            }
            item.setCreatedAt(now);
            item.setCreatedBy(operatorId);
            item.setIsDeleted(Boolean.FALSE);
            item.setUpdatedAt(now);
            item.setUpdatedBy(operatorId);
            weekScheduleTemplateItemRepository.save(item);
            index++;
        }
    }

    private void clearOtherDefaults(Long organizationId, Long keepId) {
        for (WeekScheduleTemplate other : weekScheduleTemplateRepository
            .findByOrganizationIdAndIsDefaultTrueAndIsDeletedFalse(organizationId)) {
            if (keepId != null && keepId.equals(other.getId())) {
                continue;
            }
            other.setIsDefault(Boolean.FALSE);
            weekScheduleTemplateRepository.save(other);
        }
    }

    private void assertHeaderIdentity(String code, String name) {
        if (StringUtils.isBlank(code)) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "templateCode 不能为空");
        }
        if (StringUtils.isBlank(name)) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "templateName 不能为空");
        }
    }

    private void assertUniqueCode(WeekScheduleTemplate header) {
        Optional<WeekScheduleTemplate> existing = weekScheduleTemplateRepository
            .findFirstByOrganizationIdAndTemplateCodeAndIsDeletedFalse(
                header.getOrganizationId(), header.getTemplateCode().trim());
        if (existing.isPresent() && !existing.get().getId().equals(header.getId())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "模板编码已存在");
        }
    }
}

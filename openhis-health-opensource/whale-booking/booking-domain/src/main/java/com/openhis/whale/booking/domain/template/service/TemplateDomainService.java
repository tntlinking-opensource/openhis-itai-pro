package com.openhis.whale.booking.domain.template.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.domain.template.entity.ScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.SlotProvisionTemplate;
import com.openhis.whale.booking.domain.template.repository.ScheduleTemplateRepository;
import com.openhis.whale.booking.domain.template.repository.SlotProvisionTemplateRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 排班模板写侧领域服务（T1/T2 CRUD）。
 */
@Service
@RequiredArgsConstructor
public class TemplateDomainService {

    private final SlotProvisionTemplateRepository slotProvisionTemplateRepository;
    private final ScheduleTemplateRepository scheduleTemplateRepository;

    /**
     * 保存 T1 放号时段模板。
     */
    public SlotProvisionTemplate saveSlotProvisionTemplate(SlotProvisionTemplate template, Long operatorId) {
        this.assertTemplateIdentity(template.getTemplateCode(), template.getTemplateName());
        this.assertUniqueSlotProvisionCode(template);
        LocalDateTime now = LocalDateTime.now();
        if (template.getId() == null) {
            template.setCreatedAt(now);
            template.setCreatedBy(operatorId);
            template.setIsDeleted(Boolean.FALSE);
        }
        if (template.getEnabled() == null) {
            template.setEnabled(Boolean.TRUE);
        }
        template.setUpdatedAt(now);
        template.setUpdatedBy(operatorId);
        return slotProvisionTemplateRepository.save(template);
    }

    /**
     * 软删 T1 模板。
     */
    public void deleteSlotProvisionTemplate(Long id, Long operatorId) {
        slotProvisionTemplateRepository.findById(id).ifPresent(template -> {
            template.setIsDeleted(Boolean.TRUE);
            template.setDeletedAt(LocalDateTime.now());
            template.setDeletedBy(operatorId);
            slotProvisionTemplateRepository.save(template);
        });
    }

    /**
     * 保存 T2 日程模板。
     */
    public ScheduleTemplate saveScheduleTemplate(ScheduleTemplate template, Long operatorId) {
        this.assertTemplateIdentity(template.getTemplateCode(), template.getTemplateName());
        if (StringUtils.isBlank(template.getScheduleMode())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleMode 不能为空");
        }
        if (template.getHealthcareServiceId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "healthcareServiceId 不能为空");
        }
        this.assertUniqueScheduleTemplateCode(template);
        LocalDateTime now = LocalDateTime.now();
        if (template.getId() == null) {
            template.setCreatedAt(now);
            template.setCreatedBy(operatorId);
            template.setIsDeleted(Boolean.FALSE);
        }
        if (template.getEnabled() == null) {
            template.setEnabled(Boolean.TRUE);
        }
        if (template.getPriority() == null) {
            template.setPriority(0);
        }
        template.setUpdatedAt(now);
        template.setUpdatedBy(operatorId);
        return scheduleTemplateRepository.save(template);
    }

    /**
     * 软删 T2 模板。
     */
    public void deleteScheduleTemplate(Long id, Long operatorId) {
        scheduleTemplateRepository.findById(id).ifPresent(template -> {
            template.setIsDeleted(Boolean.TRUE);
            template.setDeletedAt(LocalDateTime.now());
            template.setDeletedBy(operatorId);
            scheduleTemplateRepository.save(template);
        });
    }

    private void assertTemplateIdentity(String code, String name) {
        if (StringUtils.isBlank(code)) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "templateCode 不能为空");
        }
        if (StringUtils.isBlank(name)) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "templateName 不能为空");
        }
    }

    private void assertUniqueSlotProvisionCode(SlotProvisionTemplate template) {
        Optional<SlotProvisionTemplate> existing = slotProvisionTemplateRepository
            .findFirstByOrganizationIdAndTemplateCodeAndIsDeletedFalse(
                template.getOrganizationId(), template.getTemplateCode().trim());
        if (existing.isPresent() && !existing.get().getId().equals(template.getId())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "模板编码已存在");
        }
    }

    private void assertUniqueScheduleTemplateCode(ScheduleTemplate template) {
        Optional<ScheduleTemplate> existing = scheduleTemplateRepository
            .findFirstByOrganizationIdAndTemplateCodeAndIsDeletedFalse(
                template.getOrganizationId(), template.getTemplateCode().trim());
        if (existing.isPresent() && !existing.get().getId().equals(template.getId())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "模板编码已存在");
        }
    }
}

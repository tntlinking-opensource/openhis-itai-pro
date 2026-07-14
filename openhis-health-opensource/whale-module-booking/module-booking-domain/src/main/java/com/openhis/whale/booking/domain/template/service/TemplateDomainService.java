/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.template.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.domain.calendarexception.repository.CalendarExceptionRepository;
import com.openhis.whale.booking.domain.template.entity.ScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.SlotProvisionTemplate;
import com.openhis.whale.booking.domain.template.repository.ScheduleTemplateRepository;
import com.openhis.whale.booking.domain.template.repository.SlotProvisionTemplateRepository;
import com.openhis.whale.booking.domain.template.repository.WeekScheduleTemplateItemRepository;
import com.openhis.whale.booking.domain.template.constants.BookingTemplateCodeConstants;
import com.openhis.whale.booking.domain.template.support.BookingTemplateCodeSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

/**
 * 排班模板写侧领域服务（T1/T2 CRUD）。
 *
 * <p>删除前校验被周排班、日排班或日历例外引用，防止悬空模板。</p>
 */
@Service
@RequiredArgsConstructor
public class TemplateDomainService {

    private final SlotProvisionTemplateRepository slotProvisionTemplateRepository;
    private final ScheduleTemplateRepository scheduleTemplateRepository;
    private final WeekScheduleTemplateItemRepository weekScheduleTemplateItemRepository;
    private final CalendarExceptionRepository calendarExceptionRepository;

    /**
     * 保存 T1 放号时段模板。
     *
     * @param template 时段模板实体
     * @param operatorId 操作人
     * @return 落库后的模板
     */
    public SlotProvisionTemplate saveSlotProvisionTemplate(SlotProvisionTemplate template, Long operatorId) {
        // 更新须加载托管实体，避免带 id 的新对象触发 persist detached entity。
        SlotProvisionTemplate target = this.loadOrCreateSlotProvisionTemplate(template.getId());
        target.setOrganizationId(template.getOrganizationId());
        target.setTemplateCode(template.getTemplateCode());
        target.setTemplateName(template.getTemplateName());
        target.setSlotTemplatesJson(template.getSlotTemplatesJson());
        target.setEnabled(template.getEnabled());
        // 前置校验：编码回落/唯一性与名称必填
        this.applySlotProvisionTemplateCode(target);
        this.assertTemplateName(target.getTemplateName());
        this.assertUniqueSlotProvisionCode(target);
        LocalDateTime now = LocalDateTime.now();
        if (target.getId() == null) {
            target.setCreatedAt(now);
            target.setCreatedBy(operatorId);
            target.setIsDeleted(Boolean.FALSE);
        }
        if (target.getEnabled() == null) {
            target.setEnabled(Boolean.TRUE);
        }
        target.setUpdatedAt(now);
        target.setUpdatedBy(operatorId);
        // 持久化
        return slotProvisionTemplateRepository.save(target);
    }

    /**
     * 软删 T1 模板。
     *
     * @param id 模板主键
     * @param operatorId 操作人
     * @throws BusinessException 仍被日排班引用时
     */
    public void deleteSlotProvisionTemplate(Long id, Long operatorId) {
        if (id == null) {
            return;
        }
        // 前置校验：无下游引用方可删除
        this.assertSlotProvisionTemplateDeletable(id);
        slotProvisionTemplateRepository.findById(id).ifPresent(template -> {
            template.setIsDeleted(Boolean.TRUE);
            template.setDeletedAt(LocalDateTime.now());
            template.setDeletedBy(operatorId);
            slotProvisionTemplateRepository.save(template);
        });
    }

    /**
     * 保存 T2 日程模板。
     *
     * @param template 日排班模板实体
     * @param operatorId 操作人
     * @return 落库后的模板
     * @throws BusinessException scheduleMode 或 healthcareServiceId 缺失时
     */
    public ScheduleTemplate saveScheduleTemplate(ScheduleTemplate template, Long operatorId) {
        // 更新须加载托管实体，避免带 id 的新对象触发 persist detached entity。
        ScheduleTemplate target = this.loadOrCreateScheduleTemplate(template.getId());
        target.setOrganizationId(template.getOrganizationId());
        target.setTemplateCode(template.getTemplateCode());
        target.setTemplateName(template.getTemplateName());
        target.setScheduleMode(template.getScheduleMode());
        target.setPractitionerId(template.getPractitionerId());
        target.setLocationId(template.getLocationId());
        target.setHealthcareServiceId(template.getHealthcareServiceId());
        target.setSlotProvisionTemplateId(template.getSlotProvisionTemplateId());
        target.setDefaultReleasePolicyId(template.getDefaultReleasePolicyId());
        target.setEffectiveStart(template.getEffectiveStart());
        target.setEffectiveEnd(template.getEffectiveEnd());
        target.setPriority(template.getPriority());
        target.setSourcePractitionerRoleId(template.getSourcePractitionerRoleId());
        target.setEnabled(template.getEnabled());
        this.applyScheduleTemplateCode(target);
        this.assertTemplateName(target.getTemplateName());
        // scheduleMode 决定医师/诊室必填规则，空白则无法物化正式排班
        if (isEmpty(target.getScheduleMode())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleMode 不能为空");
        }
        if (target.getHealthcareServiceId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "healthcareServiceId 不能为空");
        }
        this.assertUniqueScheduleTemplateCode(target);
        LocalDateTime now = LocalDateTime.now();
        if (target.getId() == null) {
            target.setCreatedAt(now);
            target.setCreatedBy(operatorId);
            target.setIsDeleted(Boolean.FALSE);
        }
        if (target.getEnabled() == null) {
            target.setEnabled(Boolean.TRUE);
        }
        if (target.getPriority() == null) {
            target.setPriority(0);
        }
        target.setUpdatedAt(now);
        target.setUpdatedBy(operatorId);
        return scheduleTemplateRepository.save(target);
    }

    /**
     * 软删 T2 模板。
     *
     * @param id 模板主键
     * @param operatorId 操作人
     * @throws BusinessException 仍被周排班或日历例外引用时
     */
    public void deleteScheduleTemplate(Long id, Long operatorId) {
        if (id == null) {
            return;
        }
        this.assertScheduleTemplateDeletable(id);
        scheduleTemplateRepository.findById(id).ifPresent(template -> {
            template.setIsDeleted(Boolean.TRUE);
            template.setDeletedAt(LocalDateTime.now());
            template.setDeletedBy(operatorId);
            scheduleTemplateRepository.save(template);
        });
    }

    /**
     * 加载已有 T1 或创建新行（Snowflake 主键由 insert 阶段生成）。
     *
     * @param id 模板主键；空表示新建
     * @return 托管实体或新行
     * @throws BusinessException 指定 id 不存在时
     */
    private SlotProvisionTemplate loadOrCreateSlotProvisionTemplate(Long id) {
        if (id == null) {
            return new SlotProvisionTemplate();
        }
        return slotProvisionTemplateRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "放号时段模板不存在"));
    }

    /**
     * 加载已有 T2 或创建新行。
     *
     * @param id 模板主键；空表示新建
     * @return 托管实体或新行
     * @throws BusinessException 指定 id 不存在时
     */
    private ScheduleTemplate loadOrCreateScheduleTemplate(Long id) {
        if (id == null) {
            return new ScheduleTemplate();
        }
        return scheduleTemplateRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "日排班模板不存在"));
    }

    /**
     * 删除 T1 前校验：仍被日排班引用则拒绝，避免悬空 T1。
     *
     * @param id T1 模板主键
     * @throws BusinessException 存在引用时
     */
    private void assertSlotProvisionTemplateDeletable(Long id) {
        long scheduleTemplateRefs = scheduleTemplateRepository.countBySlotProvisionTemplateIdAndIsDeletedFalse(id);
        if (scheduleTemplateRefs > 0) {
            throw new BusinessException(
                ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT,
                "该放号时段已被 " + scheduleTemplateRefs + " 个日排班引用，请先在日排班中解除关联后再删除");
        }
    }

    /**
     * 删除 T2 前校验：仍被周明细或 T3 日历例外引用则拒绝。
     *
     * @param id T2 模板主键
     * @throws BusinessException 存在引用时
     */
    private void assertScheduleTemplateDeletable(Long id) {
        long weekItemRefs = weekScheduleTemplateItemRepository.countByDayScheduleTemplateIdAndIsDeletedFalse(id);
        if (weekItemRefs > 0) {
            throw new BusinessException(
                ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT,
                "该日排班已被 " + weekItemRefs + " 条周排班明细引用，请先在周排班中解除关联后再删除");
        }
        long calendarExceptionRefs = calendarExceptionRepository.countByOverrideScheduleTemplateIdAndIsDeletedFalse(id);
        if (calendarExceptionRefs > 0) {
            throw new BusinessException(
                ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT,
                "该日排班已被 " + calendarExceptionRefs + " 条日历例外引用，请先修改或删除相关例外后再删除");
        }
    }

    /**
     * 解析 T1 待持久化编码：显式传入 / 更新沿用 / 新建自动生成。
     *
     * @param template 待保存的 T1 实体（含 id 与 templateCode）
     */
    private void applySlotProvisionTemplateCode(SlotProvisionTemplate template) {
        String existingCode = null;
        if (template.getId() != null) {
            // 更新场景：读取库中编码以便未传时沿用
            existingCode = slotProvisionTemplateRepository.findById(template.getId())
                .map(SlotProvisionTemplate::getTemplateCode)
                .orElse(null);
        }
        template.setTemplateCode(BookingTemplateCodeSupport.resolvePersistedCode(
            template.getTemplateCode(),
            existingCode,
            BookingTemplateCodeConstants.PREFIX_SLOT_PROVISION));
    }

    /**
     * 解析 T2 待持久化编码：显式传入 / 更新沿用 / 新建自动生成。
     *
     * @param template 待保存的 T2 实体（含 id 与 templateCode）
     */
    private void applyScheduleTemplateCode(ScheduleTemplate template) {
        String existingCode = null;
        if (template.getId() != null) {
            existingCode = scheduleTemplateRepository.findById(template.getId())
                .map(ScheduleTemplate::getTemplateCode)
                .orElse(null);
        }
        template.setTemplateCode(BookingTemplateCodeSupport.resolvePersistedCode(
            template.getTemplateCode(),
            existingCode,
            BookingTemplateCodeConstants.PREFIX_DAY_SCHEDULE));
    }

    /**
     * 模板名称非空校验（列表展示与生成 Schedule 命名均依赖名称）。
     *
     * @param name 模板名称
     * @throws BusinessException 名称为空时
     */
    private void assertTemplateName(String name) {
        if (isEmpty(name)) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "templateName 不能为空");
        }
    }

    /**
     * 同科室 T1 编码唯一性校验（自动码与手填码均须唯一）。
     *
     * @param template 待保存的 T1 实体
     * @throws BusinessException 与同科室其他模板编码冲突时
     */
    private void assertUniqueSlotProvisionCode(SlotProvisionTemplate template) {
        Optional<SlotProvisionTemplate> existing = slotProvisionTemplateRepository
            .findFirstByOrganizationIdAndTemplateCodeAndIsDeletedFalse(
                template.getOrganizationId(), template.getTemplateCode().trim());
        if (existing.isPresent() && !existing.get().getId().equals(template.getId())) {
            // 同科室编码冲突：拒绝覆盖他人模板
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "模板编码已存在");
        }
    }

    /**
     * 同科室 T2 编码唯一性校验（自动码与手填码均须唯一）。
     *
     * @param template 待保存的 T2 实体
     * @throws BusinessException 与同科室其他模板编码冲突时
     */
    private void assertUniqueScheduleTemplateCode(ScheduleTemplate template) {
        Optional<ScheduleTemplate> existing = scheduleTemplateRepository
            .findFirstByOrganizationIdAndTemplateCodeAndIsDeletedFalse(
                template.getOrganizationId(), template.getTemplateCode().trim());
        if (existing.isPresent() && !existing.get().getId().equals(template.getId())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "模板编码已存在");
        }
    }
}

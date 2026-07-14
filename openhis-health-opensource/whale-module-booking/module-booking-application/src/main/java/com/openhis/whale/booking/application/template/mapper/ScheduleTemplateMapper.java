/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.template.mapper;

import com.openhis.whale.booking.application.template.request.SaveScheduleTemplateRequest;
import com.openhis.whale.booking.application.template.request.SaveSlotProvisionTemplateRequest;
import com.openhis.whale.booking.application.template.response.ScheduleTemplateDto;
import com.openhis.whale.booking.application.template.response.SlotProvisionTemplateDto;
import com.openhis.whale.booking.application.template.support.TemplateJsonSupport;
import com.openhis.whale.booking.domain.template.entity.ScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.SlotProvisionTemplate;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.stereotype.Component;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 排班模板（T1/T2）应用层映射器。
 * <p>职责：号源时段模板与日排班模板 Request/Entity/DTO 转换；不含业务门禁与事务。</p>
 */
@Component
public class ScheduleTemplateMapper {

    /**
     * T1 实体 → 详情 DTO（解析 slotTemplatesJson）。
     *
     * @param entity 号源时段模板实体
     * @return 号源时段模板 DTO
     */
    public SlotProvisionTemplateDto toSlotProvisionTemplateDto(SlotProvisionTemplate entity) {
        SlotProvisionTemplateDto dto = new SlotProvisionTemplateDto();
        dto.setId(entity.getId());
        dto.setOrganizationId(entity.getOrganizationId());
        dto.setTemplateCode(entity.getTemplateCode());
        dto.setTemplateName(entity.getTemplateName());
        dto.setSlotTemplates(TemplateJsonSupport.parseSlotItems(entity.getSlotTemplatesJson()));
        dto.setEnabled(entity.getEnabled());
        return dto;
    }

    /**
     * 保存 T1 请求 → 领域实体（序列化时段 JSON）。
     *
     * @param request 保存号源时段模板请求
     * @return 号源时段模板实体
     */
    public SlotProvisionTemplate toSlotProvisionTemplateEntity(SaveSlotProvisionTemplateRequest request) {
        SlotProvisionTemplate entity = new SlotProvisionTemplate();
        entity.setId(request.getId());
        entity.setOrganizationId(request.getOrganizationId());
        // 编码可选：空白交由领域层自动生成
        entity.setTemplateCode(trimToNull(request.getTemplateCode()));
        entity.setTemplateName(request.getTemplateName().trim());
        entity.setSlotTemplatesJson(TemplateJsonSupport.writeSlotItems(request.getSlotTemplates()));
        entity.setEnabled(request.getEnabled());
        return entity;
    }

    /**
     * T2 实体 → 详情 DTO。
     *
     * @param entity 日排班模板实体
     * @return 日排班模板 DTO
     */
    public ScheduleTemplateDto toScheduleTemplateDto(ScheduleTemplate entity) {
        ScheduleTemplateDto dto = new ScheduleTemplateDto();
        dto.setId(entity.getId());
        dto.setOrganizationId(entity.getOrganizationId());
        dto.setTemplateCode(entity.getTemplateCode());
        dto.setTemplateName(entity.getTemplateName());
        dto.setScheduleMode(entity.getScheduleMode());
        dto.setPractitionerId(entity.getPractitionerId());
        dto.setLocationId(entity.getLocationId());
        dto.setHealthcareServiceId(entity.getHealthcareServiceId());
        dto.setSlotProvisionTemplateId(entity.getSlotProvisionTemplateId());
        dto.setDefaultReleasePolicyId(entity.getDefaultReleasePolicyId());
        dto.setEffectiveStart(entity.getEffectiveStart());
        dto.setEffectiveEnd(entity.getEffectiveEnd());
        dto.setPriority(entity.getPriority());
        dto.setSourcePractitionerRoleId(entity.getSourcePractitionerRoleId());
        dto.setEnabled(entity.getEnabled());
        return dto;
    }

    /**
     * 保存 T2 请求 → 领域实体。
     *
     * @param request 保存日排班模板请求
     * @return 日排班模板实体
     */
    public ScheduleTemplate toScheduleTemplateEntity(SaveScheduleTemplateRequest request) {
        ScheduleTemplate entity = new ScheduleTemplate();
        entity.setId(request.getId());
        entity.setOrganizationId(request.getOrganizationId());
        // 编码可选：空白交由领域层自动生成
        entity.setTemplateCode(trimToNull(request.getTemplateCode()));
        entity.setTemplateName(request.getTemplateName().trim());
        entity.setScheduleMode(request.getScheduleMode().trim());
        entity.setPractitionerId(request.getPractitionerId());
        entity.setLocationId(request.getLocationId());
        entity.setHealthcareServiceId(request.getHealthcareServiceId());
        entity.setSlotProvisionTemplateId(request.getSlotProvisionTemplateId());
        entity.setDefaultReleasePolicyId(request.getDefaultReleasePolicyId());
        entity.setEffectiveStart(request.getEffectiveStart());
        entity.setEffectiveEnd(request.getEffectiveEnd());
        entity.setPriority(request.getPriority());
        entity.setSourcePractitionerRoleId(request.getSourcePractitionerRoleId());
        entity.setEnabled(request.getEnabled());
        return entity;
    }

    /**
     * 启用中的 T1 列表 → 下拉选项。
     *
     * @param templates 号源时段模板列表
     * @return 下拉选项列表
     */
    public List<OptionDto> toSlotProvisionOptions(List<SlotProvisionTemplate> templates) {
        List<OptionDto> options = new ArrayList<>();
        if (templates == null) {
            return options;
        }
        for (SlotProvisionTemplate template : templates) {
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(template.getId()));
            option.setLabel(template.getTemplateName());
            option.setSort(template.getId());
            options.add(option);
        }
        return options;
    }
}

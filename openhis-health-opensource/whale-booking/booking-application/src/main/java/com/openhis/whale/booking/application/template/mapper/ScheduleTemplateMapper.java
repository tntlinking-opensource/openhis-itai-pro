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

import java.util.ArrayList;
import java.util.List;

/**
 * 排班模板应用层映射。
 */
@Component
public class ScheduleTemplateMapper {

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

    public SlotProvisionTemplate toSlotProvisionTemplateEntity(SaveSlotProvisionTemplateRequest request) {
        SlotProvisionTemplate entity = new SlotProvisionTemplate();
        entity.setId(request.getId());
        entity.setOrganizationId(request.getOrganizationId());
        entity.setTemplateCode(request.getTemplateCode().trim());
        entity.setTemplateName(request.getTemplateName().trim());
        entity.setSlotTemplatesJson(TemplateJsonSupport.writeSlotItems(request.getSlotTemplates()));
        entity.setEnabled(request.getEnabled());
        return entity;
    }

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

    public ScheduleTemplate toScheduleTemplateEntity(SaveScheduleTemplateRequest request) {
        ScheduleTemplate entity = new ScheduleTemplate();
        entity.setId(request.getId());
        entity.setOrganizationId(request.getOrganizationId());
        entity.setTemplateCode(request.getTemplateCode().trim());
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

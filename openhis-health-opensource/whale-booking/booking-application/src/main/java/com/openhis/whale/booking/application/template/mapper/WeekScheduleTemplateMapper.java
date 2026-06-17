package com.openhis.whale.booking.application.template.mapper;

import com.openhis.whale.booking.application.template.request.SaveWeekScheduleTemplateItemRequest;
import com.openhis.whale.booking.application.template.request.SaveWeekScheduleTemplateRequest;
import com.openhis.whale.booking.application.template.response.WeekScheduleTemplateDto;
import com.openhis.whale.booking.application.template.response.WeekScheduleTemplateItemDto;
import com.openhis.whale.booking.domain.template.entity.ScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplateItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TW 周模板应用层映射。
 */
@Component
public class WeekScheduleTemplateMapper {

    public WeekScheduleTemplateDto toWeekScheduleTemplateDto(
        WeekScheduleTemplate header, List<WeekScheduleTemplateItem> items, Map<Long, ScheduleTemplate> dayTemplates) {
        WeekScheduleTemplateDto dto = new WeekScheduleTemplateDto();
        dto.setId(header.getId());
        dto.setOrganizationId(header.getOrganizationId());
        dto.setTemplateCode(header.getTemplateCode());
        dto.setTemplateName(header.getTemplateName());
        dto.setFallbackBookable(header.getFallbackBookable());
        dto.setIsDefault(header.getIsDefault());
        dto.setEnabled(header.getEnabled());
        dto.setEffectiveStart(header.getEffectiveStart());
        dto.setEffectiveEnd(header.getEffectiveEnd());
        List<WeekScheduleTemplateItemDto> itemDtos = new ArrayList<>();
        if (items != null) {
            for (WeekScheduleTemplateItem item : items) {
                itemDtos.add(this.toItemDto(item, dayTemplates));
            }
        }
        dto.setItems(itemDtos);
        return dto;
    }

    public WeekScheduleTemplateItemDto toItemDto(WeekScheduleTemplateItem item, Map<Long, ScheduleTemplate> dayTemplates) {
        WeekScheduleTemplateItemDto dto = new WeekScheduleTemplateItemDto();
        dto.setId(item.getId());
        dto.setWeekday(item.getWeekday());
        dto.setDayScheduleTemplateId(item.getDayScheduleTemplateId());
        dto.setSortNo(item.getSortNo());
        dto.setEnabled(item.getEnabled());
        if (dayTemplates != null && item.getDayScheduleTemplateId() != null) {
            ScheduleTemplate day = dayTemplates.get(item.getDayScheduleTemplateId());
            if (day != null) {
                dto.setDayScheduleTemplateName(day.getTemplateName());
            }
        }
        return dto;
    }

    public WeekScheduleTemplate toHeaderEntity(SaveWeekScheduleTemplateRequest request) {
        WeekScheduleTemplate entity = new WeekScheduleTemplate();
        entity.setId(request.getId());
        entity.setOrganizationId(request.getOrganizationId());
        entity.setTemplateCode(request.getTemplateCode().trim());
        entity.setTemplateName(request.getTemplateName().trim());
        entity.setFallbackBookable(request.getFallbackBookable());
        entity.setIsDefault(request.getIsDefault());
        entity.setEnabled(request.getEnabled());
        entity.setEffectiveStart(request.getEffectiveStart());
        entity.setEffectiveEnd(request.getEffectiveEnd());
        return entity;
    }

    public List<WeekScheduleTemplateItem> toItemEntities(SaveWeekScheduleTemplateRequest request) {
        List<WeekScheduleTemplateItem> items = new ArrayList<>();
        if (request.getItems() == null) {
            return items;
        }
        for (SaveWeekScheduleTemplateItemRequest row : request.getItems()) {
            WeekScheduleTemplateItem item = new WeekScheduleTemplateItem();
            item.setWeekday(row.getWeekday());
            item.setDayScheduleTemplateId(row.getDayScheduleTemplateId());
            item.setSortNo(row.getSortNo());
            item.setEnabled(row.getEnabled());
            items.add(item);
        }
        return items;
    }

    public Map<Long, ScheduleTemplate> indexDayTemplates(List<ScheduleTemplate> templates) {
        Map<Long, ScheduleTemplate> map = new HashMap<>();
        if (templates == null) {
            return map;
        }
        for (ScheduleTemplate template : templates) {
            map.put(template.getId(), template);
        }
        return map;
    }
}

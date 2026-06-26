package com.openhis.whale.booking.application.template.mapper;

import com.openhis.whale.booking.application.template.request.SaveWeekScheduleTemplateItemRequest;
import com.openhis.whale.booking.application.template.request.SaveWeekScheduleTemplateRequest;
import com.openhis.whale.booking.application.template.response.WeekScheduleTemplateDto;
import com.openhis.whale.booking.application.template.response.WeekScheduleTemplateItemDto;
import com.openhis.whale.booking.domain.template.entity.ScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplateItem;
import org.springframework.stereotype.Component;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TW 周模板应用层映射器。
 * <p>职责：周模板头/明细 Request/Entity/DTO 转换；不含业务门禁与事务。</p>
 */
@Component
public class WeekScheduleTemplateMapper {

    /**
     * TW 头 + 明细 + 关联 T2 → 详情 DTO。
     *
     * @param header 周模板头实体
     * @param items 周模板明细列表
     * @param dayTemplates 日排班模板 id 索引
     * @return 周模板详情 DTO
     */
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

    /**
     * TW 明细实体 → 行 DTO（附加日模板名称）。
     *
     * @param item 周模板明细实体
     * @param dayTemplates 日排班模板 id 索引
     * @return 周模板明细 DTO
     */
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

    /**
     * 保存 TW 请求 → 头实体。
     *
     * @param request 保存周模板请求
     * @return 周模板头实体
     */
    public WeekScheduleTemplate toHeaderEntity(SaveWeekScheduleTemplateRequest request) {
        WeekScheduleTemplate entity = new WeekScheduleTemplate();
        entity.setId(request.getId());
        entity.setOrganizationId(request.getOrganizationId());
        // 编码可选：空白交由领域层自动生成
        entity.setTemplateCode(trimToNull(request.getTemplateCode()));
        entity.setTemplateName(request.getTemplateName().trim());
        entity.setFallbackBookable(request.getFallbackBookable());
        entity.setIsDefault(request.getIsDefault());
        entity.setEnabled(request.getEnabled());
        entity.setEffectiveStart(request.getEffectiveStart());
        entity.setEffectiveEnd(request.getEffectiveEnd());
        return entity;
    }

    /**
     * 保存 TW 请求 → 明细实体列表。
     *
     * @param request 保存周模板请求
     * @return 周模板明细实体列表
     */
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

    /**
     * T2 日模板列表 → id 索引 Map（详情组装用）。
     *
     * @param templates 日排班模板列表
     * @return 日排班模板 id 索引
     */
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

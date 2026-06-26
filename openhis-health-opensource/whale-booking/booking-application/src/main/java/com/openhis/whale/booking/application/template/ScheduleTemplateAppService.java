package com.openhis.whale.booking.application.template;

import com.openhis.whale.booking.application.support.BookingOperatorResolver;
import com.openhis.whale.booking.application.support.BookingPagedResultBuilder;
import com.openhis.whale.booking.application.template.mapper.ScheduleTemplateMapper;
import com.openhis.whale.booking.application.template.mapper.WeekScheduleTemplateMapper;
import com.openhis.whale.booking.application.template.request.DeleteTemplateRequest;
import com.openhis.whale.booking.application.template.request.ImportFromPractitionerRoleRequest;
import com.openhis.whale.booking.application.template.request.ListSlotProvisionTemplateOptionsRequest;
import com.openhis.whale.booking.application.template.request.LoadTemplateByIdRequest;
import com.openhis.whale.booking.application.template.request.PageScheduleTemplatesRequest;
import com.openhis.whale.booking.application.template.request.PageSlotProvisionTemplatesRequest;
import com.openhis.whale.booking.application.template.request.PageWeekScheduleTemplatesRequest;
import com.openhis.whale.booking.application.template.request.SaveScheduleTemplateRequest;
import com.openhis.whale.booking.application.template.request.SaveSlotProvisionTemplateRequest;
import com.openhis.whale.booking.application.template.request.SaveWeekScheduleTemplateRequest;
import com.openhis.whale.booking.application.template.response.ImportFromPractitionerRoleResponse;
import com.openhis.whale.booking.application.template.response.ListSlotProvisionTemplateOptionsResponse;
import com.openhis.whale.booking.application.template.response.PageScheduleTemplatesResponse;
import com.openhis.whale.booking.application.template.response.PageSlotProvisionTemplatesResponse;
import com.openhis.whale.booking.application.template.response.PageWeekScheduleTemplatesResponse;
import com.openhis.whale.booking.application.template.response.SaveTemplateResponse;
import com.openhis.whale.booking.application.template.response.ScheduleTemplateDto;
import com.openhis.whale.booking.application.template.response.SlotProvisionTemplateDto;
import com.openhis.whale.booking.application.template.response.SlotProvisionTemplateItemDto;
import com.openhis.whale.booking.application.template.response.WeekScheduleTemplateDto;
import com.openhis.whale.booking.application.template.support.PractitionerRoleImportSupport;
import com.openhis.whale.booking.application.template.support.TemplateJsonSupport;
import com.openhis.whale.booking.contracts.dto.PractitionerRoleScheduleItemDto;
import com.openhis.whale.booking.contracts.reader.HealthPractitionerRoleReader;
import com.openhis.whale.booking.domain.template.entity.ScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.SlotProvisionTemplate;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplateItem;
import com.openhis.whale.booking.domain.template.service.TemplateDomainService;
import com.openhis.whale.booking.domain.template.service.TemplateQueryService;
import com.openhis.whale.booking.domain.template.service.WeekScheduleTemplateDomainService;
import com.openhis.whale.booking.domain.template.service.WeekScheduleTemplateQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 排班模板应用服务（T1/T2/TW CRUD，2.13～2.14、2.24～2.25）。
 *
 * <p>职责边界：号源时段模板（T1）、日排班模板（T2）、周模板（TW）的查询与维护；岗位导入生成 T1+T2 草稿。</p>
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/scheduleTemplate/{methodName}}。</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class ScheduleTemplateAppService {

    /** T1/T2 模板读侧。 */
    private final TemplateQueryService templateQueryService;
    /** T1/T2 模板写侧。 */
    private final TemplateDomainService templateDomainService;
    /** TW 周模板读侧。 */
    private final WeekScheduleTemplateQueryService weekScheduleTemplateQueryService;
    /** TW 周模板写侧。 */
    private final WeekScheduleTemplateDomainService weekScheduleTemplateDomainService;
    /** T1/T2 Request/Entity/DTO 映射。 */
    private final ScheduleTemplateMapper scheduleTemplateMapper;
    /** TW Request/Entity/DTO 映射。 */
    private final WeekScheduleTemplateMapper weekScheduleTemplateMapper;
    /** 写路径操作人。 */
    private final BookingOperatorResolver bookingOperatorResolver;
    /** 岗位出诊时段（health 经 Reader/Writer/Client）。 */
    private final HealthPractitionerRoleReader healthPractitionerRoleReader;

    /**
     * T1 分页列表（2.13）。
     */
    public PageSlotProvisionTemplatesResponse pageSlotProvisionTemplates(
        @Valid PageSlotProvisionTemplatesRequest request) {
        // Pre：按机构与关键字分页加载 T1 模板。
        Page<SlotProvisionTemplate> page = templateQueryService.pageSlotProvisionTemplates(
            request.getOrganizationId(),
            request.getKeyword(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<SlotProvisionTemplateDto> rows = new ArrayList<>();
        for (SlotProvisionTemplate entity : page.getContent()) {
            rows.add(scheduleTemplateMapper.toSlotProvisionTemplateDto(entity));
        }
        // Asm：映射 DTO 并包装分页结果。
        return BookingPagedResultBuilder.build(new PageSlotProvisionTemplatesResponse(), rows, page);
    }

    /**
     * T1 详情。
     */
    public SlotProvisionTemplateDto loadSlotProvisionTemplate(@Valid LoadTemplateByIdRequest request) {
        // Pre：按主键加载 T1 模板。
        // Asm：映射 T1 详情 DTO。
        return scheduleTemplateMapper.toSlotProvisionTemplateDto(
            templateQueryService.loadSlotProvisionTemplateById(request.getId()));
    }

    /**
     * T1 保存。
     */
    public SaveTemplateResponse saveSlotProvisionTemplate(@Valid SaveSlotProvisionTemplateRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：持久化 T1 模板。
        SlotProvisionTemplate saved = templateDomainService.saveSlotProvisionTemplate(
            scheduleTemplateMapper.toSlotProvisionTemplateEntity(request), operatorId);
        // Asm：返回主键。
        SaveTemplateResponse response = new SaveTemplateResponse();
        response.setId(saved.getId());
        return response;
    }

    /**
     * T1 删除。
     */
    public void deleteSlotProvisionTemplate(@Valid DeleteTemplateRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：软删 T1 模板。
        templateDomainService.deleteSlotProvisionTemplate(request.getId(), operatorId);
    }

    /**
     * T1 下拉（编辑 T2 时用）。
     */
    public ListSlotProvisionTemplateOptionsResponse listSlotProvisionTemplateOptions(
        ListSlotProvisionTemplateOptionsRequest request) {
        // Pre：解析机构并加载已启用 T1 下拉项。
        Long organizationId = null;
        if (request != null) {
            organizationId = request.getOrganizationId();
        }
        ListSlotProvisionTemplateOptionsResponse response = new ListSlotProvisionTemplateOptionsResponse();
        response.setOptions(scheduleTemplateMapper.toSlotProvisionOptions(
            templateQueryService.listEnabledSlotProvisionTemplates(organizationId)));
        // Asm：返回 T1 下拉选项响应。
        return response;
    }

    /**
     * T2 分页列表（2.14）。
     */
    public PageScheduleTemplatesResponse pageScheduleTemplates(@Valid PageScheduleTemplatesRequest request) {
        // Pre：按机构与关键字分页加载 T2 模板。
        Page<ScheduleTemplate> page = templateQueryService.pageScheduleTemplates(
            request.getOrganizationId(),
            request.getKeyword(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<ScheduleTemplateDto> rows = new ArrayList<>();
        for (ScheduleTemplate entity : page.getContent()) {
            rows.add(scheduleTemplateMapper.toScheduleTemplateDto(entity));
        }
        // Asm：映射 DTO 并包装分页结果。
        return BookingPagedResultBuilder.build(new PageScheduleTemplatesResponse(), rows, page);
    }

    /**
     * T2 详情。
     */
    public ScheduleTemplateDto loadScheduleTemplate(@Valid LoadTemplateByIdRequest request) {
        // Pre：按主键加载 T2 模板。
        // Asm：映射 T2 详情 DTO。
        return scheduleTemplateMapper.toScheduleTemplateDto(
            templateQueryService.loadScheduleTemplateById(request.getId()));
    }

    /**
     * T2 保存。
     */
    public SaveTemplateResponse saveScheduleTemplate(@Valid SaveScheduleTemplateRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：持久化 T2 模板。
        ScheduleTemplate saved = templateDomainService.saveScheduleTemplate(
            scheduleTemplateMapper.toScheduleTemplateEntity(request), operatorId);
        // Asm：返回主键。
        SaveTemplateResponse response = new SaveTemplateResponse();
        response.setId(saved.getId());
        return response;
    }

    /**
     * T2 删除。
     */
    public void deleteScheduleTemplate(@Valid DeleteTemplateRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：软删 T2 模板。
        templateDomainService.deleteScheduleTemplate(request.getId(), operatorId);
    }

    /**
     * 从岗位导入 T2 日程模板草稿（2.24）。
     */
    public ImportFromPractitionerRoleResponse importFromPractitionerRole(
        @Valid ImportFromPractitionerRoleRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：从岗位加载出诊时段并解析为 T1 时段项。
        List<PractitionerRoleScheduleItemDto> items = healthPractitionerRoleReader.listScheduleItemsByPractitioner(
            request.getPractitionerId(), request.getOrganizationId());
        PractitionerRoleScheduleItemDto sourceItem = this.pickImportSourceItem(items);
        List<SlotProvisionTemplateItemDto> slotItems = PractitionerRoleImportSupport.parseTimePeriods(
            sourceItem.getTimePeriodsJson());

        String t1Code = "PR-T1-" + request.getPractitionerId();
        // Cmd：先保存 T1 再组装并保存 T2。
        SlotProvisionTemplate t1 = templateDomainService.saveSlotProvisionTemplate(
            this.buildImportedT1(request.getOrganizationId(), t1Code, slotItems), operatorId);

        String templateCode = request.getTemplateCode();
        if (templateCode == null || templateCode.isBlank()) {
            templateCode = "PR-" + request.getPractitionerId();
        }
        String templateName = request.getTemplateName();
        if (templateName == null || templateName.isBlank()) {
            templateName = "岗位导入-" + request.getPractitionerId();
        }
        ScheduleTemplate t2 = new ScheduleTemplate();
        t2.setOrganizationId(request.getOrganizationId());
        t2.setTemplateCode(templateCode.trim());
        t2.setTemplateName(templateName.trim());
        t2.setScheduleMode("MODE-A");
        t2.setPractitionerId(request.getPractitionerId());
        t2.setLocationId(sourceItem.getLocationId());
        t2.setHealthcareServiceId(sourceItem.getHealthcareServiceId());
        t2.setSlotProvisionTemplateId(t1.getId());
        t2.setSourcePractitionerRoleId(sourceItem.getAssignmentId());
        t2.setEnabled(Boolean.TRUE);
        t2.setPriority(0);
        ScheduleTemplate saved = templateDomainService.saveScheduleTemplate(t2, operatorId);

        // Asm：返回导入后的 T2 主键与编码。
        ImportFromPractitionerRoleResponse response = new ImportFromPractitionerRoleResponse();
        response.setScheduleTemplateId(saved.getId());
        response.setTemplateCode(saved.getTemplateCode());
        return response;
    }

    /**
     * TW 周模板分页（2.25）。
     */
    public PageWeekScheduleTemplatesResponse pageWeekScheduleTemplates(
        @Valid PageWeekScheduleTemplatesRequest request) {
        // Pre：按机构与关键字分页加载 TW 周模板头。
        Page<WeekScheduleTemplate> page = weekScheduleTemplateQueryService.pageWeekScheduleTemplates(
            request.getOrganizationId(),
            request.getKeyword(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<WeekScheduleTemplateDto> rows = new ArrayList<>();
        for (WeekScheduleTemplate entity : page.getContent()) {
            rows.add(this.loadWeekDto(entity.getId()));
        }
        // Asm：组装含明细的 TW DTO 并包装分页结果。
        return BookingPagedResultBuilder.build(new PageWeekScheduleTemplatesResponse(), rows, page);
    }

    /**
     * TW 周模板详情。
     */
    public WeekScheduleTemplateDto loadWeekScheduleTemplate(@Valid LoadTemplateByIdRequest request) {
        // Pre：按主键加载 TW 头与明细行。
        // Asm：映射 TW 周模板详情 DTO。
        return this.loadWeekDto(request.getId());
    }

    /**
     * TW 周模板保存。
     */
    public SaveTemplateResponse saveWeekScheduleTemplate(@Valid SaveWeekScheduleTemplateRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：持久化 TW 头与明细行。
        WeekScheduleTemplate saved = weekScheduleTemplateDomainService.saveWeekScheduleTemplate(
            weekScheduleTemplateMapper.toHeaderEntity(request),
            weekScheduleTemplateMapper.toItemEntities(request),
            operatorId);
        // Asm：返回主键。
        SaveTemplateResponse response = new SaveTemplateResponse();
        response.setId(saved.getId());
        return response;
    }

    /**
     * TW 周模板删除。
     */
    public void deleteWeekScheduleTemplate(@Valid DeleteTemplateRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：软删 TW 周模板。
        weekScheduleTemplateDomainService.deleteWeekScheduleTemplate(request.getId(), operatorId);
    }

    private WeekScheduleTemplateDto loadWeekDto(Long id) {
        WeekScheduleTemplate header = weekScheduleTemplateQueryService.loadWeekScheduleTemplateById(id);
        List<WeekScheduleTemplateItem> items = weekScheduleTemplateQueryService.listItemsByWeekTemplateId(id);
        List<ScheduleTemplate> dayTemplates = new ArrayList<>();
        for (WeekScheduleTemplateItem item : items) {
            if (item.getDayScheduleTemplateId() != null) {
                dayTemplates.add(templateQueryService.loadScheduleTemplateById(item.getDayScheduleTemplateId()));
            }
        }
        return weekScheduleTemplateMapper.toWeekScheduleTemplateDto(
            header, items, weekScheduleTemplateMapper.indexDayTemplates(dayTemplates));
    }

    private PractitionerRoleScheduleItemDto pickImportSourceItem(List<PractitionerRoleScheduleItemDto> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "岗位出诊模板为空");
        }
        for (PractitionerRoleScheduleItemDto item : items) {
            if (item.getHealthcareServiceId() != null && item.getTimePeriodsJson() != null
                && !item.getTimePeriodsJson().isBlank()) {
                return item;
            }
        }
        throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "岗位出诊模板缺少号源类型或时段");
    }

    private SlotProvisionTemplate buildImportedT1(
        Long organizationId, String templateCode, List<SlotProvisionTemplateItemDto> slotItems) {
        SlotProvisionTemplate entity = new SlotProvisionTemplate();
        entity.setOrganizationId(organizationId);
        entity.setTemplateCode(templateCode);
        entity.setTemplateName("岗位导入时段-" + templateCode);
        entity.setSlotTemplatesJson(TemplateJsonSupport.writeSlotItems(slotItems));
        entity.setEnabled(Boolean.TRUE);
        return entity;
    }
}

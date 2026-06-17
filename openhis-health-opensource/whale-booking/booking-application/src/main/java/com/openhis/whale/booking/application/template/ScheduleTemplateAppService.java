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
import com.openhis.whale.booking.contracts.port.BookingPractitionerRolePort;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 排班模板应用服务（T1/T2 CRUD，2.13～2.14）。
 *
 * <p>接入：POST /api/app/scheduleTemplate/{methodName}</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class ScheduleTemplateAppService {

    private final TemplateQueryService templateQueryService;
    private final TemplateDomainService templateDomainService;
    private final WeekScheduleTemplateQueryService weekScheduleTemplateQueryService;
    private final WeekScheduleTemplateDomainService weekScheduleTemplateDomainService;
    private final ScheduleTemplateMapper scheduleTemplateMapper;
    private final WeekScheduleTemplateMapper weekScheduleTemplateMapper;
    private final BookingOperatorResolver bookingOperatorResolver;
    private final BookingPractitionerRolePort bookingPractitionerRolePort;

    /**
     * T1 分页列表（2.13）。
     */
    public PageSlotProvisionTemplatesResponse pageSlotProvisionTemplates(
        @Valid PageSlotProvisionTemplatesRequest request) {
        Page<SlotProvisionTemplate> page = templateQueryService.pageSlotProvisionTemplates(
            request.getOrganizationId(),
            request.getKeyword(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<SlotProvisionTemplateDto> rows = new ArrayList<>();
        for (SlotProvisionTemplate entity : page.getContent()) {
            rows.add(scheduleTemplateMapper.toSlotProvisionTemplateDto(entity));
        }
        return BookingPagedResultBuilder.build(new PageSlotProvisionTemplatesResponse(), rows, page);
    }

    /**
     * T1 详情。
     */
    public SlotProvisionTemplateDto loadSlotProvisionTemplate(@Valid LoadTemplateByIdRequest request) {
        return scheduleTemplateMapper.toSlotProvisionTemplateDto(
            templateQueryService.loadSlotProvisionTemplateById(request.getId()));
    }

    /**
     * T1 保存。
     */
    @Transactional
    public SaveTemplateResponse saveSlotProvisionTemplate(@Valid SaveSlotProvisionTemplateRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        SlotProvisionTemplate saved = templateDomainService.saveSlotProvisionTemplate(
            scheduleTemplateMapper.toSlotProvisionTemplateEntity(request), operatorId);
        SaveTemplateResponse response = new SaveTemplateResponse();
        response.setId(saved.getId());
        return response;
    }

    /**
     * T1 删除。
     */
    @Transactional
    public void deleteSlotProvisionTemplate(@Valid DeleteTemplateRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        templateDomainService.deleteSlotProvisionTemplate(request.getId(), operatorId);
    }

    /**
     * T1 下拉（编辑 T2 时用）。
     */
    public ListSlotProvisionTemplateOptionsResponse listSlotProvisionTemplateOptions(
        ListSlotProvisionTemplateOptionsRequest request) {
        Long organizationId = null;
        if (request != null) {
            organizationId = request.getOrganizationId();
        }
        ListSlotProvisionTemplateOptionsResponse response = new ListSlotProvisionTemplateOptionsResponse();
        response.setOptions(scheduleTemplateMapper.toSlotProvisionOptions(
            templateQueryService.listEnabledSlotProvisionTemplates(organizationId)));
        return response;
    }

    /**
     * T2 分页列表（2.14）。
     */
    public PageScheduleTemplatesResponse pageScheduleTemplates(@Valid PageScheduleTemplatesRequest request) {
        Page<ScheduleTemplate> page = templateQueryService.pageScheduleTemplates(
            request.getOrganizationId(),
            request.getKeyword(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<ScheduleTemplateDto> rows = new ArrayList<>();
        for (ScheduleTemplate entity : page.getContent()) {
            rows.add(scheduleTemplateMapper.toScheduleTemplateDto(entity));
        }
        return BookingPagedResultBuilder.build(new PageScheduleTemplatesResponse(), rows, page);
    }

    /**
     * T2 详情。
     */
    public ScheduleTemplateDto loadScheduleTemplate(@Valid LoadTemplateByIdRequest request) {
        return scheduleTemplateMapper.toScheduleTemplateDto(
            templateQueryService.loadScheduleTemplateById(request.getId()));
    }

    /**
     * T2 保存。
     */
    @Transactional
    public SaveTemplateResponse saveScheduleTemplate(@Valid SaveScheduleTemplateRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        ScheduleTemplate saved = templateDomainService.saveScheduleTemplate(
            scheduleTemplateMapper.toScheduleTemplateEntity(request), operatorId);
        SaveTemplateResponse response = new SaveTemplateResponse();
        response.setId(saved.getId());
        return response;
    }

    /**
     * T2 删除。
     */
    @Transactional
    public void deleteScheduleTemplate(@Valid DeleteTemplateRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        templateDomainService.deleteScheduleTemplate(request.getId(), operatorId);
    }

    /**
     * 从岗位导入 T2 日程模板草稿（2.24）。
     */
    @Transactional
    public ImportFromPractitionerRoleResponse importFromPractitionerRole(
        @Valid ImportFromPractitionerRoleRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        List<PractitionerRoleScheduleItemDto> items = bookingPractitionerRolePort.listScheduleItemsByPractitioner(
            request.getPractitionerId(), request.getOrganizationId());
        PractitionerRoleScheduleItemDto sourceItem = this.pickImportSourceItem(items);
        List<SlotProvisionTemplateItemDto> slotItems = PractitionerRoleImportSupport.parseTimePeriods(
            sourceItem.getTimePeriodsJson());

        String t1Code = "PR-T1-" + request.getPractitionerId();
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
        Page<WeekScheduleTemplate> page = weekScheduleTemplateQueryService.pageWeekScheduleTemplates(
            request.getOrganizationId(),
            request.getKeyword(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<WeekScheduleTemplateDto> rows = new ArrayList<>();
        for (WeekScheduleTemplate entity : page.getContent()) {
            rows.add(this.loadWeekDto(entity.getId()));
        }
        return BookingPagedResultBuilder.build(new PageWeekScheduleTemplatesResponse(), rows, page);
    }

    /**
     * TW 周模板详情。
     */
    public WeekScheduleTemplateDto loadWeekScheduleTemplate(@Valid LoadTemplateByIdRequest request) {
        return this.loadWeekDto(request.getId());
    }

    /**
     * TW 周模板保存。
     */
    @Transactional
    public SaveTemplateResponse saveWeekScheduleTemplate(@Valid SaveWeekScheduleTemplateRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        WeekScheduleTemplate saved = weekScheduleTemplateDomainService.saveWeekScheduleTemplate(
            weekScheduleTemplateMapper.toHeaderEntity(request),
            weekScheduleTemplateMapper.toItemEntities(request),
            operatorId);
        SaveTemplateResponse response = new SaveTemplateResponse();
        response.setId(saved.getId());
        return response;
    }

    /**
     * TW 周模板删除。
     */
    @Transactional
    public void deleteWeekScheduleTemplate(@Valid DeleteTemplateRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
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

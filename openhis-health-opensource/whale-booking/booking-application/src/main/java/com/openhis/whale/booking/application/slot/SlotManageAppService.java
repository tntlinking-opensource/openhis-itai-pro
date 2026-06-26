package com.openhis.whale.booking.application.slot;

import com.openhis.whale.booking.application.slot.mapper.SlotManageMapper;
import com.openhis.whale.booking.application.slot.request.BlockSlotsRequest;
import com.openhis.whale.booking.application.slot.request.DeleteSlotsRequest;
import com.openhis.whale.booking.application.slot.request.LoadSlotDetailRequest;
import com.openhis.whale.booking.application.slot.request.PageSlotsByDateRequest;
import com.openhis.whale.booking.application.slot.request.SetOverbookedRequest;
import com.openhis.whale.booking.application.slot.request.UnblockSlotsRequest;
import com.openhis.whale.booking.application.slot.response.PageSlotsByDateResponse;
import com.openhis.whale.booking.application.slot.response.SlotDetailDto;
import com.openhis.whale.booking.application.slot.response.SlotListItemDto;
import com.openhis.whale.booking.application.support.BookingOperatorResolver;
import com.openhis.whale.booking.application.support.BookingPagedResultBuilder;
import com.openhis.whale.booking.contracts.dto.MasterDataOption;
import com.openhis.whale.booking.contracts.reader.HealthScheduleMasterDataReader;
import com.openhis.health.domain.share.enums.ScheduleActorKind;
import com.openhis.whale.booking.domain.schedule.entity.Schedule;
import com.openhis.whale.booking.domain.schedule.entity.ScheduleActor;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleActorDateModel;
import com.openhis.whale.booking.domain.schedule.repository.ScheduleActorRepository;
import com.openhis.whale.booking.domain.schedule.repository.ScheduleRepository;
import com.openhis.whale.booking.domain.schedule.service.ScheduleQueryService;
import com.openhis.whale.booking.domain.slot.command.BlockSlotCommand;
import com.openhis.whale.booking.domain.slot.command.SetOverbookedCommand;
import com.openhis.whale.booking.domain.slot.command.UnblockSlotCommand;
import com.openhis.whale.booking.domain.slot.queries.PageSlotsByOrganizationAndDateQuery;
import com.openhis.whale.booking.domain.slot.queries.model.SlotDetailModel;
import com.openhis.whale.booking.domain.slot.service.SlotDomainService;
import com.openhis.whale.booking.domain.slot.service.SlotQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 号源日历应用服务（IF 3.x）。
 *
 * <p>职责边界：号源分页查询、详情、封锁/解封/超售/软删编排；写侧委托 {@link SlotDomainService}。</p>
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/slotManage/{methodName}}；不另写 RestController。</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class SlotManageAppService {

    /** 号源读侧：按机构/日期/日程分页与详情。 */
    private final SlotQueryService slotQueryService;
    /** 号源写侧：封锁、解封、超售、软删。 */
    private final SlotDomainService slotDomainService;
    /** 日程读侧：按医师参与者收窄 scheduleId 列表。 */
    private final ScheduleQueryService scheduleQueryService;
    /** 日程仓储：号源详情回填挂号要素。 */
    private final ScheduleRepository scheduleRepository;
    /** 日程参与者仓储：解析接诊医师。 */
    private final ScheduleActorRepository scheduleActorRepository;
    /** Request/Command/DTO 映射。 */
    private final SlotManageMapper slotManageMapper;
    /** 排班主数据：解析医师/号源类型展示名。 */
    private final HealthScheduleMasterDataReader healthScheduleMasterDataReader;
    /** 写路径操作人解析。 */
    private final BookingOperatorResolver bookingOperatorResolver;

    /**
     * 按机构与日期分页查询号源（3.1）。
     *
     * @param request 查询条件
     * @return 分页列表
     */
    public PageSlotsByDateResponse pageSlotsByDate(@Valid PageSlotsByDateRequest request) {
        // Pre：若筛选医师，先解析当日关联 scheduleId 列表。
        List<Long> scheduleIds = this.resolveScheduleIdsForPractitionerFilter(request);
        PageSlotsByOrganizationAndDateQuery query = slotManageMapper.toPageSlotsQuery(request, scheduleIds);
        Page<SlotDetailModel> page = slotQueryService.pageSlotsByOrganizationAndDate(query);
        // Asm：读模型 → 列表 DTO + 分页包装。
        List<SlotListItemDto> rows = new ArrayList<>();
        List<SlotDetailModel> models = page.getContent();
        Map<Long, RegistrationContext> registrationByScheduleId = this.loadRegistrationContextByScheduleIds(
            this.collectScheduleIds(models));
        Map<Long, String> practitionerNames = this.loadPractitionerNameMap(request.getOrganizationId());
        Map<Long, String> healthcareServiceNames = this.loadHealthcareServiceNameMap();
        Map<Long, String> organizationNames = this.loadOrganizationNameMap();
        for (SlotDetailModel model : models) {
            RegistrationContext context = registrationByScheduleId.get(model.getScheduleId());
            String scheduleStatusEnum = null;
            if (context != null) {
                scheduleStatusEnum = context.scheduleStatusEnum();
            }
            SlotListItemDto dto = slotManageMapper.toSlotListItemDto(model, scheduleStatusEnum);
            this.applyRegistrationContext(
                dto, model, context, practitionerNames, healthcareServiceNames, organizationNames);
            rows.add(dto);
        }
        return BookingPagedResultBuilder.build(new PageSlotsByDateResponse(), rows, page);
    }

    /**
     * 加载号源详情（3.2）。
     *
     * @param request 详情请求
     * @return 详情 DTO
     */
    public SlotDetailDto loadSlotDetail(@Valid LoadSlotDetailRequest request) {
        // Pre：按主键加载号源读模型。
        SlotDetailModel model = slotQueryService.loadSlotById(request.getSlotId());
        // Asm：映射为详情 DTO。
        Map<Long, RegistrationContext> registrationByScheduleId = this.loadRegistrationContextByScheduleIds(
            this.collectScheduleIds(List.of(model)));
        RegistrationContext context = registrationByScheduleId.get(model.getScheduleId());
        String scheduleStatusEnum = null;
        if (context != null) {
            scheduleStatusEnum = context.scheduleStatusEnum();
        }
        SlotDetailDto dto = slotManageMapper.toSlotDetailDto(model, scheduleStatusEnum);
        Long organizationId = model.getOrganizationId();
        if (organizationId == null && context != null) {
            organizationId = context.organizationId();
        }
        Map<Long, String> practitionerNames = this.loadPractitionerNameMap(organizationId);
        Map<Long, String> healthcareServiceNames = this.loadHealthcareServiceNameMap();
        Map<Long, String> organizationNames = this.loadOrganizationNameMap();
        this.applyRegistrationContext(dto, model, context, practitionerNames, healthcareServiceNames, organizationNames);
        return dto;
    }

    /**
     * 批量封锁号源（3.3）。领域单次仅处理一条，应用层循环编排。
     *
     * @param request 封锁请求
     */
    public void blockSlots(@Valid BlockSlotsRequest request) {
        // Gate：写路径须登录操作人。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：领域单次仅处理一条，应用层循环编排批量封锁。
        for (Long slotId : request.getSlotIds()) {
            if (slotId == null) {
                continue;
            }
            BlockSlotCommand command = slotManageMapper.toBlockSlotCommand(slotId, request.getReason(), operatorId);
            slotDomainService.blockSlot(command);
        }
    }

    /**
     * 批量解封号源（3.3）。
     *
     * @param request 解封请求
     */
    public void unblockSlots(@Valid UnblockSlotsRequest request) {
        // Gate：写路径须登录操作人。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：领域单次仅处理一条，应用层循环编排批量解封。
        for (Long slotId : request.getSlotIds()) {
            if (slotId == null) {
                continue;
            }
            UnblockSlotCommand command = slotManageMapper.toUnblockSlotCommand(slotId, operatorId);
            slotDomainService.unblockSlot(command);
        }
    }

    /**
     * 设置超售标记（3.4）。
     */
    public void setOverbooked(@Valid SetOverbookedRequest request) {
        // Gate：写路径须登录操作人。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：设置号源超售标记。
        SetOverbookedCommand command = slotManageMapper.toSetOverbookedCommand(request, operatorId);
        slotDomainService.setOverbooked(command);
    }

    /**
     * 批量删除号源（软删，仅允许无预约占用的时段）。
     *
     * @param request 删除请求
     */
    public void deleteSlots(@Valid DeleteSlotsRequest request) {
        // Gate：写路径须登录操作人。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：逐条软删；领域层拒绝 bookedCount>0 的号源。
        for (Long slotId : request.getSlotIds()) {
            if (slotId == null) {
                continue;
            }
            slotDomainService.retireSlot(slotManageMapper.toRetireSlotCommand(slotId, operatorId));
        }
    }

    /**
     * 按医师参与者收窄日程主键；未选医师时不追加 IN 条件。
     *
     * @param request 分页查询请求
     * @return scheduleId 列表；{@code null} 表示不过滤；空列表表示该日无匹配日程
     */
    private List<Long> resolveScheduleIdsForPractitionerFilter(PageSlotsByDateRequest request) {
        // 未选医师：不按 schedule 收窄。
        if (request.getPractitionerId() == null) {
            return null;
        }
        List<ScheduleActorDateModel> actorSchedules = scheduleQueryService.findSchedulesByActorAndDate(
            slotManageMapper.practitionerActorType(),
            request.getPractitionerId(),
            request.getDate(),
            request.getOrganizationId());
        List<Long> scheduleIds = slotManageMapper.toScheduleIdsFromActorDateModels(actorSchedules);
        if (scheduleIds.isEmpty()) {
            return new ArrayList<>();
        }
        return scheduleIds;
    }

    /**
     * 收集号源关联的日程主键（去重）。
     */
    private Set<Long> collectScheduleIds(List<SlotDetailModel> models) {
        Set<Long> scheduleIds = new HashSet<>();
        if (models == null) {
            return scheduleIds;
        }
        for (SlotDetailModel model : models) {
            if (model != null && model.getScheduleId() != null) {
                scheduleIds.add(model.getScheduleId());
            }
        }
        return scheduleIds;
    }

    /**
     * 批量解析挂号台所需科室/医师/号源类型（优先 Schedule 字段，缺失时回退参与者）。
     */
    private Map<Long, RegistrationContext> loadRegistrationContextByScheduleIds(Set<Long> scheduleIds) {
        Map<Long, RegistrationContext> result = new HashMap<>();
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            return result;
        }
        int practitionerType = ScheduleActorKind.PRACTITIONER.getSortOrder().intValue();
        int healthcareServiceType = ScheduleActorKind.HEALTHCARE_SERVICE.getSortOrder().intValue();
        for (Long scheduleId : scheduleIds) {
            if (scheduleId == null) {
                continue;
            }
            Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
            if (schedule == null) {
                continue;
            }
            Long healthcareServiceId = schedule.getHealthcareServiceId();
            Long practitionerId = schedule.getSubstitutePractitionerId();
            List<ScheduleActor> actors = scheduleActorRepository.findByScheduleIdOrderBySortNoAsc(schedule.getId());
            for (ScheduleActor actor : actors) {
                if (actor == null || Boolean.TRUE.equals(actor.getIsDeleted())) {
                    continue;
                }
                if (practitionerId == null && practitionerType == actor.getActorType()) {
                    practitionerId = actor.getActorId();
                }
                if (healthcareServiceId == null && healthcareServiceType == actor.getActorType()) {
                    healthcareServiceId = actor.getActorId();
                }
            }
            result.put(scheduleId, new RegistrationContext(
                schedule.getOrganizationId(), practitionerId, healthcareServiceId, schedule.getStatusEnum()));
        }
        return result;
    }

    /**
     * 将挂号上下文写入号源 DTO（机构优先取号源冗余字段）。
     */
    private void applyRegistrationContext(
        SlotListItemDto dto,
        SlotDetailModel model,
        RegistrationContext context,
        Map<Long, String> practitionerNames,
        Map<Long, String> healthcareServiceNames,
        Map<Long, String> organizationNames) {
        if (dto == null || context == null) {
            return;
        }
        if (dto.getOrganizationId() == null && model != null) {
            dto.setOrganizationId(model.getOrganizationId());
        }
        if (dto.getOrganizationId() == null) {
            dto.setOrganizationId(context.organizationId());
        }
        dto.setPractitionerId(context.practitionerId());
        dto.setHealthcareServiceId(context.healthcareServiceId());
        dto.setPractitionerName(this.resolveDisplayName(practitionerNames, context.practitionerId()));
        dto.setHealthcareServiceName(this.resolveDisplayName(healthcareServiceNames, context.healthcareServiceId()));
        dto.setOrganizationName(this.resolveDisplayName(organizationNames, dto.getOrganizationId()));
    }

    private void applyRegistrationContext(
        SlotDetailDto dto,
        SlotDetailModel model,
        RegistrationContext context,
        Map<Long, String> practitionerNames,
        Map<Long, String> healthcareServiceNames,
        Map<Long, String> organizationNames) {
        if (dto == null || context == null) {
            return;
        }
        if (dto.getOrganizationId() == null && model != null) {
            dto.setOrganizationId(model.getOrganizationId());
        }
        if (dto.getOrganizationId() == null) {
            dto.setOrganizationId(context.organizationId());
        }
        dto.setPractitionerId(context.practitionerId());
        dto.setHealthcareServiceId(context.healthcareServiceId());
        dto.setPractitionerName(this.resolveDisplayName(practitionerNames, context.practitionerId()));
        dto.setHealthcareServiceName(this.resolveDisplayName(healthcareServiceNames, context.healthcareServiceId()));
        dto.setOrganizationName(this.resolveDisplayName(organizationNames, dto.getOrganizationId()));
    }

    private Map<Long, String> loadPractitionerNameMap(Long organizationId) {
        Map<Long, String> nameMap = new HashMap<>();
        if (organizationId == null) {
            return nameMap;
        }
        List<MasterDataOption> practitioners = healthScheduleMasterDataReader
            .loadOutpatientScheduleFormOptions(organizationId).getPractitioners();
        this.fillNameMap(nameMap, practitioners);
        return nameMap;
    }

    private Map<Long, String> loadHealthcareServiceNameMap() {
        Map<Long, String> nameMap = new HashMap<>();
        List<MasterDataOption> services = healthScheduleMasterDataReader
            .loadOutpatientScheduleFormOptions(null).getHealthcareServices();
        this.fillNameMap(nameMap, services);
        return nameMap;
    }

    private Map<Long, String> loadOrganizationNameMap() {
        Map<Long, String> nameMap = new HashMap<>();
        List<MasterDataOption> departments = healthScheduleMasterDataReader
            .loadOutpatientScheduleFormOptions(null).getDepartments();
        this.fillNameMap(nameMap, departments);
        return nameMap;
    }

    private void fillNameMap(Map<Long, String> nameMap, List<MasterDataOption> options) {
        if (nameMap == null || options == null) {
            return;
        }
        for (MasterDataOption option : options) {
            if (option == null || option.getValue() == null) {
                continue;
            }
            try {
                nameMap.put(Long.parseLong(option.getValue()), option.getLabel());
            } catch (NumberFormatException ignored) {
                // skip invalid option
            }
        }
    }

    private String resolveDisplayName(Map<Long, String> nameMap, Long id) {
        if (nameMap == null || id == null) {
            return null;
        }
        return nameMap.get(id);
    }

    /**
     * 日程维度的挂号三要素快照及日程状态（用于停诊展示与可约判定）。
     */
    private record RegistrationContext(
        Long organizationId,
        Long practitionerId,
        Long healthcareServiceId,
        String scheduleStatusEnum) {
    }
}

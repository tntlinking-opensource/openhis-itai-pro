/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.administration.location.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.administration.location.command.*;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.repository.LocationRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.events.administration.location.*;
import com.openhis.health.domain.share.utils.CommandBatchUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import org.springframework.stereotype.Service;

import java.util.*;
import lombok.RequiredArgsConstructor;

/**
 * 位置写侧领域服务：登记、启停、床位运营与层级 busNo 生成。
 *
 * <p>对应 DM-ADM-003；事件在 {@code saveAll} 之后发布，避免未落库即通知下游。</p>
 */
@Service
@RequiredArgsConstructor
public class LocationDomainService {

    private final LocationRepository locationRepository;
    private final IDomainEventPublisher domainEventPublisher;
    private final BusinessSequenceService businessSequenceService;

    /**
     * 登记一条或多条位置主数据，生成层级 busNo 并发布登记事件。
     *
     * @param command 登记命令，含批量 item
     * @return 首条新位置主键
     * @throws BusinessException 同父级重名、父级不存在等
     */
    public Long registerLocation(RegisterLocationCommand command) {
        List<RegisterLocationCommand.RegisterLocationItemCommand> items = command.resolveItems();
        Map<Long, Location> parentMap = this.loadParentMapForBusNo(items);
        List<Location> toSave = new ArrayList<>();
        for (RegisterLocationCommand.RegisterLocationItemCommand item : items) {
            this.assertNameNotDuplicated(item.getParentId(), item.getName());
            Location location = this.buildNewLocation(item, parentMap);
            toSave.add(location);
        }
        List<Location> saved = locationRepository.saveAll(toSave);
        Map<Long, String> parentNameById = this.loadParentNameMap(saved);
        this.publishRegisterEvents(items, saved, parentNameById);
        return saved.get(0).getId();
    }

    /**
     * 根据登记项组装待持久化位置（含初始状态与 busNo）。
     *
     * @param item      登记项
     * @param parentMap 父级位置索引，供 busNo 拼接
     * @return 未持久化的位置聚合根
     */
    private Location buildNewLocation(
        RegisterLocationCommand.RegisterLocationItemCommand item,
        Map<Long, Location> parentMap) {
        Location location = new Location();
        location.setName(item.getName());
        location.setParentId(item.getParentId());
        location.setFormEnum(item.getFormEnum());
        location.setModeEnum(item.getModeEnum());
        location.setTypeJson(item.getTypeJson());
        location.setOrganizationId(item.getOrganizationId());
        location.setDisplayOrder(item.getDisplayOrder());
        location.setPyStr(item.getPyStr());
        location.setWbStr(item.getWbStr());
        // 病区床位主数据登记后直接启用；库房/药房等同理，由应用层显式设置 registerAsActive。
        if (Boolean.TRUE.equals(item.getRegisterAsActive())) {
            location.initActive();
        } else {
            location.initDraft();
        }
        location.assertRegisterRequiredFields();
        location.setBusNo(this.buildBusNo(item.getParentId(), parentMap));
        return location;
    }

    /**
     * 登记落库后发布领域事件（批量预加载父级名称，避免循环内单条查询）。
     *
     * @param items           与 saved 下标对齐的登记项
     * @param saved           已持久化位置
     * @param parentNameById  父级 id → 名称
     */
    private void publishRegisterEvents(
        List<RegisterLocationCommand.RegisterLocationItemCommand> items,
        List<Location> saved,
        Map<Long, String> parentNameById) {
        for (int index = 0; index < items.size(); index++) {
            RegisterLocationCommand.RegisterLocationItemCommand item = items.get(index);
            Location location = saved.get(index);
            LocationRegisteredEvent registered = new LocationRegisteredEvent(
                location.getId(), location.getName(), location.getFormEnum(), location.getOrganizationId());
            registered.setLocationBusNo(location.getBusNo());
            registered.setWardId(location.getParentId());
            if (location.getParentId() != null) {
                registered.setWardName(parentNameById.get(location.getParentId()));
            }
            domainEventPublisher.publish(registered);
            if (Boolean.TRUE.equals(item.getRegisterAsActive())) {
                domainEventPublisher.publish(new LocationActivatedEvent(
                    location.getId(), location.getName(), location.getFormEnum()));
            }
        }
    }

    /**
     * 批量加载父级位置名称，供登记事件填充病区展示名。
     *
     * @param saved 新登记位置列表
     * @return 父级 id → 名称
     */
    private Map<Long, String> loadParentNameMap(List<Location> saved) {
        Set<Long> parentIds = new LinkedHashSet<>();
        for (Location location : saved) {
            if (location.getParentId() != null) {
                parentIds.add(location.getParentId());
            }
        }
        if (parentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> nameById = new HashMap<>();
        List<Location> parents = locationRepository.findAllById(parentIds);
        for (Location parent : parents) {
            if (parent == null || Boolean.TRUE.equals(parent.getIsDeleted())) {
                continue;
            }
            nameById.put(parent.getId(), parent.getName());
        }
        return nameById;
    }

    /**
     * 启用位置。
     */
    public void activateLocation(ActivateLocationCommand command) {
        List<ActivateLocationCommand.ActivateLocationItemCommand> items = command.resolveItems();
        Map<Long, Location> map = this.loadLocationMap(CommandBatchUtil.extractIds(items, ActivateLocationCommand.ActivateLocationItemCommand::getLocationId));
        List<Location> toSave = new ArrayList<>();
        for (ActivateLocationCommand.ActivateLocationItemCommand item : items) {
            Location location = this.requireLocation(map, item.getLocationId());
            location.assertActivatable();
            location.activate();
            toSave.add(location);
        }
        locationRepository.saveAll(toSave);
        for (ActivateLocationCommand.ActivateLocationItemCommand item : items) {
            Location location = this.requireLocation(map, item.getLocationId());
            domainEventPublisher.publish(new LocationActivatedEvent(location.getId(), location.getName(), location.getFormEnum()));
        }
    }

    /**
     * 编辑位置信息。
     */
    public void editLocationInfo(EditLocationInfoCommand command) {
        List<EditLocationInfoCommand.EditLocationInfoItemCommand> items = command.resolveItems();
        Map<Long, Location> map = this.loadLocationMap(CommandBatchUtil.extractIds(items, EditLocationInfoCommand.EditLocationInfoItemCommand::getLocationId));
        List<Location> toSave = new ArrayList<>();
        for (EditLocationInfoCommand.EditLocationInfoItemCommand item : items) {
            Location location = this.requireLocation(map, item.getLocationId());
            // 业务分支处理。
            if (!Objects.equals(location.getName(), item.getName())) {
                this.assertNameNotDuplicated(location.getParentId(), item.getName());
            }
            location.setName(item.getName());
            location.setFormEnum(item.getFormEnum());
            location.setModeEnum(item.getModeEnum());
            location.setTypeJson(item.getTypeJson());
            location.setDisplayOrder(item.getDisplayOrder());
            location.setPyStr(item.getPyStr());
            location.setWbStr(item.getWbStr());
            toSave.add(location);
        }
        locationRepository.saveAll(toSave);
        for (EditLocationInfoCommand.EditLocationInfoItemCommand item : items) {
            domainEventPublisher.publish(new LocationInfoEditedEvent(item.getLocationId(), item.getChangedFields()));
        }
    }

    /**
     * 暂停位置。
     */
    public void suspendLocation(SuspendLocationCommand command) {
        List<SuspendLocationCommand.SuspendLocationItemCommand> items = command.resolveItems();
        Map<Long, Location> map = this.loadLocationMap(CommandBatchUtil.extractIds(items, SuspendLocationCommand.SuspendLocationItemCommand::getLocationId));
        List<Location> toSave = new ArrayList<>();
        for (SuspendLocationCommand.SuspendLocationItemCommand item : items) {
            Location location = this.requireLocation(map, item.getLocationId());
            location.assertSuspendable();
            location.suspend();
            toSave.add(location);
        }
        locationRepository.saveAll(toSave);
        for (SuspendLocationCommand.SuspendLocationItemCommand item : items) {
            domainEventPublisher.publish(new LocationSuspendedEvent(item.getLocationId(), item.getReason()));
        }
    }

    /**
     * 恢复位置。
     */
    public void resumeLocation(ResumeLocationCommand command) {
        List<ResumeLocationCommand.ResumeLocationItemCommand> items = command.resolveItems();
        Map<Long, Location> map = this.loadLocationMap(CommandBatchUtil.extractIds(items, ResumeLocationCommand.ResumeLocationItemCommand::getLocationId));
        List<Location> toSave = new ArrayList<>();
        for (ResumeLocationCommand.ResumeLocationItemCommand item : items) {
            Location location = this.requireLocation(map, item.getLocationId());
            location.assertResumable();
            location.activate();
            toSave.add(location);
        }
        locationRepository.saveAll(toSave);
        for (ResumeLocationCommand.ResumeLocationItemCommand item : items) {
            domainEventPublisher.publish(new LocationResumedEvent(item.getLocationId()));
        }
    }

    /**
     * 停用位置。
     */
    public void deactivateLocation(DeactivateLocationCommand command) {
        List<DeactivateLocationCommand.DeactivateLocationItemCommand> items = command.resolveItems();
        Map<Long, Location> map = this.loadLocationMap(CommandBatchUtil.extractIds(items, DeactivateLocationCommand.DeactivateLocationItemCommand::getLocationId));
        List<Location> toSave = new ArrayList<>();
        for (DeactivateLocationCommand.DeactivateLocationItemCommand item : items) {
            Location location = this.requireLocation(map, item.getLocationId());
            // 库房/药房为扁平主数据，无层级子节点，跳过树形停用门禁。
            if (!this.isWarehouseOrPharmacyForm(location.getFormEnum())) {
                this.assertNoActiveChildren(location.getId());
            }
            location.assertDeactivatable();
            location.deactivate();
            toSave.add(location);
        }
        locationRepository.saveAll(toSave);
        for (DeactivateLocationCommand.DeactivateLocationItemCommand item : items) {
            Location location = this.requireLocation(map, item.getLocationId());
            domainEventPublisher.publish(new LocationDeactivatedEvent(location.getId(), item.getReason(), location.getFormEnum()));
        }
    }

    /**
     * 废弃位置。
     */
    public void abolishLocation(AbolishLocationCommand command) {
        List<AbolishLocationCommand.AbolishLocationItemCommand> items = command.resolveItems();
        Map<Long, Location> map = this.loadLocationMap(CommandBatchUtil.extractIds(items, AbolishLocationCommand.AbolishLocationItemCommand::getLocationId));
        List<Location> toSave = new ArrayList<>();
        for (AbolishLocationCommand.AbolishLocationItemCommand item : items) {
            Location location = this.requireLocation(map, item.getLocationId());
            // 库房/药房为扁平主数据，启用态也可直接逻辑删除；树形位置仍须草稿/停用。
            if (this.isWarehouseOrPharmacyForm(location.getFormEnum())) {
                location.assertAbolishableAsFlatInventoryNode();
            } else {
                location.assertAbolishable();
            }
            location.markDeleted(command.getOperatorId());
            toSave.add(location);
        }
        locationRepository.saveAll(toSave);
        for (AbolishLocationCommand.AbolishLocationItemCommand item : items) {
            domainEventPublisher.publish(new LocationAbolishedEvent(item.getLocationId()));
        }
    }

    /**
     * 占用床位。
     */
    public void occupyBed(OccupyBedCommand command) {
        List<OccupyBedCommand.OccupyBedItemCommand> items = command.resolveItems();
        List<OccupyBedCommand.OccupyBedItemCommand> sortedItems = new ArrayList<>(items);
        sortedItems.sort(Comparator.comparing(
            OccupyBedCommand.OccupyBedItemCommand::getLocationId,
            Comparator.nullsLast(Comparator.naturalOrder())));
        List<Location> toSave = new ArrayList<>();
        for (OccupyBedCommand.OccupyBedItemCommand item : sortedItems) {
            Location location = this.requireBedForUpdate(item.getLocationId());
            location.assertPatientAssignable();
            if (Boolean.TRUE.equals(item.getOccupyFromReservation())) {
                location.assertReservedForOccupy();
            } else {
                location.assertVacantForOccupy();
            }
            location.occupy();
            toSave.add(location);
        }
        locationRepository.saveAll(toSave);
        for (OccupyBedCommand.OccupyBedItemCommand item : items) {
            domainEventPublisher.publish(new BedOccupiedEvent(item.getLocationId(), item.getEncounterId()));
        }
    }

    /**
     * 释放床位。
     */
    public void releaseBed(ReleaseBedCommand command) {
        List<ReleaseBedCommand.ReleaseBedItemCommand> items = command.resolveItems();
        List<ReleaseBedCommand.ReleaseBedItemCommand> sortedItems = new ArrayList<>(items);
        sortedItems.sort(Comparator.comparing(
            ReleaseBedCommand.ReleaseBedItemCommand::getLocationId,
            Comparator.nullsLast(Comparator.naturalOrder())));
        List<Location> toSave = new ArrayList<>();
        for (ReleaseBedCommand.ReleaseBedItemCommand item : sortedItems) {
            Location location = this.requireBedForUpdate(item.getLocationId());
            location.assertOccupiedForRelease();
            location.release();
            toSave.add(location);
        }
        locationRepository.saveAll(toSave);
        for (ReleaseBedCommand.ReleaseBedItemCommand item : items) {
            domainEventPublisher.publish(new BedReleasedEvent(item.getLocationId(), item.getEncounterId()));
        }
    }

    /**
     * 互换床运营态：双床按 id 排序加锁后 release + 交叉 occupy 一次落库（GAP-ATD-003）。
     *
     * @param releaseCommand 双方原床释放项
     * @param occupyCommand  交叉占床项（A→bedB，B→bedA）
     */
    public void swapBedsOperational(ReleaseBedCommand releaseCommand, OccupyBedCommand occupyCommand) {
        List<ReleaseBedCommand.ReleaseBedItemCommand> releaseItems = releaseCommand.resolveItems();
        List<OccupyBedCommand.OccupyBedItemCommand> occupyItems = occupyCommand.resolveItems();
        Set<Long> bedIds = new LinkedHashSet<>();
        for (ReleaseBedCommand.ReleaseBedItemCommand item : releaseItems) {
            if (item.getLocationId() != null) {
                bedIds.add(item.getLocationId());
            }
        }
        for (OccupyBedCommand.OccupyBedItemCommand item : occupyItems) {
            if (item.getLocationId() != null) {
                bedIds.add(item.getLocationId());
            }
        }
        List<Long> sortedBedIds = new ArrayList<>(bedIds);
        sortedBedIds.sort(Comparator.naturalOrder());
        Map<Long, Location> lockedById = new LinkedHashMap<>();
        for (Long bedId : sortedBedIds) {
            lockedById.put(bedId, this.requireBedForUpdate(bedId));
        }
        for (ReleaseBedCommand.ReleaseBedItemCommand item : releaseItems) {
            Location location = this.requireLockedBed(lockedById, item.getLocationId());
            location.assertOccupiedForRelease();
            location.release();
        }
        List<OccupyBedCommand.OccupyBedItemCommand> sortedOccupyItems = new ArrayList<>(occupyItems);
        sortedOccupyItems.sort(Comparator.comparing(
            OccupyBedCommand.OccupyBedItemCommand::getLocationId,
            Comparator.nullsLast(Comparator.naturalOrder())));
        for (OccupyBedCommand.OccupyBedItemCommand item : sortedOccupyItems) {
            Location location = this.requireLockedBed(lockedById, item.getLocationId());
            location.assertPatientAssignable();
            location.assertVacantForOccupy();
            location.occupy();
        }
        locationRepository.saveAll(new ArrayList<>(lockedById.values()));
        for (ReleaseBedCommand.ReleaseBedItemCommand item : releaseItems) {
            domainEventPublisher.publish(new BedReleasedEvent(item.getLocationId(), item.getEncounterId()));
        }
        for (OccupyBedCommand.OccupyBedItemCommand item : occupyItems) {
            domainEventPublisher.publish(new BedOccupiedEvent(item.getLocationId(), item.getEncounterId()));
        }
    }

    /**
     * 预留床位（GAP-ATD-007：悲观行锁 + {@code NOT_IN_USE → RESERVED}）。
     */
    public void reserveBed(ReserveBedCommand command) {
        List<ReserveBedCommand.ReserveBedItemCommand> items = command.resolveItems();
        List<ReserveBedCommand.ReserveBedItemCommand> sortedItems = new ArrayList<>(items);
        sortedItems.sort(Comparator.comparing(
            ReserveBedCommand.ReserveBedItemCommand::getLocationId,
            Comparator.nullsLast(Comparator.naturalOrder())));
        List<Location> toSave = new ArrayList<>();
        for (ReserveBedCommand.ReserveBedItemCommand item : sortedItems) {
            Location location = this.requireBedForUpdate(item.getLocationId());
            location.assertPatientAssignable();
            location.assertVacantForReserve();
            location.reserve();
            toSave.add(location);
        }
        if (!toSave.isEmpty()) {
            locationRepository.saveAll(toSave);
        }
        for (ReserveBedCommand.ReserveBedItemCommand item : sortedItems) {
            domainEventPublisher.publish(new BedReservedEvent(item.getLocationId(), item.getReason()));
        }
    }

    /**
     * 释放床位预留（{@code RESERVED → NOT_IN_USE}，取消/入院确认前释床用）。
     */
    public void releaseReservedBed(ReleaseBedCommand command) {
        List<ReleaseBedCommand.ReleaseBedItemCommand> items = command.resolveItems();
        List<ReleaseBedCommand.ReleaseBedItemCommand> sortedItems = new ArrayList<>(items);
        sortedItems.sort(Comparator.comparing(
            ReleaseBedCommand.ReleaseBedItemCommand::getLocationId,
            Comparator.nullsLast(Comparator.naturalOrder())));
        List<Location> toSave = new ArrayList<>();
        for (ReleaseBedCommand.ReleaseBedItemCommand item : sortedItems) {
            Location location = this.requireBedForUpdate(item.getLocationId());
            location.assertReservedForRelease();
            location.release();
            toSave.add(location);
        }
        if (!toSave.isEmpty()) {
            locationRepository.saveAll(toSave);
        }
        for (ReleaseBedCommand.ReleaseBedItemCommand item : sortedItems) {
            domainEventPublisher.publish(new BedReleasedEvent(item.getLocationId(), item.getEncounterId()));
        }
    }

    /**
     * 判断形态是否为库房或药房（扁平库存节点，无树形子节点约束）。
     *
     * @param formCode 形态编码
     * @return 是否库房/药房
     */
    private boolean isWarehouseOrPharmacyForm(String formCode) {
        return LocationForm.WAREHOUSE.getCode().equals(formCode)
            || LocationForm.PHARMACY.getCode().equals(formCode);
    }

    /**
     * 校验同父级下名称唯一。
     *
     * @param parentId 父级位置主键，根节点为 null
     * @param name     位置名称
     */
    private void assertNameNotDuplicated(Long parentId, String name) {
        // 业务分支处理。
        if (locationRepository.existsByParentIdAndName(parentId, name)) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_NAME_CONFLICT, "同父级下位置名称不可重复");
        }
    }

    /**
     * 停用前校验不存在启用或暂停中的子节点。
     *
     * @param parentId 父级位置主键
     */
    private void assertNoActiveChildren(Long parentId) {
        List<String> status = Arrays.asList(LocationStatus.ACTIVE.getCode(), LocationStatus.SUSPENDED.getCode());
        // 业务分支处理。
        if (!locationRepository.findAllByParentIdAndStatusEnumIn(parentId, status).isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_RULE_CONFLICT, "存在启用/暂停子节点时父级不可停用");
        }
    }

    /**
     * 校验目标位置为床位形态。
     *
     * @param location 位置聚合根
     */
    private void assertBedLocation(Location location) {
        // 委托聚合根 assertIsBed() 完成床位形态校验，遵守 §D3.2 硬门禁。
        location.assertIsBed();
    }

    /**
     * 按主键悲观锁加载床位，供占床路径消除读 Gate 与写之间的 TOCTOU。
     *
     * @param locationId 床位主键
     * @return 床位聚合根
     */
    private Location requireBedForUpdate(Long locationId) {
        Location location = locationRepository
            .findByIdAndFormEnumAndIsDeletedFalse(locationId, LocationForm.BED.getCode())
            .orElse(null);
        if (location == null) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_NOT_FOUND, "位置不存在");
        }
        return location;
    }

    /**
     * 从互换床预锁映射中取出床位。
     *
     * @param lockedById 已加锁床位映射
     * @param locationId 床位主键
     * @return 床位聚合根
     */
    private Location requireLockedBed(Map<Long, Location> lockedById, Long locationId) {
        Location location = lockedById.get(locationId);
        if (location == null) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_NOT_FOUND, "位置不存在");
        }
        return location;
    }

    /**
     * 生成位置层级 busNo：顶级为 {@link AggregateTypeConstants#LOCATION} 采番段，子级为「父 busNo + . + 本段」。
     *
     * @param parentId  父位置主键，根节点为 null
     * @param parentMap 已加载的父位置映射
     * @return 层级编码
     */
    private String buildBusNo(Long parentId, Map<Long, Location> parentMap) {
        String segment = this.allocateLocationBusNoSegment();
        if (parentId == null) {
            return segment;
        }
        Location parent = parentMap.get(parentId);
        if (parent == null) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_NOT_FOUND, "父级位置不存在");
        }
        return parent.getBusNo() + GeneralSharedConstants.POINT + segment;
    }

    /**
     * 取位置业务序号段（含聚合前缀，如 LO0010）。
     *
     * @return 本节点 busNo 末段
     */
    private String allocateLocationBusNoSegment() {
        String prefix = AggregateTypeConstants.LOCATION;
        return businessSequenceService.getSeq(prefix, BusinessSequenceConstants.CASCADE_BUS_NO_SEGMENT_SEQ_LENGTH);
    }

    /**
     * 批量登记前预加载父级位置，供层级 busNo 拼接。
     *
     * @param items 登记项
     * @return 父级 id → 位置
     */
    private Map<Long, Location> loadParentMapForBusNo(
        List<RegisterLocationCommand.RegisterLocationItemCommand> items) {
        List<Long> parentIds = new ArrayList<>();
        for (RegisterLocationCommand.RegisterLocationItemCommand item : items) {
            if (item.getParentId() != null) {
                parentIds.add(item.getParentId());
            }
        }
        if (parentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return this.loadLocationMap(parentIds);
    }

    /**
     * 按主键批量加载未软删位置，供写操作批处理使用。
     *
     * @param ids 位置主键列表
     * @return id → 位置
     */
    private Map<Long, Location> loadLocationMap(List<Long> ids) {
        List<Location> locations = locationRepository.findAllById(ids);
        Map<Long, Location> map = new HashMap<>();
        for (Location location : locations) {
            // 业务分支处理。
            if (!Boolean.TRUE.equals(location.getIsDeleted())) {
                map.put(location.getId(), location);
            }
        }
        return map;
    }

    /**
     * 从预加载映射中取出位置，不存在时抛业务异常。
     *
     * @param map        位置映射
     * @param locationId 位置主键
     * @return 位置聚合根
     */
    private Location requireLocation(Map<Long, Location> map, Long locationId) {
        Location location = map.get(locationId);
        // 业务分支处理。
        if (location == null) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_NOT_FOUND, "位置不存在");
        }
        return location;
    }
}


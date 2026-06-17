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

package com.openhis.health.domain.administration.organization.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.administration.organization.command.*;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.repository.OrganizationRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.events.administration.organization.*;
import com.openhis.health.domain.share.utils.CommandBatchUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 机构写侧领域服务。
 */
@Service
@RequiredArgsConstructor
public class OrganizationDomainService {

    private final OrganizationRepository organizationRepository;
    private final IDomainEventPublisher domainEventPublisher;
    private final BusinessSequenceService businessSequenceService;

    /**
     * 创建机构。
     */
    public Long createOrganization(CreateOrganizationCommand cmd) {
        List<CreateOrganizationCommand.CreateOrganizationItemCommand> items = cmd.resolveItems();
        Map<Long, Organization> parentMap = this.loadParentMapForBusNo(items);
        List<Organization> toSave = new ArrayList<>();
        for (CreateOrganizationCommand.CreateOrganizationItemCommand item : items) {
            this.assertNameUnique(item.getParentId(), item.getName(), null);
            Organization entity = new Organization();
            entity.setName(item.getName());
            entity.setParentId(item.getParentId());
            entity.setTypeEnum(item.getTypeEnum());
            entity.setClassEnum(item.getClassEnum());
            entity.setDisplayOrder(item.getDisplayOrder());
            entity.setDefDoctorId(item.getDefDoctorId());
            entity.setCaty(item.getCaty());
            this.fillInputCode(entity, item.getInputCode());
            this.fillInsurance(entity, item.getInsuranceInfo());
            entity.initAsActive();
            entity.assertCreateAllowed();
            if (StringUtils.isBlank(item.getBusNo())) {
                entity.setBusNo(this.buildBusNo(item.getParentId(), parentMap));
            } else {
                entity.setBusNo(item.getBusNo());
            }
            toSave.add(entity);
        }
        List<Organization> saved = organizationRepository.saveAll(toSave);
        for (Organization organization : saved) {
            domainEventPublisher.publish(new OrganizationCreatedEvent(
                organization.getId(), organization.getName(), organization.getTypeEnum(), organization.getBusNo(), organization.getParentId()));
        }
        return saved.get(0).getId();
    }

    /**
     * 发布机构。
     */
    public void publishOrganization(PublishOrganizationCommand cmd) {
        List<PublishOrganizationCommand.PublishOrganizationItemCommand> items = cmd.resolveItems();
        Map<Long, Organization> map = this.loadOrganizationMap(CommandBatchUtil.extractIds(items, PublishOrganizationCommand.PublishOrganizationItemCommand::getOrganizationId));
        List<Organization> toSave = new ArrayList<>();
        for (PublishOrganizationCommand.PublishOrganizationItemCommand item : items) {
            Organization entity = this.requireOrganization(map, item.getOrganizationId());
            entity.assertPublishAllowed();
            entity.transitToActive();
            toSave.add(entity);
        }
        organizationRepository.saveAll(toSave);
        for (PublishOrganizationCommand.PublishOrganizationItemCommand item : items) {
            Organization entity = this.requireOrganization(map, item.getOrganizationId());
            domainEventPublisher.publish(new OrganizationPublishedEvent(
                entity.getId(), entity.getName(), entity.getTypeEnum(), entity.getClassEnum()));
        }
    }

    /**
     * 修改机构。
     */
    public void modifyOrganization(ModifyOrganizationCommand cmd) {
        List<ModifyOrganizationCommand.ModifyOrganizationItemCommand> items = cmd.resolveItems();
        Map<Long, Organization> map = this.loadOrganizationMap(CommandBatchUtil.extractIds(items, ModifyOrganizationCommand.ModifyOrganizationItemCommand::getOrganizationId));
        List<Organization> toSave = new ArrayList<>();
        for (ModifyOrganizationCommand.ModifyOrganizationItemCommand item : items) {
            Organization entity = this.requireOrganization(map, item.getOrganizationId());
            entity.assertModifiable();
            // 业务分支处理。
            if (!Objects.equals(entity.getName(), item.getName())) {
                this.assertNameUnique(entity.getParentId(), item.getName(), entity.getId());
            }
            entity.setName(item.getName());
            if (StringUtils.isNotBlank(item.getBusNo())) {
                entity.setBusNo(item.getBusNo());
            }
            entity.setClassEnum(item.getClassEnum());
            entity.setDisplayOrder(item.getDisplayOrder());
            entity.setDefDoctorId(item.getDefDoctorId());
            entity.setCaty(item.getCaty());
            this.fillInputCode(entity, item.getInputCode());
            this.fillInsurance(entity, item.getInsuranceInfo());
            toSave.add(entity);
        }
        organizationRepository.saveAll(toSave);
        for (ModifyOrganizationCommand.ModifyOrganizationItemCommand item : items) {
            domainEventPublisher.publish(new OrganizationEditedEvent(item.getOrganizationId(), item.getChangedFields()));
        }
    }

    /**
     * 调整机构上级。
     */
    public void relocateOrganization(RelocateOrganizationCommand cmd) {
        List<RelocateOrganizationCommand.RelocateOrganizationItemCommand> items = cmd.resolveItems();
        Map<Long, Organization> map = this.loadOrganizationMap(CommandBatchUtil.extractIds(items, RelocateOrganizationCommand.RelocateOrganizationItemCommand::getOrganizationId));
        Map<Long, Organization> parentMap = this.loadParentMapForRelocate(items);
        Map<Long, Organization> toSaveById = new LinkedHashMap<>();
        List<OrganizationHierarchyChangedEvent> hierarchyEvents = new ArrayList<>();
        for (RelocateOrganizationCommand.RelocateOrganizationItemCommand item : items) {
            Organization entity = this.requireOrganization(map, item.getOrganizationId());
            entity.assertRelocateAllowed();
            // 业务分支处理。
            if (item.getOrganizationId().equals(item.getNewParentId())) {
                throw new BusinessException(ErrorCodeConstants.ORGANIZATION_RULE_CONFLICT, "新上级不可为自身");
            }
            this.assertNotCycle(item.getOrganizationId(), item.getNewParentId());
            Long oldParentId = entity.getParentId();
            String oldBusNo = entity.getBusNo();
            String localSegment;
            int lastDotIndex = oldBusNo.lastIndexOf(GeneralSharedConstants.POINT);
            if (lastDotIndex < 0) {
                localSegment = oldBusNo;
            } else {
                localSegment = oldBusNo.substring(lastDotIndex + 1);
            }
            String newBusNo = this.buildRelocatedBusNo(item.getNewParentId(), localSegment, parentMap);
            entity.setParentId(item.getNewParentId());
            entity.setBusNo(newBusNo);
            toSaveById.put(entity.getId(), entity);
            // 后代 busNo 前缀随本节点迁移，保持本级末段不变（BR-ORG-002）。
            String oldPrefix = oldBusNo + GeneralSharedConstants.POINT;
            List<Organization> descendants = organizationRepository.findAllByBusNoStartingWith(oldPrefix);
            for (Organization descendant : descendants) {
                String descendantBusNo = descendant.getBusNo();
                String suffix = descendantBusNo.substring(oldBusNo.length());
                descendant.setBusNo(newBusNo + suffix);
                toSaveById.put(descendant.getId(), descendant);
            }
            hierarchyEvents.add(new OrganizationHierarchyChangedEvent(
                entity.getId(), oldParentId, item.getNewParentId(), newBusNo));
        }
        organizationRepository.saveAll(new ArrayList<>(toSaveById.values()));
        for (OrganizationHierarchyChangedEvent event : hierarchyEvents) {
            domainEventPublisher.publish(event);
        }
    }

    /**
     * 停用机构。
     */
    public void retireOrganization(RetireOrganizationCommand cmd) {
        List<RetireOrganizationCommand.RetireOrganizationItemCommand> items = cmd.resolveItems();
        Map<Long, Organization> map = this.loadOrganizationMap(CommandBatchUtil.extractIds(items, RetireOrganizationCommand.RetireOrganizationItemCommand::getOrganizationId));
        List<Organization> toSave = new ArrayList<>();
        for (RetireOrganizationCommand.RetireOrganizationItemCommand item : items) {
            Organization entity = this.requireOrganization(map, item.getOrganizationId());
            entity.assertDeactivatable();
            // 存在启用子节点时不允许停用上级机构。
            if (organizationRepository.existsByParentIdAndStatusEnum(entity.getId(), PublicationStatus.ACTIVE.getCode())) {
                throw new BusinessException(ErrorCodeConstants.ORGANIZATION_RULE_CONFLICT, "存在启用子节点，机构不可停用");
            }
            entity.transitToInactive();
            toSave.add(entity);
        }
        organizationRepository.saveAll(toSave);
        for (RetireOrganizationCommand.RetireOrganizationItemCommand item : items) {
            domainEventPublisher.publish(new OrganizationRetiredEvent(item.getOrganizationId(), item.getRetireReason(), cmd.getOperatorId()));
        }
    }

    /**
     * 重新启用机构。
     */
    public void reactivateOrganization(ReactivateOrganizationCommand cmd) {
        List<ReactivateOrganizationCommand.ReactivateOrganizationItemCommand> items = cmd.resolveItems();
        Map<Long, Organization> map = this.loadOrganizationMap(CommandBatchUtil.extractIds(items, ReactivateOrganizationCommand.ReactivateOrganizationItemCommand::getOrganizationId));
        List<Organization> toSave = new ArrayList<>();
        for (ReactivateOrganizationCommand.ReactivateOrganizationItemCommand item : items) {
            Organization entity = this.requireOrganization(map, item.getOrganizationId());
            entity.assertActivatable();
            this.assertParentActive(entity.getParentId());
            entity.transitToActive();
            toSave.add(entity);
        }
        organizationRepository.saveAll(toSave);
        for (ReactivateOrganizationCommand.ReactivateOrganizationItemCommand item : items) {
            domainEventPublisher.publish(new OrganizationReactivatedEvent(item.getOrganizationId(), cmd.getOperatorId()));
        }
    }

    /**
     * 废弃机构。
     */
    public void abolishOrganization(AbolishOrganizationCommand cmd) {
        List<AbolishOrganizationCommand.AbolishOrganizationItemCommand> items = cmd.resolveItems();
        Map<Long, Organization> map = this.loadOrganizationMap(CommandBatchUtil.extractIds(items, AbolishOrganizationCommand.AbolishOrganizationItemCommand::getOrganizationId));
        List<Organization> toSave = new ArrayList<>();
        for (AbolishOrganizationCommand.AbolishOrganizationItemCommand item : items) {
            Organization entity = this.requireOrganization(map, item.getOrganizationId());
            entity.assertAbolishAllowed();
            // 业务分支处理：仍存在启用子机构时禁止废弃（仅本聚合仓储判定）。
            if (organizationRepository.existsByParentIdAndStatusEnum(
                item.getOrganizationId(), PublicationStatus.ACTIVE.getCode())) {
                throw new BusinessException(ErrorCodeConstants.ORGANIZATION_RULE_CONFLICT, "机构仍被活跃业务引用，禁止废弃");
            }
            entity.transitToDeleted(cmd.getOperatorId());
            toSave.add(entity);
        }
        organizationRepository.saveAll(toSave);
        for (AbolishOrganizationCommand.AbolishOrganizationItemCommand item : items) {
            domainEventPublisher.publish(new OrganizationAbolishedEvent(item.getOrganizationId(), item.getAbolishReason(), cmd.getOperatorId()));
        }
    }

    /**
     * 私有方法：fillInputCode。
     */
    private void fillInputCode(Organization entity, InputCodeCommand inputCode) {
        // 业务分支处理。
        if (inputCode == null) {
            return;
        }
        entity.setPyStr(inputCode.getPyStr());
        entity.setWbStr(inputCode.getWbStr());
    }

    /**
     * 私有方法：fillInsurance。
     */
    private void fillInsurance(Organization entity, MedicalInsuranceInfoCommand insuranceInfo) {
        // 业务分支处理。
        if (insuranceInfo == null) {
            return;
        }
        entity.setYbNo(insuranceInfo.getYbNo());
        entity.setYbName(insuranceInfo.getYbName());
        entity.setMedinsId(insuranceInfo.getMedinsId());
        entity.setMedinsAdmdvs(insuranceInfo.getMedinsAdmdvs());
        entity.setMedinsType(insuranceInfo.getMedinsType());
        entity.setMedinsLv(insuranceInfo.getMedinsLv());
    }

    /**
     * 私有方法：assertNameUnique。
     */
    private void assertNameUnique(Long parentId, String name, Long excludeId) {
        boolean exists;
        // 业务分支处理。
        if (excludeId == null) {
            exists = organizationRepository.existsByParentIdAndName(parentId, name);
        } else {
            exists = organizationRepository.existsByParentIdAndNameAndIdNot(parentId, name, excludeId);
        }
        // 业务分支处理。
        if (exists) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_NAME_CONFLICT, "同级机构名称不可重复");
        }
    }

    /**
     * 私有方法：assertParentActive。
     */
    private void assertParentActive(Long parentId) {
        // 业务分支处理。
        if (parentId == null) {
            return;
        }
        Organization parent = organizationRepository.findById(parentId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ORGANIZATION_NOT_FOUND, "上级机构不存在"));
        // 上级机构必须处于启用状态，子机构才允许启用。
        parent.assertActive();
    }

    /**
     * 私有方法：assertNotCycle。
     */
    private void assertNotCycle(Long organizationId, Long newParentId) {
        // 业务分支处理。
        if (newParentId == null) {
            return;
        }
        Organization cursor = organizationRepository.findById(newParentId).orElse(null);
        while (cursor != null) {
            // 业务分支处理。
            if (organizationId.equals(cursor.getId())) {
                throw new BusinessException(ErrorCodeConstants.ORGANIZATION_RULE_CONFLICT, "调整上级将导致组织树环路");
            }
            // 业务分支处理。
            if (cursor.getParentId() == null) {
                break;
            }
            cursor = organizationRepository.findById(cursor.getParentId()).orElse(null);
        }
    }

    /**
     * 生成机构层级 busNo：顶级为 {@link AggregateTypeConstants#ORGANIZATION} 采番段，子级为「父 busNo + . + 本段」。
     *
     * @param parentId  父机构主键，根节点为 null
     * @param parentMap 已加载的父机构映射
     * @return 层级编码
     */
    private String buildBusNo(Long parentId, Map<Long, Organization> parentMap) {
        String segment = this.allocateOrganizationBusNoSegment();
        if (parentId == null) {
            return segment;
        }
        Organization parent = parentMap.get(parentId);
        if (parent == null) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_NOT_FOUND, "上级机构不存在");
        }
        return parent.getBusNo() + GeneralSharedConstants.POINT + segment;
    }

    /**
     * 调整上级时按新父级 busNo 与本节点末段拼接新编码。
     *
     * @param newParentId  新上级机构主键，根节点为 null
     * @param localSegment 本节点 busNo 末段
     * @param parentMap    新上级机构映射
     * @return 迁移后的层级编码
     */
    private String buildRelocatedBusNo(Long newParentId, String localSegment, Map<Long, Organization> parentMap) {
        if (newParentId == null) {
            return localSegment;
        }
        Organization parent = parentMap.get(newParentId);
        if (parent == null) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_NOT_FOUND, "上级机构不存在");
        }
        return parent.getBusNo() + GeneralSharedConstants.POINT + localSegment;
    }

    /**
     * 取机构业务序号段（含聚合前缀，如 OR0010）。
     *
     * @return 本节点 busNo 末段
     */
    private String allocateOrganizationBusNoSegment() {
        String prefix = AggregateTypeConstants.ORGANIZATION;
        return businessSequenceService.getSeq(prefix, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
    }

    /**
     * 批量创建前预加载父级机构，供层级 busNo 拼接。
     *
     * @param items 创建项
     * @return 父级 id → 机构
     */
    private Map<Long, Organization> loadParentMapForBusNo(
        List<CreateOrganizationCommand.CreateOrganizationItemCommand> items) {
        List<Long> parentIds = new ArrayList<>();
        for (CreateOrganizationCommand.CreateOrganizationItemCommand item : items) {
            if (item.getParentId() != null) {
                parentIds.add(item.getParentId());
            }
        }
        if (parentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return this.loadOrganizationMap(parentIds);
    }

    /**
     * 批量调整上级前预加载新上级机构，供层级 busNo 重算。
     *
     * @param items 调整项
     * @return 新上级 id → 机构
     */
    private Map<Long, Organization> loadParentMapForRelocate(
        List<RelocateOrganizationCommand.RelocateOrganizationItemCommand> items) {
        List<Long> parentIds = new ArrayList<>();
        for (RelocateOrganizationCommand.RelocateOrganizationItemCommand item : items) {
            if (item.getNewParentId() != null) {
                parentIds.add(item.getNewParentId());
            }
        }
        if (parentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return this.loadOrganizationMap(parentIds);
    }

    /**
     * 私有方法：loadOrganizationMap。
     */
    private Map<Long, Organization> loadOrganizationMap(List<Long> ids) {
        List<Organization> records = organizationRepository.findAllByIdIn(ids);
        Map<Long, Organization> map = new HashMap<>();
        for (Organization record : records) {
            map.put(record.getId(), record);
        }
        return map;
    }

    /**
     * 私有方法：requireOrganization。
     */
    private Organization requireOrganization(Map<Long, Organization> map, Long organizationId) {
        Organization organization = map.get(organizationId);
        // 业务分支处理。
        if (organization == null) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_NOT_FOUND, "机构不存在");
        }
        return organization;
    }
}


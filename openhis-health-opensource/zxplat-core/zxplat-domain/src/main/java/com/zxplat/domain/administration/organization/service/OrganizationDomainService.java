/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.organization.service;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.share.constants.BusinessSequenceConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.administration.organization.command.*;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.repository.OrganizationRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.events.administration.organization.*;
import com.zxplat.domain.share.utils.CommandBatchUtil;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 机构（{@code adm_organization}）聚合写侧领域服务。
 *
 * <p>职责：创建、发布、修改、调整上级、停用/启用与逻辑废弃；维护层级 {@code bus_no} 与子节点编码联动。</p>
 * <p>不变量：同级名称唯一、组织树无环路、停用/废弃前无启用子节点；持久化先于机构领域事件发布（B.14）。</p>
 * <p>仅注入本聚合 {@link OrganizationRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class OrganizationDomainService {

    private final OrganizationRepository organizationRepository;
    private final IDomainEventPublisher domainEventPublisher;
    private final BusinessSequenceService businessSequenceService;

    /**
     * 批量创建机构并发布 {@link OrganizationCreatedEvent}。
     *
     * @param cmd 创建命令，含名称、类型、上级与医保扩展等
     * @return 首条新建机构主键
     * @throws BusinessException 同级重名或上级不存在时
     */
    public Long createOrganization(CreateOrganizationCommand cmd) {
        // Pre：解析批量项并预加载父级，供层级 busNo 拼接。
        List<CreateOrganizationCommand.CreateOrganizationItemCommand> items = cmd.resolveItems();
        Map<Long, Organization> parentMap = this.loadParentMapForBusNo(items);
        List<Organization> toSave = new ArrayList<>();
        for (CreateOrganizationCommand.CreateOrganizationItemCommand item : items) {
            // 前置校验：同级机构名称不得重复。
            this.assertNameUnique(item.getParentId(), item.getName(), null);
            // 加载/组装：初始化有效态机构聚合根。
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
            // 级联机构编码由父节点与子段序列拼接，禁止命令行注入（B.10.9）。
            String busNo = this.buildBusNo(item.getParentId(), parentMap);
            // Gate：BR-ORG-001 全局唯一（序号抬升由数据导入/迁移负责，此处只拒绝撞号）。
            this.assertBusNoUnique(busNo, null);
            entity.setBusNo(busNo);
            toSave.add(entity);
        }
        // 持久化
        List<Organization> saved = organizationRepository.saveAll(toSave);
        // 发布事件：机构已创建，供挂号/排班读模型同步
        for (Organization organization : saved) {
            domainEventPublisher.publish(new OrganizationCreatedEvent(
                organization.getId(), organization.getName(), organization.getTypeEnum(), organization.getBusNo(), organization.getParentId()));
        }
        return saved.get(0).getId();
    }

    /**
     * 发布机构：草稿/未发布态迁移至 ACTIVE 并发布 {@link OrganizationPublishedEvent}。
     *
     * @param cmd 发布命令
     * @throws BusinessException 机构不存在或当前状态不允许发布时
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
        // 持久化
        organizationRepository.saveAll(toSave);
        // 发布事件：机构已对外生效
        for (PublishOrganizationCommand.PublishOrganizationItemCommand item : items) {
            Organization entity = this.requireOrganization(map, item.getOrganizationId());
            domainEventPublisher.publish(new OrganizationPublishedEvent(
                entity.getId(), entity.getName(), entity.getTypeEnum(), entity.getClassEnum()));
        }
    }

    /**
     * 修改机构资料并发布 {@link OrganizationEditedEvent}。
     *
     * @param cmd 修改命令，含变更字段摘要
     * @throws BusinessException 机构不存在、不可修改或重名冲突时
     */
    public void modifyOrganization(ModifyOrganizationCommand cmd) {
        List<ModifyOrganizationCommand.ModifyOrganizationItemCommand> items = cmd.resolveItems();
        Map<Long, Organization> map = this.loadOrganizationMap(CommandBatchUtil.extractIds(items, ModifyOrganizationCommand.ModifyOrganizationItemCommand::getOrganizationId));
        List<Organization> toSave = new ArrayList<>();
        for (ModifyOrganizationCommand.ModifyOrganizationItemCommand item : items) {
            Organization entity = this.requireOrganization(map, item.getOrganizationId());
            entity.assertModifiable();
            // 名称变更时须重新校验同级唯一，防止重名科室并存。
            if (!Objects.equals(entity.getName(), item.getName())) {
                this.assertNameUnique(entity.getParentId(), item.getName(), entity.getId());
            }
            entity.setName(item.getName());
            entity.setClassEnum(item.getClassEnum());
            entity.setDisplayOrder(item.getDisplayOrder());
            entity.setDefDoctorId(item.getDefDoctorId());
            entity.setCaty(item.getCaty());
            this.fillInputCode(entity, item.getInputCode());
            this.fillInsurance(entity, item.getInsuranceInfo());
            toSave.add(entity);
        }
        // 持久化
        organizationRepository.saveAll(toSave);
        // 发布事件：机构资料已变更
        for (ModifyOrganizationCommand.ModifyOrganizationItemCommand item : items) {
            domainEventPublisher.publish(new OrganizationEditedEvent(item.getOrganizationId(), item.getChangedFields()));
        }
    }

    /**
     * 调整机构上级并重算本节点及后代 {@code bus_no}，发布 {@link OrganizationHierarchyChangedEvent}。
     *
     * @param cmd 调整命令，含新上级主键
     * @throws BusinessException 形成环路、新上级为自身或机构不存在时
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
            // 新上级不可为自身，否则组织树出现自环。
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
            // Gate：迁移后的完整编码不得与树中其他节点冲突（BR-ORG-001）。
            this.assertBusNoUnique(newBusNo, entity.getId());
            entity.setParentId(item.getNewParentId());
            entity.setBusNo(newBusNo);
            toSaveById.put(entity.getId(), entity);
            // 后代 busNo 前缀随本节点迁移，保持本级末段不变（BR-ORG-002）。
            String oldPrefix = oldBusNo + GeneralSharedConstants.POINT;
            List<Organization> descendants = organizationRepository.findAllByBusNoStartingWith(oldPrefix);
            for (Organization descendant : descendants) {
                String descendantBusNo = descendant.getBusNo();
                String suffix = descendantBusNo.substring(oldBusNo.length());
                String relocatedDescendantBusNo = newBusNo + suffix;
                this.assertBusNoUnique(relocatedDescendantBusNo, descendant.getId());
                descendant.setBusNo(relocatedDescendantBusNo);
                toSaveById.put(descendant.getId(), descendant);
            }
            hierarchyEvents.add(new OrganizationHierarchyChangedEvent(
                entity.getId(), oldParentId, item.getNewParentId(), newBusNo));
        }
        // 持久化：本节点与后代 busNo 须同事务更新
        organizationRepository.saveAll(new ArrayList<>(toSaveById.values()));
        // 发布事件：组织层级已变更
        for (OrganizationHierarchyChangedEvent event : hierarchyEvents) {
            domainEventPublisher.publish(event);
        }
    }

    /**
     * 停用机构并发布 {@link OrganizationRetiredEvent}；存在启用子节点时拒绝。
     *
     * @param cmd 停用命令
     * @throws BusinessException 仍有启用子机构或状态不允许停用时
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
        // 持久化
        organizationRepository.saveAll(toSave);
        // 发布事件：机构已停用
        for (RetireOrganizationCommand.RetireOrganizationItemCommand item : items) {
            domainEventPublisher.publish(new OrganizationRetiredEvent(item.getOrganizationId(), item.getRetireReason(), cmd.getOperatorId()));
        }
    }

    /**
     * 重新启用已停用机构；上级须仍为启用态。
     *
     * @param cmd 启用命令
     * @throws BusinessException 上级未启用或机构不存在时
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
        // 持久化
        organizationRepository.saveAll(toSave);
        // 发布事件：机构已重新启用
        for (ReactivateOrganizationCommand.ReactivateOrganizationItemCommand item : items) {
            domainEventPublisher.publish(new OrganizationReactivatedEvent(item.getOrganizationId(), cmd.getOperatorId()));
        }
    }

    /**
     * 逻辑废弃机构并发布 {@link OrganizationAbolishedEvent}；仍有启用子机构时拒绝。
     *
     * @param cmd 废弃命令
     * @throws BusinessException 存在启用子节点或状态不允许废弃时
     */
    public void abolishOrganization(AbolishOrganizationCommand cmd) {
        List<AbolishOrganizationCommand.AbolishOrganizationItemCommand> items = cmd.resolveItems();
        Map<Long, Organization> map = this.loadOrganizationMap(CommandBatchUtil.extractIds(items, AbolishOrganizationCommand.AbolishOrganizationItemCommand::getOrganizationId));
        List<Organization> toSave = new ArrayList<>();
        for (AbolishOrganizationCommand.AbolishOrganizationItemCommand item : items) {
            Organization entity = this.requireOrganization(map, item.getOrganizationId());
            entity.assertAbolishAllowed();
            // 仍存在启用子机构时禁止废弃（仅本聚合仓储判定）。
            if (organizationRepository.existsByParentIdAndStatusEnum(
                item.getOrganizationId(), PublicationStatus.ACTIVE.getCode())) {
                throw new BusinessException(ErrorCodeConstants.ORGANIZATION_RULE_CONFLICT, "机构仍被活跃业务引用，禁止废弃");
            }
            entity.transitToDeleted(cmd.getOperatorId());
            toSave.add(entity);
        }
        // 持久化
        organizationRepository.saveAll(toSave);
        // 发布事件：机构已废弃
        for (AbolishOrganizationCommand.AbolishOrganizationItemCommand item : items) {
            domainEventPublisher.publish(new OrganizationAbolishedEvent(item.getOrganizationId(), item.getAbolishReason(), cmd.getOperatorId()));
        }
    }

    /**
     * 写入机构检索码（拼音/五笔）；命令未传时保持原值。
     *
     * @param entity    待更新机构
     * @param inputCode 检索码命令，可为 null
     */
    private void fillInputCode(Organization entity, InputCodeCommand inputCode) {
        // 未传检索码时保持实体原值，不覆盖既有拼音五笔。
        if (inputCode == null) {
            return;
        }
        entity.setPyStr(inputCode.getPyStr());
        entity.setWbStr(inputCode.getWbStr());
    }

    /**
     * 写入机构医保定点扩展信息；命令未传时跳过。
     *
     * @param entity         待更新机构
     * @param insuranceInfo 医保信息命令，可为 null
     */
    private void fillInsurance(Organization entity, MedicalInsuranceInfoCommand insuranceInfo) {
        // 未传医保信息时跳过，避免清空已维护的定点编码。
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
     * 断言同级机构名称唯一。
     *
     * @param parentId  父机构主键
     * @param name      待校验名称
     * @param excludeId 修改场景排除自身主键，新建时传 null
     * @throws BusinessException 同级已存在同名机构时
     */
    private void assertNameUnique(Long parentId, String name, Long excludeId) {
        boolean exists;
        // 新建场景：同级下不得存在同名机构。
        if (excludeId == null) {
            exists = organizationRepository.existsByParentIdAndName(parentId, name);
        } else {
            exists = organizationRepository.existsByParentIdAndNameAndIdNot(parentId, name, excludeId);
        }
        // 同级名称冲突时拒绝，防止挂号/排班引用歧义。
        if (exists) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_NAME_CONFLICT, "同级机构名称不可重复");
        }
    }

    /**
     * 断言机构层级编码全局唯一（BR-ORG-001；与 ZXPlat OU code 同源）。
     *
     * @param busNo     待校验层级编码
     * @param excludeId 排除自身主键；新建传 null
     * @throws BusinessException 编码已被其他机构占用时
     */
    private void assertBusNoUnique(String busNo, Long excludeId) {
        if (busNo == null || busNo.isBlank()) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_INVALID_ARGUMENT, "机构编码不能为空");
        }
        boolean exists;
        if (excludeId == null) {
            exists = organizationRepository.existsByBusNo(busNo);
        } else {
            exists = organizationRepository.existsByBusNoAndIdNot(busNo, excludeId);
        }
        if (exists) {
            throw new BusinessException(
                ErrorCodeConstants.ORGANIZATION_BUS_NO_CONFLICT,
                "机构编码不可重复：" + busNo);
        }
    }

    /**
     * 断言上级机构处于启用态，子机构方可重新启用。
     *
     * @param parentId 上级机构主键，根节点为 null
     * @throws BusinessException 上级不存在或未启用时
     */
    private void assertParentActive(Long parentId) {
        // 根机构无上级，启用时跳过父节点校验。
        if (parentId == null) {
            return;
        }
        Organization parent = organizationRepository.findById(parentId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ORGANIZATION_NOT_FOUND, "上级机构不存在"));
        // 上级机构必须处于启用状态，子机构才允许启用。
        parent.assertActive();
    }

    /**
     * 断言调整上级不会形成组织树环路。
     *
     * @param organizationId 本机构主键
     * @param newParentId    拟挂接的新上级主键
     * @throws BusinessException 新上级链路上出现本节点时
     */
    private void assertNotCycle(Long organizationId, Long newParentId) {
        // 调整为根节点时无需环路检测。
        if (newParentId == null) {
            return;
        }
        Organization cursor = organizationRepository.findById(newParentId).orElse(null);
        while (cursor != null) {
            // 新上级链路上出现本节点则形成环路，必须拒绝。
            if (organizationId.equals(cursor.getId())) {
                throw new BusinessException(ErrorCodeConstants.ORGANIZATION_RULE_CONFLICT, "调整上级将导致组织树环路");
            }
            // 遍历到根节点仍未命中本 id，说明无环路。
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
        return businessSequenceService.getSeq(prefix, BusinessSequenceConstants.CASCADE_BUS_NO_SEGMENT_SEQ_LENGTH);
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
     * 按主键批量加载机构并构建内存映射。
     *
     * @param ids 机构主键列表
     * @return 主键 → 机构实体
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
     * 从内存映射强制获取机构；不存在时终止写路径。
     *
     * @param map            已加载机构映射
     * @param organizationId 目标主键
     * @return 机构实体
     * @throws BusinessException 机构不存在时
     */
    private Organization requireOrganization(Map<Long, Organization> map, Long organizationId) {
        Organization organization = map.get(organizationId);
        // 批量加载结果中无此主键，说明机构不存在或已软删。
        if (organization == null) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_NOT_FOUND, "机构不存在");
        }
        return organization;
    }
}

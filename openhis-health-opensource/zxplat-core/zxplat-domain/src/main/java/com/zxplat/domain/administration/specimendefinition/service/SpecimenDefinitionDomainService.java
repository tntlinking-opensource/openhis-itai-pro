/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.specimendefinition.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToEmpty;


import com.zxplat.domain.administration.specimendefinition.command.ActivateSpecimenDefinitionsCommand;
import com.zxplat.domain.administration.specimendefinition.command.CreateSpecimenDefinitionCommand;
import com.zxplat.domain.administration.specimendefinition.command.DeactivateSpecimenDefinitionsCommand;
import com.zxplat.domain.administration.specimendefinition.command.DeleteSpecimenDefinitionCommand;
import com.zxplat.domain.administration.specimendefinition.command.UpdateSpecimenDefinitionCommand;
import com.zxplat.domain.administration.specimendefinition.entity.SpecimenDefinition;
import com.zxplat.domain.administration.specimendefinition.repository.SpecimenDefinitionRepository;
import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.share.constants.BusinessSequenceConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.events.administration.specimendefinition.SpecimenDefinitionActivatedEvent;
import com.zxplat.domain.share.events.administration.specimendefinition.SpecimenDefinitionCreatedEvent;
import com.zxplat.domain.share.events.administration.specimendefinition.SpecimenDefinitionDeactivatedEvent;
import com.zxplat.domain.share.events.administration.specimendefinition.SpecimenDefinitionDeletedEvent;
import com.zxplat.domain.share.events.administration.specimendefinition.SpecimenDefinitionUpdatedEvent;
import com.zxplat.domain.share.utils.CommandBatchUtil;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 样本定义写侧领域服务（DM-ADM-014）。
 *
 * <p>负责标本目录主数据的新增、维护、逻辑删除与批量启停；不联动修改下游 {@code ActivityDefinition}。
 * 不变式：业务编码唯一（BR-SPDEF-001）、名称变更重算检索码（BR-SPDEF-002）、启停仅改状态（BR-SPDEF-003）。</p>
 */
@Service
@RequiredArgsConstructor
public class SpecimenDefinitionDomainService {

    private final SpecimenDefinitionRepository repository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 样本定义 busNo：主数据全局递增，前缀 {@link AggregateTypeConstants#SPECIMEN_DEFINITION}。 */
    private final BusinessSequenceService businessSequenceService;

    /**
     * 新建样本定义，初始化为启用态并发布创建事件。
     *
     * @param command 新建命令，须含名称与标本种类；业务编码由领域层按 SPD 前缀生成（禁止外部注入）
     * @return 新建主键
     * @throws BusinessException 命令为空、必填缺失、种类非法或业务编码冲突时
     */
    public Long createSpecimenDefinition(CreateSpecimenDefinitionCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_INVALID_ARGUMENT, "新建样本定义命令不能为空");
        }
        // 加载/组装聚合：从命令装配主档字段，显式传入的检索码优先保留
        SpecimenDefinition entity = new SpecimenDefinition();
        entity.setBusNo(businessSequenceService.getSeq(
            AggregateTypeConstants.SPECIMEN_DEFINITION, BusinessSequenceConstants.MASTER_DATA_3_PREFIX_SEQ_LENGTH));
        entity.setName(trimToEmpty(command.getName()));
        entity.setTypeEnum(trimToEmpty(command.getTypeEnum()));
        if (StringUtils.isNotEmpty(command.getPyStr())) {
            entity.setPyStr(command.getPyStr().trim());
        }
        if (StringUtils.isNotEmpty(command.getWbStr())) {
            entity.setWbStr(command.getWbStr().trim());
        }
        entity.initAsActive();
        // 前置校验：实体断言补齐必填、种类合法与检索码（BR-SPDEF-002）
        entity.assertBusNoNotBlank();
        entity.assertNameNotBlank();
        entity.assertTypeEnumValid();
        entity.refreshSearchCodeFromName();
        this.assertBusNoNotConflict(entity);
        // 持久化：插入主档后获得主键，供事件载荷与返回值使用
        SpecimenDefinition saved = repository.save(entity);
        // 发布事件：save 完成后投递创建事件，beforeCommit 时序由框架保障
        domainEventPublisher.publish(new SpecimenDefinitionCreatedEvent(
            saved.getId(), saved.getBusNo(), saved.getName(), saved.getTypeEnum(), saved.getStatusEnum()));
        return saved.getId();
    }

    /**
     * 维护样本定义；名称变更时重算检索码并记入变更字段。
     *
     * @param command 更新命令，须含主键
     * @throws BusinessException 记录不存在、已删、编码冲突或参数非法时
     */
    public void updateSpecimenDefinition(UpdateSpecimenDefinitionCommand command) {
        if (command == null || command.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_INVALID_ARGUMENT, "更新样本定义时主键不能为空");
        }
        // 加载/组装聚合：按主键装载未删记录，禁止静默新建
        SpecimenDefinition entity = this.loadSpecimenDefinition(command.getId());
        List<String> changedFields = new ArrayList<>();
        String oldName = entity.getName();
        if (command.getName() != null) {
            String newName = command.getName().trim();
            if (!Objects.equals(entity.getName(), newName)) {
                entity.setName(newName);
                changedFields.add(FieldNameConstants.NAME);
            }
        }
        if (command.getTypeEnum() != null) {
            String newTypeEnum = command.getTypeEnum().trim();
            if (!Objects.equals(entity.getTypeEnum(), newTypeEnum)) {
                entity.setTypeEnum(newTypeEnum);
                changedFields.add(FieldNameConstants.TYPE_ENUM);
            }
        }
        if (command.getPyStr() != null) {
            String newPyStr = command.getPyStr().trim();
            if (!Objects.equals(entity.getPyStr(), newPyStr)) {
                entity.setPyStr(newPyStr);
                changedFields.add(FieldNameConstants.PY_STR);
            }
        }
        if (command.getWbStr() != null) {
            String newWbStr = command.getWbStr().trim();
            if (!Objects.equals(entity.getWbStr(), newWbStr)) {
                entity.setWbStr(newWbStr);
                changedFields.add(FieldNameConstants.WB_STR);
            }
        }
        // 业务分支：名称变更时清空检索码并按新名称重算（BR-SPDEF-002），命令显式传入则覆盖
        if (!Objects.equals(oldName, entity.getName())) {
            entity.setPyStr(null);
            entity.setWbStr(null);
            if (command.getPyStr() != null) {
                entity.setPyStr(command.getPyStr().trim());
            }
            if (command.getWbStr() != null) {
                entity.setWbStr(command.getWbStr().trim());
            }
            entity.refreshSearchCodeFromName();
            if (!changedFields.contains(FieldNameConstants.PY_STR)) {
                changedFields.add(FieldNameConstants.PY_STR);
            }
            if (!changedFields.contains(FieldNameConstants.WB_STR)) {
                changedFields.add(FieldNameConstants.WB_STR);
            }
        }
        // 前置校验：变更后仍满足必填、种类合法与业务编码唯一（BR-SPDEF-001）
        entity.assertBusNoNotBlank();
        entity.assertNameNotBlank();
        entity.assertTypeEnumValid();
        this.assertBusNoNotConflict(entity);
        // 持久化：更新允许字段，状态不因维护而迁移
        repository.save(entity);
        // 发布事件：有实际字段变更时才投递更新事件
        if (!changedFields.isEmpty()) {
            domainEventPublisher.publish(new SpecimenDefinitionUpdatedEvent(entity.getId(), changedFields));
        }
    }

    /**
     * 批量逻辑删除样本定义；下游引用校验由应用层负责（BR-SPDEF-004）。
     *
     * @param command 批量删除命令，须含待删主键与操作人
     * @throws BusinessException 命令为空、主键缺失、记录不存在或已删时
     */
    public void deleteSpecimenDefinition(DeleteSpecimenDefinitionCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_INVALID_ARGUMENT, "删除样本定义命令不能为空");
        }
        // 前置校验：解析批量命令项，提取待删主键列表
        List<DeleteSpecimenDefinitionCommand.DeleteSpecimenDefinitionItemCommand> items = command.resolveItems();
        List<Long> ids = CommandBatchUtil.extractIds(items, DeleteSpecimenDefinitionCommand.DeleteSpecimenDefinitionItemCommand::getId);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_INVALID_ARGUMENT, "删除样本定义时 id 不能为空");
        }
        // 加载/组装聚合：批量查询待删实体，主键集合须完整命中
        List<SpecimenDefinition> entities = repository.findByIdIn(ids);
        if (entities.size() != ids.size()) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_NOT_FOUND, "删除样本定义时存在无效主键");
        }
        // 业务分支：已逻辑删除的记录不可再次删除
        for (SpecimenDefinition entity : entities) {
            if (Boolean.TRUE.equals(entity.getIsDeleted())) {
                throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_NOT_FOUND, "样本定义不存在或已删除");
            }
            entity.markDeleted(command.getOperatorId());
        }
        // 持久化：批量标记软删并写入操作人审计
        repository.saveAll(entities);
        // 发布事件：逐条投递删除事件，供读侧投影剔除
        for (Long id : ids) {
            domainEventPublisher.publish(new SpecimenDefinitionDeletedEvent(id));
        }
    }

    /**
     * 批量停用样本定义（ACTIVE → INACTIVE）。
     *
     * @param command 批量停用命令
     * @throws BusinessException 命令为空、主键缺失、记录不存在或当前非启用态时
     */
    public void deactivateSpecimenDefinitions(DeactivateSpecimenDefinitionsCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_INVALID_ARGUMENT, "停用样本定义命令不能为空");
        }
        // 前置校验：解析批量命令项，提取待停用主键列表
        List<DeactivateSpecimenDefinitionsCommand.DeactivateSpecimenDefinitionItemCommand> items = command.resolveItems();
        List<Long> ids = CommandBatchUtil.extractIds(items, DeactivateSpecimenDefinitionsCommand.DeactivateSpecimenDefinitionItemCommand::getId);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_INVALID_ARGUMENT, "停用样本定义时 id 不能为空");
        }
        // 加载/组装聚合：批量装载，主键集合须完整命中
        List<SpecimenDefinition> entities = repository.findByIdIn(ids);
        if (entities.size() != ids.size()) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_NOT_FOUND, "停用样本定义时存在无效主键");
        }
        List<SpecimenDefinition> toSave = new ArrayList<>();
        for (SpecimenDefinition entity : entities) {
            // 业务分支：仅启用态可停用，停用不删历史（BR-SPDEF-003）
            if (Boolean.TRUE.equals(entity.getIsDeleted())) {
                throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_NOT_FOUND, "样本定义不存在或已删除");
            }
            entity.assertActive();
            entity.deactivate();
            toSave.add(entity);
        }
        // 持久化：批量更新发布状态为 INACTIVE
        repository.saveAll(toSave);
        // 发布事件：逐条投递停用事件，供目录候选过滤（BR-SPDEF-005）
        for (SpecimenDefinition entity : toSave) {
            domainEventPublisher.publish(new SpecimenDefinitionDeactivatedEvent(entity.getId(), entity.getStatusEnum()));
        }
    }

    /**
     * 批量启用样本定义（INACTIVE → ACTIVE）。
     *
     * @param command 批量启用命令
     * @throws BusinessException 命令为空、主键缺失、记录不存在或当前非停用态时
     */
    public void activateSpecimenDefinitions(ActivateSpecimenDefinitionsCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_INVALID_ARGUMENT, "启用样本定义命令不能为空");
        }
        // 前置校验：解析批量命令项，提取待启用主键列表
        List<ActivateSpecimenDefinitionsCommand.ActivateSpecimenDefinitionItemCommand> items = command.resolveItems();
        List<Long> ids = CommandBatchUtil.extractIds(items, ActivateSpecimenDefinitionsCommand.ActivateSpecimenDefinitionItemCommand::getId);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_INVALID_ARGUMENT, "启用样本定义时 id 不能为空");
        }
        // 加载/组装聚合：批量装载，主键集合须完整命中
        List<SpecimenDefinition> entities = repository.findByIdIn(ids);
        if (entities.size() != ids.size()) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_NOT_FOUND, "启用样本定义时存在无效主键");
        }
        List<SpecimenDefinition> toSave = new ArrayList<>();
        for (SpecimenDefinition entity : entities) {
            // 业务分支：仅停用态可重新启用，须未逻辑删除
            if (Boolean.TRUE.equals(entity.getIsDeleted())) {
                throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_NOT_FOUND, "样本定义不存在或已删除");
            }
            entity.assertInactive();
            entity.activate();
            toSave.add(entity);
        }
        // 持久化：批量更新发布状态为 ACTIVE
        repository.saveAll(toSave);
        // 发布事件：逐条投递启用事件，恢复目录可选集合
        for (SpecimenDefinition entity : toSave) {
            domainEventPublisher.publish(new SpecimenDefinitionActivatedEvent(entity.getId(), entity.getStatusEnum()));
        }
    }

    /**
     * 按主键加载样本定义；不存在或已软删视为不存在。
     *
     * @param id 主键
     * @return 可写聚合根
     * @throws BusinessException {@link ErrorCodeConstants#SPECIMEN_DEFINITION_NOT_FOUND}
     */
    private SpecimenDefinition loadSpecimenDefinition(Long id) {
        SpecimenDefinition entity = repository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_NOT_FOUND, "样本定义不存在"));
        // 业务分支：已逻辑删除的记录视为不存在，禁止维护或重复写操作
        if (Boolean.TRUE.equals(entity.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_NOT_FOUND, "样本定义不存在或已删除");
        }
        return entity;
    }

    /**
     * 样本定义 busNo 唯一性 Gate（BR-SPDEF-001）；编辑时排除自身。
     *
     * @param entity 待持久化实体
     * @throws BusinessException {@link ErrorCodeConstants#SPECIMEN_DEFINITION_BUS_NO_CONFLICT}
     */
    private void assertBusNoNotConflict(SpecimenDefinition entity) {
        if (entity.getId() == null) {
            // 业务分支：新建场景按业务编码查重（BR-SPDEF-001）
            if (repository.existsByBusNo(entity.getBusNo())) {
                throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_BUS_NO_CONFLICT, "样本定义业务编码冲突");
            }
            return;
        }
        // 业务分支：编辑场景排除自身后查重（BR-SPDEF-001）
        if (repository.existsByBusNoAndIdNot(entity.getBusNo(), entity.getId())) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_BUS_NO_CONFLICT, "样本定义业务编码冲突");
        }
    }
}

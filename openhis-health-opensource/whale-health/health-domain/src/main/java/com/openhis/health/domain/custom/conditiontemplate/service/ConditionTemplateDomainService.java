/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.conditiontemplate.service;

import com.openhis.health.domain.custom.conditiontemplate.command.AdjustSortWeightCommand;
import com.openhis.health.domain.custom.conditiontemplate.command.BindConditionTemplateCommand;
import com.openhis.health.domain.custom.conditiontemplate.command.UnbindConditionTemplateCommand;
import com.openhis.health.domain.custom.conditiontemplate.entity.ConditionTemplate;
import com.openhis.health.domain.custom.conditiontemplate.repository.ConditionTemplateRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.custom.conditiontemplate.*;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * 诊断常用模板（{@code cus_condition_template}）聚合写侧领域服务。
 *
 * <p>维护对象（如医生、科室）与诊断定义模板之间的绑定：批量绑定、解绑与排序调整。</p>
 * <p>不变量：同对象+绑定类型下 definitionId 幂等；持久化先于模板领域事件发布。</p>
 * <p>仅注入本聚合 {@link ConditionTemplateRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class ConditionTemplateDomainService {

    private final ConditionTemplateRepository repository;
    private final IDomainEventPublisher domainEventPublisher;


    /**
     * 批量绑定诊断模板：在指定归属对象与绑定类型下插入尚未存在的模板行，命令内重复 definitionId 去重；已存在绑定幂等跳过。
     *
     * @param command 须含 objectId、bindingEnum 与明细列表
     * @return 本次新插入行的主键列表
     * @throws BusinessException 归属对象、绑定类型或诊断定义标识为空时
     */
    public List<Long> bindConditionTemplate(BindConditionTemplateCommand command) {
        if (command.getObjectId() == null || command.getBindingEnum() == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_INVALID_ARGUMENT, "归属对象与绑定类型不能为空");
        }
        List<BindConditionTemplateCommand.BindConditionTemplateItemCommand> items = command.resolveItems();

        // Pre：批量查询同一对象 + 绑定类型下的现存绑定，避免循环 existsBy 产生 N 次查询。
        List<ConditionTemplate> existed = repository.findAllByObjectIdAndBindingEnumOrderBySortWeightAscIdAsc(
            command.getObjectId(),
            command.getBindingEnum()
        );
        // Asm：按诊断定义标识构建索引用于幂等判断。
        Set<Long> existedDefinitionIds = new HashSet<>();
        for (ConditionTemplate row : existed) {
            existedDefinitionIds.add(row.getDefinitionId());
        }

        List<ConditionTemplate> toSave = new ArrayList<>();
        Set<Long> dedupDefinitionIds = new HashSet<>();
        // Loop：遍历命令项逐条处理。
        for (BindConditionTemplateCommand.BindConditionTemplateItemCommand item : items) {
            if (item.getDefinitionId() == null) {
                throw new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_INVALID_ARGUMENT, "诊断定义标识不能为空");
            }
            // 命令内重复 definitionId 去重，避免同批重复绑定。
            if (dedupDefinitionIds.contains(item.getDefinitionId())) {
                continue;
            }
            dedupDefinitionIds.add(item.getDefinitionId());
            // 已存在绑定则幂等跳过，不重复插入。
            if (existedDefinitionIds.contains(item.getDefinitionId())) {
                continue;
            }
            ConditionTemplate row = new ConditionTemplate();
            row.setObjectId(command.getObjectId());
            row.setDefinitionId(item.getDefinitionId());
            row.setBindingEnum(command.getBindingEnum());
            // 排序权重缺省为 0，保证列表有稳定次序。
            if (item.getSortWeight() == null) {
                row.setSortWeight(0);
            } else {
                row.setSortWeight(item.getSortWeight());
            }
            row.setRemark(item.getRemark());
            row.assertRequiredFields();
            toSave.add(row);
        }

        List<ConditionTemplate> saved = repository.saveAll(toSave);
        List<Long> ids = new ArrayList<>();
        // Event：逐条发布创建事件。
        for (ConditionTemplate row : saved) {
            ids.add(row.getId());
            domainEventPublisher.publish(new ConditionTemplateCreatedEvent(
                row.getId(),
                row.getObjectId(),
                row.getBindingEnum(),
                row.getDefinitionId()
            , command.getOperatorId()));
        }
        // 有新增绑定时发布批量创建事件。
        if (!ids.isEmpty()) {
            domainEventPublisher.publish(new ConditionTemplateBatchCreatedEvent(
                ids.get(0),
                command.getObjectId(),
                command.getBindingEnum(),
                ids
            , command.getOperatorId()));
        }
        return ids;
    }

    /**
     * 批量解绑：按模板行主键软删绑定记录并发布删除/批量删除事件。
     *
     * @param command 须含待删行主键与操作人
     * @throws BusinessException 行主键缺失或记录不存在时
     */
    public void unbindConditionTemplate(UnbindConditionTemplateCommand command) {
        List<UnbindConditionTemplateCommand.UnbindConditionTemplateItemCommand> items = command.resolveItems();
        List<Long> templateIds = new ArrayList<>();
        // Loop：遍历命令项收集待删主键。
        for (UnbindConditionTemplateCommand.UnbindConditionTemplateItemCommand item : items) {
            if (item == null || item.getTemplateId() == null) {
                throw new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_INVALID_ARGUMENT, "模板行主键不能为空");
            }
            templateIds.add(item.getTemplateId());
        }
        // 无待删主键时直接返回，删除入参为空则幂等。
        if (templateIds.isEmpty()) {
            return;
        }

        // Pre：批量查询待删记录。
        List<ConditionTemplate> existed = repository.findAllByIdIn(templateIds);
        Map<Long, ConditionTemplate> existedMap = new HashMap<>();
        // Loop：构建主键索引加速后续校验。
        for (ConditionTemplate row : existed) {
            existedMap.put(row.getId(), row);
        }
        for (Long id : templateIds) {
            if (!existedMap.containsKey(id)) {
                throw new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_NOT_FOUND, "模板行不存在");
            }
        }

        List<Long> deletedIds = new ArrayList<>();
        for (ConditionTemplate row : existed) {
            row.markDeleted(command.getOperatorId());
            deletedIds.add(row.getId());
        }
        repository.saveAll(existed);

        // Event：逐条发布删除事件。
        for (ConditionTemplate row : existed) {
            domainEventPublisher.publish(new ConditionTemplateDeletedEvent(row.getId(), row.getDefinitionId(), command.getOperatorId()));
        }
        // Event：发布批量删除事件。
        domainEventPublisher.publish(new ConditionTemplateBatchDeletedEvent(deletedIds.get(0), deletedIds, command.getOperatorId()));
    }

    /**
     * 调整单条模板绑定行的展示排序权重。
     *
     * @param command 须含模板行主键与非负 sortWeight
     * @throws BusinessException 权重非法或模板行不存在时
     */
    public void adjustSortWeight(AdjustSortWeightCommand command) {
        if (command.getSortWeight() == null || command.getSortWeight() < 0) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_INVALID_ARGUMENT, "排序权重必须为非负整数");
        }
        // Pre：加载目标模板行
        ConditionTemplate row = repository.findById(command.getTemplateId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_NOT_FOUND, "模板行不存在"));
        row.setSortWeight(command.getSortWeight());
        // 持久化
        repository.save(row);
    }
}

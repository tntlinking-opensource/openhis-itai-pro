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

package com.openhis.health.domain.custom.ordertemplate.service;

import com.openhis.health.domain.custom.ordertemplate.command.*;
import com.openhis.health.domain.custom.ordertemplate.entity.OrderTemplate;
import com.openhis.health.domain.custom.ordertemplate.entity.OrderTemplateLine;
import com.openhis.health.domain.custom.ordertemplate.repository.OrderTemplateLineRepository;
import com.openhis.health.domain.custom.ordertemplate.repository.OrderTemplateRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.custom.ordertemplate.*;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 医嘱组套模板写侧领域服务。
 *
 * <p>DOC-CONFLICT：SDD（DM-CUS-012-OrderTemplate-SDD）§7 对事件载荷与触发方法的表述与 DM §5.1/§5.2 不一致，
 * 且包含可选事件 {@code OrderTemplateLinesBatchChangedEvent} 的“必有”要求。当前实现以 DM 为最高权威：仅实现 DM §5.1 定义的事件，
 * 不生成可选批量事件；事件载荷按 DM 最小载荷 + 现有实现为准。</p>
 */
@Service
@RequiredArgsConstructor
public class OrderTemplateDomainService {

    private final OrderTemplateRepository orderTemplateRepository;
    private final OrderTemplateLineRepository orderTemplateLineRepository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 新建医嘱组套模板头：初始化草稿态，校验必填与模板编码在对象+绑定维度唯一后落库并发布创建事件。
     *
     * @param command 归属对象、绑定类型、场景、编码名称等及操作人
     * @return 新模板主键
     * @throws BusinessException 编码冲突或不变式失败时
     */
    public Long createOrderTemplate(CreateOrderTemplateCommand command) {
        OrderTemplate template = new OrderTemplate();
        template.setObjectId(command.getObjectId());
        template.setBindingEnum(command.getBindingEnum());
        template.setSceneEnum(command.getSceneEnum());
        template.setTemplateCode(command.getTemplateCode());
        template.setName(command.getName());
        template.setDescription(command.getDescription());
        // 业务分支处理。
        if (command.getSortWeight() == null) {
            template.setSortWeight(0);
        } else {
            template.setSortWeight(command.getSortWeight());
        }
        template.setRemark(command.getRemark());
        template.initDraft();

        // 校验：必填与基础合法性（BR-OTL-001/002/005/011）
        template.assertRequiredFields();

        // 校验：编码唯一（BR-OTL-003；仅当 templateCode 非空时）
        if (template.getTemplateCode() != null && !template.getTemplateCode().trim().isEmpty()) {
            boolean exists = orderTemplateRepository.existsByTemplateCodeAndObjectIdAndBindingEnum(
                template.getTemplateCode().trim(),
                template.getObjectId(),
                template.getBindingEnum()
            );
            // 业务分支处理。
            if (exists) {
                throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_CODE_CONFLICT, "模板编码冲突");
            }
        }

        OrderTemplate saved = orderTemplateRepository.save(template);
        domainEventPublisher.publish(new OrderTemplateCreatedEvent(saved.getId(), saved.getObjectId(), saved.getBindingEnum(), saved.getSceneEnum(), command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 更新模板头字段：须可编辑且非退役；合并非空变更、重验编码唯一后保存；有变更键时发布更新事件。
     *
     * @param command 模板主键与待更新头字段
     * @throws BusinessException 模板不存在、不可编辑、编码冲突或退役禁止修改时
     */
    public void updateOrderTemplateHeader(UpdateOrderTemplateHeaderCommand command) {
        OrderTemplate template = requireTemplate(command.getOrderTemplateId());
        template.assertEditable();

        // 状态约束：RETIRED 禁止修改头（BR-OTL-017），委托聚合根执行门控。
        template.assertNotRetired();

        List<String> changedKeys = new ArrayList<>();
        // 业务分支处理。
        if (command.getSceneEnum() != null) {
            template.setSceneEnum(command.getSceneEnum());
            changedKeys.add("scene_enum");
        }
        // 业务分支处理。
        if (command.getTemplateCode() != null) {
            template.setTemplateCode(command.getTemplateCode());
            changedKeys.add("template_code");
        }
        // 业务分支处理。
        if (command.getName() != null) {
            template.setName(command.getName());
            changedKeys.add("name");
        }
        // 业务分支处理。
        if (command.getDescription() != null) {
            template.setDescription(command.getDescription());
            changedKeys.add("description");
        }
        // 业务分支处理。
        if (command.getSortWeight() != null) {
            template.setSortWeight(command.getSortWeight());
            changedKeys.add("sort_weight");
        }
        // 业务分支处理。
        if (command.getRemark() != null) {
            template.setRemark(command.getRemark());
            changedKeys.add("remark");
        }
        // 业务分支处理。
        if (command.getStatusEnum() != null) {
            template.setStatusEnum(command.getStatusEnum());
            changedKeys.add("status_enum");
        }

        template.assertRequiredFields();

        // 业务分支处理。
        if (template.getTemplateCode() != null && !template.getTemplateCode().trim().isEmpty()) {
            boolean exists = orderTemplateRepository.existsByTemplateCodeAndObjectIdAndBindingEnumAndIdNot(
                template.getTemplateCode().trim(),
                template.getObjectId(),
                template.getBindingEnum(),
                template.getId()
            );
            // 业务分支处理。
            if (exists) {
                throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_CODE_CONFLICT, "模板编码冲突");
            }
        }

        OrderTemplate saved = orderTemplateRepository.save(template);
        // 业务分支处理。
        if (!changedKeys.isEmpty()) {
            domainEventPublisher.publish(new OrderTemplateUpdatedEvent(saved.getId(), changedKeys, command.getOperatorId()));
        }
    }

    /**
     * 发布模板：须至少存在一条有效明细行，状态迁移为已发布后保存并发布发布事件。
     *
     * @param command 模板主键与操作人
     * @throws BusinessException 空壳不可发布或状态不允许时
     */
    public void publishOrderTemplate(PublishOrderTemplateCommand command) {
        OrderTemplate template = requireTemplate(command.getOrderTemplateId());
        template.assertEditable();

        // 校验：发布时必须至少有一条有效行（BR-OTL-006）
        List<OrderTemplateLine> lines = orderTemplateLineRepository.findAllByOrderTemplateId(template.getId());
        // 业务分支处理。
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "空壳组套不可发布");
        }

        template.publish();
        OrderTemplate saved = orderTemplateRepository.save(template);
        domainEventPublisher.publish(new OrderTemplatePublishedEvent(saved.getId(), command.getOperatorId()));
    }

    /**
     * 退役模板：标记为退役并发布退役事件。
     *
     * @param command 模板主键与操作人
     * @throws BusinessException 模板不存在或不可编辑时
     */
    public void retireOrderTemplate(RetireOrderTemplateCommand command) {
        OrderTemplate template = requireTemplate(command.getOrderTemplateId());
        template.assertEditable();
        template.retire();
        OrderTemplate saved = orderTemplateRepository.save(template);
        domainEventPublisher.publish(new OrderTemplateRetiredEvent(saved.getId(), command.getOperatorId()));
    }

    /**
     * 软删模板头并级联软删全部明细行，发布删除事件；已删则幂等返回。
     *
     * @param command 模板主键与操作人
     * @throws BusinessException 模板不存在时
     */
    public void deleteOrderTemplate(DeleteOrderTemplateCommand command) {
        OrderTemplate template = requireTemplate(command.getOrderTemplateId());
        // 业务分支处理。
        if (Boolean.TRUE.equals(template.getIsDeleted())) {
            return;
        }
        template.markDeleted();
        orderTemplateRepository.save(template);

        // 级联软删行（BR-OTL-021）
        List<OrderTemplateLine> lines = orderTemplateLineRepository.findAllByOrderTemplateId(template.getId());
        for (OrderTemplateLine line : lines) {
            line.markDeleted();
        }
        orderTemplateLineRepository.saveAll(lines);

        domainEventPublisher.publish(new OrderTemplateDeletedEvent(template.getId(), command.getOperatorId()));
    }

    /**
     * 新增组套明细行：模板须允许维护行，校验行不变式后保存并发布行新增事件。
     *
     * @param command 模板主键、行种类与各默认医嘱字段
     * @return 新行主键
     * @throws BusinessException 模板不存在或不允许维护行、行校验失败时
     */
    public Long addOrderTemplateLine(AddOrderTemplateLineCommand command) {
        OrderTemplate template = requireTemplate(command.getOrderTemplateId());
        template.assertLineMaintainable();

        OrderTemplateLine line = new OrderTemplateLine();
        line.setOrderTemplateId(template.getId());
        line.setLineKindEnum(command.getLineKindEnum());
        line.setMedicationDefinitionId(command.getMedicationDefinitionId());
        line.setActivityDefinitionId(command.getActivityDefinitionId());
        line.setDeviceDefinitionId(command.getDeviceDefinitionId());
        line.setGroupKey(command.getGroupKey());
        line.setSortOrder(command.getSortOrder());
        line.setDefaultQuantity(command.getDefaultQuantity());
        line.setDefaultUnitCode(command.getDefaultUnitCode());
        line.setDefaultDays(command.getDefaultDays());
        line.setFrequencyId(command.getFrequencyId());
        line.setRouteCode(command.getRouteCode());
        line.setDosageText(command.getDosageText());
        line.setDefaultPerformOrgId(command.getDefaultPerformOrgId());
        line.setLineRemark(command.getLineRemark());

        line.assertRequiredFields();

        OrderTemplateLine saved = orderTemplateLineRepository.save(line);
        domainEventPublisher.publish(new OrderTemplateLineAddedEvent(template.getId(), saved.getId(), saved.getLineKindEnum(), command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 更新组套明细行：仅覆盖命令中非空字段，模板须允许维护行，保存后发布行更新事件。
     *
     * @param command 行主键与增量字段
     * @throws BusinessException 行或模板不存在、不允许维护或校验失败时
     */
    public void updateOrderTemplateLine(UpdateOrderTemplateLineCommand command) {
        OrderTemplateLine line = requireLine(command.getLineId());
        OrderTemplate template = requireTemplate(line.getOrderTemplateId());
        template.assertLineMaintainable();

        // 业务分支处理。
        if (command.getGroupKey() != null) {
            line.setGroupKey(command.getGroupKey());
        }
        // 业务分支处理。
        if (command.getSortOrder() != null) {
            line.setSortOrder(command.getSortOrder());
        }
        // 业务分支处理。
        if (command.getDefaultQuantity() != null) {
            line.setDefaultQuantity(command.getDefaultQuantity());
        }
        // 业务分支处理。
        if (command.getDefaultUnitCode() != null) {
            line.setDefaultUnitCode(command.getDefaultUnitCode());
        }
        // 业务分支处理。
        if (command.getDefaultDays() != null) {
            line.setDefaultDays(command.getDefaultDays());
        }
        // 业务分支处理。
        if (command.getFrequencyId() != null) {
            line.setFrequencyId(command.getFrequencyId());
        }
        // 业务分支处理。
        if (command.getRouteCode() != null) {
            line.setRouteCode(command.getRouteCode());
        }
        // 业务分支处理。
        if (command.getDosageText() != null) {
            line.setDosageText(command.getDosageText());
        }
        // 业务分支处理。
        if (command.getDefaultPerformOrgId() != null) {
            line.setDefaultPerformOrgId(command.getDefaultPerformOrgId());
        }
        // 业务分支处理。
        if (command.getLineRemark() != null) {
            line.setLineRemark(command.getLineRemark());
        }

        line.assertRequiredFields();
        OrderTemplateLine saved = orderTemplateLineRepository.save(line);
        domainEventPublisher.publish(new OrderTemplateLineUpdatedEvent(saved.getOrderTemplateId(), saved.getId(), command.getOperatorId()));
    }

    /**
     * 软删组套明细行并发布行移除事件。
     *
     * @param command 行主键与操作人
     * @throws BusinessException 行或模板不存在、不允许维护时
     */
    public void removeOrderTemplateLine(RemoveOrderTemplateLineCommand command) {
        OrderTemplateLine line = requireLine(command.getLineId());
        OrderTemplate template = requireTemplate(line.getOrderTemplateId());
        template.assertLineMaintainable();

        line.markDeleted();
        OrderTemplateLine saved = orderTemplateLineRepository.save(line);
        domainEventPublisher.publish(new OrderTemplateLineRemovedEvent(saved.getOrderTemplateId(), saved.getId(), command.getOperatorId()));
    }

    /**
     * 按主键加载组套模板，不存在则抛出业务异常。
     *
     * @param orderTemplateId 组套模板主键
     * @return 组套模板实体
     * @throws BusinessException 模板不存在时
     */
    private OrderTemplate requireTemplate(Long orderTemplateId) {
        return orderTemplateRepository.findById(orderTemplateId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_NOT_FOUND, "组套模板不存在"));
    }

    /**
     * 按主键加载组套模板行，不存在则抛出业务异常。
     *
     * @param lineId 组套模板行主键
     * @return 组套模板行实体
     * @throws BusinessException 模板行不存在时
     */
    private OrderTemplateLine requireLine(Long lineId) {
        return orderTemplateLineRepository.findById(lineId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_NOT_FOUND, "组套模板行不存在"));
    }
}

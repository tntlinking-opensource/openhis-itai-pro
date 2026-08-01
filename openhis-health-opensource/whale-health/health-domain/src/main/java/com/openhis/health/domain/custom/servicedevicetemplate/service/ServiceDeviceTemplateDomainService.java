/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.servicedevicetemplate.service;

import com.openhis.health.domain.custom.servicedevicetemplate.command.*;
import com.openhis.health.domain.custom.servicedevicetemplate.entity.ServiceDeviceTemplate;
import com.openhis.health.domain.custom.servicedevicetemplate.repository.ServiceDeviceTemplateRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.ServiceDeviceTemplateRangeCode;
import com.openhis.health.domain.share.events.custom.servicedevicetemplate.*;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * 诊疗项目与耗材/设备活动绑定写侧领域服务。
 *
 * <p>维护用法、诊疗来源项与耗材主数据、设备活动之间的数量与单位绑定；
 * 新建时范围码应由调用方显式传入（门诊/住院），缺省时仍兜底历史全院码以兼容旧调用。</p>
 */
@Service
@RequiredArgsConstructor
public class ServiceDeviceTemplateDomainService {

    private final ServiceDeviceTemplateRepository repository;
    private final IDomainEventPublisher domainEventPublisher;


    /**
     * 新建绑定：装配范围/类型/物品与设备活动引用；范围未传时兜底历史全院码、状态默认启用；校验后落库并发布创建事件。
     *
     * @param command 绑定字段与操作人
     * @return 新记录主键
     * @throws BusinessException 不变式或目标类型不允许时
     */
    public Long createBinding(CreateBindingCommand command) {
        ServiceDeviceTemplate binding = new ServiceDeviceTemplate();
        // 适用范围未传时兜底历史全院码（应用层保存须显式传门诊/住院）。
        if (command.getRangeCode() == null) {
            binding.setRangeCode(ServiceDeviceTemplateRangeCode.HOSPITAL.getCode());
        } else {
            binding.setRangeCode(command.getRangeCode());
        }
        binding.setTypeCode(command.getTypeCode());
        binding.setItemTable(command.getItemTable());
        binding.setItemId(command.getItemId());
        binding.setDevActTable(command.getDevActTable());
        binding.setDevActId(command.getDevActId());
        binding.setQuantity(command.getQuantity());
        binding.setUnitCode(command.getUnitCode());
        // 状态未传时默认启用，保证新建绑定可立即参与匹配。
        if (command.getStatusEnum() == null) {
            binding.initActiveStatus();
        } else {
            binding.setStatusEnum(command.getStatusEnum());
        }

        // 校验：必填、数量等（BR-SDT-001/008）
        binding.assertRequiredFields();

        ServiceDeviceTemplate saved = repository.save(binding);
        domainEventPublisher.publish(new ServiceDeviceTemplateCreatedEvent(saved.getId(), command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 编辑绑定：仅覆盖命令中非空字段，再次校验不变式与目标类型后保存并发布编辑事件。
     *
     * @param command 绑定主键与增量字段
     * @throws BusinessException 绑定不存在或校验失败时
     */
    public void editBinding(EditBindingCommand command) {
        ServiceDeviceTemplate binding = repository.findById(command.getBindingId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_NOT_FOUND, "绑定不存在"));

        // 更新：仅覆盖非空字段
        if (command.getRangeCode() != null) {
            binding.setRangeCode(command.getRangeCode());
        }
        // 命令传入绑定类型时覆盖类型码。
        if (command.getTypeCode() != null) {
            binding.setTypeCode(command.getTypeCode());
        }
        // 命令传入物品表名时覆盖物品锚点表。
        if (command.getItemTable() != null) {
            binding.setItemTable(command.getItemTable());
        }
        // 命令传入物品主键时覆盖物品锚点 ID。
        if (command.getItemId() != null) {
            binding.setItemId(command.getItemId());
        }
        // 命令传入设备活动表名时覆盖活动锚点表。
        if (command.getDevActTable() != null) {
            binding.setDevActTable(command.getDevActTable());
        }
        // 命令传入设备活动主键时覆盖活动锚点 ID。
        if (command.getDevActId() != null) {
            binding.setDevActId(command.getDevActId());
        }
        // 命令传入数量时覆盖绑定数量。
        if (command.getQuantity() != null) {
            binding.setQuantity(command.getQuantity());
        }
        // 命令传入单位时覆盖计量单位。
        if (command.getUnitCode() != null) {
            binding.setUnitCode(command.getUnitCode());
        }
        // 命令传入状态时覆盖启用/停用态。
        if (command.getStatusEnum() != null) {
            binding.setStatusEnum(command.getStatusEnum());
        }

        binding.assertRequiredFields();

        ServiceDeviceTemplate saved = repository.save(binding);
        domainEventPublisher.publish(new ServiceDeviceTemplateEditedEvent(saved.getId(), command.getOperatorId()));
    }

    /**
     * 软删绑定并发布删除事件。
     *
     * @param command 绑定主键与操作人
     * @throws BusinessException 绑定不存在时
     */
    public void deleteBinding(DeleteBindingCommand command) {
        ServiceDeviceTemplate binding = repository.findById(command.getBindingId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_NOT_FOUND, "绑定不存在"));

        binding.markDeleted();
        ServiceDeviceTemplate saved = repository.save(binding);
        domainEventPublisher.publish(new ServiceDeviceTemplateDeletedEvent(saved.getId(), command.getOperatorId()));
    }

    /**
     * 启用绑定并发布激活事件。
     *
     * @param command 绑定主键与操作人
     * @throws BusinessException 绑定不存在时
     */
    public void activateBinding(ActivateBindingCommand command) {
        ServiceDeviceTemplate binding = repository.findById(command.getBindingId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_NOT_FOUND, "绑定不存在"));
        binding.activate();
        ServiceDeviceTemplate saved = repository.save(binding);
        domainEventPublisher.publish(new ServiceDeviceTemplateActivatedEvent(saved.getId(), command.getOperatorId()));
    }

    /**
     * 停用绑定并发布停用（退役）事件。
     *
     * @param command 绑定主键与操作人
     * @throws BusinessException 绑定不存在时
     */
    public void deactivateBinding(DeactivateBindingCommand command) {
        ServiceDeviceTemplate binding = repository.findById(command.getBindingId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_NOT_FOUND, "绑定不存在"));
        binding.deactivate();
        ServiceDeviceTemplate saved = repository.save(binding);
        domainEventPublisher.publish(new ServiceDeviceTemplateRetiredEvent(saved.getId(), command.getOperatorId()));
    }
}

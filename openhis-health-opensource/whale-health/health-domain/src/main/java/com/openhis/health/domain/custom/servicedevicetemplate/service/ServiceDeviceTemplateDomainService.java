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
 * <p>维护号源、服务项目等目标与耗材主数据、设备活动之间的数量与单位绑定；新建默认全院范围，变更后走聚合不变式与类型门禁。</p>
 */
@Service
@RequiredArgsConstructor
public class ServiceDeviceTemplateDomainService {

    private final ServiceDeviceTemplateRepository repository;
    private final IDomainEventPublisher domainEventPublisher;


    /**
     * 新建绑定：装配范围/类型/物品与设备活动引用，缺省范围全院、状态启用；校验必填与目标类型允许后落库并发布创建事件。
     *
     * @param command 绑定字段与操作人
     * @return 新记录主键
     * @throws BusinessException 不变式或目标类型不允许时
     */
    public Long createBinding(CreateBindingCommand command) {
        ServiceDeviceTemplate binding = new ServiceDeviceTemplate();
        // 业务分支处理。
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
        // 业务分支处理。
        if (command.getStatusEnum() == null) {
            binding.initActiveStatus();
        } else {
            binding.setStatusEnum(command.getStatusEnum());
        }

        // 校验：必填、数量、号源绑定目标限制等（BR-SDT-001/008/005）
        binding.assertRequiredFields();
        binding.assertTargetAllowedForType();

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
        // 业务分支处理。
        if (command.getTypeCode() != null) {
            binding.setTypeCode(command.getTypeCode());
        }
        // 业务分支处理。
        if (command.getItemTable() != null) {
            binding.setItemTable(command.getItemTable());
        }
        // 业务分支处理。
        if (command.getItemId() != null) {
            binding.setItemId(command.getItemId());
        }
        // 业务分支处理。
        if (command.getDevActTable() != null) {
            binding.setDevActTable(command.getDevActTable());
        }
        // 业务分支处理。
        if (command.getDevActId() != null) {
            binding.setDevActId(command.getDevActId());
        }
        // 业务分支处理。
        if (command.getQuantity() != null) {
            binding.setQuantity(command.getQuantity());
        }
        // 业务分支处理。
        if (command.getUnitCode() != null) {
            binding.setUnitCode(command.getUnitCode());
        }
        // 业务分支处理。
        if (command.getStatusEnum() != null) {
            binding.setStatusEnum(command.getStatusEnum());
        }

        binding.assertRequiredFields();
        binding.assertTargetAllowedForType();

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

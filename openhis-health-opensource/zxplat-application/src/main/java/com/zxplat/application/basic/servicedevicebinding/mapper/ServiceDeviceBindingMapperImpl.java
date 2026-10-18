/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.servicedevicebinding.mapper;

import com.zxplat.application.basic.servicedevicebinding.dto.ServiceDeviceBindingDto;
import com.zxplat.application.basic.servicedevicebinding.request.SaveServiceDeviceBindingRequest;
import com.zxplat.application.shared.converter.TextValueConverter;
import com.zxplat.domain.custom.servicedevicetemplate.command.CreateBindingCommand;
import com.zxplat.domain.custom.servicedevicetemplate.command.EditBindingCommand;
import com.zxplat.domain.custom.servicedevicetemplate.queries.model.ServiceDeviceTemplateQueryModel;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.enums.ServiceDeviceTemplateTypeCode;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

/**
 * {@link ServiceDeviceBindingMapper} 默认实现：绑定类型、状态、来源表名与领域枚举/表常量对齐。
 */
@Component
public class ServiceDeviceBindingMapperImpl implements ServiceDeviceBindingMapper {

    /**
     * 将服务设备模板查询行投影为绑定列表 DTO。
     *
     * @param model 查询模型；{@code null} 时返回 {@code null}
     * @return 绑定行 DTO
     */
    @Override
    public ServiceDeviceBindingDto toDto(ServiceDeviceTemplateQueryModel model) {
        if (model == null) {
            return null;
        }
        ServiceDeviceBindingDto dto = new ServiceDeviceBindingDto();
        dto.setId(model.getId());
        dto.setRangeCode(model.getRangeCode());
        dto.setTypeCode(model.getTypeCode());
        dto.setItemTable(model.getItemTable());
        dto.setItemNo(model.getItemId());
        dto.setDevActTable(model.getDevActTable());
        dto.setDevActId(model.getDevActId());
        dto.setQuantity(model.getQuantity());
        dto.setUnitCode(model.getUnitCode());
        dto.setStatusEnum(model.getStatusEnum());
        return dto;
    }

    /**
     * 将保存请求装配为领域「创建绑定」命令。
     *
     * @param request    保存请求（无 id 表示新建）
     * @param operatorId 当前操作人
     * @return 领域创建命令
     */
    @Override
    public CreateBindingCommand toCreateCommand(SaveServiceDeviceBindingRequest request, Long operatorId) {
        if (request == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "保存失败：请求体不能为空");
        }
        String typeCode = this.resolveTypeCode(request.getBindType());
        CreateBindingCommand command = new CreateBindingCommand();
        command.setRangeCode(request.getRangeCode());
        command.setTypeCode(typeCode);
        command.setItemTable(this.resolveSourceTable(typeCode));
        command.setItemId(request.getSourceId());
        command.setDevActTable(request.getTargetTable());
        command.setDevActId(TextValueConverter.requireLong(
            request.getTargetId(), "目标ID不能为空", "目标ID格式不合法"));
        command.setQuantity(request.getQuantity());
        command.setUnitCode(request.getUnitCode());
        command.setStatusEnum(this.resolveStatusCode(request.getStatusEnum()));
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 将保存请求装配为领域「编辑绑定」命令。
     *
     * @param request    保存请求（含绑定主键 id）
     * @param operatorId 当前操作人
     * @return 领域编辑命令
     */
    @Override
    public EditBindingCommand toEditCommand(SaveServiceDeviceBindingRequest request, Long operatorId) {
        if (request == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "保存失败：请求体不能为空");
        }
        String typeCode = this.resolveTypeCode(request.getBindType());
        EditBindingCommand command = new EditBindingCommand();
        command.setBindingId(request.getId());
        command.setRangeCode(request.getRangeCode());
        command.setTypeCode(typeCode);
        command.setItemTable(this.resolveSourceTable(typeCode));
        command.setItemId(request.getSourceId());
        command.setDevActTable(request.getTargetTable());
        command.setDevActId(TextValueConverter.requireLong(
            request.getTargetId(), "目标ID不能为空", "目标ID格式不合法"));
        command.setQuantity(request.getQuantity());
        command.setUnitCode(request.getUnitCode());
        command.setStatusEnum(this.resolveStatusCode(request.getStatusEnum()));
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 解析绑定类型持久化码。
     *
     * @param bindType 请求绑定类型码
     * @return 领域类型码
     */
    private String resolveTypeCode(String bindType) {
        if (StringUtils.isEmpty(bindType)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "保存失败：绑定类型不能为空");
        }
        ServiceDeviceTemplateTypeCode type = EnumUtil.fromCode(ServiceDeviceTemplateTypeCode.class, bindType.trim());
        if (type == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "保存失败：绑定类型不合法");
        }
        return type.getCode();
    }

    /**
     * 解析发布状态持久化码。
     *
     * @param statusEnum 请求状态码
     * @return 领域状态码
     */
    private String resolveStatusCode(String statusEnum) {
        if (StringUtils.isEmpty(statusEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "保存失败：状态不能为空");
        }
        PublicationStatus status = EnumUtil.fromCode(PublicationStatus.class, statusEnum.trim());
        if (status == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "保存失败：状态不合法");
        }
        if (!PublicationStatus.ACTIVE.equals(status) && !PublicationStatus.INACTIVE.equals(status)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "保存失败：状态仅支持启用或停用");
        }
        return status.getCode();
    }

    /**
     * 绑定类型码映射到来源项物理表名。
     *
     * @param typeCode 领域绑定类型码
     * @return 来源表名
     */
    private String resolveSourceTable(String typeCode) {
        if (ServiceDeviceTemplateTypeCode.USAGE.getCode().equals(typeCode)) {
            return TableNameConstants.TER_CONCEPT;
        }
        if (ServiceDeviceTemplateTypeCode.ACTIVITY.getCode().equals(typeCode)) {
            return TableNameConstants.WKF_ACTIVITY_DEFINITION;
        }
        throw new BusinessException(
            ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "保存失败：绑定类型不合法");
    }
}

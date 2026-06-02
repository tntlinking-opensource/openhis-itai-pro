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

package com.openhis.health.application.basic.orderbinding.mapper;

import com.openhis.health.application.common.converter.TextValueConverter;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.application.basic.orderbinding.dto.OrderBindingDto;
import com.openhis.health.application.basic.orderbinding.request.SaveOrderBindingRequest;
import com.openhis.health.domain.custom.servicedevicetemplate.command.CreateBindingCommand;
import com.openhis.health.domain.custom.servicedevicetemplate.command.EditBindingCommand;
import com.openhis.health.domain.custom.servicedevicetemplate.queries.model.ServiceDeviceTemplateQueryModel;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.enums.ServiceDeviceTemplateTypeCode;
import com.openhis.whale.core.application.exception.ValidationException;
import org.springframework.stereotype.Component;

/**
 * {@link OrderBindingMapper} 默认实现：前后端绑定类型、状态、来源表名等与领域枚举/表常量互转。
 */
@Component
public class OrderBindingMapperImpl implements OrderBindingMapper {

    /**
     * 将服务设备模板查询行投影为绑定列表 DTO（类型码、状态整型与前端约定互转）。
     *
     * @param model 查询模型；{@code null} 时返回 {@code null}
     * @return 绑定行 DTO
     */
    @Override
    public OrderBindingDto toDto(ServiceDeviceTemplateQueryModel model) {
        if (model == null) {
            return null;
        }
        OrderBindingDto dto = new OrderBindingDto();
        dto.setId(model.getId());
        dto.setRangeCode(model.getRangeCode());
        dto.setTypeCode(toFrontBindType(model.getTypeCode()));
        dto.setItemNo(model.getItemId());
        dto.setDevActTable(model.getDevActTable());
        dto.setDevActId(model.getDevActId());
        dto.setQuantity(model.getQuantity());
        dto.setUnitCode(model.getUnitCode());
        dto.setStatusEnum(toFrontStatus(model.getStatusEnum()));
        return dto;
    }

    /**
     * 将保存请求装配为领域「创建绑定」命令；请求体为空时抛出校验异常。
     *
     * @param request 保存请求（无 id 表示新建）
     * @return 领域创建命令
     * @throws ValidationException 请求体为空时
     */
    @Override
    public CreateBindingCommand toCreateCommand(SaveOrderBindingRequest request) {
        if (request == null) {
            throw new ValidationException("保存失败：请求体不能为空");
        }
        CreateBindingCommand command = new CreateBindingCommand();
        command.setRangeCode(request.getRangeCode());
        command.setTypeCode(toDomainBindType(request.getBindType()));
        command.setItemTable(toSourceTable(request.getBindType()));
        command.setItemId(request.getSourceId());
        command.setDevActTable(request.getTargetTable());
        command.setDevActId(TextValueConverter.requireLong(
            request.getTargetId(), "目标ID不能为空", "目标ID格式不合法"));
        command.setQuantity(request.getQuantity());
        command.setUnitCode(request.getUnitCode());
        command.setStatusEnum(toDomainStatus(request.getStatusEnum()));
        command.setOperatorId(0L);
        return command;
    }

    /**
     * 将保存请求装配为领域「编辑绑定」命令；请求体为空时抛出校验异常。
     *
     * @param request 保存请求（含绑定主键 id）
     * @return 领域编辑命令
     * @throws ValidationException 请求体为空时
     */
    @Override
    public EditBindingCommand toEditCommand(SaveOrderBindingRequest request) {
        if (request == null) {
            throw new ValidationException("保存失败：请求体不能为空");
        }
        EditBindingCommand command = new EditBindingCommand();
        command.setBindingId(request.getId());
        command.setRangeCode(request.getRangeCode());
        command.setTypeCode(toDomainBindType(request.getBindType()));
        command.setItemTable(toSourceTable(request.getBindType()));
        command.setItemId(request.getSourceId());
        command.setDevActTable(request.getTargetTable());
        command.setDevActId(TextValueConverter.requireLong(
            request.getTargetId(), "目标ID不能为空", "目标ID格式不合法"));
        command.setQuantity(request.getQuantity());
        command.setUnitCode(request.getUnitCode());
        command.setStatusEnum(toDomainStatus(request.getStatusEnum()));
        command.setOperatorId(0L);
        return command;
    }

    /**
     * 领域绑定类型码转前端 1/2/3 字符串；未知时原样返回便于排障。
     *
     * @param domainTypeCode 领域类型码
     * @return 前端类型字符串
     */
    private String toFrontBindType(String domainTypeCode) {
        if (ServiceDeviceTemplateTypeCode.SLOT.getCode().equals(domainTypeCode)) {
            return "1";
        }
        if (ServiceDeviceTemplateTypeCode.USAGE.getCode().equals(domainTypeCode)) {
            return "2";
        }
        if (ServiceDeviceTemplateTypeCode.ACTIVITY.getCode().equals(domainTypeCode)) {
            return "3";
        }
        return domainTypeCode;
    }

    /**
     * 前端绑定类型 1/2/3 转领域 {@link ServiceDeviceTemplateTypeCode} 持久化码。
     *
     * @param frontTypeCode 前端整型类型
     * @return 领域类型码；无法识别时返回 {@code null}
     */
    private String toDomainBindType(Integer frontTypeCode) {
        if (frontTypeCode == null) {
            return null;
        }
        if (frontTypeCode == 1) {
            return ServiceDeviceTemplateTypeCode.SLOT.getCode();
        }
        if (frontTypeCode == 2) {
            return ServiceDeviceTemplateTypeCode.USAGE.getCode();
        }
        if (frontTypeCode == 3) {
            return ServiceDeviceTemplateTypeCode.ACTIVITY.getCode();
        }
        return null;
    }

    /**
     * 领域发布状态码转前端整型：生效→1，停用→2，未知默认 2（与列表停用展示一致）。
     *
     * @param statusEnum 领域状态码
     * @return 前端状态整型
     */
    private Integer toFrontStatus(String statusEnum) {
        if (PublicationStatus.ACTIVE.getCode().equals(statusEnum)) {
            return 1;
        }
        if (PublicationStatus.RETIRED.getCode().equals(statusEnum)) {
            return 2;
        }
        return 2;
    }

    /**
     * 前端状态 1/2 转领域发布状态码；非 1 一律视为停用。
     *
     * @param statusEnum 前端状态
     * @return 领域状态码；{@code null} 表示不按状态写入
     */
    private String toDomainStatus(Integer statusEnum) {
        if (statusEnum == null) {
            return null;
        }
        if (statusEnum == 1) {
            return PublicationStatus.ACTIVE.getCode();
        }
        return PublicationStatus.RETIRED.getCode();
    }

    /**
     * 前端绑定类型映射到来源项物理/逻辑表名常量（号源服务、术语概念、活动定义）。
     *
     * @param bindType 1 号源 2 用法 3 诊疗
     * @return 表名常量；不识别时 {@code null}
     */
    private String toSourceTable(Integer bindType) {
        if (bindType == null) {
            return null;
        }
        if (bindType == 1) {
            return TableNameConstants.ADM_HEALTHCARE_SERVICE;
        }
        if (bindType == 2) {
            return TableNameConstants.TER_CONCEPT;
        }
        if (bindType == 3) {
            return TableNameConstants.WKF_ACTIVITY_DEFINITION;
        }
        return null;
    }

}

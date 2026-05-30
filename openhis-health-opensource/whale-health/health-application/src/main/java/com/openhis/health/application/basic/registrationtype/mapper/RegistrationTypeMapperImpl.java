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

package com.openhis.health.application.basic.registrationtype.mapper;

import com.openhis.health.application.basic.registrationtype.dto.RegistrationTypeDto;
import com.openhis.health.application.basic.registrationtype.request.CreateRegistrationTypeRequest;
import com.openhis.health.application.basic.registrationtype.request.UpdateRegistrationTypeRequest;
import com.openhis.health.domain.administration.healthcareservice.command.CreateHealthcareServiceCommand;
import com.openhis.health.domain.administration.healthcareservice.command.ModifyHealthcareServiceCommand;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * {@link RegistrationTypeMapper} 默认实现：类型码缺省为 {@code "1"}，布尔启用标志与前端整型互转。
 */
@Component
public class RegistrationTypeMapperImpl implements RegistrationTypeMapper {
    /** 未传 typeCode 时写入领域的默认挂号类型编码。 */
    private static final String DEFAULT_REGISTRATION_TYPE_CODE = "1";

    /**
     * 将医疗服务实体转为挂号类型 DTO；实体为 {@code null} 时返回 {@code null}。
     *
     * @param entity 医疗服务聚合
     * @return DTO
     */
    @Override
    public RegistrationTypeDto toDto(HealthcareService entity) {
        if (entity == null) {
            return null;
        }
        RegistrationTypeDto dto = new RegistrationTypeDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setYbNo(entity.getYbNo());
        dto.setBusNo(entity.getBusNo());
        if (Boolean.TRUE.equals(entity.getActiveFlag())) {
            dto.setActiveFlag(1);
        } else if (Boolean.FALSE.equals(entity.getActiveFlag())) {
            dto.setActiveFlag(0);
        } else {
            dto.setActiveFlag(null);
        }
        return dto;
    }

    /**
     * 将创建请求装配为单条 {@link CreateHealthcareServiceCommand}；请求为 {@code null} 时返回 {@code null}。
     *
     * @param request    创建请求
     * @param operatorId 操作人
     * @return 领域创建命令
     */
    @Override
    public CreateHealthcareServiceCommand toCreateCommand(CreateRegistrationTypeRequest request, Long operatorId) {
        if (request == null) {
            return null;
        }
        CreateHealthcareServiceCommand cmd = new CreateHealthcareServiceCommand();
        cmd.setOperatorId(operatorId);
        CreateHealthcareServiceCommand.Item item = new CreateHealthcareServiceCommand.Item();
        item.setName(request.getName());
        item.setTypeCode(resolveTypeCode(request.getTypeCode()));
        item.setCategoryCode(null);
        item.setOfferedOrgId(null);
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 将更新请求装配为单条 {@link ModifyHealthcareServiceCommand}；请求为 {@code null} 时返回 {@code null}。
     *
     * @param request    更新请求
     * @param operatorId 操作人
     * @return 领域修改命令
     */
    @Override
    public ModifyHealthcareServiceCommand toUpdateCommand(UpdateRegistrationTypeRequest request, Long operatorId) {
        if (request == null) {
            return null;
        }
        ModifyHealthcareServiceCommand cmd = new ModifyHealthcareServiceCommand();
        cmd.setOperatorId(operatorId);
        ModifyHealthcareServiceCommand.Item item = new ModifyHealthcareServiceCommand.Item();
        item.setServiceId(request.getId());
        item.setName(request.getName());
        item.setTypeCode(resolveTypeCode(request.getTypeCode()));
        item.setCategoryCode(null);
        item.setOfferedOrgId(null);
        item.setChangedFields(null);
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 挂号类型编码归一：空白则回落默认编码 {@code 1}（与类常量 DEFAULT_REGISTRATION_TYPE_CODE 一致）。
     *
     * @param typeCode 请求中的类型码
     * @return 非空类型码
     */
    private String resolveTypeCode(String typeCode) {
        if (StringUtils.isBlank(typeCode)) {
            return DEFAULT_REGISTRATION_TYPE_CODE;
        }
        return typeCode.trim();
    }
}

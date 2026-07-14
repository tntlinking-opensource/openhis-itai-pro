/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.registrationtype.mapper;

import com.openhis.health.application.basic.registrationtype.dto.RegistrationTypeDto;
import com.openhis.health.application.basic.registrationtype.request.CreateRegistrationTypeRequest;
import com.openhis.health.application.basic.registrationtype.request.UpdateRegistrationTypeRequest;
import com.openhis.health.domain.administration.healthcareservice.command.CreateHealthcareServiceCommand;
import com.openhis.health.domain.administration.healthcareservice.command.ModifyHealthcareServiceCommand;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;

/**
 * 挂号类型（映射为医疗服务 {@link HealthcareService}）应用层 DTO 与领域写命令装配契约。
 */
public interface RegistrationTypeMapper {

    /**
     * 将医疗服务实体投影为挂号类型 DTO（启用标志转为 1/0 整型）。
     *
     * @param entity 领域实体；{@code null} 时由实现返回 {@code null}
     * @return 挂号类型展示对象
     */
    RegistrationTypeDto toDto(HealthcareService entity);

    /**
     * 组装创建医疗服务命令（挂号类型新增）。
     *
     * @param request    创建请求；{@code null} 时由实现返回 {@code null}
     * @param operatorId 操作人主键，写入领域命令
     * @return 领域创建命令
     */
    CreateHealthcareServiceCommand toCreateCommand(CreateRegistrationTypeRequest request, Long operatorId);

    /**
     * 组装修改医疗服务命令（挂号类型编辑）。
     *
     * @param request    更新请求；{@code null} 时由实现返回 {@code null}
     * @param operatorId 操作人主键
     * @return 领域修改命令
     */
    ModifyHealthcareServiceCommand toUpdateCommand(UpdateRegistrationTypeRequest request, Long operatorId);
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.registrationtype.mapper;

import com.zxplat.application.basic.registrationtype.dto.RegistrationTypeDto;
import com.zxplat.application.basic.registrationtype.request.CreateRegistrationTypeRequest;
import com.zxplat.application.basic.registrationtype.request.UpdateRegistrationTypeRequest;
import com.zxplat.domain.administration.healthcareservice.command.CreateHealthcareServiceCommand;
import com.zxplat.domain.administration.healthcareservice.command.ModifyHealthcareServiceCommand;
import com.zxplat.domain.administration.healthcareservice.entity.HealthcareService;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

/**
 * {@link RegistrationTypeMapper} 默认实现：类型码缺省为 {@code "1"}；布尔启用标志与前端整型互转。
 */
@Component
public class RegistrationTypeMapperImpl implements RegistrationTypeMapper {
    /** 未传 typeCode 时写入领域的默认挂号类型编码。 */
    private static final String DEFAULT_REGISTRATION_TYPE_CODE = "1";

    /**
     * 将医疗服务实体转为挂号类型 DTO；实体为 {@code null} 时返回 {@code null}。
     *
     * @param entity 医疗服务聚合
     * @return DTO（金额/名称摘要由 AppService enrich）
     */
    @Override
    public RegistrationTypeDto toDto(HealthcareService entity) {
        if (entity == null) {
            return null;
        }
        RegistrationTypeDto dto = new RegistrationTypeDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setActivityIds(entity.getActivityIds());
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
        item.setActivityIds(request.getActivityIds());
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
        // 编辑时不改 type/category：传实体现值由 App 填入命令，避免 ACTIVE 误触 BR-HCS-008
        item.setTypeCode(resolveTypeCode(request.getTypeCode()));
        item.setCategoryCode(null);
        item.setOfferedOrgId(null);
        item.setActivityIds(request.getActivityIds());
        item.setChangedFields(null);
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 挂号类型编码归一：空白则回落默认编码 {@code 1}。
     *
     * @param typeCode 请求中的类型码
     * @return 非空类型码
     */
    private String resolveTypeCode(String typeCode) {
        if (StringUtils.isEmpty(typeCode)) {
            return DEFAULT_REGISTRATION_TYPE_CODE;
        }
        return typeCode.trim();
    }
}

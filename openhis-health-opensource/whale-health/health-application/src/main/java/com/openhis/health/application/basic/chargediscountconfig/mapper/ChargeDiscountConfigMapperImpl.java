/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.chargediscountconfig.mapper;

import com.openhis.health.application.basic.chargediscountconfig.dto.ChargeDiscountConfigDto;
import com.openhis.health.application.basic.chargediscountconfig.request.CreateChargeDiscountConfigRequest;
import com.openhis.health.application.basic.chargediscountconfig.request.ModifyChargeDiscountConfigRequest;
import com.openhis.health.domain.custom.chargediscountconfig.command.CreateChargeDiscountConfigCommand;
import com.openhis.health.domain.custom.chargediscountconfig.command.ModifyChargeDiscountConfigCommand;
import com.openhis.health.domain.custom.chargediscountconfig.queries.model.ChargeDiscountConfigQueryModel;
import com.openhis.whale.core.application.exception.ValidationException;
import org.springframework.stereotype.Component;

/**
 * {@link ChargeDiscountConfigMapper} 默认实现：读模型与写命令字段一一投影。
 */
@Component
public class ChargeDiscountConfigMapperImpl implements ChargeDiscountConfigMapper {

    /**
     * 读模型 → 列表 DTO（展示 enrich 字段留空，由 AppService 填充）。
     *
     * @param model 读模型
     * @return DTO；model 为 null 时返回 null
     */
    @Override
    public ChargeDiscountConfigDto toDto(ChargeDiscountConfigQueryModel model) {
        if (model == null) {
            return null;
        }
        ChargeDiscountConfigDto dto = new ChargeDiscountConfigDto();
        dto.setId(model.getId());
        dto.setProductTable(model.getProductTable());
        dto.setCategoryCode(model.getCategoryCode());
        dto.setProductId(model.getProductId());
        dto.setDiscountRate(model.getDiscountRate());
        dto.setStatusEnum(model.getStatusEnum());
        dto.setRemark(model.getRemark());
        dto.setOrgId(model.getOrgId());
        return dto;
    }

    /**
     * 创建请求 → 领域创建命令（含操作人）。
     *
     * @param request    创建请求
     * @param operatorId 操作人主键
     * @return 创建命令
     */
    @Override
    public CreateChargeDiscountConfigCommand toCreateCommand(
        CreateChargeDiscountConfigRequest request,
        Long operatorId
    ) {
        if (request == null) {
            throw new ValidationException("新增收费折扣配置失败：请求体不能为空");
        }
        CreateChargeDiscountConfigCommand command = new CreateChargeDiscountConfigCommand();
        command.setProductTable(request.getProductTable());
        command.setCategoryCode(request.getCategoryCode());
        command.setProductId(request.getProductId());
        command.setDiscountRate(request.getDiscountRate());
        command.setRemark(request.getRemark());
        command.setOrgId(request.getOrgId());
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 修改请求 → 领域修改命令（含操作人；折扣率 null=款员定率）。
     *
     * @param request    修改请求
     * @param operatorId 操作人主键
     * @return 修改命令
     */
    @Override
    public ModifyChargeDiscountConfigCommand toModifyCommand(
        ModifyChargeDiscountConfigRequest request,
        Long operatorId
    ) {
        if (request == null) {
            throw new ValidationException("修改收费折扣配置失败：请求体不能为空");
        }
        ModifyChargeDiscountConfigCommand command = new ModifyChargeDiscountConfigCommand();
        command.setId(request.getId());
        command.setProductTable(request.getProductTable());
        command.setCategoryCode(request.getCategoryCode());
        command.setProductId(request.getProductId());
        command.setDiscountRate(request.getDiscountRate());
        command.setRemark(request.getRemark());
        command.setOrgId(request.getOrgId());
        command.setOperatorId(operatorId);
        return command;
    }
}

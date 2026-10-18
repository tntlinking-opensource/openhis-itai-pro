/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.chargediscountconfig.mapper;

import com.zxplat.application.basic.chargediscountconfig.dto.ChargeDiscountConfigDto;
import com.zxplat.application.basic.chargediscountconfig.request.CreateChargeDiscountConfigRequest;
import com.zxplat.application.basic.chargediscountconfig.request.ModifyChargeDiscountConfigRequest;
import com.zxplat.domain.custom.chargediscountconfig.command.CreateChargeDiscountConfigCommand;
import com.zxplat.domain.custom.chargediscountconfig.command.ModifyChargeDiscountConfigCommand;
import com.zxplat.domain.custom.chargediscountconfig.queries.model.ChargeDiscountConfigQueryModel;

/**
 * 收费折扣配置应用层映射契约：读模型→DTO、请求→领域命令。
 */
public interface ChargeDiscountConfigMapper {

    /**
     * 读模型投影为列表/详情 DTO（不含目录名 enrich，由 AppService 补）。
     *
     * @param model 领域读模型；null 时返回 null
     * @return 展示 DTO
     */
    ChargeDiscountConfigDto toDto(ChargeDiscountConfigQueryModel model);

    /**
     * 创建请求 → 领域创建命令。
     *
     * @param request    创建请求；null 时由实现抛校验异常
     * @param operatorId 当前操作人
     * @return 创建命令
     */
    CreateChargeDiscountConfigCommand toCreateCommand(CreateChargeDiscountConfigRequest request, Long operatorId);

    /**
     * 修改请求 → 领域修改命令（全量覆盖命中键与折扣率）。
     *
     * @param request    修改请求；null 时由实现抛校验异常
     * @param operatorId 当前操作人
     * @return 修改命令
     */
    ModifyChargeDiscountConfigCommand toModifyCommand(ModifyChargeDiscountConfigRequest request, Long operatorId);
}

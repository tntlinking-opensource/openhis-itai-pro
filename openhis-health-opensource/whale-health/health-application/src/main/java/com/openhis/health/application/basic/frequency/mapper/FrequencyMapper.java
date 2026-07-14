/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.frequency.mapper;

import com.openhis.health.application.basic.frequency.dto.FrequencyDto;
import com.openhis.health.application.basic.frequency.request.CreateFrequencyRequest;
import com.openhis.health.application.basic.frequency.request.UpdateFrequencyRequest;
import com.openhis.health.domain.custom.frequency.command.CreateFrequencyCommand;
import com.openhis.health.domain.custom.frequency.command.ModifyFrequencyCommand;
import com.openhis.health.domain.custom.frequency.entity.Frequency;
import com.openhis.health.domain.custom.frequency.queries.model.FrequencyPageItemQueryModel;

/**
 * 频次应用层 DTO 与领域实体、分页读模型及写命令之间的装配契约。
 */
public interface FrequencyMapper {

    /**
     * 将聚合根实体投影为前端展示 DTO（字段最全路径）。
     *
     * @param entity 领域实体；{@code null} 时由实现返回 {@code null}
     * @return 频次展示对象
     */
    FrequencyDto toDto(Frequency entity);

    /**
     * 将分页列表行读模型投影为 DTO（字段为列表所需子集）。
     *
     * @param model 分页行；{@code null} 时由实现返回 {@code null}
     * @return 频次列表行
     */
    FrequencyDto toDto(FrequencyPageItemQueryModel model);

    /**
     * 组装领域「创建频次」命令。
     *
     * @param request 前端入参；{@code null} 时由实现抛校验异常
     * @param operatorId 当前操作人主键
     * @return 领域创建命令
     */
    CreateFrequencyCommand toCreateCommand(CreateFrequencyRequest request, Long operatorId);

    /**
     * 组装领域「修改频次」命令（不含 rateCode 等创建后不可变项由领域约束）。
     *
     * @param request 前端入参；{@code null} 时由实现抛校验异常
     * @param operatorId 当前操作人主键
     * @return 领域修改命令
     */
    ModifyFrequencyCommand toModifyCommand(UpdateFrequencyRequest request, Long operatorId);
}

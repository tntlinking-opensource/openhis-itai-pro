/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.executelocation.mapper;

import com.zxplat.application.basic.executelocation.dto.ExecuteLocationDto;
import com.zxplat.application.basic.executelocation.request.CreateExecuteLocationRequest;
import com.zxplat.application.basic.executelocation.request.UpdateExecuteLocationRequest;
import com.zxplat.domain.custom.activityexecutionlocation.command.CreateExecutionRuleCommand;
import com.zxplat.domain.custom.activityexecutionlocation.command.UpdateExecutionRuleCommand;
import com.zxplat.domain.custom.activityexecutionlocation.queries.model.ExecutionRuleListQueryModel;

import java.time.LocalTime;

/**
 * 「执行地点/执行规则」应用层与活动执行位置域命令、列表读模型之间的装配契约。
 */
public interface ExecuteLocationMapper {

    /**
     * 将执行规则列表行投影为应用层 DTO。
     *
     * @param model 列表读模型；{@code null} 时由实现返回 {@code null}
     * @return 执行规则行
     */
    ExecuteLocationDto toDto(ExecutionRuleListQueryModel model);

    /**
     * 将单条创建请求装配为批量创建命令中的子项。
     *
     * @param request 创建请求；{@code null} 时由实现返回 {@code null}
     * @return 领域子命令项
     */
    CreateExecutionRuleCommand.CreateExecutionRuleItemCommand toCreateItem(
        CreateExecuteLocationRequest request);

    /**
     * 将更新请求装配为领域更新命令。
     *
     * @param request 更新请求；{@code null} 时由实现返回 {@code null}
     * @return 领域更新命令
     */
    UpdateExecutionRuleCommand toUpdateCommand(UpdateExecuteLocationRequest request);

    /**
     * 将 {@code HH:mm:ss} 风格字符串解析为 {@link LocalTime}；空串返回 {@code null}。
     *
     * @param value 时间字面量
     * @return 解析结果
     */
    LocalTime parseTime(String value);
}

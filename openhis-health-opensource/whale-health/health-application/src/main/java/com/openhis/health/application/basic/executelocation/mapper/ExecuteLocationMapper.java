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

package com.openhis.health.application.basic.executelocation.mapper;

import com.openhis.health.application.basic.executelocation.dto.ExecuteLocationDto;
import com.openhis.health.application.basic.executelocation.request.CreateExecuteLocationRequest;
import com.openhis.health.application.basic.executelocation.request.UpdateExecuteLocationRequest;
import com.openhis.health.domain.custom.activityexecutionlocation.command.CreateExecutionRuleCommand;
import com.openhis.health.domain.custom.activityexecutionlocation.command.UpdateExecutionRuleCommand;
import com.openhis.health.domain.custom.activityexecutionlocation.queries.model.ExecutionRuleListQueryModel;

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
}

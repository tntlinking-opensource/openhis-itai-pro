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
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * {@link ExecuteLocationMapper} 默认实现：时间字符串解析为 {@link LocalTime}，优先级默认补 1。
 */
@Component
public class ExecuteLocationMapperImpl implements ExecuteLocationMapper {

    /**
     * 将执行规则列表行读模型投影为应用层 DTO；入参为 {@code null} 时返回 {@code null}。
     *
     * @param model 列表读模型
     * @return 执行规则行
     */
    @Override
    public ExecuteLocationDto toDto(ExecutionRuleListQueryModel model) {
        if (model == null) {
            return null;
        }
        ExecuteLocationDto dto = new ExecuteLocationDto();
        dto.setId(model.getId());
        dto.setActivityDefinitionId(model.getActivityDefinitionId());
        dto.setActivityCategoryCode(model.getActivityCategoryCode());
        dto.setExecutionOrgId(model.getExecutionOrgId());
        dto.setExecutionLocationId(model.getExecutionLocationId());
        dto.setStartTime(model.getStartTime());
        dto.setEndTime(model.getEndTime());
        dto.setPriority(model.getPriority());
        dto.setDisplayOrder(model.getDisplayOrder());
        dto.setRemark(model.getRemark());
        return dto;
    }

    /**
     * 将单条创建执行规则请求装配为批量创建命令中的子项；请求为 {@code null} 时返回 {@code null}。
     *
     * @param request 创建请求
     * @return 领域子命令项
     */
    @Override
    public CreateExecutionRuleCommand.CreateExecutionRuleItemCommand toCreateItem(
        CreateExecuteLocationRequest request
    ) {
        if (request == null) {
            return null;
        }
        CreateExecutionRuleCommand.CreateExecutionRuleItemCommand item =
            new CreateExecutionRuleCommand.CreateExecutionRuleItemCommand();
        item.setActivityDefinitionId(request.getActivityDefinitionId());
        item.setExecutionLocationId(request.getExecutionLocationId());
        item.setStartTime(parseTime(request.getStartTime()));
        item.setEndTime(parseTime(request.getEndTime()));
        item.setPriority(defaultPriority(request.getPriority()));
        item.setDisplayOrder(request.getDisplayOrder());
        item.setRemark(request.getRemark());
        return item;
    }

    /**
     * 将更新执行规则请求装配为领域更新命令；请求为 {@code null} 时返回 {@code null}。
     *
     * @param request 更新请求
     * @return 领域更新命令
     */
    @Override
    public UpdateExecutionRuleCommand toUpdateCommand(UpdateExecuteLocationRequest request) {
        if (request == null) {
            return null;
        }
        UpdateExecutionRuleCommand command = new UpdateExecutionRuleCommand();
        command.setId(request.getId());
        command.setActivityDefinitionId(request.getActivityDefinitionId());
        command.setActivityCategoryCode(request.getActivityCategoryCode());
        command.setExecutionOrgId(request.getExecutionOrgId());
        command.setExecutionLocationId(request.getExecutionLocationId());
        command.setStartTime(parseTime(request.getStartTime()));
        command.setEndTime(parseTime(request.getEndTime()));
        command.setPriority(defaultPriority(request.getPriority()));
        command.setDisplayOrder(request.getDisplayOrder());
        command.setRemark(request.getRemark());
        return command;
    }

    /**
     * 优先级缺省为 1（最优先），与取药科室口径一致。
     *
     * @param value 前端传入优先级，可为 {@code null}
     * @return 非 null 优先级
     */
    private Integer defaultPriority(Integer value) {
        if (value == null) {
            return 1;
        }
        return value;
    }

    /**
     * 将 {@code HH:mm:ss} 风格字符串解析为 {@link LocalTime}；空串返回 {@code null}。
     *
     * @param value 时间字面量
     * @return 解析结果；格式非法时由 {@link LocalTime#parse} 抛出运行时异常
     */
    @Override
    public LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalTime.parse(value);
    }
}

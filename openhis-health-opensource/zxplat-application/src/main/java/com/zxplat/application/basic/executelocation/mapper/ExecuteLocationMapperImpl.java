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
        dto.setOrganizationId(model.getOrganizationId());
        dto.setActivityDefinitionId(model.getActivityDefinitionId());
        dto.setActivityCategoryCode(model.getActivityCategoryCode());
        // 业务类型透出供配置页回显；空表示未配类型层
        dto.setActivityTypeCode(model.getActivityTypeCode());
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
        item.setActivityTypeCode(request.getActivityTypeCode());
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
        command.setOrganizationId(request.getOrganizationId());
        command.setActivityDefinitionId(request.getActivityDefinitionId());
        command.setActivityCategoryCode(request.getActivityCategoryCode());
        command.setActivityTypeCode(request.getActivityTypeCode());
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

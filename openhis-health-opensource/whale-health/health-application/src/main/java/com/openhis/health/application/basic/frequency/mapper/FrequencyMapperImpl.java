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

package com.openhis.health.application.basic.frequency.mapper;

import com.openhis.health.application.basic.frequency.dto.FrequencyDto;
import com.openhis.health.application.basic.frequency.request.CreateFrequencyRequest;
import com.openhis.health.application.basic.frequency.request.UpdateFrequencyRequest;
import com.openhis.health.domain.custom.frequency.command.CreateFrequencyCommand;
import com.openhis.health.domain.custom.frequency.command.ModifyFrequencyCommand;
import com.openhis.health.domain.custom.frequency.entity.Frequency;
import com.openhis.health.domain.custom.frequency.queries.model.FrequencyPageItemQueryModel;
import com.openhis.whale.core.application.exception.ValidationException;
import org.springframework.stereotype.Component;

/**
 * {@link FrequencyMapper} 默认实现：实体与分页行两路投影，写命令侧完成整型/布尔标志互转。
 */
@Component
public class FrequencyMapperImpl implements FrequencyMapper {

    /**
     * 将频次聚合根实体投影为前端频次 DTO；实体为 {@code null} 时返回 {@code null}。
     *
     * @param entity 领域频次实体
     * @return 频次展示对象
     */
    @Override
    public FrequencyDto toDto(Frequency entity) {
        if (entity == null) {
            return null;
        }
        FrequencyDto dto = new FrequencyDto();
        dto.setId(entity.getId());
        dto.setRateCode(entity.getRateCode());
        dto.setName(entity.getName());
        dto.setDayInterval(entity.getDayInterval());
        dto.setDayTimes(entity.getDayTimes());
        dto.setWeekCycleFlag(toIntFlag(entity.getWeekCycleFlag()));
        dto.setTotalExecutionCount(entity.getTotalExecutionCount());
        dto.setExecutionPeriod(entity.getExecutionPeriod());
        dto.setExecutionPeriodUnit(entity.getExecutionPeriodUnit());
        dto.setActiveFlag(toIntFlag(entity.getActiveFlag()));
        dto.setSortNo(entity.getSortNo());
        dto.setCycleType(entity.getCycleType());
        dto.setApplicableScenes(entity.getApplicableScenes());
        dto.setAllowCrossDay(entity.getAllowCrossDay());
        return dto;
    }

    /**
     * 将频次分页列表行读模型投影为 DTO；入参为 {@code null} 时返回 {@code null}（字段为列表所需子集）。
     *
     * @param model 分页行读模型
     * @return 频次列表行
     */
    @Override
    public FrequencyDto toDto(FrequencyPageItemQueryModel model) {
        if (model == null) {
            return null;
        }
        FrequencyDto dto = new FrequencyDto();
        dto.setId(model.getId());
        dto.setRateCode(model.getRateCode());
        dto.setName(model.getName());
        dto.setCycleType(model.getCycleType());
        dto.setDayTimes(model.getDayTimes());
        dto.setActiveFlag(toIntFlag(model.getActiveFlag()));
        dto.setSortNo(model.getSortNo());
        return dto;
    }

    /**
     * 将新增频次请求装配为领域创建命令；请求体为空时抛出校验异常。
     *
     * @param request 创建请求
     * @return 领域创建命令
     * @throws ValidationException 请求体为空时
     */
    @Override
    public CreateFrequencyCommand toCreateCommand(CreateFrequencyRequest request) {
        if (request == null) {
            throw new ValidationException("新增频次失败：请求体不能为空");
        }
        CreateFrequencyCommand command = new CreateFrequencyCommand();
        command.setRateCode(request.getRateCode());
        command.setName(request.getName());
        command.setCycleType(request.getCycleType());
        command.setDayInterval(request.getDayInterval());
        command.setDayTimes(request.getDayTimes());
        command.setWeekCycleFlag(toBooleanFlag(request.getWeekCycleFlag()));
        command.setTotalExecutionCount(request.getTotalExecutionCount());
        command.setExecutionPeriod(request.getExecutionPeriod());
        command.setExecutionPeriodUnit(request.getExecutionPeriodUnit());
        command.setApplicableScenes(request.getApplicableScenes());
        command.setAllowCrossDay(request.getAllowCrossDay());
        command.setSortNo(request.getSortNo());
        command.setOperatorId(0L);
        return command;
    }

    /**
     * 将编辑频次请求装配为领域修改命令；请求体为空时抛出校验异常。
     *
     * @param request 编辑请求
     * @return 领域修改命令
     * @throws ValidationException 请求体为空时
     */
    @Override
    public ModifyFrequencyCommand toModifyCommand(UpdateFrequencyRequest request) {
        if (request == null) {
            throw new ValidationException("更新频次失败：请求体不能为空");
        }
        ModifyFrequencyCommand command = new ModifyFrequencyCommand();
        command.setId(request.getId());
        command.setName(request.getName());
        command.setCycleType(request.getCycleType());
        command.setDayInterval(request.getDayInterval());
        command.setDayTimes(request.getDayTimes());
        command.setWeekCycleFlag(toBooleanFlag(request.getWeekCycleFlag()));
        command.setTotalExecutionCount(request.getTotalExecutionCount());
        command.setExecutionPeriod(request.getExecutionPeriod());
        command.setExecutionPeriodUnit(request.getExecutionPeriodUnit());
        command.setApplicableScenes(request.getApplicableScenes());
        command.setAllowCrossDay(request.getAllowCrossDay());
        command.setSortNo(request.getSortNo());
        command.setOperatorId(0L);
        return command;
    }

    /**
     * 领域布尔标志转前端整型：true→1，false→0，未知→null。
     *
     * @param value 领域布尔
     * @return 前端约定整型
     */
    private Integer toIntFlag(Boolean value) {
        if (Boolean.TRUE.equals(value)) {
            return 1;
        }
        if (Boolean.FALSE.equals(value)) {
            return 0;
        }
        return null;
    }

    /**
     * 前端整型标志转领域布尔：仅 1 视为 true，其余非 null 视为 false；null 保持 null。
     *
     * @param value 前端传入整型
     * @return 领域布尔或 null
     */
    private Boolean toBooleanFlag(Integer value) {
        if (value == null) {
            return null;
        }
        return value == 1;
    }
}

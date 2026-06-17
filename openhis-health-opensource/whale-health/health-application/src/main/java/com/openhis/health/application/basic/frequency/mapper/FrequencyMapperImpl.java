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

    @Override
    public FrequencyDto toDto(Frequency entity) {
        if (entity == null) {
            return null;
        }
        FrequencyDto dto = new FrequencyDto();
        dto.setId(entity.getId());
        dto.setRateCode(entity.getRateCode());
        dto.setName(entity.getName());
        dto.setCycleType(entity.getCycleType());
        dto.setDayCount(entity.getDayCount());
        dto.setDayInterval(entity.getDayInterval());
        dto.setDayTimes(entity.getDayTimes());
        dto.setWeekCycleFlag(toIntFlag(entity.getWeekCycleFlag()));
        dto.setTotalExecutionCount(entity.getTotalExecutionCount());
        dto.setExecutionPeriod(entity.getExecutionPeriod());
        dto.setExecutionPeriodUnit(entity.getExecutionPeriodUnit());
        dto.setApplicableScenes(entity.getApplicableScenes());
        dto.setAllowCrossDay(entity.getAllowCrossDay());
        dto.setActiveFlag(toIntFlag(entity.getActiveFlag()));
        dto.setSortNo(entity.getSortNo());
        return dto;
    }

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
        dto.setDayCount(model.getDayCount());
        dto.setDayInterval(model.getDayInterval());
        dto.setDayTimes(model.getDayTimes());
        dto.setWeekCycleFlag(toIntFlag(model.getWeekCycleFlag()));
        dto.setTotalExecutionCount(model.getTotalExecutionCount());
        dto.setExecutionPeriod(model.getExecutionPeriod());
        dto.setExecutionPeriodUnit(model.getExecutionPeriodUnit());
        dto.setApplicableScenes(model.getApplicableScenes());
        dto.setAllowCrossDay(model.getAllowCrossDay());
        dto.setActiveFlag(toIntFlag(model.getActiveFlag()));
        dto.setSortNo(model.getSortNo());
        return dto;
    }

    @Override
    public CreateFrequencyCommand toCreateCommand(CreateFrequencyRequest request, Long operatorId) {
        if (request == null) {
            throw new ValidationException("新增频次失败：请求体不能为空");
        }
        CreateFrequencyCommand command = new CreateFrequencyCommand();
        command.setRateCode(request.getRateCode());
        command.setName(request.getName());
        command.setCycleType(request.getCycleType());
        command.setDayCount(request.getDayCount());
        command.setDayInterval(request.getDayInterval());
        command.setDayTimes(request.getDayTimes());
        command.setWeekCycleFlag(toBooleanFlag(request.getWeekCycleFlag()));
        command.setTotalExecutionCount(request.getTotalExecutionCount());
        command.setExecutionPeriod(request.getExecutionPeriod());
        command.setExecutionPeriodUnit(request.getExecutionPeriodUnit());
        command.setApplicableScenes(request.getApplicableScenes());
        command.setAllowCrossDay(request.getAllowCrossDay());
        command.setSortNo(request.getSortNo());
        command.setOperatorId(operatorId);
        return command;
    }

    @Override
    public ModifyFrequencyCommand toModifyCommand(UpdateFrequencyRequest request, Long operatorId) {
        if (request == null) {
            throw new ValidationException("更新频次失败：请求体不能为空");
        }
        ModifyFrequencyCommand command = new ModifyFrequencyCommand();
        command.setId(request.getId());
        command.setName(request.getName());
        command.setCycleType(request.getCycleType());
        command.setDayCount(request.getDayCount());
        command.setDayInterval(request.getDayInterval());
        command.setDayTimes(request.getDayTimes());
        command.setWeekCycleFlag(toBooleanFlag(request.getWeekCycleFlag()));
        command.setTotalExecutionCount(request.getTotalExecutionCount());
        command.setExecutionPeriod(request.getExecutionPeriod());
        command.setExecutionPeriodUnit(request.getExecutionPeriodUnit());
        command.setApplicableScenes(request.getApplicableScenes());
        command.setAllowCrossDay(request.getAllowCrossDay());
        command.setSortNo(request.getSortNo());
        command.setOperatorId(operatorId);
        return command;
    }

    private Integer toIntFlag(Boolean value) {
        if (Boolean.TRUE.equals(value)) {
            return 1;
        }
        if (Boolean.FALSE.equals(value)) {
            return 0;
        }
        return null;
    }

    private Boolean toBooleanFlag(Integer value) {
        if (value == null) {
            return null;
        }
        return value == 1;
    }
}

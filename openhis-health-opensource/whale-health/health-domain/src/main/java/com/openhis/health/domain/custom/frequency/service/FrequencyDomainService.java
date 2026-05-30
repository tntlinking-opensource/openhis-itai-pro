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

package com.openhis.health.domain.custom.frequency.service;

import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.custom.frequency.command.*;
import com.openhis.health.domain.custom.frequency.entity.Frequency;
import com.openhis.health.domain.custom.frequency.repository.FrequencyRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.custom.frequency.*;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 频次写侧领域服务。
 */
@Service
@RequiredArgsConstructor
public class FrequencyDomainService {

    private final FrequencyRepository repository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 新增频次。
     *
     * @param command 新增命令
     * @return 频次主键
     * @throws BusinessException 编码冲突或格式校验失败时
     */
    public Long createFrequency(CreateFrequencyCommand command) {
        if (command.getRateCode() != null && repository.existsByRateCode(command.getRateCode())) {
            throw new BusinessException(ErrorCodeConstants.FREQUENCY_RATE_CODE_CONFLICT, "频次编码已存在");
        }
        Frequency frequency = new Frequency();
        frequency.setRateCode(command.getRateCode());
        frequency.setName(command.getName());
        frequency.setCycleType(command.getCycleType());
        frequency.setDayCount(command.getDayCount());
        frequency.setDayInterval(command.getDayInterval());
        frequency.setDayTimes(command.getDayTimes());
        frequency.setWeekCycleFlag(command.getWeekCycleFlag());
        frequency.setTotalExecutionCount(command.getTotalExecutionCount());
        frequency.setExecutionPeriod(command.getExecutionPeriod());
        frequency.setExecutionPeriodUnit(command.getExecutionPeriodUnit());
        frequency.setApplicableScenes(command.getApplicableScenes());
        frequency.setAllowCrossDay(command.getAllowCrossDay());
        frequency.setSortNo(command.getSortNo());
        frequency.initActive();

        frequency.assertRequiredFields();
        this.assertDayTimesFormat(frequency.getDayTimes());

        Frequency saved = repository.save(frequency);

        // Event：发布频次创建事件。
        domainEventPublisher.publish(new FrequencyCreatedEvent(
            saved.getId(),
            saved.getRateCode(),
            saved.getName(),
            saved.getCycleType()
        , command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 修改频次。
     *
     * @param command 修改命令
     * @throws BusinessException 频次不存在或格式校验失败时
     */
    public void modifyFrequency(ModifyFrequencyCommand command) {
        // Pre：加载目标频次，当前聚合未维护并发戳字段，暂不执行 concurrencyStamp 比对。
        Frequency frequency = this.requireFrequency(command.getId());

        // 业务分支处理：频次名称变更。
        if (command.getName() != null) {
            frequency.setName(command.getName());
        }
        // 业务分支处理：周期类型变更。
        if (command.getCycleType() != null) {
            frequency.setCycleType(command.getCycleType());
        }
        // 业务分支处理：天数变更。
        if (command.getDayCount() != null) {
            frequency.setDayCount(command.getDayCount());
        }
        // 业务分支处理：天间隔变更。
        if (command.getDayInterval() != null) {
            frequency.setDayInterval(command.getDayInterval());
        }
        // 业务分支处理：日时点串变更。
        if (command.getDayTimes() != null) {
            frequency.setDayTimes(command.getDayTimes());
        }
        // 业务分支处理：周循环标志变更。
        if (command.getWeekCycleFlag() != null) {
            frequency.setWeekCycleFlag(command.getWeekCycleFlag());
        }
        // 业务分支处理：总执行次数变更。
        if (command.getTotalExecutionCount() != null) {
            frequency.setTotalExecutionCount(command.getTotalExecutionCount());
        }
        // 业务分支处理：执行周期变更。
        if (command.getExecutionPeriod() != null) {
            frequency.setExecutionPeriod(command.getExecutionPeriod());
        }
        // 业务分支处理：执行周期单位变更。
        if (command.getExecutionPeriodUnit() != null) {
            frequency.setExecutionPeriodUnit(command.getExecutionPeriodUnit());
        }
        // 业务分支处理：适用场景变更。
        if (command.getApplicableScenes() != null) {
            frequency.setApplicableScenes(command.getApplicableScenes());
        }
        // 业务分支处理：允许跨天标志变更。
        if (command.getAllowCrossDay() != null) {
            frequency.setAllowCrossDay(command.getAllowCrossDay());
        }
        // 业务分支处理：排序号变更。
        if (command.getSortNo() != null) {
            frequency.setSortNo(command.getSortNo());
        }

        frequency.assertRequiredFields();
        this.assertDayTimesFormat(frequency.getDayTimes());

        repository.save(frequency);

        // Event：发布频次修改事件。
        domainEventPublisher.publish(new FrequencyModifiedEvent(
            frequency.getId(),
            CustomConstants.ChangedField.DEFAULT_CHANGED_FIELDS
        , command.getOperatorId()));
    }

    /**
     * 启用频次。
     *
     * @param command 启用命令
     */
    public void activateFrequency(ActivateFrequencyCommand command) {
        Frequency frequency = this.requireFrequency(command.getId());
        frequency.assertActivatable();
        frequency.setActiveFlag(Boolean.TRUE);
        repository.save(frequency);
        domainEventPublisher.publish(new FrequencyActivatedEvent(frequency.getId(), frequency.getRateCode(), command.getOperatorId()));
    }

    /**
     * 停用频次。
     *
     * @param command 停用命令
     */
    public void deactivateFrequency(DeactivateFrequencyCommand command) {
        Frequency frequency = this.requireFrequency(command.getId());
        frequency.assertDeactivatable();
        frequency.setActiveFlag(Boolean.FALSE);
        repository.save(frequency);
        domainEventPublisher.publish(new FrequencyDeactivatedEvent(
            frequency.getId(),
            frequency.getRateCode(),
            command.getReason()
        , command.getOperatorId()));
    }

    /**
     * 删除频次。
     *
     * @param command 删除命令
     */
    public void deleteFrequency(DeleteFrequencyCommand command) {
        Frequency frequency = this.requireFrequency(command.getId());
        frequency.setIsDeleted(Boolean.TRUE);
        repository.save(frequency);
        domainEventPublisher.publish(new FrequencyDeletedEvent(frequency.getId(), frequency.getRateCode(), command.getOperatorId()));
    }

    /**
     * 读取未删除频次。
     *
     * @param id 主键
     * @return 频次
     */
    private Frequency requireFrequency(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.FREQUENCY_NOT_FOUND, "频次不存在"));
    }

    /**
     * 校验日时点串格式。
     *
     * @param dayTimes 日时点串
     */
    private void assertDayTimesFormat(String dayTimes) {
        // 业务分支处理。
        if (dayTimes == null || dayTimes.isEmpty()) {
            return;
        }
        String[] segments = dayTimes.split(GeneralSharedConstants.COMMA);
        List<String> normalized = new ArrayList<>();
        for (String segment : segments) {
            String value = segment.trim();
            // 业务分支处理。
            if (!CustomConstants.DAY_TIME_PATTERN.matcher(value).matches()) {
                throw new BusinessException(ErrorCodeConstants.FREQUENCY_INVALID_ARGUMENT, "日执行时点格式不合法");
            }
            normalized.add(value);
        }
        // 业务分支处理。
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.FREQUENCY_INVALID_ARGUMENT, "日执行时点不能为空串");
        }
    }
}

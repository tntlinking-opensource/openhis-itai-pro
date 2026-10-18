/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.frequency.service;

import com.zxplat.domain.share.constants.CustomConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.custom.frequency.command.*;
import com.zxplat.domain.custom.frequency.entity.Frequency;
import com.zxplat.domain.custom.frequency.repository.FrequencyRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.events.custom.frequency.*;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 用药频次（{@code cus_frequency}）聚合写侧领域服务。
 *
 * <p>职责：频次主数据增改、启停与软删；日时点串格式校验（BR-FRQ）。</p>
 * <p>不变量：{@code rate_code} 租户内唯一；持久化先于频次领域事件发布。</p>
 * <p>仅注入本聚合 {@link FrequencyRepository}。</p>
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
        // 前置校验：编码冲突与日时点格式
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

        // 持久化
        Frequency saved = repository.save(frequency);

        // 发布事件：频次已创建
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

        // 命令传入频次名称时覆盖展示名称。
        if (command.getName() != null) {
            frequency.setName(command.getName());
        }
        // 命令传入周期类型时覆盖周期算法。
        if (command.getCycleType() != null) {
            frequency.setCycleType(command.getCycleType());
        }
        // 命令传入天数时覆盖周期天数。
        if (command.getDayCount() != null) {
            frequency.setDayCount(command.getDayCount());
        }
        // 命令传入天间隔时覆盖间隔参数。
        if (command.getDayInterval() != null) {
            frequency.setDayInterval(command.getDayInterval());
        }
        // 命令传入日时点串时覆盖执行时刻配置。
        if (command.getDayTimes() != null) {
            frequency.setDayTimes(command.getDayTimes());
        }
        // 命令传入周循环标志时覆盖周模式。
        if (command.getWeekCycleFlag() != null) {
            frequency.setWeekCycleFlag(command.getWeekCycleFlag());
        }
        // 命令传入总执行次数时覆盖疗程次数上限。
        if (command.getTotalExecutionCount() != null) {
            frequency.setTotalExecutionCount(command.getTotalExecutionCount());
        }
        // 命令传入执行周期时覆盖周期长度。
        if (command.getExecutionPeriod() != null) {
            frequency.setExecutionPeriod(command.getExecutionPeriod());
        }
        // 命令传入执行周期单位时覆盖周期计量单位。
        if (command.getExecutionPeriodUnit() != null) {
            frequency.setExecutionPeriodUnit(command.getExecutionPeriodUnit());
        }
        // 命令传入适用场景时覆盖使用范围。
        if (command.getApplicableScenes() != null) {
            frequency.setApplicableScenes(command.getApplicableScenes());
        }
        // 命令传入允许跨天标志时覆盖跨日执行策略。
        if (command.getAllowCrossDay() != null) {
            frequency.setAllowCrossDay(command.getAllowCrossDay());
        }
        // 命令传入排序号时覆盖列表展示顺序。
        if (command.getSortNo() != null) {
            frequency.setSortNo(command.getSortNo());
        }

        frequency.assertRequiredFields();
        this.assertDayTimesFormat(frequency.getDayTimes());

        // 持久化
        repository.save(frequency);

        // 发布事件：频次已修改
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
        // 日时点串为空时跳过格式校验（由必填校验在其他路径兜底）。
        if (dayTimes == null || dayTimes.isEmpty()) {
            return;
        }
        String[] segments = dayTimes.split(GeneralSharedConstants.COMMA);
        List<String> normalized = new ArrayList<>();
        for (String segment : segments) {
            String value = segment.trim();
            // 单段须符合 HH:mm 时点格式，否则拒绝保存。
            if (!CustomConstants.DAY_TIME_PATTERN.matcher(value).matches()) {
                throw new BusinessException(ErrorCodeConstants.FREQUENCY_INVALID_ARGUMENT, "日执行时点格式不合法");
            }
            normalized.add(value);
        }
        // 拆分后无有效时点则拒绝，防止空串绕过校验。
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.FREQUENCY_INVALID_ARGUMENT, "日执行时点不能为空串");
        }
    }
}

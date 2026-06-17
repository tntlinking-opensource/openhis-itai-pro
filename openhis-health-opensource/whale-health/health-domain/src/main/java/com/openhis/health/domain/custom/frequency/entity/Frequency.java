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

package com.openhis.health.domain.custom.frequency.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * 频次聚合根实体。
 */
@Getter
@Setter
@Entity
@Table(name = "cus_frequency")
public class Frequency extends BaseAuditedEntity {

    /**
     * 频次编码。
     */
    @Column(name = "rate_code")
    private String rateCode;

    /**
     * 频次名称。
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * 周期类型（{@link com.openhis.health.domain.share.enums.FrequencyCycleType#getCode()}）。
     */
    @Column(name = "cycle_type", nullable = false)
    private String cycleType;

    /**
     * 日计数。
     */
    @Column(name = "day_count")
    private Integer dayCount;

    /**
     * 日间隔。
     */
    @Column(name = "day_interval")
    private Integer dayInterval;

    /**
     * 日执行时点串。
     */
    @Column(name = "day_times")
    private String dayTimes;

    /**
     * 周周期标记。
     */
    @Column(name = "week_cycle_flag")
    private Boolean weekCycleFlag;

    /**
     * 总执行次数。
     */
    @Column(name = "total_execution_count")
    private Integer totalExecutionCount;

    /**
     * 执行周期。
     */
    @Column(name = "execution_period")
    private Integer executionPeriod;

    /**
     * 执行周期单位。
     */
    @Column(name = "execution_period_unit")
    private String executionPeriodUnit;

    /**
     * 适用场景。
     */
    @Column(name = "applicable_scenes")
    private String applicableScenes;

    /**
     * 允许跨日（{@link com.openhis.health.domain.share.enums.FrequencyAllowCrossDay#getCode()}）。
     */
    @Column(name = "allow_cross_day")
    private String allowCrossDay;

    /**
     * 启用标记。
     */
    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag;

    /**
     * 排序号。
     */
    @Column(name = "sort_no")
    private Integer sortNo;

    /**
     * 初始化启用状态。
     */
    public void initActive() {
        this.activeFlag = Boolean.TRUE;
    }

    /**
     * 校验创建/修改必填字段。
     */
    public void assertRequiredFields() {
        if (StringUtils.isBlank(this.name)) {
            throw new BusinessException(ErrorCodeConstants.FREQUENCY_INVALID_ARGUMENT, "频次名称不能为空");
        }
        if (StringUtils.isBlank(this.cycleType)) {
            throw new BusinessException(ErrorCodeConstants.FREQUENCY_INVALID_ARGUMENT, "周期类型不能为空");
        }
    }

    /**
     * 校验当前允许启用。
     */
    public void assertActivatable() {
        if (Boolean.TRUE.equals(this.activeFlag)) {
            throw new BusinessException(ErrorCodeConstants.FREQUENCY_INVALID_STATUS, "启用状态不可重复启用");
        }
    }

    /**
     * 校验当前允许停用。
     */
    public void assertDeactivatable() {
        if (!Boolean.TRUE.equals(this.activeFlag)) {
            throw new BusinessException(ErrorCodeConstants.FREQUENCY_INVALID_STATUS, "停用状态不可重复停用");
        }
    }
}

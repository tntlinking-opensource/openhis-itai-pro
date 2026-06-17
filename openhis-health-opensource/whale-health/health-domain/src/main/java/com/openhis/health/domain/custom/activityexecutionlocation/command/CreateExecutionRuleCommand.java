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

package com.openhis.health.domain.custom.activityexecutionlocation.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 新增执行科室规则命令。
 */
@Getter
@Setter
public class CreateExecutionRuleCommand {

    /**
     * 活动类别编码。
     */
    private String activityCategoryCode;

    /**
     * 执行科室标识。
     */
    private Long executionOrgId;

    /**
     * 新增规则明细（长度 1 表示单条，>1 表示批量）。
     */
    private List<CreateExecutionRuleItemCommand> rules;

    /**
     * 操作人标识。
     */
    private Long operatorId;

    /**
     * 操作人姓名。
     */
    private String operatorName;

    /**
     * 解析规则明细列表。
     *
     * <p>空列表将被解析为长度为 0 的集合，由领域服务做必填校验。</p>
     *
     * @return 规则明细列表
     */
    public List<CreateExecutionRuleItemCommand> resolveRules() {
        if (this.rules == null) {
            return new ArrayList<>();
        }
        return this.rules;
    }

    /**
     * 规则明细子命令。
     */
    @Getter
    @Setter
    public static class CreateExecutionRuleItemCommand {
        /**
         * 诊疗定义标识。
         */
        private Long activityDefinitionId;
        /**
         * 执行位置标识。
         */
        private Long executionLocationId;
        /**
         * 生效开始时间。
         */
        private LocalTime startTime;
        /**
         * 生效结束时间。
         */
        private LocalTime endTime;
        /**
         * 优先级。
         */
        private Integer priority;
        /**
         * 展示顺序。
         */
        private Integer displayOrder;
        /**
         * 备注。
         */
        private String remark;
    }
}

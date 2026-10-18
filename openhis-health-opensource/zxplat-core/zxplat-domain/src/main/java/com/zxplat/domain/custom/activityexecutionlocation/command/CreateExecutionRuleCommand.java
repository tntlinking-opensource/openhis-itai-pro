/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.activityexecutionlocation.command;

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
     * 开立科室标识。
     */
    private Long organizationId;

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
         * 诊疗定义标识；空表示未绑单品。
         */
        private Long activityDefinitionId;
        /**
         * 业务类型码（术语 {@code activity_type_code}）；可空。未绑单品且本字段有值时为业务类型层规则。
         */
        private String activityTypeCode;
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

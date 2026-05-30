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

package com.openhis.health.domain.share.constants;

import java.util.regex.Pattern;

public interface CustomConstants {
    /**
     * 日时点格式正则（HH:mm）。
     */
    Pattern DAY_TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    /**
     * 周期类型：日周期（每日）。
     */
    Integer CYCLE_TYPE_DAILY = 1;

    /**
     * 周期类型：隔日/间隔天（历史 openhis 存值 2；展开步进读 day_interval / day_count，不在计算侧写死）。
     */
    Integer CYCLE_TYPE_EVERY_OTHER_DAY = 2;

    /**
     * 周期类型：周周期。
     */
    Integer CYCLE_TYPE_WEEKLY = 3;

    /**
     * 周期类型：必要时。
     */
    Integer CYCLE_TYPE_AS_NEEDED = 4;

    /**
     * 执行周期单位：分钟（DM PeriodUnit.min）。
     */
    String PERIOD_UNIT_MINUTE = "min";

    /**
     * 执行周期单位：小时（DM PeriodUnit.h）。
     */
    String PERIOD_UNIT_HOUR = "h";

    /**
     * 执行周期单位：天（DM PeriodUnit.d）。
     */
    String PERIOD_UNIT_DAY = "d";

    /**
     * 执行周期单位：周（DM PeriodUnit.wk）。
     */
    String PERIOD_UNIT_WEEK = "wk";

    /**
     * 执行周期单位：月（DM PeriodUnit.mo）。
     */
    String PERIOD_UNIT_MONTH = "mo";

    /**
     * 自动计费规则事件操作类型常量。
     */
    public interface ChargeItemAutoGenOperation {
        String OP_CREATE = "OP_CREATE";
        String OP_UPDATE = "OP_UPDATE";
        String OP_DELETE = "OP_DELETE";
        String OP_STATUS_CHANGE = "OP_STATUS_CHANGE";
    }

    /**
     * 就诊里程碑类型名称常量。
     */
    public interface EncounterHistoryMilestoneName {
        String TRANSFER = "transfer";
        String DISCHARGE = "discharge";
        String ADMISSION_TO_DEPT = "admission_to_dept";
        String BED_TRANSFER = "bed_transfer";
        String BED_CARD_PRINT = "bed_card_print";
        String COMPANION_WRISTBAND_PRINT = "companion_wristband_print";
    }

    // 表名常量已集中到 TableNameConstants。

    /**
     * 规则操作类型常量。
     */
    public interface OperationType {
        String CREATE = "CREATE";
        String UPDATE = "UPDATE";
        String DELETE = "DELETE";
    }

    /**
     * 变更字段常量。
     */
    public interface ChangedField {
        String DEFAULT_CHANGED_FIELDS = "profile";
    }
}

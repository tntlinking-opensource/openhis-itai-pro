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
     * 周期类型：日周期（历史 openhis 存值 1；展开步进读 day_interval / day_count，不在计算侧写死）。
     * @deprecated 使用 {@link com.openhis.health.domain.share.enums.FrequencyCycleType} code 字符串
     */
    @Deprecated
    Integer CYCLE_TYPE_DAILY = 1;

    /**
     * @deprecated 使用 {@link com.openhis.health.domain.share.enums.FrequencyCycleType}
     */
    @Deprecated
    Integer CYCLE_TYPE_EVERY_OTHER_DAY = 2;

    /**
     * @deprecated 使用 {@link com.openhis.health.domain.share.enums.FrequencyCycleType}
     */
    @Deprecated
    Integer CYCLE_TYPE_WEEKLY = 3;

    /**
     * @deprecated 使用 {@link com.openhis.health.domain.share.enums.FrequencyCycleType}
     */
    @Deprecated
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

    /**
     * 诊疗耗材绑定列表/下拉展示文案（WH-BND001）。
     */
    public interface ServiceDeviceBindingDisplayLabel {
        /** 绑定目标表：诊疗活动。 */
        String TARGET_TABLE_ACTIVITY = "诊疗";
        /** 绑定目标表：耗材。 */
        String TARGET_TABLE_DEVICE = "耗材";
    }

    /**
     * 调价单项目口径常量（药品耗材批号调价 / 诊疗一对一调价共用主从表）。
     */
    public interface PriceAdjustment {

        /** 诊疗子表占位批号（无实物批号，与药品耗材批号行区分）。 */
        String CLINICAL_DETAIL_LOT_PLACEHOLDER = "-";

        /** 读模型展示：诊疗一对一调价。 */
        String ADJUSTMENT_STYLE_CLINICAL = "clinical";

        /** 读模型展示：药品耗材批号调价。 */
        String ADJUSTMENT_STYLE_LOGISTICS = "logistics";

        /** 在途预检原因：药品医嘱申请非终态。 */
        String IN_TRANSIT_REASON_MED_REQUEST = "MED_REQUEST_IN_TRANSIT";

        /** 在途预检原因：药品发放非终态。 */
        String IN_TRANSIT_REASON_MED_DISPENSE = "MED_DISPENSE_IN_TRANSIT";

        /** 在途预检原因：耗材申请非终态。 */
        String IN_TRANSIT_REASON_DEVICE_REQUEST = "DEVICE_REQUEST_IN_TRANSIT";

        /** 在途预检原因：耗材发放非终态。 */
        String IN_TRANSIT_REASON_DEVICE_DISPENSE = "DEVICE_DISPENSE_IN_TRANSIT";

        /** 租户选项缺失时默认医嘱定价来源（批号价，IF-PRC-01 非阻断）。 */
        String ORDER_PRICING_SOURCE_DEFAULT = "LOT_NUMBER";
    }
}

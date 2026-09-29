/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

import java.util.regex.Pattern;


/**
 * 跨领域通用常量：频次周期、执行周期单位、自动计费规则事件类型等（历史兼容与术语迁移锚点）。
 */
public interface CustomConstants {
    /**
     * 日时点格式正则（HH:mm）。
     */
    Pattern DAY_TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    /**
     * 周期类型：日周期（CustomConstants，值 {@code 1}）；历史 openhis 存值，展开步进读 day_interval / day_count。
     *
     * @deprecated 使用 {@link com.openhis.health.domain.share.enums.FrequencyCycleType} code 字符串
     */
    @Deprecated
    Integer CYCLE_TYPE_DAILY = 1;

    /**
     * 周期类型：隔日周期（CustomConstants，值 {@code 2}）。
     */
    Integer CYCLE_TYPE_EVERY_OTHER_DAY = 2;

    /**
     * 周期类型：周周期（CustomConstants，值 {@code 3}）。
     */
    Integer CYCLE_TYPE_WEEKLY = 3;

    /**
     * 周期类型：按需（CustomConstants，值 {@code 4}）。
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
        /** 自动计费规则事件：新建（CustomConstants，值 {@code OP_CREATE}）。 */
        String OP_CREATE = "OP_CREATE";
        /** 自动计费规则事件：更新（CustomConstants，值 {@code OP_UPDATE}）。 */
        String OP_UPDATE = "OP_UPDATE";
        /** 自动计费规则事件：删除（CustomConstants，值 {@code OP_DELETE}）。 */
        String OP_DELETE = "OP_DELETE";
        /** 自动计费规则事件：状态变更（CustomConstants，值 {@code OP_STATUS_CHANGE}）。 */
        String OP_STATUS_CHANGE = "OP_STATUS_CHANGE";
    }

    /**
     * 就诊里程碑类型名称常量。
     */
    public interface EncounterHistoryMilestoneName {
        /** 就诊里程碑：转科（CustomConstants，值 {@code transfer}）。 */
        String TRANSFER = "transfer";
        /** 就诊里程碑：出院（CustomConstants，值 {@code discharge}）。 */
        String DISCHARGE = "discharge";
        /** 就诊里程碑：入科（CustomConstants，值 {@code admission_to_dept}）。 */
        String ADMISSION_TO_DEPT = "admission_to_dept";
        /** 就诊里程碑：转床（CustomConstants，值 {@code bed_transfer}）。 */
        String BED_TRANSFER = "bed_transfer";
        /** 就诊里程碑：床头卡打印（CustomConstants，值 {@code bed_card_print}）。 */
        String BED_CARD_PRINT = "bed_card_print";
    }

    // 表名常量已集中到 TableNameConstants。

    /**
     * 规则操作类型常量。
     */
    public interface OperationType {
        /** 规则操作：新建（CustomConstants，值 {@code CREATE}）。 */
        String CREATE = "CREATE";
        /** 规则操作：更新（CustomConstants，值 {@code UPDATE}）。 */
        String UPDATE = "UPDATE";
        /** 规则操作：删除（CustomConstants，值 {@code DELETE}）。 */
        String DELETE = "DELETE";
    }

    /**
     * 变更字段常量。
     */
    public interface ChangedField {
        /** 默认变更字段：档案信息（CustomConstants，值 {@code profile}）。 */
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

        /**
         * 未签发草稿新鲜窗口（小时）：开立时刻落在此窗口内的 DRAFT 仅提示开立医生自行删除，不自动废弃。
         * <p>取 48：覆盖隔日门诊/交班后再处理草稿；过短易误伤仍在用草稿。</p>
         */
        int UNSIGNED_DRAFT_FRESH_HOURS = 48;

        /**
         * 在途预检原因：新鲜窗口内仍存在未签发 DRAFT（须医生自行删除，不可确认绕过）。
         */
        String IN_TRANSIT_REASON_FRESH_UNSIGNED_DRAFT = "FRESH_UNSIGNED_ORDER_DRAFT";
    }
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 住院患者药品使用应用编排常量（AS-IP-PHA-005 §1.3 / §5）。
 *
 * <p>含展示模式、展示发药状态三态与业务异常码；不重复通用日期格式（见 {@link GeneralSharedConstants}）。</p>
 */
public interface InpatientPharmacyPatientMedicationUsageConstants {

    /** 展示模式：明细。 */
    String PRESENTATION_MODE_DETAIL = "DETAIL";

    /** 展示模式：汇总。 */
    String PRESENTATION_MODE_SUMMARY = "SUMMARY";

    /** 展示发药状态：待发。 */
    String USAGE_STATUS_PENDING = "PENDING";

    /** 展示发药状态：已发。 */
    String USAGE_STATUS_DISPENSED = "DISPENSED";

    /** 展示发药状态：已退。 */
    String USAGE_STATUS_RETURNED = "RETURNED";

    /** 展示发药状态文案：待发。 */
    String USAGE_STATUS_PENDING_DISPLAY = "待发";

    /** 展示发药状态文案：已发。 */
    String USAGE_STATUS_DISPENSED_DISPLAY = "已发";

    /** 展示发药状态文案：已退。 */
    String USAGE_STATUS_RETURNED_DISPLAY = "已退";

    /** 展示模式文案：明细。 */
    String PRESENTATION_MODE_DETAIL_DISPLAY = "明细";

    /** 展示模式文案：汇总。 */
    String PRESENTATION_MODE_SUMMARY_DISPLAY = "汇总";

    /** 页面初始化或患者检索失败（EX-IP-PMU-101）。 */
    String EX_IP_PMU_101 = "EX-IP-PMU-101";

    /** 就诊不可用 / 非住院 / 与 patientId 不一致（EX-IP-PMU-102）。 */
    String EX_IP_PMU_102 = "EX-IP-PMU-102";

    /** 药品使用数据加载失败（EX-IP-PMU-103）。 */
    String EX_IP_PMU_103 = "EX-IP-PMU-103";

    /** presentationMode 非法（EX-IP-PMU-201）。 */
    String EX_IP_PMU_201 = "EX-IP-PMU-201";

    /** 日期区间非法（EX-IP-PMU-202）。 */
    String EX_IP_PMU_202 = "EX-IP-PMU-202";
}

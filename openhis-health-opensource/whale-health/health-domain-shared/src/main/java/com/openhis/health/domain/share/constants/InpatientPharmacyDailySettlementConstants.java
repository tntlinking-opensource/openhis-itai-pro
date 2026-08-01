/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 住院药房日结交账应用编排消息码（对齐 AS-IP-PHA-006 §5）。
 */
public interface InpatientPharmacyDailySettlementConstants {

    /** 页面初始化失败（EX-IP-PDC-101）。 */
    String EX_IP_PDC_101 = "EX-IP-PDC-101";

    /** 时间窗或药房参数非法（EX-IP-PDC-102）。 */
    String EX_IP_PDC_102 = "EX-IP-PDC-102";

    /** 查询失败 / dispense_time 空（EX-IP-PDC-103）。 */
    String EX_IP_PDC_103 = "EX-IP-PDC-103";

    /** 勾选发药行非法（EX-IP-PDC-105）。 */
    String EX_IP_PDC_105 = "EX-IP-PDC-105";

    /** 漏交硬栏（EX-IP-PDC-202）。 */
    String EX_IP_PDC_202 = "EX-IP-PDC-202";

    /** 撤回权限或状态不允许（EX-IP-PDC-401）。 */
    String EX_IP_PDC_401 = "EX-IP-PDC-401";

    /** 无可交账发药明细（EX-IP-PDC-201）。 */
    String EX_IP_PDC_201 = "EX-IP-PDC-201";

    /** 并发挂载冲突（EX-IP-PDC-301）。 */
    String EX_IP_PDC_301 = "EX-IP-PDC-301";

    /** 发药人下拉分页上限。 */
    int PRACTITIONER_OPTION_PAGE_SIZE = 500;
}

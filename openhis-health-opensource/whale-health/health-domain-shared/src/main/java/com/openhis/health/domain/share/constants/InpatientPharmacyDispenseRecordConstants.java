/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 住院发退药记录应用编排消息码与展示常量（AS-IP-PHA-004 §5）。
 */
public interface InpatientPharmacyDispenseRecordConstants {

    /** 页面初始化失败（EX-IPR-101）。 */
    String EX_IPR_101 = "EX-IPR-101";

    /** 发药时间窗非法（EX-IPR-102）。 */
    String EX_IPR_102 = "EX-IPR-102";

    /** 查询失败（EX-IPR-103）。 */
    String EX_IPR_103 = "EX-IPR-103";

    /** 导出失败（EX-IPR-104）。 */
    String EX_IPR_104 = "EX-IPR-104";
}

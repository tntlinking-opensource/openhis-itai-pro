/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 门诊发退药记录应用编排消息码与展示常量（AS-OP-PHA-004 §5）。
 */
public interface OutpatientPharmacyDispenseRecordConstants {

    /** 页面初始化失败（EX-OPR-101）。 */
    String EX_OPR_101 = "EX-OPR-101";

    /** 发药时间窗非法（EX-OPR-102）。 */
    String EX_OPR_102 = "EX-OPR-102";

    /** 查询失败（EX-OPR-103）。 */
    String EX_OPR_103 = "EX-OPR-103";

    /** 导出失败（EX-OPR-104）。 */
    String EX_OPR_104 = "EX-OPR-104";
}

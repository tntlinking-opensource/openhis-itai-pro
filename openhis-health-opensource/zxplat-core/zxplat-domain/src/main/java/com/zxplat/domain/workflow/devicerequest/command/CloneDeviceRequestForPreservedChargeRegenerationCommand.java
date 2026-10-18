/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicerequest.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 半退保留链：按源耗材请求事实在本聚合内正向克隆新请求（DM-WKF-003 §6.1.11），供应用编排与 ChargeItem 新锚点对齐。
 */
@Getter
@Setter
public class CloneDeviceRequestForPreservedChargeRegenerationCommand {

    /** 源耗材请求主键。 */
    private Long sourceDeviceRequestId;

    /** 操作人主键。 */
    private Long operatorId;
}

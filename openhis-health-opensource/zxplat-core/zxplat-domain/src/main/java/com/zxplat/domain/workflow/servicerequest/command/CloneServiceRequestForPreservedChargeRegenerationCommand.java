/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.servicerequest.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 半退保留链：按源服务申请事实在本聚合内正向克隆新申请（DM-WKF-004 §6.1.17），供应用编排与 ChargeItem 新锚点对齐。
 */
@Getter
@Setter
public class CloneServiceRequestForPreservedChargeRegenerationCommand {

    /** 源服务申请主键。 */
    private Long sourceServiceRequestId;

    /** 操作人主键。 */
    private Long operatorId;
}

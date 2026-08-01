/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.lab.pathb;

import lombok.Getter;
import lombok.Setter;

/**
 * yunlis {@code crb_lab_outbox} 待消费行。
 */
@Getter
@Setter
public class YunlisOutboxRow {

    /** Outbox 主键。 */
    private Long id;

    /** 检验申请/报告头主键 {@code L_LABORDER_ID}。 */
    private Long labOrderId;
}

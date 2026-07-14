/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.support;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 已结算单查阅读查询（IF-IP-STL-07）。
 */
@Data
@Accessors(chain = true)
public class SettledInpatientPaymentViewQuery {

    /** 住院就诊主键。 */
    private Long encounterId;

    /** 患者主键；须与就诊归属一致（Gate）。 */
    private Long patientId;

    /** 指定查看的历史 payment 主键；空时取最近一笔 SUCCESS/REFUNDED 单。 */
    private Long paymentId;

    /** 费用呈现模式：detail / summary；空时默认 detail。 */
    private String presentationMode;
}

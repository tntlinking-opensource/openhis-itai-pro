/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.querysupport.financial;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 已结算单查阅读查询（IF-IP-STL-07）。
 */
@Data
@Accessors(chain = true)
public class SettledInpatientPaymentViewQuery {

    private Long encounterId;
    private Long patientId;
    private Long paymentId;
    private String presentationMode;
}

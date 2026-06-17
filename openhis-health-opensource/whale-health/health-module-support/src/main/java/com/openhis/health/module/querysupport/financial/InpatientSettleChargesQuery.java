/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.querysupport.financial;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 住院结算费用读查询（IF-IP-STL-04）。
 */
@Data
@Accessors(chain = true)
public class InpatientSettleChargesQuery {

    private Long encounterId;
    private Long patientId;
    private String presentationMode;
    private LocalDate chargeDateFrom;
    private LocalDate chargeDateTo;
}

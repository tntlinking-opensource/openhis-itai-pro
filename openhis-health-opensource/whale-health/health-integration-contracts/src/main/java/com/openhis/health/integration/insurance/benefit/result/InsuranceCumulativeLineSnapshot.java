/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.benefit.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 人员累计信息行快照。 */
@Data
@Accessors(chain = true)
public class InsuranceCumulativeLineSnapshot {

    private String insutype;
    private String year;
    private String cumYm;
    private String cumTypeCode;
    private BigDecimal cum;
}

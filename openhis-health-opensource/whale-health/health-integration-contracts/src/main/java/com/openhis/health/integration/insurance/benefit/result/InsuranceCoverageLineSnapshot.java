/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.benefit.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 参保险种行快照。 */
@Data
@Accessors(chain = true)
public class InsuranceCoverageLineSnapshot {

    private String insuplcAdmdvs;
    private String insutype;
    private String psnType;
    private String psnInsuStas;
    private String psnInsuDate;
    private String pausInsuDate;
    private String cvlservFlag;
    private String empName;
    private BigDecimal balc;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.registration.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 1101 参保信息行（挂号待遇确认展示）。 */
@Data
@Accessors(chain = true)
public class YbInsuInfoPreviewDto {

    private String insuplcAdmdvs;
    private String insutype;
    private String psnType;
    private String psnInsuStas;
    private String psnInsuDate;
    private String cvlservFlag;
    private String empName;
    private BigDecimal balc;
}

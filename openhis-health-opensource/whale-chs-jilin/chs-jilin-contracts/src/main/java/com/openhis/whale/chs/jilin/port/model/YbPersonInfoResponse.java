/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 1101 读卡结果。 */
@Data
@Accessors(chain = true)
public class YbPersonInfoResponse {

    private String psnNo;
    private String insuplcAdmdvs;
    private String insutype;
    private BigDecimal balc;
    private String psnName;
    private String certno;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inpatient.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 住院医保费用明细行（2301）。 */
@Data
@Accessors(chain = true)
public class IpSettleChargeLine {

    private Long chargeItemId;
    private String feedetlSn;
    private String medListCodg;
    private String medinsListCodg;
    private BigDecimal detItemFeeSumamt;
    private BigDecimal cnt;
    private BigDecimal pric;
    private LocalDateTime feeOcurTime;
    private String drordNo;
    private String bilgDeptCodg;
    private String bilgDeptName;
    private String bilgDrCodg;
    private String bilgDrName;
}

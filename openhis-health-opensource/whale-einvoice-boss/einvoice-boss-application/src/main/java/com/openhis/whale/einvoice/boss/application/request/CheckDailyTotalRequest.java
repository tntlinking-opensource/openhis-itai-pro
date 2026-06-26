package com.openhis.whale.einvoice.boss.application.request;

import lombok.Data;

import java.time.LocalDate;

/**
 * 财务日结核对请求。
 */
@Data
public class CheckDailyTotalRequest {

    /** 核对日期。 */
    private LocalDate checkDate;

    /** BUS_DATE 或 IVC_DATE。 */
    private String dateMode = "BUS_DATE";

    /** 业务线过滤 REG/AMB/IMP（可空）。 */
    private String issueKind;

    private Integer pageNo = 1;
    private Integer pageSize = 200;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.dto.request;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class QueryPlatformInvRequest {

    private String contractBusNo;
    private Long contractId;
    private String medinsListCodg;
    private String fixmedinsBchno;
    private String begndate;
    private String enddate;
    private String medListCodg;
    private String medinsProdSelNo;
    private String medinsProdPurcNo;
    private String medinsProdInvNo;
    private String psnNo;
    private String certno;
    private String mdtrtId;
    private String drugTracCodg;
    private String valiFlag;
}

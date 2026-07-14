/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.queries;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【3508–3513】平台进销存查询入参 */
@Data
@Accessors(chain = true)
public class QueryPlatformInvQuery {

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

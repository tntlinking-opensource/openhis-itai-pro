/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 1101 读卡请求。 */
@Data
@Accessors(chain = true)
public class YbPersonInfoRequest {

    private Long contractId;
    private String mdtrtCertType;
    private String mdtrtCertNo;
    private String insuplcAdmdvs;
}

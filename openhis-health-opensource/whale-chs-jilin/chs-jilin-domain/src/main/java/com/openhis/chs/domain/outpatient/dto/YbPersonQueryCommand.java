/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.outpatient.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 1101 人员信息查询命令。 */
@Data
@Accessors(chain = true)
public class YbPersonQueryCommand {

    private Long contractId;
    private String contractBusNo;
    private String mdtrtCertType;
    private String mdtrtCertNo;
    /** 卡识别码（1101 card_sn，就诊凭证类型 03 时必填）。 */
    private String cardSn;
    /** 证件号码（1101 certno）。 */
    private String certno;
    /** 人员证件类型（1101 psn_cert_type，缺省 01）。 */
    private String psnCertType;
    /** 人员姓名（1101 psn_name，电子凭证 01 时吉林平台必填）。 */
    private String psnName;
    private String insuplcAdmdvs;
    private Long operatorId;
    private Long tenantId;
}

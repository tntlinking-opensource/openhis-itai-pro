/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.benefit.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

/** 第三方参保人信息查询命令（平台 1101 语义的中性封装）。 */
@Data
@Accessors(chain = true)
public class InsurancePersonQueryCommand {

    /** 费用性质 {@code fin_contract.id}。 */
    private Long contractId;
    /** 就诊凭证类型（平台 mdtrt_cert_type）。 */
    @JsonProperty("mdtrtCertType")
    private String encounterCredentialType;
    /** 就诊凭证号码。 */
    @JsonProperty("mdtrtCertNo")
    private String encounterCredentialNo;
    /** 卡识别码（1101 card_sn，社保卡读卡）。 */
    @JsonProperty("cardSn")
    private String cardSerialNo;
    /** 证件号码（1101 certno，社保卡读卡）。 */
    @JsonProperty("certno")
    private String certificateNo;
    /** 人员证件类型（1101 psn_cert_type，缺省 01）。 */
    @JsonProperty("psnCertType")
    private String personCertificateType;
    /** 人员姓名（1101 psn_name，电子凭证 01 时必填）。 */
    @JsonProperty("psnName")
    private String personName;
    /** 参保地医保区划；可选，用于险种优选。 */
    @JsonProperty("insuplcAdmdvs")
    private String insurancePoolAreaCode;
}

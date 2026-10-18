/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.foundation.person.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

/** 1101 人员信息查询入参 */
@Data
@Accessors(chain = true)
public class YbPersonInfoRequest {

    /** 费用性质/契约主键 */
    private Long contractId;
    /** 就诊凭证类型 */
    @JsonProperty("mdtrtCertType")
    private String mdtrtCertType;
    /** 就诊凭证编号 */
    @JsonProperty("mdtrtCertNo")
    private String mdtrtCertNo;
    /** 卡识别码 */
    @JsonProperty("cardSn")
    private String cardSn;
    /** 证件号码 */
    @JsonProperty("certno")
    private String certno;
    /** 人员证件类型 */
    @JsonProperty("psnCertType")
    private String psnCertType;
    /** 人员姓名 */
    @JsonProperty("psnName")
    private String psnName;
    /** 参保地医保区划 */
    @JsonProperty("insuplcAdmdvs")
    private String insuplcAdmdvs;
}

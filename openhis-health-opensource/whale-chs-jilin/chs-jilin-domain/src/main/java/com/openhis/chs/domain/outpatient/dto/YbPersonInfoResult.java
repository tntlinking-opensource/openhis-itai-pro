/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.outpatient.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 1101 人员信息结果。 */
@Data
@Accessors(chain = true)
public class YbPersonInfoResult {

    /** 人员编号 psn_no。 */
    private String psnNo;

    /** 参保地医保区划。 */
    private String insuplcAdmdvs;

    /** 险种类型。 */
    private String insutype;

    /** 个人账户余额。 */
    private BigDecimal balc;

    /** 人员姓名。 */
    private String psnName;

    /** 证件号码。 */
    private String certno;

    /** 性别。 */
    private String gend;

    /** 出生日期。 */
    private String brdy;

    /** 参保信息列表。 */
    private List<YbInsuInfoRow> insuinfoList = new ArrayList<>();

    /** 身份信息列表。 */
    private List<YbIdetInfoRow> idetinfoList = new ArrayList<>();
}

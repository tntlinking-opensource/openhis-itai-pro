/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.outpatient.result;

import com.zxplat.plugin.chs.domain.outpatient.command.YbIdetInfoRow;
import com.zxplat.plugin.chs.domain.outpatient.command.YbInsuInfoRow;
import lombok.Data;

import lombok.experimental.Accessors;



import java.math.BigDecimal;

import java.util.ArrayList;

import java.util.List;



/** 1101 人员信息结果 */
@Data

@Accessors(chain = true)

public class YbPersonInfoResult {



    /** 人员编号 psn_no */

    private String psnNo;



    /** 参保地医保区划 */

    private String insuplcAdmdvs;



    /** 险种类型 */

    private String insutype;



    /** 个人账户余额 */

    private BigDecimal balc;



    /** 人员姓名 */

    private String psnName;



    /** 证件号码 */

    private String certno;



    /** 性别 */

    private String gend;



    /** 出生日期 */

    private String brdy;



    /** 参保信息列表 */

    private List<YbInsuInfoRow> insuinfoList = new ArrayList<>();



    /** 身份信息列表 */

    private List<YbIdetInfoRow> idetinfoList = new ArrayList<>();

    /**
     * 1101 异地失败切省通道重试成功后的实际合同 bus_no；未切换时为 null。
     */
    private String resolvedContractBusNo;

    /**
     * 1101 异地失败切省通道重试成功后的实际费用性质 id；未切换或未解析到时为 null。
     */
    private Long resolvedContractId;

}

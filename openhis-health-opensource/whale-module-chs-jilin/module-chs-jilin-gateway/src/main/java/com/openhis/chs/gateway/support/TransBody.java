/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.support;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Builder;
import lombok.Data;

/**
 * 医保交易报文（请求体）。
 *
 * @author Whale CHS */
@Data
@Builder
public class TransBody {

    @JSONField(name = "infno")
    private String infno;
    @JSONField(name = "msgid")
    private String msgid;
    @JSONField(name = "mdtrtarea_admvs")
    private String mdtrtareaAdmvs;
    @JSONField(name = "insuplc_admdvs")
    private String insuplcAdmdvs;
    @JSONField(name = "recer_sys_code")
    private String recerSysCode;
    @JSONField(name = "dev_no")
    private String devNo;
    @JSONField(name = "dev_safe_info")
    private String devSafeInfo;
    @JSONField(name = "cainfo")
    private String cainfo;
    @JSONField(name = "signtype")
    private String signtype;
    @JSONField(name = "infver")
    private String infver;
    @JSONField(name = "opter_type")
    private String opterType;
    @JSONField(name = "opter")
    private String opter;
    @JSONField(name = "opter_name")
    private String opterName;
    @JSONField(name = "inf_time")
    private String infTime;
    @JSONField(name = "fixmedins_code")
    private String fixmedinsCode;
    @JSONField(name = "fixmedins_name")
    private String fixmedinsName;
    @JSONField(name = "sign_no")
    private String signNo;
    @JSONField(name = "enc_type")
    private String encType;
    @JSONField(name = "input")
    private Object input;
}

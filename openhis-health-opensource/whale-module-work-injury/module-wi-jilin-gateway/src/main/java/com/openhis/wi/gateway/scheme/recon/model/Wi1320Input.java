/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.recon.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 【1320】工伤总额对账入参（V2.5 §5.3.2.1）。
 */
@Data
@Accessors(chain = true)
public class Wi1320Input {

    /** 对账开始日期 yyyyMMdd。 */
    @JSONField(name = "stmt_begindate")
    private String stmtBegindate;

    /** 对账结束日期 yyyyMMdd。 */
    @JSONField(name = "stmt_enddate")
    private String stmtEnddate;

    /** 是否包含退费记录（字典 1是/0否，默认 1）。 */
    @JSONField(name = "refd_setl_flag")
    private String refdSetlFlag;

    /** 医院经办人（与报文头 opter 一致时可传）。 */
    @JSONField(name = "opter")
    private String opter;

    /** 统筹区编码（本市对账时有效；院点默认 220199）。 */
    @JSONField(name = "social_pool_code")
    private String socialPoolCode;

    /** 省内异地标志（字典 1是/0否，默认 0）。 */
    @JSONField(name = "snydSign")
    private String snydSign;

    /** 全国异地标志（字典 1是/0否，默认 0）。 */
    @JSONField(name = "qgydSign")
    private String qgydSign;
}

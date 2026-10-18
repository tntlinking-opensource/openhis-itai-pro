/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.outpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 1101 人员信息查询命令 */
@Data
@Accessors(chain = true)
public class YbPersonQueryCommand {

    private Long contractId;
    private String contractBusNo;
    private String mdtrtCertType;
    private String mdtrtCertNo;
    /** 卡识别码）101 card_sn，就诊凭证类03 时必填） */
    private String cardSn;
    /** 证件号码）101 certno） */
    private String certno;
    /** 人员证件类型）101 psn_cert_type，缺01） */
    private String psnCertType;
    /** 人员姓名）101 psn_name，电子凭01 时吉林平台必填） */
    private String psnName;
    private String insuplcAdmdvs;
    private Long operatorId;
    private Long tenantId;

    /**
     * 为 true 时跳过当日 {@code yb_person_cache}，强制出站 1101 并回写缓存。
     * <p>用于读卡收银等需最新个账余额（{@code balc}）的场景。</p>
     */
    private boolean forceRefresh;
}

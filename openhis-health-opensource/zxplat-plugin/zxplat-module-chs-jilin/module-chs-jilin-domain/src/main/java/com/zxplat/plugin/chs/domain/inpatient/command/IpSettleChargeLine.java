/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 住院医保费用明细行（2301） */
@Data
@Accessors(chain = true)
public class IpSettleChargeLine {

    private Long chargeItemId;
    private String feedetlSn;
    /**
     * 原费用流水号（2301 {@code init_feedetl_sn}）；
     * 仅金额为负的冲销行填写，指向被退正数行流水。
     * 反结再生正数行虽有 {@code refund_id} 也不填（源行已 2302 撤销）。
     */
    private String initFeedetlSn;
    private String medListCodg;
    private String medinsListCodg;
    /**
     * 医院目录/项目名称（3101 {@code hilist_name}/{@code hosplist_name}；2301 可忽略）。
     */
    private String itemName;
    /**
     * 目录类别中文（药品 / 耗材 / 诊疗）。
     * <p>仅用于 2301 失败提示，不上报平台。</p>
     */
    private String catalogKind;
    private BigDecimal detItemFeeSumamt;
    private BigDecimal cnt;
    private BigDecimal pric;
    private LocalDateTime feeOcurTime;
    private String drordNo;
    private String bilgDeptCodg;
    private String bilgDeptName;
    private String bilgDrCodg;
    private String bilgDrName;
    /**
     * 中药使用方式（2301 {@code tcmdrug_used_way}）：复方 {@code 1} / 单方 {@code 2}。
     * <p>仅中草药行有值；由同处方中草药味数判定（大于 5 复方，否则单方）。</p>
     */
    private String tcmdrugUsedWay;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.audit.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊明细审核门禁命令（签名/预结算前）。
 */
@Data
@Accessors(chain = true)
public class DetailAuditOpGateCommand {

    /** 契约主键 */
    private Long contractId;
    /** 契约 bus_no */
    private String contractBusNo;
    /** HIS 就诊 ID */
    private Long encounterId;
    /** 本批收费项 ID */
    private List<Long> chargeItemIds;
    /** 触发场景（2/7 等） */
    private String trigScen;
    /** 系统代码（可空回落配置） */
    private String syscode;
    /** 机构类型 */
    private String medinsType;
    /** 机构等级 */
    private String medinsLv;
    /** 出生日期 yyyy-MM-dd */
    private String brdy;
    /** 人员编号（读卡后传入，利于 2201） */
    private String psnNo;
    /** 参保地 */
    private String insuplcAdmdvs;
    /** 险种 */
    private String insutype;
    /** 就诊凭证类型 */
    private String mdtrtCertType;
    /** 就诊凭证号 */
    private String mdtrtCertNo;
}

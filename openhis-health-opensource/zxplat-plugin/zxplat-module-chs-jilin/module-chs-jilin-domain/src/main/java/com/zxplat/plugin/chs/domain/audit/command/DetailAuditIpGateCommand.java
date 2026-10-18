/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.audit.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院明细审核门禁命令（预结算前 trig=9）。
 */
@Data
@Accessors(chain = true)
public class DetailAuditIpGateCommand {

    /** 契约主键 */
    private Long contractId;
    /** 契约 bus_no */
    private String contractBusNo;
    /** HIS 就诊 ID */
    private Long encounterId;
    /** 本批收费项 ID */
    private List<Long> chargeItemIds;
    /** 结算模式（出院全结/中途） */
    private String settleMode;
    /** 触发场景（默认 9） */
    private String trigScen;
    /** 系统代码 */
    private String syscode;
    /** 机构类型 */
    private String medinsType;
    /** 机构等级 */
    private String medinsLv;
    /** 人员编号 */
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

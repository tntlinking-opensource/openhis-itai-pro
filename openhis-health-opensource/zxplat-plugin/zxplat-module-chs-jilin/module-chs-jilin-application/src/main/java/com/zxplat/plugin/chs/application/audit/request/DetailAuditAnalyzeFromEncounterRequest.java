/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.audit.request;

import lombok.Data;

import java.util.List;

/**
 * 试调：从门诊就诊自动组装并调用 3101。
 */
@Data
public class DetailAuditAnalyzeFromEncounterRequest {

    /** 契约 bus_no */
    private String contractBusNo;
    /** 契约主键（与 bus_no 二选一） */
    private Long contractId;
    /** HIS 就诊 ID */
    private Long encounterId;
    /** 本批收费项 ID；可空时由 ContextLoader 按就诊策略装载 */
    private List<Long> chargeItemIds;
    /** 医保就诊 ID（须已平台挂号/登记） */
    private String mdtrtId;
    /** 触发场景（trig_scen，1–10） */
    private String trigScen;
    /** 系统代码；空则回落 zxplat.chs.audit.syscode */
    private String syscode;
    /** 医疗机构类型（medins_type） */
    private String medinsType;
    /** 医疗机构等级（medins_lv） */
    private String medinsLv;
    /** 出生日期 yyyy-MM-dd；可选补全患者节点 */
    private String brdy;
}

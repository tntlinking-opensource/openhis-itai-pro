/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.medcas.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 【4103】结算清单平台查询结果（列表行 + 详情 Map）。
 */
@Getter
@Setter
public class SettleListQuery4103Dto {

    /** 平台是否成功。 */
    private boolean success;

    /** 平台错误信息。 */
    private String errMsg;

    /** 平台 infcode。 */
    private String infcode;

    /** 人员编号。 */
    private String psnNo;

    /** 结算 ID。 */
    private String setlId;

    /** 结算清单流水号 setl_list_sn。 */
    private String setlListId;

    /** 病案号。 */
    private String medcasno;

    /** 患者姓名。 */
    private String psnName;

    /** 申报/结算时间原文。 */
    private String setlTime;

    /** 医疗费总额。 */
    private BigDecimal medfeeSumamt;

    /** 平台状态摘要（有则展示）。 */
    private String setlStatusText;

    /** 平台消息（失败或 warn）。 */
    private String platformMsg;

    /** setlinfo 结构化字段（详情弹窗用）。 */
    private Map<String, Object> setlinfo;
}

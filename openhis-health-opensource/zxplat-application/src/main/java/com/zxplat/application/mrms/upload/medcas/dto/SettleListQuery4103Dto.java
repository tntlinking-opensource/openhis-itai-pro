/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.medcas.dto;

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

    /**
     * 医疗费总额。
     * <p>优先 {@code setlinfo.medfee_sumamt}；平台常缺该键时回落 {@code iteminfo[].amt} 合计。</p>
     */
    private BigDecimal medfeeSumamt;

    /**
     * 平台审核状态码（{@code setlinfo.stas_type} 原文：0/1/2/3）。
     * <p>前端 Tag/分支须用本字段，勿用中文文案。</p>
     */
    private String setlStatusCode;

    /**
     * 平台审核状态展示文案（由 stas_type 映射：未提交/已提交/审核通过/审核不通过）。
     */
    private String setlStatusText;

    /**
     * 平台消息：失败取网关 errMsg；成功取 {@code setlinfo.chk_cont}（审核意见，可空）。
     */
    private String platformMsg;

    /** setlinfo 结构化字段（详情弹窗用）。 */
    private Map<String, Object> setlinfo;

    /**
     * 平台 output 全量节点（setlinfo + 诊断/手术/费用等子表），运维详情优先展示。
     */
    private Map<String, Object> platformOutput;
}

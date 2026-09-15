/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.settlelist.response;

import com.openhis.health.application.mrms.settlelist.dto.SettleListDiagnosisPreviewDto;
import com.openhis.health.application.mrms.settlelist.dto.SettleListProcedurePreviewDto;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasSettleListPayload;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 结算清单只读预览响应（全分区预留；无业务数据时空数组）。
 */
@Getter
@Setter
public class LoadSettleListPreviewResponse {

    /** 病案主档主键。 */
    @Schema(description = "病案主档主键")
    private Long caseRecordId;

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    private Long encounterId;

    /** 患者主键。 */
    @Schema(description = "患者主键")
    private Long patientId;

    /** 病案号。 */
    @Schema(description = "病案号")
    private String medcasno;

    /** 住院号。 */
    @Schema(description = "住院号")
    private String iptNo;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String psnName;

    /** 入院科别名称。 */
    @Schema(description = "入院科别")
    private String admCatyName;

    /** 入院时间。 */
    @Schema(description = "入院时间")
    private LocalDateTime admTime;

    /** 出院时间。 */
    @Schema(description = "出院时间")
    private LocalDateTime dscgTime;

    /** 特级护理天数。 */
    @Schema(description = "特级护理天数")
    private Integer spgaNurscareDays;

    /** 一级护理天数。 */
    @Schema(description = "一级护理天数")
    private Integer lv1NurscareDays;

    /** 二级护理天数。 */
    @Schema(description = "二级护理天数")
    private Integer scdNurscareDays;

    /** 三级护理天数。 */
    @Schema(description = "三级护理天数")
    private Integer lv3NurscareDays;

    /** 结算清单上传状态码。 */
    @Schema(description = "结算上传状态")
    private String settleUploadStatusCode;

    /** 平台结算清单 ID。 */
    @Schema(description = "setl_list_id")
    private String setlListId;

    /**
     * 医保人员编号 {@code psn_no}（结算上下文；未结算可空）。
     */
    @Schema(description = "医保人员编号 psn_no")
    private String psnNo;
    
    /**
     * 医保结算 ID {@code setl_id}（结算上下文；未结算可空）。
     */
    @Schema(description = "医保结算 ID setl_id")
    private String setlId;
    
    /** 诊断行（双码）。 */
    @Schema(description = "诊断信息")
    private List<SettleListDiagnosisPreviewDto> diagnoses = new ArrayList<>();

    /** 手术行（双码）。 */
    @Schema(description = "手术记录")
    private List<SettleListProcedurePreviewDto> procedures = new ArrayList<>();

    /**
     * 重症监护占位（本期无业务采集，恒空数组；上传 4101A 同口径）。
     */
    @Schema(description = "重症监护占位")
    private List<Object> icuinfo = new ArrayList<>();

    /**
     * 输血占位（本期无业务采集，恒空数组）。
     */
    @Schema(description = "输血占位")
    private List<Object> bldinfo = new ArrayList<>();

    /**
     * 费用明细占位（本期无独立清单费用行，恒空数组）。
     */
    @Schema(description = "费用项占位")
    private List<Object> iteminfo = new ArrayList<>();

    /**
     * 即将上送的 4101A 载荷；未结算或组装失败时为 null。
     */
    @Schema(description = "4101A 组装预览")
    private MedcasSettleListPayload assembledPreview;

    /** 组装预览不可用时的说明（如未结算）。 */
    @Schema(description = "组装说明")
    private String assembleHint;
}

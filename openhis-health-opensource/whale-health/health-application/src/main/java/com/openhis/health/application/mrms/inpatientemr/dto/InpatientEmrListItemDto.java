/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.inpatientemr.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 住院病例查询列表行 DTO（数据源为住院就诊；主档可尚未建档）。
 *
 * <p>字段口径与病案首页列表行对齐，便于前端复用行映射；本包独立出参，不依赖 homepage DTO。</p>
 *
 * <p>Long 主键出参统一字符串，避免前端 Number 精度丢失（同科室门禁比对 organizationId）。</p>
 */
@Getter
@Setter
public class InpatientEmrListItemDto {

    /**
     * 病案主档主键；入院后尚未打开编辑/保存时为空（列表以就诊为行）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long caseRecordId;

    /**
     * 住院就诊主键 {@code adm_encounter.id}（列表行锚点，必填）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /**
     * 患者主键 {@code adm_patient.id}。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /**
     * 院内病案号；未建档时为空。
     */
    private String medcasno;

    /** 患者姓名。 */
    private String psnName;

    /** 住院号。 */
    private String iptNo;

    /** 性别字典码。 */
    private String gendCode;

    /** 证件号码。 */
    private String certno;

    /** 入院时间。 */
    private LocalDateTime admTime;

    /** 出院时间。 */
    private LocalDateTime dscgTime;

    /** 入院科别名称。 */
    private String admCatyName;

    /** 出院科别名称。 */
    private String dscgCatyName;

    /** 主治医师姓名。 */
    private String chfdrName;

    /**
     * 入院科室主键（就诊 {@code organization_id}）。
     * <p>前端与登录所选/所属科室比对：同科室可编辑临床，其他科室只读。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;

    /** 主诊断摘要。 */
    private String mainDiagName;

    /**
     * 病案状态码；未建档时为空或 {@code none}。
     */
    private String statusCode;

    /** 病案状态展示名。 */
    private String statusLabel;

    /** 4401 首页上传状态（主档 chs_upload_status_code；空表示未上传）。 */
    private String chsUploadStatusCode;

    /**
     * 是否已结算（就诊 status_enum：COMPLETED/DISCHARGED → {@code "1"}，其余 {@code "0"}）。
     */
    private String settledCode;

    /**
     * 费用性质展示：合同名（省/市医保等）+ 险种短标（职工/居民），如 {@code 市医保·职工}。
     */
    private String insuranceTypeText;

    /**
     * 结算上传状态（主档 settle_upload_status_code；空表示未上传）。
     */
    private String settleUploadStatusCode;

    /**
     * 医嘱/清单上传状态（本期不 join，默认 null）。
     */
    private String orderUploadStatusCode;

    /**
     * 病例是否已归档（就诊下存在 LOCKED 文书）；归档后临床入口只读。
     */
    private boolean clinicalArchived;
}

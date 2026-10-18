/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.caserecord.homepage.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 病案检索列表行 DTO（数据源为住院就诊；主档可尚未建档）。
 *
 * <p>Long 主键出参统一字符串，供前端门禁比对：
 * {@code organizationId}（病历/医嘱同入院科室）、
 * {@code wardId}（体温单/护理记录管辖病区）。</p>
 */
@Getter
@Setter
public class CaseRecordListItemDto {

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

    /** 性别字典码（AdministrativeGender code，如 MALE/FEMALE）。 */
    private String gendCode;

    /**
     * 年龄展示文案（就诊上下文 {@code ageText}，如 {@code 26岁}）。
     * <p>供病案首页临床侧栏（体温单/护理记录抬头）回显；主档分页路径可空。</p>
     */
    private String ageText;

    /**
     * 床位展示名（活动床；无活动床时为「原{房}-{床}（未占床）」）。
     * <p>出院患者体温单/护理记录抬头取最近已完成就诊位置信息。</p>
     */
    private String bedName;

    /**
     * 病区/病室展示名（就诊上下文 {@code wardName}；无活动病区时为末次已完成病区）。
     */
    private String wardName;

    /**
     * 当前/末次病区主键（就诊上下文 {@code wardId}，{@code adm_location.id}，form=WARD）。
     * <p>前端体温单/护理记录门禁：与护士岗位管辖病区比对。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long wardId;

    /** 证件号码。 */
    private String certno;

    /**
     * 医保人员编号 {@code psn_no}（已结算时从医保上下文解析；未结算可空）。
     */
    private String psnNo;

    /**
     * 医保结算 ID {@code setl_id}（已结算时从医保上下文解析；未结算可空）。
     */
    private String setlId;

    /** 入院时间。 */
    private LocalDateTime admTime;

    /** 出院时间（业务真源：出院医嘱计划时间；非清床/结算）。 */
    private LocalDateTime dscgTime;

    /** 入院科别名称。 */
    private String admCatyName;

    /** 出院科别名称。 */
    private String dscgCatyName;

    /** 主治医师姓名。 */
    private String chfdrName;

    /**
     * 入院科室主键（就诊 {@code organization_id}）。
     * <p>前端病历/医嘱门禁：与登录所选/所属科室比对，同科室可写；体温单/护理记录另可按 {@link #wardId} 放行。</p>
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
     *
     * <p>住院就诊列表路径由 AppService 经 Contract/Insutype Assembler 填充；
     * 主档分页路径本期不填（无就诊合同批量装配）。</p>
     */
    private String insuranceTypeText;

    /**
     * 结算上传状态（本期不 join 结算表，默认 null，前端按「未上传」展示）。
     */
    private String settleUploadStatusCode;

    /**
     * 医嘱/清单上传状态（本期不 join，默认 null）。
     */
    private String orderUploadStatusCode;

    /**
     * 病例是否已归档（就诊下存在 PENDING_ARCHIVE/ARCHIVED 文书）；归档后临床入口只读。
     */
    private boolean clinicalArchived;
}

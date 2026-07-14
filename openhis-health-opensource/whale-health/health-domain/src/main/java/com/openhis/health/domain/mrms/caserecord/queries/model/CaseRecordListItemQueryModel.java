/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 病案列表行查询模型（DM-MRMS-001 §6）。
 *
 * <p>字段来自 {@code mrms_case_record} 列表冗余列，避免列表页 join 首页。</p>
 */
@Getter
@Setter
public class CaseRecordListItemQueryModel {

    /** 病案主档主键 {@code mrms_case_record.id}。 */
    private Long id;

    /** 院内病案号 {@code medcasno}，租户内唯一。 */
    private String medcasno;

    /** 患者姓名冗余 {@code psn_name}。 */
    private String psnName;

    /** 住院号冗余 {@code ipt_no}（{@code adm_encounter.bus_no}）。 */
    private String iptNo;

    /** 性别字典码冗余 {@code gend_code}。 */
    private String gendCode;

    /** 入院时间冗余 {@code adm_time}。 */
    private LocalDateTime admTime;

    /** 出院时间冗余 {@code dscg_time}。 */
    private LocalDateTime dscgTime;

    /** 入院科室名称冗余 {@code adm_caty_name}。 */
    private String admCatyName;

    /** 出院科室名称冗余 {@code dscg_caty_name}。 */
    private String dscgCatyName;

    /** 主任/副主任医师姓名冗余 {@code chfdr_name}。 */
    private String chfdrName;

    /** 主诊断名称摘要冗余 {@code main_diag_name}。 */
    private String mainDiagName;

    /** 病案工作流状态 code {@code status_code}。 */
    private String statusCode;
}

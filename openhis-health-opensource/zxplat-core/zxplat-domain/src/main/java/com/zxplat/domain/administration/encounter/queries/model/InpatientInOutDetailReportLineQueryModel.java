/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.queries.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 住院患者入出明细报表行投影（AS-RPT-010；仅本聚合字段，名称由应用层 enrich）。
 */
@Getter
@Setter
public class InpatientInOutDetailReportLineQueryModel {

    /** 就诊主键。 */
    private Long encounterId;

    /** 住院业务号。 */
    private String busNo;

    /** 患者主键。 */
    private Long patientId;

    /** 就诊状态码。 */
    private String statusEnum;

    /** 入院日（主档 start_time）。 */
    private LocalDateTime startTime;

    /** 出院日（主档 end_time；未出院可空）。 */
    private LocalDateTime endTime;

    /**
     * 入院科室主键：未分床取主档 organization_id；已挂病区时由应用层按最早 WARD→Location.organization 回填，
     * 本投影在领域侧回填主档 organization_id 作兜底。
     */
    private Long admissionOrganizationId;

    /** 入院病区位置主键（最早 WARD 的 location_id；可空）。 */
    private Long admissionWardLocationId;

    /** 当前或末次病区位置主键。 */
    private Long currentWardLocationId;

    /** 当前或末次病房位置主键。 */
    private Long currentHouseLocationId;

    /** 当前或末次床位位置主键。 */
    private Long currentBedLocationId;

    /** 是否存在已确认转科（最早 WARD 之后仍有其它 WARD 轨迹）。 */
    private boolean transferred;

    /**
     * 转科后病区位置主键序列（按 start_time 升序，不含最早入院病区）；无转科为空列表。
     */
    private List<Long> transferTargetWardLocationIds = new ArrayList<>();
}

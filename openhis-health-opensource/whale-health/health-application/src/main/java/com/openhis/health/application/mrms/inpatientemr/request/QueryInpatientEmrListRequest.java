/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.inpatientemr.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 住院病例查询分页请求（病案管理菜单，只读列表）。
 *
 * <p>以住院就诊为行；已建档时 left join 回填病案号等。与病案首页工作台列表筛选口径对齐的子集。</p>
 */
@Getter
@Setter
public class QueryInpatientEmrListRequest extends AbstractPageQuery {

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 入院科别名称过滤（精确匹配就诊组织名）；空则不过滤。 */
    private String admCatyName;

    /** 主治医师 {@code adm_practitioner.id} 过滤；空则不过滤。 */
    private Long attendingPractitionerId;

    /**
     * 入院日期起（含）；与 {@link #admTo} 组成闭区间，下推就诊 {@code startTime}。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    private LocalDate admFrom;

    /**
     * 入院日期止（含）；语义同 {@link #admFrom}。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    private LocalDate admTo;

    /**
     * 出院日期起（含）；与 {@link #dscgTo} 组成闭区间，下推就诊 {@code endTime}。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    private LocalDate dscgFrom;

    /**
     * 出院日期止（含）；语义同 {@link #dscgFrom}。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    private LocalDate dscgTo;

    /**
     * 是否出院（映射住院就诊状态并集）：{@code "1"}=已出院态（COMPLETED/DISCHARGED），
     * {@code "0"}=未出院态（IN_PROGRESS/ON_HOLD/PENDING）；空/全部用默认在院+出院并集。
     */
    private String dischargedCode;
}

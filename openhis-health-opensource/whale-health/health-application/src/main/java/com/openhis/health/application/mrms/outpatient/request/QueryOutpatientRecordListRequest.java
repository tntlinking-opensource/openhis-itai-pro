/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.outpatient.request;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 门诊病历查询分页请求（病案管理菜单）。
 */
@Getter
@Setter
public class QueryOutpatientRecordListRequest extends AbstractPageQuery {

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 文书状态 code（如 DRAFT/SUBMITTED）；空则不过滤。 */
    private String statusCode;

    /** 就诊科室名称（模糊）。 */
    private String deptName;

    /** 就诊日期起（{@code yyyy-MM-dd}，闭区间日初）。 */
    private LocalDate encounterDateFrom;

    /** 就诊日期止（{@code yyyy-MM-dd}，闭区间日末）。 */
    private LocalDate encounterDateTo;
}

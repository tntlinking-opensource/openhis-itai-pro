/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.list.request;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 病案检索分页请求（IF-MR-List）。
 */
@Getter
@Setter
public class QueryCaseRecordListRequest extends AbstractPageQuery {

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 状态码过滤（单值，与 statusCodes 二选一）。 */
    private String statusCode;

    /** 状态码过滤（多值 IN，优先于 statusCode）。 */
    private List<String> statusCodes;

    /** 入院科别名称过滤。 */
    private String admCatyName;

    /** 主治医师 {@code adm_practitioner.id} 过滤。 */
    private Long attendingPractitionerId;

    /** 出院日期起（{@code yyyy-MM-dd}，闭区间日初）。 */
    private LocalDate dscgFrom;

    /** 出院日期止（{@code yyyy-MM-dd}，闭区间日末）。 */
    private LocalDate dscgTo;
}

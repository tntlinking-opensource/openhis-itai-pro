/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.caserecord.queries;

import com.zxplat.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 医保办上报台分页查询入参（病案主档）。
 *
 * <p>关键字见基类 {@link AbstractPageQuery#getKeyword()}（姓名/住院号/病案号）。
 * {@code infno=4401} 时仅已归档且按 CHS 上传态过滤；其余按结算上传标志过滤，不要求归档。</p>
 */
@Getter
@Setter
public class CaseRecordUploadPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 租户主键 {@code tenant_id}，条件下推必填。 */
    private Long tenantId;

    /**
     * 交易号：{@code 4401} / {@code 4101A} / {@code 4102}；空按结算清单口径。
     */
    private String infno;

    /** CHS/结算统一入参的状态过滤 code（由应用层按交易号解释）。 */
    private String uploadStatusCode;

    /** 出院时间起（含）。 */
    private LocalDateTime dscgTimeFrom;

    /** 出院时间止（含）。 */
    private LocalDateTime dscgTimeTo;
}

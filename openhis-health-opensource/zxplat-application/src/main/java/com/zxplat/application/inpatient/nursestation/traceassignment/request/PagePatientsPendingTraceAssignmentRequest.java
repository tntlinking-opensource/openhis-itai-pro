/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.traceassignment.request;

import com.zxplat.domain.share.query.AbstractPageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * IF-IP-TRC-01：分页查询待追溯分配患者队列入参。
 *
 * <p>分页与关键字继承 {@link com.zxplat.domain.share.query.AbstractPageQuery}：
 * {@code pageNo}、{@code pageSize}、可选 {@code keyword}（姓名/床号/就诊号）。</p>
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "分页查询待追溯分配患者队列入参", requiredProperties = {"pageNo", "pageSize"})
public class PagePatientsPendingTraceAssignmentRequest extends AbstractPageQuery {

    /** 分页查询待追溯分配患者队列入参。 */
    private static final long serialVersionUID = 1L;
}

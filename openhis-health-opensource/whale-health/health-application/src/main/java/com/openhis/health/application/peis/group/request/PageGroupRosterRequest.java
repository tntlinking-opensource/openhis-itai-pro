/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 团检名册分页请求。
 */
@Getter
@Setter
public class PageGroupRosterRequest extends AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    /** 合同主键（按合同查名册时必填）。 */
    private Long contractId;

    /** 体检分组主键（可选筛选）。 */
    private Long batchId;

    /** 支付状态过滤（可选）。 */
    private String payStatusCode;

    /** 建单过滤：UNENROLLED | ENROLLED（可选，优先于 payStatusCode）。 */
    private String enrollmentFilter;

    /** 到检状态过滤（可选）。 */
    private String arrivalStatusCode;

    /** 学院主键（可选）。 */
    private Long collegeId;

    /** 班级名称（可选）。 */
    private String className;
}

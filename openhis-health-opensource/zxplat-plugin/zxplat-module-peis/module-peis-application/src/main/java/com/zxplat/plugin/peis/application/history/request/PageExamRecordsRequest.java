/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.history.request;

import com.zxplat.framework.core.application.paging.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 体检记录分页请求（方案 B：以体检单为主列表）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PageExamRecordsRequest extends AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    /** 业务日起（含）；可空。 */
    private LocalDate dateFrom;

    /** 业务日止（含）；可空。 */
    private LocalDate dateTo;

    /** 可选：按患者过滤（登记页跳转）。 */
    private Long patientId;

    /**
     * 个检/团检范围：{@code all}（默认）/ {@code personal} / {@code group}。
     */
    private String groupScope;
}

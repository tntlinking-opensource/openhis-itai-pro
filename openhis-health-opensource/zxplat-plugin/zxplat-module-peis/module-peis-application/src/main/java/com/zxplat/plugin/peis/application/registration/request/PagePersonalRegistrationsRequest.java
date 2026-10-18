/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.registration.request;

import com.zxplat.framework.core.application.paging.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 个检登记历史分页请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PagePersonalRegistrationsRequest extends AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    /** 业务日起（含）。 */
    private LocalDate dateFrom;

    /** 业务日止（含）。 */
    private LocalDate dateTo;

    /** 可选：按患者主键过滤。 */
    private Long patientId;
}

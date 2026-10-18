/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.tracenumber.queries;

import com.zxplat.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 院内在院码分页入参（DM-CUS-010 §6.2.3）。
 */
@Getter
@Setter
public class InHospitalTraceNumberPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 追溯码关键字。 */
    private String traceNoKeyword;

    /** 药品定义主键。 */
    private Long itemId;

    /** 批号关键字。 */
    private String lotNumberKeyword;
}

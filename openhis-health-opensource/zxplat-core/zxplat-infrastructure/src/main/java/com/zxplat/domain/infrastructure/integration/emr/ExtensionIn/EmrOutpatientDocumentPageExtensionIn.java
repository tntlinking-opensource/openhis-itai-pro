/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.emr.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊文书跨患者分页入参（病案「门诊病历查询」）。
 */
@Data
@Accessors(chain = true)
public class EmrOutpatientDocumentPageExtensionIn {

    /**
     * 文书类型 code。
     *
     * <p>空或平台常量 {@code OUTPATIENT_RECORD} 时，实现侧按
     * {@code category_code=OUTPATIENT} 收齐院点门诊类型（如国基 {@code DT_GJ_01}）；
     * 其它显式 code 则精确匹配。</p>
     */
    private String documentTypeCode;

    /** 患者主键集合；与 encounterIds 同时有值时为 OR。 */
    private List<Long> patientIds;

    /** 就诊主键集合；与 patientIds 同时有值时为 OR。 */
    private List<Long> encounterIds;

    /** 文书状态 code（如 DRAFT/PENDING_ARCHIVE/ARCHIVED）；空则不过滤。 */
    private String status;

    /** 页码（1-based）。 */
    private Integer pageNo;

    /** 每页条数。 */
    private Integer pageSize;
}

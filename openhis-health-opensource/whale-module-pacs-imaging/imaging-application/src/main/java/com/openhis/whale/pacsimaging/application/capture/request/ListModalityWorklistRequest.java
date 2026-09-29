/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.capture.request;

import lombok.Getter;
import lombok.Setter;

/**
 * MWL 待扫工单查询请求（IF-PACS-CAP-07 最小：HTTP 查询，非 DICOM C-FIND）。
 */
@Getter
@Setter
public class ListModalityWorklistRequest {

    /** 页码（从 1 开始）。 */
    private Integer pageNo;

    /** 每页条数。 */
    private Integer pageSize;

    /** 登记/执行科室；可选。 */
    private Long organizationId;

    /** 模态码；可选。 */
    private String modalityCode;
}

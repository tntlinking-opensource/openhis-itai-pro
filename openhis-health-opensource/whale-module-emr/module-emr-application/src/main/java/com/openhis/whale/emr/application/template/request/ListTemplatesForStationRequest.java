/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.template.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 医生站模板树：按场景与当前用户可见范围加载。
 */
@Getter
@Setter
public class ListTemplatesForStationRequest {

    /** OUTPATIENT | INPATIENT */
    private String stationKind;

    /** 文书类型编码 */
    private String documentTypeCode;

    /** 是否启用 */
    private Boolean enabled;
}

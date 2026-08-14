/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.medcas.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 一键上报分步结果（4401→4101A→4102）。
 */
@Getter
@Setter
public class OneClickReportResultDto {

    /** 4401 分步结果。 */
    private UploadResultDto homepage4401;

    /** 4101A 分步结果。 */
    private UploadResultDto settle4101A;

    /** 4102 分步结果。 */
    private UploadResultDto commit4102;

    /** 是否三步均成功（含跳过视为成功）。 */
    private Boolean allSuccess;
}

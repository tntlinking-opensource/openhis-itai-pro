/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.medcas.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 上传日志查询。
 */
@Getter
@Setter
public class ListUploadLogsRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;
}

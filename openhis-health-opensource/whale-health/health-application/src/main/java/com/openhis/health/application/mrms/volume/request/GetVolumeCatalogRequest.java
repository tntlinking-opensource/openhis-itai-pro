/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.volume.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 卷宗目录查询请求。
 */
@Getter
@Setter
public class GetVolumeCatalogRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;
}

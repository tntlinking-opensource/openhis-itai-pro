/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检名册模板文件响应。
 */
@Getter
@Setter
public class GroupRosterImportFileResponse {

    private String fileName;
    private String contentType;
    private byte[] fileBytes;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 下载团检名册导入模板请求。
 */
@Getter
@Setter
public class DownloadRosterImportTemplateRequest {

    /** 人员类型：STUDENT / STAFF。 */
    private String memberKind;
}

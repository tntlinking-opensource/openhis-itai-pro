/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.announcement.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 小程序首页公告。
 */
@Getter
@Setter
public class HomeNoticeResponse {

    private Long id;

    private String title;

    private String contentHtml;

    private String updatedAt;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.announcement.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存小程序首页公告。
 */
@Getter
@Setter
public class SaveHomeNoticeRequest {

    /** 展示标题（骨架阶段可忽略，默认「院内公告」）。 */
    private String title;

    /** 公告 HTML（所见即所得编辑器产出）。 */
    private String contentHtml;
}

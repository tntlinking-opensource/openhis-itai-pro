/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.extension.pacsimaging;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 报告影像附件视图（跨模块 SPI）。
 */
@Getter
@Setter
public class PacsReportImagingAttachmentView {

    /** 是否已绑定 Study。 */
    private Boolean hasStudy = Boolean.FALSE;

    /** Study Instance UID；无 Study 时为空。 */
    private String studyInstanceUid;

    /** 是否已拼出可用阅片 URL。 */
    private Boolean viewerAvailable = Boolean.FALSE;

    /** OHIF 启动 URL（含 Token）；不可用时为空。 */
    private String viewerLaunchUrl;

    /** Token 过期时间（ISO-8601）；不可用时为空。 */
    private String viewerExpiresAt;

    /** 关键图预览行（含预签名 URL）。 */
    private List<PacsReportKeyImageView> keyImages = new ArrayList<>();

    /**
     * 空附件视图。
     *
     * @return 无 Study、无关键图、无阅片
     */
    public static PacsReportImagingAttachmentView empty() {
        return new PacsReportImagingAttachmentView();
    }
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.extension.pacsimaging;

/**
 * 报告侧影像附件 SPI（health → imaging，可选装配）。
 *
 * <p>实现落在 {@code imaging-application}；未启用 imaging 时 Port Bean 不存在，
 * 报告书写/审核须降级为空附件，不得阻断开写。</p>
 */
public interface PacsReportImagingAttachmentPort {

    /**
     * 按工单加载关键图预览与阅片深链。
     *
     * <p>无 Study / 无端点时返回部分视图（关键图仍可有）；不得抛业务异常阻断报告。</p>
     *
     * @param workItemId 工单主键
     * @return 附件视图；{@code workItemId} 为空时返回空视图
     */
    PacsReportImagingAttachmentView loadAttachment(Long workItemId);
}

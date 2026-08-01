/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.response;

import com.openhis.chs.application.notice.dto.NoticeScanImportResultDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 确认入库进度；可续跑时 remainingCount &gt; 0。
 */
@Data
public class GetConfirmNoticeScanImportProgressResponse {

    private String jobId;
    private String status;
    private int total;
    private int processed;
    private int percent;
    private String currentPackDir;
    private String message;
    private int importedCount;
    private int skippedCount;
    private int failedCount;
    /** 尚未处理的包数；&gt;0 且非 RUNNING 时可续跑。 */
    private int remainingCount;
    /** 是否可点「继续入库」。 */
    private boolean resumable;
    private List<NoticeScanImportResultDto> results = new ArrayList<>();
}

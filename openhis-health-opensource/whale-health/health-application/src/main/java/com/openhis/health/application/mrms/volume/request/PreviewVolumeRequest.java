/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.volume.request;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 卷宗 PDF 预览请求。
 */
@Getter
@Setter
public class PreviewVolumeRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 待合并的 EMR 文档主键列表。 */
    private List<Long> documentIds = new ArrayList<>();

    /** 是否包含病案首页 PDF。 */
    private Boolean includeHomepage = Boolean.TRUE;
}

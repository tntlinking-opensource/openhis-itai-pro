/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.capture.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 采集详情响应。
 */
@Getter
@Setter
public class GetCaptureDetailResponse {

    /** 工单主键。 */
    private Long workItemId;

    /** 检查号。 */
    private String accessionNumber;

    /** 患者主键。 */
    private Long patientId;

    /** 科室。 */
    private Long organizationId;

    /** 工单状态。 */
    private String statusEnum;

    /** 模态码。 */
    private String modalityCode;

    /** 部位码。 */
    private String bodySiteCode;

    /** 是否已有媒体标志。 */
    private Boolean hasMedia;

    /** Study Instance UID；无 Study 时为空。 */
    private String studyInstanceUid;

    /** Series Instance UID；无 Study 时为空。 */
    private String seriesInstanceUid;

    /** 关键图数量。 */
    private Long keyImageCount;
}

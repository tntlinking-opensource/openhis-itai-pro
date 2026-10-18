/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.execution.response;

import com.zxplat.application.medicaltech.execution.dto.MedicalTechPatientQueueItemDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * 分页查询医技患者队列响应（AS-MT-EXE-001 IF-MT-EXE-01）。
 *
 * <p>继承 {@link PagedResult}：{@code content} 为队列行，{@code totalCount} 为总数（B.10.11）。</p>
 */
public class PageMedicalTechPatientQueueResponse extends PagedResult<MedicalTechPatientQueueItemDto> {
}

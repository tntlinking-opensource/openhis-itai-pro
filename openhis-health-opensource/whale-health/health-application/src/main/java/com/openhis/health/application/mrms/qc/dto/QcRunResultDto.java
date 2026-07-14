/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.qc.dto;

import com.openhis.health.application.mrms.homepage.dto.CaseRecordDetailDto;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 质控执行结果 DTO。
 */
@Getter
@Setter
public class QcRunResultDto {

    /** 质控批次主键。 */
    private Long qcRunId;

    /** 是否通过（无阻断级开放缺陷）。 */
    private Boolean passed;

    /** 开放阻断级缺陷数。 */
    private Integer openBlockCount;

    /** 开放警告级缺陷数。 */
    private Integer openWarnCount;

    /** 本批次缺陷明细列表。 */
    private List<QcDefectDto> defects = new ArrayList<>();

    /** 病案主档摘要（可选回填）。 */
    private CaseRecordDetailDto caseRecord;
}

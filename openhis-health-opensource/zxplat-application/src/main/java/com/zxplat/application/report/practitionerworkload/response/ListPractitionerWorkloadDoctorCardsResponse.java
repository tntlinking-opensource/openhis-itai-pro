/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.practitionerworkload.response;

import com.zxplat.application.report.practitionerworkload.dto.PractitionerWorkloadDoctorCardDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 医生工作量卡片批量查询出参。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "医生工作量卡片批量查询出参")
public class ListPractitionerWorkloadDoctorCardsResponse {

    /** 与入参 practitionerIds 顺序对齐的卡片行（无数据仍返回零合计）。 */
    @Schema(description = "医生卡片行")
    private List<PractitionerWorkloadDoctorCardDto> cards;
}

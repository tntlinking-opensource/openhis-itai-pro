/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.response;

import com.openhis.health.application.report.nurseworkload.dto.NurseWorkloadNurseCardDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 护士工作量卡片批量查询出参。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士工作量卡片批量查询出参")
public class ListNurseWorkloadNurseCardsResponse {

    /** 有数据的护士卡片（前端与下拉合并补零）。 */
    @Schema(description = "护士卡片行")
    private List<NurseWorkloadNurseCardDto> cards;
}

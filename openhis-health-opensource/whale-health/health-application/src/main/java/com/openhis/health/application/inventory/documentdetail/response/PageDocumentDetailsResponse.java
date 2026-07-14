/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.documentdetail.response;

import com.openhis.health.application.inventory.documentdetail.dto.DocumentDetailRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 单据详情分页出参（IF-INV-DDC-{域}-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "单据详情分页 IF-INV-DDC-02")
public class PageDocumentDetailsResponse {

    @Schema(description = "明细行总数")
    /** 明细行总数。 */
    private long total;

    @Schema(description = "当前页码")
    /** 当前页码。 */
    private int pageNo;

    @Schema(description = "每页条数")
    /** 每页条数。 */
    private int pageSize;

    @Schema(description = "明细行")
    /** 明细行。 */
    private List<DocumentDetailRowDto> records;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.print.printlog.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 按业务主键批量回查「是否已打印」请求（PRT-GLOBAL-001 §6）。
 *
 * <p>仅统计 {@code status=SUCCESS}；从 {@code biz_ref_json} 的数组或标量字段中匹配 keys。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "按业务主键回查已打印请求", requiredProperties = {"scenarioCode", "keys"})
public class QueryPrintedKeysRequest {

    /** 场景码（与 confirm 写入的 binding_code / scenarioCode 对齐）。 */
    @NotEmpty(message = "scenarioCode 不能为空")
    @Schema(description = "打印场景码", required = true, example = "ip-orderDispenseSheet-print")
    private String scenarioCode;

    /**
     * bizRef 主键字段名；默认 {@code listRowKeys}。
     * <p>支持数组（合打）或标量（单键）；亦兼容同义单复数（如 encounterId / encounterIds）。</p>
     */
    @Schema(description = "bizRef 主键字段名，默认 listRowKeys")
    private String keyField;

    /** 待判定的业务主键列表（单批建议 ≤ 500）。 */
    @NotEmpty(message = "keys 不能为空")
    @Schema(description = "待回查业务主键列表", required = true)
    private List<String> keys;

    /**
     * 业务查询日 YYYY-MM-DD：二次过滤 bizRef.queryDate；
     * 不收窄日志 created_at（打印日可晚于业务日）。
     */
    @Schema(description = "业务查询日 YYYY-MM-DD，可空")
    private String queryDate;

    /**
     * 业务区间起（YYYY-MM-DD）：与 {@link #bizDateTo} 同时传入时，
     * 要求 bizRef.dateFrom / dateTo 完全一致（护理记录、费用清单等）。
     */
    @Schema(description = "业务区间起 YYYY-MM-DD，可空")
    private String bizDateFrom;

    /** 业务区间止（YYYY-MM-DD），见 {@link #bizDateFrom}。 */
    @Schema(description = "业务区间止 YYYY-MM-DD，可空")
    private String bizDateTo;
}

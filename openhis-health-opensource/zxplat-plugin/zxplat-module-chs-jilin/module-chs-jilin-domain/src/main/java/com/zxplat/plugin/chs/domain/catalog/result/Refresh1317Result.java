/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 1317 同步结果：落库行数、回写院内行数、跳过行数、拉取页数。
 */
@Data
@Accessors(chain = true)
@Schema(description = "1317 同步结果")
public class Refresh1317Result {

    /** 本地镜像 upsert 行数 */
    @Schema(description = "落库行数")
    private int upserted;

    /** 成功回写院内 yb_no/yb_match_flag 的行数 */
    @Schema(description = "回写院内行数")
    private int writtenBack;

    /** 未命中院内品项或非有效行而跳过的回写次数 */
    @Schema(description = "跳过回写次数")
    private int skipped;

    /** 实际调用平台的页数 */
    @Schema(description = "拉取页数")
    private int pagesFetched;

    /** 平台返回的总记录数（recordCounts，首屏元数据） */
    @Schema(description = "平台总记录数 recordCounts")
    private Integer platformRecordCounts;

    /** 平台返回的总页数（pages） */
    @Schema(description = "平台总页数 pages")
    private Integer platformPages;
}

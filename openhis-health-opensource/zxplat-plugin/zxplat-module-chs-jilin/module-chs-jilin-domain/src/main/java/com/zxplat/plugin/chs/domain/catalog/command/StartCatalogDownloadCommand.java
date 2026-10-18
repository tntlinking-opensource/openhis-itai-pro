/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.command;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 启动/探测医保目录文件下载（1301/1305/1306 等）请求。
 * <p>用于 {@code previewCatalogDownload} 与 {@code startCatalogDownload}。
 * 正式下载无轮次上限，版本链由省级医保方案按平台应答停链；
 * {@code maxVersions&gt;0} 时仅用于探测可选上限。<。</p> */
@Data
@Schema(description = "启动医保目录文件下载请求")
public class StartCatalogDownloadCommand {

    @Schema(description = "契约 bus_no")
    private String contractBusNo;

    @Schema(description = "契约主键（与 bus_no 二选一，bus_no 优先）")
    private Long contractId;

    @Schema(description = "接口号 infno，默认 1301")
    private String infno;

    @Schema(description = "仅探测：可选轮次上限；正式下载忽略；未传表示探测也无上限")
    private Integer maxVersions;

    @Schema(description = "true 时忽略游标，从该 infno 规范初始 ver 全量拉取（1301=0；305=F002/F003；308=-1）")
    private Boolean forceStartFromZero;

    @Schema(description = "true 时清空本地 ZIP 缓存后再拉取（与 forceStartFromZero 独立，默认 false）")
    private Boolean clearZipCache;

    @Schema(description = "显式指定本次 data.ver 起点（优先于游标与 force 默认）")
    private String startLocalVer;

    @Schema(description = "1305 版本分支：F002 或 F003（infno=1305 时用于选链：全量起点或本分支游标）")
    private String verBranch;
}

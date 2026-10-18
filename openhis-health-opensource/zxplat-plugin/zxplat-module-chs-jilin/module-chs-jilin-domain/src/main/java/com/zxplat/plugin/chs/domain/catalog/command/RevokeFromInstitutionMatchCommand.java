/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.command;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 按 1317 本地镜像行撤销平台对照（3302）。
 * <p>支持院内停用/已删除（孤儿）行：平台入参取镜像 medins_list_codg + med_list_codg。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "按平台已对照镜像撤销 3302")
public class RevokeFromInstitutionMatchCommand {

    /** 契约业务编码 */
    @Schema(description = "契约业务编码 fin_contract.bus_no")
    private String contractBusNo;

    /** 1317 本地镜像主键 */
    @Schema(description = "yb_institution_catalog_match.id")
    private Long matchId;
}

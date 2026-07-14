/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.transferout.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 药库调出单分页列表请求（IF-WHS-TR-02）。
 *
 * <p>分页与关键字字段继承 {@link AbstractHealthPageRequest}（B.10.11：对外 {@code pageNo} 为 1-based）。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class PageTransferOutsRequest extends AbstractHealthPageRequest {

    /** 源药库主键，0 表示机构内全部可见药库。 */

    @Schema(description = "源药库主键，0 表示机构内全部可见药库")
    private Long sourceLocationId;

    /** 目的位置筛选。 */

    @Schema(description = "目的位置筛选")
    private Long purposeLocationId;

    /** 状态 SupplyStatus#getCode。 */
    @Schema(description = "状态 SupplyStatus#getCode")
    private String statusEnum;

    /** 申请时间起。 */
    @Schema(description = "申请时间起")
    private LocalDateTime applyTimeFrom;

    /** 申请时间止。 */
    @Schema(description = "申请时间止")
    private LocalDateTime applyTimeTo;

    /**
     * 供应类型持久化身份筛选；若传入则须为药库调出 {@link com.openhis.health.domain.share.enums.SupplyType#CABINET_TRANSFER_OUT} 的
     * {@code getCode()}，否则参数门禁拒绝。
     */
    @Schema(description = "单据类型，须为 CABINET_TRANSFER_OUT")
    /** 单据类型，须为 CABINET_TRANSFER_OUT。 */
    private String typeEnum;
}

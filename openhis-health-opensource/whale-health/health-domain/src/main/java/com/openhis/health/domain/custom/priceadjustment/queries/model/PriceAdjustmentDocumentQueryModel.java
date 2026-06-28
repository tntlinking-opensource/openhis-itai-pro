/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.custom.priceadjustment.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

/**
 * 调价单聚合读模型（按 {@code bus_no} 聚合主行与子表）。
 *
 * <p>审批字段取锚点主行；项目名称等展示字段由应用层填充。</p>
 */
@Getter
@Setter
public class PriceAdjustmentDocumentQueryModel {

    /** 调价单号 {@code bus_no}。 */
    private String busNo;

    /** 审批状态 code（{@link com.openhis.health.domain.share.enums.PriceAdjustmentStatus}）。 */
    private String statusEnum;

    /** 提交人 {@code submitter_id}。 */
    private Long submitterId;

    /** 提交时间 {@code submit_time}。 */
    private Date submitTime;

    /** 审批人 {@code approver_id}。 */
    private Long approverId;

    /** 审批时间 {@code approve_time}。 */
    private Date approveTime;

    /** 审批意见 {@code approve_comment}。 */
    private String approveComment;

    /** 项目行列表（每行含批号明细）。 */
    private List<PriceAdjustmentLineQueryModel> lines;
}

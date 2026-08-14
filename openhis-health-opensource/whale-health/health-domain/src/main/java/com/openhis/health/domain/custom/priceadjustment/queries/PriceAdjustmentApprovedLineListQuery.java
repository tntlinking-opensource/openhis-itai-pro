/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.priceadjustment.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 已生效调价项目主行分页查询入参（AS-FIN-PRC-002 调价明细）。
 *
 * <p>{@link #startDate}/{@link #endDate} 映射主行 {@code approve_time} 闭区间；状态在查询层硬过滤
 * {@code approved}。项目关键字由应用层预解析为 {@link #itemIds} 后下推。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PriceAdjustmentApprovedLineListQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 项目物理表；空表示药/耗/诊疗/服务四类并集。
     */
    private String itemTable;

    /**
     * 项目定义主键集合（应用层目录关键字解析）；{@code null} 表示不按项目收窄；
     * 空列表表示关键字无命中，读侧应直接返回空页。
     */
    private List<Long> itemIds;

    /**
     * 调价单号；非空时参与模糊匹配（与单据列表单号关键字语义一致）。
     */
    private String busNo;

    /**
     * 排除的调价原因全文列表；非空时 {@code adjustment_reason NOT IN (...)}。
     *
     * <p>供调用方在列表谓词层剔除审计单；空或未设表示不排除。</p>
     */
    private List<String> excludeAdjustmentReasons;
}

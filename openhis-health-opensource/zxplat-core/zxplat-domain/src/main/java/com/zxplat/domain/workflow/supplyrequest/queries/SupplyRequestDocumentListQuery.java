/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplyrequest.queries;

import com.zxplat.domain.share.query.AbstractPageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code wkf_supply_request} 上「按业务单号聚合」的各类单据列表过滤入参（同号多行共享头字段，列表按 {@code bus_no} 去重分页）。
 *
 * <p>继承 {@link AbstractPageQuery}：分页 {@code pageNo}/{@code pageSize}（1-based）、{@code keyword}、
 * {@code startDate}/{@code endDate} 等与全局列表约定一致（B.10.11）；申请时刻窗由应用层映射到
 * {@link #setStartDate(java.time.LocalDateTime)} / {@link #setEndDate(java.time.LocalDateTime)}。</p>
 *
 * <p>典型用法：进货、订货、退货、请领等应用层在 {@link com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestQueryService}
 * 中通过 {@code type_enum} 与其它条件收窄后，调用 {@code countDistinctBusNo} / {@code listDistinctBusNoPage} / {@code listByBusNosWithFilter}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SupplyRequestDocumentListQuery extends AbstractPageQuery {

    /** 序列化版本（与父类共同序列化场景下显式声明）。 */
    private static final long serialVersionUID = 1L;

    /**
     * 供应类型持久化码（{@link com.zxplat.domain.share.enums.SupplyType#getCode()}）；非空时等值过滤。
     * <p>与 {@link #typeEnums} 互斥，本字段非空时优先。</p>
     */
    private String typeEnum;

    /**
     * 供应类型多选（IN）；与 {@link #typeEnum} 互斥。
     * <p>用于出库可关联请领默认「药房请领 + 科室请领」并集等场景。</p>
     */
    private List<String> typeEnums;

    /** 源位置主键；与 {@code source_location_id} 对齐。 */
    private Long sourceLocationId;

    /**
     * 源位置主键多选（IN）；与 {@link #sourceLocationId} 互斥，非空时优先于单值等值过滤。
     * <p>用于「sourceLocationId=0 表示当前用户可见全部药库」等应用层门控后的 id 集合。</p>
     */
    private List<Long> sourceLocationIds;

    /** 可选目的位置主键；与 {@code purpose_location_id} 对齐。 */
    private Long purposeLocationId;

    /**
     * 目的位置主键多选（IN）；与 {@link #purposeLocationId} 互斥，非空时优先于单值等值过滤。
     * <p>用于「purposeLocationId=0 表示当前用户可见全部药房」等应用层门控后的 id 集合。</p>
     */
    private List<Long> purposeLocationIds;

    /**
     * 库房统一筛选（财务审批页）：非空时匹配 {@code source_location_id} <strong>或</strong> {@code purpose_location_id}。
     * <p>与单值/多选源目的字段互斥；本字段非空时优先，不再叠加源/目的等值谓词。</p>
     */
    private Long locationId;

    /** 可选供应商主键；非空时等值过滤 {@code supplier_id}（财务审批页供应商筛）。 */
    private Long supplierId;

    /** 可选状态；{@link com.zxplat.domain.share.enums.SupplyStatus#getCode()} 持久化字符串（B.10.8.1）。 */
    private String statusEnum;

    /** 可选租户；与 {@code tenant_id} 对齐。 */
    private Long tenantId;

    /**
     * 业务单号白名单（IN）；与关键字筛选取交集。
     * <p>住院汇总发药「已发药」列表由应用层按 {@code med_medication_dispense} 终态写入，禁止仅用 Supply 审批态替代。</p>
     */
    private List<String> busNoIn;

    /**
     * 单号模糊（仅 {@code bus_no} LIKE，不区分大小写）；与 {@link #getKeyword()} 可并存（AND）。
     * <p>财务审批页「单号」条件映射本字段；通用 keyword 仍可搜单号/原因/备注。</p>
     */
    private String busNoLike;

    /** 制单人（申请人）主键；非空时等值过滤 {@code applicant_id}。 */
    private Long applicantId;

    /** 审批人主键；非空时等值过滤 {@code approver_id}。 */
    private Long approverId;

    /**
     * 品类表名；非空时等值过滤 {@code item_table}（药品 {@code med_medication_definition} / 耗材 {@code adm_device_definition}）。
     */
    private String itemTable;

    /**
     * 制单时间窗起（闭区间，映射行 {@code occurrence_time}）；与父类 {@code startDate}/{@code endDate}（申请时刻 {@code apply_time}）分列。
     */
    private LocalDateTime occurrenceTimeFrom;

    /** 制单时间窗止（闭区间，映射 {@code occurrence_time}）。 */
    private LocalDateTime occurrenceTimeTo;

    /** 审批时间窗起（闭区间，映射 {@code approval_time}）。 */
    private LocalDateTime approvalTimeFrom;

    /** 审批时间窗止（闭区间，映射 {@code approval_time}）。 */
    private LocalDateTime approvalTimeTo;
}

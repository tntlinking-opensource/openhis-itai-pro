/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.purchaserequest.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.openhis.health.application.inventory.department.purchaserequest.dto.DeptPurchaseRequestDetailLineDto;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 科室采购申请单详情响应（IF-DEP-CPR-03）。
 *
 * <p>凡 {@code *Enum} 语义且为 {@link String} 的字段，均为各业务枚举
 * {@link com.openhis.health.domain.share.enums.EnumInterface#getCode()} 持久化值（B.10.8.1）。
 * {@link #rowVersion} 须原样带入
 * {@link com.openhis.health.application.inventory.department.purchaserequest.request.UpdateDeptPurchaseRequestRequest}（对偶订货 WHS_PO_105 → DEP_CPR_105）。</p>
 */
@Getter
@Setter
public class GetDeptPurchaseRequestDetailResponse {

    /** 业务单号（{@code bus_no} 聚合键）。 */
    private String busNo;

    /**
     * 单据类型持久化码：固定
     * {@link com.openhis.health.domain.share.enums.SupplyType#DEPT_PURCHASE_REQUEST}。
     */
    private String typeEnum;

    /**
     * 单据状态持久化码（{@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()}）。
     */
    private String statusEnum;

    /** 目的科室药房主键（单端点 {@code purpose_location_id}；无 source/supplier）。 */
    private Long purposeLocationId;

    /**
     * 目的位置类型持久化码：由科室药房 {@code form_enum} 推导的
     * {@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}。
     */
    private String purposeTypeEnum;

    /**
     * 供应类别持久化码（{@link com.openhis.health.domain.share.enums.SupplyCategory#getCode()}）。
     */
    private String categoryEnum;

    /** 经手人医务人员主键（头行 {@code practitioner_id}；可空）。 */
    private Long practitionerId;

    /** 制单时刻（头行 {@code occurrence_time}；新建/修订由请求头写入各行）。 */
    private LocalDateTime occurrenceTime;

    /** 单据备注（头行 {@code remake}；详情取同号头行代表）。 */
    private String remake;

    /**
     * 乐观锁版本：与同号明细行 JPA {@code version} 对齐；JSON 中显式输出 0。
     * 修订时映射领域 {@code expectedRowVersionForRevision}。
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Integer rowVersion;

    /**
     * 同号全部产品定义级明细行（含写模型数量/单价与读模型品名/unitBands enrich）。
     */
    private List<DeptPurchaseRequestDetailLineDto> lines;
}

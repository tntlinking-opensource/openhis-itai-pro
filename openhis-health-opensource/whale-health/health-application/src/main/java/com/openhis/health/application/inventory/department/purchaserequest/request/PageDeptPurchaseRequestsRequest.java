/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.purchaserequest.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 科室采购申请单分页列表请求（IF-DEP-CPR-02）。
 *
 * <p>继承 {@link AbstractHealthPageRequest}（B.10.11）；关键字（单号/备注）使用父类 {@code keyword}，
 * 经 {@link com.openhis.health.application.shared.pagination.PageQueryBinder#bind} 写入
 * {@link com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery}。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.department.purchaserequest.service.InventoryDepartmentPurchaseRequestAppService#pageDeptPurchaseRequests(PageDeptPurchaseRequestsRequest)}。</p>
 */
@Getter
@Setter
public class PageDeptPurchaseRequestsRequest extends AbstractHealthPageRequest {

    /**
     * 列表筛选：目的科室药房（必填），对应 {@code purpose_location_id}；
     * {@code 0} 表示当前用户可见全部科室药房（与 init 首项 {@code value="0"} /
     * {@link com.openhis.health.domain.share.constants.InventoryDepartmentConstants#ALL_DEPARTMENT_PHARMACIES_LIST_FILTER_LOCATION_ID} 对齐）。
     */
    private Long purposeLocationId;

    /**
     * 状态筛选：{@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()} 持久化字符串；可空表示不限状态。
     */
    private String statusEnum;

    /**
     * 申请时间起：可与父类 {@link #getStartDate()} 二选一，AppService 优先本字段。
     */
    private LocalDateTime applyTimeFrom;

    /**
     * 申请时间止：可与父类 {@link #getEndDate()} 二选一，AppService 优先本字段。
     */
    private LocalDateTime applyTimeTo;
}

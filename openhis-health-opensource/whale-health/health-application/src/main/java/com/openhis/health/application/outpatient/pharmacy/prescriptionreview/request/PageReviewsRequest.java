/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 分页查询点评记录请求（IF-PRV-02）。
 *
 * <p>分页与关键字继承父类：{@code keyword} 表示处方号或点评业务号模糊（OR）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "分页查询点评记录请求", requiredProperties = {"pageNo", "pageSize"})
public class PageReviewsRequest extends AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    /** 点评状态：PENDING/REVIEWED；空=不限（有效行全集）。 */
    @Schema(description = "点评状态 code")
    private String statusEnum;

    /** 点评日起（yyyy-MM-dd，闭区间日初）；空=不限。 */
    @Schema(description = "点评日起（yyyy-MM-dd）")
    private String reviewedDateFrom;

    /** 点评日止（yyyy-MM-dd，闭区间日末）；空=不限。 */
    @Schema(description = "点评日止（yyyy-MM-dd）")
    private String reviewedDateTo;

    /** 是否合理；空=不限。 */
    @Schema(description = "是否合理")
    private Boolean reasonableFlag;

    /** 点评人主键；空=不限。 */
    @Schema(description = "点评人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reviewerId;

    /** 开方日起（yyyy-MM-dd）；与止成对；空=不限。 */
    @Schema(description = "开方日起（yyyy-MM-dd）")
    private String openedDateFrom;

    /** 开方日止（yyyy-MM-dd）；与起成对；空=不限。 */
    @Schema(description = "开方日止（yyyy-MM-dd）")
    private String openedDateTo;

    /**
     * 重点药类型筛选（BASIC/NATIONAL_MONITOR/CENTRAL_PURCHASE/ANTIBIOTIC）；空=不限。
     * 麻/精见 {@link #medicinePropertyCode}。
     */
    @Schema(description = "重点药类型 KeyDrugType code：BASIC/NATIONAL_MONITOR/CENTRAL_PURCHASE/ANTIBIOTIC；空=不限")
    private String keyDrugType;

    /**
     * 药品性质筛选（NARCOTIC/PSYCH_ONE/PSYCH_TWO）；空=不限。与重点药类型同时非空时 AND。
     */
    @Schema(description = "药品性质 KeyDrugType code：NARCOTIC/PSYCH_ONE/PSYCH_TWO；空=不限")
    private String medicinePropertyCode;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.department.deptreturn.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class DeptReturnListRowDto {

    /** id 字段。 */
    private Long id;
    /** 业务单号。 */
    private String busNo;
    /** 来源/关联业务单号。 */
    private String originalBusNo;
    /** 单据类型持久化码。 */
    private Integer typeEnum;
    /** 单据状态持久化码。 */
    private Integer statusEnum;
    /** 供应类别持久化码。 */
    private Integer categoryEnum;
    /** 源库位主键。 */
    private Long sourceLocationId;
    /** 目的库位主键。 */
    private Long purposeLocationId;
    /** 供应商主键。 */
    private Long supplierId;
    /** 物品定义主键。 */
    private Long itemId;
    /** 物品来源表名。 */
    private String itemTable;
    /** 物品数量（拆零量）。 */
    private BigDecimal itemQuantity;
    /** 计量单位编码。 */
    private String unitCode;
    /** 批号。 */
    private String lotNumber;
    /** 追溯码（逗号分隔多码）。 */
    private String traceNo;
    /** 单价。 */
    private BigDecimal price;
    /** totalPrice 字段。 */
    private BigDecimal totalPrice;
    /** 申请时间。 */
    private LocalDateTime applyTime;
    /** 审批时间。 */
    private LocalDateTime approvalTime;
}

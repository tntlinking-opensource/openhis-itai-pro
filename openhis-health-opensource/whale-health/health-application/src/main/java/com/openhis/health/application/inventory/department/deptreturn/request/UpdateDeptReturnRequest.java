/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.department.deptreturn.request;

import com.openhis.health.application.inventory.department.deptreturn.dto.DeptReturnLineDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateDeptReturnRequest {

    /** 业务单号。 */
    private String busNo;
    /** 源库位主键。 */
    private Long sourceLocationId;
    /** 目的库位主键。 */
    private Long purposeLocationId;
    /** 源位置类型持久化码。 */
    private Integer sourceTypeEnum;
    /** 目的位置类型持久化码。 */
    private Integer purposeTypeEnum;
    /** 供应类别持久化码。 */
    private Integer categoryEnum;
    /** 供应商主键。 */
    private Long supplierId;
    /** 明细行列表。 */
    private List<DeptReturnLineDto> lines;
    /** 禁止传入；传入时应用层返回 DEP_RT_105。 */
    private Integer rowVersion;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.pharmacy.returnupstream.response;

import com.openhis.health.application.inventory.pharmacy.returnupstream.dto.ReturnUpstreamDetailLineDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GetReturnUpstreamDetailResponse {

    /** 业务单号。 */
    private String busNo;
    /** 来源/关联业务单号。 */
    private String originalBusNo;
    /** 单据类型持久化码。 */
    private Integer typeEnum;
    /** 单据状态持久化码。 */
    private Integer statusEnum;
    /** 供应商主键。 */
    private Long supplierId;
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
    /** 明细行列表。 */
    private List<ReturnUpstreamDetailLineDto> lines;
}

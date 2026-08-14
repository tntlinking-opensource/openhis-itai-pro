/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.PrepickedProductKeyDto;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-PRC-01：划价选品目录分页请求。
 *
 * <p>组套 IF-08 预览仅给结构；套用期以 {@link #prepickedProductKeys} 收窄本 IF，
 * 读侧返回 {@code unitPrice} 与耗材 {@code defaultDispenseLocationId}，写 IF-02/04 回传 {@code performLocation}。
 * 分页关键字走 {@link AbstractHealthPageRequest}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "护士站划价选品目录分页请求")
public class PageNurseStationPricingCatalogRequest extends AbstractHealthPageRequest {

    /**
     * 就诊主键（划价弹窗已选患者时传入；耗材库存按该就诊住院科室取药模板解析库位）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键（耗材选品按住院科室取药模板解析库位）")
    private Long encounterId;

    /**
     * 患者主键（与 encounterId 配套；可选，仅用于前端上下文透传）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键（可选）")
    private Long patientId;

    @Schema(description = "产品物理表集合；空则默认诊疗+耗材")
    /** 产品物理表集合；空则默认诊疗+耗材。 */
    private List<String> productTableNames;

    /**
     * 机构/科室单元：耗材选品优先取就诊 {@code organizationId}（入院科室），
     * 本字段作无就诊时的回落（须为住院科室，非登录人岗位管辖）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 机构单元（住院科室；耗材取药模板键）。 */
    @Schema(description = "机构/住院科室单元（取药模板键；无 encounter 时回落）")
    private Long organizationUnitId;

    /** 耗材库房筛选（仅耗材行生效，不做库存 Gate）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 耗材库房筛选。 */
    @Schema(description = "耗材库房筛选")
    private Long pharmacyLocationId;

    /** 关键字排序模式。 */
    @Schema(description = "关键字排序模式")
    private String sortMode;

    /** 预选产品键列表（快捷套用 ids 查目录）。 */
    @Schema(description = "预选产品键列表（快捷套用 ids 查目录）")
    private List<PrepickedProductKeyDto> prepickedProductKeys;

    /** 诊疗目录类别编码过滤。 */
    @Schema(description = "诊疗目录类别编码过滤")
    private String categoryCode;
}

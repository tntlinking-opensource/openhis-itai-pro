/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.PrepickedProductKeyDto;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-11：分页检索可开立医嘱项目目录请求。分页与关键字见 {@link AbstractPageQuery}（B.10.11）。
 *
 * <p>DOC-CONFLICT：AS-SDD §3.3 字段表未显式列出就诊/患者锚点，但「同就诊待发占用预减」（BR-ORD-011）在实现上必须绑定就诊；
 * 本请求补充 {@code encounterId}/{@code patientId} 作为预减与读侧门控输入。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-分页检索可开立医嘱项目目录请求")
public class PageInpatientDoctorStationAdviceCatalogRequest extends AbstractHealthPageRequest {

    /** 就诊标识（预减同就诊占用、读侧一致性）。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_ENCOUNTER_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊标识。 */
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者标识。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_PATIENT_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者标识。 */
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 机构单元解析入口。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 机构单元解析入口；为空则仅依赖当前登录科室。 */
    @Schema(description = "机构单元解析入口；为空则仅依赖当前登录科室")
    private Long organizationUnitId;

    /** 可选药房/库房 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 可选药房/库房 id，用于收窄库存维度。 */
    @Schema(description = "可选药房/库房 id，用于收窄库存维度")
    private Long pharmacyLocationId;

    /** 收窄 product_table_name 集合；空表示三表全量。 */
    @Schema(description = "收窄 product_table_name 集合；空表示三表全量")
    private List<String> productTableNames;

    /** 划价标记；为 1 时不返回药品行。 */
    @Schema(description = "划价标记；为 1 时不返回药品行")
    private Integer pricingFlag;

    /**
     * 组套/历史行 ids 收窄目录。与 {@link #keyword} OR 组合；价与 {@code defaultDispenseLocationId} 由本 IF 读侧返回。
     */
    @Schema(description = "预选产品键集合")
    private List<PrepickedProductKeyDto> prepickedProductKeys;

    @Schema(description = "关键字排序模式")
    /** 关键字排序模式。 */
    private String sortMode;

    /**
     * 诊疗目录类别过滤：申请单选品（21/22/23 或 TREATMENT/PROOF/TEST）；
     * 护理级别开立传 {@code 26}（ActivityDef category_code，IF-IP-DOR-11 · BR-IP-ORD-026）。
     */
    @Schema(description = "诊疗目录类别过滤：申请单 21/22/23 或护理级别 26")
    private String categoryEnum;

    /**
     * 排除药品目录 categoryCode（如开立西药医嘱排除中草药 {@code 4}）。
     */
    @Schema(description = "排除药品 medication_category_code 集合（如开立西药医嘱传 [\"4\"] 排除中草药）")
    private List<String> excludeMedicationCategoryCodes;

    /**
     * 仅返回可开立库存大于 0 的药/耗目录行（分页前 SQL 过滤）。
     * 过滤口径与目录「库存数量」一致：按开立科室取药模板库位（可选指定药房）收窄。
     * 诊疗定义无库存维度，不受本开关影响；{@code null}/{@code false} 时不过滤。
     */
    @Schema(description = "仅看库存>0（药/耗，按取药模板库位）；true 时分页前过滤，诊疗不受影响")
    private Boolean positiveQuantityOnly;
}

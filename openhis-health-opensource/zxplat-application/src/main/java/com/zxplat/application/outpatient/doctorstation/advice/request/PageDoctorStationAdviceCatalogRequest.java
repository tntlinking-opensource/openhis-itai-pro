/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.outpatient.doctorstation.advice.dto.PrepickedProductKeyDto;
import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-11：分页检索可开立医嘱项目目录请求。分页与关键字见 {@link AbstractPageQuery}（B.10.11）。
 *
 * <p>DOC-CONFLICT：AS-SDD §3.3 字段表未显式列出就诊/患者锚点，但「同就诊待发占用预减」（BR-ORD-011）在实现上必须绑定就诊；
 * 本请求补充 {@code encounterId}/{@code patientId} 作为预减与读侧门控输入。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-分页检索可开立医嘱项目目录请求")
public class PageDoctorStationAdviceCatalogRequest extends AbstractHealthPageRequest {

    /** 就诊标识（预减同就诊占用、读侧一致性）。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊标识。 */
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者标识。 */
    @NotNull(message = "患者标识不能为空")
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

    /**
     * 排除药品目录 categoryCode（如开立西药医嘱排除中草药 {@code 4}）。
     * 仅作用于 {@code med_medication_definition} 检索；与 {@link #categoryEnum} 无关。
     */
    @Schema(description = "排除药品 medication_category_code 集合（如开立西药医嘱传 [\"4\"] 排除中草药）")
    private List<String> excludeMedicationCategoryCodes;

    /**
     * 额外排除活动定义 {@code category_code}（与服务端恒定排除合并）。
     *
     * <p>院点可用来在「开立医嘱」选品中排除检验/检查/治疗（{@code 22}/{@code 23}/{@code 21}），
     * 迫使仅走申请单开立。申请单选品路径勿传本字段（与 {@link #categoryEnum} 自相矛盾）。</p>
     */
    @Schema(description = "额外排除诊疗 category_code（如 [\"21\",\"22\",\"23\"]）；与嘱托/体检等服务端恒定排除合并")
    private List<String> excludeCategoryCodes;

    /** 划价标记；为 1 时不返回药品行。 */
    @Schema(description = "划价标记；为 1 时不返回药品行")
    private Integer pricingFlag;

    /**
     * 组套/历史医嘱/处方行 ids 收窄目录（与 IF-08 预览结构配合）。
     * 与 {@link #keyword} OR 组合；价与 {@code defaultDispenseLocationId} 由本 IF 读侧返回。
     */
    @Schema(description = "预选产品键集合")
    private List<PrepickedProductKeyDto> prepickedProductKeys;

    @Schema(description = "关键字排序模式")
    /** 关键字排序模式。 */
    private String sortMode;

    /** 申请单选品类别过滤（检查 TEST/23、检验 PROOF/22、治疗 TREATMENT/21）。 */
    @Schema(description = "申请单选品类别过滤（检查 TEST/23、检验 PROOF/22、治疗 TREATMENT/21）")
    private String categoryEnum;

    /**
     * 诊疗业务类型（{@code type_enum} / 术语 {@code activity_type_code}）。
     * 申请单「分类」chips 筛选；空表示不按业务类型收窄。码值与存量数字字符串一致（如 {@code 2}=检查）。
     */
    @Schema(description = "诊疗业务类型 type_enum；申请单分类 chips；空=不限")
    private String typeEnum;

    /**
     * 仅返回可开立库存大于 0 的药/耗目录行（分页前 SQL 过滤，避免前端筛当前页导致条数不准）。
     * 过滤口径与目录「库存数量」一致：按开立科室取药模板库位（可选指定药房）收窄，不计药库等非候选库位账面。
     * 诊疗定义无库存维度，不受本开关影响；{@code null}/{@code false} 时不过滤。
     */
    @Schema(description = "仅看库存>0（药/耗，按取药模板库位）；true 时分页前过滤，诊疗不受影响")
    private Boolean positiveQuantityOnly;
}

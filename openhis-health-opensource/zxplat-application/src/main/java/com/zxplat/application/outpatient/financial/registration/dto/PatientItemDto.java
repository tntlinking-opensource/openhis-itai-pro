/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 挂号患者检索列表行（首诊建档/选患者弹窗；含脱敏证件与联系方式）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "患者列表项")
public class PatientItemDto {

    /** 患者主键 {@code adm_patient.id}。 */
    @Schema(description = "患者ID")
    private Long patientId;

    /** 患者业务号（院内唯一标识，供检索与建档关联）。 */
    @Schema(description = "患者业务号")
    private String patientBusNo;

    /** 患者姓名（读模型展示）。 */
    @Schema(description = "姓名")
    private String name;

    /** 性别编码：与 {@link com.zxplat.domain.share.enums.AdministrativeGender#getCode()} 一致。 */
    @Schema(description = "性别编码")
    private String genderCode;

    /** 性别中文展示文案。 */
    @Schema(description = "性别展示文案")
    private String genderText;

    /** 出生日期（主档；用于年龄推算）。 */
    @Schema(description = "出生日期")
    private LocalDate birthDate;

    /**
     * 年龄（周岁，由出生日期推算；出生日期缺失时为 {@code null}）。
     * <p>与患者主数据分页列表提示口径一致，仅供界面展示。</p>
     */
    @Schema(description = "年龄（周岁）")
    private Integer age;

    /** 脱敏后的证件号码，仅供列表展示。 */
    @Schema(description = "脱敏证件号")
    private String idCardNoMasked;

    /** 脱敏后的联系电话，仅供列表展示。 */
    @Schema(description = "脱敏手机号")
    private String phoneMasked;
}

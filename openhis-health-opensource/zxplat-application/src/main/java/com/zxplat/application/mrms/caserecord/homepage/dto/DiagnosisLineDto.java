/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.caserecord.homepage.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

/**
 * 病案首页诊断行出入参 DTO（编辑页 / saveHomepage / 结算清单回显共用）。
 *
 * <p>国临码存 {@code diagCode}/{@code diagName}；医保码名不落本表，由目录对照解析。
 * 出院手填列见 {@code confirm*} / {@code needYbUp} / {@code outIllStatusCode}。</p>
 */
@Getter
@Setter
public class DiagnosisLineDto {

    /** 行主键（字符串出站，防前端雪花 Long 精度丢失）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 诊断类型码（{@code HomepageDiagType}：admission_* / discharge_* / pathology / injury_poison）。 */
    private String diagTypeCode;

    /** 全表排序号（1 起；入院→出院→特殊 稳定顺序）。 */
    private Integer sortNo;

    /** 诊断编码（西医国临 / 中医目录码；院内落库真源）。 */
    private String diagCode;

    /** 诊断名称（与 diagCode 同源目录名）。 */
    private String diagName;

    /**
     * 目录来源（不落库；加载时按就诊开立目录或编码反查）。
     * {@code tcm_dx} 中医疾病目录，{@code tcm_syndrome} 中医证候目录，其余为西医目录。
     */
    private String sourceEnum;

    /** 入院病情字典码（4401 adm_cond：1有 2临床未确定 3情况不明 4无）。 */
    private String admCondCode;

    /**
     * 诊断转归/病情码（结算清单列「病情」；对齐老 outIllStatus：
     * 1治愈 2好转 3未愈 4死亡 9其他）。
     */
    private String outIllStatusCode;

    /** 确诊时间（结算清单回显；手填，对齐老 confirmDate）。 */
    private java.time.LocalDateTime confirmTime;

    /** 说明（结算清单回显；手填自由文本，对齐老 diagnoseRemark）。 */
    private String diagRemark;

    /**
     * 诊断医生主键（出院诊断下拉；对齐老 confirmDoctorId；字符串出站）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long confirmDrId;

    /** 诊断医生姓名（下拉选中后冗余；对齐老 confirmDoctor 展示名）。 */
    private String confirmDrName;

    /**
     * 是否上传医保（1是 0否；对齐老 needYbUp；出院行缺省按 1 回显）。
     */
    private String needYbUp;

    /** 主诊断标记（4401 maindiag_flag，1/0）。 */
    private String maindiagFlag;

    /** 有效标记（4401 vali_flag；保存路径领域写 1）。 */
    private String valiFlag;
}

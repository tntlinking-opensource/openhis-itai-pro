/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.conditiondefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 病案首页/结算清单：由落库码或目录定义解析后的国临+医保展示口径。
 *
 * <p>西医国临：{@link #clinicCode}/{@link #clinicName} 有值，医保列来自对照；
 * 西医医保：{@link #ybCode}/{@link #ybName} 有值；有国临绑码才填国临列，
 * <strong>无对照时国临列必须空</strong>（禁止把医保码塞进国临列）；
 * 中医：无国临/医保双码概念，{@link #clinicCode}/{@link #clinicName} 直接落目录码/名。</p>
 */
@Getter
@Setter
public class ClinicalDiagnosisPresentationQueryModel {

    /** 中西医维度 {@code WESTERN}/{@code TCM}。 */
    private String diagnosisKind;

    /** 主列诊断编码：西医为国临码；中医为目录编码（正常显示，非空）。 */
    private String clinicCode;

    /** 主列诊断名称：西医为国临名；中医为目录名称（正常显示）。 */
    private String clinicName;

    /** 国临目录（clinical_v2）主键；西医未对码时结算清单「对码」写回用；中医恒空。 */
    private Long clinicalDefinitionId;

    /**
     * 医保码：西医为目录对照或落库医保码；中医预览不展示双码，但可冗余同值供上传出站解析。
     */
    private String ybCode;

    /** 医保名称：西医为目录对照名；中医可冗余目录名供出站。 */
    private String ybName;
}

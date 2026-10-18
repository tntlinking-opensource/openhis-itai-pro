/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.medcas.dto;

import lombok.Data;

import java.util.List;

/**
 * 住院病案首页上传载荷（4401 语义，JSON 字段与医保平台对齐）。
 *
 * <p>职责：聚合 baseinfo 与诊断/手术/ICU 子表，供 CHS adapter 序列化为平台 input。</p>
 */
@Data
public class MedcasHomepagePayload {

    /** 病案首页基本信息节点。 */
    private MedcasHomepageBaseInfo baseinfo;

    /** 诊断信息列表。 */
    private List<MedcasHomepageDiseInfo> diseinfo;

    /** 手术信息列表。 */
    private List<MedcasHomepageOprnInfo> oprninfo;

    /** ICU 信息列表。 */
    private List<MedcasHomepageIcuInfo> icuinfo;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.medcas.dto;

import lombok.Data;

import java.util.List;

/**
 * 住院病案首页上传载荷（4401 语义，JSON 字段与医保平台对齐）。
 */
@Data
public class MedcasHomepagePayload {

    private MedcasHomepageBaseInfo baseinfo;

    private List<MedcasHomepageDiseInfo> diseinfo;

    private List<MedcasHomepageOprnInfo> oprninfo;

    private List<MedcasHomepageIcuInfo> icuinfo;
}

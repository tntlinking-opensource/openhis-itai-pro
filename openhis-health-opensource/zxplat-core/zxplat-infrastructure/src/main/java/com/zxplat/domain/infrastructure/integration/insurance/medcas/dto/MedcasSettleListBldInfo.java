/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.medcas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 【4101A】bldinfo 输血行。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MedcasSettleListBldInfo {

    /** 输血品种（字典 {@code bld_cat_code}）。 */
    @JsonProperty("bld_cat")
    private String bldCat;

    /** 输血量（文本；平台侧按数值契约校验）。 */
    @JsonProperty("bld_amt")
    private String bldAmt;

    /** 输血计量单位（如 ml、U）。 */
    @JsonProperty("bld_unt")
    private String bldUnt;
}

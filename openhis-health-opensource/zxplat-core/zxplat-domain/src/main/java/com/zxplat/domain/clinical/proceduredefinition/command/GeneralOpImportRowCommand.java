/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.proceduredefinition.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 医保「手术与操作」目录导入行（1308 → {@code general_op}）。
 */
@Getter
@Setter
public class GeneralOpImportRowCommand {

    /** 医保手术操作代码（1308 第 11 列 / hilist_code）。 */
    private String procedureCode;

    /** 手术操作名称（1308 第 12 列）。 */
    private String name;

    /** 拼音助记（可空）。 */
    private String pyStr;

    /**
     * 1308 第 16 列临床版手术操作代码。
     * 有值才绑定国临；空或平台占位 {@code null}/{@code --} 时不补建国临、不把第 11 列医保码写成国临码。
     */
    private String fileClinicCode;

    /** 1308 第 17 列临床版名称；仅在第 16 列有码且要补建国临时使用，空则用 {@link #name}。 */
    private String fileClinicName;

    /**
     * 本行要写入医保目录的国临码（仅第 16 列有临床版码时填写）。
     * 写入时遇已有不同手工对照则不覆盖。
     */
    private String bindClinicCode;
}

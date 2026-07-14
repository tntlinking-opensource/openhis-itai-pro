/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.RoundingMode;

/**
 * 标注导出 DTO 字段在 Excel 工作表中的列名、顺序与格式化规则。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Excel {

    /** 列标题。 */
    String name();

    /** 列序（升序）；未标注字段不参与导出。 */
    int sort() default Integer.MAX_VALUE;

    /** {@link java.math.BigDecimal} 导出小数位；{@code -1} 表示按原值字符串写出。 */
    int scale() default -1;

    /** {@link #scale()} 启用时的舍入模式。 */
    RoundingMode roundingMode() default RoundingMode.HALF_UP;

    /** 单元格值为空时的回落文本。 */
    String defaultValue() default "";

    /** 是否参与导出。 */
    boolean export() default true;
}

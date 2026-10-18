/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.RoundingMode;

/**
 * 标注导出 DTO 字段在 Excel 工作表中的列名、顺序与格式化规则。
 *
 * <p>数值字段须用 {@link java.math.BigDecimal}/{@link Number} 类型并由 {@code AnnotatedExcelExporter}
 * 写成数字单元格；刻度见 monetary-amount-contract §1.1（数量 0、金额展示 2）。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Excel {

    /** 列标题。 */
    String name();

    /** 列序（升序）；未标注字段不参与导出。 */
    int sort() default Integer.MAX_VALUE;

    /**
     * {@link java.math.BigDecimal}/{@link Number} 导出小数位。
     *
     * <p>{@code >= 0}：先按该位舍入，再写入<strong>数字</strong>单元格并套显示格式
     * （数量常用 {@code 0}；金额常用 health {@code CHARGE_MONETARY_DISPLAY_SCALE=2}）；
     * {@code -1}：不强制小数位，仍写入数字单元格（非文本，便于 Excel 求和）。</p>
     *
     * <p>禁止把金额先 {@code toPlainString()} 再以 String 字段导出。</p>
     */
    int scale() default -1;

    /** {@link #scale()} 启用时的舍入模式。 */
    RoundingMode roundingMode() default RoundingMode.HALF_UP;

    /** 单元格值为空时的回落文本。 */
    String defaultValue() default "";

    /** 是否参与导出。 */
    boolean export() default true;
}

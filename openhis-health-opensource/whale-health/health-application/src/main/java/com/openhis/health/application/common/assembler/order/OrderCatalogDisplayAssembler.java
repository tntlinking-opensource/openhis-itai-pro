/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.common.assembler.order;

import com.openhis.health.application.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * 门诊医嘱/处置/发药行：目录与术语展示字段的纯装配辅助（无 I/O，B.6.1 {@code common.assembler.order}）。
 *
 * <p><b>术语列</b>：调用方须先用 {@link TerminologyOptionLoader#buildLabelMap} 按编码体系预热映射，
 * 再使用 {@link #resolveCategoryDisplay} 将持久化 {@code code} 转为展示名；禁止在 Mapper/AppService 内
 * 再写私有 {@code pickLabel}。</p>
 *
 * <p><b>目录列</b>：{@link #resolveItemDisplayName}、{@link #resolveItemCatalogCode}、
 * {@link #resolveMedicationSpecText} 与定义表字段对齐，定义缺失时由调用方传入占位符（常量在
 * {@code domain.share.constants}）。</p>
 */
public final class OrderCatalogDisplayAssembler {

    /**
     * 工具类禁止实例化。
     */
    private OrderCatalogDisplayAssembler() {
    }

    /**
     * 目录定义展示名：优先定义表名称，缺失时由调用方传入的占位符兜底。
     *
     * @param definitionName 定义表 {@code name}
     * @param fallback       占位展示名（如护士站处置单占位常量）
     * @return 非空展示名
     */
    public static String resolveItemDisplayName(String definitionName, String fallback) {
        if (StringUtils.isNotBlank(definitionName)) {
            return definitionName.trim();
        }
        // 定义表无名称时回落模块占位（如护士站「诊疗项目」），避免列表空白
        return fallback;
    }

    /**
     * 目录业务编码：优先定义表 {@code bus_no}，缺失时回落为 id 字符串。
     *
     * @param busNo        定义业务编码
     * @param definitionId 定义主键
     * @return 目录编码；二者皆空时为 {@code null}
     */
    public static String resolveItemCatalogCode(String busNo, Long definitionId) {
        if (StringUtils.isNotBlank(busNo)) {
            return busNo.trim();
        }
        if (definitionId != null) {
            return String.valueOf(definitionId);
        }
        return null;
    }

    /**
     * 药品规格展示：总量或成分描述（与医生站医嘱列表同源）。
     *
     * @param medicationDefinition 药品定义；允许 null
     * @return 规格文本；无则为 {@code null}
     */
    public static String resolveMedicationSpecText(MedicationDefinition medicationDefinition) {
        if (medicationDefinition == null) {
            return null;
        }
        if (StringUtils.isNotBlank(medicationDefinition.getTotalVolume())) {
            return medicationDefinition.getTotalVolume().trim();
        }
        if (StringUtils.isNotBlank(medicationDefinition.getComprisedText())) {
            return medicationDefinition.getComprisedText().trim();
        }
        return null;
    }

    /**
     * 从预构建术语映射中取类目/单位/频次等展示名（码值 trim 后查找）。
     *
     * <p>与 {@link TerminologyOptionLoader#buildLabelMap} 配对使用；空映射或空码时返回 {@code null}。</p>
     *
     * @param labelByCode 码 → 展示名（由 {@link TerminologyOptionLoader#buildLabelMap} 预热）
     * @param code        持久化业务码；可为 null 或空白
     * @return 展示名；未命中时为 {@code null}
     */
    public static String resolveCategoryDisplay(Map<String, String> labelByCode, String code) {
        if (labelByCode == null || StringUtils.isBlank(code)) {
            return null;
        }
        return labelByCode.get(code.trim());
    }
}

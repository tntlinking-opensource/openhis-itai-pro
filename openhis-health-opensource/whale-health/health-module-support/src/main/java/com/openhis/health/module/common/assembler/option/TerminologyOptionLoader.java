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

package com.openhis.health.module.common.assembler.option;

import com.openhis.health.domain.share.utils.OptionDtoLegacyRowUtil;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 术语下拉与列表行展示映射的统一入口（B.6.1 {@code common.assembler.option}）。
 *
 * <p><b>选用指引</b>（禁止在 AppService 内再私有实现同构 {@code getOptionListByCodeSystemName → Map}）：</p>
 * <ul>
 *   <li>{@link #loadOptions(String)} / {@link #loadLegacyRows(String)}：页面初始化、筛选条件下拉，需要完整
 *       {@link OptionDto}（value/label）列表时；</li>
 *   <li>{@link #buildLabelMap(String)}：列表、发药单、处置单等<b>批量</b>把持久化 {@code code} 转成展示名时——
 *       先对本编码体系预热「码 → 文案」映射，再配合
 *       {@link com.openhis.health.module.common.assembler.order.OrderCatalogDisplayAssembler#resolveCategoryDisplay}
 *       逐行解析。</li>
 * </ul>
 *
 * <p>业务枚举展示名仍走 {@code EnumUtil}（B.10.8.1）；本类仅负责术语编码体系（字典）侧。</p>
 */
@Component
@RequiredArgsConstructor
public class TerminologyOptionLoader {

    private final CodeSystemQueryService codeSystemQueryService;

    /**
     * 按 codeSystemName 获取标准 {@link OptionDto} 列表。
     *
     * <p>用于下拉框、页面 init 等需要完整选项结构的场景；列表行批量翻译请用 {@link #buildLabelMap(String)}。</p>
     *
     * @param codeSystemName 术语编码体系名称（见 {@code TerminologyCodeSystemNameConstants}）
     * @return 下拉选项
     */
    public List<OptionDto> loadOptions(String codeSystemName) {
        return codeSystemQueryService.getOptionListByCodeSystemName(codeSystemName);
    }

    /**
     * 按 codeSystemName 获取旧前端兼容的 label/value 行。
     *
     * @param codeSystemName 术语编码体系名称
     * @return 旧版行结构
     */
    public List<Map<String, Object>> loadLegacyRows(String codeSystemName) {
        return OptionDtoLegacyRowUtil.toLabelValueRows(loadOptions(codeSystemName));
    }

    /**
     * 将编码体系下全部概念转为「码 → 展示名」映射，供列表行批量解析类目/单位/频次等展示列。
     *
     * <p>持久化列存的是术语 {@code value}（字符串 code）；展示列通过
     * {@link com.openhis.health.module.common.assembler.order.OrderCatalogDisplayAssembler#resolveCategoryDisplay}
     * 查表，未命中时为 {@code null}（由调用方决定是否回落展示 code 本身）。</p>
     *
     * @param codeSystemName 编码体系名称；空白时返回空映射
     * @return 码到展示名
     */
    public Map<String, String> buildLabelMap(String codeSystemName) {
        Map<String, String> labelByCode = new HashMap<>();
        if (StringUtils.isBlank(codeSystemName)) {
            return labelByCode;
        }
        List<OptionDto> options = loadOptions(codeSystemName.trim());
        for (OptionDto option : options) {
            if (option.getValue() != null) {
                labelByCode.put(option.getValue(), option.getLabel());
            }
        }
        return labelByCode;
    }
}

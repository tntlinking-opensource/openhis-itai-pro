/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.weistat.support;

import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 省市区 codes 出站拼接（卫统/HQMS 地址类字段回落共用）。
 *
 * <p>库列真源为斜杠拼接纯字符串；地名中文由首页 {@code *_name} 列承载（前端写入）。
 * <strong>禁止</strong>在 application 内嵌全国 level.json。
 * 本组件只把 API 侧 code 列表拼成无英文逗号文本（{@code /} 连接），避免 CSV/Excel 拆列。</p>
 */
@Component
public class WeistatRegionNameResolver {

    /**
     * 将省市区 code 列表拼成无 ASCII 逗号的文本。
     *
     * @param codes 如 {@code [220000, 220100, 220102]}
     * @return 如 {@code 220000/220100/220102}；空入参→空串
     */
    public String toPlaceText(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String token : flattenCodes(codes)) {
            if (out.length() > 0) {
                out.append('/');
            }
            out.append(token);
        }
        return out.toString();
    }

    /**
     * 展开 codes：单段内若含逗号/斜杠/顿号则再拆。
     *
     * @param codes 原始列表
     * @return 扁平 code 列表
     */
    private List<String> flattenCodes(List<String> codes) {
        List<String> flat = new ArrayList<>();
        for (String raw : codes) {
            String piece = StringUtils.trimToNull(raw);
            if (piece == null) {
                continue;
            }
            String[] parts = piece.split("[,;/、\\s]+");
            for (String part : parts) {
                String c = StringUtils.trimToNull(part);
                if (c != null) {
                    flat.add(c);
                }
            }
        }
        return flat;
    }
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.platformdict.model;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【1901】字典查询 list 行元素 */
@Data
@Accessors(chain = true)
public class Dict1901Row {

    private String value;

    @JSONField(name = "dict_value")
    private String dictValue;

    private String admdvs;

    private String code;

    private String label;

    @JSONField(name = "dict_label")
    private String dictLabel;

    @JSONField(name = "admdvs_name")
    private String admdvsName;

    private String name;

    @JSONField(name = "parentValue")
    private String parentValue;

    @JSONField(name = "parent_value")
    private String parentValueSnake;

    @JSONField(name = "Vali_Flag")
    private String valiFlagSpec;

    @JSONField(name = "vali_flag")
    private String valiFlag;

    private Integer sort;

    @JSONField(name = "sort_order")
    private Integer sortOrder;

    private Long version;

    @JSONField(name = "platform_version")
    private Long platformVersion;

    public static Dict1901Row fromJSONObject(JSONObject jo) {
        if (jo == null) {
            return null;
        }
        return jo.to(Dict1901Row.class);
    }

    public String resolveValue() {
        return firstNonBlank(value, dictValue, admdvs, code);
    }

    public String resolveLabel() {
        return firstNonBlank(label, dictLabel, admdvsName, name);
    }

    public String resolveParentValue() {
        return firstNonBlank(parentValue, parentValueSnake);
    }

    public String resolveValiFlag() {
        return firstNonBlank(valiFlagSpec, valiFlag);
    }

    public Integer resolveSortOrder() {
        if (sort != null) {
            return sort;
        }
        return sortOrder;
    }

    public Long resolvePlatformVersion() {
        if (version != null) {
            return version;
        }
        return platformVersion;
    }

    public boolean hasValueAndLabel() {
        return isNotEmpty(resolveValue()) && isNotEmpty(resolveLabel());
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (isNotEmpty(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }
}

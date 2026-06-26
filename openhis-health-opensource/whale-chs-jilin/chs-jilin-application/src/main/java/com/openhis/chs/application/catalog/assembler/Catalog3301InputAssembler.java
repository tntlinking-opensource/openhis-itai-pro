/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.assembler;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.domain.catalog.dto.request.UploadCatalog3301RowRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 【3301】目录对照上传入参组装（fixmedins_hilist → med_list_codg 映射）。 */
@Component
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class Catalog3301InputAssembler {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * 组装 3301 批量上传 input.data 数组。
     *
     * @param rows 院内目录行（含 list_type、med_list_codg、begndate/enddate）
     * @return 平台 input JSON（{@code {data:[...]}}）
     */
    public JSONObject assemble(List<UploadCatalog3301RowRequest> rows) {
        JSONArray data = new JSONArray();
        for (UploadCatalog3301RowRequest row : rows) {
            JSONObject item = new JSONObject();
            item.put("fixmedins_hilist_id", row.getFixmedinsHilistId());
            item.put("fixmedins_hilist_name", row.getFixmedinsHilistName());
            item.put("list_type", row.getListType());
            item.put("med_list_codg", row.getMedListCodg());
            item.put("begndate", resolveBegndate(row.getBegndate()));
            item.put("enddate", resolveEnddate(row.getEnddate()));
            if (StringUtils.isNotBlank(row.getAprvno())) {
                item.put("aprvno", row.getAprvno().trim());
            }
            if (StringUtils.isNotBlank(row.getSpec())) {
                item.put("spec", row.getSpec().trim());
            }
            if (StringUtils.isNotBlank(row.getDosform())) {
                item.put("dosform", row.getDosform().trim());
            }
            data.add(item);
        }
        JSONObject root = new JSONObject();
        root.put("data", data);
        return root;
    }

    private static String resolveBegndate(String value) {
        if (StringUtils.isNotBlank(value)) {
            return value.trim();
        }
        return LocalDate.now().format(DATE_FMT);
    }

    private static String resolveEnddate(String value) {
        if (StringUtils.isNotBlank(value)) {
            return value.trim();
        }
        return "2099-12-31";
    }
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.specimentubemerge.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 保存设备合管绑定请求（全量替换设备下全部组）。
 */
@Data
public class SaveBindingsRequest {

    /** 检验设备主键 {@code adm_device.id}，字符串形式兼容前端 Long。 */
    private String deviceId;

    /** 合管组列表；空列表表示清空该设备全部绑定。 */
    private List<GroupBindingsRequest> groups = new ArrayList<>();

    /**
     * 单个合管组保存载荷。
     */
    @Data
    public static class GroupBindingsRequest {

        /**
         * 设备内合管组序号（从 1 起）。
         *
         * <p>可空：服务端按提交顺序从 1 重排；前端有序号时原样保留。</p>
         */
        private Integer groupNo;

        /** 绑定的检验项目 activity 主键列表（不含采血费）。 */
        private List<String> activityIds = new ArrayList<>();

        /** 绑定的耗材定义行。 */
        private List<ConsumableLineRequest> consumableLines = new ArrayList<>();

        /** 是否额外绑定采血费 activity（名称精确匹配「采血费」）。 */
        private Boolean bindBloodCollectionFee;
    }

    /**
     * 耗材绑定行。
     */
    @Data
    public static class ConsumableLineRequest {

        /** 耗材定义主键 {@code adm_device_definition.id}。 */
        private String targetId;

        /** 合管用量，须大于 0。 */
        private BigDecimal quantity;

        /** 计量单位 code（术语 unit_code）。 */
        private String unitCode;

        /** 展示排序；未传时由服务端按提交顺序递增。 */
        private Integer displayOrder;
    }
}

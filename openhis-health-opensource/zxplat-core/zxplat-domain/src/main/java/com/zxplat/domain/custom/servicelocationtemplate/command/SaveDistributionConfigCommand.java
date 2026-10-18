/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.servicelocationtemplate.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * 整科覆盖保存发药位置规则命令。
 */
@Getter
@Setter
public class SaveDistributionConfigCommand extends BatchCommand<SaveDistributionConfigCommand.DistributionRuleItemCommand> {

    /**
     * 开立科室标识。
     */
    private Long organizationId;

    /**
     * 操作人标识。
     */
    private Long operatorId;

    /**
     * 配置行子命令。
     */
    @Getter
    @Setter
    public static class DistributionRuleItemCommand {
        /** 物品来源表（药品/耗材定义表名）。 */
        private String itemTable;
        /** 发药类别字典码（西药/中药/耗材等分流）。 */
        private String distributionCategoryCode;
        /** 默认发药位置 {@code adm_location.id}。 */
        private Long defLocationId;
        /** 物品定义主键（细粒度规则；与类别规则二选一或叠加）。 */
        private Long itemDefinitionId;
        /**
         * 耗材种类编码（术语 {@code device_type_code}）；空为通配。
         * 药品行通常为空。
         */
        private String typeCode;
        /** 发药方式/给药途径字典码；空为通配。 */
        private String methodCode;
        /** 规则生效日始时刻（闭区间左端，可空表示全天）。 */
        private LocalTime startTime;
        /** 规则生效日止时刻（闭区间右端，可空表示全天）。 */
        private LocalTime endTime;
        /** 同科室多条规则时的匹配优先级（数值越小越优先）。 */
        private Integer priority;
        /** 维护页展示排序号。 */
        private Integer displayOrder;
        /** 规则备注（不落库到业务单据）。 */
        private String remark;
    }
}

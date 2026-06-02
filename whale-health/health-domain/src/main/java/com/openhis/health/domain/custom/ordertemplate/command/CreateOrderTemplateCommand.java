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

package com.openhis.health.domain.custom.ordertemplate.command;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.enums.OrderTemplateScene;

/**
 * 创建医嘱组套模板命令。
 */
@Getter
@Setter
public class CreateOrderTemplateCommand {
    /**
     * 归属对象标识（语义由 bindingEnum 决定）。
     */
    private Long objectId;
    /**
     * 绑定类型枚举值（个人/科室/全院）。
     */
    private String bindingEnum;
    /**
     * 适用场景枚举值（门诊/住院/通用）。
     */
    private OrderTemplateScene sceneEnum;
    /**
     * 模板编码（可空；非空时按归属键范围唯一）。
     */
    private String templateCode;
    /**
     * 模板名称。
     */
    private String name;
    /**
     * 临床说明/适应症提示等描述（可空）。
     */
    private String description;
    /**
     * 列表排序权重（同层级内：数值越小越靠前；默认 0）。
     */
    private Integer sortWeight;
    /**
     * 备注（可空）。
     */
    private String remark;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}

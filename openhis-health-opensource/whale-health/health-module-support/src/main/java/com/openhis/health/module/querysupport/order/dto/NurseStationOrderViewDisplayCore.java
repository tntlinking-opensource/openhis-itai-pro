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

package com.openhis.health.module.querysupport.order.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 住院护士站医嘱查看「行展示内核」：药/诊共用字段（非 HTTP 契约，供各业务 Assembler 拷贝）。
 */
@Data
@Accessors(chain = true)
public class NurseStationOrderViewDisplayCore {

    /** 稳定行键：requestId + SOH + orderType。 */
    private String listRowKey;

    /** 药品/诊疗申请主键。 */
    private Long requestId;

    /** 医嘱类型：MED_REQUEST / SERVICE_REQUEST。 */
    private String orderType;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 请求状态 code。 */
    private String statusEnum;

    /** 请求状态人读文案。 */
    private String statusDisplay;

    /** 长临 code。 */
    private String therapyEnum;

    /** 长临人读文案。 */
    private String therapyDisplay;

    /** 诊疗类目 code（仅诊嘱）。 */
    private String categoryEnum;

    /** 医嘱名称摘要。 */
    private String adviceDisplayName;

    /** 规格/大小；诊疗可为 null。 */
    private String specificationText;

    /** 剂量/数量展示。 */
    private String dosageDisplay;

    /** 用法展示（药品）。 */
    private String usageDisplay;

    /** 频次展示。 */
    private String frequencyDisplay;

    /** 开立时间。 */
    private LocalDateTime authoredTime;

    /** 停嘱/结束时间。 */
    private LocalDateTime stopTime;

    /** 开立人姓名。 */
    private String requesterName;

    /** 执行/发药库位展示。 */
    private String performerLocationDisplay;

    /** 成组号（药品，与医生站 group_id 同源）。 */
    private Long groupId;

    /** 成组号 int 形态（≤ Integer.MAX_VALUE 时供校对列表排序）。 */
    private Integer groupNo;

    /** 组内排序号。 */
    private Integer sortNumber;
}

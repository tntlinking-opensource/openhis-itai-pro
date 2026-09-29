/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 服务申请单条加载查询模型。
 *
 * <p>含手术/麻醉申请横表计划字段（AS-IP-DOR-012 IF-IP-SUR-15）；禁止 JSON 兜首页字段。</p>
 */
@Getter
@Setter
public class ServiceRequestQueryModel {
    /** 主键。 */
    private Long id;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 类别枚举编码。 */
    private String categoryEnum;
    /** 申请单号。 */
    private String prescriptionNo;
    /** 主手术标志。 */
    private Boolean mainProcedureFlag;
    /** 手术级别字典码。 */
    private String procedureLevelCode;
    /** 切口类别字典码。 */
    private String incisionTypeCode;
    /** 麻醉方法字典码。 */
    private String anesthesiaMethodCode;
    /** 手术时间（申请可填）。 */
    private LocalDateTime procedureTime;
    /** 备注。 */
    private String remark;
    /** 主刀医师。 */
    private Long surgeonId;
    /** 一助。 */
    private Long firstAssistantId;
    /** 二助。 */
    private Long secondAssistantId;
    /** 配合护士。 */
    private Long nurseId;
    /** 麻醉医师。 */
    private Long anesthetistId;
    /** 嵌套麻醉申请单号。 */
    private String nestedAnesthesiaPrescriptionNo;
}

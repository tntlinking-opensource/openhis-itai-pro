/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.print.printlog.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.core.domain.IMultiTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 打印审计日志聚合根（DM-PRT-002 · PrintLog，append-only）。
 *
 * <p>对应表 {@code prt_print_event_log}；仅 INSERT，禁止 UPDATE/DELETE API。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "prt_print_event_log")
public class PrtPrintEventLog extends BaseAuditedEntity implements IMultiTenant {

    /** 机构主键。 */
    @Column(name = "organization_id")
    private Long organizationId;

    /** 打印链路 traceId，幂等关联键。 */
    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    /** 使用的模板 busNo。 */
    @Column(name = "bus_no", nullable = false, length = 64)
    private String busNo;

    /** 场景入口 bindingCode。 */
    @Column(name = "binding_code", length = 64)
    private String bindingCode;

    /** 打印时模板 version 快照。 */
    @Column(name = "template_version")
    private Integer templateVersion;

    /** 打印结果状态（{@link com.openhis.health.domain.share.enums.PrintLogStatus}）。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 打印机名称。 */
    @Column(name = "printer_name", length = 128)
    private String printerName;

    /** 打印份数。 */
    @Column(name = "copies")
    private Integer copies;

    /** 打印内容哈希，用于审计对账。 */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /** 失败错误码。 */
    @Column(name = "error_code", length = 64)
    private String errorCode;

    /** 失败错误信息。 */
    @Column(name = "error_message", length = 512)
    private String errorMessage;

    /** 业务引用 JSON（如单据 id 列表）。 */
    @Column(name = "biz_ref_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String bizRefJson;

    /** 打印客户端类型。 */
    @Column(name = "client_type", length = 32)
    private String clientType;

    /** 操作人主键。 */
    @Column(name = "operator_id")
    private Long operatorId;

    /** 操作人姓名快照。 */
    @Column(name = "operator_name", length = 128)
    private String operatorName;
}

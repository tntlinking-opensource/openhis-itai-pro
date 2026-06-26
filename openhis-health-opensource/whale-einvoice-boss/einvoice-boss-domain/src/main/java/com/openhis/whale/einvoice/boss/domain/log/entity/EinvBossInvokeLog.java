package com.openhis.whale.einvoice.boss.domain.log.entity;

import com.openhis.whale.einvoice.boss.domain.support.EinvBossLogRowEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 博思 HTTP 调用日志（einv_boss_invoke_log）。
 *
 * <p>仅存请求/响应摘要，不含完整报文。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "einv_boss_invoke_log")
public class EinvBossInvokeLog extends EinvBossLogRowEntity {

    /** 关联开票任务主键；无任务时为空。 */
    @Column(name = "task_id")
    private Long taskId;

    /** 博思 serviceId。 */
    @Column(name = "service_id", nullable = false, length = 50)
    private String serviceId;

    /** 业务流水号。 */
    @Column(name = "bus_no", length = 50)
    private String busNo;

    /** 博思返回码。 */
    @Column(name = "result_code", length = 20)
    private String resultCode;

    /** 调用耗时（毫秒）。 */
    @Column(name = "duration_ms")
    private Integer durationMs;

    /** 请求报文摘要。 */
    @Column(name = "request_digest", length = 500)
    private String requestDigest;

    /** 响应报文摘要。 */
    @Column(name = "response_digest", length = 500)
    private String responseDigest;
}

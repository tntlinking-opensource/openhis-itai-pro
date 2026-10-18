/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.extension.pacsintegration;

import java.util.Optional;

/**
 * PEIS → PACS 检查结果读 SPI。
 *
 * <p>PACS 未启用时无 Bean；调用方须 {@code ObjectProvider} 可选注入。</p>
 */
public interface PeisPacsExamResultPort {

    /**
     * 按服务申请反查检查工单。
     *
     * @param serviceRequestId 服务申请主键
     * @return 工单摘要；无关联时 empty
     */
    Optional<PeisPacsWorkItemView> findWorkItemByServiceRequestId(Long serviceRequestId);

    /**
     * 按工单取主报告。
     *
     * @param workItemId 工单主键
     * @return 报告摘要；无报告时 empty
     */
    Optional<PeisPacsReportSummaryView> findReportByWorkItemId(Long workItemId);
}

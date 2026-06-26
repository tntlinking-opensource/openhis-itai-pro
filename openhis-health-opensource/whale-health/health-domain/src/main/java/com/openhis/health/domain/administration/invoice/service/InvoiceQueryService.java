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

package com.openhis.health.domain.administration.invoice.service;

import com.openhis.health.domain.administration.invoice.entity.Invoice;
import com.openhis.health.domain.administration.invoice.query.InvoiceQueryCommand;
import com.openhis.health.domain.administration.invoice.repository.InvoiceRepository;
import com.openhis.health.domain.share.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

/**
 * 票据读侧查询服务。
 *
 * <p>职责：提供票据的只读查询能力，包括按主键、支付流水等维度查询。</p>
 *
 * <p>边界：只读；多数方法在记录不存在时返回 {@code null}；{@link #getLatestIssuedInvoiceByPaymentId} 限定已开具状态，避免把草稿当作有效票据。</p>
 */
@Service
@RequiredArgsConstructor
public class InvoiceQueryService {

    private final InvoiceRepository repository;

    /**
     * 按主键查询票据。
     *
     * @param cmd 查询命令（{@code invoiceId} 必填）
     * @return 票据实体；不存在时 {@code null}
     */
    public Invoice getInvoiceById(InvoiceQueryCommand cmd) {
        return repository.findById(cmd.getInvoiceId()).orElse(null);
    }

    /**
     * 按支付流水查询票据（取最新一条，不区分状态）。
     *
     * @param cmd 查询命令（{@code paymentId} 必填）
     * @return 票据实体；无关联记录时 {@code null}
     */
    public Invoice getInvoiceByPaymentId(InvoiceQueryCommand cmd) {
        return repository.findFirstByPaymentId(cmd.getPaymentId()).orElse(null);
    }

    /**
     * 按支付流水查询最新已开具票据（DM-ADM-015 §6.2；状态为 ISSUED）。
     *
     * @param cmd 查询命令（{@code paymentId} 必填）
     * @return 已开具票据；仅有草稿或未开票时 {@code null}
     */
    public Invoice getLatestIssuedInvoiceByPaymentId(InvoiceQueryCommand cmd) {
        // 开票完成态用枚举码过滤，避免把未开具记录当作可报销凭证。
        return repository.findFirstByPaymentIdAndStatusEnum(cmd.getPaymentId(), InvoiceStatus.ISSUED.getCode()).orElse(null);
    }

    /**
     * 按主键加载票据实体（Port/adapter 读路径）；不存在或已软删时 empty。
     */
    public Optional<Invoice> findOptionalInvoiceEntityById(Long invoiceId) {
        if (invoiceId == null) {
            return Optional.empty();
        }
        return repository.findById(invoiceId)
            .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()));
    }

    /**
     * 读侧 Criteria 列表（无分页）。
     */
    public List<Invoice> listInvoiceEntities(Specification<Invoice> specification) {
        return repository.findAll(specification);
    }

    /**
     * 读侧 Criteria 分页。
     */
    public Page<Invoice> pageInvoiceEntities(Specification<Invoice> specification, Pageable pageable) {
        return repository.findAll(specification, pageable);
    }
}


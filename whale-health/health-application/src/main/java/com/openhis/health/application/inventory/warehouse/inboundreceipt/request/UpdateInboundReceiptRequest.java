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

package com.openhis.health.application.inventory.warehouse.inboundreceipt.request;

import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptLineDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 *
 * <p>枚举语义字段与 {@link CreateInboundReceiptRequest} 相同（B.10.8.1）。{@link #busNo} 在应用服务侧 {@code trim}；
 * {@link #lines} 为替换后全量明细集合，非增量补丁。{@link #rowVersion} 须与
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.response.GetInboundReceiptDetailResponse#getRowVersion()}
 * 一致，供领域修订与 JPA 乐观锁对齐。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.service.InventoryWarehouseInboundReceiptAppService#updateInboundReceipt(UpdateInboundReceiptRequest)}。</p>
 */
@Getter
@Setter
public class UpdateInboundReceiptRequest {

    /**
     * 待修订的进货业务单号（首尾空白由服务端去除）。
     */
    private String busNo;

    /**
     * 药库（源位置）主键；须通过药库白名单门禁。
     */
    private Long sourceLocationId;

    /**
     * 入库目的位置主键；须通过药库白名单门禁。
     */
    private Long purposeLocationId;

    /**
     * 源位置类型持久化码（{@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}）；可空或空白。
     */
    private String sourceTypeEnum;

    /**
     * 目标位置类型持久化码（{@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}），必填非空白。
     */
    private String purposeTypeEnum;

    /**
     * 供应类别持久化码（{@link com.openhis.health.domain.share.enums.SupplyCategory#getCode()}）；可空或空白时按库存供应默认。
     */
    private String categoryEnum;

    /**
     * 供应商主键；可空，非空时校验启用态。
     */
    private Long supplierId;

    /**
     * 经手人（医务人员主键）；修订时与新建同口径，须由前端显式传入。
     */
    private Long practitionerId;

    /**
     * 全量明细行（同号替换后的完整集合）。
     */
    private List<InboundReceiptLineDto> lines;

    /**
     * 乐观锁版本：须与详情接口返回的 {@code rowVersion} 及同号 {@code wkf_supply_request.version} 对齐；
     * 超出 {@code int} 可表示范围时 Mapper 抛 {@link IllegalArgumentException}。
     */
    private Long rowVersion;
}

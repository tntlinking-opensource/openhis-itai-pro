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

package com.openhis.health.domain.share.constants;

/**
 * ChargeItemDmConstants。
 */
public interface ChargeItemDmConstants {

    /**
     * CONTEXT_MEDICATION。
     */
    String CONTEXT_MEDICATION = "1";
    /**
     * CONTEXT_DEVICE。
     */
    String CONTEXT_DEVICE = "2";
    /**
     * CONTEXT_ACTIVITY。
     */
    String CONTEXT_ACTIVITY = "3";
    /**
     * CONTEXT_REGISTER。
     */
    String CONTEXT_REGISTER = "4";

    /** 数量单位缺省（计价「次」），收费明细命令未显式传入时使用（DM-ADM-011 quantity_unit）。 */
    /**
     * DEFAULT_QUANTITY_UNIT。
     */
    String DEFAULT_QUANTITY_UNIT = "次";
}

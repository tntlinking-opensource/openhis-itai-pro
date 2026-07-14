/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

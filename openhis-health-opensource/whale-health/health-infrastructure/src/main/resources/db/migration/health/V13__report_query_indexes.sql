-- 报表查询高频索引（AS-RPT / 费用明细 / 工作量 / 出入转 / 追溯码 / 库存单据·期初期末）
-- 依据 application/report/** 与库存单据、期初期末 Spec 谓词；与既有交账类索引互补，禁止重复建同列序索引。
-- 约定：软删表一律 partial WHERE is_deleted = FALSE；时间窗列加 IS NOT NULL。

-- ---------------------------------------------------------------------------
-- med_medication_dispense：门诊/住院发药、期初期末发药轴
-- 既有 idx_med_dispense_unsettle_scan (location_id, dispense_time, cashier_settlement_id)
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_rpt_med_dispense_time_loc
    ON med_medication_dispense (dispense_time, location_id, status_enum, dispense_enum)
    WHERE is_deleted = FALSE AND dispense_time IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_med_dispense_encounter
    ON med_medication_dispense (encounter_id)
    WHERE is_deleted = FALSE AND encounter_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_med_dispense_medication
    ON med_medication_dispense (medication_id)
    WHERE is_deleted = FALSE AND medication_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_med_dispense_med_req
    ON med_medication_dispense (med_req_id)
    WHERE is_deleted = FALSE AND med_req_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_med_dispense_practitioner
    ON med_medication_dispense (practitioner_id, dispense_time)
    WHERE is_deleted = FALSE AND practitioner_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- med_medication_request：发药报表按开立科室预解析 med_req_id
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_rpt_med_req_org
    ON med_medication_request (org_id)
    WHERE is_deleted = FALSE AND org_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- wkf_device_dispense：耗材使用、护士工作量耗材轴
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_rpt_wkf_dev_dispense_time_loc
    ON wkf_device_dispense (dispense_time, location_id, status_enum)
    WHERE is_deleted = FALSE AND dispense_time IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_wkf_dev_dispense_encounter
    ON wkf_device_dispense (encounter_id)
    WHERE is_deleted = FALSE AND encounter_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_wkf_dev_dispense_device_def
    ON wkf_device_dispense (device_def_id)
    WHERE is_deleted = FALSE AND device_def_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_wkf_dev_dispense_prepare
    ON wkf_device_dispense (prepare_time, status_enum)
    WHERE is_deleted = FALSE AND prepare_time IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_wkf_dev_dispense_performer
    ON wkf_device_dispense (performer_id, dispense_time)
    WHERE is_deleted = FALSE AND performer_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_wkf_dev_dispense_preparer
    ON wkf_device_dispense (preparer_id, prepare_time)
    WHERE is_deleted = FALSE AND preparer_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- wkf_supply_request：科室发放、单据详情、期初期末出入库
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_rpt_wkf_supply_approval
    ON wkf_supply_request (approval_time, status_enum, type_enum, item_table)
    WHERE is_deleted = FALSE AND approval_time IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_wkf_supply_purpose_loc
    ON wkf_supply_request (purpose_location_id, approval_time)
    WHERE is_deleted = FALSE AND purpose_location_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_wkf_supply_source_loc
    ON wkf_supply_request (source_location_id, approval_time)
    WHERE is_deleted = FALSE AND source_location_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_wkf_supply_item
    ON wkf_supply_request (item_table, item_id)
    WHERE is_deleted = FALSE AND item_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_wkf_supply_apply_time
    ON wkf_supply_request (apply_time, status_enum, type_enum)
    WHERE is_deleted = FALSE AND apply_time IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_wkf_supply_bus_no
    ON wkf_supply_request (bus_no)
    WHERE is_deleted = FALSE;

-- ---------------------------------------------------------------------------
-- adm_charge_item：发药/耗材定价 enrich、费用明细、重点药品、工作量金额
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_rpt_adm_ci_dispense
    ON adm_charge_item (dispense_table, dispense_id)
    WHERE is_deleted = FALSE AND dispense_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_ci_service
    ON adm_charge_item (service_table, service_id)
    WHERE is_deleted = FALSE AND service_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_ci_entered
    ON adm_charge_item (entered_date, status_enum)
    WHERE is_deleted = FALSE AND entered_date IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_ci_occurrence
    ON adm_charge_item (occurrence_time, status_enum)
    WHERE is_deleted = FALSE AND occurrence_time IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_ci_encounter
    ON adm_charge_item (encounter_id, status_enum)
    WHERE is_deleted = FALSE AND encounter_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_ci_product
    ON adm_charge_item (product_table, product_id)
    WHERE is_deleted = FALSE AND product_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_ci_enterer
    ON adm_charge_item (enterer_id, entered_date)
    WHERE is_deleted = FALSE AND enterer_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_ci_req_org
    ON adm_charge_item (requesting_org_id, entered_date)
    WHERE is_deleted = FALSE AND requesting_org_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- fin_payment_reconciliation：费用明细、医生工作量
-- 既有 idx_fin_payment_cashier_settlement (enterer_id, bill_date, cashier_settlement_id)
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_rpt_fin_payment_bill
    ON fin_payment_reconciliation (kind_enum, bill_date, status_enum)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_rpt_fin_payment_encounter
    ON fin_payment_reconciliation (encounter_id, bill_date)
    WHERE is_deleted = FALSE AND encounter_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- adm_encounter：挂号明细、出入转、发药场景 EXISTS
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_rpt_adm_enc_class_start
    ON adm_encounter (class_enum, start_time, organization_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_enc_class_end
    ON adm_encounter (class_enum, end_time, organization_id)
    WHERE is_deleted = FALSE AND end_time IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_enc_org_created
    ON adm_encounter (organization_id, created_at)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_enc_patient
    ON adm_encounter (patient_id)
    WHERE is_deleted = FALSE;

-- ---------------------------------------------------------------------------
-- adm_encounter_location / participant：病区、挂号医生范围
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_rpt_adm_enc_loc_ward
    ON adm_encounter_location (location_id, form_enum, encounter_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_enc_loc_encounter
    ON adm_encounter_location (encounter_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_enc_part_practitioner
    ON adm_encounter_participant (practitioner_id, encounter_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_rpt_adm_enc_part_encounter
    ON adm_encounter_participant (encounter_id)
    WHERE is_deleted = FALSE;

-- ---------------------------------------------------------------------------
-- cln_procedure / performer：护士工作量、住院费用挂账轴
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_rpt_cln_proc_recorded
    ON cln_procedure (recorded_time, encounter_id, request_table, status_enum)
    WHERE is_deleted = FALSE AND recorded_time IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_cln_proc_encounter
    ON cln_procedure (encounter_id)
    WHERE is_deleted = FALSE AND encounter_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rpt_cln_proc_perf_practitioner
    ON cln_procedure_performer (practitioner_id, procedure_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_rpt_cln_proc_perf_procedure
    ON cln_procedure_performer (procedure_id)
    WHERE is_deleted = FALSE;

-- ---------------------------------------------------------------------------
-- cus_trace_number*：追溯码报表（主档状态 + 流水时间窗）
-- 既有 idx_cus_tn_record_trace_number_id / idx_cus_tn_record_trace_no_created
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_rpt_cus_tn_status
    ON cus_trace_number (in_hospital_status_enum, item_table, item_id)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_rpt_cus_tn_record_created
    ON cus_trace_number_record (created_at, trace_number_id);

-- ---------------------------------------------------------------------------
-- doc_inventory_item_snapshot：期初期末按库房
-- 既有 idx_doc_inv_snap_tenant_bus*
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_rpt_doc_inv_snap_warehouse
    ON doc_inventory_item_snapshot (tenant_id, warehouse_id, bus_no)
    WHERE is_deleted = FALSE;

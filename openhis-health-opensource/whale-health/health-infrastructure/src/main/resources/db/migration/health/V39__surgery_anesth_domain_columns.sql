-- 住院手术一期领域缺口：PD 挂 AD、申请/执行字段按医疗职责分表
-- 对齐 DM-CLN-009 BR-PD-010、AS-IP-DOR-012、业务口径（禁 JSON）
-- SR.activity_id 计费；国临经 cln_procedure_definition.activity_definition_id 反查
-- 医师职责字段只落 wkf_service_request（申请可预填；空则病案首页补）
-- 护士职责字段才落 cln_procedure；人员执行真源 cln_procedure_performer
-- 主手术/手术级别/麻醉方法/切口愈合：非护士职责，不落 cln_procedure
-- 手术时间：申请可填、执行可改（两边都有 procedure_time）；禁止与 occurrence_time 混用
-- 麻醉起止：仅 cln_procedure；护士站麻醉执行弹窗登记；医生开立不填

-- 国临手术定义挂靠诊疗项目
ALTER TABLE cln_procedure_definition
    ADD COLUMN IF NOT EXISTS activity_definition_id int8;

COMMENT ON COLUMN cln_procedure_definition.activity_definition_id IS
    '挂靠诊疗项目定义主键（wkf_activity_definition.id）；国临选品→计费 AD；开立/首页经 AD 反查 PD，可空';

CREATE INDEX IF NOT EXISTS idx_cln_procedure_definition_activity_definition_id
    ON cln_procedure_definition (activity_definition_id);

-- 申请侧（医师）
ALTER TABLE wkf_service_request
    ADD COLUMN IF NOT EXISTS main_procedure_flag bool;
ALTER TABLE wkf_service_request
    ADD COLUMN IF NOT EXISTS procedure_level_code varchar(64);
ALTER TABLE wkf_service_request
    ADD COLUMN IF NOT EXISTS incision_type_code varchar(64);
ALTER TABLE wkf_service_request
    ADD COLUMN IF NOT EXISTS anesthesia_method_code varchar(64);
ALTER TABLE wkf_service_request
    ADD COLUMN IF NOT EXISTS procedure_time timestamptz(6);
ALTER TABLE wkf_service_request
    ADD COLUMN IF NOT EXISTS surgeon_id int8;
ALTER TABLE wkf_service_request
    ADD COLUMN IF NOT EXISTS first_assistant_id int8;
ALTER TABLE wkf_service_request
    ADD COLUMN IF NOT EXISTS second_assistant_id int8;
ALTER TABLE wkf_service_request
    ADD COLUMN IF NOT EXISTS nurse_id int8;
ALTER TABLE wkf_service_request
    ADD COLUMN IF NOT EXISTS anesthetist_id int8;
ALTER TABLE wkf_service_request
    ADD COLUMN IF NOT EXISTS nested_anesthesia_prescription_no varchar(64);

COMMENT ON COLUMN wkf_service_request.main_procedure_flag IS
    '主手术标志（医师申请）；空则病案首页补；非护士执行职责';
COMMENT ON COLUMN wkf_service_request.procedure_level_code IS
    '手术级别字典码（医师申请/目录）；空则病案首页补；非护士执行职责';
COMMENT ON COLUMN wkf_service_request.incision_type_code IS
    '切口类别字典码（医师申请可预填）';
COMMENT ON COLUMN wkf_service_request.anesthesia_method_code IS
    '麻醉方法字典码（医师申请可预填；真源宜麻醉侧/首页）';
COMMENT ON COLUMN wkf_service_request.procedure_time IS
    '手术时间（医师申请可填；执行拷贝至 cln_procedure.procedure_time，护士可改；非 occurrence_time）';
COMMENT ON COLUMN wkf_service_request.surgeon_id IS
    '主刀医师（申请预填；执行写入 cln_procedure_performer）';
COMMENT ON COLUMN wkf_service_request.first_assistant_id IS
    '一助（申请预填；执行写入 performer）';
COMMENT ON COLUMN wkf_service_request.second_assistant_id IS
    '二助（申请预填；执行写入 performer）';
COMMENT ON COLUMN wkf_service_request.nurse_id IS
    '配合护士（申请预填；执行护士可改写，真源 cln_procedure_performer）';
COMMENT ON COLUMN wkf_service_request.anesthetist_id IS
    '麻醉医师（申请预填；执行写入 performer）';
COMMENT ON COLUMN wkf_service_request.nested_anesthesia_prescription_no IS
    '嵌套麻醉申请单号（手术 AR 关联麻醉 AR；可空）';

-- 执行侧：手术时间/样本/备注；麻醉起止；配合护士走 performer
-- occurrence_time 仅保留既有计费/计划槽位语义，禁止与 procedure_time / 麻醉起止混用
ALTER TABLE cln_procedure
    ADD COLUMN IF NOT EXISTS procedure_time timestamptz(6);
ALTER TABLE cln_procedure
    ADD COLUMN IF NOT EXISTS anesthesia_start_time timestamptz(6);
ALTER TABLE cln_procedure
    ADD COLUMN IF NOT EXISTS anesthesia_end_time timestamptz(6);
ALTER TABLE cln_procedure
    ADD COLUMN IF NOT EXISTS incision_type_code varchar(64);
ALTER TABLE cln_procedure
    ADD COLUMN IF NOT EXISTS send_lab_flag bool;
ALTER TABLE cln_procedure
    ADD COLUMN IF NOT EXISTS specimen varchar(500);
ALTER TABLE cln_procedure
    ADD COLUMN IF NOT EXISTS note varchar(500);

COMMENT ON COLUMN cln_procedure.procedure_time IS
    '手术时间（申请可预填后拷贝；护士站手术执行弹窗可改；禁止写入/混用 occurrence_time）';
COMMENT ON COLUMN cln_procedure.anesthesia_start_time IS
    '麻醉开始时间（护士站麻醉执行弹窗；医生开立不填；可空）';
COMMENT ON COLUMN cln_procedure.anesthesia_end_time IS
    '麻醉结束时间（护士站麻醉执行弹窗；医生开立不填；可空）';
COMMENT ON COLUMN cln_procedure.incision_type_code IS
    '切口类别（术中可填；可空）';
COMMENT ON COLUMN cln_procedure.send_lab_flag IS
    '是否送检（术中可填；可空）';
COMMENT ON COLUMN cln_procedure.specimen IS
    '样本（护士可填；与备注分列；可空）';
COMMENT ON COLUMN cln_procedure.note IS
    '备注（护士可填；与样本分列；可空）';

-- 平台基础种子：文书类型 + 通用元数据 + 病程简易模板（日常/上级查房）
-- 门诊/入院/首次病程/朝阳住院模板见 V4、V5；告知书见 V3。

INSERT INTO emr_document_type (
    id, code, name, default_template_id, is_enabled, sort_order, category_code,
    created_at, is_deleted, tenant_id, created_by
) VALUES
(323100000000000101, 'OUTPATIENT_RECORD', '门诊病历', 323100000000000201, TRUE, 1, 'OUTPATIENT', TIMESTAMP '2026-07-26 00:00:00+00', FALSE, 1, 1),
(323100000000000102, 'PROGRESS_NOTE',     '病程记录', 323100000000000203, TRUE, 2, 'INPATIENT',  TIMESTAMP '2026-07-26 00:00:00+00', FALSE, 1, 1),
(323100000000000103, 'ADMISSION_RECORD',  '入院记录', 323100000000000205, TRUE, 3, 'INPATIENT',  TIMESTAMP '2026-07-26 00:00:00+00', FALSE, 1, 1),
(323100000000000104, 'DISCHARGE_SUMMARY', '出院小结', 323100000000000221, TRUE, 4, 'INPATIENT',  TIMESTAMP '2026-07-26 00:00:00+00', FALSE, 1, 1);

INSERT INTO emr_metadata_field_def (
    id, field_key, label, data_type, editable_default, value_source, source_path,
    mask_rule, sort_order, is_enabled, created_at, is_deleted, tenant_id, created_by
) VALUES
(323100000000000001, 'patientName',      '患者姓名', 'string', TRUE,  'PATIENT',   'patient.name',             'NONE',    1,  TRUE, TIMESTAMP '2026-07-26 00:00:00+00', FALSE, 1, 1),
(323100000000000002, 'gender',           '性别',     'string', FALSE, 'PATIENT',   'patient.gender',           'NONE',    2,  TRUE, TIMESTAMP '2026-07-26 00:00:00+00', FALSE, 1, 1),
(323100000000000003, 'age',              '年龄',     'string', FALSE, 'PATIENT',   'patient.age',              'NONE',    3,  TRUE, TIMESTAMP '2026-07-26 00:00:00+00', FALSE, 1, 1),
(323100000000000004, 'idCard',           '证件号',   'string', FALSE, 'PATIENT',   'patient.idCard',           'ID_CARD', 4,  TRUE, TIMESTAMP '2026-07-26 00:00:00+00', FALSE, 1, 1),
(323100000000000005, 'medicalRecordNo',  '病历号',   'string', FALSE, 'PATIENT',   'patient.medicalRecordNo',  'NONE',    5,  TRUE, TIMESTAMP '2026-07-26 00:00:00+00', FALSE, 1, 1),
(323100000000000006, 'phone',            '电话',     'string', TRUE,  'PATIENT',   'patient.phone',            'PHONE',   6,  TRUE, TIMESTAMP '2026-07-26 00:00:00+00', FALSE, 1, 1),
(323100000000000007, 'deptName',         '科室',     'string', FALSE, 'ENCOUNTER', 'encounter.deptName',       'NONE',    10, TRUE, TIMESTAMP '2026-07-26 00:00:00+00', FALSE, 1, 1),
(323100000000000008, 'bedNo',            '床号',     'string', FALSE, 'ENCOUNTER', 'encounter.bedNo',          'NONE',    11, TRUE, TIMESTAMP '2026-07-26 00:00:00+00', FALSE, 1, 1),
(323100000000000009, 'orgName',          '机构名称', 'string', FALSE, 'ORG',       'org.name',                 'NONE',    12, TRUE, TIMESTAMP '2026-07-26 00:00:00+00', FALSE, 1, 1),
(323100000000000010, 'recorderName',     '记录人',   'string', FALSE, 'USER',      'user.displayName',         'NONE',    13, TRUE, TIMESTAMP '2026-07-26 00:00:00+00', FALSE, 1, 1),
(323100000000000011, 'printTime',        '打印时间', 'string', FALSE, 'SYSTEM',    'system.printedAt',         'NONE',    14, TRUE, TIMESTAMP '2026-07-26 00:00:00+00', FALSE, 1, 1),
(323100000000000012, 'primaryDiagnosis', '主诊断',   'string', FALSE, 'DIAGNOSIS', 'diagnosis.primaryDisplay', 'NONE',    15, TRUE, TIMESTAMP '2026-07-26 00:00:00+00', FALSE, 1, 1);

INSERT INTO emr_template (
    id, name, code, document_type_code, scope_level, encounter_class_code,
    content, is_enabled, created_at, is_deleted, tenant_id, created_by
) VALUES
(
    323100000000000203,
    '日常病程记录',
    'PN_DAILY',
    'PROGRESS_NOTE',
    'HOSPITAL',
    'IMP',
    $tpl_pn_daily$
{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"病程记录"}]},{"type":"paragraph","content":[{"type":"text","text":"病情变化："}]},{"type":"paragraph","content":[{"type":"text","text":"诊疗措施："}]}]}
$tpl_pn_daily$,
    TRUE,
    TIMESTAMP '2026-07-26 00:00:00+00',
    FALSE,
    1,
    1
),
(
    323100000000000204,
    '上级医师查房记录',
    'PN_SENIOR',
    'PROGRESS_NOTE',
    'HOSPITAL',
    'IMP',
    $tpl_pn_senior$
{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"上级医师查房记录"}]},{"type":"paragraph","content":[{"type":"text","text":"查房意见："}]},{"type":"paragraph","content":[{"type":"text","text":"诊疗调整："}]}]}
$tpl_pn_senior$,
    TRUE,
    TIMESTAMP '2026-07-26 00:00:00+00',
    FALSE,
    1,
    1
);

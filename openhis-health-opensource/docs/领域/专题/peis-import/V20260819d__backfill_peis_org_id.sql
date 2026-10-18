-- =============================================================================
-- PEIS 导入项目：按老系统科室名回填 wkf_activity_definition.org_id
-- 库：whale-pro · 依赖 tmp_peis_legacy_project 仍在
-- 映射说明（老名 → adm_organization.name）：
--   检验科→检验科；妇科→妇科门诊；康复科→康复医学科；口腔/口腔科→口腔科
--   内镜科→内镜室；内科→内科门诊；皮肤科→皮肤科；外科→外科门诊
--   电诊室/电测听→电诊科；影像科/彩超室→放射线科；中医/中医科2诊室→中医内科
--   儿保科→儿科门诊
--   一般检查/特检科/肺功能/耳鼻喉科/眼科/神经内科 → 体检科（库中无同名科室）
-- =============================================================================
BEGIN;

CREATE TEMP TABLE tmp_peis_dept_map (
  legacy_dept_name varchar(128) PRIMARY KEY,
  org_name varchar(128) NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_peis_dept_map (legacy_dept_name, org_name) VALUES
  ('检验科', '检验科'),
  ('妇科', '妇科门诊'),
  ('康复科', '康复医学科'),
  ('口腔', '口腔科'),
  ('口腔科', '口腔科'),
  ('内镜科', '内镜室'),
  ('内科', '内科门诊'),
  ('皮肤科', '皮肤科'),
  ('外科', '外科门诊'),
  ('电诊室', '电诊科'),
  ('电测听', '电诊科'),
  ('影像科', '放射线科'),
  ('彩超室', '放射线科'),
  ('中医', '中医内科'),
  ('中医科2诊室', '中医内科'),
  ('儿保科', '儿科门诊'),
  ('一般检查', '体检科'),
  ('特检科', '体检科'),
  ('肺功能', '体检科'),
  ('耳鼻喉科', '体检科'),
  ('眼科', '体检科'),
  ('神经内科', '体检科');

-- 回填 AD.org_id
UPDATE wkf_activity_definition a
SET org_id = o.id,
    updated_at = NOW(),
    updated_by = 1
FROM tmp_peis_legacy_project t
JOIN tmp_peis_dept_map m ON m.legacy_dept_name = t.dept_name
JOIN adm_organization o
  ON o.tenant_id = 1
 AND o.name = m.org_name
 AND o.is_deleted = FALSE
 AND o.type_enum = 'DEPARTMENT'
WHERE a.id = t.activity_id
  AND a.category_code = 'PEIS'
  AND a.is_deleted = FALSE;

-- 同步收费定义 org_id（挂在同一 AD 上）
UPDATE adm_charge_item_definition c
SET org_id = a.org_id,
    updated_at = NOW(),
    updated_by = 1
FROM wkf_activity_definition a
WHERE c.instance_table = 'wkf_activity_definition'
  AND c.instance_id = a.id
  AND a.category_code = 'PEIS'
  AND a.is_deleted = FALSE
  AND c.is_deleted = FALSE
  AND a.org_id IS NOT NULL;

-- 校验
SELECT 'PEIS_with_org' AS k, COUNT(*) AS n
FROM wkf_activity_definition
WHERE category_code = 'PEIS' AND is_deleted = FALSE AND org_id IS NOT NULL;

SELECT 'PEIS_without_org' AS k, COUNT(*) AS n
FROM wkf_activity_definition
WHERE category_code = 'PEIS' AND is_deleted = FALSE AND org_id IS NULL;

SELECT o.name AS org_name, COUNT(*) AS n
FROM wkf_activity_definition a
JOIN adm_organization o ON o.id = a.org_id
WHERE a.category_code = 'PEIS' AND a.is_deleted = FALSE
GROUP BY o.name
ORDER BY n DESC;

SELECT t.dept_name AS unmatched_legacy_dept, COUNT(*) AS n
FROM tmp_peis_legacy_project t
LEFT JOIN tmp_peis_dept_map m ON m.legacy_dept_name = t.dept_name
WHERE m.legacy_dept_name IS NULL
GROUP BY t.dept_name;

COMMIT;

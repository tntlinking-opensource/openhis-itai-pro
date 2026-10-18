-- 一次性：体检定价健康检查 + 应收缺价清单
-- 1) pricing_flag=true 却无 SALE → 须在「体检目录/诊疗目录」补零售价
-- 2) pricing_flag=false 为不收费项（如腹部彩超），收费懒生成会跳过，属正常

-- 应收却无零售价（需人工补价）
SELECT
    a.id,
    a.bus_no,
    a.name,
    a.pricing_flag,
    a.status_enum
FROM wkf_activity_definition a
WHERE a.category_code = 'PEIS'
  AND a.is_deleted = FALSE
  AND a.children_flag IS DISTINCT FROM TRUE
  AND COALESCE(a.pricing_flag, TRUE) = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM adm_charge_item_definition c
      WHERE c.instance_table = 'wkf_activity_definition'
        AND c.instance_id = a.id
        AND c.price_type_code = 'SALE'
        AND c.is_deleted = FALSE
        AND c.status_enum = 'ACTIVE'
  )
ORDER BY a.bus_no;

-- 不收费项（挂费跳过，仅供对照）
SELECT a.id, a.bus_no, a.name, a.pricing_flag
FROM wkf_activity_definition a
WHERE a.category_code = 'PEIS'
  AND a.is_deleted = FALSE
  AND a.pricing_flag = FALSE
ORDER BY a.bus_no;

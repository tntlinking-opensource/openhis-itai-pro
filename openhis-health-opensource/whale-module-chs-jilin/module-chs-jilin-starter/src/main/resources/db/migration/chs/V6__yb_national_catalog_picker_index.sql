-- 药品/国家目录选码检索加速：模糊匹配 + 1312/1301 过滤路径
-- 依赖 PostgreSQL pg_trgm（托管库若无权限，需 DBA 预先 CREATE EXTENSION）

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 1312 摘要选码（vali=1，无 source_line_no 或来源 1312）
CREATE INDEX IF NOT EXISTS idx_yb_nat_cat_picker_1312
    ON yb_national_catalog_item (contract_id, list_type, tenant_id, hilist_code)
    WHERE is_deleted = FALSE
      AND vali_flag = '1'
      AND (source_infno = '1312' OR source_line_no IS NULL);

-- 1301 明细选码/分页
CREATE INDEX IF NOT EXISTS idx_yb_nat_cat_picker_1301
    ON yb_national_catalog_item (contract_id, list_type, tenant_id, hilist_code)
    WHERE is_deleted = FALSE
      AND source_infno = '1301'
      AND source_line_no IS NOT NULL;

-- 名称 / 拼音 / 编码 / 批准文号：前后模糊（配合 ILIKE）
CREATE INDEX IF NOT EXISTS idx_yb_nat_cat_hilist_name_trgm
    ON yb_national_catalog_item USING gin (hilist_name gin_trgm_ops)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_nat_cat_pinyin_trgm
    ON yb_national_catalog_item USING gin (pinyin gin_trgm_ops)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_nat_cat_hilist_code_trgm
    ON yb_national_catalog_item USING gin (hilist_code gin_trgm_ops)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_yb_nat_cat_aprvno_trgm
    ON yb_national_catalog_item USING gin (aprvno gin_trgm_ops)
    WHERE is_deleted = FALSE
      AND aprvno IS NOT NULL
      AND aprvno <> '';

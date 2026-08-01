-- 日结打印模板：渠道标签「网银」改为「银行卡」（与 PaymentChannel.BANK_CARD 展示名对齐）
UPDATE prt_template
SET print_json = replace(print_json::text, '"title":"网银："', '"title":"银行卡："')::jsonb,
    updated_at = NOW()
WHERE is_deleted = false
  AND print_json::text LIKE '%"title":"网银："%';

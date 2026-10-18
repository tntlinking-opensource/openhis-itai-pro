-- 手工纠错：团检管理 → 团检筹备（与 peis/V15 一致）
-- 表名：whale_menus / whale_permissions（非 sys_menu）

UPDATE whale_menus
SET display_name = '团检筹备',
    remark = '菜单入口: PeisGroupCheckAdmin · 团检筹备（单位/合同/批次/名册导入）',
    updated_at = NOW()
WHERE id = 311100000000000107
  AND is_deleted = FALSE;

UPDATE whale_permissions
SET display_name = '团检筹备',
    updated_at = NOW()
WHERE id = 311100000000000007
  AND is_deleted = FALSE;

-- 验证
SELECT id, display_name, remark FROM whale_menus WHERE id = 311100000000000107;
SELECT id, name, display_name FROM whale_permissions WHERE id = 311100000000000007;

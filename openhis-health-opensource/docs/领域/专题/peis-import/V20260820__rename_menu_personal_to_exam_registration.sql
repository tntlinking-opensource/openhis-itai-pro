-- 已落地库手工执行：菜单「个检登记」→「体检登记」
-- 对应种子：peis/V2__seed_platform_menus.sql；Flyway：peis/V3__rename_personal_registration_menu.sql

UPDATE whale_permissions
SET display_name = '体检登记',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 311100000000000005
  AND name = 'Peis.Registration.View'
  AND display_name = '个检登记';

UPDATE whale_menus
SET display_name = '体检登记',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 311100000000000105
  AND code = 'PeisPersonalRegistration'
  AND display_name = '个检登记';

-- 可选校验：
-- SELECT id, name, display_name FROM whale_permissions WHERE id = 311100000000000005;
-- SELECT id, code, display_name FROM whale_menus WHERE id = 311100000000000105;

-- 门诊导诊：收回非管理员授权（若曾授给收银/护士，仅保留管理员 R/1）

DELETE FROM zxplat_permission_grants
WHERE permission_name = 'Navigation.Outpatientguidance'
  AND NOT (provider_name = 'R' AND provider_key = '1')
  AND is_deleted = FALSE;

-- PEIS V12：小程序首页公告管理菜单（内容存 peis_config · peis.miniapp.home_notice）

INSERT INTO whale_permissions (
    id, name, display_name, parent_name, description,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000013', 'Peis.Announcement.View', '小程序首页公告', 'Peis',
     '菜单入口: PeisAnnouncement · TipTap 编辑 peis.miniapp.home_notice', NULL,
     TIMESTAMP '2026-07-25 00:00:00+00', TIMESTAMP '2026-07-25 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    parent_name = EXCLUDED.parent_name,
    description = EXCLUDED.description,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;

INSERT INTO whale_menus (
    id, code, name, display_name, icon, path, component, permission_name, parent_id,
    sort_order, is_enabled, is_hidden, is_external, external_url, menu_type, remark,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by, keep_alive
)
VALUES
    ('311100000000000113', 'PeisAnnouncement', 'PeisAnnouncement', '小程序首页公告', 'lucide:megaphone',
     '/peis/config/announcement', '/peis/config/announcement/index', 'Peis.Announcement.View', '311100000000000100', 4,
     TRUE, FALSE, FALSE, '', 'menu', 'PeisAnnouncementAppService · 首页公告 WYSIWYG', NULL,
     TIMESTAMP '2026-07-25 00:00:00+00', TIMESTAMP '2026-07-25 00:00:00+00', 1, 1, FALSE, NULL, NULL, TRUE)
ON CONFLICT (id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    display_name = EXCLUDED.display_name,
    icon = EXCLUDED.icon,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    permission_name = EXCLUDED.permission_name,
    parent_id = EXCLUDED.parent_id,
    sort_order = EXCLUDED.sort_order,
    is_enabled = EXCLUDED.is_enabled,
    is_hidden = EXCLUDED.is_hidden,
    remark = EXCLUDED.remark,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;

INSERT INTO whale_permission_grants (
    id, permission_name, provider_name, provider_key,
    tenant_id, created_at, updated_at, created_by, updated_by, is_deleted, deleted_at, deleted_by
)
VALUES
    ('311100000000000213', 'Peis.Announcement.View', 'R', '1',
     NULL, TIMESTAMP '2026-07-25 00:00:00+00', TIMESTAMP '2026-07-25 00:00:00+00', 1, 1, FALSE, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    permission_name = EXCLUDED.permission_name,
    provider_name = EXCLUDED.provider_name,
    provider_key = EXCLUDED.provider_key,
    is_deleted = FALSE,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = EXCLUDED.updated_at;

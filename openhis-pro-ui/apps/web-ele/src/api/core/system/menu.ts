import type { CreateMenuParams, Menu, PageMenuParams, UpdateMenuParams } from './menu.types';

import type { PageApiResponse } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

/**
 * 查询菜单列表（分页）
 */
export async function pageMenuApi(params: PageMenuParams) {
  return publicRequestClient.post<PageApiResponse<Menu>>('/menu/getList', params);
}

/**
 * 获取菜单详情
 */
export async function getMenuApi(id: string): Promise<Menu> {
  return publicRequestClient.post<Menu>('/menu/getById', { id });
}

/**
 * 创建菜单
 */
export async function createMenuApi(params: CreateMenuParams): Promise<void> {
  return publicRequestClient.post('/menu/create', params);
}

/**
 * 更新菜单
 */
export async function updateMenuApi(params: UpdateMenuParams): Promise<void> {
  return publicRequestClient.post('/menu/update', params);
}
/**
 * 启用菜单
 */
export async function enableMenuApi(params: { id: string }): Promise<void> {
  return publicRequestClient.post('/menu/enable', params);
}

/**
 * 停用菜单
 */
export async function disableMenuApi(params: { id: string }): Promise<void> {
  return publicRequestClient.post('/menu/disable', params);
}

/**
 * 删除菜单
 */
export async function deleteMenuApi(params: { id: string }): Promise<void> {
  return publicRequestClient.post('/menu/delete', params);
}

/**
 * 获取菜单树形结构（菜单管理页，需 Navigation.MenuManagement）
 */
export async function getMenuTreeApi(): Promise<Menu[]> {
  return publicRequestClient.post<Menu[]>('/menu/getTree');
}

/**
 * 角色授权用菜单树（仅启用且未隐藏页面，需 Navigation.RoleManagement）
 */
export async function getMenuGrantTreeApi(): Promise<Menu[]> {
  return publicRequestClient.post<Menu[]>('/menu/getGrantTree');
}

/**
 * 当前登录用户可见菜单树（登录后动态路由、科室切换）
 */
export async function getMenuTreeByLoginUserApi(): Promise<Menu[]> {
  return publicRequestClient.post<Menu[]>('/menu/getTreeByLoginUser');
}

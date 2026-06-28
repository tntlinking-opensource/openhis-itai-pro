import path from 'node:path';

/** web-ele 共用的 Vite resolve.alias，避免 vite.config.mts / vite.config.local.mts 漂移 */
export function createWebEleResolveAliases(dirname: string) {
  return {
    '@': path.resolve(dirname, './src'),
    '@inventory': path.resolve(dirname, './src/views/inventory'),
    '@pharmacy': path.resolve(dirname, './src/views/inventory/pharmacy'),
    '@warehouse': path.resolve(dirname, './src/views/inventory-bk/warehouse'),
  };
}

import type { PluginOption } from 'vite';

import { join } from 'node:path';

import { dateUtil, findMonorepoRoot, fs, getPackages, readPackageJSON } from '@zxplat/node-utils';

import { readWorkspaceManifest } from '@pnpm/workspace.read-manifest';

/** 读取 monorepo 根目录 VERSION（业务产品版本，非 package.json 的 Vben 5.x） */
async function readProductVersion(): Promise<string> {
  try {
    const versionPath = join(findMonorepoRoot(), 'VERSION');
    const text = await fs.readFile(versionPath, { encoding: 'utf8' });
    const line = text
      .split(/\r?\n/)
      .map((s) => s.trim())
      .find((s) => s.length > 0 && !s.startsWith('#'));
    return line ?? '';
  } catch {
    return '';
  }
}

function resolvePackageVersion(
  pkgsMeta: Record<string, string>,
  name: string,
  value: string,
  catalog: Record<string, string>,
) {
  if (value.includes('catalog:')) {
    return catalog[name];
  }

  if (value.includes('workspace')) {
    return pkgsMeta[name];
  }

  return value;
}

async function resolveMonorepoDependencies() {
  const { packages } = await getPackages();
  const manifest = await readWorkspaceManifest(findMonorepoRoot());
  const catalog = manifest?.catalog || {};

  const resultDevDependencies: Record<string, string | undefined> = {};
  const resultDependencies: Record<string, string | undefined> = {};
  const pkgsMeta: Record<string, string> = {};

  for (const { packageJson } of packages) {
    pkgsMeta[packageJson.name] = packageJson.version;
  }

  for (const { packageJson } of packages) {
    const { dependencies = {}, devDependencies = {} } = packageJson;
    for (const [key, value] of Object.entries(dependencies)) {
      resultDependencies[key] = resolvePackageVersion(pkgsMeta, key, value, catalog);
    }
    for (const [key, value] of Object.entries(devDependencies)) {
      resultDevDependencies[key] = resolvePackageVersion(pkgsMeta, key, value, catalog);
    }
  }
  return {
    dependencies: resultDependencies,
    devDependencies: resultDevDependencies,
  };
}

/**
 * 用于注入项目信息
 */
async function viteMetadataPlugin(root = process.cwd()): Promise<PluginOption | undefined> {
  const { author, description, homepage, license, version } = await readPackageJSON(root);

  const buildTime = dateUtil().format('YYYY-MM-DD HH:mm:ss');
  const productVersion = await readProductVersion();

  return {
    async config() {
      const { dependencies, devDependencies } = await resolveMonorepoDependencies();

      const isAuthorObject = typeof author === 'object';
      const authorName = isAuthorObject ? author.name : author;
      const authorEmail = isAuthorObject ? author.email : null;
      const authorUrl = isAuthorObject ? author.url : null;

      return {
        define: {
          __VBEN_ADMIN_METADATA__: JSON.stringify({
            authorEmail,
            authorName,
            authorUrl,
            buildTime,
            dependencies,
            description,
            devDependencies,
            homepage,
            license,
            version,
          }),
          'import.meta.env.VITE_APP_VERSION': JSON.stringify(version),
          'import.meta.env.VITE_PRODUCT_VERSION': JSON.stringify(productVersion),
        },
      };
    },
    enforce: 'post',
    name: 'vite:inject-metadata',
  };
}

export { viteMetadataPlugin };

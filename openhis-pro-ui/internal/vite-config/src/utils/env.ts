import type { ApplicationPluginOptions } from '../typing';

import { existsSync } from 'node:fs';
import { join } from 'node:path';

import { fs } from '@vben/node-utils';

import dotenv from 'dotenv';

const getBoolean = (value: string | undefined) => value === 'true';

const getString = (value: string | undefined, fallback: string) => value ?? fallback;

const getNumber = (value: string | undefined, fallback: number) => Number(value) || fallback;

/**
 * 获取当前环境下生效的配置文件名
 */
function getConfFiles() {
  const script = process.env.npm_lifecycle_script as string;
  const reg = /--mode ([\d_a-z]+)/;
  const fromScript = reg.exec(script)?.[1];
  const modeIndex = process.argv.indexOf('--mode');
  const fromArgv =
    modeIndex >= 0 && process.argv[modeIndex + 1] ? process.argv[modeIndex + 1] : undefined;
  const mode = fromArgv || fromScript || 'production';
  const files = ['.env', '.env.local', `.env.${mode}`, `.env.${mode}.local`];

  // 院点：直接读 projects/project-{id} 下 .env（UTF-8），供 HTML title / loading 使用
  const rawProject = (process.env.OPENHIS_PROJECT ?? process.env.VITE_OPENHIS_PROJECT ?? '').trim();
  if (rawProject) {
    const projectId = rawProject.startsWith('project-')
      ? rawProject.slice('project-'.length)
      : rawProject;
    const projectRel = `../../projects/project-${projectId}`;
    files.push(
      `${projectRel}/.env`,
      `${projectRel}/.env.local`,
      `${projectRel}/.env.${mode}`,
      `${projectRel}/.env.${mode}.local`,
    );
  }

  return files;
}

/**
 * Get the environment variables starting with the specified prefix
 * @param match prefix
 * @param confFiles ext
 */
async function loadEnv<T = Record<string, string>>(
  match = 'VITE_GLOB_',
  confFiles = getConfFiles(),
) {
  let envConfig = {};

  for (const confFile of confFiles) {
    try {
      const confFilePath = join(process.cwd(), confFile);
      if (existsSync(confFilePath)) {
        const envPath = await fs.readFile(confFilePath, {
          encoding: 'utf8',
        });
        const env = dotenv.parse(envPath);
        envConfig = { ...envConfig, ...env };
      }
    } catch (error) {
      console.error(`Error while parsing ${confFile}`, error);
    }
  }
  const reg = new RegExp(`^(${match})`);
  Object.keys(envConfig).forEach((key) => {
    if (!reg.test(key)) {
      Reflect.deleteProperty(envConfig, key);
    }
  });
  return envConfig as T;
}

async function loadAndConvertEnv(
  match = 'VITE_',
  confFiles = getConfFiles(),
): Promise<
  Partial<ApplicationPluginOptions> & {
    appTitle: string;
    base: string;
    port: number;
  }
> {
  const envConfig = await loadEnv(match, confFiles);

  const {
    VITE_APP_TITLE,
    VITE_ARCHIVER,
    VITE_BASE,
    VITE_COMPRESS,
    VITE_DEVTOOLS,
    VITE_INJECT_APP_LOADING,
    VITE_NITRO_MOCK,
    VITE_PORT,
    VITE_PWA,
    VITE_VISUALIZER,
  } = envConfig;

  const compressTypes = (VITE_COMPRESS ?? '')
    .split(',')
    .filter((item) => item === 'brotli' || item === 'gzip');

  // 院点：优先用 dotenv 读到的 UTF-8 文件值（Windows process.env 中文易乱码，会导致 loading 回退 OpenHis）
  const isOpenHisProject = Boolean(
    (process.env.OPENHIS_PROJECT ?? process.env.VITE_OPENHIS_PROJECT ?? '').trim(),
  );

  return {
    appTitle: isOpenHisProject
      ? getString(VITE_APP_TITLE || process.env.VITE_APP_TITLE, 'Vben Admin')
      : getString(process.env.VITE_APP_TITLE || VITE_APP_TITLE, 'Vben Admin'),
    archiver: getBoolean(process.env.VITE_ARCHIVER || VITE_ARCHIVER),
    base: getString(process.env.VITE_BASE || VITE_BASE, '/'),
    compress: compressTypes.length > 0,
    compressTypes,
    devtools: getBoolean(process.env.VITE_DEVTOOLS || VITE_DEVTOOLS),
    injectAppLoading: getBoolean(process.env.VITE_INJECT_APP_LOADING || VITE_INJECT_APP_LOADING),
    nitroMock: getBoolean(process.env.VITE_NITRO_MOCK || VITE_NITRO_MOCK),
    port: getNumber(process.env.VITE_PORT || VITE_PORT, 5173),
    pwa: getBoolean(process.env.VITE_PWA || VITE_PWA),
    visualizer: getBoolean(process.env.VITE_VISUALIZER || VITE_VISUALIZER),
  };
}

export { loadAndConvertEnv, loadEnv };

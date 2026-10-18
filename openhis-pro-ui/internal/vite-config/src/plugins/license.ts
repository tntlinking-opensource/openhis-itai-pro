import type { NormalizedOutputOptions, OutputBundle, OutputChunk } from 'rollup';
import type { PluginOption } from 'vite';

import { EOL } from 'node:os';

import { dateUtil, readPackageJSON } from '@zxplat/node-utils';

/**
 * 用于注入版权信息
 * @returns
 */

async function viteLicensePlugin(root = process.cwd()): Promise<PluginOption | undefined> {
  const { description = '', homepage = '', version = '' } = await readPackageJSON(root);

  return {
    apply: 'build',
    enforce: 'post',
    generateBundle: {
      handler: (_options: NormalizedOutputOptions, bundle: OutputBundle) => {
        const date = dateUtil().format('YYYY-MM-DD ');
        const copyrightText = `/*!
  * zxplat
  * Version: ${version}
  * Author: zxplat
  * Copyright © 2018-2025 his.vip All Rights Reserved.
  * License: GNU Lesser General Public License v3.0 (LGPL-3.0)
  * Description: ${description}
  * Date Created: ${date}
  * Homepage: ${homepage}
  * Contact: 
*/
              `.trim();

        for (const [, fileContent] of Object.entries(bundle)) {
          if (fileContent.type === 'chunk' && fileContent.isEntry) {
            const chunkContent = fileContent as OutputChunk;
            // 插入版权信息
            const content = chunkContent.code;
            const updatedContent = `${copyrightText}${EOL}${content}`;

            // 更新bundle
            (fileContent as OutputChunk).code = updatedContent;
          }
        }
      },
      order: 'post',
    },
    name: 'vite:license',
  };
}

export { viteLicensePlugin };

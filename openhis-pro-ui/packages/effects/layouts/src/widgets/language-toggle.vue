<script setup lang="ts">
import type { SupportedLanguagesType } from '@zxplat/locales';

import { SUPPORT_LANGUAGES } from '@zxplat/constants';
import { Languages } from '@zxplat/icons';
import { loadLocaleMessages } from '@zxplat/locales';
import { preferences, updatePreferences } from '@zxplat/preferences';

import { VbenDropdownRadioMenu, VbenIconButton } from '@zxplat-core/shadcn-ui';

defineOptions({
  name: 'LanguageToggle',
});

async function handleUpdate(value: string | undefined) {
  if (!value) return;
  const locale = value as SupportedLanguagesType;
  updatePreferences({
    app: {
      locale,
    },
  });
  await loadLocaleMessages(locale);
}
</script>

<template>
  <div>
    <VbenDropdownRadioMenu
      :menus="SUPPORT_LANGUAGES"
      :model-value="preferences.app.locale"
      @update:model-value="handleUpdate"
    >
      <VbenIconButton class="hover:animate-[shrink_0.3s_ease-in-out]">
        <Languages class="text-foreground size-4" />
      </VbenIconButton>
    </VbenDropdownRadioMenu>
  </div>
</template>

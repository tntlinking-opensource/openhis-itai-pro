<script lang="ts" setup>
import { Settings } from '@zxplat/icons';

import { VbenIconButton } from '@zxplat-core/shadcn-ui';

import Preferences from './preferences.vue';

const emit = defineEmits<{ clearPreferencesAndLogout: [] }>();

function clearPreferencesAndLogout() {
  emit('clearPreferencesAndLogout');
}
</script>
<template>
  <Preferences @clear-preferences-and-logout="clearPreferencesAndLogout">
    <VbenIconButton class="hover:animate-[shrink_0.3s_ease-in-out]">
      <Settings class="text-foreground size-4" />
    </VbenIconButton>
  </Preferences>
</template>

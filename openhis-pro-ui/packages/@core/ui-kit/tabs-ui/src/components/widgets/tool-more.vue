<script lang="ts" setup>
import type { DropdownMenuProps } from '@zxplat-core/shadcn-ui';

import { ChevronDown } from '@zxplat-core/icons';
import { VbenDropdownMenu } from '@zxplat-core/shadcn-ui';

defineProps<DropdownMenuProps>();
</script>

<template>
  <VbenDropdownMenu :menus="menus" :modal="false">
    <div
      class="flex-center h-full cursor-pointer border-l border-border px-2 text-lg font-semibold text-muted-foreground hover:bg-muted hover:text-foreground"
    >
      <ChevronDown class="size-4" />
    </div>
  </VbenDropdownMenu>
</template>

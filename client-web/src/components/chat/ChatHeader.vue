<template>
  <div class="flex items-center justify-between px-4 py-2.5 bg-[var(--im-surface)] border-b border-[var(--im-border)]">
    <div class="flex items-center gap-2">
      <div class="w-8 h-8 rounded bg-[var(--im-green)] text-white flex items-center justify-center text-xs font-bold">
        {{ (chat.currentSession?.name || '?').charAt(0).toUpperCase() }}
      </div>
      <span class="text-sm font-medium">{{ chat.currentSession?.name }}</span>
    </div>
    <span class="text-[var(--im-text-secondary)] text-xs">···</span>
  </div>
</template>

<script setup lang="ts">
import { useChatStore } from '../../stores/chat'
const chat = useChatStore()
</script>

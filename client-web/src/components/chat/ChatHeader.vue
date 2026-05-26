<template>
  <div class="bg-[var(--im-surface)] border-b border-[var(--im-border)]">
    <!-- Normal header -->
    <div v-if="!showSearch" class="flex items-center justify-between px-4 py-2.5">
      <div class="flex items-center gap-2">
        <div class="w-8 h-8 rounded bg-[var(--im-green)] text-white flex items-center justify-center text-xs font-bold">
          {{ (chat.currentSession?.name || '?').charAt(0).toUpperCase() }}
        </div>
        <span class="text-sm font-medium">{{ chat.currentSession?.name }}</span>
      </div>
      <div class="flex items-center gap-2">
        <button @click="showSearch = true"
          class="w-7 h-7 flex items-center justify-center rounded-lg hover:bg-[var(--im-bg-secondary)] transition text-[var(--im-text-secondary)]"
          :title="$t('chat.searchMessages')">
          <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z" />
          </svg>
        </button>
        <span v-if="chat.currentSession?.type === 2" class="text-[var(--im-text-secondary)] text-xs cursor-pointer hover:text-[var(--im-green)]" @click="$emit('showGroupInfo')">···</span>
      </div>
    </div>
    <!-- Search bar -->
    <div v-else class="flex items-center gap-2 px-4 py-2">
      <input v-model="searchQuery" @keydown.enter="doSearch" :placeholder="$t('chat.searchMessages')"
        class="im-input flex-1 text-sm" ref="searchInputRef" />
      <button @click="doSearch" :disabled="chat.isSearching" class="text-xs text-[var(--im-green)] font-medium">{{ $t('chat.search') }}</button>
      <button @click="closeSearch" class="text-xs text-[var(--im-text-secondary)]">{{ $t('common.cancel') }}</button>
    </div>
    <!-- Search results -->
    <div v-if="chat.searchResults.length > 0" class="max-h-[240px] overflow-y-auto border-t border-[var(--im-border)] bg-[var(--im-bg-secondary)]">
      <div class="px-3 py-1.5 text-[10px] text-[var(--im-text-secondary)] font-medium">{{ $t('chat.searchResults') }}</div>
      <div v-for="result in chat.searchResults" :key="result.msgId"
        class="px-4 py-2 cursor-pointer hover:bg-[var(--im-surface)] border-b border-[var(--im-border)] last:border-0"
        @click="jumpToMessage(result)">
        <p class="text-sm truncate">{{ result.content?.text }}</p>
        <p class="text-[10px] text-[var(--im-text-secondary)]">{{ formatTime(result.serverTime) }}</p>
      </div>
    </div>
    <div v-else-if="hasSearched && !chat.isSearching" class="px-4 py-3 text-center text-[var(--im-text-secondary)] text-xs border-t border-[var(--im-border)]">
      {{ $t('chat.noResults') }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import { useChatStore } from '../../stores/chat'

const chat = useChatStore()
const showSearch = ref(false)
const searchQuery = ref('')
const searchInputRef = ref<HTMLInputElement>()
const hasSearched = ref(false)

defineEmits<{ showGroupInfo: [] }>()

watch(showSearch, async (val) => {
  if (val) {
    await nextTick()
    searchInputRef.value?.focus()
  }
})

async function doSearch() {
  const q = searchQuery.value.trim()
  if (!q) return
  hasSearched.value = true
  await chat.searchMessages(q, chat.currentSessionId)
}

function closeSearch() {
  showSearch.value = false
  searchQuery.value = ''
  hasSearched.value = false
  chat.clearSearch()
}

function jumpToMessage(_result: { sessionId: string; msgId: number }) {
  // Scroll to the message in the current view - close search for now
  closeSearch()
}

function formatTime(ts: number): string {
  if (!ts) return ''
  return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <div class="h-full flex">
    <!-- Session list panel -->
    <div class="w-[320px] border-r border-[var(--im-border)] flex flex-col bg-[var(--im-surface)]">
      <!-- Search bar -->
      <div class="p-2">
        <div class="relative">
          <input v-model="search" :placeholder="$t('chat.search')"
            class="im-input bg-[var(--im-bg-secondary)] pl-8" />
          <span class="absolute left-2.5 top-2.5 text-[var(--im-text-secondary)] text-xs">🔍</span>
        </div>
      </div>
      <!-- Session list -->
      <div class="flex-1 overflow-y-auto">
        <div v-for="session in filteredSessions" :key="session.sessionId"
          @click="openChat(session)"
          class="im-session-item" :class="{ active: chat.currentSessionId === session.sessionId }">
          <div class="im-avatar bg-[var(--im-green)] text-white">
            {{ (session.name || '?').charAt(0).toUpperCase() }}
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-center justify-between">
              <span class="text-[13px] font-medium truncate">{{ session.name || 'Unknown' }}</span>
              <span class="text-[10px] text-[var(--im-text-secondary)]">{{ formatTime(session.lastMsgTime) }}</span>
            </div>
            <div class="flex items-center justify-between mt-0.5">
              <span class="text-[11px] text-[var(--im-text-secondary)] truncate max-w-[180px]">{{ session.lastMsg }}</span>
              <span v-if="session.unreadCount > 0" class="im-unread-badge">
                {{ session.unreadCount > 99 ? '99+' : session.unreadCount }}
              </span>
            </div>
          </div>
        </div>
        <div v-if="filteredSessions.length === 0" class="p-8 text-center text-[var(--im-text-secondary)] text-xs">
          {{ $t('chat.noConversations') }}
        </div>
      </div>
    </div>
    <!-- Chat area -->
    <div class="flex-1 flex flex-col bg-[var(--im-bg-chat)]">
      <template v-if="chat.currentSession">
        <ChatHeader />
        <MessageArea />
        <MessageInput @send="handleSend" />
      </template>
      <template v-else>
        <div class="flex-1 flex items-center justify-center text-[var(--im-text-secondary)] text-sm">
          {{ $t('chat.noConversations') }}
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useChatStore } from '../../stores/chat'
import { useAuthStore } from '../../stores/auth'
import type { SessionInfo } from '../../proto/im'
import ChatHeader from '../../components/chat/ChatHeader.vue'
import MessageArea from '../../components/chat/MessageArea.vue'
import MessageInput from '../../components/chat/MessageInput.vue'

const chat = useChatStore()
const auth = useAuthStore()
const search = ref('')

const filteredSessions = computed(() => {
  if (!search.value) return chat.sessions
  const q = search.value.toLowerCase()
  return chat.sessions.filter(s => (s.name || '').toLowerCase().includes(q))
})

function openChat(session: SessionInfo) {
  chat.selectSession(session.sessionId)
}

function handleSend(text: string) {
  const session = chat.currentSession
  if (!session) return
  if (session.type === 1) chat.sendC2CMessage(session.targetId, text)
  else if (session.type === 2) chat.sendGroupMessage(session.targetId, text)
}

function formatTime(ts: number): string {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  if (d.getFullYear() === now.getFullYear()) return d.toLocaleDateString([], { month: 'short', day: 'numeric' })
  return d.toLocaleDateString([], { year: 'numeric', month: 'short', day: 'numeric' })
}
</script>

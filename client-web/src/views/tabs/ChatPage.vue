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
        <ChatHeader @show-group-info="showGroupInfo" />
        <MessageArea />
        <MessageInput @send="handleSend" @send-file="handleSendFile" />
      </template>
      <template v-else>
        <div class="flex-1 flex items-center justify-center text-[var(--im-text-secondary)] text-sm">
          {{ $t('chat.noConversations') }}
        </div>
      </template>
    </div>
    <!-- Group Info Panel -->
    <div v-if="groupInfoVisible" class="w-[280px] border-l border-[var(--im-border)] bg-[var(--im-surface)] flex flex-col">
      <div class="flex items-center justify-between px-4 py-3 border-b border-[var(--im-border)]">
        <span class="text-sm font-medium">{{ $t('contacts.groupInfo') }}</span>
        <button @click="groupInfoVisible = false" class="text-[var(--im-text-secondary)] hover:text-[var(--im-text)]">✕</button>
      </div>
      <div class="flex-1 overflow-y-auto">
        <div class="px-4 py-3 border-b border-[var(--im-border)]">
          <p class="text-sm font-medium">{{ chat.currentSession?.name }}</p>
        </div>
        <div class="px-4 py-2">
          <p class="text-xs text-[var(--im-text-secondary)] mb-2">{{ $t('contacts.members') }} ({{ groupMembers.length }})</p>
          <div v-for="member in groupMembers" :key="member.userId"
            class="flex items-center justify-between py-2">
            <div class="flex items-center gap-2">
              <div class="w-7 h-7 rounded bg-[var(--im-green)] text-white flex items-center justify-center text-[10px] font-bold">
                {{ (member.nickname || member.username || '?').charAt(0).toUpperCase() }}
              </div>
              <span class="text-xs">{{ member.nickname || member.username }}</span>
            </div>
            <button v-if="isGroupOwner && member.userId !== auth.userId"
              @click="kickMember(member.userId)"
              class="text-[10px] text-red-400 hover:text-red-300">
              {{ $t('contacts.kickMember') }}
            </button>
          </div>
        </div>
        <div class="px-4 py-3 space-y-2">
          <button @click="showInviteDialog = true"
            class="w-full im-btn-primary text-xs py-2">
            {{ $t('contacts.inviteMembers') }}
          </button>
          <button v-if="isGroupOwner" @click="dissolveCurrentGroup"
            class="w-full bg-red-500 hover:bg-red-600 text-white text-xs py-2 rounded-lg transition">
            {{ $t('contacts.dissolveGroup') }}
          </button>
        </div>
      </div>
    </div>
    <!-- Invite Dialog -->
    <div v-if="showInviteDialog" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div class="bg-[var(--im-surface)] rounded-xl p-6 w-80 shadow-xl max-h-[70vh] flex flex-col">
        <h3 class="text-base font-medium mb-4">{{ $t('contacts.inviteMembers') }}</h3>
        <div class="flex-1 overflow-y-auto space-y-1 mb-4">
          <div v-for="friend in inviteFriends" :key="friend.userId"
            class="flex items-center gap-2 py-1.5">
            <input type="checkbox" :value="friend.userId" v-model="selectedInviteIds"
              class="rounded border-[var(--im-border)] text-[var(--im-green)] focus:ring-[var(--im-green)]" />
            <span class="text-sm">{{ friend.nickname || friend.username }}</span>
          </div>
        </div>
        <div class="flex gap-2 justify-end">
          <button @click="showInviteDialog = false" class="px-4 py-2 text-sm rounded-lg hover:bg-[var(--im-bg-secondary)]">{{ $t('common.cancel') }}</button>
          <button @click="inviteMembers" :disabled="selectedInviteIds.length === 0" class="im-btn-primary text-sm disabled:opacity-30">{{ $t('common.confirm') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useChatStore } from '../../stores/chat'
import { useAuthStore } from '../../stores/auth'
import { getFriendList, getGroupMembers, kickGroupMember, dissolveGroup, inviteToGroup } from '../../services/api'
import type { SessionInfo } from '../../proto/im'
import ChatHeader from '../../components/chat/ChatHeader.vue'
import MessageArea from '../../components/chat/MessageArea.vue'
import MessageInput from '../../components/chat/MessageInput.vue'

const chat = useChatStore()
const auth = useAuthStore()
const search = ref('')

// Group info panel state
const groupInfoVisible = ref(false)
const groupMembers = ref<Array<{ userId: number; username: string; nickname: string; role?: number }>>([])
const isGroupOwner = ref(false)
const showInviteDialog = ref(false)
const inviteFriends = ref<Array<{ userId: number; username: string; nickname: string }>>([])
const selectedInviteIds = ref<number[]>([])

const filteredSessions = computed(() => {
  if (!search.value) return chat.sessions
  const q = search.value.toLowerCase()
  return chat.sessions.filter(s => (s.name || '').toLowerCase().includes(q))
})

function openChat(session: SessionInfo) {
  chat.selectSession(session.sessionId)
  groupInfoVisible.value = false
}

function handleSend(text: string) {
  const session = chat.currentSession
  if (!session) return
  if (session.type === 1) chat.sendC2CMessage(session.targetId, text)
  else if (session.type === 2) chat.sendGroupMessage(session.targetId, text)
}

function handleSendFile(msgType: number, url: string, fileName: string, fileSize: number) {
  const session = chat.currentSession
  if (!session) return
  if (session.type === 1) chat.sendC2CFileMessage(session.targetId, msgType, url, fileName, fileSize)
  else if (session.type === 2) chat.sendGroupFileMessage(session.targetId, msgType, url, fileName, fileSize)
}

async function showGroupInfo() {
  const session = chat.currentSession
  if (!session || session.type !== 2) return
  groupInfoVisible.value = true
  try {
    const res = await getGroupMembers(session.targetId)
    if (res.code === 0) {
      groupMembers.value = res.members || []
      isGroupOwner.value = res.members?.some((m: { userId: number; role?: number }) => m.userId === auth.userId && m.role === 1) || false
    }
  } catch { /* ignore */ }
}

async function kickMember(userId: number) {
  const session = chat.currentSession
  if (!session) return
  try {
    await kickGroupMember(session.targetId, userId)
    const res = await getGroupMembers(session.targetId)
    if (res.code === 0) groupMembers.value = res.members || []
  } catch { /* ignore */ }
}

async function dissolveCurrentGroup() {
  if (!confirm($t('contacts.dissolveConfirm'))) return
  const session = chat.currentSession
  if (!session) return
  try {
    await dissolveGroup(session.targetId)
    groupInfoVisible.value = false
  } catch { /* ignore */ }
}

async function inviteMembers() {
  const session = chat.currentSession
  if (!session || selectedInviteIds.value.length === 0) return
  try {
    await inviteToGroup(session.targetId, selectedInviteIds.value)
    const res = await getGroupMembers(session.targetId)
    if (res.code === 0) groupMembers.value = res.members || []
    showInviteDialog.value = false
    selectedInviteIds.value = []
  } catch { /* ignore */ }
}

// Need $t for confirm dialog - import from vue-i18n
import { useI18n } from 'vue-i18n'
const { t: $t } = useI18n()

// Load friend list when invite dialog opens
import { watch } from 'vue'
watch(showInviteDialog, async (val) => {
  if (val) {
    try {
      const res = await getFriendList()
      if (res.code === 0) inviteFriends.value = res.friends || []
    } catch { /* ignore */ }
  }
})

function formatTime(ts: number): string {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  if (d.getFullYear() === now.getFullYear()) return d.toLocaleDateString([], { month: 'short', day: 'numeric' })
  return d.toLocaleDateString([], { year: 'numeric', month: 'short', day: 'numeric' })
}
</script>

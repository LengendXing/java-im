<template>
  <div class="h-full flex flex-col bg-[var(--im-surface)]">
    <div class="p-2">
      <div class="relative">
        <input v-model="search" :placeholder="$t('contacts.search')" class="im-input bg-[var(--im-bg-secondary)] pl-8" />
        <span class="absolute left-2.5 top-2.5 text-[var(--im-text-secondary)] text-xs">🔍</span>
      </div>
    </div>
    <!-- Quick actions -->
    <div class="px-4 py-2 border-b border-[var(--im-border)] space-y-1">
      <div class="flex items-center gap-3 py-2 cursor-pointer hover:bg-[var(--im-bg-secondary)] rounded-lg px-2" @click="showFriendRequests = !showFriendRequests">
        <span class="text-lg">🟡</span>
        <span class="text-sm">{{ $t('contacts.newFriend') }}</span>
        <span v-if="pendingRequests.length > 0" class="ml-auto im-unread-badge text-[9px] min-w-[16px] h-[16px]">
          {{ pendingRequests.length > 9 ? '9+' : pendingRequests.length }}
        </span>
      </div>
      <div class="flex items-center gap-3 py-2 cursor-pointer hover:bg-[var(--im-bg-secondary)] rounded-lg px-2" @click="openCreateGroup">
        <span class="text-lg">🟢</span>
        <span class="text-sm">{{ $t('contacts.groupChats') }}</span>
      </div>
    </div>
    <!-- Friend Requests Section -->
    <div v-if="showFriendRequests" class="border-b border-[var(--im-border)] bg-[var(--im-bg-secondary)]">
      <div class="px-4 py-2 text-xs text-[var(--im-text-secondary)] font-medium">{{ $t('contacts.friendRequests') }}</div>
      <div v-for="req in pendingRequests" :key="req.fromUserId"
        class="flex items-center gap-3 px-4 py-2.5">
        <div class="im-avatar w-9 h-9 text-xs bg-[var(--im-green)] text-white">
          {{ (req.nickname || req.username || '?').charAt(0).toUpperCase() }}
        </div>
        <div class="flex-1 min-w-0">
          <p class="text-sm truncate">{{ req.nickname || req.username }}</p>
        </div>
        <div class="flex gap-1.5">
          <button @click="acceptRequest(req.fromUserId)"
            class="px-2.5 py-1 text-[10px] bg-[var(--im-green)] text-white rounded hover:bg-[var(--im-green-dark)] transition">
            {{ $t('contacts.accept') }}
          </button>
          <button @click="rejectRequest(req.fromUserId)"
            class="px-2.5 py-1 text-[10px] bg-gray-400 text-white rounded hover:bg-gray-500 transition">
            {{ $t('contacts.reject') }}
          </button>
        </div>
      </div>
      <div v-if="pendingRequests.length === 0" class="px-4 py-3 text-center text-[var(--im-text-secondary)] text-xs">
        {{ $t('contacts.noRequests') }}
      </div>
    </div>
    <!-- Friend list -->
    <div class="flex-1 overflow-y-auto">
      <div v-for="friend in filteredFriends" :key="friend.userId"
        class="flex items-center gap-3 px-4 py-3 cursor-pointer hover:bg-[var(--im-bg-secondary)]">
        <div class="im-avatar bg-[var(--im-green)] text-white">
          {{ (friend.nickname || friend.username || '?').charAt(0).toUpperCase() }}
        </div>
        <span class="text-sm">{{ friend.nickname || friend.username }}</span>
      </div>
      <div v-if="filteredFriends.length === 0" class="p-8 text-center text-[var(--im-text-secondary)] text-xs">
        {{ $t('contacts.noContacts') }}
      </div>
    </div>
    <!-- Add Friend Dialog -->
    <div v-if="showAddFriend" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div class="bg-[var(--im-surface)] rounded-xl p-6 w-80 shadow-xl">
        <h3 class="text-base font-medium mb-4">{{ $t('contacts.addFriend') }}</h3>
        <input v-model="addFriendQuery" :placeholder="$t('contacts.searchUser')" class="im-input mb-3" @input="debouncedSearchUsers" />
        <div v-if="searchingUsers" class="text-xs text-[var(--im-text-secondary)] py-2">{{ $t('common.loading') }}</div>
        <div v-else-if="userSearchResults.length > 0" class="space-y-1 mb-3 max-h-40 overflow-y-auto">
          <div v-for="u in userSearchResults" :key="u.userId"
            class="flex items-center gap-3 py-2 px-2 rounded-lg hover:bg-[var(--im-bg-secondary)] cursor-pointer"
            @click="sendFriendRequest(u.userId)">
            <div class="w-8 h-8 rounded-lg bg-[var(--im-green)] text-white flex items-center justify-center text-xs font-bold">
              {{ (u.nickname || u.username || '?').charAt(0).toUpperCase() }}
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm truncate">{{ u.nickname || u.username }}</p>
              <p class="text-[10px] text-[var(--im-text-secondary)]">{{ u.username }}</p>
            </div>
            <button class="px-2 py-1 text-[10px] bg-[var(--im-green)] text-white rounded hover:bg-[var(--im-green-dark)] transition">
              {{ $t('contacts.addFriend') }}
            </button>
          </div>
        </div>
        <div v-else-if="addFriendQuery && !searchingUsers" class="text-xs text-[var(--im-text-secondary)] py-2 mb-3">{{ $t('chat.noResults') }}</div>
        <div class="flex gap-2 justify-end">
          <button @click="showAddFriend = false; addFriendQuery = ''; userSearchResults = []" class="px-4 py-2 text-sm rounded-lg hover:bg-[var(--im-bg-secondary)]">{{ $t('common.cancel') }}</button>
        </div>
      </div>
    </div>
    <!-- Create Group Dialog -->
    <div v-if="showCreateGroupDialog" class="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div class="bg-[var(--im-surface)] rounded-xl p-6 w-80 shadow-xl max-h-[70vh] flex flex-col">
        <h3 class="text-base font-medium mb-4">{{ $t('contacts.createGroup') }}</h3>
        <input v-model="newGroupName" :placeholder="$t('contacts.groupName')" class="im-input mb-3" />
        <p class="text-xs text-[var(--im-text-secondary)] mb-2">{{ $t('contacts.selectMembers') }}</p>
        <div class="flex-1 overflow-y-auto space-y-1 mb-4">
          <div v-for="friend in friends" :key="friend.userId"
            class="flex items-center gap-2 py-1.5">
            <input type="checkbox" :value="friend.userId" v-model="selectedMemberIds"
              class="rounded border-[var(--im-border)] text-[var(--im-green)] focus:ring-[var(--im-green)]" />
            <span class="text-sm">{{ friend.nickname || friend.username }}</span>
          </div>
        </div>
        <div class="flex gap-2 justify-end">
          <button @click="showCreateGroupDialog = false" class="px-4 py-2 text-sm rounded-lg hover:bg-[var(--im-bg-secondary)]">{{ $t('common.cancel') }}</button>
          <button @click="createGroup" :disabled="!newGroupName.trim() || selectedMemberIds.length === 0" class="im-btn-primary text-sm disabled:opacity-30">{{ $t('common.confirm') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getFriendList, applyFriend, getFriendRequests, acceptFriend, rejectFriend, searchUsers, createGroup as apiCreateGroup } from '../../services/api'

const search = ref('')
const friends = ref<Array<{ userId: number; username: string; nickname: string }>>([])
const showAddFriend = ref(false)
const showCreateGroupDialog = ref(false)
const showFriendRequests = ref(false)
const addFriendQuery = ref('')
const userSearchResults = ref<Array<{ userId: number; username: string; nickname: string; avatarUrl: string }>>([])
const searchingUsers = ref(false)
let searchTimer: ReturnType<typeof setTimeout> | null = null

// Friend requests
const pendingRequests = ref<Array<{ fromUserId: number; username: string; nickname: string; message: string }>>([])

// Create group
const newGroupName = ref('')
const selectedMemberIds = ref<number[]>([])

const filteredFriends = computed(() => {
  if (!search.value) return friends.value
  const q = search.value.toLowerCase()
  return friends.value.filter(f =>
    (f.nickname || '').toLowerCase().includes(q) || (f.username || '').toLowerCase().includes(q)
  )
})

onMounted(async () => {
  await loadFriends()
  await loadFriendRequests()
})

async function loadFriends() {
  try {
    const res = await getFriendList()
    if (res.code === 0) friends.value = res.friends
  } catch { /* ignore */ }
}

async function loadFriendRequests() {
  try {
    const res = await getFriendRequests()
    if (res.code === 0) pendingRequests.value = res.requests || []
  } catch { /* ignore */ }
}

function debouncedSearchUsers() {
  if (searchTimer) clearTimeout(searchTimer)
  const q = addFriendQuery.value.trim()
  if (!q) { userSearchResults.value = []; return }
  searchTimer = setTimeout(async () => {
    searchingUsers.value = true
    try {
      const res = await searchUsers(q)
      if (res.code === 0) userSearchResults.value = res.data || []
    } catch { /* ignore */ }
    finally { searchingUsers.value = false }
  }, 300)
}

async function sendFriendRequest(targetUserId: number) {
  try {
    await applyFriend(targetUserId)
    showAddFriend.value = false
    addFriendQuery.value = ''
    userSearchResults.value = []
    await loadFriends()
  } catch { /* ignore */ }
}

async function acceptRequest(fromUserId: number) {
  try {
    await acceptFriend(fromUserId)
    pendingRequests.value = pendingRequests.value.filter(r => r.fromUserId !== fromUserId)
    await loadFriends()
  } catch { /* ignore */ }
}

async function rejectRequest(fromUserId: number) {
  try {
    await rejectFriend(fromUserId)
    pendingRequests.value = pendingRequests.value.filter(r => r.fromUserId !== fromUserId)
  } catch { /* ignore */ }
}

function openCreateGroup() {
  newGroupName.value = ''
  selectedMemberIds.value = []
  showCreateGroupDialog.value = true
}

async function createGroup() {
  const name = newGroupName.value.trim()
  if (!name || selectedMemberIds.value.length === 0) return
  try {
    await apiCreateGroup(name, selectedMemberIds.value)
    showCreateGroupDialog.value = false
    await loadFriends()
  } catch { /* ignore */ }
}
</script>

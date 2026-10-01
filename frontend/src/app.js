import { Client } from '@stomp/stompjs';

const API = '/api';
const app = document.querySelector('#app');
const state = {
  session: readSession(),
  authMode: 'login',
  rooms: [],
  publicRooms: [],
  activeRoom: null,
  messages: [],
  members: [],
  typing: new Map(),
  unread: {},
  connection: 'Offline',
  error: '',
  notice: '',
  modal: null,
  inviteRoom: null,
  draft: '',
  nearBottom: true,
  search: '',
  client: null,
  reconnectTimer: 0,
  heartbeatTimer: 0,
  typingTimers: new Map(),
  pending: new Map(),
  lastTypingAt: 0,
  reconnectAttempt: 0,
  socketVersion: 0,
  toastTimer: 0,
  busy: false,
  theme: localStorage.getItem('orbit-theme') === 'light' ? 'light' : 'dark',
  createRoomOpen: false,
  mobileRoomsOpen: false,
  mobileMembers: false,
  emojiOpen: false
};

function readSession() {
  try { return JSON.parse(sessionStorage.getItem('chat-session') || 'null'); }
  catch { return null; }
}

function escapeHtml(value = '') {
  return String(value).replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character]);
}

function initials(value = '?') {
  return escapeHtml(value.trim().split(/\s+/).slice(0, 2).map((part) => part[0] || '').join('').toUpperCase() || '?');
}

function icon(name, size = '') {
  const paths = {
    search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M4.93 4.93l1.42 1.42m11.3 11.3 1.42 1.42M2 12h2m16 0h2M4.93 19.07l1.42-1.42m11.3-11.3 1.42-1.42"/>',
    moon: '<path d="M20.9 13A9 9 0 0 1 11 3.1 9 9 0 1 0 20.9 13Z"/>',
    smile: '<circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2M9 9h.01M15 9h.01"/>',
    paperclip: '<path d="m21.4 11.1-8.5 8.5a5.5 5.5 0 0 1-7.8-7.8l9.2-9.2a3.5 3.5 0 0 1 5 5l-9.2 9.2a1.5 1.5 0 0 1-2.1-2.1l8.5-8.5"/>',
    hash: '<path d="M5 9h14M4 15h14M10 3 8 21m8-18-2 18"/>',
    chevron: '<path d="m9 18 6-6-6-6"/>',
    copy: '<rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h3"/>',
    send: '<path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/>',
    users: '<path d="M16 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="10" cy="7" r="4"/><path d="M20 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>',
    lock: '<rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>',
    globe: '<circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15 15 0 0 1 0 20M12 2a15 15 0 0 0 0 20"/>',
    more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
    logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5m5 5H9"/>',
    close: '<path d="m18 6-12 12M6 6l12 12"/>',
    check: '<path d="m5 12 4 4L19 6"/>',
    edit: '<path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L8 18l-4 1 1-4Z"/>',
    trash: '<path d="M3 6h18M8 6V4h8v2m3 0-1 14H6L5 6m4 4v6m6-6v6"/>',
    arrow: '<path d="M7 17 17 7M7 7h10v10"/>'
  };
  return `<svg class="icon ${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || paths.hash}</svg>`;
}

async function api(path, options = {}) {
  const headers = { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers };
  if (state.session?.accessToken) headers.Authorization = `Bearer ${state.session.accessToken}`;
  const response = await fetch(`${API}${path}`, { ...options, headers });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.detail || body.message || `Request failed (${response.status})`);
  }
  if (response.status === 204) return null;
  return response.json();
}

function notify(message, type = 'error') {
  state[type] = message;
  render();
  clearTimeout(state.toastTimer);
  if (type === 'notice') {
    state.toastTimer = setTimeout(() => {
      state.notice = '';
      render();
    }, 3200);
  }
}

function render() {
  document.documentElement.dataset.theme = state.theme;
  document.querySelector('meta[name="theme-color"]').content = state.theme === 'dark' ? '#101116' : '#f4f5f9';
  if (!state.session) {
    app.innerHTML = renderAuth();
    return;
  }

  function resizeComposer() {
    const textarea = app.querySelector('.message-composer textarea');
    if (!textarea) return;
    textarea.style.height = 'auto';
    textarea.style.height = `${Math.min(textarea.scrollHeight, 160)}px`;
  }

  const oldList = app.querySelector('.message-list');
  const oldScroll = oldList?.scrollTop || 0;
  const wasAtBottom = oldList
    ? oldList.scrollHeight - oldList.scrollTop - oldList.clientHeight < 110
    : state.nearBottom;
  const focused = document.activeElement;
  const focusSelector = focused?.dataset?.focusKey;
  const selection = focused && typeof focused.selectionStart === 'number'
    ? [focused.selectionStart, focused.selectionEnd] : null;
  if (focused?.matches('.message-composer textarea')) state.draft = focused.value;

  app.innerHTML = renderChat();
  const list = app.querySelector('.message-list');
  if (list) {
    list.scrollTop = state.nearBottom || wasAtBottom ? list.scrollHeight : oldScroll;
    state.nearBottom = list.scrollHeight - list.scrollTop - list.clientHeight < 110;
  }
  if (focusSelector) {
    const next = app.querySelector(`[data-focus-key="${CSS.escape(focusSelector)}"]`);
    next?.focus({ preventScroll: true });
    if (selection && next?.setSelectionRange) next.setSelectionRange(...selection);
  }
  resizeComposer();
}

function renderAuth() {
  const register = state.authMode === 'register';
  return `<main class="auth-layout">
    <div class="auth-art" aria-hidden="true">
      <div class="art-orbit orbit-a"></div><div class="art-orbit orbit-b"></div>
      <div class="art-glow"></div><div class="art-message message-one">The best ideas happen<br>when we're together.</div>
      <div class="art-message message-two"><span class="mini-avatar mint">M</span><span>That’s exactly it ✨</span></div>
      <div class="art-message message-three"><span class="mini-avatar lilac">J</span><span>Just shared the brief!</span></div>
      <div class="art-copy"><div class="brand-mark">${icon('arrow')}</div><h2>Make room for<br><em>good conversation.</em></h2><p>A thoughtful place for teams to connect, share ideas, and move work forward.</p><div class="art-caption"><span class="live-dot"></span> YOUR TEAM, IN SYNC</div></div>
    </div>
    <section class="auth-pane">
      <form class="auth-card" data-form="auth">
        <a class="wordmark" href="#" aria-label="Orbit home"><span class="brand-mark">${icon('arrow')}</span> orbit</a>
        <div class="eyebrow">${register ? 'GET STARTED' : 'WELCOME BACK'}</div>
        <h1>${register ? 'Create your Orbit' : 'Good to see you.'}</h1>
        <p class="subtle">${register ? 'Your team’s next great conversation starts here.' : 'Sign in to pick up where your team left off.'}</p>
        ${register ? `<label class="field-label">Your name<div class="input-wrap"><span class="input-icon">${icon('users')}</span><input name="displayName" autocomplete="nickname" minlength="2" maxlength="40" placeholder="e.g. Alex Morgan" required></div></label>` : ''}
        <label class="field-label">Work email<div class="input-wrap"><span class="input-icon">＠</span><input name="email" type="email" autocomplete="email" maxlength="254" placeholder="you@company.com" required></div></label>
        <label class="field-label">Password<div class="input-wrap"><span class="input-icon">${icon('lock')}</span><input name="password" type="password" autocomplete="${register ? 'new-password' : 'current-password'}" minlength="${register ? '12' : '1'}" maxlength="72" placeholder="${register ? 'At least 12 characters' : 'Enter your password'}" required><button class="show-password" type="button" data-action="toggle-password">Show</button></div></label>
        ${state.error ? `<div class="inline-error">${escapeHtml(state.error)}</div>` : ''}
        <button class="button button-primary auth-submit">${register ? 'Create account' : 'Sign in'} <span>${icon('arrow')}</span></button>
        <div class="auth-switch">${register ? 'Already have an account?' : 'New to Orbit?'} <button class="text-link" type="button" data-action="auth-mode">${register ? 'Sign in' : 'Create an account'}</button></div>
        <div class="auth-foot">${icon('lock')} Your conversations stay private and secure.</div>
      </form>
    </section>
  </main>`;
}

function roomButton(room, publicRoom = false) {
  const active = state.activeRoom?.id === room.id;
  const unread = state.unread[room.id] || 0;
  return `<button type="button" class="room-item ${active ? 'selected' : ''}" data-action="open-room" data-id="${escapeHtml(room.id)}">
    <span class="room-icon">${icon(room.isPublic ? 'hash' : 'lock')}</span>
    <span class="room-label">${escapeHtml(room.name)}</span>
    ${unread ? `<span class="unread-pill">${unread > 99 ? '99+' : unread}</span>` : publicRoom ? `<span class="room-people">${room.memberCount || 0}</span>` : ''}
  </button>`;
}

function renderChat() {
  const room = state.activeRoom;
  const roomAdmin = room && Number(room.creatorId) === Number(state.session.userId);
  const roomMessages = state.messages.map(renderMessage).join('');
  const filteredRooms = state.rooms.filter((item) => item.name.toLowerCase().includes(state.search.toLowerCase()));
  const filteredPublic = state.publicRooms.filter((item) => item.name.toLowerCase().includes(state.search.toLowerCase()));
  const connected = state.connection === 'Connected';
  return `<main class="workspace">
    <button type="button" class="drawer-scrim ${state.mobileRoomsOpen || state.mobileMembers ? 'visible' : ''}" data-action="close-drawers" aria-label="Close navigation"></button>
    <aside class="left-rail ${state.mobileRoomsOpen ? 'mobile-open' : ''}">
      <div class="sidebar-brand"><a class="wordmark" href="#"><span class="brand-mark">${icon('arrow')}</span> orbit</a><button type="button" class="icon-button theme-toggle-sidebar" data-action="toggle-theme" aria-label="Switch to ${state.theme === 'dark' ? 'light' : 'dark'} theme" title="Switch theme">${icon(state.theme === 'dark' ? 'sun' : 'moon')}</button><button type="button" class="icon-button close-sidebar" data-action="close-drawers" aria-label="Close rooms">${icon('close')}</button><span class="plan-tag">TEAM</span></div>
      <div class="workspace-picker"><span class="workspace-avatar">N</span><span><strong>Northstar Studio</strong><small>Workspace</small></span>${icon('chevron')}</div>
      <label class="search-box">${icon('search')}<input type="search" value="${escapeHtml(state.search)}" placeholder="Search rooms" data-input="room-search" data-focus-key="room-search"><kbd>⌘ K</kbd></label>
      <div class="side-scroll">
        <div class="section-heading"><span>YOUR ROOMS</span><span class="section-count">${state.rooms.length}</span></div>
        <nav class="room-nav">${filteredRooms.map((item) => roomButton(item)).join('') || `<div class="side-empty">${state.search ? 'No matching rooms.' : 'Your room list is ready for a conversation.'}</div>`}</nav>
        <div class="section-heading public-heading"><span>DISCOVER</span><span class="section-count">${state.publicRooms.length}</span></div>
        <nav class="room-nav public-nav">${filteredPublic.map((item) => roomButton(item, true)).join('') || `<div class="side-empty">${state.search ? 'No matching rooms.' : 'No public rooms to discover yet.'}</div>`}</nav>
        <button type="button" class="create-room-link" data-action="show-create">${icon('plus')} Create a room</button>
        ${state.inviteRoom ? renderInviteCard() : ''}
        <form class="join-form" data-form="join">
          <div class="join-title">JOIN A ROOM</div>
          <div class="join-controls"><input name="roomCode" value="" placeholder="Paste ID or invite link" aria-label="Room ID or invite link" required><button type="submit" title="Join room">${icon('arrow')}</button></div>
        </form>
      </div>
      <div class="sidebar-profile"><span class="avatar avatar-profile">${initials(state.session.displayName)}</span><span class="profile-copy"><strong>${escapeHtml(state.session.displayName)}</strong><small>Available</small></span><button type="button" class="icon-button profile-menu" data-action="logout" title="Sign out">${icon('logout')}</button></div>
    </aside>
    <section class="conversation">
      <header class="conversation-header">
        <div class="room-heading">
          ${room ? `<div class="room-title-icon">${icon(room.isPublic ? 'hash' : 'lock')}</div><div class="room-heading-copy"><div class="room-name-line"><h1>${escapeHtml(room.name)}</h1><span class="privacy-badge">${room.isPublic ? 'Public' : 'Private'}</span></div><p>${room.memberCount || 0} members <span class="header-separator">·</span> ${state.members.filter((member) => member.online).length} online</p></div>` : `<div class="room-title-icon empty-icon">${icon('arrow')}</div><div class="room-heading-copy"><h1>Your workspace</h1><p>A quieter place to do great work together.</p></div>`}
        </div>
        <div class="header-actions">
          <button type="button" class="icon-button mobile-rooms" data-action="toggle-rooms" aria-label="Open rooms">${icon('menu')}</button>
          <button type="button" class="icon-button theme-toggle" data-action="toggle-theme" aria-label="Switch to ${state.theme === 'dark' ? 'light' : 'dark'} theme" title="Switch theme">${icon(state.theme === 'dark' ? 'sun' : 'moon')}</button>
          ${room ? `<button type="button" class="button button-quiet invite-action" data-action="copy-invite">${icon('copy')} <span>Invite</span></button><div class="connection-chip ${connected ? 'is-connected' : ''} ${state.connection === 'Reconnecting' ? 'is-reconnecting' : ''}"><i></i>${escapeHtml(state.connection)}</div>` : `<div class="connection-chip ${connected ? 'is-connected' : ''}"><i></i> ${escapeHtml(state.connection)}</div>`}
          <button type="button" class="mobile-members button button-quiet" data-action="toggle-members">${icon('users')}</button>
        </div>
      </header>
      ${room ? `<div class="room-subbar">
        <div class="subbar-details"><span>${icon('users')} ${room.memberCount || 0} members</span><span>${icon(room.isPublic ? 'globe' : 'lock')} ${room.isPublic ? 'Discoverable' : 'Invite only'}</span></div>
        ${roomAdmin ? `<div class="room-admin-actions"><button type="button" class="subbar-action" data-action="toggle-privacy">${room.isPublic ? 'Make private' : 'Make public'}</button><button type="button" class="subbar-action" data-action="rotate-invite">Manage invite</button><button type="button" class="subbar-action danger-text" data-action="delete-room">Delete room</button></div>` : `<button type="button" class="subbar-action danger-text" data-action="leave-room">Leave room</button>`}
      </div>` : ''}
      <div class="toast-stack">${state.error ? `<div class="toast toast-error" role="alert"><span class="toast-symbol">!</span><span>${escapeHtml(state.error)}</span><button data-action="dismiss-error" aria-label="Dismiss">${icon('close')}</button></div>` : ''}${state.notice ? `<div class="toast toast-success" role="status"><span class="toast-symbol">${icon('check')}</span><span>${escapeHtml(state.notice)}</span><button data-action="dismiss-notice" aria-label="Dismiss">${icon('close')}</button></div>` : ''}</div>
      <section class="message-list" aria-live="polite" aria-label="Messages">
        ${room ? roomMessages || `<div class="empty-conversation"><div class="empty-art"><span></span><span></span><span></span></div><div class="eyebrow">A FRESH START</div><h2>Make the first move.</h2><p>Every great conversation starts with a hello.</p><button class="button button-soft" data-action="focus-composer">Say hello ${icon('arrow')}</button></div>` : `<div class="welcome-panel"><div class="welcome-mark">${icon('arrow')}</div><div class="eyebrow">WELCOME TO YOUR WORKSPACE</div><h2>Good work starts<br>with a conversation.</h2><p>Open one of your rooms or discover a new one to get started.</p><div class="welcome-stats"><span>${state.rooms.length}<small>your rooms</small></span><span>${state.publicRooms.length}<small>to discover</small></span></div></div>`}
      </section>
      <div class="conversation-bottom">
        <div class="typing-status">${renderTyping()}</div>
        ${room && state.unread[room.id] ? `<button type="button" class="jump-latest" data-action="jump-latest">${state.unread[room.id]} new message${state.unread[room.id] === 1 ? '' : 's'} <span>Jump to latest ↓</span></button>` : ''}
        <form class="message-composer" data-form="send">
          <div class="composer-shell">
            <textarea name="content" rows="1" maxlength="4000" placeholder="${room ? `Message #${escapeHtml(room.name)}` : 'Choose a room to start messaging'}" data-input="draft" data-focus-key="draft" ${!room || !connected ? 'disabled' : ''}>${escapeHtml(state.draft)}</textarea>
            ${state.emojiOpen ? renderEmojiPicker() : ''}
            <div class="composer-tools"><span class="composer-hint">${room ? 'Enter to send · Shift + Enter for a new line' : 'Messages are saved to your room history'}</span><div class="composer-right"><button type="button" class="composer-tool attachment-button" data-action="attachments" aria-label="Attachments are not supported yet" title="File attachments are not available yet">${icon('paperclip')}</button><button type="button" class="composer-tool" data-action="toggle-emoji" aria-label="Choose emoji" aria-expanded="${state.emojiOpen}">${icon('smile')}</button><button type="submit" class="send-button" ${!room || !connected || !state.draft.trim() ? 'disabled' : ''} aria-label="Send message">${icon('send')}</button></div></div>
          </div>
        </form>
      </div>
    </section>
    <aside class="right-rail ${state.mobileMembers ? 'mobile-open' : ''}">
      <div class="right-rail-header"><div><span class="eyebrow">THE ROOM</span><h2>People</h2></div><button type="button" class="icon-button close-mobile" data-action="toggle-members">${icon('close')}</button></div>
      ${room ? `<div class="online-summary"><span class="live-dot"></span><strong>${state.members.filter((member) => member.online).length} online</strong><span>of ${state.members.length} members</span></div>
        <div class="member-list">${state.members.map((member) => renderMember(member, room, roomAdmin)).join('') || `<div class="member-empty">No members in this room yet.</div>`}</div>
        <div class="rail-room-card"><div class="rail-room-symbol">${icon(room.isPublic ? 'globe' : 'lock')}</div><strong>${escapeHtml(room.name)}</strong><span>${escapeHtml(room.id)}</span><button type="button" data-action="copy-room-id">${icon('copy')} Copy room ID</button></div>` : `<div class="people-empty">${icon('users')}<p>Room members will appear here when you open a room.</p></div>`}
      <div class="rail-footer"><span>ORBIT FOR TEAMS</span><span>Made for better conversations.</span></div>
    </aside>
  </main>
  ${state.createRoomOpen ? renderCreateRoomModal() : ''}
  ${state.modal ? renderModal() : ''}`;
}

function renderEmojiPicker() {
  const emojis = ['😀', '😂', '🥹', '😍', '🤔', '🙌', '👏', '🎉', '✨', '🔥', '💜', '👍', '👀', '🙏', '💡', '❤️'];
  return `<div class="emoji-picker" role="group" aria-label="Choose an emoji">${emojis.map((emoji) => `<button type="button" data-action="insert-emoji" data-emoji="${emoji}" aria-label="Insert ${emoji}">${emoji}</button>`).join('')}</div>`;
}

function renderCreateRoomModal() {
  return `<div class="modal-scrim" data-action="close-create-room"><form class="confirm-dialog create-room-dialog" data-form="create" role="dialog" aria-modal="true" aria-labelledby="create-room-title">
    <button type="button" class="dialog-close icon-button" data-action="close-create-room" aria-label="Close">${icon('close')}</button>
    <div class="modal-icon">${icon('plus')}</div><div class="eyebrow">A PLACE TO CONNECT</div>
    <h2 id="create-room-title">Create a room</h2><p>Bring your team together around a new conversation.</p>
    <label class="create-room-label" for="new-room-name">Room name</label><input id="new-room-name" class="dialog-input" name="name" minlength="2" maxlength="80" placeholder="e.g. product-launch" required>
    <label class="visibility-option"><input type="checkbox" name="isPublic" checked><span><strong>Make this room discoverable</strong><small>Anyone in your workspace can find and join it.</small></span></label>
    <div class="modal-actions"><button type="button" class="button button-quiet" data-action="close-create-room">Cancel</button><button type="submit" class="button button-primary">Create room ${icon('arrow')}</button></div>
  </form></div>`;
}

function renderInviteCard() {
  const room = state.inviteRoom;
  const link = inviteLink(room);
  return `<div class="invite-card"><div class="invite-card-top"><span class="invite-check">${icon('check')}</span><button data-action="dismiss-invite" aria-label="Close invite">${icon('close')}</button></div><strong>${escapeHtml(room.name)} is ready.</strong><span class="invite-caption">Share a link to bring your people in.</span><div class="invite-code">${escapeHtml(room.id)}</div><button class="button button-primary invite-copy" data-action="copy-invite">${icon('copy')} Copy invite link</button><button class="invite-copy-id" data-action="copy-room-id">Copy room ID instead</button><small>${room.isPublic ? 'Anyone in your workspace can discover this room.' : 'Only people with your invite link can join.'}</small></div>`;
}

function renderMember(member, room, admin) {
  const isOwner = Number(member.id) === Number(room.creatorId);
  return `<div class="member-row"><div class="member-avatar-wrap"><span class="member-avatar member-color-${Math.abs(Number(member.id) || 0) % 5}">${initials(member.name)}</span><i class="presence-dot ${member.online ? 'online' : ''}"></i></div><div class="member-copy"><strong>${escapeHtml(member.name)} ${isOwner ? '<span class="owner-label">OWNER</span>' : ''}</strong><small>${member.online ? 'Active now' : 'Offline'}</small></div>${admin && !isOwner ? `<div class="member-menu"><button title="Kick member" data-action="kick-member" data-id="${member.id}">Kick</button><button title="Ban member" data-action="ban-member" data-id="${member.id}">Ban</button></div>` : ''}</div>`;
}

function renderMessage(message) {
  const mine = Number(message.senderId) === Number(state.session.userId);
  const stamp = message.timestamp ? new Date(message.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';
  const receipt = message.receiptState || message.status || 'sent';
  return `<article class="message-row ${mine ? 'mine' : ''}" data-message-id="${escapeHtml(message.id)}">
    ${!mine ? `<span class="message-avatar">${initials(message.sender)}</span>` : ''}
    <div class="message-stack"><div class="message-meta ${mine ? 'meta-mine' : ''}"><strong>${mine ? 'You' : escapeHtml(message.sender || 'Teammate')}</strong><time>${escapeHtml(stamp)}</time></div>
      <div class="message-bubble">${escapeHtml(message.content || '')}</div>${message.editedAt ? '<small class="edited-label">edited</small>' : ''}
      ${mine ? `<div class="message-trailing"><span class="receipt-state">${escapeHtml(receipt)} ${receipt === 'read' ? '<span class="read-check">✓✓</span>' : '<span class="sent-check">✓</span>'}</span>${message.id && message.id !== message.clientMessageId ? `<div class="message-actions"><button data-action="edit-message" data-id="${escapeHtml(message.id)}" title="Edit">${icon('edit')}</button><button data-action="delete-message" data-id="${escapeHtml(message.id)}" title="Delete">${icon('trash')}</button></div>` : ''}</div>` : ''}
    </div>
  </article>`;
}

function renderTyping() {
  const names = [...state.typing.values()];
  return names.length
    ? `<span class="typing-dots"><i></i><i></i><i></i></span><strong>${escapeHtml(names.slice(0, 2).join(', '))}${names.length > 2 ? ` +${names.length - 2}` : ''}</strong> ${names.length === 1 ? 'is' : 'are'} typing`
    : '<span class="typing-placeholder"></span>';
}

function renderModal() {
  return `<div class="modal-scrim" data-action="dismiss-modal"><section class="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="modal-title" data-dialog>
    <div class="modal-icon ${state.modal.tone || ''}">${icon(state.modal.icon || 'trash')}</div><div class="eyebrow">PLEASE CONFIRM</div>
    <h2 id="modal-title">${escapeHtml(state.modal.title)}</h2><p>${escapeHtml(state.modal.description)}</p>
    <div class="modal-actions"><button class="button button-quiet" data-action="dismiss-modal">Cancel</button><button class="button button-danger" data-action="confirm" ${state.busy ? 'disabled' : ''}>${state.busy ? 'Please wait…' : escapeHtml(state.modal.confirm || 'Confirm')}</button></div>
  </section></div>`;
}

async function loadRooms() {
  if (!state.session) return;
  try {
    const [rooms, publicRooms] = await Promise.all([
      api('/rooms'),
      api('/rooms/public')
    ]);
    const changed = rooms.map((room) => room.id).join('|') !== state.rooms.map((room) => room.id).join('|');
    state.rooms = rooms;
    state.publicRooms = publicRooms;
    if (state.activeRoom) {
      const current = rooms.find((room) => room.id === state.activeRoom.id);
      if (current) state.activeRoom = current;
    }
    render();
    if (changed && state.activeRoom) connectSocket();
  } catch (error) {
    notify(error.message);
  }
}

function inviteLink(room) {
  const url = new URL(location.href);
  url.search = '';
  url.searchParams.set('room', room.id);
  if (room.inviteCode) url.searchParams.set('invite', room.inviteCode);
  return url.toString();
}

async function activateRoom(roomOrId, inviteCode = null) {
  try {
    const id = typeof roomOrId === 'string' ? roomOrId : roomOrId.id;
    const suffix = inviteCode ? `?invite=${encodeURIComponent(inviteCode)}` : '';
    const room = await api(`/rooms/${encodeURIComponent(id)}/join${suffix}`, { method: 'POST' });
    state.rooms = [room, ...state.rooms.filter((item) => item.id !== room.id)];
    state.publicRooms = state.publicRooms.filter((item) => item.id !== room.id);
    state.activeRoom = room;
    state.messages = [];
    state.members = [];
    state.typing.clear();
    state.unread[room.id] = 0;
    state.draft = '';
    state.nearBottom = true;
    state.error = '';
    state.inviteRoom = null;
    state.mobileRoomsOpen = false;
    state.mobileMembers = false;
    state.emojiOpen = false;
    history.replaceState(null, '', `${location.pathname}${location.hash}`);
    render();
    connectSocket();
    const messages = await api(`/rooms/${encodeURIComponent(room.id)}/messages?limit=50`);
    if (state.activeRoom?.id !== room.id) return;
    state.messages = [...messages].reverse();
    render();
    if (state.client?.connected) publishRoomJoin(room.id, messages);
    return true;
  } catch (error) {
    notify(error.message);
    return false;
  }
}

function connectSocket() {
  const version = ++state.socketVersion;
  clearInterval(state.heartbeatTimer);
  clearTimeout(state.reconnectTimer);
  if (state.client) {
    const old = state.client;
    state.client = null;
    old.deactivate();
  }
  if (!state.activeRoom || !state.session) {
    state.connection = 'Offline';
    render();
    return;
  }
  state.connection = 'Connecting';
  render();
  const client = new Client({
    webSocketFactory: () => new WebSocket(`${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws`),
    connectHeaders: { Authorization: `Bearer ${state.session.accessToken}` },
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    reconnectDelay: 0,
    onConnect: () => {
      if (version !== state.socketVersion) return;
      state.reconnectAttempt = 0;
      state.connection = 'Connected';
      [...state.rooms.filter((room) => room.id !== state.activeRoom?.id), state.activeRoom]
        .filter(Boolean)
        .forEach((room) => client.subscribe(`/topic/rooms/${room.id}`, (frame) => onRoomEvent(JSON.parse(frame.body), room.id)));
      client.subscribe('/user/queue/events', (frame) => onUserEvent(JSON.parse(frame.body)));
      publishRoomJoin(state.activeRoom.id, state.messages);
      state.heartbeatTimer = setInterval(() => {
        if (client.connected) client.publish({ destination: '/app/heartbeat', body: '{}' });
      }, 25000);
      render();
    },
    onWebSocketClose: () => {
      clearInterval(state.heartbeatTimer);
      if (version !== state.socketVersion) return;
      state.reconnectAttempt++;
      state.connection = 'Reconnecting';
      render();
      const delay = Math.min(30000, 750 * (2 ** Math.min(state.reconnectAttempt, 6)));
      state.reconnectTimer = setTimeout(() => {
        if (version === state.socketVersion && state.activeRoom) client.activate();
      }, Math.round(delay * (0.7 + Math.random() * 0.6)));
    },
    onWebSocketError: () => {
      if (version === state.socketVersion) {
        state.connection = 'Reconnecting';
        render();
      }
    },
    onStompError: (frame) => notify(frame.headers.message || 'The chat connection was rejected.')
  });
  state.client = client;
  client.activate();
}

function publishRoomJoin(roomId, messages = []) {
  const client = state.client;
  if (!client?.connected || state.activeRoom?.id !== roomId) return;
  client.publish({ destination: '/app/room.join', body: JSON.stringify({ roomId }) });
  messages.forEach((message) => {
    if (Number(message.senderId) !== Number(state.session.userId)) {
      client.publish({ destination: '/app/chat.receipt', body: JSON.stringify({ messageId: message.id, state: 'DELIVERED' }) });
      if (state.nearBottom) client.publish({ destination: '/app/chat.receipt', body: JSON.stringify({ messageId: message.id, state: 'READ' }) });
    }
  });
  state.pending.forEach((pending) => {
    if (pending.roomId === roomId) client.publish({ destination: '/app/chat.send', body: JSON.stringify(pending) });
  });
}

function onRoomEvent(event, subscribedRoomId) {
  if (event.type === 'CHAT') {
    if (event.roomId !== state.activeRoom?.id) {
      if (Number(event.senderId) !== Number(state.session.userId)) {
        state.unread[event.roomId] = (state.unread[event.roomId] || 0) + 1;
        render();
      }
      return;
    }
    if (!state.messages.some((item) => item.id === event.id
      || (event.clientMessageId && item.clientMessageId === event.clientMessageId))) {
      state.messages.push(event);
    }
    if (Number(event.senderId) !== Number(state.session.userId) && state.client?.connected) {
      state.client.publish({ destination: '/app/chat.receipt', body: JSON.stringify({ messageId: event.id, state: 'DELIVERED' }) });
      if (state.nearBottom) {
        state.client.publish({ destination: '/app/chat.receipt', body: JSON.stringify({ messageId: event.id, state: 'READ' }) });
      } else {
        state.unread[event.roomId] = (state.unread[event.roomId] || 0) + 1;
      }
    }
    render();
    return;
  }
  if (event.type === 'MEMBERS' && event.roomId === state.activeRoom?.id) {
    state.members = event.members || [];
    render();
    return;
  }
  if (event.roomId !== state.activeRoom?.id) return;
  if (event.type === 'RECEIPT' && Number(event.senderId) === Number(state.session.userId)) {
    state.messages = state.messages.map((message) => message.id === event.id
      ? { ...message, receiptState: event.status } : message);
  } else if (event.type === 'MESSAGE_EDITED') {
    state.messages = state.messages.map((message) => message.id === event.id
      ? { ...message, content: event.content, editedAt: event.timestamp } : message);
  } else if (event.type === 'MESSAGE_DELETED') {
    state.messages = state.messages.filter((message) => message.id !== event.id);
  } else if (event.type === 'TYPING' && Number(event.senderId) !== Number(state.session.userId)) {
    const member = state.members.find((item) => Number(item.id) === Number(event.senderId));
    state.typing.set(event.senderId, member?.name || 'Someone');
    clearTimeout(state.typingTimers.get(event.senderId));
    state.typingTimers.set(event.senderId, setTimeout(() => {
      state.typing.delete(event.senderId);
      render();
    }, 1600));
  } else {
    return;
  }
  render();
}

function onUserEvent(event) {
  if (event.type === 'ERROR') {
    notify(event.detail || 'That action could not be completed.');
  } else if (event.type === 'ACK') {
    state.pending.delete(event.clientMessageId);
    state.messages = state.messages.map((message) => message.clientMessageId === event.clientMessageId
      ? { ...message, id: event.id, status: 'sent' } : message);
    render();
  } else if (event.type === 'ROOM_REMOVED' || event.type === 'ROOM_DELETED') {
    state.rooms = state.rooms.filter((room) => room.id !== event.roomId);
    state.publicRooms = state.publicRooms.filter((room) => room.id !== event.roomId);
    delete state.unread[event.roomId];
    if (state.activeRoom?.id === event.roomId) {
      state.activeRoom = null;
      state.messages = [];
      state.members = [];
      state.typing.clear();
      connectSocket();
    }
    state.notice = event.type === 'ROOM_DELETED' ? 'This room was deleted by its owner.' : 'You are no longer a member of this room.';
    render();
  }
}

function dispatch(destination, payload) {
  if (!state.client?.connected) {
    notify('Reconnecting to Orbit. Try again in a moment.');
    return false;
  }
  state.client.publish({ destination, body: JSON.stringify(payload) });
  return true;
}

async function createRoom(form) {
  const data = new FormData(form);
  try {
    const room = await api('/rooms', {
      method: 'POST',
      body: JSON.stringify({ name: data.get('name'), isPublic: data.has('isPublic') })
    });
    state.createRoomOpen = false;
    state.inviteRoom = room;
    notify(`${room.name} is ready to go.`, 'notice');
    await activateRoom(room);
    state.inviteRoom = room;
    render();
  } catch (error) {
    notify(error.message);
  }
}

async function joinRoom(form) {
  const input = new FormData(form).get('roomCode').trim();
  if (!input) return;
  try {
    let roomId = input;
    let invite = null;
    if (/^https?:\/\//i.test(input)) {
      const url = new URL(input);
      roomId = url.searchParams.get('room') || url.pathname.split('/').filter(Boolean).at(-1) || '';
      invite = url.searchParams.get('invite');
    }
    if (await activateRoom(roomId, invite)) form.reset();
  } catch (error) {
    notify(error.message);
  }
}

async function submitAuth(form) {
  const data = Object.fromEntries(new FormData(form).entries());
  try {
    const session = await api(`/auth/${state.authMode}`, { method: 'POST', body: JSON.stringify(data) });
    state.session = session;
    sessionStorage.setItem('chat-session', JSON.stringify(session));
    state.error = '';
    render();
    await loadRooms();
    const query = new URLSearchParams(location.search);
    const roomId = query.get('room');
    if (roomId) await activateRoom(roomId, query.get('invite'));
  } catch (error) {
    state.error = error.message;
    render();
  }
}

async function act(action, element, event) {
  const id = element.dataset.id;
  const room = state.activeRoom;
  if (action === 'auth-mode') {
    state.authMode = state.authMode === 'login' ? 'register' : 'login';
    state.error = '';
    render();
  } else if (action === 'toggle-password') {
    const input = element.parentElement.querySelector('input');
    input.type = input.type === 'password' ? 'text' : 'password';
    element.textContent = input.type === 'password' ? 'Show' : 'Hide';
  } else if (action === 'logout') {
    state.client?.deactivate();
    state.session = null;
    state.rooms = [];
    state.publicRooms = [];
    state.activeRoom = null;
    sessionStorage.removeItem('chat-session');
    render();
  } else if (action === 'show-create') {
    state.createRoomOpen = true;
    render();
    app.querySelector('#new-room-name')?.focus();
  } else if (action === 'open-room') {
    if (id !== room?.id) await activateRoom(id);
  } else if (action === 'copy-invite') {
    if (room) await copyText(inviteLink(room), 'Invite link copied');
  } else if (action === 'copy-room-id') {
    if (room) await copyText(room.id, 'Room ID copied');
  } else if (action === 'dismiss-invite') {
    state.inviteRoom = null;
    render();
  } else if (action === 'dismiss-error') {
    state.error = '';
    render();
  } else if (action === 'dismiss-notice') {
    state.notice = '';
    render();
  } else if (action === 'toggle-members') {
    state.mobileMembers = !state.mobileMembers;
    state.mobileRoomsOpen = false;
    render();
  } else if (action === 'toggle-rooms') {
    state.mobileRoomsOpen = !state.mobileRoomsOpen;
    state.mobileMembers = false;
    render();
  } else if (action === 'close-drawers') {
    state.mobileRoomsOpen = false;
    state.mobileMembers = false;
    render();
  } else if (action === 'toggle-theme') {
    state.theme = state.theme === 'dark' ? 'light' : 'dark';
    localStorage.setItem('orbit-theme', state.theme);
    render();
  } else if (action === 'toggle-emoji') {
    state.emojiOpen = !state.emojiOpen;
    render();
  } else if (action === 'insert-emoji') {
    const textarea = app.querySelector('.message-composer textarea');
    const emoji = element.dataset.emoji || '';
    const cursor = textarea?.selectionStart ?? state.draft.length;
    state.draft = `${state.draft.slice(0, cursor)}${emoji}${state.draft.slice(cursor)}`;
    state.emojiOpen = false;
    render();
    const nextTextarea = app.querySelector('.message-composer textarea');
    nextTextarea?.focus();
    nextTextarea?.setSelectionRange(cursor + emoji.length, cursor + emoji.length);
  } else if (action === 'attachments') {
    notify('File attachments are not supported by the chat API yet.', 'notice');
  } else if (action === 'close-create-room') {
    if (event.target === element
      || element.closest('.dialog-close')
      || element.closest('.modal-actions')) {
      state.createRoomOpen = false;
      render();
    }
  } else if (action === 'focus-composer') {
    app.querySelector('.message-composer textarea')?.focus();
  } else if (action === 'jump-latest') {
    state.nearBottom = true;
    state.unread[room.id] = 0;
    render();
  } else if (action === 'toggle-privacy') {
    await updatePrivacy(!room.isPublic);
  } else if (action === 'rotate-invite') {
    await rotateInvite();
  } else if (action === 'leave-room') {
    openModal({
      title: `Leave #${room.name}?`,
      description: 'You will be removed from this room. The room and its history will remain available to everyone else.',
      confirm: 'Leave room',
      icon: 'arrow',
      run: leaveRoom
    });
  } else if (action === 'delete-room') {
    openModal({
      title: `Delete #${room.name}?`,
      description: 'This permanently removes the room, its message history, and all member access. This cannot be undone.',
      confirm: 'Delete room',
      run: deleteRoom
    });
  } else if (action === 'kick-member' || action === 'ban-member') {
    const member = state.members.find((item) => Number(item.id) === Number(id));
    if (!member) return;
    const ban = action === 'ban-member';
    openModal({
      title: `${ban ? 'Ban' : 'Remove'} ${member.name}?`,
      description: ban ? 'They will be removed and will not be able to rejoin this room.' : 'They will be removed and can rejoin a public room.',
      confirm: ban ? 'Ban member' : 'Remove member',
      run: () => moderateMember(member, ban)
    });
  } else if (action === 'edit-message') {
    const message = state.messages.find((item) => item.id === id);
    if (!message) return;
    const content = window.prompt('Edit your message', message.content);
    if (content?.trim() && content.trim() !== message.content) dispatch('/app/chat.edit', { messageId: message.id, content });
  } else if (action === 'delete-message') {
    const message = state.messages.find((item) => item.id === id);
    if (!message) return;
    openModal({
      title: 'Delete this message?',
      description: 'It will be removed for everyone in this room.',
      confirm: 'Delete message',
      run: () => dispatch('/app/chat.delete', { messageId: message.id })
    });
  } else if (action === 'dismiss-modal') {
    if (element === app.querySelector('.modal-scrim') || !element.closest('[data-dialog]')) {
      state.modal = null;
      render();
    }
  } else if (action === 'confirm') {
    await confirmModal();
  }
}

function openModal(modal) {
  state.modal = modal;
  render();
}

async function confirmModal() {
  if (!state.modal || state.busy) return;
  state.busy = true;
  render();
  try {
    await state.modal.run();
    state.modal = null;
  } catch (error) {
    notify(error.message);
  } finally {
    state.busy = false;
    render();
  }
}

async function updatePrivacy(isPublic) {
  const room = state.activeRoom;
  try {
    const updated = await api(`/rooms/${room.id}/settings`, { method: 'PATCH', body: JSON.stringify({ isPublic }) });
    state.activeRoom = updated;
    state.rooms = state.rooms.map((item) => item.id === updated.id ? updated : item);
    notify(`Room is now ${updated.isPublic ? 'public' : 'private'}.`, 'notice');
    render();
    loadRooms();
  } catch (error) {
    notify(error.message);
  }
}

async function rotateInvite() {
  try {
    const updated = await api(`/rooms/${state.activeRoom.id}/invite/rotate`, { method: 'POST' });
    state.activeRoom = updated;
    state.rooms = state.rooms.map((item) => item.id === updated.id ? updated : item);
    state.inviteRoom = updated;
    notify('Invite rotated. The previous link has expired.', 'notice');
    render();
  } catch (error) {
    notify(error.message);
  }
}

async function leaveRoom() {
  const room = state.activeRoom;
  await api(`/rooms/${room.id}/memberships/current`, { method: 'DELETE' });
  state.rooms = state.rooms.filter((item) => item.id !== room.id);
  state.activeRoom = null;
  state.messages = [];
  state.members = [];
  state.typing.clear();
  delete state.unread[room.id];
  connectSocket();
  notify(`You left #${room.name}.`, 'notice');
  render();
}

async function deleteRoom() {
  const room = state.activeRoom;
  await api(`/rooms/${room.id}`, { method: 'DELETE' });
  state.rooms = state.rooms.filter((item) => item.id !== room.id);
  state.activeRoom = null;
  state.messages = [];
  state.members = [];
  connectSocket();
  notify(`#${room.name} and its history were deleted.`, 'notice');
  render();
  loadRooms();
}

async function moderateMember(member, ban) {
  const room = state.activeRoom;
  await api(`/rooms/${room.id}/members/${member.id}${ban ? '/ban' : ''}`, { method: ban ? 'POST' : 'DELETE' });
  notify(`${member.name} ${ban ? 'was banned from' : 'was removed from'} the room.`, 'notice');
}

async function copyText(text, message) {
  try {
    await navigator.clipboard.writeText(text);
    notify(message, 'notice');
  } catch {
    notify('Clipboard access is unavailable. Select and copy the invite manually.');
  }
}

function sendMessage(form) {
  const content = new FormData(form).get('content').trim();
  if (!content || !state.activeRoom || !state.client?.connected) return;
  const event = {
    type: 'CHAT',
    roomId: state.activeRoom.id,
    clientMessageId: crypto.randomUUID(),
    content
  };
  state.pending.set(event.clientMessageId, event);
  state.messages.push({
    ...event,
    id: event.clientMessageId,
    senderId: state.session.userId,
    sender: state.session.displayName,
    timestamp: new Date().toISOString(),
    status: 'sending'
  });
  state.draft = '';
  form.elements.content.value = '';
  state.nearBottom = true;
  state.client.publish({ destination: '/app/chat.send', body: JSON.stringify(event) });
  render();
}

function emitTyping() {
  if (Date.now() - state.lastTypingAt < 900 || !state.activeRoom) return;
  state.lastTypingAt = Date.now();
  dispatch('/app/chat.typing', { roomId: state.activeRoom.id });
}

function openInviteFromUrl() {
  const params = new URLSearchParams(location.search);
  const roomId = params.get('room');
  if (roomId) activateRoom(roomId, params.get('invite'));
}

app.addEventListener('submit', (event) => {
  const form = event.target.closest('form[data-form]');
  if (!form) return;
  event.preventDefault();
  const kind = form.dataset.form;
  if (kind === 'auth') submitAuth(form);
  else if (kind === 'create') createRoom(form);
  else if (kind === 'join') joinRoom(form);
  else if (kind === 'send') sendMessage(form);
});

app.addEventListener('click', (event) => {
  const target = event.target.closest('[data-action]');
  if (target) act(target.dataset.action, target, event);
});

app.addEventListener('input', (event) => {
  const input = event.target;
  if (input.dataset.input === 'draft') {
    state.draft = input.value;
    input.closest('form')?.querySelector('.send-button')?.toggleAttribute('disabled', !input.value.trim() || !state.client?.connected);
    resizeComposer();
    emitTyping();
  } else if (input.dataset.input === 'room-search') {
    state.search = input.value;
    const start = input.selectionStart;
    render();
    const next = app.querySelector('[data-input="room-search"]');
    next?.focus();
    next?.setSelectionRange(start, start);
  }
});

app.addEventListener('keydown', (event) => {
  if (event.target.matches('.message-composer textarea') && event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    app.querySelector('.message-composer')?.requestSubmit();
  } else if (event.key === 'Escape' && (state.modal || state.createRoomOpen || state.emojiOpen || state.mobileRoomsOpen || state.mobileMembers)) {
    state.modal = null;
    state.createRoomOpen = false;
    state.emojiOpen = false;
    state.mobileRoomsOpen = false;
    state.mobileMembers = false;
    render();
  } else if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault();
    app.querySelector('[data-input="room-search"]')?.focus();
  }
});

app.addEventListener('scroll', (event) => {
  if (!event.target.matches('.message-list')) return;
  const list = event.target;
  state.nearBottom = list.scrollHeight - list.scrollTop - list.clientHeight < 110;
  if (state.nearBottom && state.activeRoom) state.unread[state.activeRoom.id] = 0;
}, true);

window.addEventListener('focus', loadRooms);
window.addEventListener('online', () => state.activeRoom && connectSocket());
window.addEventListener('beforeunload', () => state.client?.deactivate());

render();
if (state.session) {
  loadRooms().then(() => openInviteFromUrl());
} else if (new URLSearchParams(location.search).has('room')) {
  state.authMode = 'login';
}

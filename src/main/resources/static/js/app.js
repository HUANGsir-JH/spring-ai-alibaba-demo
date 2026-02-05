/**
 * AI Chat Application
 * 与后端 SSE 接口对接，实现流式对话功能
 */

// ==================== 常量定义 ====================
const STORAGE_KEY = 'saademo-history';
const SSE_EVENTS = {
    MODEL: '[MODEL]',
    COMPLETE: '[COMPLETE]',
    ERROR: '[ERROR]',
    TOOL: '[TOOL]',
    THINKING: '[THINKING]',
    CONTEXT: '[CONTEXT]',
    TIMEOUT: '[TIMEOUT]'
};

// ==================== DOM 元素 ====================
const elements = {
    // 主要元素
    chatContainer: document.getElementById('chatContainer'),
    promptInput: document.getElementById('promptInput'),
    sendBtn: document.getElementById('sendBtn'),
    attachBtn: document.getElementById('attachBtn'),

    // 会话相关
    sessionIdDisplay: document.getElementById('sessionIdDisplay'),
    sessionIdInput: document.getElementById('sessionIdInput'),
    configSessionBtn: document.getElementById('configSessionBtn'),
    setSessionBtn: document.getElementById('setSessionBtn'),
    sessionModal: document.getElementById('sessionModal'),
    closeModalBtn: document.getElementById('closeModalBtn'),
    cancelSessionBtn: document.getElementById('cancelSessionBtn'),

    // 历史记录
    newChatBtn: document.getElementById('newChatBtn'),
    historyList: document.getElementById('historyList'),
    historySearchInput: document.getElementById('historySearchInput'),

    // 欢迎界面
    welcomeScreen: document.getElementById('welcomeScreen')
};

// ==================== 状态管理 ====================
let state = {
    currentSessionId: null,
    currentMessages: [],
    currentRoundMessages: [], // 当前轮次的消息，用于保存历史
    isStreaming: false,
    eventSource: null,
    currentStreamingMessageId: null
};

// ==================== 初始化 ====================
function init() {
    // 生成初始 sessionId
    generateNewSessionId();

    // 绑定事件监听器
    bindEvents();

    // 加载历史记录
    loadHistoryList();

    // 设置 marked.js 配置
    if (typeof marked !== 'undefined') {
        marked.setOptions({
            breaks: true,
            gfm: true,
            highlight: function (code, lang) {
                return code;
            }
        });
    }

    // 自动调整输入框高度
    autoResizeTextarea();
}

// ==================== 事件绑定 ====================
function bindEvents() {
    // 发送按钮
    elements.sendBtn.addEventListener('click', sendMessage);

    // 回车发送（Shift + Enter 换行）
    elements.promptInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    // 输入框自动调整高度
    elements.promptInput.addEventListener('input', autoResizeTextarea);

    // 会话配置
    elements.configSessionBtn.addEventListener('click', () => {
        elements.sessionModal.classList.add('show');
        elements.sessionIdInput.value = state.currentSessionId;
        elements.sessionIdInput.focus();
    });

    elements.closeModalBtn.addEventListener('click', closeSessionModal);
    elements.cancelSessionBtn.addEventListener('click', closeSessionModal);
    elements.setSessionBtn.addEventListener('click', () => {
        const newSessionId = elements.sessionIdInput.value.trim();
        if (newSessionId) {
            switchSession(newSessionId);
            closeSessionModal();
        }
    });

    // 点击模态框背景关闭
    elements.sessionModal.addEventListener('click', (e) => {
        if (e.target === elements.sessionModal) {
            closeSessionModal();
        }
    });

    // 新建对话
    elements.newChatBtn.addEventListener('click', generateNewSessionId);

    // 历史搜索
    elements.historySearchInput.addEventListener('input', filterHistory);

    // 附件上传（暂不实现功能）
    elements.attachBtn.addEventListener('click', () => {
        showNotification('文件上传功能即将推出');
    });

    // 窗口关闭时清理连接
    window.addEventListener('beforeunload', cleanup);
}

// ==================== Session 管理 ====================
function generateNewSessionId() {
    const sessionId = 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
    switchSession(sessionId);
}

function switchSession(sessionId) {
    // 关闭当前连接
    cleanup();

    // 更新状态
    state.currentSessionId = sessionId;
    state.currentMessages = [];

    // 更新 UI
    elements.sessionIdDisplay.textContent = sessionId;

    // 清空聊天界面
    clearChatContainer();

    // 显示欢迎界面
    elements.welcomeScreen.classList.remove('hidden');

    // 加载该会话的历史消息
    loadSessionHistory(sessionId);

    // 更新侧边栏选中状态
    updateHistoryListSelection();
}

function closeSessionModal() {
    elements.sessionModal.classList.remove('show');
}

function cleanup() {
    if (state.eventSource) {
        state.eventSource.close();
        state.eventSource = null;
    }
}

// ==================== 消息发送 ====================
function sendMessage() {
    const prompt = elements.promptInput.value.trim();

    if (!prompt) {
        showNotification('请输入消息内容');
        return;
    }

    if (state.isStreaming) {
        showNotification('请等待当前响应完成');
        return;
    }

    if (!state.currentSessionId) {
        generateNewSessionId();
    }

    // 隐藏欢迎界面
    elements.welcomeScreen.classList.add('hidden');

    // 清空当前轮次消息记录
    state.currentRoundMessages = [];

    // 显示用户消息
    appendMessage('user', prompt);

    // 保存到历史记录
    saveMessageToHistory({
        sessionId: state.currentSessionId,
        role: 'user',
        content: prompt,
        eventType: null,
        timestamp: Date.now()
    });

    // 清空输入框并重置高度
    elements.promptInput.value = '';
    elements.promptInput.style.height = 'auto';

    // 禁用发送按钮
    setStreamingState(true);

    // 显示思考中指示器
    showThinkingIndicator();

    // 建立 SSE 连接
    connectSSE(prompt);
}

function connectSSE(prompt) {
    // 关闭之前的连接（如果有）
    cleanup();

    const sessionId = state.currentSessionId;
    const url = `/stream/mem/agent?prompt=${encodeURIComponent(prompt)}&sessionId=${encodeURIComponent(sessionId)}`;

    try {
        const eventSource = new EventSource(url);
        state.eventSource = eventSource;

        // 连接打开
        eventSource.onopen = () => {
            console.log('SSE 连接已建立');
        };

        // 监听模型响应事件
        eventSource.addEventListener(SSE_EVENTS.MODEL, (event) => {
            handleModelEvent(event.data);
        });

        // 监听工具调用事件
        eventSource.addEventListener(SSE_EVENTS.TOOL, (event) => {
            handleToolEvent(event.data);
        });

        // 监听思考过程事件
        eventSource.addEventListener(SSE_EVENTS.THINKING, (event) => {
            handleThinkingEvent(event.data);
        });

        // 监听上下文事件
        eventSource.addEventListener(SSE_EVENTS.CONTEXT, (event) => {
            handleContextEvent(event.data);
        });

        // 监听完成事件
        eventSource.addEventListener(SSE_EVENTS.COMPLETE, (event) => {
            handleCompleteEvent(event.data);
            cleanup();
        });

        // 监听错误事件
        eventSource.addEventListener(SSE_EVENTS.ERROR, (event) => {
            handleErrorEvent(event.data);
            cleanup();
        });

        // 监听超时事件
        eventSource.addEventListener(SSE_EVENTS.TIMEOUT, (event) => {
            handleTimeoutEvent(event.data);
            cleanup();
        });

        // 连接错误处理
        eventSource.onerror = (error) => {
            console.error('SSE 连接错误:', error);
            removeThinkingIndicator();
            setStreamingState(false);

            if (eventSource.readyState === EventSource.CLOSED) {
                console.log('SSE 连接已关闭');
            }

            cleanup();
        };

    } catch (error) {
        console.error('创建 SSE 连接失败:', error);
        removeThinkingIndicator();
        setStreamingState(false);
        appendMessage('assistant', '', SSE_EVENTS.ERROR, '连接失败，请重试');
    }
}

// ==================== SSE 事件处理 ====================
function handleModelEvent(content) {
    removeThinkingIndicator();

    if (!content) return;

    if (!state.currentStreamingMessageId) {
        // 创建新的助手消息
        const messageId = appendMessage('assistant', content, SSE_EVENTS.MODEL);
        state.currentStreamingMessageId = messageId;
    } else {
        // 追加到现有消息
        appendToMessage(state.currentStreamingMessageId, content);
    }
}

function handleToolEvent(content) {
    removeThinkingIndicator();
    if (content) {
        appendMessage('assistant', content, SSE_EVENTS.TOOL);
    }
}

function handleThinkingEvent(content) {
    removeThinkingIndicator();
    if (content) {
        appendMessage('assistant', content, SSE_EVENTS.THINKING);
    }
}

function handleContextEvent(content) {
    if (content) {
        appendMessage('assistant', content, SSE_EVENTS.CONTEXT);
    }
}

function handleCompleteEvent(content) {
    removeThinkingIndicator();
    setStreamingState(false);
    state.currentStreamingMessageId = null;

    // 保存助手的最终消息到历史
    saveAssistantMessageToHistory();
}

function handleErrorEvent(content) {
    removeThinkingIndicator();
    setStreamingState(false);
    state.currentStreamingMessageId = null;
    appendMessage('assistant', content || '发生错误', SSE_EVENTS.ERROR);
    // 保存错误消息到历史
    saveAssistantMessageToHistory();
}

function handleTimeoutEvent(content) {
    removeThinkingIndicator();
    setStreamingState(false);
    state.currentStreamingMessageId = null;
    appendMessage('assistant', content || '响应超时', SSE_EVENTS.TIMEOUT);
    // 保存超时消息到历史
    saveAssistantMessageToHistory();
}

// ==================== 消息显示 ====================
function appendMessage(role, content, eventType = null, isFromHistory = false) {
    const messageId = 'msg_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
    const timestamp = Date.now();

    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${role}`;
    messageDiv.dataset.messageId = messageId;
    messageDiv.dataset.timestamp = timestamp;

    // 创建头像
    const avatar = document.createElement('div');
    avatar.className = 'message-avatar';
    avatar.textContent = role === 'user' ? '我' : 'AI';

    // 创建消息主体
    const messageMain = document.createElement('div');
    messageMain.className = 'message-main';

    // 创建消息头部（作者和时间）
    const messageHeader = document.createElement('div');
    messageHeader.className = 'message-header';

    const author = document.createElement('span');
    author.className = 'message-author';
    author.textContent = role === 'user' ? '你' : 'AI 助手';

    const time = document.createElement('span');
    time.className = 'message-time';
    time.textContent = formatTime(timestamp);
    time.title = new Date(timestamp).toLocaleString('zh-CN');

    messageHeader.appendChild(author);
    messageHeader.appendChild(time);

    // 创建消息气泡
    const bubble = document.createElement('div');
    bubble.className = 'message-bubble';

    // 如果有事件类型，添加标签
    if (eventType) {
        const eventTag = createEventTag(eventType);
        bubble.appendChild(eventTag);
    }

    // 内容容器
    const contentDiv = document.createElement('div');
    contentDiv.className = 'markdown-content';

    if (eventType && eventType !== SSE_EVENTS.MODEL) {
        // 非模型响应，显示原始文本
        contentDiv.textContent = content;
    } else if (content) {
        // 模型响应，渲染 Markdown
        if (typeof marked !== 'undefined') {
            contentDiv.innerHTML = marked.parse(content);
        } else {
            contentDiv.textContent = content;
        }
    }

    bubble.appendChild(contentDiv);
    messageMain.appendChild(messageHeader);
    messageMain.appendChild(bubble);

    // 创建消息操作按钮
    const actions = createMessageActions(role, messageId, content);
    messageMain.appendChild(actions);

    // 组装消息
    messageDiv.appendChild(avatar);
    messageDiv.appendChild(messageMain);

    elements.chatContainer.appendChild(messageDiv);
    scrollToBottom();

    // 消息数据对象
    const messageData = {
        id: messageId,
        role,
        content,
        eventType,
        timestamp
    };

    // 保存到当前消息列表
    state.currentMessages.push(messageData);

    // 如果不是从历史记录加载，且是助手消息，添加到当前轮次消息列表
    if (!isFromHistory && role === 'assistant') {
        state.currentRoundMessages.push(messageData);
    }

    return messageId;
}

function appendToMessage(messageId, content) {
    const messageDiv = elements.chatContainer.querySelector(`[data-message-id="${messageId}"]`);
    if (!messageDiv) return;

    const contentDiv = messageDiv.querySelector('.markdown-content');
    if (!contentDiv) return;

    // 获取当前内容
    const currentContent = contentDiv.textContent;
    const newContent = currentContent + content;

    // 更新内容
    if (typeof marked !== 'undefined') {
        contentDiv.innerHTML = marked.parse(newContent);
    } else {
        contentDiv.textContent = newContent;
    }

    // 更新状态中的消息内容
    const msgIndex = state.currentMessages.findIndex(m => m.id === messageId);
    if (msgIndex !== -1) {
        state.currentMessages[msgIndex].content = newContent;
    }

    // 同时更新当前轮次消息内容
    const roundMsgIndex = state.currentRoundMessages.findIndex(m => m.id === messageId);
    if (roundMsgIndex !== -1) {
        state.currentRoundMessages[roundMsgIndex].content = newContent;
    }

    scrollToBottom();
}

function createEventTag(eventType) {
    const tag = document.createElement('span');
    tag.className = `event-tag ${getEventClass(eventType)}`;
    tag.dataset.event = eventType;
    tag.textContent = eventType;
    return tag;
}

function getEventClass(eventType) {
    const eventClasses = {
        [SSE_EVENTS.MODEL]: 'event-model',
        [SSE_EVENTS.TOOL]: 'event-tool',
        [SSE_EVENTS.THINKING]: 'event-thinking',
        [SSE_EVENTS.COMPLETE]: 'event-complete',
        [SSE_EVENTS.ERROR]: 'event-error',
        [SSE_EVENTS.CONTEXT]: 'event-context',
        [SSE_EVENTS.TIMEOUT]: 'event-timeout'
    };
    return eventClasses[eventType] || 'event-model';
}

function createMessageActions(role, messageId, content) {
    const actions = document.createElement('div');
    actions.className = 'message-actions';

    // 复制按钮
    const copyBtn = createActionButton('复制', () => {
        copyToClipboard(content);
        showNotification('已复制到剪贴板');
    });
    actions.appendChild(copyBtn);

    // 用户消息添加重新编辑按钮
    if (role === 'user') {
        const editBtn = createActionButton('重新编辑', () => {
            elements.promptInput.value = content;
            elements.promptInput.focus();
            autoResizeTextarea();
        });
        actions.appendChild(editBtn);
    }

    return actions;
}

function createActionButton(text, onClick) {
    const btn = document.createElement('button');
    btn.className = 'message-action-btn';
    btn.textContent = text;
    btn.addEventListener('click', onClick);
    return btn;
}

function showThinkingIndicator() {
    // 检查是否已存在思考指示器
    if (elements.chatContainer.querySelector('.thinking-indicator')) {
        return;
    }

    const indicator = document.createElement('div');
    indicator.className = 'thinking-indicator';
    indicator.innerHTML = `
        <div class="loading-dots">
            <span></span>
            <span></span>
            <span></span>
        </div>
        <span>AI 正在思考...</span>
    `;

    elements.chatContainer.appendChild(indicator);
    scrollToBottom();
}

function removeThinkingIndicator() {
    const indicator = elements.chatContainer.querySelector('.thinking-indicator');
    if (indicator) {
        indicator.remove();
    }
}

function clearChatContainer() {
    elements.chatContainer.innerHTML = '';
    state.currentMessages = [];
    state.currentRoundMessages = [];
}

function scrollToBottom() {
    requestAnimationFrame(() => {
        elements.chatContainer.scrollTop = elements.chatContainer.scrollHeight;
    });
}

function setStreamingState(streaming) {
    state.isStreaming = streaming;
    elements.sendBtn.disabled = streaming;
    elements.promptInput.disabled = streaming;
}

// ==================== 历史记录管理 ====================
function getHistory() {
    try {
        const history = localStorage.getItem(STORAGE_KEY);
        return history ? JSON.parse(history) : [];
    } catch (e) {
        console.error('读取历史记录失败:', e);
        return [];
    }
}

function saveHistory(history) {
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(history));
    } catch (e) {
        console.error('保存历史记录失败:', e);
    }
}

function saveMessageToHistory(message) {
    const history = getHistory();
    history.push(message);

    // 保留最近 1000 条记录
    if (history.length > 1000) {
        history.shift();
    }

    saveHistory(history);

    // 更新侧边栏
    loadHistoryList();
}

function saveAssistantMessageToHistory() {
    // 只保存当前轮次的助手消息（避免重复保存）
    state.currentRoundMessages.forEach(msg => {
        if (msg.content && msg.role === 'assistant') {
            saveMessageToHistory({
                sessionId: state.currentSessionId,
                role: 'assistant',
                content: msg.content,
                eventType: msg.eventType,
                timestamp: msg.timestamp
            });
        }
    });

    // 清空当前轮次消息记录
    state.currentRoundMessages = [];
}

function loadSessionHistory(sessionId) {
    const history = getHistory();
    const sessionMessages = history.filter(m => m.sessionId === sessionId);

    if (sessionMessages.length === 0) {
        return;
    }

    // 隐藏欢迎界面
    elements.welcomeScreen.classList.add('hidden');

    // 按时间排序
    sessionMessages.sort((a, b) => a.timestamp - b.timestamp);

    // 渲染消息
    sessionMessages.forEach(msg => {
        if (msg.role === 'user') {
            appendMessage('user', msg.content, null, true);
        } else if (msg.role === 'assistant') {
            // 使用保存的 eventType，如果没有则默认为 MODEL
            const eventType = msg.eventType || SSE_EVENTS.MODEL;
            appendMessage('assistant', msg.content, eventType, true);
        }
    });
}

function loadHistoryList() {
    const history = getHistory();

    // 按 sessionId 分组
    const sessions = new Map();

    history.forEach(msg => {
        if (!sessions.has(msg.sessionId)) {
            sessions.set(msg.sessionId, {
                sessionId: msg.sessionId,
                messages: [],
                firstTimestamp: msg.timestamp,
                lastTimestamp: msg.timestamp
            });
        }

        const session = sessions.get(msg.sessionId);
        session.messages.push(msg);
        session.lastTimestamp = Math.max(session.lastTimestamp, msg.timestamp);
    });

    // 转换为数组并按最后时间倒序排序
    const sessionList = Array.from(sessions.values())
        .sort((a, b) => b.lastTimestamp - a.lastTimestamp);

    // 渲染
    renderHistoryList(sessionList);
}

function renderHistoryList(sessionList) {
    if (sessionList.length === 0) {
        elements.historyList.innerHTML = '<div style="padding: 20px; text-align: center; color: var(--text-secondary); font-size: 13px;">暂无历史记录</div>';
        return;
    }

    elements.historyList.innerHTML = sessionList.map(session => {
        // 获取第一条用户消息作为标题
        const firstUserMsg = session.messages.find(m => m.role === 'user');
        const title = firstUserMsg ? firstUserMsg.content.substring(0, 30) : '新对话';
        const timeStr = formatRelativeTime(session.lastTimestamp);

        return `
            <div class="history-item ${session.sessionId === state.currentSessionId ? 'active' : ''}"
                 data-session-id="${session.sessionId}">
                <div class="history-item-content">
                    <div class="history-item-title">${escapeHtml(title)}${title.length > 30 ? '...' : ''}</div>
                    <div class="history-item-time">${timeStr}</div>
                </div>
                <div class="history-item-actions">
                    <button class="history-item-btn btn-delete" title="删除">
                        <svg width="14" height="14" viewBox="0 0 14 14" fill="currentColor">
                            <path d="M5 1h4M1 3h12M11 3v9a1 1 0 01-1 1H4a1 1 0 01-1-1V3"/>
                        </svg>
                    </button>
                </div>
            </div>
        `;
    }).join('');

    // 绑定点击事件
    elements.historyList.querySelectorAll('.history-item').forEach(item => {
        const sessionId = item.dataset.sessionId;

        // 点击切换会话
        item.addEventListener('click', (e) => {
            // 如果点击的是删除按钮，不切换会话
            if (e.target.closest('.btn-delete')) {
                return;
            }
            switchSession(sessionId);
        });

        // 删除按钮
        const deleteBtn = item.querySelector('.btn-delete');
        if (deleteBtn) {
            deleteBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                deleteSession(sessionId);
            });
        }
    });
}

function filterHistory() {
    const searchText = elements.historySearchInput.value.toLowerCase().trim();

    if (!searchText) {
        loadHistoryList();
        return;
    }

    const history = getHistory();
    const sessions = new Map();

    history.forEach(msg => {
        // 搜索消息内容
        if (msg.content.toLowerCase().includes(searchText)) {
            if (!sessions.has(msg.sessionId)) {
                sessions.set(msg.sessionId, {
                    sessionId: msg.sessionId,
                    messages: [],
                    firstTimestamp: msg.timestamp,
                    lastTimestamp: msg.timestamp
                });
            }

            const session = sessions.get(msg.sessionId);
            session.messages.push(msg);
            session.lastTimestamp = Math.max(session.lastTimestamp, msg.timestamp);
        }
    });

    const sessionList = Array.from(sessions.values())
        .sort((a, b) => b.lastTimestamp - a.lastTimestamp);

    renderHistoryList(sessionList);
}

function deleteSession(sessionId) {
    if (!confirm('确定要删除这个对话吗？')) {
        return;
    }

    const history = getHistory();
    const filteredHistory = history.filter(m => m.sessionId !== sessionId);
    saveHistory(filteredHistory);

    // 如果删除的是当前会话，创建新会话
    if (sessionId === state.currentSessionId) {
        generateNewSessionId();
    }

    loadHistoryList();
    showNotification('对话已删除');
}

function updateHistoryListSelection() {
    elements.historyList.querySelectorAll('.history-item').forEach(item => {
        if (item.dataset.sessionId === state.currentSessionId) {
            item.classList.add('active');
        } else {
            item.classList.remove('active');
        }
    });
}

// ==================== 工具函数 ====================
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function copyToClipboard(text) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).catch(err => {
            console.error('复制失败:', err);
            fallbackCopyToClipboard(text);
        });
    } else {
        fallbackCopyToClipboard(text);
    }
}

function fallbackCopyToClipboard(text) {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    try {
        document.execCommand('copy');
    } catch (err) {
        console.error('复制失败:', err);
    }
    document.body.removeChild(textarea);
}

function showNotification(message) {
    // 移除已存在的通知
    const existingNotification = document.querySelector('.notification');
    if (existingNotification) {
        existingNotification.remove();
    }

    const notification = document.createElement('div');
    notification.className = 'notification';
    notification.textContent = message;
    document.body.appendChild(notification);

    setTimeout(() => {
        notification.classList.add('hide');
        setTimeout(() => notification.remove(), 300);
    }, 2500);
}

function formatTime(timestamp) {
    const date = new Date(timestamp);
    const hours = date.getHours().toString().padStart(2, '0');
    const minutes = date.getMinutes().toString().padStart(2, '0');
    return `${hours}:${minutes}`;
}

function formatRelativeTime(timestamp) {
    const now = Date.now();
    const diff = now - timestamp;

    const seconds = Math.floor(diff / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (seconds < 60) {
        return '刚刚';
    } else if (minutes < 60) {
        return `${minutes}分钟前`;
    } else if (hours < 24) {
        return `${hours}小时前`;
    } else if (days < 7) {
        return `${days}天前`;
    } else {
        const date = new Date(timestamp);
        const month = (date.getMonth() + 1).toString().padStart(2, '0');
        const day = date.getDate().toString().padStart(2, '0');
        return `${month}-${day}`;
    }
}

function autoResizeTextarea() {
    const textarea = elements.promptInput;
    textarea.style.height = 'auto';
    const newHeight = Math.min(textarea.scrollHeight, 200);
    textarea.style.height = newHeight + 'px';
}

// ==================== 启动 ====================
document.addEventListener('DOMContentLoaded', init);

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
    chatContainer: document.getElementById('chatContainer'),
    promptInput: document.getElementById('promptInput'),
    sendBtn: document.getElementById('sendBtn'),
    sessionIdInput: document.getElementById('sessionIdInput'),
    setSessionBtn: document.getElementById('setSessionBtn'),
    newChatBtn: document.getElementById('newChatBtn'),
    historyList: document.getElementById('historyList')
};

// ==================== 状态管理 ====================
let state = {
    currentSessionId: null,
    currentHistory: [],
    isStreaming: false,
    eventSources: new Map() // 存储活跃的 EventSource 连接
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
    marked.setOptions({
        breaks: true,
        gfm: true
    });
}

// ==================== 事件绑定 ====================
function bindEvents() {
    // 发送按钮
    elements.sendBtn.addEventListener('click', sendMessage);

    // 回车发送（Ctrl/Cmd + Enter 换行）
    elements.promptInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            if (!e.ctrlKey && !e.metaKey) {
                sendMessage();
            }
        }
    });

    // 设置 sessionId
    elements.setSessionBtn.addEventListener('click', () => {
        const newSessionId = elements.sessionIdInput.value.trim();
        if (newSessionId) {
            switchSession(newSessionId);
        }
    });

    // 新建对话
    elements.newChatBtn.addEventListener('click', () => {
        generateNewSessionId();
    });

    // 窗口关闭时清理连接
    window.addEventListener('beforeunload', cleanupAllConnections);
}

// ==================== Session 管理 ====================
function generateNewSessionId() {
    const sessionId = 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
    switchSession(sessionId);
}

function switchSession(sessionId) {
    // 关闭当前连接
    closeCurrentConnection();

    // 更新状态
    state.currentSessionId = sessionId;
    elements.sessionIdInput.value = sessionId;

    // 清空聊天界面
    clearChatContainer();

    // 加载该会话的历史消息
    loadSessionHistory(sessionId);

    // 更新侧边栏选中状态
    updateHistoryListSelection(sessionId);
}

function closeCurrentConnection() {
    if (state.eventSources.has(state.currentSessionId)) {
        const eventSource = state.eventSources.get(state.currentSessionId);
        eventSource.close();
        state.eventSources.delete(state.currentSessionId);
    }
}

function cleanupAllConnections() {
    state.eventSources.forEach((es) => es.close());
    state.eventSources.clear();
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

    // 显示用户消息
    appendMessage('user', prompt);

    // 保存到历史记录
    saveToHistory({
        sessionId: state.currentSessionId,
        role: 'user',
        content: prompt,
        timestamp: Date.now()
    });

    // 清空输入框
    elements.promptInput.value = '';

    // 禁用发送按钮
    setStreamingState(true);

    // 建立 SSE 连接
    connectSSE(prompt);
}

function connectSSE(prompt) {
    // 关闭之前的连接（如果有）
    closeCurrentConnection();

    const sessionId = state.currentSessionId;
    const url = `/stream/mem/agent?prompt=${encodeURIComponent(prompt)}&sessionId=${encodeURIComponent(sessionId)}`;

    try {
        const eventSource = new EventSource(url);
        state.eventSources.set(sessionId, eventSource);

        // 连接打开
        eventSource.onopen = () => {
            console.log('SSE 连接已建立');
        };

        // 监听模型响应事件
        eventSource.addEventListener(SSE_EVENTS.MODEL, (event) => {
            handleSSEEvent(SSE_EVENTS.MODEL, event.data, {
                sessionId,
                appendMessage,
                updateLastMessage,
                setStreamingState
            });
        });

        // 监听工具调用事件
        eventSource.addEventListener(SSE_EVENTS.TOOL, (event) => {
            handleSSEEvent(SSE_EVENTS.TOOL, event.data, {
                sessionId,
                appendMessage,
                updateLastMessage,
                setStreamingState
            });
        });

        // 监听思考过程事件
        eventSource.addEventListener(SSE_EVENTS.THINKING, (event) => {
            handleSSEEvent(SSE_EVENTS.THINKING, event.data, {
                sessionId,
                appendMessage,
                updateLastMessage,
                setStreamingState
            });
        });

        // 监听上下文压缩事件
        eventSource.addEventListener(SSE_EVENTS.CONTEXT, (event) => {
            handleSSEEvent(SSE_EVENTS.CONTEXT, event.data, {
                sessionId,
                appendMessage,
                updateLastMessage,
                setStreamingState
            });
        });

        // 监听完成事件
        eventSource.addEventListener(SSE_EVENTS.COMPLETE, (event) => {
            handleSSEEvent(SSE_EVENTS.COMPLETE, event.data, {
                sessionId,
                appendMessage,
                updateLastMessage,
                setStreamingState
            });
            eventSource.close();
            state.eventSources.delete(sessionId);
        });

        // 监听错误事件
        eventSource.addEventListener(SSE_EVENTS.ERROR, (event) => {
            handleSSEEvent(SSE_EVENTS.ERROR, event.data, {
                sessionId,
                appendMessage,
                updateLastMessage,
                setStreamingState
            });
            eventSource.close();
            state.eventSources.delete(sessionId);
        });

        // 监听超时事件
        eventSource.addEventListener(SSE_EVENTS.TIMEOUT, (event) => {
            handleSSEEvent(SSE_EVENTS.TIMEOUT, event.data, {
                sessionId,
                appendMessage,
                updateLastMessage,
                setStreamingState
            });
            eventSource.close();
            state.eventSources.delete(sessionId);
        });

        // 连接错误处理
        eventSource.onerror = (error) => {
            console.error('SSE 连接错误:', error);
            setStreamingState(false);

            // 检查连接状态
            if (eventSource.readyState === EventSource.CLOSED) {
                console.log('SSE 连接已关闭');
            }

            eventSource.close();
            state.eventSources.delete(sessionId);
        };

    } catch (error) {
        console.error('创建 SSE 连接失败:', error);
        setStreamingState(false);
        appendMessage('assistant', '', SSE_EVENTS.ERROR, '连接失败，请重试');
    }
}

// ==================== SSE 事件处理 ====================
function handleSSEEvent(eventName, content, handlers) {
    const { appendMessage, updateLastMessage, setStreamingState } = handlers;

    switch (eventName) {
        case SSE_EVENTS.MODEL:
            // 模型响应 - 累积显示
            updateLastMessage(SSE_EVENTS.MODEL, content, true);
            break;

        case SSE_EVENTS.TOOL:
            // 工具调用
            appendMessage('assistant', '', SSE_EVENTS.TOOL, content);
            break;

        case SSE_EVENTS.THINKING:
            // 思考过程
            appendMessage('assistant', '', SSE_EVENTS.THINKING, content);
            break;

        case SSE_EVENTS.CONTEXT:
            // 上下文压缩信息
            appendMessage('assistant', '', SSE_EVENTS.CONTEXT, content);
            break;

        case SSE_EVENTS.COMPLETE:
            // 完成 - 保存助手消息到历史
            setStreamingState(false);
            appendMessage('assistant', '', SSE_EVENTS.COMPLETE, '响应完成');

            // 获取最后一条模型消息并保存
            saveToHistory({
                sessionId: state.currentSessionId,
                role: 'assistant',
                content: '[COMPLETE]',
                timestamp: Date.now()
            });
            break;

        case SSE_EVENTS.ERROR:
            // 错误
            setStreamingState(false);
            appendMessage('assistant', '', SSE_EVENTS.ERROR, content);
            break;

        case SSE_EVENTS.TIMEOUT:
            // 超时
            setStreamingState(false);
            appendMessage('assistant', '', SSE_EVENTS.TIMEOUT, '响应超时');
            break;

        default:
            // 未知事件类型，也显示
            if (eventName && content) {
                appendMessage('assistant', '', eventName, content);
            }
    }
}

// ==================== 消息显示 ====================
function appendMessage(role, content, eventType = null, rawContent = null) {
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${role}`;

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

    if (rawContent !== null) {
        // 显示原始内容（用于事件消息）
        contentDiv.textContent = rawContent;
    } else if (content) {
        // 渲染 Markdown
        contentDiv.innerHTML = marked.parse(content);
    }

    bubble.appendChild(contentDiv);
    messageDiv.appendChild(bubble);

    elements.chatContainer.appendChild(messageDiv);
    scrollToBottom();

    return messageDiv;
}

function updateLastMessage(eventType, content, isStreaming = false) {
    const messages = elements.chatContainer.querySelectorAll('.message.assistant');
    if (messages.length === 0) {
        // 没有助手消息，创建一个
        appendMessage('assistant', '', eventType, content);
        return;
    }

    const lastMessage = messages[messages.length - 1];
    const lastEventTag = lastMessage.querySelector('.event-tag');
    const contentDiv = lastMessage.querySelector('.markdown-content');

    // 检查最后一个事件类型是否相同
    if (lastEventTag && lastEventTag.dataset.event === eventType) {
        // 追加内容
        const currentText = contentDiv.textContent;
        contentDiv.textContent = currentText + content;

        // 如果是流式响应，重新渲染 Markdown
        if (contentDiv.innerHTML.includes('```')) {
            const fullText = contentDiv.textContent;
            contentDiv.innerHTML = marked.parse(fullText);
        }
    } else {
        // 不同事件类型，创建新消息
        appendMessage('assistant', '', eventType, content);
    }

    if (!isStreaming) {
        scrollToBottom();
    }
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

function clearChatContainer() {
    elements.chatContainer.innerHTML = '';
}

function scrollToBottom() {
    elements.chatContainer.scrollTop = elements.chatContainer.scrollHeight;
}

function setStreamingState(streaming) {
    state.isStreaming = streaming;
    elements.sendBtn.disabled = streaming;
    elements.sendBtn.textContent = streaming ? '思考中...' : '发送';
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

function saveToHistory(message) {
    const history = getHistory();
    history.push(message);

    // 只保留最近 100 条记录
    if (history.length > 100) {
        history.shift();
    }

    saveHistory(history);
    loadHistoryList();
}

function loadSessionHistory(sessionId) {
    const history = getHistory();
    const sessionMessages = history.filter(m => m.sessionId === sessionId);

    state.currentHistory = sessionMessages;

    // 渲染消息
    sessionMessages.forEach(msg => {
        if (msg.role === 'user') {
            appendMessage('user', msg.content);
        }
        // 跳过系统消息（如 [COMPLETE]）
    });
}

function loadHistoryList() {
    const history = getHistory();

    // 按 sessionId 分组，只保留每个 session 的第一条用户消息作为标题
    const sessions = new Map();

    history.forEach(msg => {
        if (msg.role === 'user') {
            if (!sessions.has(msg.sessionId)) {
                sessions.set(msg.sessionId, {
                    sessionId: msg.sessionId,
                    preview: msg.content.substring(0, 20),
                    timestamp: msg.timestamp
                });
            }
        }
    });

    // 转换为数组并按时间倒序排序
    const sessionList = Array.from(sessions.values())
        .sort((a, b) => b.timestamp - a.timestamp);

    // 渲染
    elements.historyList.innerHTML = sessionList.map(session => `
        <div class="history-item ${session.sessionId === state.currentSessionId ? 'active' : ''}"
             data-session-id="${session.sessionId}">
            ${escapeHtml(session.preview) || '新对话'}...
        </div>
    `).join('');

    // 绑定点击事件
    elements.historyList.querySelectorAll('.history-item').forEach(item => {
        item.addEventListener('click', () => {
            const sessionId = item.dataset.sessionId;
            switchSession(sessionId);
        });
    });
}

function updateHistoryListSelection(sessionId) {
    elements.historyList.querySelectorAll('.history-item').forEach(item => {
        if (item.dataset.sessionId === sessionId) {
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

function showNotification(message) {
    // 简单的通知实现
    const notification = document.createElement('div');
    notification.style.cssText = `
        position: fixed;
        top: 20px;
        left: 50%;
        transform: translateX(-50%);
        background-color: #1976d2;
        color: white;
        padding: 12px 24px;
        border-radius: 8px;
        z-index: 1000;
        animation: fadeIn 0.3s ease;
    `;
    notification.textContent = message;
    document.body.appendChild(notification);

    setTimeout(() => {
        notification.style.opacity = '0';
        notification.style.transition = 'opacity 0.3s';
        setTimeout(() => notification.remove(), 300);
    }, 2000);
}

// ==================== 启动 ====================
document.addEventListener('DOMContentLoaded', init);

(() => {
  const state = { token: '', tab: 'dashboard', content: null, lawyer: null, consultation: null, consultations: [], consultationMessages: [] };
  let consultationSocket = null;
  const shell = document.querySelector('#app-shell');
  const loginShell = document.querySelector('#login-shell');
  const area = document.querySelector('#content-area');

  const escapeHtml = value => String(value ?? '').replace(/[&<>'"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c]);
  const requestJsonWithTimeout = async (url, options = {}, timeout = 15000) => {
    const controller = new AbortController();
    let timer;
    try {
      const request = (async () => {
        const response = await fetch(url, { ...options, signal: controller.signal });
        const body = await response.json().catch(() => null);
        return { response, body };
      })();
      const timeoutError = new Promise((_, reject) => {
        timer = setTimeout(() => {
          controller.abort();
          reject(new Error('请求超时，请确认服务器正在运行后重试'));
        }, timeout);
      });
      return await Promise.race([request, timeoutError]);
    } catch (error) {
      if (error.name === 'AbortError') throw new Error('请求超时，请确认服务器正在运行后重试');
      throw error;
    } finally {
      clearTimeout(timer);
    }
  };
  const request = async (path, options = {}) => {
    const { response, body } = await requestJsonWithTimeout(`/api/admin${path}`, {
      ...options,
      headers: { 'Content-Type': 'application/json', 'X-Admin-Token': state.token, ...(options.headers || {}) }
    });
    if (!response.ok || !body || body.code !== 200) throw new Error((body && body.message) || '请求失败');
    return body.data;
  };
  const showError = error => alert(error.message || '操作失败，请稍后重试');
  const formatTime = value => value ? String(value).replace('T', ' ').slice(0, 16) : '-';
  const status = value => `<span class="status ${value !== 'pending' ? 'done' : ''}">${escapeHtml(value || 'pending')}</span>`;
  async function render() {
    document.querySelectorAll('.sidebar button').forEach(button => button.classList.toggle('active', button.dataset.tab === state.tab));
    try {
      if (state.tab === 'dashboard') await renderDashboard();
      if (state.tab === 'consultations') await renderConsultations();
      if (state.tab === 'contracts') await renderContracts();
      if (state.tab === 'content') await renderContent();
      if (state.tab === 'lawyers') await renderLawyers();
      if (state.tab === 'templates') await renderTemplates();
      if (state.tab === 'notices') await renderNotices();
      if (state.tab === 'feedbacks') await renderFeedbacks();
    } catch (error) {
      area.innerHTML = `<div class="panel"><p class="form-message">${escapeHtml(error.message)}</p></div>`;
    }
  }

  async function renderDashboard() {
    const data = await request('/dashboard');
    const metrics = [['用户', data.userCount], ['咨询', data.consultationCount], ['合同', data.contractCount], ['待处理咨询', data.pendingConsultations], ['待审核合同', data.pendingContracts], ['模板', data.templateCount]];
    area.innerHTML = `<h2>概览</h2><div class="metrics">${metrics.map(([name, value]) => `<div class="metric"><span>${name}</span><strong>${value || 0}</strong></div>`).join('')}</div>`;
  }

  async function renderConsultations() {
    if (state.consultation) {
      await renderConsultationChat();
      return;
    }
    const list = await request('/consultations');
    state.consultations = list;
    area.innerHTML = `<div class="toolbar"><h2>咨询处理</h2><button class="secondary" onclick="adminApp.refresh()">刷新</button></div>${table(list, ['标题', '类型', '指定律师', '联系方式', '提交时间', '状态', '会话'], item => `<tr><td><strong>${escapeHtml(item.title)}</strong><div class="muted">${escapeHtml(item.content)}</div></td><td>${escapeHtml(item.type)}</td><td>${item.lawyerId ? escapeHtml(item.lawyerName || ('律师 #' + item.lawyerId)) : '-'}</td><td>${escapeHtml(item.phone || '-')}</td><td>${formatTime(item.createdTime)}</td><td>${status(item.status)}</td><td><button class="secondary" onclick="adminApp.openConsultation(${item.id}, ${item.lawyerId ? 'true' : 'false'}, '${item.status}')">${item.lawyerId ? '处理预约' : '打开会话'}</button></td></tr>`)}`;
  }

  const chatMessageHtml = message => `<div class="consult-chat-message ${message.senderRole === 'admin' ? 'outgoing' : 'incoming'}"><div class="consult-chat-role">${message.senderRole === 'admin' ? '管理台' : '用户'} · ${formatTime(message.createdTime)}</div><div class="consult-chat-bubble">${escapeHtml(message.content)}</div></div>`;
  const renderConsultationMessages = () => {
    const thread = document.querySelector('#consult-chat-thread');
    if (!thread) return;
    thread.innerHTML = state.consultationMessages.length ? state.consultationMessages.map(chatMessageHtml).join('') : '<p class="muted">暂无会话消息</p>';
    thread.scrollTop = thread.scrollHeight;
  };
  const appendConsultationMessage = message => {
    if (!message || state.consultationMessages.some(item => String(item.id) === String(message.id))) return;
    state.consultationMessages = [...state.consultationMessages, message];
    renderConsultationMessages();
  };
  const connectConsultationSocket = consultationId => {
    if (consultationSocket) consultationSocket.close();
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    consultationSocket = new WebSocket(`${protocol}//${location.host}/ws/chat?role=admin&consultationId=${consultationId}`);
    consultationSocket.onmessage = event => {
      try {
        if (state.consultation && state.consultation.id === consultationId) appendConsultationMessage(JSON.parse(event.data));
      } catch (_) { }
    };
  };
  async function renderConsultationChat() {
    const consultationId = state.consultation.id;
    const isBooking = Boolean(state.consultation.isBooking);
    const isFinished = ['closed', 'cancelled', 'declined', 'completed'].includes(state.consultation.status);
    const bookingStatuses = [['pending', '待确认'], ['processing', '处理中'], ['confirmed', '已确认'], ['need_info', '需补充'], ['declined', '无法承接'], ['completed', '已完成']];
    const appointmentTime = state.consultation.appointmentTime ? String(state.consultation.appointmentTime).slice(0, 16) : '';
    const bookingPanel = isBooking ? `<div class="booking-panel"><h3>预约安排</h3><div class="booking-fields"><label>处理状态<select id="booking-status">${bookingStatuses.map(([value, label]) => `<option value="${value}" ${state.consultation.status === value ? 'selected' : ''}>${label}</option>`).join('')}</select></label><label>预约时间<input id="booking-time" type="datetime-local" value="${appointmentTime}"></label><label>沟通方式<input id="booking-contact" value="${escapeHtml(state.consultation.contactMethod || '')}" placeholder="例如：电话沟通、微信视频"></label><label class="full">处理说明<textarea id="booking-note" placeholder="确认、需补充或无法承接时，请填写清楚说明">${escapeHtml(state.consultation.bookingNote || '')}</textarea></label></div><button onclick="adminApp.saveBooking()" ${isFinished ? 'disabled' : ''}>保存预约处理结果</button></div>` : `<div class="consult-chat-composer"><textarea id="consult-chat-input" placeholder="输入处理回复，用户将即时收到" ${isFinished ? 'disabled' : ''}></textarea><button onclick="adminApp.sendConsultationMessage()" ${isFinished ? 'disabled' : ''}>发送</button></div>`;
    state.consultationMessages = await request(`/consultations/${consultationId}/messages`);
    area.innerHTML = `<div class="toolbar"><div><button class="secondary" onclick="adminApp.closeConsultation()">返回列表</button><h2>${isBooking ? '律师预约处理' : '咨询会话'} #${consultationId}</h2></div><button class="secondary" onclick="adminApp.refreshConsultation()">刷新</button></div><div class="consult-chat-panel"><div class="consult-chat-thread" id="consult-chat-thread"></div>${bookingPanel}</div>`;
    renderConsultationMessages();
    connectConsultationSocket(consultationId);
  }

  async function renderContracts() {
    const list = await request('/contracts');
    area.innerHTML = `<div class="toolbar"><h2>合同审核</h2><button class="secondary" onclick="adminApp.refresh()">刷新</button></div>${table(list, ['合同', '用户', '提交时间', '状态', '审核'], item => `<tr><td><strong>${escapeHtml(item.title)}</strong><div class="muted">${escapeHtml(item.fileName || '无附件')}</div></td><td>${item.userId}</td><td>${formatTime(item.createdTime)}</td><td>${status(item.status)}</td><td><div class="row-actions">${item.fileName ? `<button class="secondary" onclick="adminApp.downloadContract(${item.id})">下载原件</button>` : ''}</div><select id="contract-status-${item.id}"><option value="pending" ${item.status === 'pending' ? 'selected' : ''}>待审核</option><option value="processing" ${item.status === 'processing' ? 'selected' : ''}>审核中</option><option value="reviewed" ${item.status === 'reviewed' ? 'selected' : ''}>已完成</option><option value="rejected" ${item.status === 'rejected' ? 'selected' : ''}>需补充</option></select><textarea id="contract-review-${item.id}" placeholder="填写真实审核结论">${escapeHtml(item.reviewResult || '')}</textarea><button onclick="adminApp.saveContract(${item.id})">保存审核结果</button></td></tr>`)}`;
  }

  const contentForm = item => `<div class="panel"><h3>${item ? '编辑内容' : '发布内容'}</h3><form id="content-form"><div class="form-grid"><label>类型<select name="contentType"><option value="article">普法文章</option><option value="law">法规动态</option><option value="book">法规阅读</option><option value="video">视频</option></select></label><label>标题<input name="title" required value="${escapeHtml(item?.title || '')}"></label><label class="full">摘要<textarea name="summary">${escapeHtml(item?.summary || '')}</textarea></label><label class="full">正文<textarea name="content">${escapeHtml(item?.content || '')}</textarea></label><label>来源名称<input name="sourceName" value="${escapeHtml(item?.sourceName || '')}"></label><label>来源链接<input name="sourceUrl" type="url" value="${escapeHtml(item?.sourceUrl || '')}"></label><label>封面链接<input name="coverUrl" type="url" value="${escapeHtml(item?.coverUrl || '')}"></label><label>内容附件<input name="file" type="file" accept=".pdf,.doc,.docx,.txt"><span class="field-hint">书籍可上传 PDF、DOC、DOCX、TXT，最大 10MB${item?.fileName ? `；当前：${escapeHtml(item.fileName)}` : ''}</span></label><label>发布状态<select name="isPublished"><option value="true">发布</option><option value="false">保存为未发布</option></select></label></div><div class="form-actions"><button type="submit">${item ? '保存修改' : '发布内容'}</button>${item ? '<button type="button" class="secondary" onclick="adminApp.cancelContentEdit()">取消</button>' : ''}</div></form></div>`;
  async function renderContent() {
    const type = state.contentType || 'article';
    const list = await request(`/content?type=${type}`);
    area.innerHTML = `<div class="toolbar"><h2>内容发布</h2><div><select id="content-type-filter" onchange="adminApp.changeContentType(this.value)"><option value="article">普法文章</option><option value="law">法规动态</option><option value="book">法规阅读</option><option value="video">视频</option></select><button class="secondary" onclick="adminApp.refresh()">刷新</button></div></div>${contentForm(state.content)}${table(list, ['标题', '状态', '更新时间', '操作'], item => `<tr><td><strong>${escapeHtml(item.title)}</strong><div class="muted">${escapeHtml(item.summary || '')}</div></td><td>${status(item.isPublished ? '已发布' : '未发布')}</td><td>${formatTime(item.publishedTime || item.createdTime)}</td><td><div class="row-actions"><button class="secondary" onclick="adminApp.editContent(${item.id})">编辑</button><button class="danger" onclick="adminApp.deleteContent(${item.id})">删除</button></div></td></tr>`)}`;
    document.querySelector('#content-type-filter').value = type;
    const form = document.querySelector('#content-form');
    form.contentType.value = state.content?.contentType || type;
    form.isPublished.value = String(state.content?.isPublished ?? true);
    form.onsubmit = event => { event.preventDefault(); saveContent(new FormData(form)); };
  }

  const lawyerForm = item => `<div class="panel"><h3>${item ? '编辑律师' : '新增律师'}</h3><form id="lawyer-form"><div class="form-grid"><label>姓名<input name="name" required value="${escapeHtml(item?.name || '')}"></label><label>律所<input name="lawFirm" value="${escapeHtml(item?.lawFirm || '')}"></label><label>擅长领域<input name="specialties" value="${escapeHtml(item?.specialties || '')}"></label><label>上传头像（可选）<input name="avatarFile" type="file" accept="image/jpeg,image/png"><span class="field-hint">支持 JPG、PNG，最大 3MB</span></label><label>头像链接（备用）<input name="avatarUrl" type="url" value="${escapeHtml(item?.avatarUrl || '')}" placeholder="不上传时可填写外部图片链接"></label>${item?.avatarUrl ? `<div class="avatar-preview"><img src="${escapeHtml(item.avatarUrl)}" alt="当前头像"><span>当前头像</span></div>` : ''}<label class="full">介绍<textarea name="introduction">${escapeHtml(item?.introduction || '')}</textarea></label><label>预约状态<select name="isAvailable"><option value="true">可预约</option><option value="false">暂停预约</option></select></label></div><div class="form-actions"><button type="submit">${item ? '保存修改' : '新增律师'}</button>${item ? '<button type="button" class="secondary" onclick="adminApp.cancelLawyerEdit()">取消</button>' : ''}</div></form></div>`;
  async function renderLawyers() {
    const list = await request('/lawyers');
    area.innerHTML = `<div class="toolbar"><h2>律师管理</h2><button class="secondary" onclick="adminApp.refresh()">刷新</button></div>${lawyerForm(state.lawyer)}${table(list, ['律师', '律所', '领域', '状态', '操作'], item => `<tr><td><strong>${escapeHtml(item.name)}</strong><div class="muted">${escapeHtml(item.introduction || '')}</div></td><td>${escapeHtml(item.lawFirm || '-')}</td><td>${escapeHtml(item.specialties || '-')}</td><td>${status(item.isAvailable ? '可预约' : '暂停')}</td><td><div class="row-actions"><button class="secondary" onclick="adminApp.editLawyer(${item.id})">编辑</button><button class="danger" onclick="adminApp.deleteLawyer(${item.id})">删除</button></div></td></tr>`)}`;
    const form = document.querySelector('#lawyer-form');
    form.isAvailable.value = String(state.lawyer?.isAvailable ?? true);
    form.onsubmit = event => { event.preventDefault(); saveLawyer(new FormData(form)); };
  }

  async function renderTemplates() {
    const list = await request('/templates');
    area.innerHTML = `<div class="toolbar"><h2>文书模板</h2><button class="secondary" onclick="adminApp.refresh()">刷新</button></div><div class="panel"><h3>新增模板</h3><form id="template-form"><div class="form-grid"><label>标题<input name="title" required></label><label>分类<input name="category" required placeholder="civil / contract 等"></label><label class="full">说明<textarea name="description"></textarea></label><label class="full">正文（可选）<textarea name="content"></textarea></label><label class="full">模板文件（可选）<input name="file" type="file" accept=".pdf,.doc,.docx,.txt"></label></div><div class="form-actions"><button type="submit">保存模板</button></div></form></div>${table(list, ['标题', '分类', '附件', '使用次数', '操作'], item => `<tr><td>${escapeHtml(item.title)}</td><td>${escapeHtml(item.category || '-')}</td><td>${item.fileName ? escapeHtml(item.fileName) : '无'}</td><td>${item.downloadCount || 0}</td><td><button class="danger" onclick="adminApp.deleteTemplate(${item.id})">删除</button></td></tr>`)}`;
    document.querySelector('#template-form').onsubmit = event => { event.preventDefault(); saveTemplate(new FormData(event.currentTarget)); };
  }

  async function renderNotices() {
    const list = await request('/notices');
    area.innerHTML = `<div class="toolbar"><h2>系统通知</h2><button class="secondary" onclick="adminApp.refresh()">刷新</button></div><div class="panel"><h3>发布通知</h3><form id="notice-form"><div class="form-grid"><label class="full">标题<input name="title" required></label><label class="full">内容<textarea name="content" required></textarea></label></div><div class="form-actions"><button type="submit">发布通知</button></div></form></div>${table(list, ['标题', '内容', '发布时间', '操作'], item => `<tr><td>${escapeHtml(item.title)}</td><td>${escapeHtml(item.content)}</td><td>${formatTime(item.createdTime)}</td><td><button class="danger" onclick="adminApp.deleteNotice(${item.id})">删除</button></td></tr>`)}`;
    document.querySelector('#notice-form').onsubmit = event => { event.preventDefault(); saveNotice(new FormData(event.currentTarget)); };
  }

  async function renderFeedbacks() {
    const list = await request('/feedbacks');
    area.innerHTML = `<div class="toolbar"><h2>意见反馈</h2><button class="secondary" onclick="adminApp.refresh()">刷新</button></div>${table(list, ['用户', '反馈内容', '提交时间', '处理'], item => `<tr><td>${item.userId}</td><td>${escapeHtml(item.content)}</td><td>${formatTime(item.createdTime)}</td><td><select id="feedback-status-${item.id}"><option value="pending" ${item.status === 'pending' ? 'selected' : ''}>待处理</option><option value="processing" ${item.status === 'processing' ? 'selected' : ''}>处理中</option><option value="resolved" ${item.status === 'resolved' ? 'selected' : ''}>已解决</option></select><textarea id="feedback-reply-${item.id}" placeholder="填写回复，用户将在消息中心看到">${escapeHtml(item.reply || '')}</textarea><button onclick="adminApp.saveFeedback(${item.id})">保存处理结果</button></td></tr>`)}`;
  }

  const table = (items, headings, row) => items.length ? `<div class="table-wrap"><table><thead><tr>${headings.map(x => `<th>${x}</th>`).join('')}</tr></thead><tbody>${items.map(row).join('')}</tbody></table></div>` : '<div class="panel empty">暂无数据</div>';
  const jsonForm = formData => Object.fromEntries([...formData.entries()].map(([key, value]) => [key, value instanceof File ? value : value.trim()]));
  const uploadContentFile = async (id, file) => {
    const upload = new FormData();
    upload.append('file', file);
    const { response, body } = await requestJsonWithTimeout(`/api/admin/content/${id}/file`, { method: 'POST', headers: { 'X-Admin-Token': state.token }, body: upload });
    if (!response.ok || !body || body.code !== 200) throw new Error((body && body.message) || '附件上传失败');
    return body.data;
  };
  const saveContent = async formData => {
    const data = jsonForm(formData);
    const file = data.file;
    delete data.file;
    data.isPublished = data.isPublished === 'true';
    try {
      const saved = state.content ? await request(`/content/${state.content.id}`, { method: 'PUT', body: JSON.stringify(data) }) : await request('/content', { method: 'POST', body: JSON.stringify(data) });
      if (file instanceof File && file.size) await uploadContentFile(saved.id, file);
      state.content = null;
      render();
    } catch (error) { showError(error); }
  };
  const uploadLawyerAvatar = async file => {
    const upload = new FormData();
    upload.append('file', file);
    const { response, body } = await requestJsonWithTimeout('/api/admin/lawyers/avatar', { method: 'POST', body: upload });
    if (!response.ok || !body || body.code !== 200 || !body.data?.url) throw new Error((body && body.message) || '头像上传失败');
    return body.data.url;
  };
  const saveLawyer = async formData => {
    const data = jsonForm(formData);
    const avatarFile = data.avatarFile;
    delete data.avatarFile;
    data.isAvailable = data.isAvailable === 'true';
    try {
      if (avatarFile instanceof File && avatarFile.size) data.avatarUrl = await uploadLawyerAvatar(avatarFile);
      state.lawyer ? await request(`/lawyers/${state.lawyer.id}`, { method: 'PUT', body: JSON.stringify(data) }) : await request('/lawyers', { method: 'POST', body: JSON.stringify(data) });
      state.lawyer = null;
      render();
    } catch (error) { showError(error); }
  };
  const saveTemplate = async formData => { try { const file = formData.get('file'); if (file && file.size) { const response = await fetch('/api/admin/templates/upload', { method: 'POST', headers: { 'X-Admin-Token': state.token }, body: formData }); const body = await response.json(); if (!response.ok || body.code !== 200) throw new Error(body.message || '上传失败'); } else { const data = jsonForm(formData); delete data.file; await request('/templates', { method: 'POST', body: JSON.stringify(data) }); } render(); } catch (error) { showError(error); } };
  const saveNotice = async formData => { try { await request('/notices', { method: 'POST', body: JSON.stringify(jsonForm(formData)) }); render(); } catch (error) { showError(error); } };

  window.adminApp = {
    refresh: render,
    changeTab: tab => { if (consultationSocket) consultationSocket.close(); consultationSocket = null; state.tab = tab; state.content = null; state.lawyer = null; state.consultation = null; render(); },
    changeContentType: type => { state.contentType = type; state.content = null; render(); },
    cancelContentEdit: () => { state.content = null; render(); },
    cancelLawyerEdit: () => { state.lawyer = null; render(); },
    editContent: async id => { try { state.content = await request(`/content/${id}`); render(); } catch (error) { showError(error); } },
    editLawyer: async id => { try { state.lawyer = await request(`/lawyers/${id}`); render(); } catch (error) { showError(error); } },
    openConsultation: (id, isBooking = false, status = 'pending') => { const item = state.consultations.find(entry => Number(entry.id) === Number(id)); state.consultation = item ? { ...item, isBooking: Boolean(item.lawyerId) } : { id, isBooking, status }; render(); },
    closeConsultation: () => { if (consultationSocket) consultationSocket.close(); consultationSocket = null; state.consultation = null; state.consultationMessages = []; render(); },
    refreshConsultation: () => renderConsultationChat().catch(showError),
    sendConsultationMessage: async () => { const input = document.querySelector('#consult-chat-input'); const content = input ? input.value.trim() : ''; if (!content) return; try { const message = await request(`/consultations/${state.consultation.id}/messages`, { method: 'POST', body: JSON.stringify({ content }) }); if (input) input.value = ''; appendConsultationMessage(message); } catch (error) { showError(error); } },
    saveBooking: async () => { const status = document.querySelector('#booking-status').value; const appointmentTime = document.querySelector('#booking-time').value; const contactMethod = document.querySelector('#booking-contact').value.trim(); const bookingNote = document.querySelector('#booking-note').value.trim(); try { const result = await request(`/consultations/${state.consultation.id}/booking`, { method: 'PUT', body: JSON.stringify({ status, appointmentTime, contactMethod, bookingNote }) }); state.consultation = { ...state.consultation, ...result.consultation }; await renderConsultationChat(); } catch (error) { showError(error); } },
    saveContract: async id => { try { await request(`/contracts/${id}`, { method: 'PUT', body: JSON.stringify({ status: document.querySelector(`#contract-status-${id}`).value, reviewResult: document.querySelector(`#contract-review-${id}`).value }) }); render(); } catch (error) { showError(error); } },
    saveFeedback: async id => { try { await request(`/feedbacks/${id}`, { method: 'PUT', body: JSON.stringify({ status: document.querySelector(`#feedback-status-${id}`).value, reply: document.querySelector(`#feedback-reply-${id}`).value.trim() }) }); render(); } catch (error) { showError(error); } },
    deleteContent: async id => { if (confirm('确定删除这条内容？')) try { await request(`/content/${id}`, { method: 'DELETE' }); render(); } catch (error) { showError(error); } },
    deleteLawyer: async id => { if (confirm('确定删除该律师？')) try { await request(`/lawyers/${id}`, { method: 'DELETE' }); render(); } catch (error) { showError(error); } },
    deleteTemplate: async id => { if (confirm('确定删除该模板？')) try { await request(`/templates/${id}`, { method: 'DELETE' }); render(); } catch (error) { showError(error); } },
    deleteNotice: async id => { if (confirm('确定删除该通知？')) try { await request(`/notices/${id}`, { method: 'DELETE' }); render(); } catch (error) { showError(error); } },
    downloadContract: async id => { try { const response = await fetch(`/api/admin/contracts/${id}/file`, { headers: { 'X-Admin-Token': state.token } }); if (!response.ok) throw new Error('文件下载失败'); const disposition = response.headers.get('Content-Disposition') || ''; const match = disposition.match(/filename\*=UTF-8''([^;]+)/); const link = document.createElement('a'); link.href = URL.createObjectURL(await response.blob()); link.download = match ? decodeURIComponent(match[1]) : '合同文件'; link.click(); setTimeout(() => URL.revokeObjectURL(link.href), 1000); } catch (error) { showError(error); } }
  };

  document.querySelector('#sidebar').addEventListener('click', event => { const tab = event.target.dataset.tab; if (tab) window.adminApp.changeTab(tab); });
  document.querySelector('#logout-button').addEventListener('click', () => { window.location.assign('/admin/logout'); });
  const enterConsole = () => {
    loginShell.classList.add('hidden');
    shell.classList.remove('hidden');
    window.scrollTo(0, 0);
    document.documentElement.scrollTop = 0;
    document.body.scrollTop = 0;
    render();
  };
  const loginResult = new URLSearchParams(window.location.search).get('login');
  if (loginResult === 'success') {
    window.history.replaceState({}, document.title, '/admin/index.html');
    enterConsole();
  } else if (loginResult === 'failed') {
    document.querySelector('#login-message').textContent = '管理员口令错误';
  } else {
    request('/dashboard').then(enterConsole).catch(() => {});
  }
})();

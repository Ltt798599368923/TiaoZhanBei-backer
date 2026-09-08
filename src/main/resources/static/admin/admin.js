(() => {
  const state = { token: '', tab: 'dashboard', content: null, lawyer: null, template: null, consultation: null, consultations: [], consultationMessages: [] };
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
  const templateCategoryLabels = {
    complaint: '起诉状 / 自诉状', defense: '答辩状', appeal: '上诉状', application: '申请书 / 申诉书',
    authorization: '授权委托', preservation: '保全措施', execution: '执行程序', statement: '意见 / 陈述', other: '其他文书',
    civil: '民事类（旧分类）', criminal: '刑事类（旧分类）', contract: '合同类（旧分类）',
    administrative: '行政类（旧分类）', company: '公司类（旧分类）'
  };
  const templatePracticeLabels = {
    civil_commercial: '民商事', criminal: '刑事', administrative: '行政', intellectual_property: '知识产权',
    state_compensation: '国家赔偿', enforcement: '执行', maritime: '海事', environmental: '环境资源', other: '其他领域'
  };
  const templateMaterialLabels = { template: '空白模板', example: '填写实例', guide: '填写说明' };
  const templateLabel = (labels, value) => labels[value] || value || '未分类';
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
    const metrics = [['用户', data.userCount], ['咨询', data.consultationCount], ['合同', data.contractCount], ['待处理咨询', data.pendingConsultations], ['待审核合同', data.pendingContracts], ['待处理反馈', data.pendingFeedbacks], ['模板', data.templateCount]];
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

  const contentForm = item => {
    return `<div class="panel"><h3>${item ? '编辑内容' : '发布内容'}</h3><form id="content-form"><div class="content-guide" id="content-guide"></div><div class="form-grid"><label>类型<select name="contentType" onchange="adminApp.updateContentForm(this.value)"><option value="article">普法文章（法理白话）</option><option value="law">法规动态（法治新程）</option><option value="book">法规阅读（在线阅读）</option><option value="video">视频（小视讲堂）</option></select></label><label>标题<input name="title" required value="${escapeHtml(item?.title || '')}"></label><label class="full">摘要<textarea name="summary" placeholder="列表页展示的简短介绍">${escapeHtml(item?.summary || '')}</textarea></label><label class="full">正文<textarea name="content">${escapeHtml(item?.content || '')}</textarea><span class="field-hint" id="content-body-hint"></span></label><label>来源名称（可选）<input name="sourceName" value="${escapeHtml(item?.sourceName || '')}" placeholder="例如：法视界编辑部"></label><label data-content-field="source-url">来源链接（可选）<input name="sourceUrl" type="url" value="${escapeHtml(item?.sourceUrl || '')}" placeholder="只作为权威来源参考，不影响在线阅读"></label><label>封面链接（可选）<input name="coverUrl" type="url" value="${escapeHtml(item?.coverUrl || '')}"></label><label data-content-field="attachment">法规资料附件（可选）<input name="file" type="file" accept=".pdf,.doc,.docx,.txt"><span class="field-hint">仅法规阅读可上传 PDF、DOC、DOCX、TXT，最大 10MB${item?.fileName ? `；当前：${escapeHtml(item.fileName)}` : ''}</span></label><label>发布状态<select name="isPublished"><option value="true">发布</option><option value="false">保存为未发布</option></select></label></div><div class="form-actions"><button type="submit">${item ? '保存修改' : '发布内容'}</button>${item ? '<button type="button" class="secondary" onclick="adminApp.cancelContentEdit()">取消</button>' : ''}</div></form></div>`;
  };

  const CONTENT_FORM_GUIDES = {
    article: {
      guide: '将发布到小程序「法理白话」。用户点击文章卡片后直接阅读正文，不需要附件或外部链接。',
      bodyHint: '必填。正文会直接显示在小程序文章详情页。',
      bodyPlaceholder: '请输入完整的普法文章正文'
    },
    law: {
      guide: '将发布到小程序「法治新程」。用户点击后可直接阅读正文，来源链接仅作为补充参考。',
      bodyHint: '必填。请填写法规动态的完整说明或解读正文。',
      bodyPlaceholder: '请输入法规动态正文'
    },
    book: {
      guide: '将发布到小程序「法规阅读」。用户优先在线阅读正文，PDF 或 Word 附件仅作补充资料。',
      bodyHint: '必填。请填写可直接在线阅读的完整法规正文。',
      bodyPlaceholder: '请输入法规全文或已审核的正文'
    },
    video: {
      guide: '将发布到小程序「小视讲堂」。请填写可直接播放的视频链接，正文可填写视频简介。',
      bodyHint: '可选。可填写视频简介、要点或文字稿。',
      bodyPlaceholder: '请输入视频简介或文字稿'
    }
  };

  const updateContentForm = type => {
    const form = document.querySelector('#content-form');
    const config = CONTENT_FORM_GUIDES[type] || CONTENT_FORM_GUIDES.article;
    if (!form) return;
    const body = form.querySelector('[name="content"]');
    const sourceUrl = form.querySelector('[name="sourceUrl"]');
    const sourceUrlField = form.querySelector('[data-content-field="source-url"]');
    const attachment = form.querySelector('[name="file"]');
    const attachmentField = form.querySelector('[data-content-field="attachment"]');
    const guide = document.querySelector('#content-guide');
    const bodyHint = document.querySelector('#content-body-hint');

    body.required = type !== 'video';
    body.placeholder = config.bodyPlaceholder;
    sourceUrl.required = type === 'video';
    sourceUrlField.hidden = type === 'article';
    sourceUrl.disabled = type === 'article';
    attachmentField.hidden = type !== 'book';
    attachment.disabled = type !== 'book';
    guide.textContent = config.guide;
    bodyHint.textContent = config.bodyHint;
  };
  async function renderContent() {
    const type = state.contentType || 'article';
    const list = await request(`/content?type=${type}`);
    const importPanel = type === 'book' ? `<div class="panel import-panel"><h3>导入法规审核清单</h3><p>首次导入会保存为未发布。请核对版本、正文和官方来源，再逐条发布。</p><div class="import-actions"><input id="legal-library-file" type="file" accept="application/json,.json"><button type="button" onclick="adminApp.importLegalLibrary()">导入审核清单</button></div></div>` : '';
    area.innerHTML = `<div class="toolbar"><h2>内容发布</h2><div><select id="content-type-filter" onchange="adminApp.changeContentType(this.value)"><option value="article">普法文章</option><option value="law">法规动态</option><option value="book">法规阅读</option><option value="video">视频</option></select><button class="secondary" onclick="adminApp.refresh()">刷新</button></div></div>${importPanel}${contentForm(state.content)}${table(list, ['标题', '状态', '更新时间', '操作'], item => `<tr><td><strong>${escapeHtml(item.title)}</strong><div class="muted">${escapeHtml(item.summary || '')}</div></td><td>${status(item.isPublished ? '已发布' : '未发布')}</td><td>${formatTime(item.publishedTime || item.createdTime)}</td><td><div class="row-actions"><button class="secondary" onclick="adminApp.editContent(${item.id})">编辑</button><button class="danger" onclick="adminApp.deleteContent(${item.id})">删除</button></div></td></tr>`)}`;
    document.querySelector('#content-type-filter').value = type;
    const form = document.querySelector('#content-form');
    form.contentType.value = state.content?.contentType || type;
    form.isPublished.value = String(state.content?.isPublished ?? true);
    updateContentForm(form.contentType.value);
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
    const item = state.template;
    const optionList = (choices, selected) => choices.map(([value, label]) => `<option value="${value}" ${selected === value ? 'selected' : ''}>${label}</option>`).join('');
    const categories = [
      ['complaint', '起诉状 / 自诉状'], ['defense', '答辩状'], ['appeal', '上诉状'], ['application', '申请书 / 申诉书'],
      ['authorization', '授权委托'], ['preservation', '保全措施'], ['execution', '执行程序'], ['statement', '意见 / 陈述'], ['other', '其他文书']
    ];
    const practiceAreas = [
      ['civil_commercial', '民商事'], ['criminal', '刑事'], ['administrative', '行政'], ['intellectual_property', '知识产权'],
      ['state_compensation', '国家赔偿'], ['enforcement', '执行'], ['maritime', '海事'], ['environmental', '环境资源'], ['other', '其他领域']
    ];
    const materialTypes = [['template', '空白模板'], ['example', '填写实例'], ['guide', '填写说明']];
    const selectedCategory = categories.some(([value]) => value === item?.category) ? item.category : 'other';
    const selectedPracticeArea = practiceAreas.some(([value]) => value === item?.practiceArea) ? item.practiceArea : 'other';
    const selectedMaterialType = materialTypes.some(([value]) => value === item?.materialType) ? item.materialType : 'template';
    area.innerHTML = `<div class="toolbar"><h2>文书模板</h2><button class="secondary" onclick="adminApp.refresh()">刷新</button></div>
      <div class="panel import-panel"><h3>导入模板审核清单</h3><p>审核后可导入模板、实例或填写说明。实例发布前务必完成当事人信息脱敏；导入的内容将直接作为小程序在线阅读正文。</p><div class="import-actions"><input id="template-library-file" type="file" accept="application/json,.json"><button type="button" onclick="adminApp.importTemplateLibrary()">导入审核清单</button></div></div>
      <div class="panel"><h3>${item ? '编辑文书' : '发布文书'}</h3><form id="template-form"><div class="form-grid">
        <label>文书标题<input name="title" required value="${escapeHtml(item?.title || '')}" placeholder="例如：买卖合同纠纷起诉状"></label>
        <label>文书类型<select name="category" required>${optionList(categories, selectedCategory)}</select></label>
        <label>业务领域<select name="practiceArea" required>${optionList(practiceAreas, selectedPracticeArea)}</select></label>
        <label>资料形态<select name="materialType" required>${optionList(materialTypes, selectedMaterialType)}</select><span class="field-hint">实例仅供参考，发布前请完成脱敏。</span></label>
        <label class="full">使用说明<textarea name="description" placeholder="说明适用场景、填写要点或注意事项">${escapeHtml(item?.description || '')}</textarea></label>
        <label class="full">在线阅读正文（推荐）<textarea name="content" placeholder="填写后，用户可直接在小程序中阅读和复制；仅上传原件时，用户将通过文档查看器打开。">${escapeHtml(item?.content || '')}</textarea></label>
        <label class="full">Word / PDF 原件${item?.fileName ? `（当前：${escapeHtml(item.fileName)}）` : ''}<input name="file" type="file" accept=".pdf,.doc,.docx,.txt"><span class="field-hint">在线正文与原件至少提供一种；上传新文件会替换当前原件。</span></label>
      </div><div class="form-actions"><button type="submit">${item ? '保存修改' : '发布文书'}</button>${item ? '<button type="button" class="secondary" onclick="adminApp.cancelTemplateEdit()">取消</button>' : ''}</div></form></div>
      ${table(list, ['文书', '分类', '阅读方式', '原件', '使用次数', '操作'], entry => `<tr><td><strong>${escapeHtml(entry.title)}</strong><div class="muted">${escapeHtml(entry.description || '')}</div></td><td><div class="template-badges"><span>${escapeHtml(templateLabel(templateCategoryLabels, entry.category))}</span><span>${escapeHtml(templateLabel(templatePracticeLabels, entry.practiceArea))}</span><span>${escapeHtml(templateLabel(templateMaterialLabels, entry.materialType))}</span></div></td><td>${entry.content ? '可在线阅读' : '仅原件查看'}</td><td>${entry.fileName ? escapeHtml(entry.fileName) : '无'}</td><td>${entry.downloadCount || 0}</td><td><div class="row-actions"><button class="secondary" onclick="adminApp.editTemplate(${entry.id})">编辑</button><button class="danger" onclick="adminApp.deleteTemplate(${entry.id})">删除</button></div></td></tr>`)}`;
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
  const saveTemplate = async formData => {
    try {
      const file = formData.get('file');
      if (file && file.size) {
        if (state.template) formData.append('id', state.template.id);
        const response = await fetch('/api/admin/templates/upload', { method: 'POST', headers: { 'X-Admin-Token': state.token }, body: formData });
        const body = await response.json();
        if (!response.ok || body.code !== 200) throw new Error(body.message || '上传失败');
      } else {
        const data = jsonForm(formData);
        delete data.file;
        if (state.template) await request(`/templates/${state.template.id}`, { method: 'PUT', body: JSON.stringify(data) });
        else await request('/templates', { method: 'POST', body: JSON.stringify(data) });
      }
      state.template = null;
      render();
    } catch (error) { showError(error); }
  };
  const saveNotice = async formData => { try { await request('/notices', { method: 'POST', body: JSON.stringify(jsonForm(formData)) }); render(); } catch (error) { showError(error); } };
  const importManifest = async (inputId, path, label) => {
    const file = document.querySelector(`#${inputId}`)?.files?.[0];
    if (!file) throw new Error('请先选择 JSON 审核清单');
    let manifest;
    try {
      manifest = JSON.parse(await file.text());
    } catch (_) {
      throw new Error('无法读取清单，请选择有效的 JSON 文件');
    }
    const items = Array.isArray(manifest) ? manifest : manifest.items;
    if (!Array.isArray(items) || !items.length) throw new Error('清单中没有可导入的内容');
    const result = await request(path, { method: 'POST', body: JSON.stringify({ items }) });
    const rejected = Array.isArray(result.rejected) && result.rejected.length ? `；未导入 ${result.rejected.length} 条` : '';
    alert(`${label}完成：新增 ${result.created || 0} 条，更新 ${result.updated || 0} 条${rejected}`);
    render();
  };

  window.adminApp = {
    refresh: render,
    changeTab: tab => { if (consultationSocket) consultationSocket.close(); consultationSocket = null; state.tab = tab; state.content = null; state.lawyer = null; state.template = null; state.consultation = null; render(); },
    changeContentType: type => { state.contentType = type; state.content = null; render(); },
    updateContentForm: type => updateContentForm(type),
    cancelContentEdit: () => { state.content = null; render(); },
    cancelLawyerEdit: () => { state.lawyer = null; render(); },
    cancelTemplateEdit: () => { state.template = null; render(); },
    importLegalLibrary: () => importManifest('legal-library-file', '/content/import', '法规审核清单导入'),
    importTemplateLibrary: () => importManifest('template-library-file', '/templates/import', '模板审核清单导入'),
    editContent: async id => { try { state.content = await request(`/content/${id}`); render(); } catch (error) { showError(error); } },
    editLawyer: async id => { try { state.lawyer = await request(`/lawyers/${id}`); render(); } catch (error) { showError(error); } },
    editTemplate: async id => { try { const list = await request('/templates'); state.template = list.find(item => Number(item.id) === Number(id)) || null; if (!state.template) throw new Error('模板不存在'); render(); } catch (error) { showError(error); } },
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

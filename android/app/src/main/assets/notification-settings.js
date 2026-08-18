(() => {
  const root = document.querySelector('.nova-mobile-settings');
  if (!root) return;
  const home = root.querySelector('[data-page="home"]');
  const detail = root.querySelector('[data-page="detail"]');
  const KEY = 'nova.notification.categories';
  const categories = [
    ['nova', 'NOVA', 'Получать уведомления о задачах NOVA.'],
    ['groups', 'Групповые чаты', 'Получать уведомления из групповых чатов.'],
    ['tasks', 'Задачи', 'Получать напоминания и результаты задач.'],
    ['health', 'Здоровье', 'Получать уведомления о функциях здоровья.'],
    ['usage', 'Использование', 'Получать полезные сведения об использовании NOVA.'],
    ['marketing', 'Маркетинг', 'Получать новости, предложения и рекомендации.'],
    ['answers', 'Ответы', 'Получать уведомления, когда ответ NOVA готов.'],
    ['tips', 'Персонализированные подсказки', 'Получать персональные советы и подсказки.'],
    ['projects', 'Проекты', 'Получать уведомления об изменениях в проектах.']
  ];
  function load() {
    const defaults = Object.fromEntries(categories.map(([id]) => [id, true]));
    try { return { ...defaults, ...JSON.parse(localStorage.getItem(KEY) || '{}') }; }
    catch { return defaults; }
  }
  function save(value) { localStorage.setItem(KEY, JSON.stringify(value)); }
  function setPages(showDetail = true) {
    home.classList.toggle('active', !showDetail);
    detail.classList.toggle('active', showDetail);
  }
  function pageHead(title) {
    return `<div class="nms-page-head"><button class="nms-back" data-notification-back>‹</button><h2>${title}</h2></div>`;
  }
  function renderList() {
    const state = load();
    detail.innerHTML = `${pageHead('Уведомления')}<div class="nms-notification-list">${categories.map(([id, title]) => `
      <button class="nms-notification-row" data-notification-id="${id}">
        <span>${title}</span><small>${state[id] ? 'Включено' : 'Выключено'}</small>
      </button>`).join('')}</div>`;
    setPages(true);
  }
  function renderCategory(id) {
    const state = load();
    const category = categories.find(item => item[0] === id);
    if (!category) return renderList();
    detail.innerHTML = `${pageHead(category[1])}
      <div class="nms-notification-caption">Где вы будете получать уведомления</div>
      <label class="nms-notification-toggle">
        <span>Push-уведомления</span>
        <input class="nms-switch" data-notification-toggle="${id}" type="checkbox" ${state[id] ? 'checked' : ''}>
      </label>
      <p class="nms-notification-help">${category[2]}</p>`;
    setPages(true);
  }
  root.addEventListener('click', event => {
    const notifications = event.target.closest('[data-open="notifications"]');
    if (notifications) {
      event.preventDefault();
      event.stopImmediatePropagation();
      renderList();
      return;
    }
    const category = event.target.closest('[data-notification-id]');
    if (category) {
      event.preventDefault();
      renderCategory(category.dataset.notificationId);
      return;
    }
    if (event.target.closest('[data-notification-back]')) {
      event.preventDefault();
      if (detail.querySelector('[data-notification-toggle]')) renderList();
      else setPages(false);
    }
  }, true);
  root.addEventListener('change', async event => {
    const toggle = event.target.closest('[data-notification-toggle]');
    if (!toggle) return;
    const state = load();
    state[toggle.dataset.notificationToggle] = toggle.checked;
    save(state);
    if (toggle.checked && 'Notification' in window && Notification.permission === 'default') {
      try { await Notification.requestPermission(); } catch {}
    }
  });
})();

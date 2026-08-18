(() => {
  const native = window.NovaAndroidUpdaterNative;
  if (!native) return;

  document.head.insertAdjacentHTML('beforeend', '<link rel="stylesheet" href="android-updater.css">');
  document.body.insertAdjacentHTML('beforeend', `
    <button class="android-update-pill" type="button" hidden>Обновить</button>
    <div class="android-update-modal">
      <div class="android-update-card">
        <h3>Обновления NOVA AI</h3>
        <div class="android-update-grid">
          <span>Текущая версия</span><b class="au-current">—</b>
          <span>Доступная версия</span><b class="au-latest">Проверяю…</b>
        </div>
        <div class="android-update-progress"><i></i></div>
        <div class="android-update-notes"></div>
        <label class="android-update-options"><input type="checkbox" class="au-auto" checked> Автоматически проверять обновления</label>
        <div class="android-update-message"></div>
        <div class="android-update-actions">
          <button type="button" class="au-close">Закрыть</button>
          <button type="button" class="au-check">Проверить обновления</button>
          <button type="button" class="primary au-action" hidden>Обновить</button>
        </div>
      </div>
    </div>`);

  const q = (selector) => document.querySelector(selector);
  const pill = q('.android-update-pill');
  const modal = q('.android-update-modal');
  const action = q('.au-action');
  const message = q('.android-update-message');
  let info = null;
  let busy = false;

  function setPill(text, state = '', visible = true) {
    pill.textContent = text;
    pill.hidden = !visible;
    pill.className = `android-update-pill ${visible ? 'show' : ''} ${state}`.trim();
  }

  function updateAbout(data, statusText) {
    const current = document.querySelector('#aboutCurrentVersion');
    const latest = document.querySelector('#aboutLatestVersion');
    const status = document.querySelector('#aboutUpdateStatus');
    if (current) current.textContent = data?.currentName || '—';
    if (latest) latest.textContent = data?.latestName || 'Обновлений нет';
    if (status) status.textContent = statusText || (data?.available ? `Доступна версия ${data.latestName}` : 'Установлена последняя версия');
  }

  function render(data) {
    info = data;
    busy = false;
    q('.au-current').textContent = data.currentName || '—';
    q('.au-latest').textContent = data.latestName || 'Обновлений нет';
    q('.android-update-notes').textContent = data.notes || '';
    q('.au-auto').checked = data.autoCheck !== false;
    action.hidden = !data.available;

    if (data.available) {
      const label = data.downloaded ? 'Установить обновление' : 'Обновить';
      setPill(label, 'available');
      action.textContent = label;
    } else {
      setPill('Обновить', 'idle', false);
    }
    updateAbout(data);
    if (data.message) message.textContent = data.message;
  }

  function check(manual) {
    if (busy) return;
    busy = true;
    message.textContent = 'Проверяю обновления…';
    updateAbout(info, 'Проверяю обновления…');
    native.check(!!manual);
  }

  function runUpdate() {
    if (busy || !info || !info.available) return;
    if (info.downloaded) {
      native.install();
      return;
    }
    busy = true;
    message.textContent = 'Начинаю загрузку…';
    native.download();
  }

  window.NovaAndroidUpdater = {
    receive(type, data) {
      if (type === 'info') {
        render(data);
      } else if (type === 'progress') {
        const progress = Math.max(0, Math.min(100, data.progress || 0));
        q('.android-update-progress i').style.width = `${progress}%`;
        action.hidden = false;
        action.textContent = `Скачивание ${progress}%`;
        setPill(`Скачивание ${progress}%`, 'downloading');
      } else {
        busy = false;
        message.textContent = data.message || 'Не удалось проверить обновления';
        setPill('Обновить', 'error', false);
        updateAbout(info, message.textContent);
      }
    },
    open() { modal.classList.add('open'); },
    check(manual = true) { check(manual); },
    getInfo() { native.getInfo(); }
  };

  pill.addEventListener('click', () => {
    modal.classList.add('open');
  });
  action.addEventListener('click', runUpdate);
  q('.au-close').addEventListener('click', () => modal.classList.remove('open'));
  q('.au-check').addEventListener('click', () => check(true));
  q('.au-auto').addEventListener('change', (event) => native.setAutoCheck(event.target.checked));

  native.getInfo();
  setTimeout(() => check(false), 400);
})();

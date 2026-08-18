(() => {
  const updater = window.NovaAndroidUpdater;
  if (!updater) return;
  let state = { kind: 'idle', text: 'Нажмите «Проверить», чтобы проверить обновления.' };
  function ensureStatus() {
    const button = document.querySelector('.nova-mobile-settings [data-check-update]');
    if (!button) return null;
    let status = document.querySelector('#nmsUpdateCheckStatus');
    if (!status) {
      status = document.createElement('p');
      status.id = 'nmsUpdateCheckStatus';
      status.className = 'nms-update-check-status';
      button.closest('.nms-card')?.append(status);
    }
    const className = `nms-update-check-status ${state.kind}`;
    if (status.className !== className) status.className = className;
    if (status.textContent !== state.text) status.textContent = state.text;
    button.disabled = state.kind === 'checking';
    const buttonText = state.kind === 'checking' ? 'Проверяю…' : 'Проверить обновления';
    if (button.textContent !== buttonText) button.textContent = buttonText;
    return status;
  }
  function setState(kind, text) { state = { kind, text }; ensureStatus(); }
  const originalReceive = updater.receive.bind(updater);
  updater.receive = (type, data = {}) => {
    originalReceive(type, data);
    if (type === 'info') {
      if (data.available) setState('available', `Найдено обновление: версия ${data.latestName}.`);
      else setState('current', `Обновлений не найдено. Установлена последняя версия ${data.currentName || ''}.`.trim());
    } else if (type === 'progress') {
      setState('checking', `Скачивание обновления: ${Math.max(0, Math.min(100, data.progress || 0))}%`);
    } else {
      setState('error', data.message || 'Не удалось проверить обновления. Проверьте интернет-соединение.');
    }
  };
  const originalCheck = updater.check.bind(updater);
  updater.check = (manual = true) => {
    setState('checking', 'Проверяю наличие обновлений…');
    originalCheck(manual);
  };
  new MutationObserver(ensureStatus).observe(document.body, { childList: true, subtree: true });
  ensureStatus();
})();

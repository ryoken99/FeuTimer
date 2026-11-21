/* Client controls supplied by the PC launcher across installed versions. */
(async () => {
  const status = await (await fetch('/pc/status')).json();
  const params = new URLSearchParams(location.search);
  if (status.layout && !params.has('rondas')) {
    params.set('rondas','tv');
    location.replace('/?' + params);
    return;
  }
  const channelKey = 'feu.pc.update.channel';
  if (params.get('canal') === 'teste') localStorage.setItem(channelKey,'test');
  const channel = () => localStorage.getItem(channelKey) === 'test' ? 'test' : 'stable';
  const footer = document.querySelector('#bottom-right');
  function controls() {
    const update = footer.querySelector('[data-bar-action="download_update"]');
    if (update) {
      let toggle = footer.querySelector('[data-pc-channel]');
      if (!toggle) {
        toggle = document.createElement('button');
        toggle.type = 'button';
        toggle.className = 'tv-focusable opt-pill';
        toggle.dataset.pcChannel = '';
        update.before(toggle);
      }
      const text = channel() === 'test' ? 'Canal: Teste' : 'Canal: Estável';
      if (toggle.textContent !== text) toggle.textContent = text;
    }
    const version = footer.querySelector('.version-tag');
    if (version && status.layout && !version.textContent.includes('Layout Test')) version.append(' · Layout Test');
  }
  new MutationObserver(controls).observe(footer,{childList:true,subtree:true});
  controls();
  if (params.get('abrir') === 'definicoes') {
    document.querySelector('[data-bar-action="open_settings"]')?.click();
    history.replaceState(null,'',location.pathname);
  }
  let installing = false;
  document.addEventListener('click',async event => {
    if (event.target.closest('[data-pc-channel]')) {
      event.stopImmediatePropagation();
      localStorage.setItem(channelKey,channel() === 'test' ? 'stable' : 'test');
      controls();
      return;
    }
    const button = event.target.closest('[data-bar-action="download_update"]');
    if (!button) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    if (installing) return;
    installing = true;
    button.disabled = true;
    button.textContent = 'A verificar e instalar…';
    try {
      const response = await fetch('/pc/install?channel='+channel(),{method:'POST'});
      const result = await response.json();
      if (!response.ok) throw new Error(result.error || 'Não foi possível instalar a atualização.');
      if (result.current) button.textContent = 'Já estás atualizado';
      else {
        button.textContent = 'A reiniciar…';
        location.replace(result.layout ? '/?rondas=tv' : '/');
      }
    } catch (err) { button.textContent = err.message; }
    finally { installing = false; button.disabled = false; }
  }, true);
})();

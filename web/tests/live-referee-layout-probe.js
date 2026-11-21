(() => {
  const vw = document.documentElement.clientWidth;
  const vh = document.documentElement.clientHeight;
  const kids = Array.from(document.querySelector('.console').children).map((e) => {
    const r = e.getBoundingClientRect();
    return { cls: e.className.split(' ')[0], l: Math.round(r.left), t: Math.round(r.top), r: Math.round(r.right), b: Math.round(r.bottom) };
  });
  const overlaps = [];
  for (let i = 0; i < kids.length; i++) for (let j = i + 1; j < kids.length; j++) {
    const a = kids[i], b = kids[j];
    const ox = Math.min(a.r, b.r) - Math.max(a.l, b.l);
    const oy = Math.min(a.b, b.b) - Math.max(a.t, b.t);
    if (ox > 1 && oy > 1) overlaps.push(a.cls + '×' + b.cls);
  }
  // any control that is clipped by its own card or spills outside the viewport
  const spill = [];
  document.querySelectorAll('.console button, .console input').forEach((e) => {
    const r = e.getBoundingClientRect();
    if (r.width && (r.right > vw + 1 || r.bottom > vh + 1 || r.left < -1 || r.top < -1)) spill.push((e.id || e.className || e.tagName).toString().slice(0, 30));
  });
  const clipped = [];
  document.querySelectorAll('.fighter, .clock-card').forEach((card) => {
    if (card.scrollHeight > card.clientHeight + 1 || card.scrollWidth > card.clientWidth + 1) clipped.push(card.className.split(' ')[0] + ':' + (card.scrollHeight - card.clientHeight) + '/' + (card.scrollWidth - card.clientWidth));
  });
  return JSON.stringify({ vw, vh, orient: vw > vh ? 'landscape' : 'portrait', docScroll: document.documentElement.scrollHeight - vh, overlaps, spill: spill.slice(0, 6), clipped });
})()

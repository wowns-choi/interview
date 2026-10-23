// ── 합격자 보고서: 고치면 바로 저장, 항목 추가·삭제·끌어서 순서 바꾸기, PDF ──
(function () {
  var rp = document.getElementById('rp');
  if (!rp) return;
  var state = document.getElementById('rp-state');
  var timer = null;
  var inflight = null;
  var dirty = false;

  function autosize(ta) {
    ta.style.height = 'auto';
    ta.style.height = ta.scrollHeight + 'px';
  }

  function val(el, key) {
    var f = el.querySelector('[data-k="' + key + '"]');
    return f ? f.value.trim() : '';
  }

  function collect() {
    var qa = [];
    rp.querySelectorAll('[data-list="qa"]').forEach(function (list) {
      list.querySelectorAll('.rp-item').forEach(function (li) {
        qa.push({ kind: list.getAttribute('data-kind'), question: val(li, 'question'), answer: val(li, 'answer'),
          excerpt: li.getAttribute('data-excerpt') || '' });
      });
    });
    return {
      reportDate: rp.querySelector('[data-f="reportDate"]').value,
      interviewDate: rp.querySelector('[data-f="interviewDate"]').value,
      qaSource: rp.getAttribute('data-source'),
      info: Array.prototype.map.call(rp.querySelectorAll('[data-list="info"] .rp-item'), function (el) {
        return { label: val(el, 'label'), value: val(el, 'value') };
      }),
      qa: qa,
      opinions: Array.prototype.map.call(rp.querySelectorAll('[data-list="opinions"] .rp-item'), function (el) {
        return { team: val(el, 'team'), name: val(el, 'name'), content: val(el, 'content') };
      })
    };
  }

  function save() {
    clearTimeout(timer);
    timer = null;
    dirty = false;
    state.textContent = '저장 중';
    state.classList.remove('err');
    inflight = fetch(rp.getAttribute('data-url'), {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'fetch' },
      body: JSON.stringify(collect())
    }).then(function (res) {
      if (!res.ok) return res.text().then(function (t) { throw new Error(t || res.status); });
      state.textContent = '저장됨';
    }).catch(function (err) {
      dirty = true;
      state.textContent = '저장 실패';
      state.classList.add('err');
      state.title = err.message;
    }).finally(function () { inflight = null; });
    return inflight;
  }

  function changed(now) {
    dirty = true;
    state.textContent = '';
    clearTimeout(timer);
    timer = setTimeout(save, now ? 0 : 600);
  }

  function flush() {
    if (dirty || timer) return save();
    return inflight || Promise.resolve();
  }

  function renumber() {
    rp.querySelectorAll('[data-list="qa"]').forEach(function (list) {
      list.querySelectorAll('.rp-qno').forEach(function (no, i) { no.textContent = 'Q' + (i + 1); });
      // 질문이 하나도 없는 묶음(예: 지원자 질문)은 PDF에서 제목까지 뺀다
      list.closest('.rp-qa-group').classList.toggle('is-empty', !list.querySelector('.rp-item'));
    });
  }

  rp.querySelectorAll('textarea').forEach(autosize);
  renumber();

  rp.addEventListener('input', function (e) {
    if (e.target.tagName === 'TEXTAREA') autosize(e.target);
    changed(false);
  });
  rp.addEventListener('change', function (e) {
    if (e.target.type === 'date') changed(true);
  });

  rp.addEventListener('click', function (e) {
    var del = e.target.closest('.rp-del');
    if (del) {
      del.closest('.rp-item').remove();
      renumber();
      changed(true);
      return;
    }
    var add = e.target.closest('[data-add]');
    if (!add) return;
    var kind = add.getAttribute('data-add');
    var list = kind === 'qa'
      ? rp.querySelector('[data-list="qa"][data-kind="' + add.getAttribute('data-kind') + '"]')
      : rp.querySelector('[data-list="' + kind + '"]');
    var item = document.getElementById('tpl-' + kind).content.firstElementChild.cloneNode(true);
    list.appendChild(item);
    item.querySelectorAll('textarea').forEach(autosize);
    renumber();
    var first = item.querySelector('input, textarea');
    if (first) first.focus();
    changed(true);
  });

  // 끌어서 순서 바꾸기: 손잡이를 잡았을 때만 끈다. 질문은 면접관 질문과 지원자 질문 사이로도 옮길 수 있다
  var dragging = null;
  rp.addEventListener('mousedown', function (e) {
    var handle = e.target.closest('.rp-handle');
    if (handle) handle.closest('.rp-item').setAttribute('draggable', 'true');
  });
  rp.addEventListener('dragstart', function (e) {
    var item = e.target.closest && e.target.closest('.rp-item[draggable="true"]');
    if (!item) return;
    dragging = item;
    item.classList.add('is-dragging');
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', '');
  });
  rp.addEventListener('dragover', function (e) {
    if (!dragging) return;
    var group = dragging.parentElement.getAttribute('data-list');
    var list = e.target.closest('[data-list="' + group + '"]');
    if (!list) return;
    e.preventDefault();
    var over = e.target.closest('.rp-item');
    if (over === dragging) return;
    if (over && over.parentElement === list) {
      var r = over.getBoundingClientRect();
      // 지원자 정보는 두 칸씩 놓이므로 가로 위치로, 나머지는 세로 위치로 앞뒤를 정한다
      var after = group === 'info' ? e.clientX > r.left + r.width / 2 : e.clientY > r.top + r.height / 2;
      list.insertBefore(dragging, after ? over.nextSibling : over);
    } else if (!over) {
      list.appendChild(dragging);
    }
    renumber();
  });
  rp.addEventListener('dragend', function () {
    if (!dragging) return;
    dragging.classList.remove('is-dragging');
    dragging.removeAttribute('draggable');
    dragging = null;
    renumber();
    changed(true);
  });
  document.addEventListener('mouseup', function () {
    if (!dragging) rp.querySelectorAll('.rp-item[draggable]').forEach(function (el) { el.removeAttribute('draggable'); });
  });

  // 서버에서 보고서를 바꾸는 버튼(정렬, 다시 만들기)은 고친 내용을 먼저 저장한 뒤 보낸다
  document.addEventListener('submit', function (e) {
    if (!dirty && !timer && !inflight) return;
    var form = e.target;
    e.preventDefault();
    e.stopImmediatePropagation();
    var submitter = e.submitter;
    flush().then(function () { form.requestSubmit(submitter && submitter.form === form ? submitter : undefined); });
  }, true);

  document.getElementById('rp-pdf').addEventListener('click', function () {
    flush().then(function () {
      rp.querySelectorAll('textarea').forEach(autosize);
      window.print();
    });
  });

  window.addEventListener('beforeunload', function (e) {
    if (dirty || timer || inflight) {
      flush();
      e.preventDefault();
      e.returnValue = '';
    }
  });
})();

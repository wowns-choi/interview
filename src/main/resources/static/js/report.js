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
      interviewDate: rp.querySelector('[data-f="interviewDate"]').value,
      position: rp.querySelector('[data-f="position"]').value,
      qaSource: rp.getAttribute('data-source'),
      info: Array.prototype.map.call(rp.querySelectorAll('[data-list="info"] .rp-item'), function (el) {
        return { label: val(el, 'label'), value: val(el, 'value') };
      }),
      certs: Array.prototype.map.call(rp.querySelectorAll('[data-list="certs"] .rp-item'), function (el) {
        return { label: val(el, 'label'), value: val(el, 'value') };
      }),
      qa: qa,
      opinions: Array.prototype.map.call(rp.querySelectorAll('[data-list="opinions"] .rp-item'), function (el) {
        var on = el.querySelector('.rp-mini.is-on');
        return { team: val(el, 'team'), name: val(el, 'name'), content: val(el, 'content'), decision: on ? on.getAttribute('data-v') : '' };
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
  // 출처 바꾸기·다시 찾기 뒤에는 보고서 내용만 새로 받아 온다
  document.addEventListener('partial', function () {
    rp.querySelectorAll('textarea').forEach(autosize);
    renumber();
  });
  // 지원자 화면의 보고서 단계가 보일 때 (숨어 있을 때는 높이를 잴 수 없다)
  document.addEventListener('stageshow', function (e) {
    if (e.detail === 'report') rp.querySelectorAll('textarea').forEach(autosize);
  });

  rp.addEventListener('input', function (e) {
    if (e.target.tagName === 'TEXTAREA') autosize(e.target);
    changed(false);
  });
  rp.addEventListener('change', function (e) {
    if (e.target.type === 'date' || e.target.type === 'datetime-local') changed(true);
  });

  rp.addEventListener('click', function (e) {
    // 면접관마다 합격·불합격: 같은 것을 다시 누르면 지운다
    var mini = e.target.closest('.rp-mini');
    if (mini) {
      var was = mini.classList.contains('is-on');
      mini.parentElement.querySelectorAll('.rp-mini').forEach(function (b) { b.classList.remove('is-on'); });
      if (!was) mini.classList.add('is-on');
      changed(true);
      return;
    }
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
      // 지원자 정보·자격증은 두 칸씩 놓이므로 가로 위치로, 나머지는 세로 위치로 앞뒤를 정한다
      var after = list.classList.contains('rp-info') ? e.clientX > r.left + r.width / 2 : e.clientY > r.top + r.height / 2;
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

  // 서버에서 보고서를 바꾸는 버튼(출처 바꾸기, 다시 찾기, 다시 만들기)은 고친 내용을 먼저 저장한 뒤 보낸다
  document.addEventListener('submit', function (e) {
    if (!dirty && !timer && !inflight) return;
    var form = e.target;
    e.preventDefault();
    e.stopImmediatePropagation();
    var submitter = e.submitter;
    flush().then(function () { form.requestSubmit(submitter && submitter.form === form ? submitter : undefined); });
  }, true);

  // PDF: 지원자 화면 안이라 보고서 종이만 인쇄하고, 파일 이름이 되는 제목을 잠시 바꾼다
  var pdf = document.getElementById('rp-pdf');
  if (pdf) pdf.addEventListener('click', function () {
    flush().then(function () {
      rp.querySelectorAll('textarea').forEach(autosize);
      var title = document.title;
      document.title = pdf.getAttribute('data-title') || title;
      document.body.classList.add('print-report');
      window.print();
      document.body.classList.remove('print-report');
      document.title = title;
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

// 합격 여부: 누르면 지원자 기록에 바로 저장 (다시 누르면 비움). 보고서 JSON이 아니라 지원자 값이다
(function () {
  var box = document.getElementById('rp-result');
  if (!box) return;
  var state = document.getElementById('rp-state');
  var buttons = box.querySelectorAll('[data-decision]');
  // 면접 날짜는 면접 전 단계 질문지 제목 줄에서 바뀔 수 있으니 그 칸을 먼저 본다
  function dateNow() {
    var el = document.getElementById('iv-date');
    return el ? el.value : box.getAttribute('data-date');
  }
  document.addEventListener('ivdecision', function (e) {
    if (e.detail.from !== 'report') show(e.detail.decision);
  });
  function show(decision) {
    buttons.forEach(function (b) {
      var on = b.getAttribute('data-decision') === decision;
      b.classList.toggle('is-on', on);
      b.setAttribute('aria-pressed', on);
    });
  }
  buttons.forEach(function (b) {
    b.addEventListener('click', function () {
      var decision = b.classList.contains('is-on') ? '' : b.getAttribute('data-decision');
      var before = box.querySelector('.is-on');
      show(decision);
      state.textContent = '저장 중';
      state.classList.remove('err');
      fetch(box.getAttribute('data-url'), {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'fetch' },
        body: JSON.stringify({ interviewed: box.getAttribute('data-interviewed') === 'true',
          date: dateNow(), decision: decision })
      }).then(function (res) {
        return res.ok ? res.json() : res.text().then(function (t) { throw new Error(t); });
      }).then(function (saved) {
        box.setAttribute('data-interviewed', saved.interviewed);
        show(saved.decision);
        // 같은 화면 면접 후 단계의 도장도 맞춘다
        document.dispatchEvent(new CustomEvent('ivdecision', { detail: { decision: saved.decision, from: 'report' } }));
        state.textContent = '저장됨';
      }).catch(function (e) {
        show(before ? before.getAttribute('data-decision') : '');
        state.textContent = e.message || '저장하지 못했습니다';
        state.classList.add('err');
      });
    });
  });
})();

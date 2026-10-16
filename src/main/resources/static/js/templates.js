/* 공통 질문 설정: 섹션·질문 편집과 드래그앤드롭. 모든 변경은 바로 서버에 저장한다. */
(function () {
  var board = document.getElementById('board');
  if (!board) return;
  var base = board.getAttribute('data-base');
  var empty = document.getElementById('board-empty');
  var toastEl = document.getElementById('toast');
  var toastTimer;

  // ── 서버 호출 ──
  function api(method, path, body) {
    return fetch(base + path, {
      method: method,
      headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'fetch' },
      body: body === undefined ? undefined : JSON.stringify(body)
    }).then(function (res) {
      if (!res.ok) return res.text().then(function (t) { throw new Error(t || '저장하지 못했습니다.'); });
      return res.status === 204 ? null : res.json();
    }).catch(function (err) {
      toast(err.message || '저장하지 못했습니다.', true);
      throw err;
    });
  }

  function toast(msg, isError) {
    toastEl.textContent = msg;
    toastEl.classList.toggle('is-error', !!isError);
    toastEl.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { toastEl.hidden = true; }, isError ? 4000 : 1600);
  }

  function refreshCounts() {
    board.querySelectorAll('.sec').forEach(function (sec) {
      sec.querySelector('.sec-count').textContent = sec.querySelectorAll('.q').length;
    });
    empty.hidden = board.querySelector('.sec') !== null;
  }

  function clone(id) {
    return document.getElementById(id).content.firstElementChild.cloneNode(true);
  }

  // ── 그 자리에서 고치기: 입력칸으로 바꾸고 Enter 저장, Esc 취소 ──
  function inlineEdit(el, opts) {
    if (el.parentNode.querySelector('.inline-edit')) return;
    var original = opts.value;
    var field = document.createElement(opts.multiline ? 'textarea' : 'input');
    field.className = 'inline-edit' + (opts.multiline ? ' is-multi' : '');
    field.value = original;
    if (opts.placeholder) field.placeholder = opts.placeholder;
    el.hidden = true;
    el.parentNode.insertBefore(field, el.nextSibling);
    if (opts.multiline) autosize(field);
    field.focus();
    field.select();

    var done = false;
    function finish(save) {
      if (done) return;
      done = true;
      var value = field.value.trim();
      if (!save || value === '' || value === original) {
        field.remove();
        el.hidden = false;
        if (opts.onCancel) opts.onCancel(value);
        return;
      }
      field.disabled = true;
      opts.onSave(value).then(function () {
        el.textContent = value;
        field.remove();
        el.hidden = false;
      }, function () {
        done = false;
        field.disabled = false;
        field.focus();
      });
    }
    field.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') { e.preventDefault(); finish(false); }
      if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); finish(true); }
    });
    if (opts.multiline) field.addEventListener('input', function () { autosize(field); });
    field.addEventListener('blur', function () { finish(true); });
  }

  function autosize(ta) {
    ta.style.height = 'auto';
    ta.style.height = ta.scrollHeight + 2 + 'px';
  }

  // ── 클릭 처리 ──
  board.addEventListener('click', function (e) {
    var btn = e.target.closest('button');
    if (!btn) return;
    var sec = btn.closest('.sec');
    var q = btn.closest('.q');

    if (btn.classList.contains('js-rename')) {
      var name = sec.querySelector('.sec-name');
      inlineEdit(name, {
        value: name.textContent, placeholder: '섹션 이름',
        onSave: function (v) { return api('PATCH', '/sections/' + sec.dataset.id, { name: v }).then(function () { toast('섹션 이름을 바꿨습니다'); }); }
      });
    } else if (btn.classList.contains('js-del-section')) {
      var n = sec.querySelectorAll('.q').length;
      var label = sec.querySelector('.sec-name').textContent;
      if (!confirm('"' + label + '" 섹션을 삭제할까요?' + (n ? '\n안에 있는 질문 ' + n + '개도 함께 삭제됩니다.' : ''))) return;
      api('DELETE', '/sections/' + sec.dataset.id).then(function () { sec.remove(); refreshCounts(); toast('섹션을 삭제했습니다'); });
    } else if (btn.classList.contains('js-edit')) {
      var content = q.querySelector('.q-content');
      inlineEdit(content, {
        value: content.textContent, multiline: true,
        onSave: function (v) { return api('PATCH', '/questions/' + q.dataset.id, { content: v }).then(function () { toast('저장했습니다'); }); }
      });
    } else if (btn.classList.contains('js-del')) {
      api('DELETE', '/questions/' + q.dataset.id).then(function () { q.remove(); refreshCounts(); toast('질문을 삭제했습니다'); });
    } else if (btn.classList.contains('js-add-q')) {
      addQuestion(sec);
    }
  });

  // 질문 추가: 목록 끝에 빈 줄을 만들고 바로 입력
  function addQuestion(sec) {
    var list = sec.querySelector('.q-list');
    var li = clone('tpl-question');
    li.classList.add('is-new');
    list.appendChild(li);
    var content = li.querySelector('.q-content');
    inlineEdit(content, {
      value: '', multiline: true, placeholder: '질문을 입력하고 Enter',
      onSave: function (v) {
        return api('POST', '/sections/' + sec.dataset.id + '/questions', { content: v }).then(function (saved) {
          li.dataset.id = saved.id;
          li.classList.remove('is-new');
          refreshCounts();
          addQuestion(sec); // 이어서 입력
        });
      },
      onCancel: function () { li.remove(); }
    });
  }

  // 섹션 추가: 맨 아래에 만들고 이름부터 입력
  document.getElementById('add-section').addEventListener('click', function () {
    var sec = clone('tpl-section');
    sec.classList.add('is-new');
    board.appendChild(sec);
    empty.hidden = true;
    sec.scrollIntoView({ behavior: 'smooth', block: 'center' });
    var name = sec.querySelector('.sec-name');
    inlineEdit(name, {
      value: '', placeholder: '섹션 이름을 입력하고 Enter',
      onSave: function (v) {
        return api('POST', '/sections', { name: v }).then(function (saved) {
          sec.dataset.id = saved.id;
          sec.classList.remove('is-new');
          toast('섹션을 추가했습니다');
          addQuestion(sec);
        });
      },
      onCancel: function () { sec.remove(); refreshCounts(); }
    });
  });

  // ── 드래그앤드롭 ──
  // 손잡이를 누를 때만 끌 수 있게 해서, 글자 선택이나 입력을 방해하지 않는다
  var dragging = null;
  var before = null;

  board.addEventListener('mousedown', function (e) {
    var handle = e.target.closest('.handle');
    if (!handle) return;
    var item = handle.classList.contains('sec-handle') ? handle.closest('.sec') : handle.closest('.q');
    if (item.classList.contains('is-new')) return;
    item.setAttribute('draggable', 'true');
  });
  document.addEventListener('mouseup', function () {
    board.querySelectorAll('[draggable=true]').forEach(function (el) { if (el !== dragging) el.removeAttribute('draggable'); });
  });

  board.addEventListener('dragstart', function (e) {
    dragging = e.target.closest('[draggable=true]');
    if (!dragging) return;
    before = snapshot();
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', '');
    setTimeout(function () { dragging.classList.add('is-dragging'); board.classList.add('is-sorting'); }, 0);
    e.stopPropagation();
  });

  board.addEventListener('dragover', function (e) {
    if (!dragging) return;
    e.preventDefault();
    if (dragging.classList.contains('sec')) {
      var after = itemAfter(board, '.sec', e.clientY);
      if (after == null) board.appendChild(dragging);
      else if (after !== dragging) board.insertBefore(dragging, after);
    } else {
      var sec = e.target.closest('.sec');
      if (!sec) return;
      var list = sec.querySelector('.q-list');
      var next = itemAfter(list, '.q', e.clientY);
      if (next == null) list.appendChild(dragging);
      else if (next !== dragging) list.insertBefore(dragging, next);
    }
  });

  board.addEventListener('drop', function (e) { if (dragging) e.preventDefault(); });

  board.addEventListener('dragend', function () {
    if (!dragging) return;
    dragging.classList.remove('is-dragging');
    dragging.removeAttribute('draggable');
    board.classList.remove('is-sorting');
    dragging = null;
    refreshCounts();
    var now = snapshot();
    if (JSON.stringify(now) === JSON.stringify(before)) return;
    api('PUT', '/order', now).then(function () { toast('순서를 저장했습니다'); }, function () {
      setTimeout(function () { location.reload(); }, 1200);
    });
  });

  // 마우스 위치 바로 아래에 오는 항목 (없으면 null = 맨 끝)
  function itemAfter(container, selector, y) {
    var items = Array.prototype.filter.call(container.children, function (el) {
      return el.matches(selector) && !el.classList.contains('is-dragging');
    });
    for (var i = 0; i < items.length; i++) {
      var box = items[i].getBoundingClientRect();
      if (y < box.top + box.height / 2) return items[i];
    }
    return null;
  }

  function snapshot() {
    return Array.prototype.filter.call(board.querySelectorAll('.sec'), function (s) { return s.dataset.id; })
      .map(function (s) {
        return {
          sectionId: Number(s.dataset.id),
          questionIds: Array.prototype.filter.call(s.querySelectorAll('.q'), function (q) { return q.dataset.id; })
            .map(function (q) { return Number(q.dataset.id); })
        };
      });
  }
})();

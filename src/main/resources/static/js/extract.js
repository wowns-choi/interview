(function () {
  var ws = document.getElementById('ws');
  if (!ws) return;
  var positionBase = ws.getAttribute('data-position-base');
  var resultBase = ws.getAttribute('data-result-base');
  var toastEl = document.getElementById('toast');
  var toastTimer;

  function api(method, url, body) {
    return fetch(url, {
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

  function clone(id) {
    return document.getElementById(id).content.firstElementChild.cloneNode(true);
  }

  function autosize(ta) {
    ta.style.height = 'auto';
    ta.style.height = ta.scrollHeight + 2 + 'px';
  }

  // 그 자리에서 고치기: Enter 저장, Shift+Enter 줄바꿈, Esc 취소, 바깥을 누르면 저장
  function editInline(target, fields, opts) {
    if (target.hidden) return;
    var form = document.createElement('div');
    form.className = 'inline-form';
    var inputs = fields.map(function (f) {
      var el = document.createElement(f.multiline ? 'textarea' : 'input');
      el.className = 'inline-edit' + (f.className ? ' ' + f.className : '');
      el.value = f.value || '';
      el.placeholder = f.placeholder || '';
      if (f.multiline) {
        el.rows = 1;
        el.addEventListener('input', function () { autosize(el); });
      }
      form.appendChild(el);
      return el;
    });
    target.hidden = true;
    target.parentNode.insertBefore(form, target.nextSibling);
    inputs.forEach(function (el) { if (el.tagName === 'TEXTAREA') autosize(el); });
    inputs[0].focus();

    var done = false;
    function values() { return inputs.map(function (el) { return el.value.trim(); }); }
    function close() { form.remove(); target.hidden = false; }
    function finish(save) {
      if (done) return;
      done = true;
      var v = values();
      var unchanged = v.every(function (x, i) { return x === (fields[i].value || ''); });
      if (!save || v[0] === '' || unchanged) {
        close();
        if (opts.onCancel) opts.onCancel();
        return;
      }
      inputs.forEach(function (el) { el.disabled = true; });
      opts.onSave(v).then(function () {
        close();
      }, function () {
        done = false;
        inputs.forEach(function (el) { el.disabled = false; });
        inputs[0].focus();
      });
    }
    form.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') { e.preventDefault(); finish(false); }
      if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); finish(true); }
    });
    form.addEventListener('focusout', function (e) {
      if (!form.contains(e.relatedTarget)) finish(true);
    });
  }

  initImages();
  initCriteria();
  initQuestions();

  // ── 포지션 이미지 (클릭 · 끌어다 놓기 · 붙여넣기) ──
  function initImages() {
    var form = document.getElementById('crit-form');
    if (!form) return;
    var input = document.getElementById('files');
    var list = document.getElementById('img-list');
    var addBtn = document.getElementById('img-add');
    var note = document.getElementById('img-note');
    var text = document.getElementById('jdText');
    var MAX = 5, MB = 1024 * 1024;
    var IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/webp', 'image/gif'];
    var files = [];

    function kind(f) {
      if (IMAGE_TYPES.indexOf(f.type) >= 0) return 'image';
      var name = f.name.toLowerCase();
      if (f.type === 'application/pdf' || /\.pdf$/.test(name)) return 'pdf';
      if (f.type === 'text/plain' || /\.(txt|md)$/.test(name)) return 'text';
      return null;
    }
    var noteTimer;

    var picker = document.createElement('input');
    picker.type = 'file';
    picker.accept = input.accept;
    picker.multiple = true;
    picker.addEventListener('change', function () { add(picker.files); picker.value = ''; });
    addBtn.addEventListener('click', function () { picker.click(); });

    function say(msg) {
      note.textContent = msg;
      clearTimeout(noteTimer);
      noteTimer = setTimeout(function () { note.textContent = ''; }, 4000);
    }

    function add(newFiles) {
      var rejected = [];
      Array.prototype.forEach.call(newFiles, function (f) {
        var k = kind(f);
        var limit = k === 'image' ? 5 : 10;
        if (!k) rejected.push(f.name + ' (형식)');
        else if (f.size > limit * MB) rejected.push(f.name + ' (' + limit + 'MB 초과)');
        else if (files.length >= MAX) rejected.push(f.name + ' (최대 ' + MAX + '개)');
        else files.push(f);
      });
      sync();
      if (rejected.length) say('넣지 못한 파일: ' + rejected.join(', '));
    }

    function sync() {
      var dt = new DataTransfer();
      files.forEach(function (f) { dt.items.add(f); });
      input.files = dt.files;
      list.querySelectorAll('img').forEach(function (img) { URL.revokeObjectURL(img.src); });
      list.innerHTML = '';
      files.forEach(function (f, i) {
        var li = document.createElement('li');
        var img;
        if (kind(f) === 'image') {
          img = document.createElement('img');
          img.src = URL.createObjectURL(f);
          img.alt = f.name;
        } else {
          li.className = 'is-doc';
          img = document.createElement('span');
          img.className = 'doc';
          img.innerHTML = '<svg class="i"><use href="#i-file"/></svg><b></b><small></small>';
          img.querySelector('b').textContent = kind(f) === 'pdf' ? 'PDF' : 'TXT';
          img.querySelector('small').textContent = f.name;
          img.title = f.name;
        }
        var rm = document.createElement('button');
        rm.type = 'button';
        rm.className = 'img-rm';
        rm.title = '빼기';
        rm.innerHTML = '<svg class="i"><use href="#i-x"/></svg>';
        rm.addEventListener('click', function () { files.splice(i, 1); sync(); });
        li.appendChild(img);
        li.appendChild(rm);
        list.appendChild(li);
      });
      addBtn.hidden = files.length >= MAX;
    }

    ['dragenter', 'dragover'].forEach(function (t) {
      form.addEventListener(t, function (e) {
        if (Array.prototype.indexOf.call(e.dataTransfer.types, 'Files') < 0) return;
        e.preventDefault();
        form.classList.add('is-over');
      });
    });
    form.addEventListener('dragleave', function (e) {
      if (!form.contains(e.relatedTarget)) form.classList.remove('is-over');
    });
    form.addEventListener('drop', function (e) {
      form.classList.remove('is-over');
      if (!e.dataTransfer.files.length) return;
      e.preventDefault();
      add(e.dataTransfer.files);
    });
    document.addEventListener('paste', function (e) {
      var pasted = e.clipboardData ? e.clipboardData.files : null;
      if (!pasted || !pasted.length) return;
      e.preventDefault();
      add(pasted);
    });
    form.addEventListener('submit', function (e) {
      if (text.value.trim() === '' && files.length === 0) {
        e.preventDefault();
        e.stopPropagation();
        text.focus();
        say('포지션 설명이나 파일을 넣어 주세요');
      }
    });
  }

  // ── 평가항목 편집 ──
  function initCriteria() {
    var list = document.getElementById('crit-list');
    if (!list) return;
    var count = document.getElementById('crit-count');

    function refresh() { count.textContent = list.querySelectorAll('.crit[data-id]:not([data-id=""])').length; }

    function setReq(li, req) {
      li.dataset.requirement = req;
      var b = li.querySelector('.js-req');
      b.className = 'req req-' + req + ' js-req';
      b.textContent = req === 'MUST' ? '필수' : '우대';
    }

    function payload(li, name, desc) {
      return { name: name, description: desc, requirement: li.dataset.requirement };
    }

    function edit(li, isNew) {
      var body = li.querySelector('.crit-body');
      var name = li.querySelector('.crit-name');
      var desc = li.querySelector('.crit-desc');
      editInline(body, [
        { value: name.textContent, placeholder: '평가항목', className: 'crit-name-input' },
        { value: desc.textContent, placeholder: '무엇을 보면 충족인지', multiline: true }
      ], {
        onSave: function (v) {
          var req = isNew
            ? api('POST', positionBase + '/criteria', payload(li, v[0], v[1])).then(function (saved) { li.dataset.id = saved.id; })
            : api('PATCH', positionBase + '/criteria/' + li.dataset.id, payload(li, v[0], v[1]));
          return req.then(function () {
            name.textContent = v[0];
            desc.textContent = v[1];
            refresh();
          });
        },
        onCancel: function () { if (isNew && !li.dataset.id) li.remove(); }
      });
    }

    list.addEventListener('click', function (e) {
      var btn = e.target.closest('button');
      if (!btn) return;
      var li = btn.closest('.crit');
      if (!li.dataset.id) return;
      if (btn.classList.contains('js-req')) {
        var next = li.dataset.requirement === 'MUST' ? 'PREFERRED' : 'MUST';
        var prev = li.dataset.requirement;
        setReq(li, next);
        api('PATCH', positionBase + '/criteria/' + li.dataset.id,
          payload(li, li.querySelector('.crit-name').textContent, li.querySelector('.crit-desc').textContent))
          .catch(function () { setReq(li, prev); });
      } else if (btn.classList.contains('js-crit-edit')) {
        edit(li, false);
      } else if (btn.classList.contains('js-crit-del')) {
        if (!confirm('"' + li.querySelector('.crit-name').textContent + '" 평가항목을 삭제할까요?')) return;
        api('DELETE', positionBase + '/criteria/' + li.dataset.id).then(function () { li.remove(); refresh(); });
      }
    });

    document.getElementById('crit-add').addEventListener('click', function () {
      var li = clone('tpl-crit');
      list.appendChild(li);
      edit(li, true);
    });
  }

  // ── 질문지 편집 · 드래그 ──
  function initQuestions() {
    var board = document.getElementById('iq-board');
    if (!board) return;

    board.addEventListener('click', function (e) {
      var btn = e.target.closest('button');
      if (!btn) return;
      var row = btn.closest('.iq');
      if (btn.classList.contains('js-q-edit')) {
        var content = row.querySelector('.iq-content');
        editInline(content, [{ value: content.textContent, multiline: true }], {
          onSave: function (v) {
            return api('PATCH', resultBase + '/questions/' + row.dataset.id, { content: v[0] })
              .then(function () { content.textContent = v[0]; toast('저장했습니다'); });
          }
        });
      } else if (btn.classList.contains('js-q-del')) {
        api('DELETE', resultBase + '/questions/' + row.dataset.id).then(function () { row.remove(); toast('삭제했습니다'); });
      } else if (btn.classList.contains('js-q-add')) {
        addQuestion(btn.closest('.iq-sec'));
      }
    });

    function addQuestion(sec) {
      var row = clone('tpl-q');
      row.classList.add('is-new');
      sec.querySelector('.iq-list').appendChild(row);
      var content = row.querySelector('.iq-content');
      editInline(content, [{ value: '', placeholder: '질문을 입력하고 Enter', multiline: true }], {
        onSave: function (v) {
          return api('POST', resultBase + '/questions', { section: sec.dataset.section, content: v[0] }).then(function (saved) {
            row.dataset.id = saved.id;
            row.classList.remove('is-new');
            content.textContent = v[0];
            var ta = row.querySelector('textarea.answer');
            ta.setAttribute('data-url', resultBase + '/questions/' + saved.id + '/answer');
            if (window.bindAnswer) window.bindAnswer(ta);
            return saveOrder();
          });
        },
        onCancel: function () { row.remove(); }
      });
    }

    function snapshot() {
      return Array.prototype.map.call(board.querySelectorAll('.iq-sec'), function (s) {
        return {
          section: s.dataset.section,
          questionIds: Array.prototype.filter.call(s.querySelectorAll('.iq'), function (q) { return q.dataset.id; })
            .map(function (q) { return Number(q.dataset.id); })
        };
      });
    }

    function saveOrder() {
      return api('PUT', resultBase + '/questions/order', snapshot());
    }

    var dragging = null;
    var before = null;

    board.addEventListener('mousedown', function (e) {
      var handle = e.target.closest('.iq-handle');
      if (!handle) return;
      var row = handle.closest('.iq');
      if (!row.classList.contains('is-new')) row.setAttribute('draggable', 'true');
    });
    document.addEventListener('mouseup', function () {
      board.querySelectorAll('[draggable=true]').forEach(function (el) { if (el !== dragging) el.removeAttribute('draggable'); });
    });
    board.addEventListener('dragstart', function (e) {
      dragging = e.target.closest('[draggable=true]');
      if (!dragging) return;
      before = JSON.stringify(snapshot());
      e.dataTransfer.effectAllowed = 'move';
      e.dataTransfer.setData('text/plain', '');
      setTimeout(function () { dragging.classList.add('is-dragging'); board.classList.add('is-sorting'); }, 0);
    });
    board.addEventListener('dragover', function (e) {
      if (!dragging) return;
      e.preventDefault();
      var sec = e.target.closest('.iq-sec');
      if (!sec) return;
      var list = sec.querySelector('.iq-list');
      var next = itemAfter(list, e.clientY);
      if (next == null) list.appendChild(dragging);
      else if (next !== dragging) list.insertBefore(dragging, next);
    });
    board.addEventListener('drop', function (e) { if (dragging) e.preventDefault(); });
    board.addEventListener('dragend', function () {
      if (!dragging) return;
      dragging.classList.remove('is-dragging');
      dragging.removeAttribute('draggable');
      board.classList.remove('is-sorting');
      dragging = null;
      if (JSON.stringify(snapshot()) === before) return;
      saveOrder().then(function () { toast('순서를 저장했습니다'); }, function () {
        setTimeout(function () { location.reload(); }, 1200);
      });
    });

    function itemAfter(list, y) {
      var items = Array.prototype.filter.call(list.children, function (el) {
        return el.classList.contains('iq') && !el.classList.contains('is-dragging');
      });
      for (var i = 0; i < items.length; i++) {
        var box = items[i].getBoundingClientRect();
        if (y < box.top + box.height / 2) return items[i];
      }
      return null;
    }
  }
})();

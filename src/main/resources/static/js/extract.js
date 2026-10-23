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
  initTitle();
  initConditions();
  initCriteria();
  initQuestions();
  initReview();
  // 질문 추출·합치기·면접 기록 뒤에는 그 영역만 새로 받아 오므로 다시 붙인다
  document.addEventListener('partial', function (e) {
    if (e.detail.some(function (el) { return el.id === 'questions'; })) initQuestions();
  });
  // 타임라인이 넓으면 최근 기록이 보이도록 오른쪽 끝에서 시작
  document.querySelectorAll('.tl-scroll').forEach(function (el) { el.scrollLeft = el.scrollWidth; });

  // ── 가린 이력서 검토: 드래그해서 가리기(같은 글자 모두), 되돌리기 ──
  function initReview() {
    var src = document.getElementById('masked-text');
    var view = document.getElementById('mask-view');
    if (!src || !view) return;
    var undoBtn = document.getElementById('mask-undo');
    var LABEL = /\[(?:이름|전화번호|이메일|주민번호|링크|생년월일|주소|개인정보|학교|회사\d*|[A-Z]사|연봉|경력|학력|교육|자격증)\]/g;
    var NEW_LABEL = '[개인정보]';
    var history = [];
    var maskBtn = document.createElement('button');
    maskBtn.type = 'button';
    maskBtn.className = 'mask-float';
    maskBtn.textContent = '가리기';
    maskBtn.hidden = true;
    document.body.appendChild(maskBtn);

    src.hidden = true;
    view.hidden = false;
    render();

    function segments(text) {
      var out = [], last = 0, m;
      LABEL.lastIndex = 0;
      while ((m = LABEL.exec(text))) {
        if (m.index > last) out.push({ mask: false, v: text.slice(last, m.index) });
        out.push({ mask: true, v: m[0] });
        last = m.index + m[0].length;
      }
      if (last < text.length) out.push({ mask: false, v: text.slice(last) });
      return out;
    }

    function join(segs) {
      return segs.map(function (s) { return s.v; }).join('');
    }

    function render() {
      view.textContent = '';
      segments(src.value).forEach(function (s) {
        if (!s.mask) {
          view.appendChild(document.createTextNode(s.v));
          return;
        }
        var chip = document.createElement('span');
        chip.className = 'pii';
        chip.textContent = s.v.slice(1, -1);
        view.appendChild(chip);
      });
      if (undoBtn) undoBtn.hidden = !history.length;
    }

    function set(text) {
      if (text === src.value) return;
      history.push(src.value);
      src.value = text;
      render();
    }

    // 가린 칸 밖의 같은 글자를 모두 가린다
    function maskAll(word) {
      set(join(segments(src.value).map(function (s) {
        return s.mask ? s : { mask: false, v: s.v.split(word).join(NEW_LABEL) };
      })));
    }

    function selection() {
      var sel = window.getSelection();
      if (!sel.rangeCount || sel.isCollapsed) return null;
      var range = sel.getRangeAt(0);
      if (!view.contains(range.commonAncestorContainer)) return null;
      var word = sel.toString().trim();
      return word ? { word: word, rect: range.getBoundingClientRect() } : null;
    }

    function placeButton() {
      var s = selection();
      maskBtn.hidden = !s;
      if (!s) return;
      maskBtn.style.top = (window.scrollY + s.rect.top - 40) + 'px';
      maskBtn.style.left = (window.scrollX + s.rect.left + s.rect.width / 2) + 'px';
    }

    view.addEventListener('mouseup', function () { setTimeout(placeButton, 0); });
    view.addEventListener('keyup', placeButton);
    view.addEventListener('scroll', function () { maskBtn.hidden = true; });
    document.addEventListener('mousedown', function (e) {
      if (e.target !== maskBtn) maskBtn.hidden = true;
    });
    // 누르는 순간 선택이 풀리지 않게
    maskBtn.addEventListener('mousedown', function (e) { e.preventDefault(); });
    maskBtn.addEventListener('click', function () {
      var s = selection();
      maskBtn.hidden = true;
      if (!s) return;
      window.getSelection().removeAllRanges();
      maskAll(s.word);
    });

    if (undoBtn) {
      undoBtn.addEventListener('click', function () {
        if (!history.length) return;
        src.value = history.pop();
        render();
      });
    }

    // 직접 고치기: 본문을 입력칸으로 바꿔 아무 글자나 고친다. 끝내면 다시 가린 칩으로 보여 준다
    var editBtn = document.getElementById('mask-edit');
    var before = null;
    if (editBtn) {
      editBtn.addEventListener('click', function () {
        if (before === null) {
          before = src.value;
          var top = view.scrollTop;
          view.hidden = true;
          src.hidden = false;
          src.classList.add('is-editing');
          src.focus({ preventScroll: true });
          src.setSelectionRange(0, 0);
          src.scrollTop = top;
          editBtn.textContent = '고치기 끝';
          if (undoBtn) undoBtn.hidden = true;
        } else {
          if (src.value !== before) history.push(before);
          before = null;
          src.hidden = true;
          src.classList.remove('is-editing');
          view.hidden = false;
          editBtn.textContent = '직접 고치기';
          render();
        }
      });
    }
  }

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

  // ── 이름 바꾸기: 포지션(제목·왼쪽 목록)과 지원자(왼쪽 목록)를 그 자리에서 고친다 ──
  function initTitle() {
    var title = document.getElementById('pos-title');
    var titleBtn = document.getElementById('pos-rename');

    // 바뀐 이름을 목록·제목·탭 제목에 모두 반영
    function applied(url, name) {
      document.querySelectorAll('.side-pos > a').forEach(function (a) {
        if (a.getAttribute('href') === url) a.querySelector('strong').textContent = name;
      });
      var candList = document.getElementById('cand-list');
      if (candList) {
        Array.prototype.forEach.call(candList.options, function (o) {
          if (o.value === url) o.textContent = name + ' · ' + o.getAttribute('data-tail');
        });
        candList.dispatchEvent(new Event('refit'));
      }
      if (title && url === positionBase) {
        title.textContent = name;
        document.title = name;
      }
      if (resultBase && url === resultBase) {
        document.querySelectorAll('[data-cand-name]').forEach(function (el) { el.textContent = name; });
        var avatar = document.querySelector('.pf-avatar');
        if (avatar) avatar.textContent = name.charAt(0);
        var pdf = document.getElementById('pdf-btn');
        if (pdf) pdf.setAttribute('data-filename', '면접질문지_' + name);
        document.title = name;
      }
    }

    function rename(target, btn, url, current, className) {
      if (target.hidden) return;
      var isCandidate = url.indexOf('/results/') >= 0;
      btn.hidden = true;
      editInline(target, [{ value: current, placeholder: isCandidate ? '지원자 이름' : '포지션 이름', className: className }], {
        onSave: function (v) {
          return api('PATCH', url, isCandidate ? { name: v[0] } : { title: v[0] }).then(function (saved) {
            applied(url, isCandidate ? saved.name : saved.title);
            btn.hidden = false;
          });
        },
        onCancel: function () { btn.hidden = false; }
      });
    }

    if (title) {
      var editTitle = function () { rename(title, titleBtn, positionBase, title.textContent, 'title-input'); };
      titleBtn.addEventListener('click', editTitle);
      title.addEventListener('click', editTitle);
    }
    // 포지션 목록의 이름 (평가 항목 추출 탭)
    document.addEventListener('click', function (e) {
      var btn = e.target.closest('.side-rename');
      if (!btn) return;
      var a = btn.closest('.side-pos').querySelector('a');
      rename(a, btn, a.getAttribute('href'), a.querySelector('strong').textContent, '');
    });
    // 지원자 이름 (적합도 검사 탭의 제목)
    var candTitle = document.getElementById('cand-title');
    var candBtn = document.getElementById('cand-rename');
    if (candTitle && candBtn && resultBase) {
      candBtn.addEventListener('click', function () {
        rename(candTitle, candBtn, resultBase, candTitle.textContent, 'title-input');
      });
    }
  }

  // ── 경력·학력 조건 고치기: 드롭다운으로 고르고, 정해진 문장으로 저장한다 ──
  function initConditions() {
    var dl = document.getElementById('conds');
    if (!dl) return;
    var btn = document.getElementById('conds-edit');
    var dd = {};
    dl.querySelectorAll('dd[data-key]').forEach(function (el) { dd[el.getAttribute('data-key')] = el; });
    var EDU = [['', '공고에 없음'], ['ANY', '학력무관'], ['HIGH', '고졸 이상'], ['COLLEGE', '초대졸(2·3년제) 이상'],
      ['UNIV', '대졸(4년제) 이상'], ['MASTER', '석사 이상'], ['DOCTOR', '박사 이상']];
    var years = [];
    for (var y = 1; y <= 20; y++) years.push([String(y), y + '년']);
    var form = null;
    var before = null;

    function select(options, value) {
      var s = document.createElement('select');
      s.className = 'cond-select';
      options.forEach(function (o) { s.add(new Option(o[1], o[0])); });
      s.value = value || '';
      return s;
    }

    function open() {
      before = {};
      Object.keys(dd).forEach(function (k) { before[k] = dd[k].textContent; });
      form = {
        expKind: select([['', '공고에 없음'], ['ANY', '경력무관'], ['MIN', '이상'], ['MAX', '이하']], dl.dataset.expKind),
        expYears: select(years, dl.dataset.expYears || '3'),
        edu: select(EDU, dl.dataset.edu)
      };
      var exp = document.createElement('div');
      exp.className = 'cond-row';
      exp.append(form.expYears, form.expKind);
      function sync() {
        form.expYears.hidden = !(form.expKind.value === 'MIN' || form.expKind.value === 'MAX');
      }
      form.expKind.addEventListener('change', sync);
      sync();
      dd.requiredExperience.replaceChildren(exp);
      dd.requiredEducation.replaceChildren(form.edu);
      form.major = document.createElement('input');
      form.major.type = 'text';
      form.major.className = 'inline-edit';
      form.major.placeholder = '예: 컴퓨터공학 등 관련 학과';
      form.major.value = before.requiredMajor;
      dd.requiredMajor.replaceChildren(form.major);

      var actions = document.createElement('div');
      actions.className = 'conds-actions';
      actions.innerHTML = '<button type="button" class="btn btn-ghost btn-sm">취소</button><button type="button" class="btn btn-primary btn-sm">저장</button>';
      actions.children[0].addEventListener('click', function () { close(null); });
      actions.children[1].addEventListener('click', save);
      dl.appendChild(actions);
      form.actions = actions;
      dl.classList.add('is-editing');
      btn.hidden = true;
      dl.addEventListener('keydown', onKey);
    }

    function onKey(e) {
      if (e.key === 'Escape') { e.preventDefault(); close(null); }
      if (e.key === 'Enter' && !e.isComposing) { e.preventDefault(); save(); }
    }

    function save() {
      var kind = form.expKind.value;
      var exp = kind === 'ANY' ? '경력무관'
        : kind ? '경력 ' + form.expYears.value + '년 ' + (kind === 'MIN' ? '이상' : '이하') : '';
      var eduLabel = EDU.filter(function (o) { return o[0] === form.edu.value; })[0];
      var edu = form.edu.value ? eduLabel[1] : '';
      var state = { expKind: kind, expYears: kind === 'MIN' || kind === 'MAX' ? form.expYears.value : '', edu: form.edu.value };
      var major = form.major.value.trim();
      api('PATCH', positionBase + '/conditions', { requiredExperience: exp, requiredEducation: edu, requiredMajor: major })
        .then(function () {
          Object.keys(state).forEach(function (k) { dl.dataset[k] = state[k]; });
          close({ requiredExperience: exp, requiredEducation: edu, requiredMajor: major });
          toast('저장했습니다');
        }, function () {});
    }

    function close(values) {
      var v = values || before;
      Object.keys(dd).forEach(function (k) { dd[k].textContent = v[k]; });
      form.actions.remove();
      form = null;
      dl.classList.remove('is-editing');
      dl.removeEventListener('keydown', onKey);
      btn.hidden = false;
    }

    btn.addEventListener('click', open);
  }

  // ── 평가항목 편집 ──
  function initCriteria() {
    var list = document.getElementById('crit-list');
    if (!list) return;
    var count = document.getElementById('crit-count');

    function refresh() { count.textContent = list.querySelectorAll('.crit[data-id]:not([data-id=""])').length; }

    function payload(li, name, desc) {
      return { name: name, description: desc };
    }

    function edit(li, isNew) {
      var body = li.querySelector('.crit-body');
      var name = li.querySelector('.crit-name');
      var desc = li.querySelector('.crit-desc');
      editInline(body, [
        { value: name.textContent, placeholder: '평가 기준', className: 'crit-name-input' },
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
      if (btn.classList.contains('js-crit-edit')) {
        edit(li, false);
      } else if (btn.classList.contains('js-crit-del')) {
        if (!confirm('"' + li.querySelector('.crit-name').textContent + '" 평가 기준을 삭제할까요?')) return;
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
    var pool = document.getElementById('qx-pool');
    if (pool) pool.addEventListener('click', onClick);
    if (!board) return;

    board.addEventListener('click', onClick);

    function onClick(e) {
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
        api('DELETE', resultBase + '/questions/' + row.dataset.id).then(function () { removeRow(row); toast('삭제했습니다'); });
      } else if (btn.classList.contains('js-q-add')) {
        addQuestion(btn.closest('.iq-sec'));
      } else if (btn.classList.contains('js-q-check') && row.dataset.id) {
        var checked = !row.classList.contains('is-checked');
        setChecked(row, checked);
        fetch(resultBase + '/questions/' + row.dataset.id + '/checked', {
          method: 'POST',
          headers: { 'X-Requested-With': 'fetch' },
          body: new URLSearchParams({ checked: checked })
        }).then(function (res) {
          if (!res.ok) throw new Error();
        }).catch(function () { setChecked(row, !checked); toast('저장하지 못했습니다'); });
      } else if (btn.classList.contains('js-q-must') && row.dataset.id) {
        var must = !row.classList.contains('is-must');
        setMust(row, must);
        fetch(resultBase + '/questions/' + row.dataset.id + '/must-ask', {
          method: 'POST',
          headers: { 'X-Requested-With': 'fetch' },
          body: new URLSearchParams({ mustAsk: must })
        }).then(function (res) {
          if (!res.ok) throw new Error();
        }).catch(function () { setMust(row, !must); toast('저장하지 못했습니다'); });
      }
    }

    function setMust(row, must) {
      row.classList.toggle('is-must', must);
      row.classList.remove('is-missing');
      var b = row.querySelector('.js-q-must');
      b.classList.toggle('is-on', must);
      b.setAttribute('aria-pressed', must);
    }

    // 필수질문 체크: 필수 질문 중 번호를 체크하지 않은 것을 알려 주고 그 질문으로 간다
    var mustCheck = document.getElementById('must-check');
    if (mustCheck) {
      var mustResult = document.getElementById('must-result');
      mustCheck.addEventListener('click', function () {
        var rows = Array.prototype.slice.call(board.querySelectorAll('.q-row'));
        var missing = [];
        var total = 0;
        rows.forEach(function (row, i) {
          row.classList.remove('is-missing');
          if (!row.classList.contains('is-must')) return;
          total++;
          if (!row.classList.contains('is-checked')) {
            row.classList.add('is-missing');
            missing.push({ no: i + 1, row: row });
          }
        });
        mustResult.hidden = false;
        mustResult.classList.toggle('is-bad', missing.length > 0);
        if (!total) {
          mustResult.textContent = '필수 질문이 없습니다.';
        } else if (!missing.length) {
          mustResult.textContent = '필수 질문 ' + total + '개를 모두 물었습니다.';
        } else {
          mustResult.textContent = '아직 묻지 않은 필수 질문: ' + missing.map(function (m) { return m.no + '번'; }).join(', ');
          missing[0].row.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
      });
    }

    // 추출한 질문: 다 지우면 합치기 버튼도 필요 없으니 새로 그린다
    function removeRow(row) {
      var inPool = pool && pool.contains(row);
      var sec = row.closest('.qx-sec');
      row.remove();
      if (!inPool) return;
      if (sec && !sec.querySelector('.iq')) sec.remove();
      var left = pool.querySelectorAll('.iq').length;
      if (!left) window.refreshPartial('#questions');
      else pool.querySelector('.qx-pool-head b').textContent = left;
    }

    function setChecked(row, checked) {
      row.classList.toggle('is-checked', checked);
      if (checked) row.classList.remove('is-missing');
      row.querySelector('.js-q-check').setAttribute('aria-pressed', checked);
    }

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

// ── 면접 질문지 탭(질문지 / 면접 기록), 입력 칸 열고 닫기, 고른 파일 이름 ──
(function () {
  document.addEventListener('click', function (e) {
    var btn = e.target.closest('[data-open]');
    if (!btn) return;
    var panel = document.getElementById(btn.getAttribute('data-open'));
    if (!panel) return;
    panel.hidden = !panel.hidden;
    if (!panel.hidden) {
      var ta = panel.querySelector('textarea');
      if (ta) ta.focus();
    }
  });

  initTabs(true);
  document.addEventListener('partial', function () { initTabs(false); });

  function initTabs(first) {
  var tabs = document.querySelectorAll('[data-qtab]');
  if (tabs.length) {
    var showTab = function (name) {
      tabs.forEach(function (t) { t.setAttribute('aria-selected', t.getAttribute('data-qtab') === name); });
      document.querySelectorAll('[data-qpane]').forEach(function (p) { p.hidden = p.getAttribute('data-qpane') !== name; });
      if (window.autosize) document.querySelectorAll('[data-qpane="' + name + '"] textarea.answer').forEach(window.autosize);
      try { localStorage.setItem('qTab', name); } catch (err) { /* 무시 */ }
    };
    tabs.forEach(function (t) {
      t.addEventListener('click', function () { showTab(t.getAttribute('data-qtab')); });
    });
    var start = 'sheet';
    try { start = localStorage.getItem('qTab') || 'sheet'; } catch (err) { /* 무시 */ }
    if (first && location.hash === '#record') start = 'record';
    // 질문 추출·질문지로 돌아오면 타이핑 탭
    if (first && (location.hash === '#extract' || location.hash === '#questions')) start = 'sheet';
    showTab(start === 'record' ? 'record' : 'sheet');
    if (first && location.hash === '#record') document.getElementById('questions').scrollIntoView();
  }

  var form = document.getElementById('rec-form');
  if (!form) return;
  var file = form.querySelector('input[type=file]');
  file.addEventListener('change', function () {
    form.querySelector('.aa-name').textContent = file.files.length ? file.files[0].name : '';
    // 파일을 고르면 붙여넣은 글 대신 파일로 만든다
    if (file.files.length) form.querySelector('textarea').value = '';
  });
  }
})();

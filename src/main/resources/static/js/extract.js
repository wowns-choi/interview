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
  initExtractDialog();
  initReview();
  initWriting();
  // 질문 추출·합치기·면접 기록 뒤에는 그 영역만 새로 받아 오므로 다시 붙인다
  document.addEventListener('partial', function (e) {
    if (e.detail.some(function (el) { return el.id === 'questions'; })) {
      initQuestions();
      initExtractDialog();
      revealExtracted();
    }
  });

  // 추출이 끝나면 추출한 질문으로 부드럽게 내려가고, 새로 나온 질문이 차례로 떠오른다
  var beforeExtract = null;
  function revealExtracted() {
    var before = beforeExtract;
    beforeExtract = null;
    var pool = document.getElementById('qx-pool');
    if (!before || !pool) return;
    var fresh = Array.prototype.filter.call(pool.querySelectorAll('.qx-row'), function (li) {
      return before.indexOf(li.getAttribute('data-id')) < 0;
    });
    if (!fresh.length) return;
    pool.scrollIntoView({ behavior: 'smooth', block: 'start' });
    pool.classList.remove('is-new');
    void pool.offsetWidth;
    pool.classList.add('is-new');
    // 「질문지에 넣기」도 몇 번 고동쳐 다음 할 일을 알린다
    var merge = pool.querySelector('.qx-pool-head button');
    if (merge) {
      merge.classList.add('is-hint');
      setTimeout(function () { merge.classList.remove('is-hint'); }, 3600);
    }
    fresh.forEach(function (li, i) {
      li.style.setProperty('--i', i);
      li.classList.add('is-arriving');
    });
    setTimeout(function () {
      pool.classList.remove('is-new');
      fresh.forEach(function (li) { li.classList.remove('is-arriving'); li.style.removeProperty('--i'); });
    }, 1400 + fresh.length * 90);
  }

  // ── 평가 기준을 막 추출했으면 조건·평가 기준을 글 쓰듯 채운다 (사용자 2026-10-27: 한꺼번에 팍 나오던 것) ──
  // 누르거나 키를 누르면 바로 끝까지 보인다
  function initWriting() {
    var grid = document.querySelector('.pos-grid[data-write]');
    if (!grid || (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches)) return;
    // 공고는 바로 보이고, 조건·평가 기준만 쓴다 (사용자 2026-10-27: 공고까지 써지니 불편)
    var tracks = [[], []];
    var all = [];
    var add = function (track, el, msPerChar, maxMs, row) {
      var text = el.textContent;
      if (!text.trim()) return;
      el.textContent = '';
      el.classList.add('wr-wait');
      if (row) row.classList.add('wr-wait');
      var s = { el: el, text: text, ms: Math.min(maxMs, text.length * msPerChar), row: row };
      tracks[track].push(s);
      all.push(s);
    };
    grid.querySelectorAll('#conds dd').forEach(function (dd) { add(1, dd, 30, 500); });
    grid.querySelectorAll('#crit-list .crit').forEach(function (li) {
      add(1, li.querySelector('.crit-name'), 30, 700, li);
      add(1, li.querySelector('.crit-desc'), 12, 900);
    });
    if (!all.length) return;

    var done = false;
    var show = function (s) {
      s.el.classList.remove('wr-wait');
      if (s.row) s.row.classList.remove('wr-wait');
    };
    var carets = [];
    var finish = function () {
      if (done) return;
      done = true;
      carets.forEach(function (c) { c.remove(); });
      all.forEach(function (s) { show(s); s.el.textContent = s.text; });
      document.removeEventListener('pointerdown', finish, true);
      document.removeEventListener('keydown', finish, true);
    };
    var left = tracks.filter(function (t) { return t.length; }).length;
    var run = function (steps) {
      var caret = document.createElement('span');
      caret.className = 'wr-caret';
      carets.push(caret);
      var i = 0, node, startAt;
      var begin = function () {
        show(steps[i]);
        node = document.createTextNode('');
        steps[i].el.replaceChildren(node, caret);
        startAt = performance.now();
      };
      var frame = function (now) {
        if (done) return;
        var s = steps[i];
        var n = Math.min(s.text.length, Math.ceil((now - startAt) / s.ms * s.text.length));
        node.data = s.text.slice(0, n);
        if (n >= s.text.length) {
          s.el.textContent = s.text;
          if (++i >= steps.length) {
            caret.remove();
            if (--left === 0) finish();
            return;
          }
          begin();
        }
        requestAnimationFrame(frame);
      };
      begin();
      requestAnimationFrame(frame);
    };
    document.addEventListener('pointerdown', finish, true);
    document.addEventListener('keydown', finish, true);
    tracks.forEach(function (t) { if (t.length) run(t); });
  }


  // ── 개인정보 엄격 보호 모드: 가린 이력서 확인. 드래그해서 가리기(같은 글자 모두), 직접 고치기, 되돌리기 ──
  function initReview() {
    var src = document.getElementById('masked-text');
    var view = document.getElementById('mask-view');
    if (!src || !view) return;
    var undoBtn = document.getElementById('mask-undo');
    var LABEL = /\[(?:이름|전화번호|이메일|주민번호|링크|생년월일|주소|개인정보|학교\d*|회사\d*|[A-Z]사|연봉|경력|학력|교육\d*|활동\d+|자격증)\]/g;
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

  // ── 면접 질문 추출 창: 기본 네 유형은 켜고 끄기만, 직접 만든 유형은 추가하면 바로 서버에 저장되고 ×로 바로 지운다.
  //    켜고 끈 상태는 「추출」을 누를 때만 브라우저에 남고, 취소·닫기는 그 상태로 되돌린다 ──
  function initExtractDialog() {
    var chips = document.getElementById('qx-chips');
    var form = document.getElementById('qx-form');
    if (!chips || !form) return;
    var dialog = form.closest('dialog');
    var url = chips.getAttribute('data-url');
    var addBtn = document.getElementById('qx-add');
    var input = document.getElementById('qx-new');
    var newRow = document.getElementById('qx-new-row');
    var submit = form.querySelector('[type=submit]');
    var submitted = false;

    var boxes = function () { return Array.prototype.slice.call(chips.querySelectorAll('.qx-type input')); };
    var savedOff = function () {
      try {
        var off = JSON.parse(localStorage.getItem('qxOff') || '[]');
        return Array.isArray(off) ? off : [];
      } catch (err) { return []; }
    };
    var sync = function () { submit.disabled = !chips.querySelector('.qx-type input:checked'); };
    var reset = function () {
      var off = savedOff();
      boxes().forEach(function (b) { b.checked = off.indexOf(b.value) < 0; });
      closeInput();
      sync();
    };
    var customChip = function (type) {
      var wrap = document.createElement('span');
      wrap.className = 'qx-chip';
      wrap.setAttribute('data-id', type.id);
      var label = document.createElement('label');
      label.className = 'qx-type';
      var box = document.createElement('input');
      box.type = 'checkbox';
      box.name = 'type';
      box.value = type.name;
      box.checked = true;
      var text = document.createElement('span');
      text.textContent = type.name;
      label.append(box, text);
      var del = document.createElement('button');
      del.type = 'button';
      del.className = 'qx-chip-del';
      del.title = '지우기';
      del.innerHTML = '<svg class="i"><use href="#i-x"/></svg>';
      wrap.append(label, del);
      return wrap;
    };
    var closeInput = function () {
      newRow.hidden = true;
      input.value = '';
      addBtn.hidden = false;
    };
    var saving = false;
    var commitInput = function () {
      if (newRow.hidden || saving) return;
      var name = input.value.replace(/\s+/g, ' ').trim();
      if (!name) { closeInput(); return; }
      var same = boxes().find(function (b) { return b.value.toLowerCase() === name.toLowerCase(); });
      if (same) { same.checked = true; closeInput(); sync(); return; }
      saving = true;
      api('POST', url, { name: name }).then(function (type) {
        var there = chips.querySelector('.qx-chip[data-id="' + type.id + '"] input');
        if (there) there.checked = true;
        else chips.insertBefore(customChip(type), addBtn);
        closeInput();
        sync();
      }).catch(function () { input.focus(); }).then(function () { saving = false; });
    };

    reset();
    addBtn.addEventListener('click', function () {
      addBtn.hidden = true;
      newRow.hidden = false;
      input.focus();
    });
    input.addEventListener('keydown', function (e) {
      if (e.isComposing) return;
      if (e.key === 'Enter') { e.preventDefault(); commitInput(); }
      if (e.key === 'Escape') { e.preventDefault(); closeInput(); addBtn.focus(); }
    });
    document.getElementById('qx-new-save').addEventListener('click', function () {
      if (input.value.trim()) commitInput(); else input.focus();
    });
    document.getElementById('qx-new-cancel').addEventListener('click', function () { closeInput(); addBtn.focus(); });
    chips.addEventListener('change', sync);
    // ×: 서버에서 바로 지운다
    chips.addEventListener('click', function (e) {
      var del = e.target.closest('.qx-chip-del');
      if (!del) return;
      var chip = del.closest('.qx-chip');
      api('DELETE', url + '/' + chip.getAttribute('data-id')).then(function () {
        chip.remove();
        sync();
      }).catch(function () { /* 토스트로 알림 */ });
    });
    if (dialog) dialog.addEventListener('close', function () {
      if (!submitted) reset();
      submitted = false;
    });
    form.addEventListener('submit', function (e) {
      if (!chips.querySelector('.qx-type input:checked')) {
        e.preventDefault();
        e.stopImmediatePropagation();
        return;
      }
      var off = boxes().filter(function (b) { return !b.checked; }).map(function (b) { return b.value; });
      try { localStorage.setItem('qxOff', JSON.stringify(off)); } catch (err) { /* 무시 */ }
      submitted = true;
      beforeExtract = Array.prototype.map.call(document.querySelectorAll('#qx-pool .qx-row'), function (li) { return li.getAttribute('data-id'); });
      if (dialog && dialog.open) dialog.close();
    });
  }
  // 타임라인이 넓으면 최근 기록이 보이도록 오른쪽 끝에서 시작
  document.querySelectorAll('.tl-scroll').forEach(function (el) { el.scrollLeft = el.scrollWidth; });

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
    var go = document.getElementById('crit-go');

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
      ready();
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
    // 공고 글이나 파일이 있어야 버튼이 살아난다
    function ready() { if (go) go.disabled = text.value.trim() === '' && files.length === 0; }
    text.addEventListener('input', ready);
    ready();
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
      document.querySelectorAll('#cand-list, select[data-cand-copy]').forEach(function (candList) {
        Array.prototype.forEach.call(candList.options, function (o) {
          if (o.value === url) o.setAttribute('data-name', name);
        });
        if (window.candLabel) window.candLabel(candList);
        candList.dispatchEvent(new Event('refit'));
      });
      if (title && url === positionBase) {
        title.textContent = name;
        document.title = name;
      }
      if (resultBase && url === resultBase) {
        document.querySelectorAll('[data-cand-name]').forEach(function (el) { el.textContent = name; });
        var avatar = document.querySelector('.pf-avatar');
        if (avatar) avatar.textContent = name.charAt(0);
        var pdf = document.getElementById('pdf-btn');
        if (pdf) pdf.setAttribute('data-filename', '면접질문준비_' + name);
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

  // ── 경력·학력 조건 고치기: 칸마다 연필을 누르면 그 칸만 드롭다운이 되고, 고르면(학과는 Enter·칸 밖으로) 정해진 문장으로 바로 저장. Esc는 취소 (사용자 2026-10-27) ──
  function initConditions() {
    var dl = document.getElementById('conds');
    if (!dl) return;
    var dd = {};
    dl.querySelectorAll('dd[data-key]').forEach(function (el) { dd[el.getAttribute('data-key')] = el; });
    var EDU = [['', '공고에 없음'], ['ANY', '학력무관'], ['HIGH', '고졸 이상'], ['COLLEGE', '초대졸(2·3년제) 이상'],
      ['UNIV', '대졸(4년제) 이상'], ['MASTER', '석사 이상'], ['DOCTOR', '박사 이상']];
    var years = [];
    for (var y = 1; y <= 20; y++) years.push([String(y), y + '년']);
    var editing = null; // { key, cell, before, value(), state(), cleanup }

    function select(options, value) {
      var s = document.createElement('select');
      s.className = 'cond-select';
      options.forEach(function (o) { s.add(new Option(o[1], o[0])); });
      s.value = value || '';
      return s;
    }

    function texts() {
      var t = {};
      Object.keys(dd).forEach(function (k) { t[k] = dd[k].textContent; });
      return t;
    }

    function open(key) {
      if (editing) close(false);
      var cell = dd[key];
      var e = { key: key, cell: cell, before: cell.textContent };
      if (key === 'requiredExperience') {
        var kind = select([['', '공고에 없음'], ['ANY', '경력무관'], ['MIN', '이상'], ['MAX', '이하']], dl.dataset.expKind);
        var yrs = select(years, dl.dataset.expYears || '3');
        var row = document.createElement('div');
        row.className = 'cond-row';
        row.append(yrs, kind);
        var sync = function () { yrs.hidden = !(kind.value === 'MIN' || kind.value === 'MAX'); };
        sync();
        kind.addEventListener('change', function () { sync(); save(); });
        yrs.addEventListener('change', save);
        e.value = function () {
          return kind.value === 'ANY' ? '경력무관'
            : kind.value ? '경력 ' + yrs.value + '년 ' + (kind.value === 'MIN' ? '이상' : '이하') : '';
        };
        e.state = function () { return { expKind: kind.value, expYears: kind.value === 'MIN' || kind.value === 'MAX' ? yrs.value : '' }; };
        cell.replaceChildren(row);
        e.focus = kind;
      } else if (key === 'requiredEducation') {
        var edu = select(EDU, dl.dataset.edu);
        edu.addEventListener('change', save);
        e.value = function () { return edu.value ? EDU.filter(function (o) { return o[0] === edu.value; })[0][1] : ''; };
        e.state = function () { return { edu: edu.value }; };
        cell.replaceChildren(edu);
        e.focus = edu;
      } else {
        var major = document.createElement('input');
        major.type = 'text';
        major.className = 'inline-edit';
        major.placeholder = '예: 컴퓨터공학';
        major.value = e.before;
        major.addEventListener('blur', function () { if (editing === e) save(); });
        e.value = function () { return major.value.trim(); };
        e.state = function () { return {}; };
        cell.replaceChildren(major);
        e.focus = major;
      }
      editing = e;
      cell.parentNode.classList.add('is-editing');
      dl.classList.add('is-cell-editing');
      e.focus.focus();
    }

    function close(keep) {
      var e = editing;
      if (!e) return;
      editing = null;
      e.cell.textContent = keep ? e.value() : e.before;
      e.cell.parentNode.classList.remove('is-editing');
      dl.classList.remove('is-cell-editing');
    }

    function save() {
      var e = editing;
      if (!e) return;
      if (e.saving) return;
      var value = e.value();
      if (value === e.before) { close(false); return; }
      e.saving = true;
      var body = texts();
      body[e.key] = value;
      var state = e.state();
      // 경력 칸이면 고른 값도 보내 서버가 개월(하한·상한)로 저장한다
      if (e.key === 'requiredExperience') {
        body.expKind = state.expKind;
        body.expYears = state.expYears;
      }
      api('PATCH', positionBase + '/conditions', body).then(function () {
        Object.keys(state).forEach(function (k) { dl.dataset[k] = state[k]; });
        e.saving = false;
        if (editing === e) close(true);
        else if (!editing || editing.cell !== e.cell) e.cell.textContent = value;
        toast('저장했습니다');
      }, function () { e.saving = false; });
    }

    dl.addEventListener('click', function (ev) {
      var pen = ev.target.closest('.cond-pen');
      if (pen) open(pen.getAttribute('data-edit'));
    });
    dl.addEventListener('keydown', function (ev) {
      if (!editing) return;
      if (ev.key === 'Escape') { ev.preventDefault(); close(false); }
      if (ev.key === 'Enter' && !ev.isComposing && ev.target.tagName === 'INPUT') { ev.preventDefault(); save(); }
    });
    // 고르기만 하고 바깥을 누르면 그대로 닫는다 (고른 값은 이미 저장됐다)
    document.addEventListener('pointerdown', function (ev) {
      if (editing && !editing.cell.parentNode.contains(ev.target)) {
        if (editing.cell.querySelector('input')) save(); else close(false);
      }
    });
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
      } else if (btn.classList.contains('js-sec-rename')) {
        renameSection(btn.closest('.iq-sec'));
      } else if (btn.classList.contains('js-sec-del')) {
        deleteSection(btn.closest('.iq-sec'));
      } else if (btn.classList.contains('js-sec-add')) {
        addSection(btn);
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

    // 필수질문 체크: 필수 질문 중 번호를 체크하지 않은 것과 면접 본 날짜를 비워 둔 것을 알려 주고 그리로 간다
    document.querySelectorAll('.js-must-check').forEach(function (mustCheck) {
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
        var date = document.getElementById('iv-date');
        var noDate = date && !date.value;
        if (date) date.classList.toggle('is-missing', noDate);
        var lines = [];
        if (noDate) lines.push('면접 본 날짜를 적지 않았습니다.');
        if (missing.length) {
          lines.push('아직 묻지 않은 필수 질문: ' + missing.map(function (m) { return m.no + '번'; }).join(', '));
        } else if (!noDate) {
          lines.push(total ? '필수 질문 ' + total + '개를 모두 물었습니다.' : '필수 질문이 없습니다.');
        }
        if (window.showToast) window.showToast(lines.join(' '), noDate || missing.length > 0, 4500);
        var first = noDate ? date : missing.length ? missing[0].row : null;
        if (first) first.scrollIntoView({ behavior: 'smooth', block: 'center' });
      });
    });

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

    // ── 카테고리 (사용자 2026-10-28): 질문지의 카테고리는 질문마다 붙은 이름이라, 바꾸면 순서 저장으로 그 질문들의 이름이 바뀐다.
    //    이 지원자 질문지에만 적용되고 공통 질문 설정은 그대로다 ──
    function sectionNames(except) {
      return Array.prototype.filter.call(board.querySelectorAll('.iq-sec'), function (s) { return s !== except; })
        .map(function (s) { return s.dataset.section; });
    }

    function nameSection(sec, value, onCancel) {
      var title = sec.querySelector('.iq-sec-title');
      editInline(title, [{ value: value, placeholder: '카테고리 이름을 입력하고 Enter', className: 'iq-sec-input' }], {
        onSave: function (v) {
          if (sectionNames(sec).indexOf(v[0]) >= 0) {
            toast('같은 이름의 카테고리가 있습니다');
            return Promise.reject();
          }
          var old = sec.dataset.section;
          sec.dataset.section = v[0];
          title.textContent = v[0];
          if (!sec.querySelector('.iq[data-id]')) return Promise.resolve();
          return saveOrder().then(function () { toast('저장했습니다'); }, function (err) {
            sec.dataset.section = old;
            title.textContent = old;
            throw err;
          });
        },
        onCancel: onCancel
      });
    }

    function renameSection(sec) {
      nameSection(sec, sec.dataset.section);
    }

    function deleteSection(sec) {
      if (sec.querySelector('.iq')) {
        toast('질문을 다른 카테고리로 옮기거나 지운 뒤에 삭제할 수 있습니다');
        return;
      }
      sec.remove();
    }

    function addSection(btn) {
      var sec = board.querySelector('.iq-sec').cloneNode(true);
      sec.querySelector('.iq-list').innerHTML = '';
      sec.dataset.section = '';
      sec.querySelector('.iq-sec-title').textContent = '';
      board.insertBefore(sec, btn);
      // 이름을 정하지 않고 닫으면 없던 일로 한다
      nameSection(sec, '', function () { if (!sec.dataset.section) sec.remove(); });
    }

    function snapshot() {
      return Array.prototype.filter.call(board.querySelectorAll('.iq-sec'), function (s) { return s.dataset.section; }).map(function (s) {
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

// ── 입력 칸 열고 닫기, 면접 기록(녹음본) 폼의 고른 파일 이름 ──
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

  initRecord();
  document.addEventListener('partial', initRecord);

  // ── 면접 내용 정리를 스트림으로 (사용자 2026-11-06): 항목을 받는 대로 면접관 질문·지원자 질문 목록에 하나씩 붙인다.
  //    다 끝나면 저장된 결과로 다시 그린다. 실패하면 원래 화면으로 되돌린다 ──
  window.streamRecord = function (form) {
    var box = document.getElementById('record');
    if (!box || box.dataset.streaming) return;
    var before = Array.prototype.slice.call(box.childNodes);
    var body = new FormData(form);
    box.dataset.streaming = '1';

    var el = function (tag, cls, text) {
      var n = document.createElement(tag);
      if (cls) n.className = cls;
      if (text != null) n.textContent = text;
      return n;
    };
    var head = el('div', 'rec-head');
    var live = el('span', 'rec-live', '녹음본을 읽는 중');
    head.appendChild(live);
    var groups = {};
    var view = document.createDocumentFragment();
    view.appendChild(head);
    [['INTERVIEWER', '면접관 질문'], ['CANDIDATE', '지원자 질문']].forEach(function (g) {
      var sec = el('section', 'rec-group');
      var h = el('h3', 'iq-sec-name', g[1] + ' ');
      var count = el('span', 'rec-count', '0');
      h.appendChild(count);
      var list = el('ol', 'rec-list');
      sec.appendChild(h);
      sec.appendChild(list);
      view.appendChild(sec);
      groups[g[0]] = { list: list, count: count, n: 0 };
    });
    box.replaceChildren(view);

    var total = 0;
    var add = function (it) {
      var g = groups[it.kind === 'CANDIDATE' ? 'CANDIDATE' : 'INTERVIEWER'];
      g.n++;
      total++;
      g.count.textContent = g.n;
      live.textContent = '정리하는 중 · ' + total + '개';
      var li = el('li', 'rec-item is-new');
      var q = el('p', 'rec-q');
      q.appendChild(el('span', 'rec-no', 'Q' + g.n));
      q.appendChild(document.createTextNode(it.question));
      li.appendChild(q);
      li.appendChild(el('p', 'rec-a', it.answer));
      if (it.excerpt) {
        var ex = el('p', 'q-excerpt');
        ex.appendChild(el('span', null, '녹취'));
        ex.appendChild(document.createTextNode(it.excerpt));
        li.appendChild(ex);
      }
      g.list.appendChild(li);
    };
    var fail = function (msg) {
      delete box.dataset.streaming;
      box.replaceChildren.apply(box, before);
      window.showToast(msg || '면접 내용을 정리하지 못했습니다.', true);
    };

    fetch(form.getAttribute('data-stream'), { method: 'POST', body: body, headers: { 'X-Requested-With': 'fetch' } })
      .then(function (res) {
        if (!res.ok || !res.body) {
          return res.text().then(function (t) { fail(t); });
        }
        var reader = res.body.getReader();
        var decoder = new TextDecoder();
        var buf = '';
        var finished = false;
        var handle = function (line) {
          if (!line.trim()) return;
          var m = JSON.parse(line);
          if (m.item) add(m.item);
          else if (m.error) { finished = true; fail(m.error); }
          else if (m.done) {
            finished = true;
            delete box.dataset.streaming;
            window.refreshPartial('#record-stage').then(function () {
              window.showToast('질문-답변 ' + m.count + '개를 정리했습니다.', false);
            });
          }
        };
        var pump = function () {
          return reader.read().then(function (r) {
            if (r.done) {
              if (buf) handle(buf);
              if (!finished) fail('연결이 끊겼습니다. 다시 정리해 주세요.');
              return;
            }
            buf += decoder.decode(r.value, { stream: true });
            var lines = buf.split('\n');
            buf = lines.pop();
            lines.forEach(handle);
            return pump();
          });
        };
        return pump();
      })
      .catch(function () { fail(); });
  };

  function initRecord() {
  var form = document.getElementById('rec-form');
  // 다른 영역만 새로 그려졌으면 폼은 그대로라 다시 묶지 않는다
  if (!form || form.dataset.bound) return;
  form.dataset.bound = '1';
  var file = form.querySelector('input[type=file]');
  var text = form.querySelector('textarea');
  var dropped = form.querySelector('.aa-dropped');
  // 파일을 고르면 붙여넣은 글 대신 파일로 만든다: 글 칸 자리에 파일 이름을 보여 준다
  var showFile = function () {
    var has = file.files.length > 0;
    form.querySelector('.aa-name').textContent = has ? file.files[0].name : '';
    dropped.hidden = !has;
    text.hidden = has;
    if (has) text.value = '';
  };
  file.addEventListener('change', showFile);
  form.querySelector('.js-aa-clear').addEventListener('click', function () {
    file.value = '';
    showFile();
    text.focus();
  });
  ['dragenter', 'dragover'].forEach(function (type) {
    form.addEventListener(type, function (e) {
      if (!e.dataTransfer || Array.prototype.indexOf.call(e.dataTransfer.types, 'Files') < 0) return;
      e.preventDefault();
      form.classList.add('is-over');
    });
  });
  form.addEventListener('dragleave', function (e) {
    if (!form.contains(e.relatedTarget)) form.classList.remove('is-over');
  });
  form.addEventListener('drop', function (e) {
    form.classList.remove('is-over');
    if (!e.dataTransfer || !e.dataTransfer.files.length) return;
    e.preventDefault();
    var dt = new DataTransfer();
    dt.items.add(e.dataTransfer.files[0]);
    file.files = dt.files;
    showFile();
  });
  }
})();

(function () {
  // ── 폼 제출: data-confirm 확인, data-busy 진행 중 표시 ──
  document.addEventListener('submit', function (e) {
    var form = e.target;
    var msg = form.getAttribute('data-confirm');
    if (msg && !window.confirm(msg)) {
      e.preventDefault();
      return;
    }
    var busy = form.getAttribute('data-busy');
    if (busy) {
      var overlay = document.getElementById('busy-overlay');
      document.getElementById('busy-text').textContent = busy;
      var sub = form.getAttribute('data-busy-sub');
      if (sub) document.getElementById('busy-sub').textContent = sub;
      overlay.hidden = false;
      Array.prototype.forEach.call(form.elements, function (el) { if (el.tagName === 'BUTTON') el.disabled = true; });
    }
  });

  // ── 포지션 고르기: 고르면 그 주소로 ──
  document.querySelectorAll('select[data-nav]').forEach(function (sel) {
    sel.addEventListener('change', function () { location.href = sel.value; });
    if (!sel.closest('.pick-select')) return; // 사이드바의 지원자 고르기는 칸 폭 그대로
    // 고른 이름 길이에 맞춘다 (select 폭은 보통 가장 긴 option 기준이라 제목 크기 글자가 잘린다)
    var fit = function () {
      var probe = document.createElement('span');
      var cs = getComputedStyle(sel);
      probe.style.cssText = 'position:absolute;visibility:hidden;white-space:pre;font:' + cs.font + ';letter-spacing:' + cs.letterSpacing;
      probe.textContent = sel.options[sel.selectedIndex] ? sel.options[sel.selectedIndex].text : '';
      document.body.appendChild(probe);
      sel.style.width = Math.ceil(probe.getBoundingClientRect().width + parseFloat(cs.paddingLeft) + parseFloat(cs.paddingRight) + 12) + 'px';
      probe.remove();
    };
    fit();
    if (document.fonts) document.fonts.ready.then(fit);
    sel.addEventListener('refit', fit); // 고른 항목의 글자가 바뀌었을 때
  });

  // ── 표의 행 전체 클릭 ──
  document.querySelectorAll('tr[data-href]').forEach(function (tr) {
    tr.addEventListener('click', function (e) {
      if (e.target.closest('a')) return;
      location.href = tr.getAttribute('data-href');
    });
  });

  // ── 지원자 비교: 경력·학력 미충족 제외, 제외됨 접기 ──
  var rk = document.getElementById('rk');
  if (rk) {
    var rkBase = rk.getAttribute('data-base');
    var rkSave = function (path, body) {
      return fetch(rkBase + path, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'fetch' },
        body: JSON.stringify(body)
      }).then(function (res) {
        if (!res.ok) return res.text().then(function (t) { throw new Error(t || '저장하지 못했습니다.'); });
        location.reload();
      }).catch(function (err) { alert(err.message); });
    };
    rk.querySelectorAll('[data-rk-exclude]').forEach(function (box) {
      box.addEventListener('change', function () {
        rkSave('/exclusions', {
          career: rk.querySelector('[data-rk-exclude=career]').checked,
          education: rk.querySelector('[data-rk-exclude=education]').checked
        });
      });
    });
    document.querySelectorAll('[data-rk-fold]').forEach(function (fold) {
      fold.addEventListener('click', function () {
        var open = fold.classList.toggle('is-open');
        document.querySelectorAll('.rk-row.is-EXCLUDED').forEach(function (tr) { tr.hidden = !open; });
      });
    });
    // 면접 본 사람만·합격한 사람만: 화면에서만 거른다 (고른 것은 브라우저에 기억)
    var filters = rk.querySelectorAll('[data-rk-filter]');
    var applyFilters = function () {
      var only = {};
      filters.forEach(function (f) { only[f.getAttribute('data-rk-filter')] = f.checked; });
      try { localStorage.setItem('rkFilters', JSON.stringify(only)); } catch (e) { /* 무시 */ }
      document.querySelectorAll('.rk-row').forEach(function (tr) {
        var out = (only.interviewed && tr.getAttribute('data-interviewed') !== 'true')
          || (only.passed && tr.getAttribute('data-decision') !== 'PASS')
          || (only.engineer && tr.getAttribute('data-engineer') !== 'true')
          || (only.sqld && tr.getAttribute('data-sqld') !== 'true');
        tr.classList.toggle('is-filtered', out);
      });
      // 묶음 제목은 그 아래에 남은 사람이 없으면 숨긴다
      document.querySelectorAll('.rk-group').forEach(function (g) {
        var left = false;
        for (var n = g.nextElementSibling; n && !n.classList.contains('rk-group'); n = n.nextElementSibling) {
          if (!n.classList.contains('is-filtered')) left = true;
        }
        g.classList.toggle('is-filtered', !left);
      });
    };
    try {
      var saved = JSON.parse(localStorage.getItem('rkFilters') || '{}');
      filters.forEach(function (f) { f.checked = !!saved[f.getAttribute('data-rk-filter')]; });
    } catch (e) { /* 저장소를 못 쓰면 끈 채로 */ }
    filters.forEach(function (f) { f.addEventListener('change', applyFilters); });
    applyFilters();
  }

  // ── 이력서 드롭존 (1인은 하나, 다수는 지원자마다 하나) ──
  var DZ_TEXT = '이력서·포트폴리오를 끌어다 놓거나 클릭';
  function showFiles(dz) {
    var input = dz.querySelector('input[type=file]');
    var names = Array.prototype.map.call(input.files || [], function (f) { return f.name; });
    dz.classList.toggle('has-file', names.length > 0);
    dz.querySelector('.dz-text').textContent = names.length ? names.join(', ') : DZ_TEXT;
  }
  function bindDropzone(dz) {
    dz.querySelector('input[type=file]').addEventListener('change', function () { showFiles(dz); });
    ['dragenter', 'dragover'].forEach(function (t) {
      dz.addEventListener(t, function () { dz.classList.add('is-over'); });
    });
    ['dragleave', 'drop'].forEach(function (t) {
      dz.addEventListener(t, function () { dz.classList.remove('is-over'); });
    });
    showFiles(dz);
  }
  document.querySelectorAll('.dropzone').forEach(bindDropzone);

  // ── 포지션 적합도 검사(다수): 지원자 칸 추가·빼기. "지원자 추가"에 파일을 여러 개 놓으면 파일마다 한 칸 ──
  var bzGrid = document.getElementById('bz-grid');
  if (bzGrid) {
    var bzNext = bzGrid.querySelectorAll('.bz').length + 1;
    var addZone = function (file) {
      var zone = bzGrid.querySelector('.bz').cloneNode(true);
      var dz = zone.querySelector('.dropzone');
      var input = dz.querySelector('input[type=file]');
      input.value = '';
      input.name = 'applicant-' + bzNext;
      zone.querySelector('.bz-no').textContent = '지원자 ' + bzNext;
      bzNext++;
      if (file) {
        var dt = new DataTransfer();
        dt.items.add(file);
        input.files = dt.files;
      }
      bzGrid.appendChild(zone);
      bindDropzone(dz);
      relabel();
      return zone;
    };
    // 화면의 "지원자 n"은 빼고 넣을 때마다 1부터 다시 센다 (필드 이름은 그대로)
    var relabel = function () {
      bzGrid.querySelectorAll('.bz-no').forEach(function (el, i) { el.textContent = '지원자 ' + (i + 1); });
    };
    var addBtn = document.getElementById('bz-add');
    addBtn.addEventListener('click', function () { addZone(null); });
    ['dragenter', 'dragover'].forEach(function (t) {
      addBtn.addEventListener(t, function (e) { e.preventDefault(); addBtn.classList.add('is-over'); });
    });
    addBtn.addEventListener('dragleave', function () { addBtn.classList.remove('is-over'); });
    addBtn.addEventListener('drop', function (e) {
      e.preventDefault();
      addBtn.classList.remove('is-over');
      var files = e.dataTransfer.files;
      // 비어 있는 칸부터 채우고, 모자라면 칸을 늘린다
      var empty = Array.prototype.filter.call(bzGrid.querySelectorAll('.bz input[type=file]'), function (i) { return !i.files.length; });
      Array.prototype.forEach.call(files, function (f) {
        var input = empty.shift();
        if (!input) { addZone(f); return; }
        var dt = new DataTransfer();
        dt.items.add(f);
        input.files = dt.files;
        showFiles(input.closest('.dropzone'));
      });
    });
    bzGrid.addEventListener('click', function (e) {
      var del = e.target.closest('.bz-del');
      if (!del) return;
      var zone = del.closest('.bz');
      if (bzGrid.querySelectorAll('.bz').length > 1) {
        zone.remove();
        relabel();
      } else {
        zone.querySelector('input[type=file]').value = '';
        showFiles(zone.querySelector('.dropzone'));
      }
    });
    document.getElementById('batch-form').addEventListener('submit', function (e) {
      var any = Array.prototype.some.call(bzGrid.querySelectorAll('input[type=file]'), function (i) { return i.files.length > 0; });
      if (!any) {
        e.preventDefault();
        e.stopImmediatePropagation();
        document.getElementById('busy-overlay').hidden = true;
        alert('이력서 파일을 올려 주세요.');
      }
    }, true);
  }

  // ── 다수 검사 진행 상황: 진행 중인 지원자가 있으면 목록을 몇 초마다 새로 그린다 ──
  var candList = document.getElementById('cand-list');
  if (candList && candList.querySelector('[data-in-progress="true"]')) {
    var current = candList.getAttribute('data-current');
    var currentStatus = candList.getAttribute('data-current-status');
    var base = candList.getAttribute('data-url').replace(/\/positions\/\d+\/candidates$/, '');
    var render = function (rows) {
      if (document.activeElement === candList) return; // 펼쳐 보는 중에는 그대로 둔다
      var placeholder = candList.querySelector('option[value=""]');
      candList.innerHTML = '';
      if (placeholder) candList.appendChild(placeholder);
      candList.closest('.side-select').querySelector('span').textContent = '지원자 ' + rows.length + '명';
      rows.forEach(function (row) {
        var tail = row.inProgress ? row.label
          : (row.state === 'RANKED' || row.state === 'EXCLUDED') ? row.score + ' / ' + row.max
          : row.state === 'STALE' ? '재검사 필요'
          : row.status === 'FAILED' ? '실패' : row.label;
        var o = document.createElement('option');
        o.value = base + '/results/' + row.id;
        o.setAttribute('data-tail', tail);
        o.textContent = row.name + ' · ' + tail;
        o.selected = String(row.id) === current;
        candList.appendChild(o);
      });
      candList.dispatchEvent(new Event('refit'));
    };
    var poll = function () {
      fetch(candList.getAttribute('data-url'), { headers: { 'X-Requested-With': 'fetch' } })
        .then(function (res) { return res.ok ? res.json() : Promise.reject(); })
        .then(function (rows) {
          render(rows);
          var mine = rows.filter(function (r) { return String(r.id) === current; })[0];
          // 보고 있는 지원자의 단계가 바뀌면 화면을 새로 연다
          if (mine && mine.status !== currentStatus) { location.reload(); return; }
          if (rows.some(function (r) { return r.inProgress; })) setTimeout(poll, 3000);
        })
        .catch(function () { setTimeout(poll, 6000); });
    };
    setTimeout(poll, 3000);
  }

  // ── 면접 기록: 바꾸면 바로 저장한다. 날짜나 합격·불합격을 고르면 "면접봤어요"도 체크된다 ──
  var iv = document.getElementById('iv-bar');
  if (iv) {
    var ivDone = document.getElementById('iv-done');
    var ivDate = document.getElementById('iv-date');
    var ivState = document.getElementById('iv-state');
    var ivButtons = iv.querySelectorAll('[data-decision]');
    var decision = function () {
      var on = iv.querySelector('[data-decision].is-on');
      return on ? on.getAttribute('data-decision') : '';
    };
    var show = function (d) {
      ivDone.checked = d.interviewed;
      ivDate.value = d.date;
      ivButtons.forEach(function (b) {
        var on = b.getAttribute('data-decision') === d.decision;
        b.classList.toggle('is-on', on);
        b.setAttribute('aria-pressed', on);
      });
      // 보고서 버튼은 합격일 때만
      var report = document.getElementById('iv-report');
      if (report) report.hidden = d.decision !== 'PASS';
    };
    var ivSave = function () {
      var before = { interviewed: ivDone.checked, date: ivDate.value, decision: decision() };
      ivState.textContent = '저장 중';
      ivState.classList.remove('err');
      fetch(iv.getAttribute('data-url'), {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'fetch' },
        body: JSON.stringify(before)
      }).then(function (res) {
        return res.ok ? res.json() : res.text().then(function (t) { throw new Error(t); });
      }).then(function (saved) {
        show(saved);
        ivState.textContent = '저장됨';
      }).catch(function (e) {
        ivState.textContent = e.message || '저장하지 못했습니다';
        ivState.classList.add('err');
      });
    };
    ivDone.addEventListener('change', function () {
      // 면접을 안 본 것으로 되돌리면 날짜와 결과도 지운다
      if (!ivDone.checked) show({ interviewed: false, date: '', decision: '' });
      ivSave();
    });
    ivDate.addEventListener('change', ivSave);
    ivButtons.forEach(function (b) {
      b.addEventListener('click', function () {
        var on = !b.classList.contains('is-on');
        ivButtons.forEach(function (x) { x.classList.remove('is-on'); x.setAttribute('aria-pressed', false); });
        b.classList.toggle('is-on', on);
        b.setAttribute('aria-pressed', on);
        ivSave();
      });
    });
  }

  // ── 목차: 화면에 없는 영역은 숨기고, 지금 보고 있는 영역을 표시한다 ──
  var toc = document.getElementById('toc');
  if (toc) {
    var links = [];
    toc.querySelectorAll('a').forEach(function (a) {
      var target = document.getElementById(a.getAttribute('href').slice(1));
      if (target) links.push({ a: a, target: target }); else a.hidden = true;
    });
    var mark = function () {
      var line = 140;
      var current = links[0];
      links.forEach(function (l) { if (l.target.getBoundingClientRect().top <= line) current = l; });
      // 맨 아래까지 내리면 마지막 항목
      if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2) current = links[links.length - 1];
      links.forEach(function (l) { l.a.classList.toggle('active', l === current); });
    };
    if (links.length) {
      window.addEventListener('scroll', mark, { passive: true });
      mark();
    }
  }

  // ── 답변 자동 저장 ──
  var pending = new Map();   // textarea -> timer
  var inflight = new Set();  // Promise
  var answered = document.getElementById('answered-count');

  function save(ta) {
    var row = ta.closest('.q-row');
    var state = row.querySelector('.save-state');
    state.textContent = '저장 중';
    state.classList.remove('err');
    var body = new URLSearchParams({ answer: ta.value });
    var p = fetch(ta.getAttribute('data-url'), {
      method: 'POST',
      headers: { 'X-Requested-With': 'fetch' },
      body: body
    }).then(function (res) {
      if (!res.ok) return res.text().then(function (t) { throw new Error(t || res.status); });
      state.textContent = '저장됨';
      row.classList.toggle('is-answered', ta.value.trim() !== '');
      // 답변을 적으면 서버도 체크해 둔다
      if (ta.value.trim() !== '') {
        row.classList.add('is-checked');
        row.classList.remove('is-missing');
        var no = row.querySelector('.js-q-check');
        if (no) no.setAttribute('aria-pressed', 'true');
      }
      if (answered) answered.textContent = document.querySelectorAll('.q-row.is-answered').length;
    }).catch(function (err) {
      state.textContent = '저장 실패';
      state.classList.add('err');
      state.title = err.message;
    });
    inflight.add(p);
    p.finally(function () { inflight.delete(p); });
    return p;
  }

  function flush() {
    pending.forEach(function (timer, ta) { clearTimeout(timer); save(ta); });
    pending.clear();
    return Promise.all(Array.from(inflight));
  }

  function autosize(ta) {
    ta.style.height = 'auto';
    ta.style.height = Math.max(ta.scrollHeight + 2, 40) + 'px';
  }

  function bindAnswer(ta) {
    autosize(ta);
    ta.addEventListener('input', function () {
      autosize(ta);
      ta.closest('.q-row').querySelector('.save-state').textContent = '';
      clearTimeout(pending.get(ta));
      pending.set(ta, setTimeout(function () { pending.delete(ta); save(ta); }, 700));
    });
    ta.addEventListener('blur', function () {
      if (pending.has(ta)) { clearTimeout(pending.get(ta)); pending.delete(ta); save(ta); }
    });
  }
  document.querySelectorAll('textarea.answer').forEach(bindAnswer);
  window.bindAnswer = bindAnswer;
  window.autosize = autosize;

  window.addEventListener('beforeunload', function (e) {
    if (pending.size > 0 || inflight.size > 0) {
      flush();
      e.preventDefault();
      e.returnValue = '';
    }
  });

})();

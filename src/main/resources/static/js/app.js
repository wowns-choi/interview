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
  });

  // ── 표의 행 전체 클릭 ──
  document.querySelectorAll('tr[data-href]').forEach(function (tr) {
    tr.addEventListener('click', function (e) {
      if (e.target.closest('a')) return;
      location.href = tr.getAttribute('data-href');
    });
  });

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
      if (candList.querySelector('.inline-form')) return; // 이름을 고치는 중에는 그대로 둔다
      candList.innerHTML = '';
      rows.forEach(function (row) {
        var li = document.createElement('li');
        li.className = 'side-pos';
        var a = document.createElement('a');
        a.className = 'side-item side-cand' + (String(row.id) === current ? ' active' : '');
        a.href = base + '/results/' + row.id;
        var name = document.createElement('strong');
        name.textContent = row.name;
        a.appendChild(name);
        var tail = document.createElement('span');
        if (!row.inProgress && row.state === 'RANKED') {
          tail.className = 'side-score';
          tail.textContent = row.score;
          var small = document.createElement('small');
          small.textContent = ' / ' + row.max;
          tail.appendChild(small);
        } else if (!row.inProgress && row.state === 'STALE') {
          tail.className = 'side-state is-stale';
          tail.textContent = '재검사 필요';
        } else {
          tail.className = 'side-state' + (row.inProgress ? ' is-progress' : row.status === 'FAILED' ? ' is-failed' : '');
          tail.textContent = row.label;
        }
        a.appendChild(tail);
        li.appendChild(a);
        if (!row.inProgress) {
          var rn = document.createElement('button');
          rn.type = 'button';
          rn.className = 'icon-btn side-rename';
          rn.title = '이름 바꾸기';
          rn.innerHTML = '<svg class="i"><use href="#i-pencil"/></svg>';
          li.appendChild(rn);
        }
        candList.appendChild(li);
      });
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

  window.addEventListener('beforeunload', function (e) {
    if (pending.size > 0 || inflight.size > 0) {
      flush();
      e.preventDefault();
      e.returnValue = '';
    }
  });

  // PDF: 화면 그대로 인쇄한다. 저장 안 된 답변을 먼저 저장하고, 제목을 파일 이름으로 쓴다
  var pdf = document.getElementById('pdf-btn');
  if (pdf) {
    pdf.addEventListener('click', function () {
      flush().then(function () {
        var title = document.title;
        document.title = pdf.getAttribute('data-filename') || title;
        document.querySelectorAll('textarea.answer').forEach(autosize);
        window.print();
        document.title = title;
      });
    });
  }

})();

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
      form.querySelectorAll('button').forEach(function (b) { b.disabled = true; });
    }
  });

  // ── 표의 행 전체 클릭 ──
  document.querySelectorAll('tr[data-href]').forEach(function (tr) {
    tr.addEventListener('click', function (e) {
      if (e.target.closest('a')) return;
      location.href = tr.getAttribute('data-href');
    });
  });

  // ── 이력서 드롭존 ──
  var dz = document.getElementById('dropzone');
  if (dz) {
    var input = dz.querySelector('input[type=file]');
    var label = document.getElementById('dz-text');
    var show = function () {
      var f = input.files && input.files[0];
      dz.classList.toggle('has-file', !!f);
      label.textContent = f ? f.name : '이력서 PDF를 끌어다 놓거나 클릭';
    };
    input.addEventListener('change', show);
    ['dragenter', 'dragover'].forEach(function (t) {
      dz.addEventListener(t, function () { dz.classList.add('is-over'); });
    });
    ['dragleave', 'drop'].forEach(function (t) {
      dz.addEventListener(t, function () { dz.classList.remove('is-over'); });
    });
    show();
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

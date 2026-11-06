(function () {
  // ── 상단 탭: 눌러서 옮길 때만 밑줄 전환을 켠다 (header.jspf의 pageswap·pagereveal이 본다) ──
  document.addEventListener('click', function (e) {
    var tab = e.target.closest('.tabs .tab');
    if (!tab || e.ctrlKey || e.metaKey || e.shiftKey || e.button !== 0) return;
    try { sessionStorage.setItem('tabNav', '1'); } catch (err) { /* 전환만 빠진다 */ }
  });

  // ── 폼 제출: data-confirm 확인, data-busy 진행 중 표시 ──
  document.addEventListener('submit', function (e) {
    var form = e.target;
    var msg = form.getAttribute('data-confirm');
    if (msg && !window.confirm(msg)) {
      e.preventDefault();
      return;
    }
    // 면접 내용 정리: 받는 대로 항목을 보여 준다 (extract.js의 streamRecord)
    if (form.hasAttribute('data-stream') && window.streamRecord) {
      e.preventDefault();
      window.streamRecord(form);
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
    var partial = form.getAttribute('data-partial');
    if (partial && !e.defaultPrevented) {
      e.preventDefault();
      var body;
      try { body = new FormData(form, e.submitter); } catch (err) {
        body = new FormData(form);
        if (e.submitter && e.submitter.name) body.append(e.submitter.name, e.submitter.value);
      }
      partialLoad(fetch(form.action, { method: 'POST', body: body }), partial, form);
    }
  });

  // ── 일부만 다시 그리기: 서버가 돌려준 같은 페이지에서 그 영역만 바꿔 끼운다 (보던 위치 그대로) ──
  function partialLoad(request, selectors, form) {
    var overlay = document.getElementById('busy-overlay');
    var done = function () {
      overlay.hidden = true;
      if (form) Array.prototype.forEach.call(form.elements, function (el) { if (el.tagName === 'BUTTON') el.disabled = false; });
    };
    return request.then(function (res) {
      return res.text().then(function (html) { return { url: res.url, html: html }; });
    }).then(function (page) {
      var url = new URL(page.url);
      if (url.pathname !== location.pathname) { location.href = page.url; return; }
      var doc = new DOMParser().parseFromString(page.html, 'text/html');
      var pairs = selectors.split(',').map(function (s) {
        return { old: document.querySelector(s.trim()), neu: doc.querySelector(s.trim()) };
      });
      if (pairs.some(function (p) { return !p.old || !p.neu; })) { location.href = page.url; return; }
      pairs.forEach(function (p) {
        Array.prototype.slice.call(p.old.attributes).forEach(function (a) { p.old.removeAttribute(a.name); });
        Array.prototype.forEach.call(p.neu.attributes, function (a) { p.old.setAttribute(a.name, a.value); });
        p.old.replaceChildren.apply(p.old, Array.prototype.slice.call(p.neu.childNodes));
        p.old.querySelectorAll('textarea.answer').forEach(bindAnswer);
      });
      done();
      var err = doc.querySelector('.flash-error');
      var ok = doc.querySelector('.flash-ok');
      if (err) showToast(err.textContent.trim(), true);
      else if (ok) showToast(ok.textContent.trim(), false);
      document.dispatchEvent(new CustomEvent('partial', { detail: pairs.map(function (p) { return p.old; }) }));
    }).catch(function () {
      done();
      showToast('요청을 처리하지 못했습니다.', true);
    });
  }
  window.refreshPartial = function (selectors) {
    return partialLoad(fetch(location.pathname, { headers: { Accept: 'text/html' } }), selectors);
  };

  // 지원자 고르기 항목: 이름 · 등록 시각. 이름과 시각이 같으면(한 번에 여러 명) 먼저 올린 순서로 (2), (3)을 붙인다
  window.candLabel = function (select) {
    // 닫힌 칸에도 이름·날짜·시각을 나눠 보이도록 고른 항목을 그대로 비춰 주는 칸을 둔다 (꾸밀 수 있는 select에서만, 사용자 2026-10-28)
    if (window.CSS && CSS.supports('appearance', 'base-select') && !select.querySelector('selectedcontent')) {
      var shown = document.createElement('button');
      shown.appendChild(document.createElement('selectedcontent'));
      select.insertBefore(shown, select.firstChild);
    }
    var seen = {};
    var opts = Array.prototype.filter.call(select.options, function (o) { return o.hasAttribute('data-name'); });
    for (var i = opts.length - 1; i >= 0; i--) { // 목록은 최근 것이 위
      var o = opts[i];
      var at = o.getAttribute('data-at');
      var label = o.getAttribute('data-name') + ' · ' + at;
      seen[label] = (seen[label] || 0) + 1;
      // 이름 · 날짜 · 시각을 색으로 나눈다 (새 목록 스타일에서만 보인다, 나머지는 글자 그대로)
      var cut = at.indexOf('일 ') + 1;
      var line = document.createElement('span');
      line.className = 'cn';
      [['cn-name', o.getAttribute('data-name')], [null, ' '], ['cn-day', cut > 0 ? at.slice(0, cut) : at],
        [null, ' '], ['cn-time', (cut > 0 ? at.slice(cut + 1) : '') + (seen[label] > 1 ? ' (' + seen[label] + ')' : '')]]
        .forEach(function (p) {
          if (!p[0]) { line.appendChild(document.createTextNode(p[1])); return; }
          var s = document.createElement('span');
          s.className = p[0];
          s.textContent = p[1];
          line.appendChild(s);
        });
      o.textContent = '';
      o.appendChild(line);
    }
    // 닫힌 칸은 항목을 바꿀 때만 다시 비추므로, 글을 고친 뒤에는 직접 맞춘다
    var sc = select.querySelector('selectedcontent');
    var cur = select.options[select.selectedIndex];
    if (sc && cur) sc.replaceChildren.apply(sc, Array.prototype.map.call(cur.childNodes, function (n) { return n.cloneNode(true); }));
  };
  document.querySelectorAll('#cand-list, select[data-cand-copy]').forEach(window.candLabel);

  var toastTimer = null;
  window.showToast = function (msg, isError, ms) { showToast(msg, isError, ms); };
  function showToast(msg, isError, ms) {
    if (!msg) return;
    var el = document.getElementById('toast');
    if (!el) {
      el = document.createElement('div');
      el.className = 'toast';
      el.id = 'toast';
      el.setAttribute('role', 'status');
      document.body.appendChild(el);
    }
    el.textContent = msg;
    el.classList.toggle('is-error', !!isError);
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.hidden = true; }, ms || (isError ? 4000 : 1800));
  }

  // ── 포지션 고르기: 고르면 그 주소로 ──
  document.querySelectorAll('select[data-nav]').forEach(function (sel) {
    sel.addEventListener('change', function () { location.href = sel.value; });
    if (!sel.closest('.pick-select')) return; // 사이드바의 지원자 고르기는 칸 폭 그대로
    // 고른 이름 길이에 맞춘다 (select 폭은 보통 가장 긴 option 기준이라 제목 크기 글자가 잘린다)
    var fit = function () {
      // 닫힌 칸이 고른 항목을 그대로 비추면(꾸밀 수 있는 select) 글 크기가 제각각이라 내용에 맞춘다
      if (sel.querySelector('selectedcontent')) { sel.style.width = 'max-content'; return; }
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

  // 가린 이력서: [이름]·[A사] 같은 가린 자리를 꼬리표로 (처음 열 때 한 번)
  var MASK_LABEL = /\[(?:이름|전화번호|이메일|주민번호|링크|생년월일|주소|개인정보|회사|[A-Z]사|연봉|경력|학교|학력|교육|자격증)\]/g;
  function markMasked(box) {
    if (!box || box.hasAttribute('data-marked')) return;
    box.setAttribute('data-marked', '');
    var text = box.textContent, last = 0, m;
    box.textContent = '';
    MASK_LABEL.lastIndex = 0;
    while ((m = MASK_LABEL.exec(text))) {
      box.appendChild(document.createTextNode(text.slice(last, m.index)));
      var tag = document.createElement('span');
      tag.className = 'mask-tag';
      tag.textContent = m[0]; // 대괄호째 둬야 복사해도 [이름]으로 남는다
      box.appendChild(tag);
      last = m.index + m[0].length;
    }
    box.appendChild(document.createTextNode(text.slice(last)));
  }

  // ── 지원자 화면 단계: 면접 전 · 면접 후 · 보고서. 한 화면 안에서 바꾸고 주소 끝(#after)에 남긴다 (사용자 2026-10-28) ──
  var stageNav = document.getElementById('stage-nav');
  if (stageNav) {
    var STAGES = ['before', 'after', 'report'];
    // 예전 주소의 #record(녹음본)는 면접 후, #extract·#questions 같은 질문지 자리는 면접 전
    var stageOf = function (hash) {
      var h = (hash || '').replace('#', '');
      if (STAGES.indexOf(h) >= 0) return h;
      return h === 'record' ? 'after' : 'before';
    };
    var showStage = function (name, scroll) {
      stageNav.querySelectorAll('[data-stage]').forEach(function (b) {
        var on = b.getAttribute('data-stage') === name;
        b.classList.toggle('is-on', on);
        b.setAttribute('aria-current', on ? 'step' : 'false');
      });
      document.querySelectorAll('.stage-pane').forEach(function (p) {
        var show = p.getAttribute('data-pane') === name;
        // 단계 버튼으로 바꿀 때만 새 단계가 스르륵 떠오른다 (사용자 2026-10-28). 처음 열 때는 바로
        if (show && p.hidden && scroll) {
          p.classList.remove('is-entering');
          void p.offsetWidth;
          p.classList.add('is-entering');
          p.addEventListener('animationend', function end(e) {
            if (e.target !== p) return; // 안쪽 요소의 애니메이션 끝은 넘긴다
            p.classList.remove('is-entering');
            p.removeEventListener('animationend', end);
          });
        }
        p.hidden = !show;
      });
      document.querySelectorAll('[data-only]').forEach(function (el) {
        el.hidden = el.getAttribute('data-only').split(' ').indexOf(name) < 0;
      });
      document.documentElement.setAttribute('data-stage', name);
      var tocBox = document.getElementById('toc');
      if (tocBox) tocBox.classList.toggle('is-open', name === 'before');
      // 숨어 있을 때 잰 칸 높이는 0이라, 보이고 나서 다시 잰다 (보고서 칸은 report.js가 stageshow를 받아 잰다)
      setTimeout(function () {
        document.querySelectorAll('.stage-pane:not([hidden]) textarea.answer').forEach(function (t) { if (window.autosize) window.autosize(t); });
        document.dispatchEvent(new CustomEvent('stageshow', { detail: name }));
      }, 0);
      if (scroll) window.scrollTo(0, 0);
    };
    stageNav.addEventListener('click', function (e) {
      // html에도 data-stage가 있어서 버튼으로 좁힌다 (책갈피 링크를 누르면 html까지 올라가 맨 위로 튀었다)
      var b = e.target.closest('button[data-stage]');
      if (!b) return;
      var name = b.getAttribute('data-stage');
      history.replaceState(null, '', location.pathname + location.search + '#' + name);
      showStage(name, true);
    });
    // 사이드바 맨 아래로 (필수질문 체크·면접 후 버튼이 있는 끝)
    var toBottom = document.getElementById('to-bottom');
    if (toBottom) toBottom.addEventListener('click', function () {
      window.scrollTo({ top: document.documentElement.scrollHeight, behavior: 'smooth' });
    });
    // 면접 질문지 밑 「면접 후」 같은 단계 넘김 버튼: 사이드바 단계 버튼을 누른 것과 같다
    document.addEventListener('click', function (e) {
      var go = e.target.closest('[data-go-stage]');
      if (!go) return;
      var b = stageNav.querySelector('[data-stage="' + go.getAttribute('data-go-stage') + '"]');
      if (b) b.click();
    });
    var first = location.hash;
    showStage(stageOf(first), false);
    // 처음 그릴 때는 바로 두고, 그 뒤 단계를 바꿀 때만 책갈피가 스르륵 열리고 닫힌다
    requestAnimationFrame(function () { requestAnimationFrame(function () { stageNav.classList.add('is-ready'); }); });
    // 질문지 안의 자리(#extract 등)로 왔으면 그 자리로
    if (first && STAGES.indexOf(first.slice(1)) < 0 && first !== '#record') {
      var spot = document.getElementById(first.slice(1));
      if (spot) spot.scrollIntoView();
    }
    window.addEventListener('hashchange', function () { showStage(stageOf(location.hash), false); });
  }

  // ── 첨부 파일 목록: 바깥을 누르거나 항목을 고르면 닫는다 ──
  document.addEventListener('click', function (e) {
    document.querySelectorAll('details.att[open]').forEach(function (d) {
      if (!d.contains(e.target) || e.target.closest('.att-item')) d.open = false;
    });
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') document.querySelectorAll('details.att[open]').forEach(function (d) { d.open = false; });
  });

  // ── 자세히 보기 창: data-dialog(카드 전체)로 열고, 닫기 버튼이나 바깥을 누르면 닫는다 ──
  document.addEventListener('click', function (e) {
    var open = e.target.closest('[data-dialog]');
    if (open) {
      var dlg = document.getElementById(open.getAttribute('data-dialog'));
      if (dlg && dlg.showModal) {
        markMasked(dlg.querySelector('[data-masked]'));
        dlg.showModal();
      }
      return;
    }
    if (e.target.closest('dialog [data-close]')) { e.target.closest('dialog').close(); return; }
    // 바깥(배경) 누름. 떠 있는 창(AI와 대화)은 모달이 아니라 여백·모서리를 눌러도 닫지 않는다
    if (e.target.tagName === 'DIALOG' && e.target.matches(':modal')) e.target.close();
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && e.target.matches('div[data-dialog]')) e.target.click();
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
      // 불이 켜지는 걸 보여 주고 새로 그린다
      var waved = new Promise(function (ok) { setTimeout(ok, 450); });
      return fetch(rkBase + path, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'fetch' },
        body: JSON.stringify(body)
      }).then(function (res) {
        if (!res.ok) return res.text().then(function (t) { throw new Error(t || '저장하지 못했습니다.'); });
        return waved.then(function () { location.reload(); });
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
        closeDetails();
        var open = fold.classList.toggle('is-open');
        document.querySelectorAll('.rk-row.is-' + fold.getAttribute('data-rk-fold')).forEach(function (tr) { tr.hidden = !open; });
      });
    });
    // 다시 검사 중인 지원자가 있으면 잠시 뒤 새로 그린다 (끝나면 멈춘다)
    var rkPoll = document.querySelector('.rk-stage[data-poll]');
    if (rkPoll) setTimeout(function () { location.reload(); }, +rkPoll.getAttribute('data-poll'));
    // 줄을 누르면 그 지원자가 검사받은 평가 기준을 줄 아래에 펼친다. 지원자 화면으로는 끝의 「자세히」로 간다 (사용자 2026-10-28)
    var closeDetails = function () {
      document.querySelectorAll('.rk-detail').forEach(function (d) { d.classList.remove('is-open'); d.hidden = true; });
      document.querySelectorAll('.rk-row.is-open').forEach(function (r) { r.classList.remove('is-open'); });
    };
    document.querySelectorAll('.rk-row').forEach(function (tr) {
      var d = tr.nextElementSibling;
      if (!d || !d.classList.contains('rk-detail')) return;
      tr.classList.add('can-open');
      tr.addEventListener('click', function (e) {
        if (e.target.closest('a')) return;
        var open = !d.classList.contains('is-open');
        tr.classList.toggle('is-open', open);
        if (open) {
          d.hidden = false;
          void d.offsetHeight; // 접힌 모습을 한 번 그린 뒤 펼쳐야 스르륵 열린다
          d.classList.add('is-open');
        } else {
          d.classList.remove('is-open');
        }
      });
      // 다 접히고 나서야 줄을 숨긴다 (그 사이 다시 펼쳤으면 그대로)
      d.addEventListener('transitionend', function (e) {
        if (e.propertyName === 'grid-template-rows' && !d.classList.contains('is-open')) d.hidden = true;
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
          if (n.classList.contains('rk-row') && !n.classList.contains('is-filtered')) left = true;
        }
        g.classList.toggle('is-filtered', !left);
      });
      closeDetails();
      rkPage = 0;
      pageRanked();
    };
    // 순위 줄은 RK_PAGE명씩 페이지로 나눈다 (거른 뒤에 센다, 거르면 1페이지로)
    var RK_PAGE = 10, rkPage = 0, rkPager = null;
    var pageRanked = function () {
      var all = document.querySelectorAll('.rk-row.is-RANKED');
      var rows = Array.prototype.filter.call(all, function (tr) { return !tr.classList.contains('is-filtered'); });
      var pages = Math.ceil(rows.length / RK_PAGE);
      if (rkPage >= pages) rkPage = Math.max(0, pages - 1);
      all.forEach(function (tr) { tr.classList.remove('is-paged'); });
      rows.forEach(function (tr, i) {
        if (Math.floor(i / RK_PAGE) !== rkPage) tr.classList.add('is-paged');
      });
      if (pages <= 1) { if (rkPager) rkPager.hidden = true; return; }
      if (!rkPager) {
        rkPager = document.createElement('tr');
        rkPager.className = 'rk-pager';
        var td = document.createElement('td');
        td.colSpan = document.querySelector('.rk thead tr').children.length;
        rkPager.appendChild(td);
        td.addEventListener('click', function (e) {
          var b = e.target.closest('button[data-page]');
          if (!b || b.disabled) return;
          closeDetails();
          rkPage = +b.getAttribute('data-page');
          pageRanked();
          document.querySelector('.rk').scrollIntoView({ block: 'start' });
        });
      }
      var td2 = rkPager.firstChild;
      td2.textContent = '';
      var add = function (label, page, cls, current) {
        var b = document.createElement('button');
        b.type = 'button';
        b.className = 'rk-page' + (cls ? ' ' + cls : '') + (current ? ' is-current' : '');
        b.textContent = label;
        b.setAttribute('data-page', page);
        if (current) b.setAttribute('aria-current', 'page');
        if (page < 0 || page >= pages) b.disabled = true;
        td2.appendChild(b);
      };
      add('‹', rkPage - 1, 'rk-page-step');
      // 페이지가 많으면 처음·끝·지금 앞뒤 둘만, 사이는 …
      var gap = false;
      for (var p = 0; p < pages; p++) {
        if (pages > 9 && p > 0 && p < pages - 1 && Math.abs(p - rkPage) > 2) {
          if (!gap) { var s = document.createElement('span'); s.className = 'rk-page-gap'; s.textContent = '…'; td2.appendChild(s); }
          gap = true;
          continue;
        }
        gap = false;
        add(String(p + 1), p, '', p === rkPage);
      }
      add('›', rkPage + 1, 'rk-page-step');
      rkPager.hidden = false;
      var last = rows[rows.length - 1];
      if (last.nextElementSibling && last.nextElementSibling.classList.contains('rk-detail')) last = last.nextElementSibling;
      last.parentNode.insertBefore(rkPager, last.nextSibling);
    };
    try {
      var saved = JSON.parse(localStorage.getItem('rkFilters') || '{}');
      filters.forEach(function (f) { f.checked = !!saved[f.getAttribute('data-rk-filter')]; });
    } catch (e) { /* 저장소를 못 쓰면 끈 채로 */ }
    filters.forEach(function (f) { f.addEventListener('change', applyFilters); });
    applyFilters();
    // 전구 깜빡임은 방금 켠 칩에만
    rk.querySelectorAll('.fchip input').forEach(function (box) {
      box.addEventListener('change', function () {
        var chip = box.closest('.fchip');
        chip.classList.remove('is-flick');
        if (box.checked) { void chip.offsetWidth; chip.classList.add('is-flick'); }
      });
    });
  }

  // ── 평가 기준 추출 왼쪽 포지션 목록: 끝까지 내리면 다음 묶음을 이어 붙인다 ──
  var sideList = document.getElementById('side-list');
  if (sideList && sideList.getAttribute('data-next')) {
    var sideBusy = false;
    var sidePage = +sideList.getAttribute('data-page');
    var sideMore = function () {
      var next = sideList.getAttribute('data-next');
      if (sideBusy || !next || sideList.scrollTop + sideList.clientHeight < sideList.scrollHeight - 80) return;
      sideBusy = true;
      fetch(sideList.getAttribute('data-more') + '?offset=' + next, { headers: { 'X-Requested-With': 'fetch' } })
        .then(function (res) { if (!res.ok) throw new Error(); return res.text(); })
        .then(function (html) {
          var box = document.createElement('ul');
          box.innerHTML = html;
          var items = box.querySelectorAll('li.side-pos');
          items.forEach(function (li) { sideList.appendChild(li); });
          if (items.length < sidePage) sideList.removeAttribute('data-next');
          else sideList.setAttribute('data-next', +next + items.length);
        })
        .catch(function () { /* 다음에 다시 내리면 또 시도 */ })
        .then(function () { sideBusy = false; });
    };
    sideList.addEventListener('scroll', sideMore, { passive: true });
  }

  // ── 이력서 드롭존 (지원자마다 하나): 고르거나 놓을 때마다 쌓이고, 파일마다 뺄 수 있다 ──
  var DZ_TEXT = '이력서 또는 포트폴리오를 넣어주세요';
  function setFiles(dz, files) {
    dz._files = files;
    var dt = new DataTransfer();
    files.forEach(function (f) { dt.items.add(f); });
    dz.querySelector('input[type=file]').files = dt.files;
    showFiles(dz);
  }
  function showFiles(dz) {
    var files = dz._files || [];
    dz.classList.toggle('has-file', files.length > 0);
    dz.dispatchEvent(new Event('dzchange', { bubbles: true }));
    dz.querySelector('.dz-text').textContent = files.length ? '파일 더 넣기' : DZ_TEXT;
    var list = dz.querySelector('.dz-files');
    if (!list) {
      list = document.createElement('ul');
      list.className = 'dz-files';
      dz.appendChild(list);
    }
    list.replaceChildren.apply(list, files.map(function (f, i) {
      var li = document.createElement('li');
      var name = document.createElement('span');
      name.textContent = f.name;
      var x = document.createElement('button');
      x.type = 'button';
      x.className = 'dz-x';
      x.title = '빼기';
      x.textContent = '×';
      x.addEventListener('click', function (e) {
        e.preventDefault();
        e.stopPropagation();
        setFiles(dz, files.filter(function (g, j) { return j !== i; }));
      });
      li.append(name, x);
      return li;
    }));
    list.hidden = !files.length;
  }
  function bindDropzone(dz) {
    var input = dz.querySelector('input[type=file]');
    dz._files = [];
    input.addEventListener('change', function () {
      var all = (dz._files || []).slice();
      Array.prototype.forEach.call(input.files, function (f) {
        var dup = all.some(function (g) { return g.name === f.name && g.size === f.size && g.lastModified === f.lastModified; });
        if (!dup) all.push(f);
      });
      setFiles(dz, all);
    });
    ['dragenter', 'dragover'].forEach(function (t) {
      dz.addEventListener(t, function () { dz.classList.add('is-over'); });
    });
    ['dragleave', 'drop'].forEach(function (t) {
      dz.addEventListener(t, function () { dz.classList.remove('is-over'); });
    });
    showFiles(dz);
  }
  document.querySelectorAll('.dropzone').forEach(bindDropzone);

  // ── 포지션 적합도 검사: 지원자 칸 추가·빼기 (파일은 칸에만 놓는다) ──
  var bzGrid = document.getElementById('bz-grid');
  if (bzGrid) {
    var bzNext = bzGrid.querySelectorAll('.bz').length + 1;
    var addZone = function () {
      var zone = bzGrid.querySelector('.bz').cloneNode(true);
      var dz = zone.querySelector('.dropzone');
      var input = dz.querySelector('input[type=file]');
      input.value = '';
      input.name = 'applicant-' + bzNext;
      zone.querySelector('.bz-no').textContent = '지원자 ' + bzNext;
      bzNext++;
      bzGrid.insertBefore(zone, document.getElementById('bz-add'));
      bindDropzone(dz);
      relabel();
      return zone;
    };
    // 화면의 "지원자 n"은 빼고 넣을 때마다 1부터 다시 센다 (필드 이름은 그대로)
    var relabel = function () {
      bzGrid.querySelectorAll('.bz-no').forEach(function (el, i) { el.textContent = '지원자 ' + (i + 1); });
    };
    // 개인정보 엄격 보호 모드에서는 한 명씩이라 추가 버튼이 없다
    var addBtn = document.getElementById('bz-add');
    if (addBtn) {
      addBtn.addEventListener('click', function () { addZone(); });
      // 버튼에 파일을 놓아도 받지 않는다 (브라우저가 파일을 여는 것만 막는다)
      addBtn.addEventListener('dragover', function (e) { e.preventDefault(); e.dataTransfer.dropEffect = 'none'; });
      addBtn.addEventListener('drop', function (e) { e.preventDefault(); });
    }
    // 검사 버튼: 파일을 넣은 지원자가 있어야 켜지고, 몇 명인지 보인다
    var goBtn = document.getElementById('bz-go');
    var goCount = function () {
      if (!goBtn) return;
      var n = Array.prototype.filter.call(bzGrid.querySelectorAll('input[type=file]'), function (i) { return i.files.length > 0; }).length;
      goBtn.disabled = n === 0;
      goBtn.querySelector('.bz-go-label').textContent = (n > 1 ? '지원자 ' + n + '명 ' : '') + goBtn.getAttribute('data-label');
    };
    bzGrid.addEventListener('dzchange', goCount);
    goCount();
    bzGrid.addEventListener('click', function (e) {
      var del = e.target.closest('.bz-del');
      if (!del) return;
      var zone = del.closest('.bz');
      if (bzGrid.querySelectorAll('.bz').length > 1) {
        zone.remove();
        relabel();
        goCount();
      } else {
        setFiles(zone.querySelector('.dropzone'), []);
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
    // 위쪽 고정 줄에도 같은 지원자 고르기가 있다
    var lists = [candList].concat(Array.prototype.slice.call(document.querySelectorAll('select[data-cand-copy]')));
    var render = function (rows) {
      lists.forEach(function (list) { renderList(list, rows); });
      var busy = rows.filter(function (r) { return r.inProgress; }).length;
      var head = candList.closest('label').querySelector('span');
      head.firstChild.nodeValue = '지원자 ' + rows.length + '명';
      var em = head.querySelector('.pick-busy');
      em.hidden = !busy;
      em.textContent = ' · ' + busy + '명 진행 중';
    };
    var renderList = function (candList, rows) {
      if (document.activeElement === candList) return; // 펼쳐 보는 중에는 그대로 둔다
      var placeholder = candList.querySelector('option[value=""]');
      candList.innerHTML = '';
      if (placeholder) candList.appendChild(placeholder);
      rows.forEach(function (row) {
        var tail = row.inProgress ? row.label
          : (row.state === 'RANKED' || row.state === 'EXCLUDED') ? row.score + ' / ' + row.max
          : row.state === 'STALE' ? '재검사 필요'
          : row.status === 'FAILED' ? '실패' : row.label;
        var o = document.createElement('option');
        o.value = base + '/results/' + row.id;
        o.setAttribute('data-tail', tail);
        if (row.inProgress || row.status === 'FAILED' || row.status === 'UPLOADED') {
          o.setAttribute('data-mark', row.status === 'FAILED' ? '실패' : row.label);
          o.setAttribute('data-mark-kind', row.inProgress ? 'progress' : row.status === 'UPLOADED' ? 'wait' : 'failed');
        }
        o.setAttribute('data-name', row.name);
        o.setAttribute('data-at', row.at);
        o.selected = String(row.id) === current;
        candList.appendChild(o);
      });
      window.candLabel(candList);
      candList.dispatchEvent(new Event('refit'));
    };
    var poll = function () {
      fetch(candList.getAttribute('data-url'), { headers: { 'X-Requested-With': 'fetch' } })
        .then(function (res) { return res.ok ? res.json() : Promise.reject(); })
        .then(function (rows) {
          render(rows);
          var mine = rows.filter(function (r) { return String(r.id) === current; })[0];
          // 보고 있는 지원자의 단계가 바뀌면 화면을 새로 연다
          if (mine && mine.status !== currentStatus) {
            // 검사가 끝나 결과가 처음 나오면 다시 연 화면에서 결과를 차례로 보여 준다
            if (!mine.inProgress && mine.status !== 'FAILED') {
              try { sessionStorage.setItem('reveal', current); } catch (err) { /* 연출만 빠진다 */ }
            }
            location.reload();
            return;
          }
          if (rows.some(function (r) { return r.inProgress; })) setTimeout(poll, 3000);
        })
        .catch(function () { setTimeout(poll, 6000); });
    };
    setTimeout(poll, 3000);
  }

  // ── 검사 결과가 처음 나올 때의 연출 (다시 검사 직후, 또는 진행 중이던 검사가 끝나 화면을 다시 열었을 때) ──
  var detail = document.querySelector('.detail');
  if (detail) {
    var revealId = null;
    try { revealId = sessionStorage.getItem('reveal'); sessionStorage.removeItem('reveal'); } catch (err) { /* 무시 */ }
    var shown = candList && revealId && revealId === candList.getAttribute('data-current');
    var root = document.documentElement;
    var reveal = shown || detail.hasAttribute('data-reveal');
    if (!reveal) root.classList.remove('revealing');
    if (reveal) {
      // 단계로 나뉘었으면 면접 전 영역의 카드들이 차례로 올라온다
      var pane = detail.querySelector('.stage-pane[data-pane="before"]') || detail;
      Array.prototype.forEach.call(pane.children, function (el, i) { el.style.setProperty('--i', i); });
      detail.querySelectorAll('.ev').forEach(function (el, j) { el.style.setProperty('--j', j); });
      var profile = detail.querySelector('.profile');
      if (profile) profile.classList.add('is-stamping');
      detail.classList.add('reveal');
      root.classList.remove('revealing'); // 같은 순간에 풀어야 카드들이 처음 상태(투명)에서 시작한다
      // 점수는 0부터 0.5씩 올라간다
      var pill = detail.querySelector('.score-pill');
      var num = pill && pill.firstChild && pill.firstChild.nodeType === 3 ? pill.firstChild : null;
      var target = num ? parseFloat(num.nodeValue) : NaN;
      if (!isNaN(target) && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        var start = null;
        var duration = 1300;
        var tick = function (t) {
          if (start === null) start = t;
          var p = Math.min(1, (t - start) / duration);
          var v = Math.round(target * (1 - Math.pow(1 - p, 3)) * 2) / 2;
          num.nodeValue = String(v);
          if (p < 1) requestAnimationFrame(tick); else pill.classList.add('is-done');
        };
        num.nodeValue = '0';
        setTimeout(function () { requestAnimationFrame(tick); }, 250);
      }
    }
  }

  // ── 면접 기록: 바꾸면 바로 저장한다. 면접 본 날짜가 있으면(또는 합격·불합격을 고르면) 면접 본 것이다.
  //    날짜 칸은 면접 질문지 제목 줄에 있고 질문지가 부분 갱신되면 새로 그려지므로 이벤트는 문서에서 받는다 ──
  var iv = document.getElementById('iv-bar');
  if (iv) {
    // 도장은 면접 후와 면접 전 맨 위 두 곳에 있다. 어느 쪽을 찍어도 둘 다 맞춘다 (사용자 2026-10-28)
    var ivButtons = document.querySelectorAll('.iv-stamp[data-decision]');
    var dateValue = function () {
      var el = document.getElementById('iv-date');
      return el ? el.value : (iv.getAttribute('data-date') || '');
    };
    // 날짜 없이 "면접봤어요"만 체크해 둔 예전 기록은 날짜를 고치기 전까지 그대로 둔다
    var legacy = iv.getAttribute('data-interviewed') === 'true' && !dateValue();
    var decision = function () {
      var on = iv.querySelector('.iv-stamp.is-on');
      return on ? on.getAttribute('data-decision') : '';
    };
    var show = function (d) {
      iv.setAttribute('data-date', d.date);
      var el = document.getElementById('iv-date');
      if (el) el.value = d.date;
      ivButtons.forEach(function (b) {
        var on = b.getAttribute('data-decision') === d.decision;
        b.classList.toggle('is-on', on);
        b.setAttribute('aria-pressed', on);
      });
    };
    var ivSave = function (state) {
      var before = { interviewed: legacy || !!dateValue() || !!decision(), date: dateValue(), decision: decision() };
      state.textContent = '저장 중';
      state.classList.remove('err');
      fetch(iv.getAttribute('data-url'), {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'fetch' },
        body: JSON.stringify(before)
      }).then(function (res) {
        return res.ok ? res.json() : res.text().then(function (t) { throw new Error(t); });
      }).then(function (saved) {
        show(saved);
        state.textContent = '저장됨';
        // 같은 화면 보고서 단계의 합격 여부도 맞춘다
        document.dispatchEvent(new CustomEvent('ivdecision', { detail: { decision: saved.decision, from: 'stamp' } }));
      }).catch(function (e) {
        state.textContent = e.message || '저장하지 못했습니다';
        state.classList.add('err');
      });
    };
    document.addEventListener('change', function (e) {
      if (e.target.id !== 'iv-date') return;
      e.target.classList.remove('is-missing');
      legacy = false;
      ivSave(document.getElementById('iv-date-state'));
    });
    document.addEventListener('ivdecision', function (e) {
      if (e.detail.from !== 'stamp') show({ date: dateValue(), decision: e.detail.decision });
    });
    ivButtons.forEach(function (b) {
      b.addEventListener('click', function () {
        var on = !b.classList.contains('is-on');
        var d = on ? b.getAttribute('data-decision') : '';
        ivButtons.forEach(function (x) {
          var xOn = x.getAttribute('data-decision') === d;
          x.classList.toggle('is-on', xOn);
          x.setAttribute('aria-pressed', xOn);
          x.classList.remove('is-stamped');
        });
        // 찍을 때만 도장 내려찍는 움직임
        b.classList.remove('is-stamped');
        if (on) { void b.offsetWidth; b.classList.add('is-stamped'); }
        ivSave(b.closest('.iv-bar').querySelector('.save-state'));
      });
    });
  }

  // ── 포지션·평가 기준 고정 줄: 원래 자리가 위로 사라지면 보인다 ──
  var pickBar = document.getElementById('pick-bar');
  var pick = document.querySelector('.pick');
  if (pickBar && pick) {
    pickBar.hidden = false;
    document.documentElement.style.setProperty('--pin-h', pickBar.offsetHeight + 'px');
    var pinTop = parseFloat(getComputedStyle(pickBar).top);
    var pin = function () { pickBar.hidden = pick.getBoundingClientRect().bottom > pinTop; };
    window.addEventListener('scroll', pin, { passive: true });
    window.addEventListener('resize', function () {
      var was = pickBar.hidden;
      pickBar.hidden = false;
      document.documentElement.style.setProperty('--pin-h', pickBar.offsetHeight + 'px');
      pickBar.hidden = was;
      pin();
    });
    pin();
  }

  // ── 목차: 화면에 없는 영역은 숨기고, 지금 보고 있는 영역을 표시한다 ──
  var toc = document.getElementById('toc');
  if (toc) {
    var links = [];
    toc.querySelectorAll('a').forEach(function (a) {
      var target = document.getElementById(a.getAttribute('href').slice(1));
      if (target) links.push({ a: a, target: target }); else a.hidden = true;
    });
    // 목차로 고른 영역은 사용자가 직접 스크롤할 때까지 그대로 표시한다 (맨 아래라 더 못 내려가도)
    var chosen = null;
    var unchoose = function () { if (chosen) { chosen = null; mark(); } };
    var mark = function () {
      if (chosen) { links.forEach(function (l) { l.a.classList.toggle('active', l === chosen); }); return; }
      // 목차로 옮기면 영역 위가 scroll-padding-top(상단 바·고정 줄 아래)에 멈춘다. 그보다 조금 아래까지를 지금 영역으로 본다
      // 면접 전 칸이 숨어 있으면 위치가 모두 0이라 마지막 항목이 잡힌다. 다시 보일 때(stageshow) 잰다
      if (!links[0].target.getClientRects().length) return;
      var line = (parseFloat(getComputedStyle(document.documentElement).scrollPaddingTop) || 150) + 24;
      var current = links[0];
      links.forEach(function (l) { if (l.target.getBoundingClientRect().top <= line) current = l; });
      // 맨 아래까지 내리면 마지막 항목
      if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2) current = links[links.length - 1];
      links.forEach(function (l) { l.a.classList.toggle('active', l === current); });
    };
    if (links.length) {
      window.addEventListener('scroll', mark, { passive: true });
      document.addEventListener('stageshow', function () { chosen = null; mark(); });
      ['wheel', 'touchstart', 'keydown', 'mousedown'].forEach(function (t) {
        window.addEventListener(t, function (e) { if (!(e.target.closest && e.target.closest('#toc'))) unchoose(); }, { passive: true });
      });
      // 목차를 누르면 부드럽게 옮긴다 (새로 연 페이지가 #위치로 갈 때는 바로 간다)
      links.forEach(function (l) {
        l.a.addEventListener('click', function (e) {
          e.preventDefault();
          chosen = l;
          mark();
          l.target.scrollIntoView({ behavior: 'smooth', block: 'start' });
          history.replaceState(null, '', l.a.getAttribute('href'));
        });
      });
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

// 평가 기준 카드: 검사 전 카드는 펼치지 않고, 인쇄할 때는 모두 펼친다
(function () {
  document.addEventListener('click', function (e) {
    var head = e.target.closest('details.ev[data-static] > summary');
    if (head) e.preventDefault();
  });
  window.addEventListener('beforeprint', function () {
    document.querySelectorAll('details.ev:not([data-static])').forEach(function (d) { d.open = true; });
  });
})();

// ── AI와 대화 (사용자 2026-10-28, 예전 AI 의견 보기): 지금 단계(면접 전·면접 후)의 정해진 자료를 보고 답한다.
//    자료는 고르지 않고 「AI가 보는 자료」로 보여 주기만 한다.
//    기본은 지원자 화면 안에 떠 있는 창이고, 제목 줄의 창 분리 버튼을 누르면 별도의 브라우저 창(팝업, chat-window.jsp)으로 옮긴다
//    (사용자 2026-10-28). 팝업도 그 지원자 화면에 묶인다: 화면을 떠나면 닫히고, 단계는 화면을 따라간다 ──
(function () {
  var dlg = document.getElementById('dlg-chat');
  if (!dlg) return;
  var btn = document.getElementById('chat-btn');
  var isWindow = dlg.classList.contains('is-window');
  var url = dlg.getAttribute('data-url');
  var log = document.getElementById('chat-log');
  var src = document.getElementById('chat-src');
  var examples = document.getElementById('chat-examples');
  var form = document.getElementById('chat-form');
  var input = document.getElementById('chat-input');
  var send = document.getElementById('chat-send');
  var stage = 'BEFORE';
  var busy = false;

  function toast(msg) {
    if (window.showToast) window.showToast(msg, true, 4000);
  }

  function bubble(role, text) {
    var el = document.createElement('div');
    el.className = 'chat-msg is-' + (role === 'AI' ? 'ai' : 'user');
    if (role === 'AI') setMarkdown(el, text); else el.textContent = text;
    log.appendChild(el);
    return el;
  }

  // AI 답은 마크다운으로 온다 (사용자 2026-10-28). 먼저 전부 이스케이프하고 제목·목록·굵게·코드만 살린다
  function esc(s) {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function inline(s) {
    return esc(s)
      .replace(/`([^`]+)`/g, '<code>$1</code>')
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/__([^_]+)__/g, '<strong>$1</strong>');
  }

  function markdown(text) {
    var out = [], list = null, para = [], code = null;
    function flushPara() { if (para.length) { out.push('<p>' + para.join('<br>') + '</p>'); para = []; } }
    function flushList() { if (list) { out.push('<' + list.tag + '>' + list.items.join('') + '</' + list.tag + '>'); list = null; } }
    text.replace(/\r/g, '').split('\n').forEach(function (line) {
      if (code !== null) {
        if (/^\s*```/.test(line)) { out.push('<pre><code>' + esc(code.join('\n')) + '</code></pre>'); code = null; }
        else code.push(line);
        return;
      }
      if (/^\s*```/.test(line)) { flushPara(); flushList(); code = []; return; }
      var m;
      // 빈 줄로 띄운 번호 목록(1. … 빈 줄 2. …)도 한 목록으로 이어 간다
      if (!line.trim()) { flushPara(); return; }
      if ((m = line.match(/^\s*#{1,6}\s+(.*)$/))) { flushPara(); flushList(); out.push('<h4>' + inline(m[1]) + '</h4>'); return; }
      if (/^\s*(-{3,}|\*{3,})\s*$/.test(line)) { flushPara(); flushList(); out.push('<hr>'); return; }
      var ul = line.match(/^(\s*)[-*•]\s+(.*)$/), ol = line.match(/^(\s*)\d+[.)]\s+(.*)$/);
      if (ul || ol) {
        flushPara();
        var tag = ol ? 'ol' : 'ul', mm = ol || ul;
        // 들여쓴 하위 항목은 바깥 목록에 그대로 붙인다
        if (!list || (list.tag !== tag && !mm[1])) { flushList(); list = { tag: tag, items: [] }; }
        var num = ol && !mm[1] ? ' value="' + parseInt(line, 10) + '"' : '';
        list.items.push('<li' + num + (mm[1] ? ' class="is-sub"' : '') + '>' + inline(mm[2]) + '</li>');
        return;
      }
      if (list && /^\s+/.test(line)) { list.items[list.items.length - 1] = list.items[list.items.length - 1].replace(/<\/li>$/, '<br>' + inline(line.trim()) + '</li>'); return; }
      flushList();
      para.push(inline(line));
    });
    if (code !== null) out.push('<pre><code>' + esc(code.join('\n')) + '</code></pre>');
    flushPara(); flushList();
    return out.join('');
  }

  // 써지는 동안 매번 통째로 바꾸면 끌어 놓은 선택이 풀린다 (사용자 2026-10-28: 다 써진 뒤에야 끌어서 고를 수 있었다).
  // 그래서 달라진 곳만 고친다: 같은 노드는 그대로 두고, 글은 뒤에 이어 붙인다
  function morph(oldParent, newParent) {
    var news = newParent.childNodes;
    for (var i = 0; i < news.length; i++) {
      var n = news[i], o = oldParent.childNodes[i];
      if (!o) { oldParent.appendChild(n.cloneNode(true)); continue; }
      if (o.isEqualNode(n)) continue;
      if (o.nodeType === 3 && n.nodeType === 3) {
        if (n.data.indexOf(o.data) === 0) o.appendData(n.data.slice(o.data.length));
        else o.data = n.data;
      } else if (o.nodeType === 1 && n.nodeType === 1 && o.tagName === n.tagName && sameAttrs(o, n)) {
        morph(o, n);
      } else {
        oldParent.replaceChild(n.cloneNode(true), o);
      }
    }
    while (oldParent.childNodes.length > news.length) oldParent.removeChild(oldParent.lastChild);
  }

  function sameAttrs(a, b) {
    if (a.attributes.length !== b.attributes.length) return false;
    for (var i = 0; i < a.attributes.length; i++) {
      if (b.getAttribute(a.attributes[i].name) !== a.attributes[i].value) return false;
    }
    return true;
  }

  function setMarkdown(el, text) {
    el.classList.add('is-md');
    var next = document.createElement('div');
    next.innerHTML = markdown(text);
    morph(el, next);
  }

  function scrollDown() {
    log.scrollTop = log.scrollHeight;
  }

  function renderSources(list) {
    document.getElementById('chat-src-n').textContent = list.filter(function (s) { return !s.empty; }).length;
    src.innerHTML = '';
    list.forEach(function (s) {
      var li = document.createElement('li');
      li.className = 'chat-src-item' + (s.empty ? ' is-empty' : '');
      var b = document.createElement('b');
      b.textContent = s.label;
      li.appendChild(b);
      if (s.empty || s.detail) {
        var em = document.createElement('em');
        em.textContent = s.empty ? '없음' : s.detail;
        li.appendChild(em);
      }
      src.appendChild(li);
    });
  }

  // 대화가 시작되면 첫 예시만 두고 나머지는 「예시 더보기」로 접는다 (사용자 2026-10-28: 쌓여 보여 정신없다)
  function renderExamples() {
    var texts = (examples.getAttribute(stage === 'AFTER' ? 'data-after' : 'data-before') || '').split('|').filter(Boolean);
    examples.innerHTML = '';
    examples.classList.remove('show-more');
    texts.forEach(function (t, i) {
      var b = document.createElement('button');
      b.type = 'button';
      b.className = 'chat-ex' + (i > 0 ? ' is-more' : '');
      b.textContent = t;
      examples.appendChild(b);
    });
    if (texts.length > 1) {
      var more = document.createElement('button');
      more.type = 'button';
      more.className = 'chat-ex-more';
      more.textContent = '예시 더보기';
      examples.appendChild(more);
    }
    examples.classList.toggle('is-big', !log.children.length);
  }

  // AI가 보는 자료 목록만 다시 불러온다 (대화는 그대로)
  function refreshSources() {
    fetch(url + '?stage=' + stage, { headers: { 'X-Requested-With': 'fetch' } })
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (data) { if (data) renderSources(data.sources); });
  }

  // 화면 일부가 바뀌면(면접 기록 만들기, 질문지에 넣기 등) 자료도 바뀌었을 수 있다.
  // 떠 있는 창은 바로, 따로 뜬 창은 이 숫자가 바뀐 것을 보고 다시 불러온다 (사용자 2026-10-28)
  if (!isWindow) {
    window.chatMaterialVer = 0;
    document.addEventListener('partial', function () {
      window.chatMaterialVer++;
      if (dlg.open) refreshSources();
    });
  }

  function load() {
    log.innerHTML = '';
    src.innerHTML = '';
    fetch(url + '?stage=' + stage, { headers: { 'X-Requested-With': 'fetch' } })
      .then(function (res) { return res.ok ? res.json() : res.text().then(function (t) { throw new Error(t); }); })
      .then(function (data) {
        renderSources(data.sources);
        data.messages.forEach(function (m) { bubble(m.role, m.content); });
        renderExamples();
        scrollDown();
      })
      .catch(function (e) { toast(e.message || '대화를 불러오지 못했습니다.'); });
  }

  if (!isWindow) {
    // ── 떠 있는 창 (사용자 2026-10-28): 화면을 쓰면서 옆에 둘 수 있게 모달이 아니다.
    //    제목 줄로 옮기고, 모서리로 크기를 바꾸고, ─ 로 오른쪽 아래에 접어 둔다. 자리와 크기는 이 브라우저에 기억한다 ──
    var GEO_KEY = 'chatGeo';
  
    function saveGeo() {
      if (dlg.classList.contains('is-min') || !dlg.open) return;
      var r = dlg.getBoundingClientRect();
      try { localStorage.setItem(GEO_KEY, JSON.stringify({ x: r.left, y: r.top, w: r.width, h: r.height })); } catch (e) { /* 기억 못 해도 된다 */ }
    }
  
    function clampInto(x, y, w, h) {
      var vw = window.innerWidth, vh = window.innerHeight;
      w = Math.min(w, vw - 16);
      h = Math.min(h, vh - 16);
      return { x: Math.max(8, Math.min(x, vw - w - 8)), y: Math.max(8, Math.min(y, vh - h - 8)), w: w, h: h };
    }
  
    function placeWindow() {
      var g = null;
      try { g = JSON.parse(localStorage.getItem(GEO_KEY) || 'null'); } catch (e) { g = null; }
      var vw = window.innerWidth, vh = window.innerHeight;
      if (!g) {
        var w = Math.min(860, vw - 48), h = Math.min(760, vh - 64);
        g = { x: (vw - w) / 2, y: (vh - h) / 2, w: w, h: h };
      }
      g = clampInto(g.x, g.y, g.w, g.h);
      dlg.style.left = g.x + 'px';
      dlg.style.top = g.y + 'px';
      dlg.style.width = g.w + 'px';
      dlg.style.height = g.h + 'px';
    }
  
    function setMin(on) {
      dlg.classList.toggle('is-min', on);
      if (on) document.getElementById('chat-src-box').open = false;
      else { scrollDown(); input.focus(); }
    }
  
    btn.addEventListener('click', function () {
      if (dlg.open) {
        // 이미 열려 있으면(접어 둔 것 포함) 대화를 다시 불러오지 않고 펼치기만 한다
        setMin(false);
        return;
      }
      var name = document.documentElement.getAttribute('data-stage');
      stage = name === 'after' ? 'AFTER' : 'BEFORE';
      document.getElementById('chat-src-box').open = false;
      dlg.classList.remove('is-min');
      placeWindow();
      dlg.show();
      load();
      input.focus();
    });
  
    document.getElementById('chat-min').addEventListener('click', function (e) {
      e.stopPropagation();
      setMin(true);
    });
  
    // 접힌 줄을 누르면 펼친다 (닫기 버튼은 제외)
    dlg.addEventListener('click', function (e) {
      if (dlg.classList.contains('is-min') && !e.target.closest('[data-close]')) setMin(false);
    });
  
    // 제목 줄을 끌어 옮긴다. 버튼과 자료 목록은 끌지 않는다
    dlg.querySelector('header').addEventListener('pointerdown', function (e) {
      if (e.button !== 0 || dlg.classList.contains('is-min') || e.target.closest('button, summary, details')) return;
      var r = dlg.getBoundingClientRect();
      var dx = e.clientX - r.left, dy = e.clientY - r.top;
      var head = e.currentTarget;
      head.setPointerCapture(e.pointerId);
      dlg.classList.add('is-dragging');
      function move(ev) {
        var g = clampInto(ev.clientX - dx, ev.clientY - dy, r.width, r.height);
        dlg.style.left = g.x + 'px';
        dlg.style.top = g.y + 'px';
      }
      function up() {
        head.removeEventListener('pointermove', move);
        head.removeEventListener('pointerup', up);
        head.removeEventListener('pointercancel', up);
        dlg.classList.remove('is-dragging');
        saveGeo();
      }
      head.addEventListener('pointermove', move);
      head.addEventListener('pointerup', up);
      head.addEventListener('pointercancel', up);
    });
  
    // 모서리로 크기를 바꾸면 기억한다
    if (window.ResizeObserver) {
      var resizeTimer;
      new ResizeObserver(function () {
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(saveGeo, 250);
      }).observe(dlg);
    }
  
    // 창 크기가 줄면 화면 밖으로 나가지 않게 당겨 온다
    window.addEventListener('resize', function () {
      if (!dlg.open || dlg.classList.contains('is-min')) return;
      var r = dlg.getBoundingClientRect();
      var g = clampInto(r.left, r.top, r.width, r.height);
      dlg.style.left = g.x + 'px';
      dlg.style.top = g.y + 'px';
    });
  
    dlg.addEventListener('close', function () { dlg.classList.remove('is-min'); });

    // 창 분리: 지원자마다 이름 붙인 팝업으로 옮긴다. 이미 열려 있으면 그 창을 앞으로
    var popup = null;
    var openPopup = function () {
      var w = 560, h = Math.min(820, screen.availHeight - 80);
      var left = Math.max(0, (window.screenX || 0) + window.outerWidth - w - 40);
      var top = Math.max(0, (window.screenY || 0) + 80);
      popup = window.open(dlg.getAttribute('data-window'), 'chat-' + dlg.getAttribute('data-id'),
        'popup=yes,width=' + w + ',height=' + h + ',left=' + left + ',top=' + top);
      if (!popup) {
        toast('팝업이 막혀 있습니다. 주소창 오른쪽에서 이 사이트의 팝업을 허용해 주세요.');
        return false;
      }
      popup.focus();
      return true;
    };
    document.getElementById('chat-pop').addEventListener('click', function () {
      if (busy) { toast('답을 다 받은 뒤에 창을 분리할 수 있습니다.'); return; }
      if (openPopup()) dlg.close();
    });
    // 팝업이 떠 있으면 「AI와 대화」는 그 창을 앞으로 가져온다
    btn.addEventListener('click', function (e) {
      if (popup && !popup.closed) {
        e.stopImmediatePropagation();
        popup.focus();
      }
    }, true);
    // 팝업의 「화면 안으로」 버튼이 부른다: 팝업을 잊고 화면 안 창으로 다시 연다
    window.chatDock = function () {
      popup = null;
      window.focus();
      btn.click();
    };
    // 지원자 화면을 떠나면 팝업도 닫는다
    addEventListener('pagehide', function () {
      if (popup && !popup.closed) popup.close();
    });
  }

  // 자료 목록은 겉에 뜨는 창이라 바깥을 누르면 닫는다
  dlg.addEventListener('click', function (e) {
    var box = document.getElementById('chat-src-box');
    if (box.open && !box.contains(e.target)) box.open = false;
  });

  examples.addEventListener('click', function (e) {
    if (e.target.closest('.chat-ex-more')) {
      var open = examples.classList.toggle('show-more');
      e.target.closest('.chat-ex-more').textContent = open ? '접기' : '예시 더보기';
      return;
    }
    var b = e.target.closest('.chat-ex');
    if (!b) return;
    if (examples.classList.contains('show-more')) examples.querySelector('.chat-ex-more').click();
    ask(b.textContent);
    input.focus();
  });

  input.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
      e.preventDefault();
      form.requestSubmit();
    }
  });

  // 글이 써지는 동안 따라 내려가는 건 사용자가 위로 올리기 전까지만 (사용자 2026-10-28: 처음부터 천천히 읽으려고 올리면
  // 다시 아래로 끌려갔다). 휠·터치·스크롤바 무엇으로든 위로 움직이면 멈추고, 맨 아래까지 다시 내리면 또 따라간다
  var follow = true;
  var lastTop = 0;
  log.addEventListener('scroll', function () {
    var atBottom = log.scrollHeight - log.scrollTop - log.clientHeight < 4;
    if (log.scrollTop < lastTop - 1) follow = false;
    if (atBottom) follow = true;
    lastTop = log.scrollTop;
  });
  log.addEventListener('wheel', function (e) { if (e.deltaY < 0) follow = false; }, { passive: true });

  function followDown() {
    if (!follow) return;
    log.scrollTop = log.scrollHeight;
    lastTop = log.scrollTop;
  }

  // 답은 스트림으로 온다 (사용자 2026-10-28): 한 줄에 JSON 하나, {"d": 조각}… 끝에 {"done": true, "content"} 또는 {"error"}
  function readStream(res, onDelta) {
    var reader = res.body.getReader();
    var decoder = new TextDecoder();
    var buf = '';
    var result = null;
    function handle(line) {
      if (!line.trim()) return;
      var m = JSON.parse(line);
      if (m.d) onDelta(m.d);
      else if (m.done) result = m.content;
      else if (m.error) throw new Error(m.error);
    }
    function pump() {
      return reader.read().then(function (r) {
        buf += decoder.decode(r.value || new Uint8Array(), { stream: !r.done });
        var lines = buf.split('\n');
        buf = lines.pop();
        lines.forEach(handle);
        if (!r.done) return pump();
        handle(buf);
        if (result === null) throw new Error('AI 답이 중간에 끊겼습니다.');
        return result;
      });
    }
    return pump();
  }

  function ask(text) {
    if (!text || busy) return;
    busy = true;
    send.disabled = true;
    bubble('USER', text);
    input.value = '';
    examples.classList.remove('is-big');
    var wait = bubble('AI', '답변 중…');
    wait.classList.add('is-wait');
    follow = true;
    followDown();
    // 받은 글을 한 번에 붙이지 않고 타자 치듯 조금씩 보여 준다 (사용자 2026-10-28: 스트림이어도 뭉텅이로 붙어 써지는 느낌이 없었다).
    // 밀린 글이 많을수록 빨리 따라잡아, 다 받은 뒤 오래 기다리지 않는다
    var got = '', shown = 0, frame = 0, done = null;
    function paint() {
      setMarkdown(wait, got.slice(0, shown));
      followDown();
    }
    function tick() {
      frame = 0;
      var backlog = got.length - shown;
      if (backlog > 0) {
        shown += Math.max(1, Math.ceil(backlog / 90));
        paint();
      }
      if (shown < got.length) frame = requestAnimationFrame(tick);
      else if (done) done();
    }
    fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'fetch' },
      body: JSON.stringify({ stage: stage, message: text })
    }).then(function (res) {
      if (!res.ok) return res.text().then(function (t) { throw new Error(t); });
      return readStream(res, function (d) {
        if (!got) {
          wait.classList.remove('is-wait');
          wait.classList.add('is-typing');
        }
        got += d;
        if (!frame) frame = requestAnimationFrame(tick);
      });
    }).then(function (content) {
      // 받은 글을 다 보여 준 뒤에 마무리한다
      return new Promise(function (resolve) {
        got = content;
        done = resolve;
        if (!frame) tick();
      });
    }).then(function () {
      wait.classList.remove("is-wait", "is-typing");
      setMarkdown(wait, got);
      followDown();
    }).catch(function (err) {
      // 저장되지 않은 질문은 입력칸에 돌려놓는다
      if (frame) cancelAnimationFrame(frame);
      wait.remove();
      log.lastChild && log.lastChild.remove();
      input.value = text;
      toast(err.message || 'AI가 답하지 못했습니다.');
      renderExamples();
    }).finally(function () {
      busy = false;
      send.disabled = false;
    });
  }

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    ask(input.value.trim());
  });

  document.getElementById('chat-reset').addEventListener('click', function () {
    if (busy || !log.children.length) return;
    fetch(url, { method: 'DELETE', headers: { 'X-Requested-With': 'fetch' } })
      .then(function (res) { if (!res.ok) throw new Error(); load(); })
      .catch(function () { toast('대화를 지우지 못했습니다.'); });
  });

  // ── 팝업 창: 지원자 화면에 묶인다. 그 화면이 닫히거나 다른 곳으로 가면 닫고, 단계(면접 전·후)는 그 화면을 따라간다 ──
  if (isWindow) {
    var home = dlg.getAttribute('data-home');
    // 화면 안으로: 지원자 화면의 떠 있는 창으로 돌려보내고 이 창은 닫는다 (사용자 2026-10-28)
    document.getElementById('chat-dock').addEventListener('click', function () {
      if (busy) { toast('답을 다 받은 뒤에 되돌릴 수 있습니다.'); return; }
      try {
        if (window.opener && window.opener.chatDock) window.opener.chatDock();
      } catch (e) { /* 지원자 화면이 없으면 그냥 닫는다 */ }
      window.close();
    });
    var openerStage = function () {
      try {
        var o = window.opener;
        if (!o || o.closed || o.location.pathname !== home) return null;
        return o.document.documentElement.getAttribute('data-stage') === 'after' ? 'AFTER' : 'BEFORE';
      } catch (e) {
        return null;
      }
    };
    // 지원자 화면이 자료를 바꾼 횟수 (다시 불러온 화면이면 처음부터 센다)
    var openerVer = function () {
      try { return window.opener.chatMaterialVer; } catch (e) { return null; }
    };
    stage = openerStage() || 'BEFORE';
    var ver = openerVer();
    load();
    input.focus();
    setInterval(function () {
      var s = openerStage();
      if (s === null) { window.close(); return; }
      var v = openerVer();
      // 단계가 바뀌거나 지원자 화면의 내용이 바뀌면 AI가 보는 자료만 다시 불러온다 (대화는 지원자마다 하나라 그대로)
      if (s !== stage || v !== ver) {
        var stageChanged = s !== stage;
        stage = s;
        ver = v;
        refreshSources();
        if (stageChanged && !busy) renderExamples();
      }
    }, 1000);
  }
})();

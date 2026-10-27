<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="${not empty r ? r.candidate.displayName : '포지션 적합도 검사'}"/>
<c:set var="activeTab" value="fit"/>
<%@ include file="common/header.jspf" %>
<%-- 결과 연출: 그리기 전에 숨겨 두어야 깜빡이지 않는다 (app.js가 풀어 준다) --%>
<script>
  try {
    if (${justChecked ? 'true' : 'false'} || sessionStorage.getItem('reveal')) document.documentElement.classList.add('revealing');
  } catch (e) { /* 연출만 빠진다 */ }
</script>

<c:choose>
<c:when test="${empty position}">
  <div class="empty-page">평가 기준을 추출한 포지션이 없습니다. <a href="<c:url value='/positions'/>">평가 기준 추출</a></div>
</c:when>
<c:otherwise>
<div class="ws" id="ws"
     data-position-base="<c:url value='/positions/${position.id}'/>"
     data-result-base="<c:if test='${not empty r}'><c:url value='/results/${r.candidate.id}'/></c:if>">

<c:if test="${not empty r}">
  <header class="print-head">
    <h1 data-cand-name><c:out value="${r.candidate.displayName}"/></h1>
    <span><c:out value="${position.title}"/></span>
  </header>
</c:if>

<c:set var="pickBase" value="/fit"/>
<%@ include file="common/position-pick.jspf" %>

<div class="split fit-split${empty r or not r.fitChecked ? ' is-single' : ''}">
  <c:if test="${not empty r and r.fitChecked}">
  <aside class="side">
    <nav class="toc" id="toc">
      <a href="#sec-profile">개인정보</a>
      <a href="#sec-evidence">평가 기준 충족 여부</a>
      <a href="#questions">면접 질문</a>
      <a href="#opinion">AI 의견</a>
    </nav>
  </aside>
  </c:if>

  <div class="detail"${justChecked ? ' data-reveal' : ''}>
<c:choose>
  <c:when test="${empty r}">
    <section class="stage" id="fit">
      <header class="stage-head"><h2>포지션 적합도 검사</h2></header>
      <form method="post" enctype="multipart/form-data" action="<c:url value='/positions/${position.id}/batch'/>"
            data-busy="이력서를 읽는 중" id="batch-form">
        <div class="bz-grid" id="bz-grid">
          <c:forEach var="n" begin="1" end="1">
            <div class="bz">
              <label class="dropzone">
                <input type="file" name="applicant-${n}" accept=".pdf,.doc,.docx,.ppt,.pptx,.hwp,.hwpx" multiple>
                <span class="bz-no">지원자 ${n}</span>
                <span class="dz-icon" aria-hidden="true"><svg class="i"><use href="#i-file"/></svg></span>
                <span class="dz-text">이력서·포트폴리오를 끌어다 놓거나 클릭</span>
                <span class="dz-types">PDF · DOC · DOCX · PPT · PPTX · HWP · HWPX</span>
              </label>
              <button type="button" class="icon-btn bz-del" title="빼기"><svg class="i"><use href="#i-trash"/></svg></button>
            </div>
          </c:forEach>
        </div>
        <button type="button" class="add-row bz-add" id="bz-add"><svg class="i"><use href="#i-plus"/></svg>지원자 추가</button>
        <button type="submit" class="btn btn-primary btn-lg bz-submit">포지션 적합도 검사</button>
      </form>
    </section>
  </c:when>
  <c:otherwise>
<c:set var="st" value="${r.candidate.status}"/>
<c:set var="busy" value="${st == 'QUEUED' or st == 'MASKING' or st == 'ANALYZING'}"/>
<c:set var="pf" value="${r.profile}"/>
<c:set var="showProfile" value="${r.fitChecked and ((not empty pf and pf.filled) or not empty position.requiredExperience)}"/>
<c:set var="renameBtn"><c:if test="${not busy}"><button type="button" class="icon-btn" id="cand-rename" title="이름 바꾸기"><svg class="i"><use href="#i-pencil"/></svg></button></c:if></c:set>
<%-- 면접 기록: 면접관이 직접 체크한다 (AI는 합격·불합격을 정하지 않는다) --%>
<div class="iv-bar" id="iv-bar" data-url="<c:url value='/results/${r.candidate.id}/interview'/>" data-interviewed="${r.candidate.interviewed}" data-date="${r.candidate.interviewDate}">
  <div class="iv-decision" role="group" aria-label="합격 여부">
    <button type="button" class="iv-pass${r.candidate.decision == 'PASS' ? ' is-on' : ''}" data-decision="PASS" aria-pressed="${r.candidate.decision == 'PASS'}">합격</button>
    <button type="button" class="iv-fail${r.candidate.decision == 'FAIL' ? ' is-on' : ''}" data-decision="FAIL" aria-pressed="${r.candidate.decision == 'FAIL'}">불합격</button>
  </div>
  <%-- 보고서는 합격 여부와 상관없이 만들 수 있다 --%>
  <c:choose>
    <c:when test="${hasReport}">
      <a class="btn btn-primary btn-sm iv-report" id="iv-report" href="<c:url value='/results/${r.candidate.id}/report'/>">보고서</a>
    </c:when>
    <c:otherwise>
      <form class="iv-report" id="iv-report" method="post" action="<c:url value='/results/${r.candidate.id}/report'/>"
            data-busy="보고서를 만드는 중" data-busy-sub="면접 질문지와 녹음본에서 지원자 정보를 찾고 있습니다">
        <button type="submit" class="btn btn-primary btn-sm">보고서</button>
      </form>
    </c:otherwise>
  </c:choose>
  <span class="save-state" id="iv-state" aria-live="polite"></span>
  <c:if test="${not busy}">
  <div class="cand-actions">
  <c:if test="${not empty r.candidate.maskedText}">
  <form method="post" action="<c:url value='/results/${r.candidate.id}/check'/>" data-busy="포지션 적합도를 검사하는 중"
        <c:if test="${not empty r.sections}">data-confirm="다시 검사하면 지금 질문지가 지워집니다. 계속할까요?"</c:if>>
    <button type="submit" class="btn btn-sm ${r.fitChecked ? 'btn-ghost' : 'btn-primary'}">${r.fitChecked ? '다시 검사' : '검사'}</button>
  </form>
  </c:if>
  <%-- 가리기 전에 멈췄거나 실패: 저장해 둔 원본으로 처음부터 --%>
  <c:if test="${empty r.candidate.maskedText and not empty candFiles}">
  <form method="post" action="<c:url value='/results/${r.candidate.id}/restart'/>">
    <button type="submit" class="btn btn-sm btn-primary">다시 검사</button>
  </form>
  </c:if>
  <form method="post" action="<c:url value='/results/${r.candidate.id}/delete'/>" data-confirm="이 지원자의 검사 결과와 질문지를 삭제할까요?">
    <button type="submit" class="btn btn-ghost btn-sm btn-danger-text">삭제</button>
  </form>
  </div>
  </c:if>
</div>
<%-- 가린 이력서(외부 AI가 본 글) 보기와 지원자가 낸 원본 파일 (가리지 않은 원본) --%>
<c:if test="${not empty candFiles or not empty r.candidate.maskedText}">
<ul class="file-chips cand-files">
  <c:if test="${not empty r.candidate.maskedText}">
    <li><button type="button" class="file-chip is-masked" data-dialog="dlg-masked"><svg class="i"><use href="#i-eye"/></svg><span>가린 이력서</span></button></li>
  </c:if>
  <c:forEach var="cf" items="${candFiles}">
    <li><a class="file-chip" href="<c:url value='/results/${r.candidate.id}/files/${cf.id}'/>" download title="내려받기">
      <svg class="i"><use href="#i-file"/></svg><span><c:out value="${cf.fileName}"/></span></a></li>
  </c:forEach>
</ul>
<c:if test="${not empty r.candidate.maskedText}">
<dialog class="pf-dialog masked-dialog" id="dlg-masked">
  <header><h3>가린 이력서</h3><button type="button" class="icon-btn" data-close title="닫기"><svg class="i"><use href="#i-x"/></svg></button></header>
  <div class="masked-doc" data-masked><c:out value="${r.candidate.maskedText}"/></div>
</dialog>
</c:if>
</c:if>
<c:if test="${busy or empty r.candidate.maskedText or (st == 'FAILED' and not r.fitChecked) or not showProfile}">
<section class="stage" id="fit">
  <header class="stage-head">
    <div class="title-edit">
      <h2 data-cand-name id="cand-title"><c:out value="${r.candidate.displayName}"/></h2>
      ${renameBtn}
    </div>
  </header>
        <c:choose>
          <c:when test="${st == 'QUEUED' or st == 'MASKING' or st == 'ANALYZING'}">
            <div class="progress-box" id="progress-box">
              <span class="spinner"></span>
              <strong>${st == 'QUEUED' ? '차례를 기다리는 중' : st == 'MASKING' ? '개인정보를 가리는 중' : '포지션 적합도를 검사하는 중'}</strong>
            </div>
          </c:when>
          <c:when test="${empty r.candidate.maskedText}">
            <div class="flash flash-error"><c:out value="${r.candidate.errorMessage}"/></div>
          </c:when>
          <c:when test="${st == 'FAILED' and not r.fitChecked}">
            <div class="flash flash-error">검사하지 못했습니다. <c:out value="${r.candidate.errorMessage}"/></div>
          </c:when>
        </c:choose>
</section>
</c:if>

        <c:set var="pf" value="${r.profile}"/>
        <c:set var="cev" value="${r.careerEval}"/>
        <c:set var="fit" value="${r.fit}"/>
        <c:if test="${r.fitChecked and ((not empty pf and pf.filled) or not empty position.requiredExperience)}">
        <section class="stage" id="sec-profile">
          <header class="stage-head"><h2>개인정보</h2></header>
          <div class="profile${justChecked ? ' is-stamping' : ''}">
            <%-- 인적사항: 왼쪽에 이름, 오른쪽에 연락처를 이름표 · 값 목록으로 --%>
              <div class="pf-person${pf.personFilled ? '' : ' is-name-only'}">
                <div class="pf-who">
                  <div>
                    <div class="title-edit">
                      <div class="pf-name" data-cand-name id="cand-title"><c:out value="${r.candidate.displayName}"/></div>
                      ${renameBtn}
                    </div>
                    <div class="pf-meta">
                      <c:out value="${pf.gender}"/><c:if test="${not empty pf.gender and not empty pf.birth}"> · </c:if><c:out value="${pf.birth}"/>
                    </div>
                  </div>
                </div>
                <c:if test="${not empty pf.phone or not empty pf.email or not empty pf.address}">
                <dl class="pf-info">
                  <c:if test="${not empty pf.phone}"><dt>전화번호</dt><dd><c:out value="${pf.phone}"/></dd></c:if>
                  <c:if test="${not empty pf.email}"><dt>이메일</dt><dd><c:out value="${pf.email}"/></dd></c:if>
                  <c:if test="${not empty pf.address}"><dt>주소</dt><dd><c:out value="${pf.address}"/></dd></c:if>
                </dl>
                </c:if>
              </div>

            <%-- 경력 · 학력 · 자격증: 충족 여부가 먼저 보이고, 카드를 누르면 자세한 내용 --%>
            <c:set var="gauge" value="${r.careerGauge}"/>
            <c:set var="certs" value="${cev.certificates}"/>
            <div class="pf-checks">
              <div class="pf-check"<c:if test="${not empty r.relevantCareers}"> data-dialog="dlg-career" tabindex="0" role="button" aria-label="경력 자세히"</c:if>>
                <div class="pf-check-head">
                  <c:choose>
                    <c:when test="${not empty fit.career}"><c:set var="markLabel" value="경력"/><c:set var="markKind" value="${fit.career ? 'met' : 'unmet'}"/><c:set var="markText" value="${fit.career ? '충족' : '미충족'}"/>
<%@ include file="common/verdict-mark.jspf" %>
                    </c:when>
                    <c:otherwise><h3>경력</h3><span class="verdict-none">${empty position.requiredExperience ? '조건 없음' : '판단 못 함'}</span></c:otherwise>
                  </c:choose>
                </div>
                <c:set var="ln" value="${r.careerLine}"/><c:set var="lnTitle" value="${cev.relevantReason}"/>
                <div class="cmps">
<%@ include file="common/compare-line.jspf" %>
                </div>
              </div>

              <div class="pf-check"<c:if test="${not empty r.schools}"> data-dialog="dlg-education" tabindex="0" role="button" aria-label="학력 자세히"</c:if>>
                <div class="pf-check-head">
                  <c:choose>
                    <c:when test="${not empty fit.education}"><c:set var="markLabel" value="학력"/><c:set var="markKind" value="${fit.education ? 'met' : 'unmet'}"/><c:set var="markText" value="${fit.education ? '충족' : '미충족'}"/>
<%@ include file="common/verdict-mark.jspf" %>
                    </c:when>
                    <c:otherwise><h3>학력</h3><span class="verdict-none">${empty position.requiredEducation and empty position.requiredMajor ? '조건 없음' : '판단 못 함'}</span></c:otherwise>
                  </c:choose>
                </div>
                <c:set var="lnTitle" value="${cev.educationReason}"/>
                <div class="cmps">
                  <c:forEach var="ln" items="${r.educationLines}">
<%@ include file="common/compare-line.jspf" %>
                  </c:forEach>
                </div>
              </div>

              <%-- 자격증: 근거 분석 호출(GPT)이 읽은 것. 둘 다 있으면 동그라미, 하나면 세모, 없으면 X --%>
              <div class="pf-check">
                <div class="pf-check-head">
                  <c:choose>
                    <c:when test="${not empty certs}">
                      <c:set var="certCount" value="${(certs.infoEngineer ? 1 : 0) + (certs.sqld ? 1 : 0)}"/>
                      <c:set var="markLabel" value="자격증"/><c:set var="markKind" value="${certCount == 2 ? 'met' : certCount == 1 ? 'part' : 'unmet'}"/><c:set var="markText" value="${certCount == 2 ? '둘 다 있음' : certCount == 1 ? '하나 있음' : '없음'}"/>
<%@ include file="common/verdict-mark.jspf" %>
                    </c:when>
                    <c:otherwise><h3>자격증</h3></c:otherwise>
                  </c:choose>
                </div>
                <c:choose>
                  <c:when test="${empty certs}"><p class="pf-sub">다시 검사하면 표시됩니다</p></c:when>
                  <c:otherwise>
                    <dl class="pf-facts">
                      <dt>정보처리기사</dt><dd><span class="cert-flag ${certs.infoEngineer ? 'is-yes' : 'is-no'}">${certs.infoEngineer ? '있음' : '없음'}</span></dd>
                      <dt>SQLD</dt><dd><span class="cert-flag ${certs.sqld ? 'is-yes' : 'is-no'}">${certs.sqld ? '있음' : '없음'}</span></dd>
                    </dl>
                  </c:otherwise>
                </c:choose>
              </div>
            </div>

            <%-- 타임라인: 시작 순서대로 한 줄씩 --%>
            <c:set var="tl" value="${r.timeline}"/>
            <c:if test="${not empty tl and not empty tl.entries}">
              <div class="pf-card tl-card">
                <div class="pf-card-head"><h3>타임라인</h3></div>
                <ol class="tl-items">
                  <c:forEach var="e" items="${tl.entries}">
                    <li class="is-${e.tone}">
                      <span class="tl-when"><c:out value="${e.periodText}"/></span>
                      <span class="tl-kind"><c:out value="${e.tone == 'gap' ? '공백' : e.kind}"/></span>
                      <span class="tl-what">
                        <c:choose>
                          <c:when test="${e.tone == 'gap'}">이력서상 기록 없음 <small><c:out value="${e.label}"/></small></c:when>
                          <c:otherwise><strong><c:out value="${e.label}"/></strong><c:if test="${not empty e.detail}"><small><c:out value="${e.detail}"/></small></c:if></c:otherwise>
                        </c:choose>
                      </span>
                    </li>
                  </c:forEach>
                </ol>
              </div>
            </c:if>

            <%-- 상세보기 창 --%>
            <c:if test="${not empty r.relevantCareers}">
              <dialog class="pf-dialog" id="dlg-career">
                <header><h3>경력 상세</h3><button type="button" class="icon-btn" data-close title="닫기"><svg class="i"><use href="#i-x"/></svg></button></header>
                <c:if test="${not empty gauge and not empty gauge.relevantMonths}"><p class="pf-dialog-sum">포지션 관련 <c:out value="${gauge.relevantText}"/></p></c:if>
                <ul class="pf-list">
                  <c:forEach var="cr" items="${r.relevantCareers}">
                    <li>
                      <div class="pf-item-main">
                        <strong><c:out value="${cr.company}"/></strong>
                        <c:if test="${not empty cr.department or not empty cr.position}">
                          <span class="pf-sub"><c:out value="${cr.department}"/><c:if test="${not empty cr.department and not empty cr.position}"> · </c:if><c:out value="${cr.position}"/></span>
                        </c:if>
                        <c:set var="crDuration" value="${not empty cr.duration ? cr.duration : (empty cev ? '' : cev.durationOf(cr.alias))}"/>
                        <c:if test="${not empty cr.period or not empty crDuration}">
                          <span class="pf-when"><c:out value="${cr.period}"/><c:if test="${not empty crDuration}"> <em><c:out value="${crDuration}"/></em></c:if></span>
                        </c:if>
                      </div>
                    </li>
                  </c:forEach>
                </ul>
              </dialog>
            </c:if>
            <c:if test="${not empty r.schools}">
              <dialog class="pf-dialog" id="dlg-education">
                <header><h3>학력 상세</h3><button type="button" class="icon-btn" data-close title="닫기"><svg class="i"><use href="#i-x"/></svg></button></header>
                <ul class="pf-list">
                  <c:forEach var="ed" items="${r.schools}">
                    <li>
                      <div class="pf-item-main">
                        <strong><c:out value="${ed.school}"/></strong>
                        <c:if test="${not empty ed.major}"><span class="pf-sub"><c:out value="${ed.major}"/></span></c:if>
                        <c:if test="${not empty ed.period}"><span class="pf-when"><c:out value="${ed.period}"/></span></c:if>
                      </div>
                      <c:if test="${not empty ed.status or not empty ed.gpa}">
                        <div class="pf-item-side">
                          <c:if test="${not empty ed.status}"><span class="pf-badge"><c:out value="${ed.status}"/></span></c:if>
                          <c:if test="${not empty ed.gpa}"><span class="pf-pill">학점 <c:out value="${ed.gpa}"/></span></c:if>
                        </div>
                      </c:if>
                    </li>
                  </c:forEach>
                </ul>
              </dialog>
            </c:if>
          </div>
        </section>
        </c:if>

        <c:if test="${r.fitChecked}">
        <section class="stage" id="sec-evidence">
          <header class="stage-head"><h2>평가 기준 충족 여부</h2>
            <c:set var="score" value="${r.score}"/>
            <c:if test="${not empty score}">
              <span class="score-pill${score.complete ? '' : ' is-stale'}" title="${score.complete ? '' : '검사 뒤에 평가 기준이 바뀌었습니다. 다시 검사하세요.'}">${score.totalText}<small> / ${score.max}점</small></span>
            </c:if>
          </header>
          <div class="ev-list">
            <div class="ev-sum">
              <span class="ev-tag ev-DIRECT"><i class="hb ev-DIRECT"></i>충족 <b>${r.directCount}</b></span>
              <span class="ev-tag ev-INDIRECT"><i class="hb ev-INDIRECT"></i>부분 충족 <b>${r.indirectCount}</b></span>
              <span class="ev-tag ev-NONE"><i class="hb ev-NONE"></i>미확인 <b>${r.noneCount}</b></span>
            </div>
            <c:forEach var="cv" items="${r.criteria}" varStatus="cs">
              <c:set var="evKind" value="${cv.checked ? cv.match : 'UNCHECKED'}"/>
              <details class="ev ev-${evKind}"${cv.checked ? "" : " data-static"}>
                <summary class="ev-head">
                  <span class="ev-no">${cs.count < 10 ? '0' : ''}${cs.count}</span>
                  <h4><c:out value="${cv.criterion.name}"/></h4>
                  <span class="ev-tag"><i class="hb ev-${evKind}"></i>${evKind == 'DIRECT' ? '충족' : evKind == 'INDIRECT' ? '부분 충족' : evKind == 'NONE' ? '미확인' : '검사 전'}</span>
                  <c:if test="${cv.checked}"><svg class="ev-chev" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 9l6 6 6-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg></c:if>
                </summary>
                <c:if test="${cv.checked}">
                  <div class="ev-body">
                    <c:choose>
                      <c:when test="${empty cv.quotedEvidences}">
                        <p class="ev-none"><c:out value="${empty cv.noneNote ? '이력서에서 관련 내용을 찾지 못했습니다.' : cv.noneNote}"/></p>
                      </c:when>
                      <c:otherwise>
                        <c:forEach var="ev" items="${cv.quotedEvidences}">
                          <div class="ev-item">
                            <blockquote class="quote ${ev.verifiedYn == 'Y' ? '' : 'quote-unverified'}"><c:out value="${ev.quote}"/></blockquote>
                            <c:if test="${ev.verifiedYn != 'Y'}"><p class="warn-text">이력서 원문에서 찾지 못한 문장입니다</p>
                              <c:if test="${not empty ev.closestQuote}"><div class="ev-closest"><span>가장 비슷한 원문</span><p><c:out value="${ev.closestQuote}"/></p></div></c:if>
                            </c:if>
                            <p class="interp"><c:out value="${ev.interpretation}"/></p>
                          </div>
                        </c:forEach>
                      </c:otherwise>
                    </c:choose>
                  </div>
                </c:if>
              </details>
            </c:forEach>
          </div>
        </section>
        </c:if>

<%-- 면접 질문지 (적합도 검사가 끝나야 나온다, 그 전엔 자리도 없다): 타이핑(질문 추출 → 질문지, 면접관이 적는 답변) / 녹음본(녹음본으로 만든 질문-답변).
     질문 추출은 타이핑 질문지에만 넣으므로 타이핑 탭 안에 둔다 --%>
<c:set var="qOpen" value="${r.fitChecked}"/>
<c:if test="${qOpen}">
<section class="stage" id="questions">
  <header class="stage-head">
    <h2>면접 질문지</h2>
    <div class="qtabs" role="tablist">
      <button type="button" role="tab" class="qtab" data-qtab="sheet" aria-selected="true">타이핑</button>
      <button type="button" role="tab" class="qtab" data-qtab="record" aria-selected="false">녹음본<c:if test="${not empty record}"> <b>${fn:length(record.items)}</b></c:if></button>
    </div>
    <label class="iv-date">면접 본 날짜<input type="date" id="iv-date" value="${r.candidate.interviewDate}"><span class="save-state" id="iv-date-state" aria-live="polite"></span></label>
  </header>
  <div class="qpane" data-qpane="sheet">
  <%-- 질문 추출: 버튼 → 창에서 유형 이름을 적고(기본 네 유형, 직접 적은 유형) 추출. 여러 번 추출해 쌓고, 질문지에 넣는다 --%>
  <dialog class="pf-dialog qx-dialog" id="dlg-extract">
    <header><h3>면접 질문 추출</h3><button type="button" class="icon-btn" data-close title="닫기"><svg class="i"><use href="#i-x"/></svg></button></header>
    <form method="post" data-partial="#questions" action="<c:url value='/results/${r.candidate.id}/questions/generate'/>" data-busy="면접 질문을 추출하는 중" id="qx-form">
      <%-- 기본 네 유형은 켜고 끄기만. 직접 만든 유형은 「+ 유형 추가」로 바로 저장되고, ×로 바로 지운다 --%>
      <div class="qx-chips" id="qx-chips" data-url="<c:url value='/question-types'/>">
        <c:forEach var="t" items="${['거짓말 판독기', '어디까지 알고 한 거야?', '그 기술 왜 선택했어?', '해본 적 있어?']}">
          <label class="qx-type"><input type="checkbox" name="type" value="${t}" checked><span>${t}</span></label>
        </c:forEach>
        <c:forEach var="ct" items="${customTypes}">
          <span class="qx-chip" data-id="${ct.id}"><label class="qx-type"><input type="checkbox" name="type" value="<c:out value='${ct.name}'/>" checked><span><c:out value="${ct.name}"/></span></label><button type="button" class="qx-chip-del" title="지우기"><svg class="i"><use href="#i-x"/></svg></button></span>
        </c:forEach>
        <button type="button" class="qx-add" id="qx-add"><svg class="i"><use href="#i-plus"/></svg>유형 추가</button>
        <span class="qx-new-row" id="qx-new-row" hidden><input type="text" class="qx-new" id="qx-new" maxlength="60" placeholder="유형 이름" aria-label="추가할 유형"><button type="button" class="btn btn-primary btn-sm" id="qx-new-save">저장</button><button type="button" class="btn btn-ghost btn-sm" id="qx-new-cancel">취소</button></span>
      </div>
      <footer class="qx-foot">
        <button type="button" class="btn btn-ghost" data-close>취소</button>
        <button type="submit" class="btn btn-primary">추출</button>
      </footer>
    </form>
  </dialog>
  <div id="extract">
    <button type="button" class="qx-empty" data-dialog="dlg-extract"><svg class="ico"><use href="#i-plus"/></svg>면접 질문 추출</button>
    <c:if test="${not empty r.extracted}">
      <div class="qx-pool" id="qx-pool">
        <div class="qx-pool-head">
          <h3>추출한 질문 <b>${r.extractedCount}</b></h3>
          <form method="post" action="<c:url value='/results/${r.candidate.id}/questions/merge'/>" data-partial="#questions" data-busy="질문지를 만드는 중">
            <button type="submit" class="btn btn-primary btn-sm">${empty r.sections ? '공통 질문과 합치기' : '질문지에 넣기'}</button>
          </form>
        </div>
        <c:forEach var="grp" items="${r.extracted}">
          <section class="qx-sec">
            <h4 class="iq-sec-name"><c:out value="${grp.section}"/></h4>
            <ol class="iq-list">
              <c:forEach var="qv" items="${grp.questions}">
                <c:set var="q" value="${qv.question}"/>
                <li class="iq qx-row" data-id="${q.id}">
                  <span class="iq-no is-static"></span>
                  <div class="iq-main">
                    <div class="iq-top">
                      <p class="iq-content"><c:out value="${q.content}"/></p>
                      <span class="row-actions">
                        <button type="button" class="icon-btn js-q-edit" title="수정"><svg class="i"><use href="#i-pencil"/></svg></button>
                        <button type="button" class="icon-btn danger js-q-del" title="삭제"><svg class="i"><use href="#i-trash"/></svg></button>
                      </span>
                    </div>
                    <c:if test="${not empty qv.criterionName}"><div class="iq-meta"><span class="iq-crit"><c:out value="${qv.criterionName}"/></span></div></c:if>
                    <c:if test="${not empty qv.followUps}">
                      <ul class="iq-follow"><c:if test="${q.questionType.experience}"><li class="iq-follow-cap">해봤다고 하면</li></c:if><c:forEach var="f" items="${qv.followUps}"><li><c:out value="${f}"/></li></c:forEach></ul>
                    </c:if>
                  </div>
                </li>
              </c:forEach>
            </ol>
          </section>
        </c:forEach>
      </div>
    </c:if>
    <c:if test="${not empty r.sections}"><hr class="qx-rule"></c:if>
  </div>
  <c:if test="${not empty r.sections}">
  <div class="iq-board" id="iq-board">
    <c:forEach var="sec" items="${r.sections}">
      <section class="iq-sec" data-section="<c:out value='${sec.section}'/>">
        <h3 class="iq-sec-name"><c:out value="${sec.section}"/></h3>
        <ol class="iq-list">
          <c:forEach var="qv" items="${sec.questions}">
            <c:set var="q" value="${qv.question}"/>
            <c:choose>
              <c:when test="${q.origin == 'COMMON_PERSONALIZED'}"><c:set var="kind" value="personal"/><c:set var="kindLabel" value="공통 · 맞춤"/></c:when>
              <c:when test="${q.origin == 'COMMON_ORIGINAL' or q.origin == 'FIXED'}"><c:set var="kind" value="common"/><c:set var="kindLabel" value="공통"/></c:when>
              <c:when test="${q.origin == 'MANUAL' or empty q.questionType}"><c:set var="kind" value="manual"/><c:set var="kindLabel" value="직접 추가"/></c:when>
              <c:when test="${q.questionType == 'CUSTOM'}"><c:set var="kind" value="custom"/><c:set var="kindLabel" value="${q.typeLabel}"/></c:when>
              <c:when test="${q.questionType.claim}"><c:set var="kind" value="verify"/><c:set var="kindLabel" value="${q.questionType.label}"/></c:when>
              <c:otherwise><c:set var="kind" value="skill"/><c:set var="kindLabel" value="${q.questionType.label}"/></c:otherwise>
            </c:choose>
            <li class="q-row iq${empty q.answer ? '' : ' is-answered'}${q.checked ? ' is-checked' : ''}${q.mustAsk ? ' is-must' : ''}" data-id="${q.id}">
              <button type="button" class="iq-no js-q-check" title="물어본 질문 체크" aria-pressed="${q.checked}"></button>
              <span class="handle iq-handle" title="끌어서 옮기기"><svg class="i"><use href="#i-grip"/></svg></span>
              <div class="iq-main">
                <div class="iq-top">
                  <p class="iq-content"><c:out value="${q.content}"/></p>
                  <button type="button" class="must-btn js-q-must${q.mustAsk ? ' is-on' : ''}" aria-pressed="${q.mustAsk}" title="필수 질문">필수</button>
                  <span class="row-actions">
                    <button type="button" class="icon-btn js-q-edit" title="수정"><svg class="i"><use href="#i-pencil"/></svg></button>
                    <button type="button" class="icon-btn danger js-q-del" title="삭제"><svg class="i"><use href="#i-trash"/></svg></button>
                  </span>
                </div>
                <c:if test="${not empty qv.originalContent}"><p class="iq-origin"><span>원래 질문</span><c:out value="${qv.originalContent}"/></p></c:if>
                <c:if test="${kind != 'common'}">
                  <div class="iq-meta">
                    <span class="kind kind-${kind}"><c:out value="${kindLabel}"/></span>
                    <c:if test="${not empty qv.criterionName}"><span class="iq-crit"><c:out value="${qv.criterionName}"/></span></c:if>
                  </div>
                </c:if>
                <c:if test="${not empty qv.followUps}">
                  <ul class="iq-follow"><c:if test="${not empty q.questionType and q.questionType.experience}"><li class="iq-follow-cap">해봤다고 하면</li></c:if><c:forEach var="f" items="${qv.followUps}"><li><c:out value="${f}"/></li></c:forEach></ul>
                </c:if>
              </div>
              <div class="q-answer">
                <div class="q-answer-box">
                <textarea class="answer" rows="1" placeholder="답변 기록"
                          data-url="<c:url value='/results/${r.candidate.id}/questions/${q.id}/answer'/>"><c:out value="${q.answer}"/></textarea>
                <span class="save-state" aria-live="polite"></span>
                </div>
                <c:if test="${not empty q.answerExcerpt}"><p class="q-excerpt"><span>녹취</span><c:out value="${q.answerExcerpt}"/></p></c:if>
              </div>
            </li>
          </c:forEach>
        </ol>
        <button type="button" class="add-row js-q-add"><svg class="i"><use href="#i-plus"/></svg>질문 추가</button>
      </section>
    </c:forEach>
  </div>
  <div class="must-check">
    <button type="button" class="btn" id="must-check">필수질문 체크</button>
    <p class="must-result" id="must-result" aria-live="polite" hidden></p>
  </div>
  </c:if>
  </div>

  <%-- 면접 기록: 녹음본만 보고 만든 질문-답변 (녹음본 순서). 질문지 답변과 섞지 않는다 --%>
  <div class="qpane" data-qpane="record" id="record" hidden>
    <form class="aa rec-form" id="rec-form" method="post" data-partial="#questions" enctype="multipart/form-data" action="<c:url value='/results/${r.candidate.id}/record'/>"
          data-busy="녹음본으로 면접 기록을 만드는 중" data-busy-sub="녹음본이 길면 몇 분 걸립니다"
          <c:if test="${not empty record}">data-confirm="면접 기록을 새로 만듭니다. 지금 기록은 바뀝니다." hidden</c:if>>
      <textarea name="transcript" class="aa-text" rows="8" placeholder="녹음본 글을 붙여넣거나 파일을 끌어다 놓으세요"><c:out value="${r.candidate.transcript}"/></textarea>
      <div class="aa-dropped" hidden><svg class="i"><use href="#i-file"/></svg><span class="aa-name"></span><button type="button" class="icon-btn js-aa-clear" title="빼기"><svg class="i"><use href="#i-x"/></svg></button></div>
      <div class="aa-foot">
        <label class="aa-file"><input type="file" name="file" accept=".txt,.md,.doc,.docx,.pdf,.hwp,.hwpx"><svg class="i"><use href="#i-file"/></svg><span>파일 올리기</span></label>
        <c:if test="${not empty record}"><button type="button" class="btn btn-ghost btn-sm" data-open="rec-form">취소</button></c:if>
        <button type="submit" class="btn btn-primary btn-sm">${empty record ? '면접 기록 만들기' : '다시 만들기'}</button>
      </div>
    </form>
    <c:if test="${not empty record}">
      <div class="rec-head">
        <span class="rec-meta"><c:out value="${record.createdAt}"/></span>
        <c:if test="${not empty r.candidate.transcript}">
          <details class="rec-src"><summary>녹음본 보기</summary><pre><c:out value="${r.candidate.transcript}"/></pre></details>
        </c:if>
        <button type="button" class="btn btn-ghost btn-sm" data-open="rec-form">다시 만들기</button>
      </div>
      <c:forEach var="g" begin="0" end="1">
        <c:set var="items" value="${g == 0 ? record.interviewerItems : record.candidateItems}"/>
        <section class="rec-group">
          <h3 class="iq-sec-name">${g == 0 ? '면접관 질문' : '지원자 질문'} <span class="rec-count">${fn:length(items)}</span></h3>
          <c:if test="${empty items}"><p class="qpane-empty">없음</p></c:if>
          <ol class="rec-list">
            <c:forEach var="it" items="${items}" varStatus="st">
              <li class="rec-item">
                <p class="rec-q"><span class="rec-no">Q${st.count}</span><c:out value="${it.question}"/></p>
                <p class="rec-a"><c:out value="${it.answer}"/></p>
                <c:if test="${not empty it.excerpt}"><p class="q-excerpt"><span>녹취</span><c:out value="${it.excerpt}"/></p></c:if>
              </li>
            </c:forEach>
          </ol>
        </section>
      </c:forEach>
    </c:if>
  </div>
</section>
</c:if>

<%-- AI 의견 참고: 뽑는다면·안 뽑는다면 그 이유. 결정은 면접관이 한다 --%>
<c:if test="${qOpen}">
<section class="stage op" id="opinion">
  <header class="stage-head">
    <h2>AI 의견 참고</h2>
    <c:if test="${not empty r.opinion}"><span class="op-at"><c:out value="${r.opinion.askedAt}"/></span></c:if>
    <div class="stage-actions">
      <form method="post" action="<c:url value='/results/${r.candidate.id}/opinion'/>" data-partial="#opinion" data-busy="AI 의견을 받는 중">
        <button type="submit" class="btn btn-sm ${empty r.opinion ? 'btn-primary' : 'btn-ghost'}">${empty r.opinion ? 'AI 의견 받기' : '다시 받기'}</button>
      </form>
    </div>
  </header>
  <c:if test="${not empty r.opinion}">
    <div class="op-grid">
      <div class="op-col is-yes">
        <h3>뽑는다면</h3>
        <c:choose>
          <c:when test="${empty r.opinion.hireReasons}"><p class="op-empty">없음</p></c:when>
          <c:otherwise>
            <ul class="op-list">
              <c:forEach var="p" items="${r.opinion.hireReasons}">
                <li><p class="op-point"><c:out value="${p.point}"/></p><c:if test="${not empty p.basis}"><p class="op-basis"><c:out value="${p.basis}"/></p></c:if></li>
              </c:forEach>
            </ul>
          </c:otherwise>
        </c:choose>
      </div>
      <div class="op-col is-no">
        <h3>안 뽑는다면</h3>
        <c:choose>
          <c:when test="${empty r.opinion.noHireReasons}"><p class="op-empty">없음</p></c:when>
          <c:otherwise>
            <ul class="op-list">
              <c:forEach var="p" items="${r.opinion.noHireReasons}">
                <li><p class="op-point"><c:out value="${p.point}"/></p><c:if test="${not empty p.basis}"><p class="op-basis"><c:out value="${p.basis}"/></p></c:if></li>
              </c:forEach>
            </ul>
          </c:otherwise>
        </c:choose>
      </div>
    </div>
  </c:if>
</section>
</c:if>
  </c:otherwise>
</c:choose>
  </div>
</div>
</div>

<template id="tpl-q">
  <li class="q-row iq" data-id="">
    <button type="button" class="iq-no js-q-check" title="물어본 질문 체크" aria-pressed="false"></button>
    <span class="handle iq-handle" title="끌어서 옮기기"><svg class="i"><use href="#i-grip"/></svg></span>
    <div class="iq-main">
      <div class="iq-top">
        <p class="iq-content"></p>
        <button type="button" class="must-btn js-q-must" aria-pressed="false" title="필수 질문">필수</button>
        <span class="row-actions">
          <button type="button" class="icon-btn js-q-edit" title="수정"><svg class="i"><use href="#i-pencil"/></svg></button>
          <button type="button" class="icon-btn danger js-q-del" title="삭제"><svg class="i"><use href="#i-trash"/></svg></button>
        </span>
      </div>
      <div class="iq-meta"><span class="kind kind-manual">직접 추가</span></div>
    </div>
    <div class="q-answer">
      <textarea class="answer" rows="1" placeholder="답변 기록" data-url=""></textarea>
      <span class="save-state" aria-live="polite"></span>
    </div>
  </li>
</template>
<div class="toast" id="toast" role="status" hidden></div>

<script src="<c:url value='/js/extract.js'/>?v=${applicationScope.assetVersion}"></script>
</c:otherwise>
</c:choose>
<%@ include file="common/footer.jspf" %>

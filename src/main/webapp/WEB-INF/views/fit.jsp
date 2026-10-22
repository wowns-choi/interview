<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="${not empty r ? r.candidate.displayName : '포지션 적합도 검사'}"/>
<c:set var="activeTab" value="fit"/>
<%@ include file="common/header.jspf" %>

<c:choose>
<c:when test="${empty position}">
  <div class="empty-page">평가항목을 추출한 포지션이 없습니다. <a href="<c:url value='/positions'/>">평가 항목 추출</a></div>
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

<div class="split">
  <aside class="side">
    <a class="side-new ${empty r and not batch ? 'active' : ''}" href="<c:url value='/fit?position=${position.id}'/>"><svg class="i"><use href="#i-plus"/></svg>포지션 적합도 검사 (1인)</a>
    <a class="side-new ${empty r and batch ? 'active' : ''}" href="<c:url value='/fit?position=${position.id}&mode=batch'/>"><svg class="i"><use href="#i-plus"/></svg>포지션 적합도 검사 (다수)</a>
    <ul class="side-list" id="cand-list" data-url="<c:url value='/positions/${position.id}/candidates'/>"
        data-current="${empty r ? '' : r.candidate.id}" data-current-status="${empty r ? '' : r.candidate.status}">
      <c:forEach var="row" items="${candidates}">
        <li class="side-pos">
          <a class="side-item side-cand ${not empty r and r.candidate.id == row.candidateId ? 'active' : ''}" href="<c:url value='/results/${row.candidateId}'/>"
             data-in-progress="${row.inProgress}">
            <strong><c:out value="${row.displayName}"/></strong>
            <c:choose>
              <c:when test="${row.inProgress}"><span class="side-state is-progress">${row.statusLabel}</span></c:when>
              <c:when test="${row.state == 'RANKED' or row.state == 'EXCLUDED'}"><span class="side-score">${row.score.totalText}<small> / ${row.score.max}</small></span></c:when>
              <c:when test="${row.state == 'STALE'}"><span class="side-state is-stale">재검사 필요</span></c:when>
              <c:when test="${row.status == 'FAILED'}"><span class="side-state is-failed">실패</span></c:when>
              <c:otherwise><span class="side-state">${row.statusLabel}</span></c:otherwise>
            </c:choose>
          </a>
          <c:if test="${not row.inProgress}"><button type="button" class="icon-btn side-rename" title="이름 바꾸기"><svg class="i"><use href="#i-pencil"/></svg></button></c:if>
        </li>
      </c:forEach>
    </ul>
  </aside>

  <div class="detail">
<c:choose>
  <c:when test="${empty r and batch}">
    <section class="stage" id="fit">
      <header class="stage-head"><h2>포지션 적합도 검사 (다수)</h2></header>
      <form method="post" enctype="multipart/form-data" action="<c:url value='/positions/${position.id}/batch'/>"
            data-busy="이력서를 읽는 중" id="batch-form">
        <div class="bz-grid" id="bz-grid">
          <c:forEach var="n" begin="1" end="3">
            <div class="bz">
              <label class="dropzone">
                <input type="file" name="applicant-${n}" accept=".pdf,.docx,.pptx,.hwp,.hwpx" multiple>
                <span class="bz-no">지원자 ${n}</span>
                <span class="dz-icon" aria-hidden="true"><svg class="i"><use href="#i-file"/></svg></span>
                <span class="dz-text">이력서·포트폴리오를 끌어다 놓거나 클릭</span>
              </label>
              <button type="button" class="icon-btn bz-del" title="빼기"><svg class="i"><use href="#i-trash"/></svg></button>
            </div>
          </c:forEach>
        </div>
        <button type="button" class="add-row bz-add" id="bz-add"><svg class="i"><use href="#i-plus"/></svg>지원자 추가</button>
        <button type="submit" class="btn btn-primary btn-lg bz-submit">개인정보 가리기 및 포지션 적합도 검사 일괄 진행</button>
      </form>
    </section>
  </c:when>
  <c:when test="${empty r}">
    <section class="stage" id="fit">
      <header class="stage-head"><h2>포지션 적합도 검사 (1인)</h2></header>
      <form method="post" enctype="multipart/form-data" action="<c:url value='/positions/${position.id}/resume'/>"
            data-busy="개인정보를 가리는 중" class="resume-form">
        <label class="dropzone" id="dropzone">
          <input type="file" id="resume" name="resume" accept=".pdf,.docx,.pptx,.hwp,.hwpx" multiple required>
          <span class="dz-icon" aria-hidden="true"><svg class="i"><use href="#i-file"/></svg></span>
          <span class="dz-text" id="dz-text">이력서·포트폴리오를 끌어다 놓거나 클릭</span>
        </label>
        <button type="submit" class="btn btn-primary btn-lg">개인정보 가리기</button>
      </form>
    </section>
  </c:when>
  <c:otherwise>
<section class="stage" id="fit">
  <header class="stage-head">
    <h2 data-cand-name><c:out value="${r.candidate.displayName}"/></h2>
    <c:set var="score" value="${r.score}"/>
    <c:if test="${not empty score}">
      <span class="score-pill${score.complete ? '' : ' is-stale'}" title="${score.complete ? '' : '검사 뒤에 평가항목이 바뀌었습니다. 다시 검사하세요.'}">${score.totalText}<small> / ${score.max}점</small></span>
    </c:if>
  </header>
        <c:set var="st" value="${r.candidate.status}"/>
        <c:choose>
          <c:when test="${st == 'QUEUED' or st == 'MASKING' or st == 'ANALYZING'}">
            <div class="progress-box" id="progress-box">
              <span class="spinner"></span>
              <strong>${st == 'QUEUED' ? '차례를 기다리는 중' : st == 'MASKING' ? '개인정보를 가리는 중' : '포지션 적합도를 검사하는 중'}</strong>
            </div>
          </c:when>
          <c:when test="${empty r.candidate.maskedText}">
            <div class="flash flash-error"><c:out value="${r.candidate.errorMessage}"/></div>
            <form method="post" action="<c:url value='/results/${r.candidate.id}/delete'/>" data-confirm="이 지원자를 삭제할까요?">
              <button type="submit" class="btn btn-ghost btn-sm btn-danger-text">삭제</button>
            </form>
          </c:when>
          <c:otherwise>
        <div class="review">
          <form method="post" action="<c:url value='/results/${r.candidate.id}/check'/>" data-busy="이력서를 검사하는 중" id="review-form"
                <c:if test="${not empty r.sections}">data-confirm="다시 검사하면 지금 질문지가 지워집니다. 계속할까요?"</c:if>>
            <div class="mask-view" id="mask-view" hidden></div>
            <textarea name="maskedText" id="masked-text" class="crit-text"><c:out value="${r.candidate.maskedText}"/></textarea>
          </form>
          <div class="review-side">
            <button type="submit" form="review-form" class="btn btn-primary btn-lg">${r.fitChecked ? '다시 검사' : '적합도 검사'}</button>
            <div class="review-tools">
              <button type="button" class="btn btn-ghost btn-sm" id="mask-edit">직접 고치기</button>
              <button type="button" class="btn btn-ghost btn-sm" id="mask-undo" hidden>되돌리기</button>
              <form method="post" action="<c:url value='/results/${r.candidate.id}/delete'/>" data-confirm="이 이력서의 검사 결과와 질문지를 삭제할까요?">
                <button type="submit" class="btn btn-ghost btn-sm btn-danger-text">삭제</button>
              </form>
            </div>
          </div>
        </div>

        <c:if test="${r.candidate.status == 'FAILED' and not r.fitChecked}">
          <div class="flash flash-error">검사하지 못했습니다. <c:out value="${r.candidate.errorMessage}"/></div>
        </c:if>
          </c:otherwise>
        </c:choose>

        <c:set var="pf" value="${r.profile}"/>
        <c:set var="cev" value="${r.careerEval}"/>
        <c:set var="fit" value="${r.fit}"/>
        <c:if test="${r.fitChecked and ((not empty pf and pf.filled) or not empty position.requiredExperience)}">
          <section class="profile${justChecked ? ' is-stamping' : ''}">
            <c:if test="${pf.personFilled}">
              <div class="pf-person">
                <div class="pf-who">
                  <span class="pf-avatar"><c:out value="${fn:substring(r.candidate.displayName, 0, 1)}"/></span>
                  <div>
                    <div class="pf-name" data-cand-name><c:out value="${r.candidate.displayName}"/></div>
                    <div class="pf-tags">
                      <c:if test="${not empty pf.gender}"><span><c:out value="${pf.gender}"/></span></c:if>
                      <c:if test="${not empty pf.birth}"><span><c:out value="${pf.birth}"/></span></c:if>
                    </div>
                  </div>
                </div>
                <div class="pf-contacts">
                  <c:if test="${not empty pf.phone}">
                    <div class="pf-field"><svg class="i"><use href="#i-phone"/></svg><div><small>전화번호</small><span><c:out value="${pf.phone}"/></span></div></div>
                  </c:if>
                  <c:if test="${not empty pf.email}">
                    <div class="pf-field"><svg class="i"><use href="#i-mail"/></svg><div><small>이메일</small><span><c:out value="${pf.email}"/></span></div></div>
                  </c:if>
                  <c:if test="${not empty pf.address}">
                    <div class="pf-field"><svg class="i"><use href="#i-pin"/></svg><div><small>주소</small><span><c:out value="${pf.address}"/></span></div></div>
                  </c:if>
                  <c:if test="${not empty pf.link}">
                    <div class="pf-field"><svg class="i"><use href="#i-link"/></svg><div><small>포트폴리오</small>
                      <c:choose>
                        <c:when test="${fn:startsWith(pf.link, 'http://') or fn:startsWith(pf.link, 'https://')}">
                          <a href="<c:out value='${pf.link}'/>" target="_blank" rel="noopener noreferrer"><c:out value="${pf.link}"/></a>
                        </c:when>
                        <c:otherwise><span><c:out value="${pf.link}"/></span></c:otherwise>
                      </c:choose>
                    </div></div>
                  </c:if>
                </div>
              </div>
            </c:if>

            <div class="pf-cards">
              <c:if test="${not empty pf.careers or not empty pf.totalCareer or not empty position.requiredExperience}">
                <div class="pf-card pf-wide">
                  <c:set var="gauge" value="${r.careerGauge}"/>
                  <div class="pf-card-head">
                    <h3>경력<c:if test="${not empty fit.career}"><span class="stamp ${fit.career ? 'is-met' : 'is-unmet'}">${fit.career ? '충족' : '미충족'}</span></c:if></h3>
                    <c:choose>
                      <c:when test="${not empty cev and not empty gauge}"><strong class="pf-big" title="<c:out value='${cev.totalReason}'/>">총 <c:out value="${gauge.actualText}"/></strong></c:when>
                      <c:when test="${not empty pf.totalCareer}"><strong class="pf-big"><c:out value="${pf.totalCareer}"/></strong></c:when>
                      <c:when test="${not empty gauge}"><strong class="pf-big">총 <c:out value="${gauge.actualText}"/></strong></c:when>
                    </c:choose>
                    <c:if test="${not empty gauge.relevantMonths}">
                      <span class="pf-rel" title="<c:out value='${cev.relevantReason}'/>">포지션 관련 <c:out value="${gauge.relevantText}"/></span>
                    </c:if>
                    <span class="pf-end">
                      <c:choose>
                        <c:when test="${empty gauge.markPercent}">
                          <c:if test="${not empty position.requiredExperience}"><span class="pf-req">공고 <c:out value="${position.requiredExperience}"/></span></c:if>
                        </c:when>
                        <c:when test="${gauge.over}"><span class="gauge-diff is-over">공고보다 <c:out value="${gauge.diffText}"/> 더</span></c:when>
                      </c:choose>
                    </span>
                  </div>
                  <c:if test="${not empty gauge}">
                    <div class="gauge">
                      <div class="gauge-bar">
                        <span class="gauge-total" style="width: ${gauge.totalPercent}%"></span>
                        <c:if test="${not empty gauge.relevantPercent}"><span class="gauge-fill" style="width: ${gauge.relevantPercent}%"></span></c:if>
                        <c:if test="${not empty gauge.markPercent}"><i class="gauge-mark" style="left: ${gauge.markPercent}%"></i></c:if>
                      </div>
                      <c:if test="${not empty gauge.markPercent}">
                        <div class="gauge-labels">
                          <span class="gauge-req${gauge.markPercent >= 85 ? ' is-end' : ''}" style="left: ${gauge.markPercent}%">공고 <c:out value="${position.requiredExperience}"/></span>
                        </div>
                      </c:if>
                    </div>
                  </c:if>
                  <c:if test="${not empty pf.careers}">
                    <ul class="pf-list pf-timeline">
                      <c:forEach var="cr" items="${pf.careers}">
                        <c:if test="${empty r.careerEval or r.careerEval.counts(cr.alias)}">
                        <li>
                          <div class="pf-item-main">
                            <strong><c:out value="${cr.company}"/></strong>
                            <c:if test="${not empty cr.department or not empty cr.position}">
                              <span class="pf-sub"><c:out value="${cr.department}"/><c:if test="${not empty cr.department and not empty cr.position}"> · </c:if><c:out value="${cr.position}"/></span>
                            </c:if>
                            <c:if test="${not empty cr.period or not empty cr.duration}">
                              <span class="pf-when"><c:out value="${cr.period}"/><c:if test="${not empty cr.duration}"> <em><c:out value="${cr.duration}"/></em></c:if></span>
                            </c:if>
                          </div>
                        </li>
                        </c:if>
                      </c:forEach>
                    </ul>
                  </c:if>
                </div>
              </c:if>

              <c:if test="${not empty pf.education}">
                <div class="pf-card">
                  <div class="pf-card-head">
                    <h3>학력<c:if test="${not empty fit.education}"><span class="stamp ${fit.education ? 'is-met' : 'is-unmet'}">${fit.education ? '충족' : '미충족'}</span></c:if></h3>
                    <c:if test="${not empty cev.finalEducation}"><strong class="pf-big" title="<c:out value='${cev.educationReason}'/>">최종 <c:out value="${cev.finalEducation}"/></strong></c:if>
                    <c:if test="${not empty cev.requiredEducation}"><span class="pf-req">공고 <c:out value="${cev.requiredEducation}"/></span></c:if>
                  </div>
                  <ul class="pf-list">
                    <c:forEach var="ed" items="${pf.education}">
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
                </div>
              </c:if>

              <c:if test="${not empty pf.certificates}">
                <div class="pf-card">
                  <div class="pf-card-head"><h3>자격증</h3></div>
                  <ul class="pf-list">
                    <c:forEach var="ce" items="${pf.certificates}">
                      <li>
                        <div class="pf-item-main">
                          <strong><c:out value="${ce.name}"/></strong>
                          <c:if test="${not empty ce.issuer}"><span class="pf-sub"><c:out value="${ce.issuer}"/></span></c:if>
                        </div>
                        <c:if test="${not empty ce.date}"><span class="pf-when"><c:out value="${ce.date}"/></span></c:if>
                      </li>
                    </c:forEach>
                  </ul>
                </div>
              </c:if>
            </div>

            <c:set var="tl" value="${r.timeline}"/>
            <c:if test="${not empty tl}">
              <div class="pf-card tl-card">
                <div class="pf-card-head"><h3>타임라인</h3></div>
                <div class="tl-scroll">
                <div class="tl" style="min-width: calc(var(--tl-name) + ${tl.width}px)">
                  <div class="tl-grid" aria-hidden="true">
                    <c:forEach var="tick" items="${tl.ticks}"><i style="left: ${tick.left}%"></i></c:forEach>
                    <c:forEach var="gap" items="${tl.gaps}"><b style="left: ${gap.left}%; width: ${gap.width}%"></b></c:forEach>
                  </div>
                  <c:forEach var="lane" items="${tl.lanes}">
                    <div class="tl-lane is-${lane.tone}">
                      <span class="tl-name"><c:out value="${lane.name}"/></span>
                      <div class="tl-rows">
                        <c:if test="${empty lane.rows}"><div class="tl-row"></div></c:if>
                        <c:forEach var="row" items="${lane.rows}">
                          <div class="tl-row">
                            <c:forEach var="bar" items="${row}">
                              <span class="tl-bar${empty bar.side ? '' : ' is-out is-out-'.concat(bar.side)}" style="left: ${bar.left}%; width: ${bar.width}%"
                                    title="<c:out value='${bar.label}'/><c:if test='${not empty bar.detail}'> · <c:out value='${bar.detail}'/></c:if> · <c:out value='${bar.periodText}'/>">
                                <span class="tl-text"><strong><c:out value="${bar.label}"/></strong><small><c:out value="${bar.periodText}"/></small></span>
                              </span>
                            </c:forEach>
                          </div>
                        </c:forEach>
                      </div>
                    </div>
                  </c:forEach>
                  <div class="tl-lane is-gap">
                    <span class="tl-name">기록 없음</span>
                    <div class="tl-rows">
                      <div class="tl-row">
                        <c:forEach var="gap" items="${tl.gaps}">
                          <span class="tl-bar" style="left: ${gap.left}%; width: ${gap.width}%" title="이력서상 기록 없음 · <c:out value='${gap.periodText}'/> (<c:out value='${gap.lengthText}'/>)">
                            <strong><c:out value="${gap.lengthText}"/></strong><small><c:out value="${gap.periodText}"/></small>
                          </span>
                        </c:forEach>
                      </div>
                    </div>
                  </div>
                  <div class="tl-axis">
                    <span class="tl-name"></span>
                    <div class="tl-rows"><c:forEach var="tick" items="${tl.ticks}"><span style="left: ${tick.left}%">${tick.year}</span></c:forEach></div>
                  </div>
                </div>
                </div>
                <%-- PDF에서는 그래프 대신 이 목록만 보인다 --%>
                <table class="tl-list">
                  <c:forEach var="e" items="${tl.entries}">
                    <tr class="is-${e.tone}"><th><c:out value="${e.kind}"/></th><td><c:out value="${e.label}"/><c:if test="${not empty e.detail}"><small> · <c:out value="${e.detail}"/></small></c:if></td><td><c:out value="${e.periodText}"/></td></tr>
                  </c:forEach>
                </table>
              </div>
            </c:if>
          </section>
        </c:if>

        <c:if test="${r.fitChecked}">
          <div class="ev-list">
            <c:forEach var="cv" items="${r.criteria}" varStatus="cs">
              <c:set var="evKind" value="${cv.checked ? cv.match : 'UNCHECKED'}"/>
              <article class="ev ev-${evKind}">
                <header class="ev-head">
                  <span class="ev-no">${cs.count < 10 ? '0' : ''}${cs.count}</span>
                  <h4><c:out value="${cv.criterion.name}"/></h4>
                  <span class="ev-tag"><i class="hb ev-${evKind}"></i>${evKind == 'DIRECT' ? '직접 근거' : evKind == 'INDIRECT' ? '간접 근거' : evKind == 'NONE' ? '근거 없음' : '검사 전'}</span>
                </header>
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
                            <c:if test="${ev.verifiedYn != 'Y'}"><p class="warn-text">이력서 원문에서 찾지 못한 문장입니다</p></c:if>
                            <p class="interp"><c:out value="${ev.interpretation}"/></p>
                          </div>
                        </c:forEach>
                      </c:otherwise>
                    </c:choose>
                  </div>
                </c:if>
              </article>
            </c:forEach>
          </div>
        </c:if>
</section>

<%-- 면접 질문 추출: 유형을 골라 여러 번 추출해 쌓고, 공통 질문과 합치면 질문지가 된다 --%>
<c:set var="qOpen" value="${r.fitChecked}"/>
<section class="stage qx ${qOpen ? '' : 'is-locked'}" id="extract">
  <header class="stage-head">
    <h2>면접 질문 추출</h2>
  </header>

  <c:if test="${qOpen}">
    <form class="qx-bar" method="post" action="<c:url value='/results/${r.candidate.id}/questions/generate'/>" data-busy="면접 질문을 추출하는 중">
      <div class="qx-group">
        <span class="qx-cap">이력서에 쓴 것</span>
        <label class="qx-type"><input type="checkbox" name="types" value="VERIFY" checked><span>거짓말 판독기</span></label>
        <label class="qx-type"><input type="checkbox" name="types" value="DEPTH" checked><span>어디까지 알고 한 거야?</span></label>
        <label class="qx-type"><input type="checkbox" name="types" value="CHOICE" checked><span>그 기술 왜 선택했어?</span></label>
      </div>
      <div class="qx-group">
        <span class="qx-cap">포지션 요구 기술</span>
        <label class="qx-type"${r.experienceAskable ? '' : ' title="모든 평가항목에 직접 근거가 있습니다"'}><input type="checkbox" name="types" value="EXPERIENCE" ${r.experienceAskable ? 'checked' : 'disabled'}><span>해본 적 있어?</span></label>
      </div>
      <button type="submit" class="btn btn-primary">${r.extractedCount + r.questionCount > 0 ? '더 추출' : '질문 추출'}</button>
    </form>

    <c:if test="${not empty r.extracted}">
      <div class="qx-pool" id="qx-pool">
        <div class="qx-pool-head">
          <h3>추출한 질문 <b>${r.extractedCount}</b></h3>
          <form method="post" action="<c:url value='/results/${r.candidate.id}/questions/merge'/>" data-busy="질문지를 만드는 중">
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
                      <ul class="iq-follow"><c:if test="${not q.questionType.claim}"><li class="iq-follow-cap">해봤다고 하면</li></c:if><c:forEach var="f" items="${qv.followUps}"><li><c:out value="${f}"/></li></c:forEach></ul>
                    </c:if>
                  </div>
                </li>
              </c:forEach>
            </ol>
          </section>
        </c:forEach>
      </div>
    </c:if>
  </c:if>
</section>

<%-- 면접 질문지 (공통 질문과 합친 것) --%>
<c:if test="${qOpen and not empty r.sections}">
<section class="stage" id="questions">
  <header class="stage-head">
    <h2>면접 질문지</h2>
    <div class="stage-actions">
      <button type="button" class="btn btn-ghost btn-sm" data-open="aa-panel">자동 답변 채우기</button>
      <button type="button" class="btn btn-primary btn-sm" id="pdf-btn" data-filename="면접질문지_<c:out value='${r.candidate.displayName}'/>"><svg class="i"><use href="#i-download"/></svg>PDF</button>
    </div>
  </header>
  <form class="aa" id="aa-panel" method="post" enctype="multipart/form-data" action="<c:url value='/results/${r.candidate.id}/answers/auto'/>" data-busy="녹취록으로 답변을 채우는 중" hidden>
    <textarea name="transcript" class="aa-text" rows="6" placeholder="면접 녹취록을 붙여넣으세요"></textarea>
    <div class="aa-foot">
      <label class="btn btn-ghost btn-sm aa-file"><input type="file" name="file" accept=".txt,.md,.docx,.pdf,.hwp,.hwpx"><span>파일 선택</span></label>
      <span class="aa-name"></span>
      <button type="button" class="btn btn-ghost btn-sm" data-open="aa-panel">취소</button>
      <button type="submit" class="btn btn-primary btn-sm">답변 채우기</button>
    </div>
  </form>
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
              <c:when test="${q.questionType.claim}"><c:set var="kind" value="verify"/><c:set var="kindLabel" value="${q.questionType.label}"/></c:when>
              <c:otherwise><c:set var="kind" value="skill"/><c:set var="kindLabel" value="${q.questionType.label}"/></c:otherwise>
            </c:choose>
            <li class="q-row iq${empty q.answer ? '' : ' is-answered'}${q.checked ? ' is-checked' : ''}" data-id="${q.id}">
              <button type="button" class="iq-no js-q-check" title="물어본 질문 체크" aria-pressed="${q.checked}"></button>
              <span class="handle iq-handle" title="끌어서 옮기기"><svg class="i"><use href="#i-grip"/></svg></span>
              <div class="iq-main">
                <div class="iq-top">
                  <p class="iq-content"><c:out value="${q.content}"/></p>
                  <span class="row-actions">
                    <button type="button" class="icon-btn js-q-edit" title="수정"><svg class="i"><use href="#i-pencil"/></svg></button>
                    <button type="button" class="icon-btn danger js-q-del" title="삭제"><svg class="i"><use href="#i-trash"/></svg></button>
                  </span>
                </div>
                <c:if test="${not empty qv.originalContent}"><p class="iq-origin"><span>원래 질문</span><c:out value="${qv.originalContent}"/></p></c:if>
                <c:if test="${kind != 'common'}">
                  <div class="iq-meta">
                    <span class="kind kind-${kind}">${kindLabel}</span>
                    <c:if test="${not empty qv.criterionName}"><span class="iq-crit"><c:out value="${qv.criterionName}"/></span></c:if>
                  </div>
                </c:if>
                <c:if test="${not empty qv.followUps}">
                  <ul class="iq-follow"><c:if test="${not empty q.questionType and not q.questionType.claim}"><li class="iq-follow-cap">해봤다고 하면</li></c:if><c:forEach var="f" items="${qv.followUps}"><li><c:out value="${f}"/></li></c:forEach></ul>
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
</section>
</c:if>

<%-- AI 의견 참고: 뽑는다면·안 뽑는다면 그 이유. 결정은 면접관이 한다 --%>
<c:if test="${qOpen}">
<section class="stage op" id="opinion">
  <header class="stage-head">
    <h2>AI 의견 참고</h2>
    <c:if test="${not empty r.opinion}"><span class="op-at"><c:out value="${r.opinion.askedAt}"/></span></c:if>
    <div class="stage-actions">
      <form method="post" action="<c:url value='/results/${r.candidate.id}/opinion'/>" data-busy="AI 의견을 받는 중">
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

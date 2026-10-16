<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="${empty position ? '면접 질문 추출하기' : position.title}"/>
<c:set var="activeTab" value="extract"/>
<%@ include file="common/header.jspf" %>

<div class="ws" id="ws"
     data-position-base="<c:if test='${not empty position}'><c:url value='/positions/${position.id}'/></c:if>"
     data-result-base="<c:if test='${not empty r}'><c:url value='/results/${r.candidate.id}'/></c:if>">

<c:if test="${not empty r}">
  <header class="print-head">
    <h1><c:out value="${r.candidate.displayName}"/></h1>
    <span><c:out value="${position.title}"/></span>
  </header>
</c:if>

<%-- ① 평가항목 추출 --%>
<section class="stage" id="criteria">
  <header class="stage-head">
    <span class="stage-no">1</span>
    <h2>평가항목 추출</h2>
    <c:if test="${not empty position}">
      <span class="stage-title"><c:out value="${position.title}"/></span>
      <div class="stage-actions">
        <a class="btn btn-ghost btn-sm" href="<c:url value='/extract'/>"><svg class="i"><use href="#i-plus"/></svg>새 포지션</a>
        <form method="post" action="<c:url value='/positions/${position.id}/criteria/extract'/>" data-busy="평가항목을 추출하는 중"
              data-confirm="평가항목을 새로 추출할까요? 지금 평가항목과 이 포지션의 검사 결과가 바뀝니다.">
          <button type="submit" class="btn btn-ghost btn-sm">다시 추출</button>
        </form>
        <form method="post" action="<c:url value='/positions/${position.id}/delete'/>" data-confirm="이 포지션을 삭제할까요? 검사한 이력서와 질문지도 함께 삭제됩니다.">
          <button type="submit" class="btn btn-ghost btn-sm btn-danger-text">삭제</button>
        </form>
      </div>
    </c:if>
  </header>

  <div class="crit-flow">
    <c:choose>
      <c:when test="${empty position}">
        <form class="panel crit-in" id="crit-form" method="post" enctype="multipart/form-data" action="<c:url value='/extract'/>"
              data-busy="평가항목을 추출하는 중">
          <h3 class="panel-label">포지션 설명</h3>
          <textarea id="jdText" name="jdText" class="crit-text" placeholder="채용공고나 포지션 설명을 붙여 넣으세요"><c:out value="${jdText}"/></textarea>
          <div class="img-zone">
            <input type="file" id="files" name="files" accept="image/png,image/jpeg,image/webp,image/gif,application/pdf,.pdf,text/plain,.txt,.md" multiple hidden>
            <ul class="img-list" id="img-list"></ul>
            <button type="button" class="img-add" id="img-add"><svg class="i"><use href="#i-file"/></svg>파일 추가<span class="img-hint">이미지 · PDF · TXT</span></button>
            <p class="img-note" id="img-note"></p>
          </div>
          <button type="submit" class="btn btn-primary btn-lg">평가항목 추출</button>
        </form>
      </c:when>
      <c:otherwise>
        <section class="panel crit-in">
          <h3 class="panel-label">포지션 설명</h3>
          <c:if test="${not empty position.jdText}">
            <div class="crit-text crit-text-read"><c:out value="${position.jdText}"/></div>
          </c:if>
          <c:if test="${not empty imageIds}">
            <ul class="img-list">
              <c:forEach var="imgId" items="${imageIds}">
                <li><a href="<c:url value='/positions/${position.id}/images/${imgId}'/>" target="_blank" rel="noopener">
                  <img src="<c:url value='/positions/${position.id}/images/${imgId}'/>" alt="포지션 이미지"></a></li>
              </c:forEach>
            </ul>
          </c:if>
        </section>
      </c:otherwise>
    </c:choose>

    <div class="flow-arrow" aria-hidden="true"><svg class="i"><use href="#i-arrow-right"/></svg></div>

    <section class="panel crit-out">
      <h3 class="panel-label">평가항목<span class="count" id="crit-count">${fn:length(criteria)}</span></h3>
      <c:choose>
        <c:when test="${empty position}">
          <div class="empty-box">추출한 평가항목이 여기에 표시됩니다</div>
        </c:when>
        <c:otherwise>
          <ul class="crit-list" id="crit-list">
            <c:forEach var="c" items="${criteria}">
              <li class="crit" data-id="${c.id}" data-requirement="${c.requirement}">
                <button type="button" class="req req-${c.requirement} js-req" title="필수/우대 바꾸기">${c.requirement == 'MUST' ? '필수' : '우대'}</button>
                <div class="crit-body">
                  <p class="crit-name"><c:out value="${c.name}"/></p>
                  <p class="crit-desc"><c:out value="${c.description}"/></p>
                </div>
                <span class="row-actions">
                  <button type="button" class="icon-btn js-crit-edit" title="수정"><svg class="i"><use href="#i-pencil"/></svg></button>
                  <button type="button" class="icon-btn danger js-crit-del" title="삭제"><svg class="i"><use href="#i-trash"/></svg></button>
                </span>
              </li>
            </c:forEach>
          </ul>
          <button type="button" class="add-row" id="crit-add"><svg class="i"><use href="#i-plus"/></svg>평가항목 추가</button>
        </c:otherwise>
      </c:choose>
    </section>
  </div>
</section>

<%-- ② 포지션 적합도 검사 --%>
<c:set var="fitOpen" value="${not empty position and not empty criteria}"/>
<section class="stage ${fitOpen ? '' : 'is-locked'}" id="fit">
  <header class="stage-head">
    <span class="stage-no">2</span>
    <h2>포지션 적합도 검사</h2>
    <c:if test="${fitOpen and not empty candidates}">
      <nav class="people">
        <c:forEach var="cand" items="${candidates}">
          <a class="person ${not empty r and r.candidate.id == cand.candidateId ? 'active' : ''}" href="<c:url value='/results/${cand.candidateId}'/>#fit">
            <span class="dot dot-${cand.status}"></span><c:out value="${cand.displayName}"/></a>
        </c:forEach>
        <a class="person person-new ${empty r ? 'active' : ''}" href="<c:url value='/positions/${position.id}'/>#fit"><svg class="i"><use href="#i-plus"/></svg>이력서</a>
      </nav>
    </c:if>
  </header>

  <c:if test="${fitOpen}">
    <c:choose>
      <c:when test="${empty r}">
        <form method="post" enctype="multipart/form-data" action="<c:url value='/positions/${position.id}/resume'/>"
              data-busy="이력서를 검사하는 중" class="resume-form">
          <label class="dropzone" id="dropzone">
            <input type="file" id="resume" name="resume" accept="application/pdf,.pdf" required>
            <span class="dz-icon" aria-hidden="true"><svg class="i"><use href="#i-file"/></svg></span>
            <span class="dz-text" id="dz-text">이력서 PDF를 끌어다 놓거나 클릭</span>
          </label>
          <button type="submit" class="btn btn-primary btn-lg">적합도 검사</button>
        </form>
      </c:when>
      <c:otherwise>
        <div class="cand-head">
          <c:if test="${r.fitChecked}">
            <span class="sum sum-DIRECT">직접 근거 ${r.directCount}</span>
            <span class="sum sum-INDIRECT">간접 ${r.indirectCount}</span>
            <span class="sum sum-NONE">없음 ${r.noneCount}</span>
          </c:if>
          <div class="stage-actions">
            <form method="post" action="<c:url value='/results/${r.candidate.id}/check'/>" data-busy="이력서를 검사하는 중"
                  <c:if test="${not empty r.sections}">data-confirm="다시 검사하면 지금 질문지가 지워집니다. 계속할까요?"</c:if>>
              <button type="submit" class="btn ${r.hasUncheckedCriteria and r.fitChecked ? 'btn-primary' : 'btn-ghost'} btn-sm">다시 검사</button>
            </form>
            <form method="post" action="<c:url value='/results/${r.candidate.id}/delete'/>" data-confirm="이 이력서의 검사 결과와 질문지를 삭제할까요?">
              <button type="submit" class="btn btn-ghost btn-sm btn-danger-text">삭제</button>
            </form>
          </div>
        </div>

        <c:if test="${r.candidate.status == 'FAILED' and not r.fitChecked}">
          <div class="flash flash-error">검사하지 못했습니다. <c:out value="${r.candidate.errorMessage}"/></div>
        </c:if>

        <c:if test="${r.fitChecked}">
          <div class="ev-table">
            <c:forEach var="cv" items="${r.criteria}">
              <article class="ev-row ev-${cv.checked ? cv.match : 'UNCHECKED'}">
                <div class="ev-crit">
                  <div class="ev-title">
                    <span class="req req-${cv.criterion.requirement}">${cv.criterion.requirement == 'MUST' ? '필수' : '우대'}</span>
                    <h4><c:out value="${cv.criterion.name}"/></h4>
                  </div>
                  <div>
                    <span class="match match-${cv.checked ? cv.match : 'UNCHECKED'}">
                      <c:choose>
                        <c:when test="${not cv.checked}">검사 전</c:when>
                        <c:when test="${cv.match == 'DIRECT'}">직접 근거</c:when>
                        <c:when test="${cv.match == 'INDIRECT'}">간접 근거</c:when>
                        <c:otherwise>근거 없음</c:otherwise>
                      </c:choose>
                    </span>
                  </div>
                </div>
                <div class="ev-proof">
                  <c:choose>
                    <c:when test="${not cv.checked}"></c:when>
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
              </article>
            </c:forEach>
          </div>
        </c:if>
      </c:otherwise>
    </c:choose>
  </c:if>
</section>

<%-- ③ 면접 질문 추출 --%>
<c:set var="qOpen" value="${not empty r and r.fitChecked}"/>
<section class="stage ${qOpen ? '' : 'is-locked'}" id="questions">
  <header class="stage-head">
    <span class="stage-no">3</span>
    <h2>면접 질문 추출</h2>
    <c:if test="${qOpen and not empty r.sections}">
      <div class="stage-actions">
        <form method="post" action="<c:url value='/results/${r.candidate.id}/questions/generate'/>" data-busy="면접 질문을 만드는 중"
              data-confirm="질문을 새로 추출할까요? 지금 질문지의 수정 내용과 답변이 지워집니다.">
          <button type="submit" class="btn btn-ghost btn-sm">다시 추출</button>
        </form>
        <button type="button" class="btn btn-primary btn-sm" id="pdf-btn" data-filename="면접질문지_<c:out value='${r.candidate.displayName}'/>"><svg class="i"><use href="#i-download"/></svg>PDF</button>
      </div>
    </c:if>
  </header>

  <c:if test="${qOpen}">
    <c:choose>
      <c:when test="${empty r.sections}">
        <div class="q-cta">
          <c:if test="${r.candidate.status == 'FAILED'}">
            <div class="flash flash-error">질문을 만들지 못했습니다. <c:out value="${r.candidate.errorMessage}"/></div>
          </c:if>
          <form method="post" action="<c:url value='/results/${r.candidate.id}/questions/generate'/>" data-busy="면접 질문을 만드는 중">
            <button type="submit" class="btn btn-primary btn-lg">면접 질문 추출</button>
          </form>
        </div>
      </c:when>
      <c:otherwise>
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
                    <c:when test="${q.origin == 'MANUAL'}"><c:set var="kind" value="manual"/><c:set var="kindLabel" value="직접 추가"/></c:when>
                    <c:when test="${q.questionType == 'VERIFY'}"><c:set var="kind" value="verify"/><c:set var="kindLabel" value="경력 검증"/></c:when>
                    <c:otherwise><c:set var="kind" value="skill"/><c:set var="kindLabel" value="역량 검증"/></c:otherwise>
                  </c:choose>
                  <li class="q-row iq ${empty q.answer ? '' : 'is-answered'}" data-id="${q.id}">
                    <span class="handle iq-handle" title="끌어서 옮기기"><svg class="i"><use href="#i-grip"/></svg></span>
                    <div class="iq-main">
                      <div class="iq-top">
                        <p class="iq-content"><c:out value="${q.content}"/></p>
                        <span class="row-actions">
                          <button type="button" class="icon-btn js-q-edit" title="수정"><svg class="i"><use href="#i-pencil"/></svg></button>
                          <button type="button" class="icon-btn danger js-q-del" title="삭제"><svg class="i"><use href="#i-trash"/></svg></button>
                        </span>
                      </div>
                      <c:if test="${kind != 'common'}">
                        <div class="iq-meta">
                          <span class="kind kind-${kind}">${kindLabel}</span>
                          <c:if test="${not empty qv.criterionName}"><span class="iq-crit"><c:out value="${qv.criterionName}"/></span></c:if>
                        </div>
                      </c:if>
                      <c:if test="${not empty qv.followUps}">
                        <ul class="iq-follow"><c:forEach var="f" items="${qv.followUps}"><li><c:out value="${f}"/></li></c:forEach></ul>
                      </c:if>
                    </div>
                    <div class="q-answer">
                      <textarea class="answer" rows="1" placeholder="답변 기록"
                                data-url="<c:url value='/results/${r.candidate.id}/questions/${q.id}/answer'/>"><c:out value="${q.answer}"/></textarea>
                      <span class="save-state" aria-live="polite"></span>
                    </div>
                  </li>
                </c:forEach>
              </ol>
              <button type="button" class="add-row js-q-add"><svg class="i"><use href="#i-plus"/></svg>질문 추가</button>
            </section>
          </c:forEach>
        </div>
      </c:otherwise>
    </c:choose>
  </c:if>
</section>

<c:if test="${empty position and not empty recent}">
  <section class="recent">
    <h2>최근 포지션</h2>
    <table class="table">
      <thead><tr><th>포지션</th><th class="w-num">평가항목</th><th class="w-num">이력서</th><th class="w-date">일시</th></tr></thead>
      <tbody>
      <c:forEach var="p" items="${recent}">
        <tr class="row-link" data-href="<c:url value='/positions/${p.id}'/>">
          <td class="strong"><a href="<c:url value='/positions/${p.id}'/>" style="color:inherit"><c:out value="${p.title}"/></a></td>
          <td class="muted">${p.criteriaCount}</td>
          <td class="muted">${p.candidateCount}</td>
          <td class="muted">${fn:substring(fn:replace(p.createdAt, 'T', ' '), 0, 16)}</td>
        </tr>
      </c:forEach>
      </tbody>
    </table>
  </section>
</c:if>
</div>

<template id="tpl-crit">
  <li class="crit" data-id="" data-requirement="MUST">
    <button type="button" class="req req-MUST js-req" title="필수/우대 바꾸기">필수</button>
    <div class="crit-body"><p class="crit-name"></p><p class="crit-desc"></p></div>
    <span class="row-actions">
      <button type="button" class="icon-btn js-crit-edit" title="수정"><svg class="i"><use href="#i-pencil"/></svg></button>
      <button type="button" class="icon-btn danger js-crit-del" title="삭제"><svg class="i"><use href="#i-trash"/></svg></button>
    </span>
  </li>
</template>
<template id="tpl-q">
  <li class="q-row iq" data-id="">
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

<script src="<c:url value='/js/extract.js'/>"></script>
<%@ include file="common/footer.jspf" %>

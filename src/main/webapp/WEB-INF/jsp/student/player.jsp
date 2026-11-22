<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<c:set var="a" value="${v.attempt}"/>
<c:set var="ex" value="${v.exam}"/>
<t:layout title="${ex.title}" js="player.js" bare="true">
<c:url var="saveUrl" value="/api/attempts/${a.id}/answers"/>
<c:url var="submitUrl" value="/api/attempts/${a.id}/submit"/>
<c:url var="focusUrl" value="/api/attempts/${a.id}/focus-lost"/>
<c:url var="resultsUrl" value="/student/results"/>
<c:url var="reloadUrl" value="/student/attempts/${a.id}"/>
<div id="player" class="${ex.blockCopyPaste ? 'no-select' : ''}"
     data-session="<c:out value='${v.playerSession}'/>" data-ends-at="${a.endsAt.toEpochMilli()}" data-server-now="${v.serverNow}"
     data-save-url="${saveUrl}" data-submit-url="${submitUrl}" data-focus-url="${focusUrl}" data-results-url="${resultsUrl}"
     data-reload-url="${reloadUrl}" data-block-copy="${ex.blockCopyPaste}" data-max-focus="${ex.maxFocusLosses}"
     data-focus-count="${a.focusLossCount}" data-total="${v.questions.size()}">

    <div id="fs-overlay" class="fs-overlay d-none" role="dialog" aria-modal="true" aria-labelledby="fs-title">
        <div class="fs-box">
            <h2 class="h4" id="fs-title">Full screen is required</h2>
            <p>This exam must be written in full screen. Leaving full screen or switching to another window is recorded and reported to the examiner.</p>
            <button type="button" class="btn btn-primary btn-lg" id="fs-enter">Enter full screen and continue</button>
        </div>
    </div>
    <header class="player-header">
        <div class="container-xxl py-2 d-flex flex-wrap align-items-center justify-content-between gap-2">
            <div>
                <div class="fw-semibold"><c:out value="${ex.title}"/></div>
                <div class="small text-muted"><span id="answered-count">0</span> of ${v.questions.size()} answered
                    <span class="ms-2 save-state text-muted" id="save-state" aria-live="polite"></span></div>
            </div>
            <div class="d-flex align-items-center gap-3">
                <div class="text-end"><div class="small text-muted">Time left</div><div class="player-timer" id="timer" role="timer" aria-live="off">--:--</div></div>
                <button type="button" class="btn btn-success" id="btn-submit">Submit exam</button>
            </div>
        </div>
    </header>

    <div id="focus-warning" class="alert alert-warning rounded-0 mb-0 text-center d-none" role="alert"></div>
    <div id="offline-warning" class="alert alert-danger rounded-0 mb-0 text-center d-none" role="alert">
        Connection problem &mdash; your answers will be saved as soon as the connection returns. Keep this page open.</div>

    <div class="container-xxl py-4">
        <div class="row g-4">
            <div class="col-lg-9">
                <c:forEach items="${v.questions}" var="q" varStatus="st">
                    <section class="card q-card d-none" data-qid="${q.id}" data-type="${q.type}" data-index="${st.index}" aria-label="Question ${q.number}">
                        <div class="card-body p-4">
                            <div class="d-flex justify-content-between align-items-start mb-3">
                                <div class="text-muted small">Question ${q.number} of ${v.questions.size()} &middot; <c:out value="${fmt.num(q.marks)}"/> mark(s)</div>
                                <div class="form-check form-switch"><input class="form-check-input flag-toggle" type="checkbox" role="switch" id="flag${q.id}" ${q.flagged ? 'checked' : ''}>
                                    <label class="form-check-label small" for="flag${q.id}">Mark for review</label></div>
                            </div>
                            <c:if test="${q.text != '[Image question]'}"><div class="q-text mb-3 fs-5"><c:out value="${q.text}"/></div></c:if>
                            <c:if test="${not empty q.imagePath}"><p><t:attachment name="${q.imagePath}"/></p></c:if>

                            <c:choose>
                                <c:when test="${q.type == 'MCQ_SINGLE' or q.type == 'TRUE_FALSE'}">
                                    <c:forEach items="${q.options}" var="o">
                                        <label class="option-row d-flex align-items-center gap-2 w-100"><input class="form-check-input mt-0 ans-input" type="radio" name="q${q.id}" value="<c:out value='${o.key}'/>" ${q.selected.contains(o.key) ? 'checked' : ''}><span class="q-text"><c:out value="${o.text}"/><c:if test="${not empty o.imagePath}"><img class="q-image opt-image" alt="Option picture" src="<c:url value='/files/q/${o.imagePath}'/>"></c:if></span></label>
                                    </c:forEach>
                                </c:when>
                                <c:when test="${q.type == 'MCQ_MULTI'}">
                                    <div class="text-muted small mb-2">Select all that apply.</div>
                                    <c:forEach items="${q.options}" var="o">
                                        <label class="option-row d-flex align-items-center gap-2 w-100"><input class="form-check-input mt-0 ans-input" type="checkbox" name="q${q.id}" value="<c:out value='${o.key}'/>" ${q.selected.contains(o.key) ? 'checked' : ''}><span class="q-text"><c:out value="${o.text}"/><c:if test="${not empty o.imagePath}"><img class="q-image opt-image" alt="Option picture" src="<c:url value='/files/q/${o.imagePath}'/>"></c:if></span></label>
                                    </c:forEach>
                                </c:when>
                                <c:when test="${q.type == 'MATCHING'}">
                                    <div class="text-muted small mb-2">Match each item on the left with an item on the right.</div>
                                    <c:forEach items="${q.options}" var="o">
                                        <div class="matching-row d-flex flex-wrap align-items-center gap-2 mb-2" data-left="<c:out value='${o.key}'/>">
                                            <div class="flex-grow-1 option-row mb-0 q-text"><c:out value="${o.text}"/></div>
                                            <select class="form-select w-auto ans-input" aria-label="Match for <c:out value='${o.text}'/>">
                                                <option value="">Select...</option>
                                                <c:forEach items="${q.rightItems}" var="r"><option value="<c:out value='${r.key}'/>" ${q.pairs[o.key] == r.key ? 'selected' : ''}><c:out value="${r.text}"/></option></c:forEach>
                                            </select>
                                        </div>
                                    </c:forEach>
                                </c:when>
                                <c:when test="${q.type == 'ESSAY' or q.type == 'SHORT_ANSWER'}">
                                    <textarea class="form-control ans-input" rows="${q.type == 'ESSAY' ? 10 : 4}" maxlength="20000" aria-label="Your answer"><c:out value="${q.answer}"/></textarea>
                                </c:when>
                                <c:when test="${q.type == 'NUMERIC'}">
                                    <input class="form-control ans-input w-auto" type="text" inputmode="decimal" maxlength="40" autocomplete="off" aria-label="Your numeric answer" value="<c:out value='${q.answer}'/>">
                                </c:when>
                                <c:otherwise>
                                    <input class="form-control ans-input" type="text" maxlength="500" autocomplete="off" aria-label="Your answer" value="<c:out value='${q.answer}'/>">
                                </c:otherwise>
                            </c:choose>
                            <c:if test="${q.type != 'MATCHING'}"><button type="button" class="btn btn-link btn-sm px-0 mt-2 clear-answer">Clear answer</button></c:if>
                        </div>
                        <div class="card-footer bg-white d-flex justify-content-between">
                            <button type="button" class="btn btn-outline-secondary btn-prev">&laquo; Previous</button>
                            <button type="button" class="btn btn-primary btn-next">Next &raquo;</button>
                        </div>
                    </section>
                </c:forEach>
            </div>
            <aside class="col-lg-3">
                <div class="card position-sticky" style="top:5.5rem"><div class="card-body">
                    <h2 class="h6">Questions</h2>
                    <div class="d-flex flex-wrap gap-1" id="palette">
                        <c:forEach items="${v.questions}" var="q" varStatus="st">
                            <button type="button" class="btn btn-outline-secondary palette-btn" data-goto="${st.index}" data-qid="${q.id}" aria-label="Go to question ${q.number}">${q.number}</button>
                        </c:forEach>
                    </div>
                    <hr>
                    <div class="small text-muted d-flex flex-column gap-1">
                        <span><span class="badge palette-legend bg-success">&nbsp;</span> Answered</span>
                        <span><span class="badge palette-legend" style="background:#f59e0b">&nbsp;</span> Marked for review</span>
                        <span><span class="badge palette-legend border text-bg-light">&nbsp;</span> Not answered</span>
                    </div>
                </div></div>
            </aside>
        </div>
    </div>

    <div class="player-overlay" id="overlay-confirm" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
        <div class="bg-white text-dark rounded-3 p-4" style="max-width:440px">
            <h2 class="h5" id="confirm-title">Submit your exam?</h2>
            <p id="confirm-text" class="mb-3"></p>
            <div class="d-flex gap-2 justify-content-center">
                <button type="button" class="btn btn-outline-secondary" id="btn-cancel-submit">Keep working</button>
                <button type="button" class="btn btn-success" id="btn-confirm-submit">Submit now</button>
            </div>
        </div>
    </div>
    <div class="player-overlay" id="overlay-message" role="alertdialog" aria-modal="true">
        <div class="bg-white text-dark rounded-3 p-4" style="max-width:460px">
            <h2 class="h5" id="message-title"></h2>
            <p id="message-text" class="mb-3"></p>
            <a class="btn btn-primary d-none" id="message-link" href="#"></a>
        </div>
    </div>
</div>
</t:layout>

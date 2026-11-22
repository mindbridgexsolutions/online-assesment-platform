<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Review questions" active="${empty s.examId ? 'questions' : 'exams'}" js="upload-review.js">
    <style>@keyframes qblink { 0%,100% { background-color: #fff3cd; } 50% { background-color: #ffe066; } }
        .qcard.dupcard, .qcard:target { background: #fff3cd; outline: 2px solid #ffc107; animation: qblink 1s ease-in-out 8; }</style>
    <c:set var="draft" value="${s.status == 'PENDING' or (s.status == 'CONFIRMED' and empty s.examId)}"/>
    <c:set var="saved" value="${s.status == 'CONFIRMED' and empty s.examId}"/>
    <c:set var="m" value="${s.metrics}"/>
    <c:set var="attention" value="${m.needsReview + m.invalid}"/>
    <nav class="small mb-2"><a href="<c:url value='/teacher/questions'/>">Question bank</a> / <c:out value="${s.fileName}"/></nav>
    <h1 class="h3 page-title mb-1">Let&rsquo;s check your questions</h1>
    <p class="text-muted mb-3"><c:out value="${s.fileName}"/> &middot; <strong><c:out value="${s.subjectName}"/></strong>
        <c:choose><c:when test="${s.status == 'PENDING'}"> &middot; nothing is saved until you press a button below.</c:when><c:otherwise> &middot; <span class="badge text-bg-success">Saved paper</span> change it any way you like, then send it again.</c:otherwise></c:choose></p>

    <div class="d-flex flex-wrap gap-2 mb-3" id="chips">
        <button type="button" class="btn btn-sm btn-outline-secondary active" data-f="all">All questions <strong>${m.total}</strong></button>
        <button type="button" class="btn btn-sm btn-outline-success" data-f="ok">&#9989; Ready <strong>${m.eligible}</strong></button>
        <c:if test="${attention > 0}"><button type="button" class="btn btn-sm btn-outline-danger" data-f="fix">&#9888;&#65039; Need a look <strong>${attention}</strong></button></c:if>
        <c:if test="${m.duplicates > 0}"><button type="button" class="btn btn-sm btn-outline-warning" data-f="dup">&#9851;&#65039; Already in the bank <strong>${m.duplicates}</strong></button></c:if>
    </div>
    <c:if test="${attention > 0}"><div class="alert alert-warning py-2 small">Some questions need a quick fix (usually a missing correct answer). Press <strong>Edit</strong> on them, or leave them out. We never guess an answer for you.</div></c:if>

    <c:forEach items="${s.rows}" var="r">
        <c:set var="bad" value="${r.vstatus == 'INVALID' or r.vstatus == 'NEEDS_REVIEW'}"/>
        <c:set var="tag" value="${bad ? 'fix' : (r.status == 'DUPLICATE' ? 'dup' : 'ok')}"/>
        <div id="row-${r.rowNo}" class="card mb-2 qcard ${(r.status == 'DUPLICATE' and not r.excluded) ? 'dupcard ' : ''}${bad ? 'border-danger' : (r.status == 'DUPLICATE' ? 'border-warning' : '')}" data-f="${tag}" ${r.excluded ? 'style="opacity:.55"' : ''}>
            <div class="card-body py-3">
                <div class="d-flex justify-content-between align-items-start gap-2 flex-wrap">
                    <div class="fw-semibold"><span class="text-muted me-1">${r.rowNo}.</span><c:out value="${r.item.text}"/></div>
                    <div class="d-flex gap-1 align-items-center flex-wrap">
                        <c:choose>
                            <c:when test="${bad}"><span class="badge text-bg-danger">Needs a look</span></c:when>
                            <c:when test="${r.status == 'DUPLICATE'}"><span class="badge text-bg-warning">Already in the bank</span></c:when>
                            <c:when test="${r.excluded}"><span class="badge text-bg-secondary">Left out</span></c:when>
                            <c:otherwise><span class="badge text-bg-success">Ready</span></c:otherwise>
                        </c:choose>
                        <span class="badge text-bg-light border">${empty r.item.marks ? '1' : r.item.marks} mark(s)</span>
                    </div>
                </div>
                <c:if test="${not empty r.item.image}"><p class="mt-2 mb-1"><img class="q-image" alt="Question picture" src="<c:url value='/files/q/${r.item.image}'/>"></p></c:if>
                <ul class="list-unstyled mb-1 mt-2">
                    <c:forEach items="${r.item.options}" var="o" varStatus="st"><c:if test="${not empty o.text or not empty o.image}">
                        <li class="py-1 ${r.correctFlags[st.index] ? 'text-success fw-semibold' : ''}"><span class="me-1">${letters[st.index]}.</span><c:if test="${r.correctFlags[st.index]}"><span aria-label="correct">&#10003;</span> </c:if><c:out value="${o.text}"/>
                            <c:if test="${not empty o.image}"><img class="q-image opt-image" alt="Option picture" src="<c:url value='/files/q/${o.image}'/>"></c:if></li>
                    </c:if></c:forEach>
                </ul>
                <c:if test="${not empty r.message}"><div class="small text-${bad ? 'danger' : 'warning-emphasis'}"><c:out value="${r.message}"/>
                    <c:if test="${r.status == 'DUPLICATE' and fn:contains(r.message, 'question #')}">
                        <a class="fw-semibold" href="<c:url value='/teacher/questions?highlightQuestion=${fn:substringBefore(fn:substringAfter(r.message, "question #"), ")")}'/>">Show it in the Question bank &rarr;</a></c:if>
                    <c:if test="${r.status == 'DUPLICATE' and fn:contains(r.message, 'of this file')}">
                        <a class="fw-semibold" href="#row-${fn:substringBefore(fn:substringAfter(r.message, "as row "), " ")}">Jump to that question &darr;</a></c:if></div></c:if>
                <c:if test="${not empty r.item.warning}"><div class="small text-warning-emphasis"><c:out value="${r.item.warning}"/></div></c:if>
                <c:if test="${draft}">
                <div class="d-flex gap-2 mt-2 flex-wrap">
                    <a class="btn btn-sm btn-outline-primary" href="<c:url value='/teacher/uploads/${s.id}/items/${r.id}/edit'/>">Edit</a>
                    <c:if test="${r.status == 'DUPLICATE'}">
                        <form method="post" action="<c:url value='/teacher/uploads/${s.id}/items/${r.id}/keep'/>"><t:csrf/><input type="hidden" name="keep" value="${not r.keep}">
                            <button class="btn btn-sm ${r.keep ? 'btn-warning' : 'btn-outline-secondary'}" type="submit">${r.keep ? 'Adding it anyway (undo)' : 'Add it anyway'}</button></form>
                    </c:if>
                    <c:if test="${not bad}">
                        <form method="post" action="<c:url value='/teacher/uploads/${s.id}/exclude'/>"><t:csrf/><input type="hidden" name="ids" value="${r.id}"><input type="hidden" name="excluded" value="${not r.excluded}">
                            <button class="btn btn-sm btn-outline-secondary" type="submit">${r.excluded ? 'Include it' : 'Leave it out'}</button></form>
                    </c:if>
                    <form method="post" action="<c:url value='/teacher/uploads/${s.id}/items/${r.id}/delete'/>"><t:csrf/><button class="btn btn-sm btn-outline-danger" type="submit" data-confirm="Remove this question?">Remove</button></form>
                </div></c:if>
            </div>
        </div>
    </c:forEach>

    <c:if test="${draft}">
    <details class="card mb-3"><summary class="card-body py-2 small text-muted" style="cursor:pointer">Prefer editing in Word?</summary>
        <div class="card-body pt-0">
            <p class="small text-muted mb-2">Download this list as a Word file (pictures included), change it, then upload it back. It replaces what you see here.</p>
            <form method="post" enctype="multipart/form-data" class="d-flex flex-wrap gap-2 align-items-center" action="<c:url value='/teacher/uploads/${s.id}/replace'><c:param name='_csrf' value='${_csrf.token}'/></c:url>">
                <a class="btn btn-sm btn-outline-primary" href="<c:url value='/teacher/uploads/${s.id}/download.docx'/>">Download as Word</a>
                <input class="form-control form-control-sm w-auto" type="file" name="file" accept=".docx" required>
                <button class="btn btn-sm btn-outline-secondary" type="submit" data-confirm="Replace every question here with the ones in your Word file?">Upload edited file</button>
            </form>
        </div>
    </details>
    </c:if>

    <div class="card sticky-bottom shadow-sm"><div class="card-body d-flex flex-wrap gap-2 align-items-center">
        <c:if test="${draft}">
            <c:if test="${not saved}">
            <form method="post" action="<c:url value='/teacher/uploads/${s.id}/merge-to-bank'/>"><t:csrf/>
                <button class="btn btn-success" type="submit" ${m.eligible == 0 ? 'disabled' : ''}>Add ${m.eligible} to Question bank</button></form>
            </c:if>
            <a class="btn btn-primary ${m.eligible == 0 ? 'disabled' : ''}" href="<c:url value='/teacher/uploads/${s.id}/publish'/>">Send to students &rarr;</a>
            <a class="btn btn-outline-secondary" href="<c:url value='/teacher/uploads/${s.id}/add-questions'/>">+ Add from Question bank</a>
            <form method="post" class="ms-auto" action="<c:url value='/teacher/uploads/${s.id}/discard'/>"><t:csrf/><button class="btn btn-link text-danger" type="submit" data-confirm="${saved ? 'Delete this saved paper? Your Question bank and exams stay as they are.' : 'Throw this upload away? Nothing will be saved.'}">${saved ? 'Delete paper' : 'Throw away'}</button></form>
        </c:if>
        <c:if test="${not draft}">
            <a class="btn btn-outline-secondary" href="<c:url value='/teacher/questions'/>">Back to Question bank</a>
        </c:if>
    </div></div>
</t:layout>

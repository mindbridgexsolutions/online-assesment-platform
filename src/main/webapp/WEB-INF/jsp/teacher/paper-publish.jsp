<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Send to students" active="questions" js="paper-publish.js">
    <nav class="small mb-2"><a href="<c:url value='/teacher/questions'/>">Question bank</a> / <a href="<c:url value='/teacher/uploads/${s.id}'/>"><c:out value="${s.fileName}"/></a> / Send to students</nav>
    <h1 class="h3 page-title mb-1">Send to students</h1>
    <p class="text-muted">${s.metrics.eligible} question(s) from <strong><c:out value="${s.fileName}"/></strong> become an exam. Pick the students, set the time, and they can start right away.</p>
    <form method="post" action="<c:url value='/teacher/uploads/${s.id}/publish'/>"><t:csrf/>
        <div class="card mb-3"><div class="card-body row g-3">
            <div class="col-md-8"><label class="form-label" for="title">Exam name</label><input class="form-control" id="title" name="title" maxlength="200" required value="<c:out value='${defaultTitle}'/>"></div>
            <div class="col-md-4"><label class="form-label" for="dur">Minutes to answer</label><input class="form-control" id="dur" type="number" name="durationMinutes" min="1" max="600" value="30" required></div>
            <div class="col-md-6"><label class="form-label" for="st">Opens</label><input class="form-control" id="st" type="datetime-local" name="startAt" value="${defaultStart}" required></div>
            <div class="col-md-6"><label class="form-label" for="en">Closes</label><input class="form-control" id="en" type="datetime-local" name="endAt" value="${defaultEnd}" required></div>
            <input type="hidden" name="passingMarks" value="0">
        </div></div>

        <div class="card mb-3"><div class="card-body">
            <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
                <h2 class="h6 mb-0">Who gets it? <span class="badge text-bg-primary" id="count">0 selected</span></h2>
                <div class="d-flex gap-2 flex-wrap">
                    <input class="form-control form-control-sm w-auto" id="find" placeholder="Search name or username" aria-label="Search students">
                    <button class="btn btn-sm btn-outline-secondary" type="button" id="all">Select all shown</button>
                    <button class="btn btn-sm btn-outline-secondary" type="button" id="none">Clear</button>
                </div>
            </div>
            <c:if test="${not empty batchPicks}">
                <div class="mb-2 small"><span class="text-muted me-1">Quick pick a batch:</span>
                    <c:forEach items="${batchPicks}" var="b"><button type="button" class="btn btn-sm btn-outline-primary me-1 mb-1 batch" data-ids="${b.studentIds}"><c:out value="${b.name}"/></button></c:forEach></div>
            </c:if>
            <c:choose>
                <c:when test="${empty students}"><p class="text-muted mb-0">There are no active students yet. Ask an administrator to add students first.</p></c:when>
                <c:otherwise>
                    <div class="row g-1" style="max-height:320px;overflow:auto" id="list">
                        <c:forEach items="${students}" var="u">
                            <div class="col-12 col-md-6 col-xl-4 stu" data-q="<c:out value='${u.fullName} ${u.username}'/>">
                                <label class="form-check border rounded px-5 py-2 w-100">
                                    <input class="form-check-input pick" type="checkbox" name="studentIds" value="${u.id}"> <c:out value="${u.fullName}"/> <span class="text-muted small">(<c:out value="${u.username}"/>)</span>
                                </label></div>
                        </c:forEach>
                    </div>
                </c:otherwise>
            </c:choose>
        </div></div>
        <button class="btn btn-primary btn-lg" type="submit" id="go" data-confirm="Create this exam and send it to the selected students now?">Send to <span id="n">0</span> student(s)</button>
        <a class="btn btn-link" href="<c:url value='/teacher/uploads/${s.id}'/>">Back</a>
    </form>
</t:layout>

<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<c:set var="e" value="${d.exam}"/>
<c:set var="canEdit" value="${e.status != 'ARCHIVED' and not d.structureLocked}"/>
<t:layout title="${e.title}" active="exams">
    <div class="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3">
        <div>
            <h1 class="h3 page-title mb-1"><c:out value="${e.title}"/>
                <span class="badge text-bg-${e.status == 'PUBLISHED' ? 'success' : (e.status == 'DRAFT' ? 'secondary' : 'dark')} align-middle fs-6"><c:out value="${e.status.label}"/></span></h1>
            <div class="text-muted"><c:out value="${e.subject.name}"/> &middot; <c:out value="${fmt.dateTime(e.startAt)}"/> to <c:out value="${fmt.dateTime(e.endAt)}"/> &middot; ${e.durationMinutes} min &middot; ${e.maxAttempts} attempt(s)</div>
        </div>
        <div class="d-flex flex-wrap gap-2">
            <c:if test="${e.status != 'ARCHIVED'}"><a class="btn btn-outline-secondary" href="<c:url value='/teacher/exams/${e.id}/edit'/>">Edit settings</a></c:if>
            <a class="btn btn-outline-primary" href="<c:url value='/reports/exams/${e.id}'/>">Results &amp; reports</a>
            <c:if test="${e.status == 'DRAFT' and (e.approvalStatus == 'DRAFT' or e.approvalStatus == 'REJECTED')}">
                <form method="post" action="<c:url value='/teacher/exams/${e.id}/request-approval'/>"><t:csrf/>
                    <button class="btn btn-outline-success" type="submit">Send for approval</button></form>
            </c:if>
            <c:if test="${e.status == 'DRAFT'}">
                <form method="post" action="<c:url value='/teacher/exams/${e.id}/publish'/>"><t:csrf/>
                    <button class="btn btn-success" type="submit" ${empty d.problems ? '' : 'disabled'}>Publish</button></form>
            </c:if>
            <c:if test="${e.status == 'PUBLISHED' and not d.structureLocked}">
                <form method="post" action="<c:url value='/teacher/exams/${e.id}/unpublish'/>"><t:csrf/>
                    <button class="btn btn-outline-warning" type="submit">Unpublish</button></form>
            </c:if>
            <c:if test="${e.status != 'ARCHIVED'}">
                <form method="post" action="<c:url value='/teacher/exams/${e.id}/archive'/>"><t:csrf/>
                    <button class="btn btn-outline-danger" type="submit" data-confirm="Archive this exam? Students will no longer see it.">Archive</button></form>
            </c:if>
        </div>
    </div>

    <c:if test="${not empty d.problems}">
        <div class="alert alert-warning"><strong>Before you can publish:</strong>
            <ul class="mb-0"><c:forEach items="${d.problems}" var="p"><li><c:out value="${p}"/></li></c:forEach></ul></div>
    </c:if>
    <c:if test="${d.structureLocked}"><div class="alert alert-info">Students have started this exam, so questions and assignments are locked.</div></c:if>

    <div class="row g-3 mb-3">
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body"><div class="stat-value">${d.totalQuestions}</div><div class="stat-label">Questions per student</div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body"><div class="stat-value"><c:out value="${fmt.num(d.totalMarks)}"/></div><div class="stat-label">Total marks</div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body"><div class="stat-value"><c:out value="${fmt.num(e.passingMarks)}"/></div><div class="stat-label">Passing marks</div></div></div></div>
        <div class="col-6 col-lg-3"><div class="card stat-card"><div class="card-body"><div class="stat-value">${d.attemptCount}</div><div class="stat-label">Attempts so far</div></div></div></div>
    </div>

    <c:if test="${not empty coCoverage}">
        <div class="card mb-3"><div class="card-body">
            <div class="row g-3">
                <div class="col-md-6"><strong>CO coverage (marks)</strong><div class="mt-1">
                    <c:forEach items="${coCoverage}" var="x"><span class="badge text-bg-${x.label == 'No CO' ? 'warning' : 'primary'} me-1"><c:out value="${x.label}"/>: <c:out value="${fmt.num(x.marks)}"/> (${x.count} Q)</span></c:forEach></div></div>
                <div class="col-md-6"><strong>Bloom's level (marks)</strong><div class="mt-1">
                    <c:forEach items="${bloomCoverage}" var="x"><span class="badge text-bg-secondary me-1"><c:out value="${x.label}"/>: <c:out value="${fmt.num(x.marks)}"/> (${x.count} Q)</span></c:forEach></div></div>
            </div></div></div>
    </c:if>
    <div class="card mb-3"><div class="card-body d-flex flex-wrap justify-content-between align-items-center gap-2">
        <div><strong>Approval:</strong> <span class="badge text-bg-${e.approvalStatus == 'APPROVED' ? 'success' : (e.approvalStatus == 'REJECTED' ? 'danger' : 'warning')}"><c:out value="${e.approvalStatus}"/></span> &middot; <strong>Type:</strong> <c:out value="${e.examType.label}"/> &middot; <strong>Results:</strong> <c:out value="${e.resultPolicy.label}"/>
            <c:if test="${e.resultPolicy == 'SCHEDULED'}"> &middot; <span class="badge text-bg-info">Publishes <c:out value="${fmt.dateTime(e.resultsPublishAt)}"/></span></c:if>
            <c:if test="${e.resultPolicy == 'MANUAL'}"> &middot; <span class="badge text-bg-${e.resultsReleased ? 'success' : 'secondary'}">${e.resultsReleased ? 'Released' : 'Not released'}</span></c:if></div>
        <c:if test="${e.resultPolicy == 'MANUAL' and e.status != 'DRAFT'}">
            <form method="post" action="<c:url value='/teacher/exams/${e.id}/release'/>"><t:csrf/>
                <input type="hidden" name="released" value="${!e.resultsReleased}">
                <button class="btn btn-sm ${e.resultsReleased ? 'btn-outline-secondary' : 'btn-primary'}" type="submit">${e.resultsReleased ? 'Hide results' : 'Release results to students'}</button></form>
        </c:if>
    </div></div>

    <div class="row g-3">
        <div class="col-xl-7">
            <div class="card mb-3"><div class="card-body">
                <div class="d-flex justify-content-between align-items-center mb-2">
                    <h2 class="h5 mb-0">Selected questions</h2>
                    <c:if test="${canEdit}"><span class="d-flex gap-2"><a class="btn btn-sm btn-primary" href="<c:url value='/teacher/exams/${e.id}/pick'/>">Pick from question bank</a>
                        <a class="btn btn-sm btn-outline-primary" href="<c:url value='/teacher/exams/${e.id}/upload'/>">Upload Questions (.docx)</a>
                        <a class="btn btn-sm btn-link" href="<c:url value='/teacher/exams/upload-template.docx'/>">Download Template</a></span></c:if>
                </div>
                <div class="table-responsive"><table class="table table-sm align-middle mb-0">
                    <thead><tr><th>#</th><th>Question</th><th>Type</th><th style="width:130px">Marks</th><c:if test="${canEdit}"><th></th></c:if></tr></thead>
                    <tbody>
                    <c:forEach items="${d.picked}" var="p" varStatus="st">
                        <tr><td>${st.index + 1}</td><td><div class="text-truncate" style="max-width:340px"><c:out value="${p.text}"/></div></td>
                            <td class="small"><c:out value="${p.typeLabel}"/></td>
                            <td><c:choose><c:when test="${canEdit}">
                                <form class="d-flex gap-1" method="post" action="<c:url value='/teacher/exams/${e.id}/questions/${p.id}/marks'/>"><t:csrf/>
                                    <input class="form-control form-control-sm" type="number" step="0.01" min="0.01" name="marks" value="<c:out value='${fmt.num(p.marks)}'/>">
                                    <button class="btn btn-sm btn-outline-secondary" type="submit">Set</button></form>
                            </c:when><c:otherwise><c:out value="${fmt.num(p.marks)}"/></c:otherwise></c:choose></td>
                            <c:if test="${canEdit}"><td class="text-end"><form method="post" action="<c:url value='/teacher/exams/${e.id}/questions/${p.id}/remove'/>"><t:csrf/>
                                <button class="btn btn-sm btn-outline-danger" type="submit" aria-label="Remove">&times;</button></form></td></c:if></tr>
                    </c:forEach>
                    <c:if test="${empty d.picked}"><tr><td colspan="5" class="text-muted text-center py-3">No fixed questions. Pick some, or use random rules below.</td></tr></c:if>
                    </tbody>
                </table></div>
            </div></div>

            <div class="card"><div class="card-body">
                <h2 class="h5">Random selection rules</h2>
                <p class="text-muted small">Each student gets a different random set drawn from the question bank when they start.</p>
                <table class="table table-sm align-middle">
                    <thead><tr><th>Category</th><th>Difficulty</th><th>Count</th><th>Marks each</th><th>Available</th><c:if test="${canEdit}"><th></th></c:if></tr></thead>
                    <tbody>
                    <c:forEach items="${d.rules}" var="r">
                        <tr><td><c:out value="${r.categoryName}"/></td><td><c:out value="${r.difficulty == null ? 'Any' : r.difficulty.label}"/></td>
                            <td>${r.count}</td><td><c:out value="${fmt.num(r.marksEach)}"/></td>
                            <td class="${r.available < r.count ? 'text-danger fw-semibold' : ''}">${r.available}</td>
                            <c:if test="${canEdit}"><td class="text-end"><form method="post" action="<c:url value='/teacher/exams/${e.id}/rules/${r.id}/remove'/>"><t:csrf/>
                                <button class="btn btn-sm btn-outline-danger" type="submit" aria-label="Remove">&times;</button></form></td></c:if></tr>
                    </c:forEach>
                    <c:if test="${empty d.rules}"><tr><td colspan="6" class="text-muted text-center py-2">No rules.</td></tr></c:if>
                    </tbody>
                </table>
                <c:if test="${canEdit}">
                    <form class="row g-2 align-items-end" method="post" action="<c:url value='/teacher/exams/${e.id}/rules'/>"><t:csrf/>
                        <div class="col-md-3"><label class="form-label small mb-1" for="r-cat">Category</label>
                            <select class="form-select form-select-sm" id="r-cat" name="categoryId"><option value="">Any</option>
                                <c:forEach items="${categories}" var="cat"><option value="${cat.id}"><c:out value="${cat.name}"/></option></c:forEach></select></div>
                        <div class="col-md-3"><label class="form-label small mb-1" for="r-diff">Difficulty</label>
                            <select class="form-select form-select-sm" id="r-diff" name="difficulty"><option value="">Any</option>
                                <c:forEach items="${difficulties}" var="df"><option value="${df}"><c:out value="${df.label}"/></option></c:forEach></select></div>
                        <div class="col-md-2"><label class="form-label small mb-1" for="r-count">Count</label>
                            <input class="form-control form-control-sm" id="r-count" name="count" type="number" min="1" max="200" value="5" required></div>
                        <div class="col-md-2"><label class="form-label small mb-1" for="r-marks">Marks each</label>
                            <input class="form-control form-control-sm" id="r-marks" name="marks" type="number" step="0.01" min="0.01" value="1" required></div>
                        <div class="col-md-2"><button class="btn btn-sm btn-outline-primary w-100" type="submit">Add rule</button></div>
                    </form>
                </c:if>
            </div></div>
        </div>

        <div class="col-xl-5">
            <div class="card mb-3"><div class="card-body">
                <h2 class="h5">Assigned to</h2>
                <ul class="list-group list-group-flush mb-3">
                    <c:forEach items="${d.assignments}" var="a">
                        <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                            <span><span class="badge text-bg-light border me-2"><c:out value="${a.kind}"/></span><c:out value="${a.label}"/>
                                <c:if test="${a.kind == 'Batch'}"><span class="text-muted small">&middot; ${a.studentCount} students</span></c:if></span>
                            <c:if test="${e.status != 'ARCHIVED'}"><form method="post" action="<c:url value='/teacher/exams/${e.id}/assign/${a.id}/remove'/>"><t:csrf/>
                                <button class="btn btn-sm btn-outline-danger" type="submit" aria-label="Remove">&times;</button></form></c:if>
                        </li>
                    </c:forEach>
                    <c:if test="${empty d.assignments}"><li class="list-group-item text-muted px-0">Nobody yet.</li></c:if>
                </ul>
                <c:if test="${e.status != 'ARCHIVED'}">
                    <form method="post" action="<c:url value='/teacher/exams/${e.id}/assign/batches'/>" class="mb-3"><t:csrf/>
                        <label class="form-label small mb-1">Add batches</label>
                        <div class="border rounded p-2 mb-2" style="max-height:160px;overflow:auto">
                            <c:forEach items="${batches}" var="b">
                                <div class="form-check"><input class="form-check-input" type="checkbox" name="batchIds" value="${b.id}" id="b${b.id}">
                                    <label class="form-check-label" for="b${b.id}"><c:out value="${b.name}"/> <span class="text-muted small">(<c:out value="${b.code}"/>)</span></label></div>
                            </c:forEach>
                            <c:if test="${empty batches}"><span class="text-muted small">No active batches. An administrator can create them.</span></c:if>
                        </div>
                        <button class="btn btn-sm btn-outline-primary" type="submit">Assign batches</button>
                    </form>
                    <form method="post" action="<c:url value='/teacher/exams/${e.id}/assign/students'/>"><t:csrf/>
                        <label class="form-label small mb-1" for="students">Add individual students (usernames, comma separated)</label>
                        <input class="form-control form-control-sm mb-2" id="students" name="students" maxlength="2000" required>
                        <button class="btn btn-sm btn-outline-primary" type="submit">Assign students</button>
                    </form>
                </c:if>
            </div></div>
            <div class="card"><div class="card-body small">
                <h2 class="h6">Settings</h2>
                <ul class="mb-0 ps-3">
                    <li>Negative marking: <c:choose><c:when test="${e.negativeMarking}">Yes (<c:out value="${fmt.num(e.negativeFraction)}"/> of marks)</c:when><c:otherwise>No</c:otherwise></c:choose></li>
                    <li>Shuffle questions: ${e.shuffleQuestions ? 'Yes' : 'No'} &middot; options: ${e.shuffleOptions ? 'Yes' : 'No'}</li>
                    <li>Answer review: ${e.reviewAllowed ? 'Allowed' : 'Not allowed'}</li>
                    <li>Focus-loss limit: <c:out value="${e.maxFocusLosses == 0 ? 'none' : e.maxFocusLosses}"/></li>
                    <li>IP restriction: <c:out value="${empty e.allowedIps ? 'none' : e.allowedIps}"/></li>
                </ul>
            </div></div>
        </div>
    </div>
</t:layout>

<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<c:set var="isEdit" value="${not empty form.id}"/>
<t:layout title="${isEdit ? 'Edit exam' : 'New exam'}" active="exams">
    <h1 class="h3 page-title mb-3"><c:out value="${isEdit ? 'Edit exam' : 'Create exam'}"/></h1>
    <c:if test="${locked}"><div class="alert alert-warning">Students have started this exam. Only the title, instructions, end time, result and security settings can be changed.</div></c:if>
    <c:if test="${not empty errors}"><div class="alert alert-danger"><ul class="mb-0"><c:forEach items="${errors}" var="e"><li><c:out value="${e}"/></li></c:forEach></ul></div></c:if>
    <c:choose>
        <c:when test="${isEdit}"><c:url var="action" value="/teacher/exams/${form.id}"/></c:when>
        <c:otherwise><c:url var="action" value="/teacher/exams"/></c:otherwise>
    </c:choose>
    <form method="post" action="${action}">
        <t:csrf/>
        <fieldset>
        <div class="row g-4">
            <div class="col-lg-7">
                <div class="card mb-3"><div class="card-body">
                    <div class="mb-3"><label class="form-label" for="title">Title</label>
                        <input class="form-control" id="title" name="title" maxlength="200" required value="<c:out value='${form.title}'/>"></div>
                    <div class="mb-3"><label class="form-label" for="subjectId">Subject</label>
                        <select class="form-select" id="subjectId" name="subjectId" required ${locked ? 'disabled' : ''}>
                            <c:forEach items="${subjects}" var="s"><option value="${s.id}" ${s.id == form.subjectId ? 'selected' : ''}><c:out value="${s.name}"/></option></c:forEach>
                        </select>
                        <c:if test="${locked}"><input type="hidden" name="subjectId" value="${form.subjectId}"></c:if></div>
                    <div class="mb-0"><label class="form-label" for="instructions">Instructions shown to students</label>
                        <textarea class="form-control" id="instructions" name="instructions" rows="5" maxlength="5000"><c:out value="${form.instructions}"/></textarea></div>
                </div></div>
                <div class="card"><div class="card-body">
                    <h2 class="h6">Scheduling</h2>
                    <div class="row g-3">
                        <div class="col-md-6"><label class="form-label" for="startAt">Opens at (<c:out value="${fmt.zoneName()}"/>)</label>
                            <input class="form-control" type="datetime-local" id="startAt" name="startAt" required value="<c:out value='${fmt.local(form.startAt)}'/>" ${locked ? 'readonly' : ''}></div>
                        <div class="col-md-6"><label class="form-label" for="endAt">Closes at</label>
                            <input class="form-control" type="datetime-local" id="endAt" name="endAt" required value="<c:out value='${fmt.local(form.endAt)}'/>"></div>
                        <div class="col-md-6"><label class="form-label" for="durationMinutes">Duration per attempt (minutes)</label>
                            <input class="form-control" type="number" id="durationMinutes" name="durationMinutes" min="1" max="600" required value="${form.durationMinutes}" ${locked ? 'readonly' : ''}></div>
                        <div class="col-md-6"><label class="form-label" for="maxAttempts">Attempts allowed</label>
                            <input class="form-control" type="number" id="maxAttempts" name="maxAttempts" min="1" max="20" required value="${form.maxAttempts}" ${locked ? 'readonly' : ''}></div>
                    </div>
                    <div class="form-text mt-2">Each student's timer starts when they begin and never runs past the closing time.</div>
                </div></div>
            </div>
            <div class="col-lg-5">
                <div class="card mb-3"><div class="card-body">
                    <h2 class="h6">Marking</h2>
                    <div class="mb-3"><label class="form-label" for="passingMarks">Passing marks</label>
                        <input class="form-control" type="number" step="0.01" min="0" id="passingMarks" name="passingMarks" required value="<c:out value='${form.passingMarks}'/>" ${locked ? 'readonly' : ''}></div>
                    <div class="form-check form-switch mb-2">
                        <input class="form-check-input" type="checkbox" role="switch" id="negativeMarking" name="negativeMarking" value="true" data-toggle-target="#neg-box" ${form.negativeMarking ? 'checked' : ''} ${locked ? 'disabled' : ''}>
                        <input type="hidden" name="_negativeMarking" value="on">
                        <label class="form-check-label" for="negativeMarking">Negative marking</label></div>
                    <div id="neg-box" class="mb-3 d-none"><label class="form-label" for="negativeFraction">Deduct this fraction of a question's marks for a wrong answer</label>
                        <input class="form-control" type="number" step="0.05" min="0" max="1" id="negativeFraction" name="negativeFraction" value="<c:out value='${form.negativeFraction}'/>" ${locked ? 'readonly' : ''}></div>
                    <div class="form-check form-switch mb-2"><input class="form-check-input" type="checkbox" role="switch" id="shuffleQuestions" name="shuffleQuestions" value="true" ${form.shuffleQuestions ? 'checked' : ''} ${locked ? 'disabled' : ''}>
                        <input type="hidden" name="_shuffleQuestions" value="on"><label class="form-check-label" for="shuffleQuestions">Shuffle questions</label></div>
                    <div class="form-check form-switch"><input class="form-check-input" type="checkbox" role="switch" id="shuffleOptions" name="shuffleOptions" value="true" ${form.shuffleOptions ? 'checked' : ''} ${locked ? 'disabled' : ''}>
                        <input type="hidden" name="_shuffleOptions" value="on"><label class="form-check-label" for="shuffleOptions">Shuffle options</label></div>
                </div></div>
                <div class="card mb-3"><div class="card-body">
                    <h2 class="h6">Results</h2>
                    <div class="mb-3"><label class="form-label" for="examType">Exam type</label>
                        <select class="form-select" id="examType" name="examType">
                            <c:forEach items="${examTypes}" var="t"><option value="${t}" ${t == form.examType ? 'selected' : ''}><c:out value="${t.label}"/></option></c:forEach></select></div>
                    <div class="mb-3"><label class="form-label" for="resultPolicy">Students see their result</label>
                        <select class="form-select" id="resultPolicy" name="resultPolicy">
                            <c:forEach items="${policies}" var="p"><option value="${p}" ${p == form.resultPolicy ? 'selected' : ''}><c:out value="${p.label}"/></option></c:forEach>
                        </select></div>
                    <div class="mb-3"><label class="form-label" for="resultDelayDays">Hold results for how many days after the exam ends (for example 15)</label>
                        <input class="form-control" type="number" min="0" max="365" id="resultDelayDays" name="resultDelayDays" value="<c:out value='${form.resultDelayDays}'/>">
                        <div class="form-text">Or leave this empty and choose the exact date and time below. Only used for &ldquo;On a date and time the teacher chooses&rdquo;.</div></div>
                    <div class="mb-3"><label class="form-label" for="resultsPublishAt">Publish results at (exact date and time)</label>
                        <input class="form-control" type="datetime-local" id="resultsPublishAt" name="resultsPublishAt" value="<c:out value='${fmt.local(form.resultsPublishAt)}'/>">
                        <div class="form-text">Students see their result summary only from this moment. You can change it until then.</div></div>
                    <div class="form-text">Students see a result summary only (marks obtained, questions attempted and not attempted); the answer review is not shown.</div>
                </div></div>
                <div class="card"><div class="card-body">
                    <h2 class="h6">Exam security</h2>
                    <div class="mb-3"><label class="form-label" for="allowedIps">Allowed IPs / ranges (optional)</label>
                        <input class="form-control" id="allowedIps" name="allowedIps" maxlength="2000" placeholder="203.0.113.10, 192.168.1.0/24" value="<c:out value='${form.allowedIps}'/>">
                        <div class="form-text">Leave empty to allow any network (e.g. restrict to the lab).</div></div>
                    <div class="mb-3"><label class="form-label" for="maxFocusLosses">Auto-submit after this many tab/window switches (0 = never)</label>
                        <input class="form-control" type="number" min="0" max="50" id="maxFocusLosses" name="maxFocusLosses" value="${form.maxFocusLosses}"></div>
                    <div class="form-check form-switch"><input class="form-check-input" type="checkbox" role="switch" id="blockCopyPaste" name="blockCopyPaste" value="true" ${form.blockCopyPaste ? 'checked' : ''}>
                        <input type="hidden" name="_blockCopyPaste" value="on"><label class="form-check-label" for="blockCopyPaste">Block copy / paste / right-click in the exam</label></div>
                </div></div>
            </div>
        </div>
        </fieldset>
        <div class="mt-4 d-flex gap-2">
            <button class="btn btn-primary" type="submit">Save</button>
            <a class="btn btn-outline-secondary" href="<c:url value="/teacher/exams"/>">Cancel</a>
        </div>
    </form>
</t:layout>

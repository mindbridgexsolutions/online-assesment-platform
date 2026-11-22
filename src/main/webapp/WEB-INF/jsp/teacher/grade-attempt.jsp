<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Grade answers" active="grading">
    <h1 class="h3 page-title mb-1">Grade answers</h1>
    <div class="text-muted mb-3"><c:out value="${student.fullName}"/> <c:if test="${not empty student.rollNo}">(<c:out value="${student.rollNo}"/>)</c:if> &middot; <c:out value="${exam.title}"/> &middot; attempt #${attempt.attemptNo}</div>
    <form method="post" action="<c:url value='/teacher/grading/${attempt.id}'/>"><t:csrf/>
        <c:forEach items="${essays}" var="row" varStatus="st">
            <div class="card mb-3"><div class="card-body">
                <div class="d-flex justify-content-between"><span class="text-muted small">Essay ${st.index + 1}</span><span class="small text-muted">Max <c:out value="${fmt.num(row.question.marks)}"/> marks</span></div>
                <div class="q-text fw-semibold mb-2"><c:out value="${row.question.text}"/></div>
                <div class="q-text border rounded p-3 bg-light mb-3"><c:out value="${row.answer.answered ? row.answer.answer : '(not answered)'}"/></div>
                <div class="row g-2">
                    <div class="col-sm-3"><label class="form-label small" for="m${row.question.id}">Marks</label>
                        <input class="form-control" type="number" step="0.25" min="0" max="${row.question.marks}" id="m${row.question.id}" name="marks_${row.question.id}" value="<c:out value='${row.answer.needsManual ? "" : fmt.num(row.answer.awardedMarks)}'/>" required></div>
                    <div class="col-sm-9"><label class="form-label small" for="f${row.question.id}">Feedback to student (optional)</label>
                        <input class="form-control" id="f${row.question.id}" name="feedback_${row.question.id}" maxlength="2000" value="<c:out value='${row.answer.feedback}'/>"></div>
                </div>
            </div></div>
        </c:forEach>
        <c:if test="${empty essays}"><div class="alert alert-info">This attempt has no essay questions.</div></c:if>
        <button class="btn btn-primary" type="submit">Save grades</button>
        <a class="btn btn-outline-secondary" href="<c:url value='/teacher/grading'/>">Back</a>
    </form>
</t:layout>

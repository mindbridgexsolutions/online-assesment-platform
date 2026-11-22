<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Grading" active="grading">
    <h1 class="h3 page-title mb-3">Answers waiting for grading</h1>
    <div class="card"><div class="table-responsive"><table class="table table-hover align-middle mb-0">
        <thead><tr><th>Exam</th><th>Student</th><th>Roll no</th><th>Submitted</th><th>Pending</th><th></th></tr></thead>
        <tbody>
        <c:forEach items="${rows}" var="r">
            <tr><td><c:out value="${r.examTitle}"/></td><td><c:out value="${r.studentName}"/></td><td><c:out value="${r.rollNo}"/></td>
                <td class="small"><c:out value="${fmt.dateTime(r.submittedAt)}"/></td><td>${r.pendingManual}</td>
                <td class="text-end"><a class="btn btn-sm btn-primary" href="<c:url value='/teacher/grading/${r.attemptId}'/>">Grade</a></td></tr>
        </c:forEach>
        <c:if test="${empty rows}"><tr><td colspan="6" class="text-center text-muted py-4">Nothing to grade. </td></tr></c:if>
        </tbody></table></div></div>
</t:layout>

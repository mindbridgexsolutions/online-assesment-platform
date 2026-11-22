<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Add questions" active="questions">
    <nav class="small mb-2"><a href="<c:url value='/teacher/questions'/>">Question bank</a> / <a href="<c:url value='/teacher/uploads/${s.id}'/>"><c:out value="${s.fileName}"/></a> / Add questions</nav>
    <h1 class="h3 page-title mb-1">Add questions from your bank</h1>
    <p class="text-muted">Tick the questions of <strong><c:out value="${s.subjectName}"/></strong> you want in this paper. Only multiple-choice questions can be added.</p>
    <form class="d-flex gap-2 mb-3" method="get" action="<c:url value='/teacher/uploads/${s.id}/add-questions'/>">
        <input class="form-control" name="q" value="<c:out value='${fQ}'/>" placeholder="Search the question text" aria-label="Search">
        <button class="btn btn-outline-secondary" type="submit">Search</button>
    </form>
    <form method="post" action="<c:url value='/teacher/uploads/${s.id}/add-questions'/>"><t:csrf/>
        <c:choose>
            <c:when test="${empty found}"><div class="card card-body text-muted">No questions found in this subject yet.</div></c:when>
            <c:otherwise>
                <c:forEach items="${found}" var="f">
                    <label class="card mb-2"><div class="card-body py-2 d-flex gap-2 align-items-start">
                        <input class="form-check-input mt-1" type="checkbox" name="ids" value="${f.id}">
                        <div><c:out value="${f.text}"/> <span class="badge text-bg-light border ms-1"><c:out value="${f.type.label}"/></span>
                            <span class="badge text-bg-light border">${f.defaultMarks} mark(s)</span></div>
                    </div></label>
                </c:forEach>
            </c:otherwise>
        </c:choose>
        <div class="card sticky-bottom shadow-sm mt-3"><div class="card-body d-flex gap-2">
            <button class="btn btn-primary" type="submit">Add ticked questions</button>
            <a class="btn btn-link" href="<c:url value='/teacher/uploads/${s.id}'/>">Back to the paper</a>
        </div></div>
    </form>
</t:layout>

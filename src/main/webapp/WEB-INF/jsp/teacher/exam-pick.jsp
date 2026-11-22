<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Pick questions" active="exams">
    <h1 class="h3 page-title mb-1">Pick questions</h1>
    <div class="text-muted mb-3"><c:out value="${exam.title}"/> &middot; <c:out value="${exam.subject.name}"/></div>
    <form class="card card-body mb-3" method="get" action="<c:url value='/teacher/exams/${exam.id}/pick'/>">
        <div class="row g-2 align-items-end">
            <div class="col-md-4"><label class="form-label small mb-1" for="q">Search</label><input class="form-control" id="q" name="q" value="<c:out value='${fQ}'/>"></div>
            <div class="col-md-2"><label class="form-label small mb-1" for="categoryId">Category</label>
                <select class="form-select" id="categoryId" name="categoryId" data-autosubmit><option value="">All</option>
                    <c:forEach items="${categories}" var="cat"><option value="${cat.id}" ${cat.id == fCategory ? 'selected' : ''}><c:out value="${cat.name}"/></option></c:forEach></select></div>
            <div class="col-md-2"><label class="form-label small mb-1" for="type">Type</label>
                <select class="form-select" id="type" name="type" data-autosubmit><option value="">All</option>
                    <c:forEach items="${types}" var="ty"><option value="${ty}" ${ty == fType ? 'selected' : ''}><c:out value="${ty.label}"/></option></c:forEach></select></div>
            <div class="col-md-2"><label class="form-label small mb-1" for="difficulty">Level</label>
                <select class="form-select" id="difficulty" name="difficulty" data-autosubmit><option value="">All</option>
                    <c:forEach items="${difficulties}" var="df"><option value="${df}" ${df == fDifficulty ? 'selected' : ''}><c:out value="${df.label}"/></option></c:forEach></select></div>
            <div class="col-md-2"><button class="btn btn-outline-primary w-100" type="submit">Filter</button></div>
        </div>
    </form>
    <form method="post" action="<c:url value='/teacher/exams/${exam.id}/questions'/>"><t:csrf/>
        <div class="card"><div class="table-responsive"><table class="table table-hover align-middle mb-0">
            <thead><tr><th style="width:40px"></th><th>Question</th><th>Category</th><th>Type</th><th>Level</th><th class="text-end">Marks</th></tr></thead>
            <tbody>
            <c:forEach items="${questions}" var="q">
                <tr><td><input class="form-check-input" type="checkbox" name="questionIds" value="${q.id}" aria-label="Select"></td>
                    <td><div class="text-truncate" style="max-width:520px"><c:out value="${q.text}"/></div></td>
                    <td class="small"><c:out value="${q.category == null ? '' : q.category.name}"/></td>
                    <td class="small"><c:out value="${q.type.label}"/></td><td class="small"><c:out value="${q.difficulty.label}"/></td>
                    <td class="text-end">${q.defaultMarks}</td></tr>
            </c:forEach>
            <c:if test="${empty questions}"><tr><td colspan="6" class="text-center text-muted py-4">No more questions match. Showing at most 200.</td></tr></c:if>
            </tbody></table></div></div>
        <div class="mt-3 d-flex gap-2"><button class="btn btn-primary" type="submit">Add selected to exam</button>
            <a class="btn btn-outline-secondary" href="<c:url value='/teacher/exams/${exam.id}'/>">Back to exam</a></div>
    </form>
</t:layout>

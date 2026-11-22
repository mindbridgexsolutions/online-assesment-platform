<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Question bank" active="questions" js="bank-highlight.js">
    <style>
        @keyframes hlpulse { 0%,100% { background-color: #fff3cd; box-shadow: 0 0 0 0 rgba(255,193,7,.9); } 50% { background-color: #ffe066; box-shadow: 0 0 0 12px rgba(255,193,7,0); } }
        .hl { background: #fff3cd !important; outline: 2px solid #ffc107; animation: hlpulse 1s ease-in-out 8; }
        .hl td { background: transparent !important; box-shadow: none !important; }
    </style>
    <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
        <h1 class="h3 page-title mb-0">Question bank</h1>
        <div class="d-flex gap-2">
            <a class="btn btn-outline-primary" href="<c:url value='/teacher/questions/import'/>">Import / export</a>
            <a class="btn btn-primary" href="<c:url value='/teacher/questions/new'/>">Add question</a>
        </div>
    </div>
    <div class="card mb-3"><div class="card-body">
        <h2 class="h6 mb-2">Upload questions</h2>
        <form method="post" enctype="multipart/form-data" class="row g-2 align-items-end" action="<c:url value='/teacher/papers/upload'><c:param name='_csrf' value='${_csrf.token}'/></c:url>">
            <div class="col-md-4"><label class="form-label small mb-1" for="upSubject">Subject</label>
                <select class="form-select" id="upSubject" name="subjectId" required><option value="">Choose subject</option>
                    <c:forEach items="${subjects}" var="s"><option value="${s.id}"><c:out value="${s.name}"/></option></c:forEach></select></div>
            <div class="col-md-5"><label class="form-label small mb-1" for="upFile">Word, Excel or CSV file</label><input class="form-control" id="upFile" type="file" name="file" accept=".docx,.xlsx,.csv" required></div>
            <div class="col-md-3"><button class="btn btn-primary w-100" type="submit">Upload and review</button></div>
            <div class="col-12 small text-muted">You check every question on the next screen. Nothing is added to the bank until you confirm. Templates: <a href="<c:url value='/teacher/exams/upload-template.docx'/>">Word</a> &middot; <a href="<c:url value='/teacher/questions/import'/>">CSV and other types</a>.</div>
        </form>
        <c:if test="${not empty drafts}">
            <hr class="my-3"><h3 class="h6">My question papers</h3>
            <div class="table-responsive"><table class="table table-sm align-middle mb-0">
                <thead><tr><th>Paper</th><th>Subject</th><th class="text-end">Questions</th><th>State</th><th></th></tr></thead>
                <tbody><c:forEach items="${drafts}" var="d"><tr id="paper-${d.id}" class="${d.id == hp ? 'hl' : ''}">
                    <td><a href="<c:url value='/teacher/uploads/${d.id}'/>"><c:out value="${d.fileName}"/></a></td><td class="small"><c:out value="${d.subjectName}"/></td>
                    <td class="text-end">${d.questions}</td>
                    <td><c:choose><c:when test="${d.status == 'PENDING'}"><span class="badge text-bg-warning">Waiting for your check</span></c:when><c:otherwise><span class="badge text-bg-success">Saved</span></c:otherwise></c:choose></td>
                    <td class="text-end text-nowrap">
                        <a class="btn btn-sm btn-outline-primary" href="<c:url value='/teacher/uploads/${d.id}'/>">${d.status == 'PENDING' ? 'Review' : 'Open / edit'}</a>
                        <c:if test="${d.valid > 0 or d.status == 'CONFIRMED'}"><a class="btn btn-sm btn-primary" href="<c:url value='/teacher/uploads/${d.id}/publish'/>">Send to students</a></c:if>
                        <form method="post" class="d-inline" action="<c:url value='/teacher/uploads/${d.id}/discard'/>"><t:csrf/><button class="btn btn-sm btn-outline-danger" type="submit" data-confirm="Delete this paper? Your Question bank and exams stay as they are.">Delete</button></form></td>
                </tr></c:forEach></tbody></table></div>
        </c:if>
    </div></div>
    <c:if test="${not empty hq}">
        <div class="card hl mb-3"><div class="card-body d-flex flex-wrap justify-content-between align-items-center gap-2" id="found-question">
            <div><div class="small text-muted mb-1">The question you were looking for (already in your bank)</div>
                <strong><c:out value="${hq.text}"/></strong>
                <span class="badge text-bg-light border ms-1"><c:out value="${hq.type.label}"/></span> <span class="text-muted small"><c:out value="${hq.subjectName}"/></span></div>
            <a class="btn btn-sm btn-outline-primary" href="<c:url value='/teacher/questions/${hq.id}/edit'/>">Open</a>
        </div></div>
    </c:if>
    <form class="card card-body mb-3" method="get" action="<c:url value='/teacher/questions'/>">
        <div class="row g-2 align-items-end">
            <div class="col-md-3"><label class="form-label small mb-1" for="q">Search text</label>
                <input class="form-control" id="q" name="q" value="<c:out value='${fQ}'/>"></div>
            <div class="col-md-2"><label class="form-label small mb-1" for="subjectId">Subject</label>
                <select class="form-select" id="subjectId" name="subjectId" data-autosubmit>
                    <option value="">All</option>
                    <c:forEach items="${subjects}" var="s"><option value="${s.id}" ${s.id == fSubject ? 'selected' : ''}><c:out value="${s.name}"/></option></c:forEach>
                </select></div>
            <div class="col-md-2"><label class="form-label small mb-1" for="categoryId">Category</label>
                <select class="form-select" id="categoryId" name="categoryId" data-autosubmit>
                    <option value="">All</option>
                    <c:forEach items="${categories}" var="cat"><option value="${cat.id}" ${cat.id == fCategory ? 'selected' : ''}><c:out value="${cat.name}"/></option></c:forEach>
                </select></div>
            <div class="col-md-2"><label class="form-label small mb-1" for="type">Type</label>
                <select class="form-select" id="type" name="type" data-autosubmit>
                    <option value="">All</option>
                    <c:forEach items="${types}" var="ty"><option value="${ty}" ${ty == fType ? 'selected' : ''}><c:out value="${ty.label}"/></option></c:forEach>
                </select></div>
            <div class="col-md-1"><label class="form-label small mb-1" for="difficulty">Level</label>
                <select class="form-select" id="difficulty" name="difficulty" data-autosubmit>
                    <option value="">All</option>
                    <c:forEach items="${difficulties}" var="d"><option value="${d}" ${d == fDifficulty ? 'selected' : ''}><c:out value="${d.label}"/></option></c:forEach>
                </select></div>
            <div class="col-md-2 d-flex gap-2"><button class="btn btn-outline-primary w-100" type="submit">Filter</button>
                <a class="btn btn-outline-secondary" href="<c:url value='/teacher/questions'/>">Reset</a></div>
        </div>
    </form>
    <div class="card"><div class="table-responsive">
        <table class="table table-hover align-middle mb-0">
            <thead><tr><th style="width:42%">Question</th><th>Subject / category</th><th>Type</th><th>Level</th><th class="text-end">Marks</th><th></th></tr></thead>
            <tbody>
            <c:forEach items="${page.content}" var="q">
                <tr>
                    <td><div class="text-truncate" style="max-width:480px"><c:out value="${q.text}"/></div>
                        <c:forEach items="${q.tags}" var="tg"><span class="badge text-bg-light border me-1"><c:out value="${tg}"/></span></c:forEach></td>
                    <td><c:out value="${q.subjectName}"/><div class="small text-muted"><c:out value="${q.categoryName}"/></div></td>
                    <td class="small"><c:out value="${q.type.label}"/></td>
                    <td><span class="badge text-bg-${q.difficulty == 'EASY' ? 'success' : (q.difficulty == 'HARD' ? 'danger' : 'warning')}"><c:out value="${q.difficulty.label}"/></span></td>
                    <td class="text-end">${q.defaultMarks}</td>
                    <td class="text-end text-nowrap">
                        <a class="btn btn-sm btn-outline-primary" href="<c:url value='/teacher/questions/${q.id}/edit'/>">Edit</a>
                        <form class="d-inline" method="post" action="<c:url value='/teacher/questions/${q.id}/archive'/>">
                            <t:csrf/><button class="btn btn-sm btn-outline-danger" type="submit" data-confirm="Archive this question? It stays in past attempts but cannot be used in new exams.">Archive</button>
                        </form>
                        <form class="d-inline" method="post" action="<c:url value='/teacher/questions/${q.id}/delete'/>"><t:csrf/><button class="btn btn-sm btn-danger" type="submit" data-confirm="Delete this question for good? This cannot be undone.">Delete</button></form>
                    </td>
                </tr>
            </c:forEach>
            <c:if test="${empty page.content}"><tr><td colspan="6" class="text-center text-muted py-4">No questions match.</td></tr></c:if>
            </tbody>
        </table>
    </div></div>
    <t:pager page="${page}" path="/teacher/questions"/>
</t:layout>

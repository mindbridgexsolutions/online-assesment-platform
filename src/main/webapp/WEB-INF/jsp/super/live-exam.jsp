<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Correct exam" active="live">
    <c:set var="ex" value="${view.exam}"/>
    <h1 class="h3 page-title mb-1"><c:out value="${ex.title}"/></h1>
    <p class="text-muted"><strong>${view.inProgress}</strong> student(s) writing now, <strong>${view.finished}</strong> already submitted. Changes reach running students on their next save and every submitted score is recalculated.</p>
    <form class="card card-body mb-3 flex-row gap-2 align-items-center" method="post" action="<c:url value='/super/live/${ex.id}/recalculate'/>"><t:csrf/>
        <input class="form-control" name="reason" placeholder="Reason (saved in audit log)" required minlength="5" maxlength="300">
        <button class="btn btn-outline-primary text-nowrap" type="submit">Recalculate all submitted scores</button></form>
    <c:forEach items="${view.questions}" var="q" varStatus="st">
        <div class="card mb-3"><div class="card-body">
            <div class="d-flex justify-content-between">
                <h2 class="h6">Q${st.count}. <c:out value="${q.text}"/></h2>
                <span><span class="badge text-bg-light border">${q.marks} marks</span>
                <c:if test="${q.voidMode == 'DROPPED'}"><span class="badge text-bg-warning">Dropped</span></c:if>
                <c:if test="${q.voidMode == 'FREE_MARKS'}"><span class="badge text-bg-info">Free marks</span></c:if></span>
            </div>
            <ul class="list-unstyled small mb-3">
            <c:forEach items="${q.options}" var="o"><li><c:if test="${o.correct}"><span class="text-success fw-bold">&#10003;</span> </c:if><c:out value="${o.text}"/>
                <c:if test="${not empty o.imagePath}"><img class="q-image opt-image" alt="Option picture" src="<c:url value='/files/q/${o.imagePath}'/>"></c:if></li></c:forEach></ul>
            <div class="row g-3">
                <div class="col-lg-6"><form method="post" action="<c:url value='/super/live/${ex.id}/q/${q.questionId}/accept'/>"><t:csrf/>
                    <div class="fw-semibold small mb-1">Accept more than one option</div>
                    <c:forEach items="${q.options}" var="o"><label class="d-block small"><input type="checkbox" name="optionIds" value="${o.id}" ${o.correct ? 'checked disabled' : ''}> <c:out value="${o.text}"/></label></c:forEach>
                    <div class="d-flex gap-2 mt-2"><input class="form-control form-control-sm" name="reason" placeholder="Reason" required minlength="5"><button class="btn btn-sm btn-primary text-nowrap">Accept ticked</button></div></form></div>
                <div class="col-lg-6">
                    <div class="fw-semibold small mb-1">Edit the question or answer key</div>
                    <a class="btn btn-sm btn-outline-primary" href="<c:url value='/teacher/questions/${q.questionId}/edit'/>">Edit question</a>
                    <form class="d-flex gap-2 mt-2" method="post" action="<c:url value='/super/live/${ex.id}/q/${q.questionId}/push'/>"><t:csrf/>
                        <input class="form-control form-control-sm" name="reason" placeholder="Reason" required minlength="5"><button class="btn btn-sm btn-outline-primary text-nowrap">Send edit to running and submitted attempts</button></form>
                    <div class="fw-semibold small mt-3 mb-1">Withdraw the question</div>
                    <form class="d-flex gap-2 flex-wrap" method="post" action="<c:url value='/super/live/${ex.id}/q/${q.questionId}/void'/>"><t:csrf/>
                        <input class="form-control form-control-sm" name="reason" placeholder="Reason" required minlength="5">
                        <button class="btn btn-sm btn-outline-warning" name="mode" value="DROPPED">Drop question</button>
                        <button class="btn btn-sm btn-outline-info" name="mode" value="FREE_MARKS">Give everyone the marks</button>
                        <button class="btn btn-sm btn-outline-secondary" name="mode" value="NONE">Undo</button></form>
                </div></div>
        </div></div>
    </c:forEach>
    <a class="btn btn-link" href="<c:url value='/super/live'/>">Back</a>
</t:layout>

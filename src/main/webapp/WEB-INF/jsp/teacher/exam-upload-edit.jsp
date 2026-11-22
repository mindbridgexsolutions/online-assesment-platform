<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Edit uploaded question" active="${empty s.examId ? 'questions' : 'exams'}">
    <h1 class="h3 page-title mb-1">Edit row ${row.rowNo}</h1>
    <c:if test="${not empty row.message}"><div class="alert alert-${row.status == 'ERROR' ? 'danger' : 'warning'}"><c:out value="${row.message}"/></div></c:if>
    <form method="post" enctype="multipart/form-data" action="<c:url value='/teacher/uploads/${s.id}/items/${row.id}/edit'><c:param name='_csrf' value='${_csrf.token}'/></c:url>">
        <div class="card mb-3"><div class="card-body">
            <label class="form-label fw-semibold" for="text">Question</label>
            <textarea class="form-control mb-2" id="text" name="text" rows="3"><c:out value="${row.item.text}"/></textarea>
            <c:if test="${not empty row.item.image}"><p><img class="q-image" alt="Question picture" src="<c:url value='/files/q/${row.item.image}'/>">
                <label class="d-block small"><input type="checkbox" name="removeQuestionImage" value="true"> Remove this picture</label></p></c:if>
            <label class="form-label small" for="qimg">${empty row.item.image ? 'Add a picture' : 'Replace the picture'}</label>
            <input class="form-control" id="qimg" type="file" name="questionImage" accept="image/*">
        </div></div>
        <div class="card mb-3"><div class="card-header">Options (tick the correct one or ones)</div><div class="card-body">
            <c:forEach items="${letters}" var="L" varStatus="st">
                <c:set var="o" value="${row.item.options[st.index]}"/>
                <div class="row g-2 align-items-start mb-3">
                    <div class="col-auto pt-2"><input type="checkbox" name="correct" value="${L}" ${row.correctFlags[st.index] ? 'checked' : ''} aria-label="Option ${L} is correct"></div>
                    <div class="col-md-5"><div class="input-group"><span class="input-group-text">${L}</span><input class="form-control" name="optionText" value="<c:out value='${o.text}'/>" maxlength="1000"></div></div>
                    <div class="col-md-5"><input class="form-control form-control-sm" type="file" name="optionImage" accept="image/*" aria-label="Picture for option ${L}">
                        <c:if test="${not empty o.image}"><img class="q-image opt-image mt-1" alt="Option picture" src="<c:url value='/files/q/${o.image}'/>">
                            <label class="small d-block"><input type="checkbox" name="removeOptionImage" value="${st.index}"> Remove picture</label></c:if></div>
                </div>
            </c:forEach>
        </div></div>
        <div class="card mb-3"><div class="card-body row g-3">
            <div class="col-md-3"><label class="form-label" for="marks">Marks</label><input class="form-control" id="marks" name="marks" value="<c:out value='${row.item.marks}'/>"></div>
            <div class="col-md-3"><label class="form-label" for="co">CO</label>
                <select class="form-select" id="co" name="co"><option value="">None</option>
                    <c:forEach items="${cos}" var="c"><option value="<c:out value='${c.code}'/>" ${c.code == row.item.co ? 'selected' : ''}><c:out value="${c.code}"/></option></c:forEach></select></div>
            <div class="col-md-3"><label class="form-label" for="bloom">Bloom's</label>
                <select class="form-select" id="bloom" name="bloom"><option value="">Not set</option>
                    <c:forEach items="${blooms}" var="b"><option value="${b.label}" ${b.label == row.item.bloom or b.name() == row.item.bloom ? 'selected' : ''}><c:out value="${b.label}"/></option></c:forEach></select></div>
            <div class="col-md-3"><label class="form-label" for="unit">Unit</label><input class="form-control" id="unit" name="unit" value="<c:out value='${row.item.unit}'/>"></div>
            <div class="col-md-3"><label class="form-label" for="diff">Difficulty</label>
                <select class="form-select" id="diff" name="difficulty"><option value="">Medium</option>
                    <option ${row.item.difficulty == 'Easy' ? 'selected' : ''}>Easy</option><option ${row.item.difficulty == 'Medium' ? 'selected' : ''}>Medium</option><option ${row.item.difficulty == 'Hard' ? 'selected' : ''}>Hard</option></select></div>
        </div></div>
        <button class="btn btn-primary" type="submit">Save and check again</button>
        <a class="btn btn-link" href="<c:url value='/teacher/uploads/${s.id}'/>">Back to review</a>
    </form>
    <c:if test="${not empty row.item.image or not empty row.item.options[0].image or not empty row.item.options[1].image or not empty row.item.options[2].image or not empty row.item.options[3].image}">
    <form class="card mt-3" method="post" action="<c:url value='/teacher/uploads/${s.id}/items/${row.id}/move-image'/>"><t:csrf/>
        <div class="card-body row g-2 align-items-end">
            <div class="col-12"><strong>Picture in the wrong place?</strong> <span class="small text-muted">Save your edits first. If the target already has a picture, the two are swapped.</span></div>
            <div class="col-auto"><label class="form-label small" for="mvf">Move from</label><select class="form-select form-select-sm" id="mvf" name="from">
                <option value="Q">Question</option><c:forEach items="${letters}" var="L"><option value="${L}">Option ${L}</option></c:forEach></select></div>
            <div class="col-auto"><label class="form-label small" for="mvt">to</label><select class="form-select form-select-sm" id="mvt" name="to">
                <c:forEach items="${letters}" var="L"><option value="${L}">Option ${L}</option></c:forEach><option value="Q">Question</option></select></div>
            <div class="col-auto"><button class="btn btn-sm btn-outline-primary" type="submit">Move picture</button></div>
        </div>
    </form>
    </c:if>
</t:layout>

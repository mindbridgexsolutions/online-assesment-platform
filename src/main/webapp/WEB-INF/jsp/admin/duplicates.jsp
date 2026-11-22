<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Duplicate questions" active="duplicates">
    <h1 class="h3 page-title mb-1">Duplicate questions</h1>
    <p class="text-muted">Questions with the same wording and options. Choose the one to keep and tick the copies to remove. Exams and past results that used a removed copy are moved to the kept question, so nothing breaks.</p>
    <c:forEach items="${groups}" var="g">
        <form class="card mb-3" method="post" action="<c:url value='/admin/duplicates/merge'/>"><t:csrf/>
            <div class="card-header"><c:out value="${g.subject}"/> &middot; ${g.members.size()} copies</div>
            <div class="table-responsive"><table class="table mb-0 align-middle">
                <thead><tr><th>Keep</th><th>Remove</th><th>Question</th><th>Category</th><th>Source</th><th>Used in exams</th><th>Past attempts</th></tr></thead><tbody>
                <c:forEach items="${g.members}" var="m" varStatus="st"><tr>
                    <td><input type="radio" name="keep" value="${m.id}" ${st.first ? 'checked' : ''} required></td>
                    <td><input type="checkbox" name="remove" value="${m.id}" ${st.first ? '' : 'checked'}></td>
                    <td><c:out value="${m.text}"/></td><td><c:out value="${m.category}"/></td><td class="small"><c:out value="${m.source}"/></td>
                    <td>${m.exams}</td><td>${m.attempts}</td></tr></c:forEach>
            </tbody></table></div>
            <div class="card-footer text-end"><button class="btn btn-primary" type="submit">Keep selected, remove ticked</button></div>
        </form>
    </c:forEach>
    <c:if test="${empty groups}"><div class="alert alert-success">No duplicates found. The question bank is clean.</div></c:if>
</t:layout>

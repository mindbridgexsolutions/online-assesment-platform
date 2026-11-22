<%@ tag language="java" pageEncoding="UTF-8" body-content="empty" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ attribute name="page" type="org.springframework.data.domain.Page" required="true" %>
<%@ attribute name="path" required="true" description="Context-relative path, e.g. /admin/users" %>
<c:if test="${page.totalPages > 1}">
    <c:set var="from" value="${page.number - 3 < 0 ? 0 : page.number - 3}"/>
    <c:set var="to" value="${page.number + 3 > page.totalPages - 1 ? page.totalPages - 1 : page.number + 3}"/>
    <nav aria-label="Pagination" class="mt-3">
        <ul class="pagination pagination-sm mb-0">
            <li class="page-item ${page.first ? 'disabled' : ''}">
                <c:url var="prevUrl" value="${path}">
                    <c:forEach items="${param}" var="p"><c:if test="${p.key != 'page'}"><c:param name="${p.key}" value="${p.value}"/></c:if></c:forEach>
                    <c:param name="page" value="${page.number - 1}"/>
                </c:url>
                <a class="page-link" href="${prevUrl}">&laquo;</a>
            </li>
            <c:forEach begin="${from}" end="${to}" var="i">
                <c:url var="pageUrl" value="${path}">
                    <c:forEach items="${param}" var="p"><c:if test="${p.key != 'page'}"><c:param name="${p.key}" value="${p.value}"/></c:if></c:forEach>
                    <c:param name="page" value="${i}"/>
                </c:url>
                <li class="page-item ${i == page.number ? 'active' : ''}"><a class="page-link" href="${pageUrl}">${i + 1}</a></li>
            </c:forEach>
            <li class="page-item ${page.last ? 'disabled' : ''}">
                <c:url var="nextUrl" value="${path}">
                    <c:forEach items="${param}" var="p"><c:if test="${p.key != 'page'}"><c:param name="${p.key}" value="${p.value}"/></c:if></c:forEach>
                    <c:param name="page" value="${page.number + 1}"/>
                </c:url>
                <a class="page-link" href="${nextUrl}">&raquo;</a>
            </li>
        </ul>
        <div class="small text-muted mt-1">${page.totalElements} records</div>
    </nav>
</c:if>

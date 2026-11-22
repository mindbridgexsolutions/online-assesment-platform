<%@ tag language="java" pageEncoding="UTF-8" body-content="empty" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ attribute name="name" required="true" type="java.lang.String" %>
<%@ attribute name="thumb" required="false" type="java.lang.Boolean" %>
<%-- Shows a question attachment: images inline, PDF / Word files as a download link. --%>
<c:choose>
    <c:when test="${fn:endsWith(name, '.pdf')}">
        <a class="btn btn-outline-secondary btn-sm" target="_blank" rel="noopener" href="<c:url value='/files/q/${name}'/>">&#128196; Open attached PDF</a>
    </c:when>
    <c:when test="${fn:endsWith(name, '.docx')}">
        <a class="btn btn-outline-secondary btn-sm" target="_blank" rel="noopener" href="<c:url value='/files/q/${name}'/>">&#128221; Download attached Word document</a>
    </c:when>
    <c:otherwise>
        <img class="${thumb ? 'q-thumb' : 'q-image'}" alt="Question illustration" src="<c:url value='/files/q/${name}'/>">
    </c:otherwise>
</c:choose>

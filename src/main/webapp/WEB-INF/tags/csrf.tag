<%@ tag language="java" pageEncoding="UTF-8" body-content="empty" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<input type="hidden" name="${_csrf.parameterName}" value="<c:out value='${_csrf.token}'/>">

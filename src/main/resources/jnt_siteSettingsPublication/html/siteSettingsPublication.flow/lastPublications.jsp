<%@ page import="java.util.Map"%>
<%@ page import="java.util.Date"%>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="utility" uri="http://www.jahia.org/tags/utilityLib"%>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib"%>
<%@ taglib prefix="functions" uri="http://www.jahia.org/tags/functions"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form"%>
<%@ taglib prefix="fnt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>

<%--@elvariable id="currentNode" type="org.jahia.services.content.JCRNodeWrapper"--%>
<%--@elvariable id="currentResource" type="org.jahia.services.render.Resource"--%>
<%--@elvariable id="flowRequestContext" type="org.springframework.webflow.execution.RequestContext"--%>
<%--@elvariable id="out" type="java.io.PrintWriter"--%>
<%--@elvariable id="renderContext" type="org.jahia.services.render.RenderContext"--%>
<%--@elvariable id="script" type="org.jahia.services.render.scripting.Script"--%>
<%--@elvariable id="scriptInfo" type="java.lang.String"--%>
<%--@elvariable id="url" type="org.jahia.services.render.URLGenerator"--%>
<%--@elvariable id="workspace" type="java.lang.String"--%>

<template:addResources type="javascript"
    resources="jquery.min.js,jquery-ui.min.js,admin-bootstrap.js,jquery.tablesorter.js,jquery.blockUI.js,workInProgress.js,bootbox.min.js" />
<template:addResources type="css" resources="admin-bootstrap.css" />
<template:addResources type="css"
    resources="jquery-ui.smoothness.css,jquery-ui.smoothness-jahia.css,tablecloth.css" />

<spring:eval var="SUCCESS"
    expression="T(org.jahia.modules.sitesettings.publication.SiteAdminPublicationJob).SUCCESS" />
<spring:eval var="ERROR"
    expression="T(org.jahia.modules.sitesettings.publication.SiteAdminPublicationJob).ERROR" />
<spring:eval var="NOTHING_TO_PUBLISH"
    expression="T(org.jahia.modules.sitesettings.publication.SiteAdminPublicationJob).NOTHING_TO_PUBLISH" />
<spring:eval var="STATUS_ADDED"
    expression="T(org.jahia.services.scheduler.BackgroundJob).STATUS_ADDED" />
<spring:eval var="STATUS_SCHEDULED"
    expression="T(org.jahia.services.scheduler.BackgroundJob).STATUS_SCHEDULED" />
<spring:eval var="STATUS_EXECUTING"
    expression="T(org.jahia.services.scheduler.BackgroundJob).STATUS_EXECUTING" />
<spring:eval var="STATUS_CANCELED"
    expression="T(org.jahia.services.scheduler.BackgroundJob).STATUS_CANCELED" />

<h2>
    <fmt:message key="siteSettingsPublication.publicationJobs.title" />
</h2>

<c:set var="totalCount" value="${fn:length(publicationJobs)}" />

<template:addResources type="inlinejavascript">
    <script type="text/javascript">
        <c:if test="${totalCount > 0}">

            $(document).ready(function () {
                $("#tablePublicationJobs").tablesorter({
                    sortList: [[0, 1]],
                    headers: {5: {sorter: false}}
                });
            });

        </c:if>

    </script>
</template:addResources>

<div>
    <div>
        <form action="${flowExecutionUrl}" method="post"
            style="display: inline;">
            <input type="hidden" name="_eventId" value="back" />
            <button class="btn" type="submit" name="backButton">
                <i class="icon-arrow-left"></i> &nbsp;
                <fmt:message
                    key="siteSettingsPublication.publicationJobs.back" />
            </button>
        </form>
    </div>

    <div>
        <table class="table table-bordered table-striped table-hover"
            id="tablePublicationJobs">
            <thead>
                <tr>
                    <th data-sorter="shortDate"
                        data-date-format="mmddyyyy"><fmt:message
                            key="siteSettingsPublication.publicationJobs.startDate" />
                    </th>
                    <th data-sorter="shortDate"
                        data-date-format="mmddyyyy"><fmt:message
                            key="siteSettingsPublication.publicationJobs.endDate" />
                    </th>
                    <th><fmt:message
                            key="siteSettingsPublication.publicationJobs.path" />
                    </th>
                    <th><fmt:message
                            key="siteSettingsPublication.publicationJobs.language" />
                    </th>
                    <th><fmt:message
                            key="siteSettingsPublication.publicationJobs.status" />
                    </th>
                    <th><fmt:message
                            key="siteSettingsPublication.publicationJobs.details" />
                    </th>
                </tr>
            </thead>
            <tbody>
                <c:choose>
                    <c:when test="${totalCount > 0}">
                        <c:set var="count" value="0" />
                        <c:forEach items="${publicationJobs}"
                            var="publicationJob">
                            <c:set var="jobDetail"
                                value="${publicationJob.jobDataMap}" />
                            <jsp:useBean id="dateValue"
                                class="java.util.Date" />
                            <jsp:setProperty name="dateValue"
                                property="time"
                                value="${jobDetail['begin']}" />
                            <fmt:formatDate value="${dateValue}"
                                pattern="MM/dd/yyyy HH:mm:ss"
                                var="beginDate" />
                            <tr>
                                <td>
                                    <c:if test="${not empty jobDetail['begin']}">
                                       ${beginDate}
                                    </c:if>
                                </td>
                                <jsp:setProperty name="dateValue"
                                    property="time"
                                    value="${jobDetail['end']}" />
                                <td><c:if
                                        test="${not empty jobDetail['end']}">
                                        <fmt:formatDate
                                            value="${dateValue}"
                                            pattern="MM/dd/yyyy HH:mm:ss" />
                                    </c:if></td>
                                <td>${jobDetail['path']}</td>
                                <td>${jobDetail['language']}</td>
                                <td><c:choose>
                                        <c:when
                                            test="${jobDetail['result'] == null}">
                                            <fmt:message
                                                key="siteSettingsPublication.publicationJobs.${jobDetail['status']}" />
                                        </c:when>
                                        <c:otherwise>
                                            <fmt:message
                                                key="siteSettingsPublication.publicationJobs.${jobDetail['result']}" />
                                        </c:otherwise>
                                    </c:choose></td>
                                <td><c:choose>
                                        <c:when
                                            test="${jobDetail['result'] == null}">
                                            <fmt:message
                                                key="siteSettingsPublication.publicationJobs.details.${jobDetail['status']}" />
                                        </c:when>
                                        <c:when
                                            test="${jobDetail['result'] == ERROR}">
                                            <div id="detail${count}"
                                                class="modal hide fade">
                                                <div
                                                    class="modal-header">
                                                    <button
                                                        type="button"
                                                        class="close"
                                                        data-dismiss="modal"
                                                        aria-hidden="true">
                                                        &times;</button>
                                                    <h3>
                                                        <fmt:message
                                                            key="siteSettingsPublication.publicationJobs.details" />
                                                    </h3>
                                                </div>
                                                <div class="modal-body">
                                                    <p>
                                                        <fmt:message
                                                            key="siteSettingsPublication.publicationJobs.publicationPath">
                                                            <fmt:param
                                                                value="<strong>${jobDetail['path']}</strong>" />
                                                        </fmt:message>
                                                    </p>
                                                    <p>
                                                        <fmt:message
                                                            key="siteSettingsPublication.publicationJobs.language" />
                                                        : <strong>${jobDetail['language']}</strong>
                                                    </p>
                                                    <p>
                                                        <fmt:message
                                                            key="siteSettingsPublication.publicationJobs.startDate" />
                                                        : <strong>${beginDate}</strong>
                                                    </p>
                                                    <c:if
                                                        test="${fn:length(jobDetail['conflict']) > 0}">
                                                        <p>
                                                            <fmt:message
                                                                key="siteSettingsPublication.publicationJobs.conflicts" />
                                                        <ul>
                                                            <c:forEach
                                                                items="${jobDetail['conflict']}"
                                                                var="item">
                                                                <li>${item}</li>
                                                            </c:forEach>
                                                        </ul>
                                                        </p>
                                                    </c:if>
                                                    <c:if
                                                        test="${fn:length(jobDetail['missingProperty']) > 0}">
                                                        <p>
                                                            <fmt:message
                                                                key="siteSettingsPublication.publicationJobs.missingProperty" />
                                                        <ul>
                                                            <c:forEach
                                                                items="${jobDetail['missingProperty']}"
                                                                var="item">
                                                                <li>${item}</li>
                                                            </c:forEach>
                                                        </ul>
                                                        </p>
                                                    </c:if>
                                                </div>
                                                <div
                                                    class="modal-footer">
                                                    <a href="#"
                                                        class="btn"
                                                        data-dismiss="modal">
                                                        <fmt:message
                                                            key="siteSettingsPublication.publicationJobs.close" />
                                                    </a>
                                                </div>
                                            </div>
                                            <a href="#detail${count}"
                                                role="button"
                                                class="btn"
                                                data-toggle="modal">
                                                <fmt:message
                                                    key="siteSettingsPublication.publicationJobs.showDetails" />
                                            </a>
                                            <c:set var="count"
                                                value="${count + 1}" />
                                        </c:when>
                                        <c:when
                                            test="${jobDetail['result'] == NOTHING_TO_PUBLISH}">
                                            <fmt:message
                                                key="siteSettingsPublication.publicationJobs.contentPublished" />
                                        </c:when>
                                    </c:choose></td>
                            </tr>
                        </c:forEach>
                    </c:when>
                    <c:otherwise>
                        <tr>
                            <td colspan="6"><fmt:message
                                    key="siteSettingsPublication.publicationJobs.noItemFound" />
                            </td>
                        </tr>
                    </c:otherwise>
                </c:choose>
            </tbody>
        </table>
    </div>
</div>


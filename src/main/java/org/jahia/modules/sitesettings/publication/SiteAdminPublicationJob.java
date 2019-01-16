/*
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2019 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ===================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.modules.sitesettings.publication;

import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.modules.sitesettings.publication.service.PublicationResultEmailNotificationService;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.*;
import org.jahia.services.scheduler.BackgroundJob;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

import java.util.*;

/**
 * This job is used by Publication site settings to execute a publication of a given node identified by its path, in a given language.
 * In case some nodes are conflicting with live nodes or some mandatory properties are missing, the job will be aborted before starting to publish anything.
 *
 * This job needs two mandatory parameters "path" and "language".
 *
 * This job assumes that some basics checks have been done early:
 * - the path corresponds to an existing node
 * - the path corresponds to a child node of the current site
 *
 * @author kevan
 */
public class SiteAdminPublicationJob extends BackgroundJob {

    /**
     * Job execution result: success.
     */
    public static final String SUCCESS = "success";

    /**
     * Job execution result: it was not possible to publish due to a conflict or a missing mandatory property.
     */
    public static final String ERROR = "error";

    /**
     * Job execution result: there was nothing to publish.
     */
    public static final String NOTHING_TO_PUBLISH = "nothingToPublish";

    /**
     * Job execution result: an unexpected failure happened while the job was in progress.
     */
    public static final String UNEXPECTED_FAILURE = "unexpectedFailure";

    /**
     * Key of the job data containing the UUID of the site whose nodes are published.
     */
    public static final String PUBLICATION_JOB_SITE_UUID = "siteUuid";

    /**
     * Key of the job data containing the path of the node to be published.
     */
    public static final String PUBLICATION_JOB_PATH = "path";

    /**
     * Key of the job data containing code of the language to publish the node in.
     */
    public static final String PUBLICATION_JOB_LANGUAGE = "language";

    /**
     * Key of the result status of the job execution.
     */
    public static final String PUBLICATION_JOB_RESULT = "result";

    /**
     * Key of the reported conflicts path.
     */
    public static final String PUBLICATION_JOB_CONFLICTS = "conflict";

    /**
     * Key of the reported missing properties path.
     */
    public static final String PUBLICATION_JOB_MISSING_PROPERTY = "missingProperty";

    /**
     * Key of the successful publication end timestamp value.
     */
    public static final String PUBLICATION_JOB_END = "publicationEnd";

    /**
     * Key for UI Locale
     */
    public static final String UI_LOCALE = "uiLocale";

    private static final Logger logger = LoggerFactory.getLogger(SiteAdminPublicationJob.class);

    @Override
    public void executeJahiaJob(JobExecutionContext jobExecutionContext) throws Exception {

        // get job data
        JobDetail jobDetail = jobExecutionContext.getJobDetail();
        final JobDataMap jobDataMap = jobDetail.getJobDataMap();
        final String path = (String) jobDataMap.get(PUBLICATION_JOB_PATH);
        final String language = (String) jobDataMap.get(PUBLICATION_JOB_LANGUAGE);

        try {

            final JCRPublicationService publicationService = JCRPublicationService.getInstance();

            // check data
            if (StringUtils.isEmpty(path) || StringUtils.isEmpty(language)) {
                throw new IllegalArgumentException("Path and language are mandatory to execute the site admin publication job");
            }

            JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, new JCRCallback<Object>() {

                @Override
                public Object doInJCR(JCRSessionWrapper session) throws RepositoryException {

                    JCRNodeWrapper node = session.getNode(path);
                    List<PublicationInfo> publicationInfos = publicationService.getPublicationInfo(node.getIdentifier(), Collections.singleton(language), true, true, true, Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE);

                    // check for conflict issues and mandatory properties
                    List<PublicationInfoNode> nonPublishableInfos = new LinkedList<>();
                    boolean needPublication = false;
                    for (PublicationInfo publicationInfo : publicationInfos) {
                        needPublication |= needsPublication(publicationInfo.getRoot(), nonPublishableInfos);
                    }
                    if (!nonPublishableInfos.isEmpty()) {
                        logger.warn("Site admin publication job for path [{}] and language [{}] has been aborted due to conflicts or missing mandatory properties", path, language);
                        jobDataMap.put(PUBLICATION_JOB_RESULT, ERROR);
                        List<String> conflictNodes = new ArrayList<>();
                        List<String> missingMandatoryPropertyNodes = new ArrayList<>();
                        for (PublicationInfoNode publicationInfo : nonPublishableInfos) {
                            if (publicationInfo.getStatus() == PublicationInfo.CONFLICT) {
                                conflictNodes.add(publicationInfo.getPath());
                            } else {
                                missingMandatoryPropertyNodes.add(publicationInfo.getPath());
                            }
                        }
                        jobDataMap.put(PUBLICATION_JOB_CONFLICTS, conflictNodes);
                        jobDataMap.put(PUBLICATION_JOB_MISSING_PROPERTY, missingMandatoryPropertyNodes);
                    } else if (!needPublication) {
                        // nothing to publish
                        logger.info("Site admin publication job for path [{}] and language [{}] finished with nothing to publish", path, language);
                        jobDataMap.put(PUBLICATION_JOB_RESULT, NOTHING_TO_PUBLISH);
                    } else {
                        // do the publication
                        publicationService.publishByMainId(node.getIdentifier(), Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE, Collections.singleton(language), true, Collections.<String>emptyList());
                        jobDataMap.put(PUBLICATION_JOB_END, Long.toString(System.currentTimeMillis()));
                        jobDataMap.put(PUBLICATION_JOB_RESULT, SUCCESS);
                    }

                    return null;
                }
            });
        } catch (Exception e) {
            jobDataMap.put(PUBLICATION_JOB_RESULT, UNEXPECTED_FAILURE);
            throw e;
        } finally {
            // send notification
            try {
                PublicationResultEmailNotificationService notificationService = (PublicationResultEmailNotificationService) SpringContextSingleton.getBeanInModulesContext("org.jahia.modules.sitesettings.publication.service.PublicationResultEmailNotificationService");
                notificationService.notifyJobCompleted(jobDataMap);
            } catch (Exception e) {
                // avoid failing the entire job due to any secondary notification issues, just log instead
                String message = "Error sending notification aboult completion of publication of " + path + " in language " + language + " (was '" + jobDataMap.get(PUBLICATION_JOB_RESULT) + "')";
                logger.error(message, e);
            }
        }
    }

    private static boolean needsPublication(PublicationInfoNode publicationInfoNode, List<PublicationInfoNode> nonPublishableInfos) {
        boolean needsPublication = publicationInfoNode.getStatus() != PublicationInfo.PUBLISHED;
        if (publicationInfoNode.getStatus() == PublicationInfo.CONFLICT || publicationInfoNode.getStatus() == PublicationInfo.MANDATORY_LANGUAGE_UNPUBLISHABLE) {
            nonPublishableInfos.add(publicationInfoNode);
        }
        for (PublicationInfoNode child : publicationInfoNode.getChildren()) {
            needsPublication |= needsPublication(child, nonPublishableInfos);
        }
        for (PublicationInfo reference : publicationInfoNode.getReferences()) {
            needsPublication |= needsPublication(reference.getRoot(), nonPublishableInfos);
        }
        return needsPublication;
    }
}
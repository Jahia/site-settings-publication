/*
 * ==========================================================================================
 * =                            JAHIA'S ENTERPRISE DISTRIBUTION                             =
 * ==========================================================================================
 *
 *                                  http://www.jahia.com
 *
 * JAHIA'S ENTERPRISE DISTRIBUTIONS LICENSING - IMPORTANT INFORMATION
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2017 Jahia Solutions Group. All rights reserved.
 *
 *     This file is part of a Jahia's Enterprise Distribution.
 *
 *     Jahia's Enterprise Distributions must be used in accordance with the terms
 *     contained in the Jahia Solutions Group Terms & Conditions as well as
 *     the Jahia Sustainable Enterprise License (JSEL).
 *
 *     For questions regarding licensing, support, production usage...
 *     please contact our team at sales@jahia.com or go to http://www.jahia.com/license.
 *
 * ==========================================================================================
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
     * Key of the job data containing the path of the node to be published.
     */
    public static final String PUBLICATION_JOB_PATH = "path";

    /**
     * Key of the job data containing code of the language to publish the node in.
     */
    public static final String PUBLICATION_JOB_LANGUAGE = "language";

    /**
     * Key of the result status of the job execution
     */
    public static final String PUBLICATION_JOB_RESULT = "result";

    /**
     * Key of the reported conflicts path
     */
    public static final String PUBLICATION_JOB_CONFLICTS = "conflict";

    /**
     * Key of the reported missing properties path
     */
    public static final String PUBLICATION_JOB_MISSING_PROPERTY = "missingProperty";

    /**
     * Key for UI Locale
     */
    public static final String UI_LOCALE = "uiLocale";

    private static final Logger logger = LoggerFactory.getLogger(SiteAdminPublicationJob.class);

    @Override
    public void executeJahiaJob(JobExecutionContext jobExecutionContext) throws Exception {

        final JCRPublicationService publicationService = JCRPublicationService.getInstance();

        // get job data
        JobDetail jobDetail = jobExecutionContext.getJobDetail();
        final JobDataMap jobDataMap = jobDetail.getJobDataMap();
        final String path = (String) jobDataMap.get(PUBLICATION_JOB_PATH);
        final String language = (String) jobDataMap.get(PUBLICATION_JOB_LANGUAGE);

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
                    needPublication |= populateNonPublishableInfos(publicationInfo.getRoot(), nonPublishableInfos);
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
                    jobDataMap.put(PUBLICATION_JOB_RESULT, SUCCESS);
                }

                // send notification
                try {
                    PublicationResultEmailNotificationService notificationService = (PublicationResultEmailNotificationService) SpringContextSingleton.getBeanInModulesContext("org.jahia.modules.sitesettings.publication.service.PublicationResultEmailNotificationService");
                    notificationService.notifyJobCompleted(jobDataMap);
                } catch (Exception e) {
                    // avoid failing the entire job due to any secondary notification issues, just log instead
                    String message = "Error sending notification aboult completion of publication of " + path + " in language " + language + " (was " + jobDataMap.get(PUBLICATION_JOB_RESULT) + ")";
                    logger.error(message, e);
                }

                return null;
            }
        });
    }

    private boolean populateNonPublishableInfos(PublicationInfoNode publicationInfoNode, List<PublicationInfoNode> nonPublishableInfos) {
        // Do a publication only if the content status is different than published
        boolean needPublication = publicationInfoNode.getStatus() != PublicationInfo.PUBLISHED;
        if (publicationInfoNode.getStatus() == PublicationInfo.CONFLICT || publicationInfoNode.getStatus() == PublicationInfo.MANDATORY_LANGUAGE_UNPUBLISHABLE) {
            nonPublishableInfos.add(publicationInfoNode);
        }
        for (PublicationInfoNode child : publicationInfoNode.getChildren()) {
            needPublication |= populateNonPublishableInfos(child, nonPublishableInfos);
        }
        for (PublicationInfo reference : publicationInfoNode.getReferences()) {
            needPublication |= populateNonPublishableInfos(reference.getRoot(), nonPublishableInfos);
        }
        return needPublication;
    }
}
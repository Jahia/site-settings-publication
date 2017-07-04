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

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.services.content.*;
import org.jahia.services.scheduler.BackgroundJob;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

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

    public static String PUBLICATION_JOB_PATH = "path";
    public static String PUBLICATION_JOB_LANGUAGE = "language";

    private static final Logger logger = LoggerFactory.getLogger(SiteAdminPublicationJob.class);

    @Override
    public void executeJahiaJob(JobExecutionContext jobExecutionContext) throws Exception {

        final JCRPublicationService publicationService = JCRPublicationService.getInstance();

        // get job data
        JobDetail jobDetail = jobExecutionContext.getJobDetail();
        JobDataMap jobDataMap = jobDetail.getJobDataMap();
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
                if (CollectionUtils.isEmpty(publicationInfos)) {
                    // nothing to publish
                    return null;
                }

                // check for conflict issues and mandatory properties
                List<PublicationInfoNode> nonPublishableInfos = new LinkedList<>();
                for (PublicationInfo publicationInfo : publicationInfos) {
                    populateNonPublishableInfos(publicationInfo.getRoot(), nonPublishableInfos);
                }
                if (!nonPublishableInfos.isEmpty()) {
                    // TODO in the future we will need to store the result of this state somewhere (list of nodes/reasons why the job have been abort), the nonPublishableInfos will be the main source of information in that case
                    logger.warn("Site admin publication job has been aborted due to conflicts or missing mandatory properties");
                    return null;
                }

                // do the publication
                publicationService.publishByMainId(node.getIdentifier(), Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE, Collections.singleton(language), true, Collections.<String>emptyList());

                return null;
            }
        });
    }

    private void populateNonPublishableInfos(PublicationInfoNode publicationInfoNode, List<PublicationInfoNode> nonPublishableInfos) {
        if (publicationInfoNode.getStatus() == PublicationInfo.CONFLICT || publicationInfoNode.getStatus() == PublicationInfo.MANDATORY_LANGUAGE_UNPUBLISHABLE) {
            nonPublishableInfos.add(publicationInfoNode);
        }
        for (PublicationInfoNode child : publicationInfoNode.getChildren()) {
            populateNonPublishableInfos(child, nonPublishableInfos);
        }
        for (PublicationInfo reference : publicationInfoNode.getReferences()) {
            populateNonPublishableInfos(reference.getRoot(), nonPublishableInfos);
        }
    }
}
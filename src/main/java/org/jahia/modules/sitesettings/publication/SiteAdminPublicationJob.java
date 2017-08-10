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
import org.jahia.modules.sitesettings.publication.util.SiteAdminPublicationUtils;
import org.jahia.services.content.*;
import org.quartz.JobDataMap;
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
public class SiteAdminPublicationJob extends SiteAdminPublicationJobSupport {

    /**
     * Job execution result: there was nothing to publish.
     */
    public static final String NOTHING_TO_PUBLISH = "nothingToPublish";

    /**
     * Key of the job data containing code of the language to publish the node in.
     */
    public static final String PUBLICATION_JOB_LANGUAGE = "language";

    /**
     * Key of the reported conflicts path.
     */
    public static final String PUBLICATION_JOB_CONFLICTS = "conflict";

    /**
     * Key of the successful publication end timestamp value.
     */
    public static final String PUBLICATION_JOB_END = "publicationEnd";

    private static final Logger logger = LoggerFactory.getLogger(SiteAdminPublicationJob.class);

    @Override
    protected void doExecute(final JobDataMap jobDataMap) throws Exception {

        // get job data
        final String path = (String) jobDataMap.get(PUBLICATION_JOB_PATH);
        String language = (String) jobDataMap.get(PUBLICATION_JOB_LANGUAGE);

        // check data
        if (StringUtils.isEmpty(path) || StringUtils.isEmpty(language)) {
            throw new IllegalArgumentException("Path and language are mandatory to execute the site admin publication job");
        }

        String identifier = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, new JCRCallback<String>() {

            @Override
            public String doInJCR(JCRSessionWrapper session) throws RepositoryException {
                return session.getNode(path).getIdentifier();
            }
        });

        JCRPublicationService publicationService = JCRPublicationService.getInstance();
        List<PublicationInfo> publicationInfos = publicationService.getPublicationInfo(identifier, Collections.singleton(language), true, true, true, Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE);

        boolean needPublication = false;
        List<String> conflictingPaths = new LinkedList<>();
        List<String> missingMandatoryPropertyPaths = new LinkedList<>();
        for (PublicationInfo publicationInfo : publicationInfos) {
            SiteAdminPublicationUtils.PublicationData publicationData = SiteAdminPublicationUtils.getPublicationData(publicationInfo);
            needPublication |= publicationData.getNeedsPublication();
            populateNonPublishablePaths(publicationData.getConflictingNodes(), conflictingPaths);
            populateNonPublishablePaths(publicationData.getMissingMandatoryPropertiesNodes(), missingMandatoryPropertyPaths);
        }

        if (!conflictingPaths.isEmpty() || !missingMandatoryPropertyPaths.isEmpty()) {
            logger.warn("Site admin publication job for path [{}] and language [{}] has been aborted due to conflicts or missing mandatory properties", path, language);
            jobDataMap.put(PUBLICATION_JOB_RESULT, ERROR);
            jobDataMap.put(PUBLICATION_JOB_CONFLICTS, conflictingPaths);
            jobDataMap.put(PUBLICATION_JOB_MISSING_PROPERTY, missingMandatoryPropertyPaths);
        } else if (!needPublication) {
            // nothing to publish
            logger.info("Site admin publication job for path [{}] and language [{}] finished with nothing to publish", path, language);
            jobDataMap.put(PUBLICATION_JOB_RESULT, NOTHING_TO_PUBLISH);
        } else {
            // do the publication
            publicationService.publishByMainId(identifier, Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE, Collections.singleton(language), true, Collections.<String>emptyList());
            jobDataMap.put(PUBLICATION_JOB_END, Long.toString(System.currentTimeMillis()));
            jobDataMap.put(PUBLICATION_JOB_RESULT, SUCCESS);
        }
    }

    private static void populateNonPublishablePaths(Collection<PublicationInfoNode> nonPublishableNodes, Collection<String> nonPublishablePaths) {
        for (PublicationInfoNode nonPublishableNode : nonPublishableNodes) {
            nonPublishablePaths.add(nonPublishableNode.getPath());
        }
    }
}
package org.jahia.modules.sitesettings.publication;

import org.jahia.modules.sitesettings.publication.service.PublicationResultEmailNotificationService;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.scheduler.BackgroundJob;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SiteAdminPublicationJobSupport extends BackgroundJob {

    /**
     * Job execution result: success.
     */
    public static final String SUCCESS = "success";

    /**
     * Job execution result: it was not possible to publish due to a conflict or a missing mandatory property.
     */
    public static final String ERROR = "error";

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
     * Key of the result status of the job execution.
     */
    public static final String PUBLICATION_JOB_RESULT = "result";

    /**
     * Key of the reported missing properties path.
     */
    public static final String PUBLICATION_JOB_MISSING_PROPERTY = "missingProperty";

    /**
     * Key for UI Locale
     */
    public static final String PUBLICATION_JOB_UI_LOCALE = "uiLocale";

    private static final Logger logger = LoggerFactory.getLogger(SiteAdminPublicationJobSupport.class);

    @Override
    public void executeJahiaJob(JobExecutionContext jobExecutionContext) throws Exception {
        JobDataMap jobDataMap = jobExecutionContext.getJobDetail().getJobDataMap();
        try {
            doExecute(jobDataMap);
        } catch (Exception e) {
            jobDataMap.put(PUBLICATION_JOB_RESULT, UNEXPECTED_FAILURE);
            throw e;
        } finally {
            try {
                sendNotification(jobDataMap);
            } catch (Exception e) {
                // Avoid failing the entire job due to any secondary notification issues, just log instead.
                String path = (String) jobDataMap.get(PUBLICATION_JOB_PATH);
                String message = "Error sending notification aboult completion/failure of publication of " + path + " (was '" + jobDataMap.get(PUBLICATION_JOB_RESULT) + "')";
                logger.error(message, e);
            }
        }
    }

    abstract protected void doExecute(JobDataMap jobDataMap) throws Exception;

    protected void sendNotification(JobDataMap jobDataMap) {
        PublicationResultEmailNotificationService notificationService = (PublicationResultEmailNotificationService) SpringContextSingleton.getBeanInModulesContext("org.jahia.modules.sitesettings.publication.service.PublicationResultEmailNotificationService");
        notificationService.notifyJobCompleted(jobDataMap);
    }
}

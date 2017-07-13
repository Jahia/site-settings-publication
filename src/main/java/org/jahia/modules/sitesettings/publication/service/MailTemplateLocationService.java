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
package org.jahia.modules.sitesettings.publication.service;

/**
 * Service that delivers mail template location
 *
 * @author David Griffon
 */
public class MailTemplateLocationService {

    /**
     * define the location of the mail template
     */
    private String mailTemplateLocation;

    public String getMailTemplateLocation() {
        return mailTemplateLocation;
    }

    public void setMailTemplateLocation(String mailTemplateLocation) {
        this.mailTemplateLocation = mailTemplateLocation;
    }
}

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
package org.jahia.modules.sitesettings.publication.webflow;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Model object for a site publication.
 *
 * @author Sergiy Shyrkov
 */
public class SitePublication implements Serializable {

    public enum Scope {

        ENTIRE_SITE,
        SITE_SUBNODE
    }

    private static final long serialVersionUID = -4565197823923232164L;

    private String currentSiteUuid;

    private String currentSiteKey;

    private String currentSiteName;

    private Set<String> languages = Collections.emptySet();

    private String nodePath;

    private Scope scope;

    private List<String> siteLanguages = Collections.emptyList();

    /**
     * Initializes an instance of this model object.
     *
     * @param siteKey the key of the current site
     * @param siteName the name of the current site
     */
    public SitePublication(String siteUuid, String siteKey, String siteName) {
        this.currentSiteUuid = siteUuid;
        this.currentSiteKey = siteKey;
        this.currentSiteName = siteName;
    }

    public String getCurrentSiteUuid() {
        return currentSiteUuid;
    }

    public String getCurrentSiteKey() {
        return currentSiteKey;
    }

    public String getCurrentSiteName() {
        return currentSiteName;
    }

    public Set<String> getLanguages() {
        return languages;
    }

    public String getNodePath() {
        return nodePath;
    }

    public Scope getScope() {
        return scope;
    }

    public List<String> getSiteLanguages() {
        return siteLanguages;
    }

    public void setLanguages(Set<String> languages) {
        this.languages = languages;
    }

    public void setNodePath(String nodePath) {
        this.nodePath = nodePath != null ? nodePath.trim() : nodePath;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public void setSiteLanguages(List<String> siteLanguages) {
        this.siteLanguages = siteLanguages;
    }
}

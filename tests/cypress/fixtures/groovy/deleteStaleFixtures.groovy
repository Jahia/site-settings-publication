import org.jahia.services.content.JCRTemplate
import org.jahia.services.sites.JahiaSitesService
import org.jahia.services.usermanager.JahiaUserManagerService

// Removes the sites and the users that an earlier run left behind when its teardown failed.
String prefix = "PREFIX"
JCRTemplate.instance.doExecuteWithSystemSession { session ->
    JahiaSitesService sites = JahiaSitesService.instance
    sites.getSitesNodeList(session).findAll { it.siteKey.startsWith(prefix) }.each { sites.removeSite(it) }
    JahiaUserManagerService users = JahiaUserManagerService.instance
    Properties criteria = new Properties()
    criteria.setProperty("username", prefix + "*")
    users.searchUsers(criteria, session).each { users.deleteUser(it.path, session) }
    session.save()
    return null
}

import org.jahia.services.content.JCRTemplate
import org.jahia.services.sites.JahiaSitesService
import org.jahia.services.usermanager.JahiaUserManagerService

// Removes the sites and the users that an earlier run left behind when its teardown failed.
// The keys match the exact shape the spec generates (prefix, then 8 lowercase alphanumerics), so a
// site or a user of the developer that only shares the prefix stays.
String prefix = "PREFIX"
def siteKey = ~/${prefix}[a-z0-9]{8}/
def username = ~/${prefix}admin[a-z0-9]{8}/
JCRTemplate.instance.doExecuteWithSystemSession { session ->
    JahiaSitesService sites = JahiaSitesService.instance
    sites.getSitesNodeList(session).findAll { it.siteKey ==~ siteKey }.each { sites.removeSite(it) }
    JahiaUserManagerService users = JahiaUserManagerService.instance
    Properties criteria = new Properties()
    criteria.setProperty("username", prefix + "admin*")
    users.searchUsers(criteria, session).findAll { it.name ==~ username }.each { users.deleteUser(it.path, session) }
    session.save()
    return null
}

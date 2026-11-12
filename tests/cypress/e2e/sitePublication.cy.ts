import {
    addNode,
    context,
    createSite,
    createUser,
    deleteSite,
    deleteUser,
    enableModule,
    grantRoles,
    jfaker,
    publishAndWaitJobEnding,
} from '@jahia/cypress'
import {
    liveStatus,
    sitePublication,
    waitForPrivilegedMembership,
    waitForSitePublicationJobs,
} from '../page-object/sitePublication'

// Migrated from the Selenium class PublishBigSites (TestRail case 12874), tracked in Jahia/selenium#1670.
// Each test opens the screen anew. The publications are acts in `before` hooks, and the tests after them
// read their outcome, so the order of the tests matters.
describe('Site publication from jContent', () => {
    const prefix = 'sitepub'
    const suffix = jfaker.string.alphanumeric({ length: 8, casing: 'lower', safe: true })
    const siteKey = `${prefix}${suffix}`
    const siteAdmin = { username: `${prefix}admin${suffix}`, password: 'password' }
    const languages = ['en', 'fr', 'de']
    const pageName = 'the-new-page'
    const pagePath = `/sites/${siteKey}/home/${pageName}`

    const openAsSiteAdmin = () => {
        cy.login(siteAdmin.username, siteAdmin.password)
        sitePublication.visit(siteKey)
    }

    const openScreen = () => {
        sitePublication.entry().should('be.visible').click()
        sitePublication.title().should('contain.text', 'Site publication')
        sitePublication.waitUntilLoaded()
    }

    const fillNodeScopeInFrench = () => {
        sitePublication.selectNodeScope()
        sitePublication.nodePath().should('be.enabled').clear().type(pagePath)
        sitePublication.selectLanguage('fr')
    }

    const startPublication = () => {
        sitePublication.publishButton().should('be.enabled').click()
        sitePublication.confirmPublish().click()
    }

    const confirmPublication = () => {
        startPublication()
        sitePublication
            .successAlert()
            .should('be.visible')
            .and('contain.text', 'The publication has been started as a background job.')
    }

    before(() => {
        cy.executeGroovy('groovy/deleteStaleFixtures.groovy', { PREFIX: prefix }).should('eq', '.installed')
        createSite(siteKey, {
            templateSet: 'templates-system',
            serverName: 'localhost',
            locale: 'en',
            languages: languages.join(','),
        })
        // Publish the site once, so the page created next is the only content left unpublished.
        publishAndWaitJobEnding(`/sites/${siteKey}`, languages)
        addNode({
            parentPathOrId: `/sites/${siteKey}/home`,
            primaryNodeType: 'jnt:page',
            name: pageName,
            properties: [
                { name: 'j:templateName', type: 'STRING', value: 'home' },
                ...languages.map((language) => ({
                    name: 'jcr:title',
                    type: 'STRING',
                    value: 'The new page',
                    language,
                })),
            ],
        })
        createUser(siteAdmin.username, siteAdmin.password)
        grantRoles(`/sites/${siteKey}`, ['site-administrator'], siteAdmin.username, 'USER')
        waitForPrivilegedMembership(siteAdmin.username)
    })

    after(() => {
        cy.apolloClient()
        deleteSite(siteKey)
        deleteUser(siteAdmin.username)
    })

    it('FT-001: hides the Site publication entry while the module is not enabled on the site', () => {
        context.tag('site-publication', 'menu-hidden', 'testrail-case-12874')
        openAsSiteAdmin()
        sitePublication.tagManagerEntry().should('be.visible')
        sitePublication.entry().should('not.exist')
    })

    describe('once the module is enabled on the site', () => {
        before(() => {
            enableModule('site-settings-publication', siteKey)
        })

        beforeEach(() => {
            openAsSiteAdmin()
        })

        it('FT-002: shows the Site publication entry to a site administrator', () => {
            context.tag('site-publication', 'menu-visible', 'testrail-case-12874')
            sitePublication.entry().should('be.visible').click()
            sitePublication.title().should('contain.text', 'Site publication')
        })

        it('FT-003: keeps Publish disabled until a scope and a language are chosen', () => {
            context.tag('site-publication', 'publish-button-state', 'testrail-case-12874')
            openScreen()
            sitePublication
                .showLastPublicationsButton()
                .should('be.visible')
                .and('contain.text', 'Show last publication status')
            sitePublication.publishButton().should('be.disabled')
            // A language without a scope keeps Publish disabled.
            sitePublication.selectLanguage('fr')
            sitePublication.publishButton().should('be.disabled')
            sitePublication.language('fr').uncheck({ force: true })
            sitePublication.selectNodeScope()
            sitePublication.nodePath().should('be.enabled').clear().type(pagePath)
            sitePublication.publishButton().should('be.disabled')
            sitePublication.selectLanguage('fr')
            sitePublication.publishButton().should('be.enabled')
        })

        it('FT-004: asks for a confirmation that names the node path and the chosen language', () => {
            context.tag('site-publication', 'confirm-dialog', 'testrail-case-12874')
            openScreen()
            fillNodeScopeInFrench()
            sitePublication.publishButton().click()
            sitePublication
                .confirmDialog()
                .should('be.visible')
                .and(
                    'contain.text',
                    `You are about to publish everything under the node ${pagePath} in the following languages:`,
                )
            sitePublication.confirmDialog().find('li').should('have.length', 1).and('contain.text', '[fr]')
            sitePublication.confirmCancel().should('be.visible').and('contain.text', 'Cancel')
            sitePublication.confirmPublish().should('be.visible').and('contain.text', 'Publish')
        })

        it('FT-005: keeps the node path and the language when the confirmation is cancelled', () => {
            context.tag('site-publication', 'confirm-cancel', 'testrail-case-12874')
            openScreen()
            fillNodeScopeInFrench()
            sitePublication.publishButton().click()
            sitePublication.confirmCancel().should('be.visible').click()
            sitePublication.confirmDialog().should('not.exist')
            sitePublication.nodePath().should('have.value', pagePath)
            sitePublication.language('fr').should('be.checked')
        })

        describe('once the French publication of the page has finished', () => {
            let startedAlert = ''

            before(() => {
                openAsSiteAdmin()
                openScreen()
                fillNodeScopeInFrench()
                startPublication()
                sitePublication
                    .successAlert()
                    .should('be.visible')
                    .invoke('text')
                    .then((text) => {
                        startedAlert = text
                    })
                waitForSitePublicationJobs()
            })

            it('FT-006: starts the publication as a background job', () => {
                context.tag('site-publication', 'publish-started', 'testrail-case-12874')
                expect(startedAlert).to.contain('The publication has been started as a background job.')
            })

            it('FT-007: lists the finished job with its path, language and status', () => {
                context.tag('site-publication', 'history-node-scope', 'testrail-case-12874')
                openScreen()
                sitePublication.showLastPublicationsButton().click()
                sitePublication.historyRows().should('have.length', 1)
                sitePublication.historyRows().first().find('td').eq(2).should('have.text', pagePath)
                sitePublication.historyRows().first().find('td').eq(3).should('have.text', 'fr')
                sitePublication.historyRows().first().find('td').eq(4).should('contain.text', 'Successful')
                sitePublication.backToPublication().click()
                sitePublication.publishButton().should('exist')
            })

            it('FT-008: makes the page live in French only', () => {
                context.tag('site-publication', 'live-per-language', 'testrail-case-12874')
                liveStatus('fr', pagePath).should('eq', 200)
                liveStatus('en', pagePath).should('eq', 404)
            })
        })

        describe('once the whole site is published in all languages', () => {
            before(() => {
                openAsSiteAdmin()
                openScreen()
                sitePublication.selectSiteScope()
                sitePublication.selectAllLanguages()
                languages.forEach((language) => sitePublication.language(language).should('be.checked'))
                confirmPublication()
                waitForSitePublicationJobs()
            })

            it('FT-009: adds one history row per site language', () => {
                context.tag('site-publication', 'history-site-scope', 'testrail-case-12874')
                openScreen()
                sitePublication.showLastPublicationsButton().click()
                // One row for the French publication of the page, then one row per site language.
                sitePublication.historyRows().should('have.length', 1 + languages.length)
                sitePublication.historyRows().each((row) => {
                    cy.wrap(row).find('td').eq(4).should('contain.text', 'Successful')
                })
            })

            it('FT-010: makes the page live in every site language', () => {
                context.tag('site-publication', 'live-all-languages', 'testrail-case-12874')
                languages.forEach((language) => liveStatus(language, pagePath).should('eq', 200))
            })
        })
    })
})

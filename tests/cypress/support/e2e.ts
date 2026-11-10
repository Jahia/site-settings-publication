import 'cypress-wait-until'

// eslint-disable-next-line @typescript-eslint/no-var-requires
require('@jahia/cypress/dist/support/registerSupport').registerSupport()

// The jContent and the JSP screens raise errors of their own scripts, which say nothing about the module under test.
Cypress.on('uncaught:exception', () => false)

package org.odisee.security

import groovy.test.GroovyTestCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockServletContext
import org.odisee.document.PathTree
import org.odisee.io.OdiseePath
import org.odisee.io.TenantPaths
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc

import org.springframework.security.core.context.SecurityContextHolder

import java.nio.file.Files

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class OdiseeSecurityConfigurationTest extends GroovyTestCase {

    private AnnotationConfigWebApplicationContext context

    private MockMvc mvc

    private FakeMachineToken fake

    private String adaToken

    @Override
    protected void setUp() {
        Files.deleteIfExists(OidcFile.location())
        PathTree.delete(OdiseePath.ODISEE_VAR.resolve('user').resolve('ada'))
        TenantPaths.createDirectories('ada')
        fake = FakeMachineToken.start()
        fake.writeBinding()
        adaToken = fake.sign(
                azp: 'ada',
                client_id: 'ada',
                preferred_username: 'bob',
                sub: 'bob',
                user: 'carol'
        )
        context = new AnnotationConfigWebApplicationContext()
        context.setServletContext(new MockServletContext())
        context.register(SecurityProbeConfig)
        context.refresh()
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build()
    }

    @Override
    protected void tearDown() {
        context?.close()
        fake?.stop()
        Files.deleteIfExists(OidcFile.location())
        PathTree.delete(OdiseePath.ODISEE_VAR.resolve('user').resolve('ada'))
    }

    void testReadyStaysAnonymous() {
        mvc.perform(get('/ready')).andExpect(status().isOk())
        mvc.perform(get('/ready').header('Authorization', 'Bearer not-a-token')).andExpect(status().isOk())
    }

    void testTemplateWithoutLoginIs401() {
        mvc.perform(get('/template/Letter')).andExpect(status().isUnauthorized())
    }

    void testTemplateWithLoginIsAllowed() {
        mvc.perform(get('/template/Letter').header('Authorization', "Bearer ${adaToken}")).andExpect(status().isOk())
    }

    void testGenerateWithoutLoginIs401() {
        mvc.perform(post('/document/generate')).andExpect(status().isUnauthorized())
    }

    void testGenerateWithLoginIsTheClientId() {
        mvc.perform(post('/document/generate').header('Authorization', "Bearer ${adaToken}"))
                .andExpect(status().isOk())
                .andExpect(content().string('ada'))
    }

    void testJobWithoutLoginIs401() {
        mvc.perform(post('/document/jobs')).andExpect(status().isUnauthorized())
        mvc.perform(get('/document/jobs/abc')).andExpect(status().isUnauthorized())
    }

    void testJobWithLoginIsAllowed() {
        mvc.perform(post('/document/jobs').header('Authorization', "Bearer ${adaToken}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string('ada'))
        mvc.perform(get('/document/jobs/abc').header('Authorization', "Bearer ${adaToken}")).andExpect(status().isOk())
    }

    void testUiClientUsesTheLoggedInUsername() {
        String token = fake.sign(azp: 'odisee-ui', preferred_username: 'ada', sub: 'not-ada')
        mvc.perform(post('/document/generate').header('Authorization', "Bearer ${token}"))
                .andExpect(status().isOk())
                .andExpect(content().string('ada'))
    }

    void testGenerateRejectsATokenForADifferentAudience() {
        String token = fake.sign(azp: 'ada', aud: 'account', preferred_username: 'ada')
        mvc.perform(post('/document/generate').header('Authorization', "Bearer ${token}"))
                .andExpect(status().isUnauthorized())
    }

    @Configuration
    @EnableWebMvc
    @Import(OdiseeSecurityConfiguration)
    static class SecurityProbeConfig {
        @Bean
        ProbeController probeController() {
            new ProbeController()
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping('/ready')
        String ready() {
            'ready'
        }

        @GetMapping('/template/{name}')
        String template() {
            'catalog'
        }

        @PostMapping(path = '/document/generate', produces = 'text/plain')
        String generate() {
            caller()
        }

        @PostMapping(path = '/document/jobs', produces = 'text/plain')
        @ResponseStatus(HttpStatus.ACCEPTED)
        String submitJob() {
            caller()
        }

        private static String caller() {
            SecurityContextHolder.context?.authentication?.name
        }

        @GetMapping('/document/jobs/{id}')
        String showJob() {
            'job'
        }
    }

}

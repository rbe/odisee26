package org.odisee.security

import groovy.test.GroovyTestCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockServletContext
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc

import java.nio.file.Files

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class OdiseeSecurityConfigurationTest extends GroovyTestCase {

    private AnnotationConfigWebApplicationContext context

    private MockMvc mvc

    @Override
    protected void setUp() {
        Files.deleteIfExists(PasswordFile.location())
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder()
        PasswordFile.create(PasswordFile.location(), 'ada', 'ada-secret', false, encoder)
        context = new AnnotationConfigWebApplicationContext()
        context.setServletContext(new MockServletContext())
        context.register(SecurityProbeConfig)
        context.refresh()
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build()
    }

    @Override
    protected void tearDown() {
        context?.close()
        Files.deleteIfExists(PasswordFile.location())
    }

    void testReadyStaysAnonymous() {
        mvc.perform(get('/ready')).andExpect(status().isOk())
    }

    void testTemplateWithoutLoginIs401() {
        mvc.perform(get('/template/Letter')).andExpect(status().isUnauthorized())
    }

    void testTemplateWithLoginIsAllowed() {
        mvc.perform(get('/template/Letter').with(httpBasic('ada', 'ada-secret'))).andExpect(status().isOk())
    }

    void testGenerateWithoutLoginIs401() {
        mvc.perform(post('/document/generate')).andExpect(status().isUnauthorized())
    }

    void testGenerateWithLoginIsAllowed() {
        mvc.perform(post('/document/generate').with(httpBasic('ada', 'ada-secret'))).andExpect(status().isOk())
    }

    void testJobWithoutLoginIs401() {
        mvc.perform(post('/document/jobs')).andExpect(status().isUnauthorized())
        mvc.perform(get('/document/jobs/abc')).andExpect(status().isUnauthorized())
    }

    void testJobWithLoginIsAllowed() {
        mvc.perform(post('/document/jobs').with(httpBasic('ada', 'ada-secret'))).andExpect(status().isAccepted())
        mvc.perform(get('/document/jobs/abc').with(httpBasic('ada', 'ada-secret'))).andExpect(status().isOk())
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

        @PostMapping('/document/generate')
        String generate() {
            'ok'
        }

        @PostMapping('/document/jobs')
        @ResponseStatus(HttpStatus.ACCEPTED)
        String submitJob() {
            'accepted'
        }

        @GetMapping('/document/jobs/{id}')
        String showJob() {
            'job'
        }
    }

}

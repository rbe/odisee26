package org.odisee

class UrlMappings {

    static mappings = {
        '/'(view: '/index')
        '500'(view: '/error')
        '404'(view: '/notFound')
        // Odisee
        '/ready'(controller: 'ready', action: 'index')
        '/user'(controller: 'user', action: 'create', method: 'POST')
        '/template/$name'(controller: 'template', action: 'show', method: 'GET')
        '/document/generate'(controller: 'document', action: 'generate')
        "/document/generate/$id?"(controller: 'document', action: 'generate')
    }

}

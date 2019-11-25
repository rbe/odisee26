package org.odisee

class UrlMappings {

    static mappings = {
        '/'(view: '/index')
        '500'(view: '/error')
        '404'(view: '/notFound')
        // Odisee
        '/document/generate'(controller: 'document', action: 'generate')
        "/document/generate/$id?"(controller: 'document', action: 'generate')
    }

}

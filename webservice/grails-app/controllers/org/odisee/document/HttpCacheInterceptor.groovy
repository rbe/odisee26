package org.odisee.document

class HttpCacheInterceptor {

    HttpCacheInterceptor() {
        match controller: 'document'
    }

    boolean before() {
        log.debug "Setting Cache-Control header"
        response.setHeader('Cache-Control', 'no-cache,no-store,must-revalidate,max-age=0')
        true
    }

    boolean after() { true }

    void afterView() {
        // no-op
    }

}

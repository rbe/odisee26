package eu.artofcoding.odisee.client.javafx;

import org.odisee.client.OdiseeClient;
import org.odisee.client.OutputFormat;

/**
 * Desktop entry to {@code org.odisee.client.OdiseeClient}.
 * {@code open} uses the service URL and bearer access token.
 * {@code preparePdf} calls {@code createRequest(template, OutputFormat.PDF)} and {@code setUserfield}.
 */
public final class DesktopClient {

    private DesktopClient() {
    }

    public static OdiseeClient open(final String serviceUrl, final String accessToken) {
        return OdiseeClient.bearer(serviceUrl, accessToken);
    }

    public static OdiseeClient preparePdf(final OdiseeClient client, final String template,
                                          final String fieldName, final String value) {
        client.createRequest(template, OutputFormat.PDF);
        client.setUserfield(fieldName, value);
        return client;
    }

}

package net.adoptium;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.qute.i18n.MessageBundles;
import io.quarkus.runtime.configuration.LocaleConverter;
import io.quarkus.vertx.web.RouteFilter;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import net.adoptium.api.DownloadRepository;
import net.adoptium.config.ApplicationConfig;
import net.adoptium.model.Download;
import net.adoptium.model.IndexTemplate;
import net.adoptium.model.UserSystem;
import net.adoptium.utils.UserAgentParser;
import net.adoptopenjdk.api.v3.models.Binary;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import java.util.List;

import static io.quarkus.qute.i18n.MessageBundles.ATTRIBUTE_LOCALE;

// index.html in META-INF.resources is used as static resource (not template)
@Path("/")
public class IndexResource {

    private static final Logger LOG = Logger.getLogger(IndexResource.class);

    @Inject
    ApplicationConfig appConfig;

    private final DownloadRepository repository;

    /**
     * Checked Templates ensure type-safety in html templating.
     */
    @CheckedTemplate
    public static class Templates {
        /**
         * The method name of a `static native TemplateInstance` refers to the name of a .html file in templates/DownloadResource.
         *
         * @param template all data accessible by the template
         * @return a Template with values from template filled in
         */
        public static native TemplateInstance index(IndexTemplate template);
    }

    @Inject
    public IndexResource(DownloadRepository repository) {
        this.repository = repository;
    }

    @RouteFilter
    public void localeMiddleware(RoutingContext rc) {
        // @CookieParam(ATTRIBUTE_LOCALE) String locale
        // order: query, cookie, header
        // if query parameter `locale` is set, update cookie and Accept-Language header
        // qute uses the Accept-Language header
        String defaultLocale = appConfig.getDefaultLocale().getLanguage();
        Cookie localeCookie = rc.getCookie(ATTRIBUTE_LOCALE);

        List<String> localeQuery = rc.queryParam(ATTRIBUTE_LOCALE);
        if (localeQuery.size() > 0)
            localeCookie = Cookie.cookie(ATTRIBUTE_LOCALE, localeQuery.get(0));

        // if cookie & query-param aren't set, use header
        if (localeCookie == null || localeCookie.getValue().equals("")) {
            String acceptLanguage = rc.request().getHeader("Accept-Language");
            if (acceptLanguage == null) {
                LOG.info("locale - using default");
                localeCookie = Cookie.cookie(ATTRIBUTE_LOCALE, defaultLocale);
            } else {
                LOG.info("locale - using Accept-Language: " + acceptLanguage);
                acceptLanguage = new LocaleConverter().convert(acceptLanguage).getLanguage();
                localeCookie = Cookie.cookie(ATTRIBUTE_LOCALE, acceptLanguage);
            }
        }

        // qute did the Accept-Language parsing for us, but to set the correct cookie we need to parse it outselves
        // how does quarks/qute do it'
        localeCookie.setPath("/");
        LOG.info("locale - using: " + localeCookie.getValue());
        rc.response().addCookie(localeCookie);
        rc.request().headers().set("Accept-Language", localeCookie.getValue());
        rc.next();
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance get(@HeaderParam("user-agent") String ua) {
        AppMessages bundle = MessageBundles.get(AppMessages.class);

        UserSystem clientSystem = UserAgentParser.getOsAndArch(ua);
        if (clientSystem.getOs() == null) {
            LOG.warnf("no OS detected for ua: %s", ua);
            IndexTemplate data = new IndexTemplate(bundle.welcomeClientOsUndetected());
            return Templates.index(data);
        }

        Download recommended = repository.getUserDownload(clientSystem.getOs(), clientSystem.getArch());
        if (recommended == null) {
            LOG.warnf("no binary found for clientSystem: %s", clientSystem);
            IndexTemplate data = new IndexTemplate(bundle.welcomeClientOsUnsupported());
            return Templates.index(data);
        }

        String thankYouPath = repository.buildThankYouPath(recommended);
        LOG.infof("user: %s -> [%s] binary: %s", clientSystem, thankYouPath, recommended);

        Binary binary = recommended.getBinary();
        IndexTemplate data = new IndexTemplate(binary.getOs(), binary.getArchitecture(), recommended.getSemver(), binary.getProject(), thankYouPath);
        return Templates.index(data);
    }
}

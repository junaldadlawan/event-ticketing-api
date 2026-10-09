package com.junaldadlawan.event_ticketing_api.upload.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

/**
 * Knows what "one of our own uploaded files" looks like as a URL, for the places that store such a URL
 * (a profile or post picture) and must not accept just any link: the URL must point at this server's own
 * {@code /api/v1/uploads/files/<name>}, with a name {@link ImageStorageService} could have generated, and the file
 * must really exist. Anything else - another host, a path trick, a query string, a missing file - is not ours.
 * <p>
 * It also removes a file that nothing refers to any more (a replaced profile or post picture), so uploads do not pile up.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadedFileUrls {

    private static final String FILES_PATH = "/api/v1/uploads/files/";

    private final ImageStorageService imageStorageService;

    @PersistenceContext
    private EntityManager entityManager;

    /** The same optional fixed public origin the upload endpoint builds its URLs from. */
    @Value("${app.upload.public-base-url:}")
    private String publicBaseUrl;

    /** The stored file name behind {@code url}, if it is one of our own existing upload URLs. */
    public Optional<String> ownFileName(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
            return Optional.empty();
        }
        String path = uri.getRawPath();
        if (path == null || !path.startsWith(FILES_PATH)) {
            return Optional.empty();
        }
        String name = path.substring(FILES_PATH.length());
        if (!ImageStorageService.isStoredName(name) || !imageStorageService.exists(name)) {
            return Optional.empty();
        }
        String expectedOrigin = expectedOrigin();
        if (expectedOrigin != null && !expectedOrigin.equalsIgnoreCase(originOf(uri))) {
            return Optional.empty();
        }
        return Optional.of(name);
    }

    /** Deletes the file behind {@code url} unless something still refers to it. Best effort: never throws. */
    public void deleteIfUnreferenced(String url) {
        try {
            Optional<String> name = ownFileName(url);
            if (name.isPresent() && !isReferenced(url)) {
                imageStorageService.delete(name.get());
            }
        } catch (RuntimeException e) {
            log.warn("Could not clean up an unused upload", e);
        }
    }

    /** Whether any profile picture, post picture, ticket template, event image or organization document still uses {@code url}. */
    boolean isReferenced(String url) {
        return exists("select count(u) from User u where u.avatarUrl = :url", url)
                || exists("select count(p) from Post p where p.imageUrl = :url and p.deletedAt is null", url)
                || exists("select count(t) from TicketTemplate t where t.backgroundImageUrl = :url or t.logoUrl = :url", url)
                || exists("select count(e) from Event e join e.images i where i = :url", url)
                || exists("select count(o) from Organization o join o.documents d where d.url = :url", url);
    }

    private boolean exists(String jpql, String url) {
        Long count = entityManager.createQuery(jpql, Long.class).setParameter("url", url).getSingleResult();
        return count != null && count > 0;
    }

    /** The origin uploads are served from: the configured public one, else this request's; null outside a web request. */
    private String expectedOrigin() {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            try {
                return originOf(URI.create(publicBaseUrl.trim()));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        if (RequestContextHolder.getRequestAttributes() == null) {
            return null;
        }
        try {
            return originOf(URI.create(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return null;
        }
    }

    private static String originOf(URI uri) {
        if (uri.getScheme() == null || uri.getHost() == null) {
            return "";
        }
        int port = uri.getPort();
        boolean defaultPort = port == -1 || (port == 80 && "http".equalsIgnoreCase(uri.getScheme()))
                || (port == 443 && "https".equalsIgnoreCase(uri.getScheme()));
        return uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase() + (defaultPort ? "" : ":" + port);
    }
}

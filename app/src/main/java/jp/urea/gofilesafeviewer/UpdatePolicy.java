package jp.urea.gofilesafeviewer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;

/** Update traffic is independent of the WebView allowlist. No credentials are used. */
final class UpdatePolicy {
    static final String APPLICATION_ID = "jp.urea.gofilesafeviewer";
    static final String RELEASE_ROOT = "https://github.com/urea/gofile-safe-viewer/releases/";
    static final String MANIFEST_URL = RELEASE_ROOT + "latest/download/update.json";
    static final long MAX_APK_BYTES = 100L * 1024 * 1024;
    static final int MAX_MANIFEST_BYTES = 32768;

    private UpdatePolicy() { }

    static boolean allowsRedirect(String value, String assetName) {
        if (!"update.json".equals(assetName) && !"GofileSafeViewer.apk".equals(assetName)) return false;
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            String path = uri.getRawPath();
            if ("github.com".equals(host)) {
                if (uri.getRawQuery() != null) return false;
                String prefix = "/urea/gofile-safe-viewer/releases/";
                return ("update.json".equals(assetName) && path.equals(prefix + "latest/download/update.json"))
                        || path.matches(prefix + "download/v[0-9]+\\.[0-9]+\\.[0-9]+/" + java.util.regex.Pattern.quote(assetName));
            }
            // GitHub release-asset storage for this repository only. Signed CDN queries are allowed.
            if ("release-assets.githubusercontent.com".equals(host)) {
                return path.matches("/github-production-release-asset/1368944764/[a-zA-Z0-9-]+");
            }
            return "objects.githubusercontent.com".equals(host)
                    && path.matches("/github-production-release-asset-[a-zA-Z0-9]+/1368944764/[a-zA-Z0-9-]+");
        } catch (URISyntaxException | NullPointerException ex) {
            return false;
        }
    }

    static boolean validRelease(String version, String apkUrl) {
        return version != null && version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                && (RELEASE_ROOT + "download/v" + version + "/GofileSafeViewer.apk").equals(apkUrl);
    }

    static boolean validSha256(String value) {
        return value != null && value.matches("[a-fA-F0-9]{64}");
    }

    static boolean validSize(long value) { return value > 0 && value <= MAX_APK_BYTES; }
    static boolean isNewer(long candidate, long current) {
        return candidate > current && candidate <= Integer.MAX_VALUE;
    }

    static boolean sameSigners(byte[][] installed, byte[][] candidate) {
        if (installed == null || candidate == null || installed.length == 0 || installed.length != candidate.length) return false;
        String[] a = new String[installed.length];
        String[] b = new String[candidate.length];
        for (int i = 0; i < a.length; i++) {
            if (installed[i] == null || candidate[i] == null) return false;
            a[i] = hex(installed[i]);
            b[i] = hex(candidate[i]);
        }
        Arrays.sort(a);
        Arrays.sort(b);
        return Arrays.equals(a, b);
    }

    static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }
}

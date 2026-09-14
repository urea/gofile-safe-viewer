package jp.urea.gofilesafeviewer;

import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class UpdatePolicyTest {
    @Test public void acceptsOnlyProjectReleasePaths() {
        assertTrue(UpdatePolicy.allowsRedirect(UpdatePolicy.MANIFEST_URL, "update.json"));
        assertTrue(UpdatePolicy.allowsRedirect(UpdatePolicy.RELEASE_ROOT + "download/v0.10.0/update.json", "update.json"));
        assertTrue(UpdatePolicy.allowsRedirect(UpdatePolicy.RELEASE_ROOT + "download/v0.11.0/GofileSafeViewer.apk", "GofileSafeViewer.apk"));
    }
    @Test public void rejectsSpoofedOrNonHttpsOrigins() {
        String[] urls = { "http://github.com/urea/gofile-safe-viewer/releases/latest/download/update.json",
            "https://github.com.evil.example/urea/gofile-safe-viewer/releases/latest/download/update.json",
            "https://user@github.com/urea/gofile-safe-viewer/releases/latest/download/update.json",
            "https://github.com:444/urea/gofile-safe-viewer/releases/latest/download/update.json",
            "https://github.com/other/project/releases/latest/download/update.json",
            "https://github.com/urea/gofile-safe-viewer/releases/latest/download/update.json?token=secret",
            "https://github.com/urea/gofile-safe-viewer/releases/latest/download/update.json#fragment",
            "https://github.com/urea/gofile-safe-viewer/releases/download/v1.0.0/%75pdate.json",
            "https://example.com/?gofile=update.json", "file:///update.json", "javascript:alert(1)", "", null };
        for (String value : urls) assertFalse(String.valueOf(value), UpdatePolicy.allowsRedirect(value, "update.json"));
    }
    @Test public void restrictsAssetCdnToThisRepository() {
        assertTrue(UpdatePolicy.allowsRedirect("https://release-assets.githubusercontent.com/github-production-release-asset/1368944764/abc-123?sig=test", "GofileSafeViewer.apk"));
        assertTrue(UpdatePolicy.allowsRedirect("https://objects.githubusercontent.com/github-production-release-asset-2e65be/1368944764/abc-123?sig=test", "update.json"));
        assertFalse(UpdatePolicy.allowsRedirect("https://release-assets.githubusercontent.com/github-production-release-asset/999/abc-123", "update.json"));
        assertFalse(UpdatePolicy.allowsRedirect("https://release-assets.githubusercontent.com.evil.example/github-production-release-asset/1368944764/abc", "update.json"));
        assertFalse(UpdatePolicy.allowsRedirect("https://release-assets.githubusercontent.com/github-production-release-asset/1368944764/../abc", "update.json"));
        assertFalse(UpdatePolicy.allowsRedirect(UpdatePolicy.MANIFEST_URL, "unexpected.apk"));
    }
    @Test public void usesVersionCodeNotLexicalVersionOrdering() {
        assertTrue(UpdatePolicy.isNewer(10, 9)); assertTrue(UpdatePolicy.isNewer(100, 99));
        assertFalse(UpdatePolicy.isNewer(10, 10)); assertFalse(UpdatePolicy.isNewer(9, 10));
        assertFalse(UpdatePolicy.isNewer(-1, 10)); assertFalse(UpdatePolicy.isNewer(Long.MAX_VALUE, 10));
    }
    @Test public void checksSizeAndHashFormat() {
        assertTrue(UpdatePolicy.validSize(1)); assertTrue(UpdatePolicy.validSize(UpdatePolicy.MAX_APK_BYTES));
        assertFalse(UpdatePolicy.validSize(0)); assertFalse(UpdatePolicy.validSize(-1)); assertFalse(UpdatePolicy.validSize(UpdatePolicy.MAX_APK_BYTES + 1));
        assertTrue(UpdatePolicy.validSha256(new String(new char[64]).replace('\0', 'a')));
        assertFalse(UpdatePolicy.validSha256("abc")); assertFalse(UpdatePolicy.validSha256(new String(new char[64]).replace('\0', 'g')));
        assertEquals("00ff12", UpdatePolicy.hex(new byte[] {0, (byte) 255, 18}));
    }
    @Test public void rejectsDifferentOrEmptySigners() {
        byte[][] a = { {1, 2, 3}, {4, 5, 6} }; byte[][] b = { {4, 5, 6}, {1, 2, 3} };
        assertTrue(UpdatePolicy.sameSigners(a, b));
        assertFalse(UpdatePolicy.sameSigners(a, new byte[][] {{1, 2, 3}}));
        assertFalse(UpdatePolicy.sameSigners(a, new byte[][] {{4, 5, 6}, {1, 2, 4}}));
        assertFalse(UpdatePolicy.sameSigners(null, a)); assertFalse(UpdatePolicy.sameSigners(new byte[0][], new byte[0][]));
    }
    private JSONObject validMetadata() throws Exception {
        return new JSONObject().put("schemaVersion", 1).put("applicationId", UpdatePolicy.APPLICATION_ID)
            .put("versionCode", 11).put("versionName", "0.11.0").put("minSdk", 23).put("sizeBytes", 1000)
            .put("sha256", new String(new char[64]).replace('\0', 'a'))
            .put("apkUrl", UpdatePolicy.RELEASE_ROOT + "download/v0.11.0/GofileSafeViewer.apk")
            .put("releaseNotes", "テスト更新");
    }
    @Test public void readsValidManifest() throws Exception {
        UpdateManager.Release release = UpdateManager.Release.parse(validMetadata().toString());
        assertEquals(11, release.versionCode); assertEquals("テスト更新", release.notes); assertEquals(1000, release.size);
    }
    private void rejects(JSONObject json) {
        try { UpdateManager.Release.parse(json.toString()); fail("invalid manifest accepted"); } catch (Exception expected) { }
    }
    @Test public void rejectsWrongPackageSchemaAndVersion() throws Exception {
        rejects(validMetadata().put("applicationId", "evil.example")); rejects(validMetadata().put("schemaVersion", 2));
        rejects(validMetadata().put("versionCode", -1)); rejects(validMetadata().put("versionCode", Long.MAX_VALUE));
        rejects(validMetadata().put("minSdk", 0)); rejects(validMetadata().put("minSdk", 1000));
    }
    @Test public void rejectsTamperedDownloadMetadata() throws Exception {
        rejects(validMetadata().put("sha256", "bad")); rejects(validMetadata().put("sizeBytes", 0));
        rejects(validMetadata().put("sizeBytes", UpdatePolicy.MAX_APK_BYTES + 1));
        rejects(validMetadata().put("apkUrl", "https://evil.example/update.apk"));
        rejects(validMetadata().put("apkUrl", UpdatePolicy.RELEASE_ROOT + "download/v0.12.0/GofileSafeViewer.apk"));
        rejects(validMetadata().put("releaseNotes", new String(new char[8001])));
    }
    @Test public void rejectsMissingFieldsAndInvalidJson() throws Exception {
        JSONObject json = validMetadata(); json.remove("sha256"); rejects(json);
        try { UpdateManager.Release.parse("<html>error</html>"); fail(); } catch (Exception expected) { }
    }
}

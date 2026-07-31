package ru.moscow.foxkiss.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.sql.DataSource;
import java.util.HexFormat;
import java.sql.Connection;

public final class H2LibraryLoader {

    private static final String h2Version = "2.2.224";
    private static final String nameLibrary = "h2-" + h2Version + ".jar";
    private static final String mavenbaseUrl = "https://repo1.maven.org/maven2/com/h2database/h2/" + h2Version + "/";

    private H2LibraryLoader() {
    }

    public static DataSource loadDataSource(JavaPlugin plugin, String jdbcUrl) {
        Path librariesDirectory = plugin.getDataFolder().toPath().resolve("libraries");
        Path library = librariesDirectory.resolve(nameLibrary);

        try {
            Files.createDirectories(librariesDirectory);
            if (!Files.exists(library)) {
                plugin.getLogger().info("Downloading H2 JDBC driver " + h2Version + "...");
                downloadAndVerify(library);
            }

            URLClassLoader classLoader = new URLClassLoader(
                    new java.net.URL[]{library.toUri().toURL()},
                    H2LibraryLoader.class.getClassLoader()
            );
            Class<?> poolClass = Class.forName("org.h2.jdbcx.JdbcConnectionPool", true, classLoader);
            DataSource dataSource = createPool(poolClass, jdbcUrl, "SA");
            try (Connection ignored = dataSource.getConnection()) {
                return dataSource;
            } catch (Exception primaryException) {
                dispose(dataSource);
                DataSource legacyDataSource = createPool(poolClass, jdbcUrl, "");
                try (Connection ignored = legacyDataSource.getConnection()) {
                    plugin.getLogger().warning("H2 database uses a legacy empty user name; it will be kept for compatibility.");
                    return legacyDataSource;
                } catch (Exception legacyException) {
                    dispose(legacyDataSource);
                    primaryException.addSuppressed(legacyException);
                    throw primaryException;
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load H2 JDBC driver", exception);
        }
    }

    private static DataSource createPool(Class<?> poolClass, String jdbcUrl, String username) throws ReflectiveOperationException {
        Object pool = poolClass.getMethod("create", String.class, String.class, String.class)
                .invoke(null, jdbcUrl, username, "");
        if (pool instanceof DataSource dataSource) {
            return dataSource;
        }
        throw new IllegalStateException("Downloaded library does not provide an H2 connection pool");
    }

    private static void dispose(DataSource dataSource) {
        try {
            dataSource.getClass().getMethod("dispose").invoke(dataSource);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void downloadAndVerify(Path library) throws IOException, NoSuchAlgorithmException {
        Path temporaryFile = library.resolveSibling(nameLibrary + ".tmp");
        try (InputStream input = URI.create(mavenbaseUrl + nameLibrary).toURL().openStream()) {
            Files.copy(input, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
        }

        String expectedChecksum;
        try (InputStream input = URI.create(mavenbaseUrl + nameLibrary + ".sha1").toURL().openStream()) {
            expectedChecksum = new String(input.readAllBytes()).trim().split("\\s+")[0];
        }

        String actualChecksum = sha1(temporaryFile);
        if (!expectedChecksum.equalsIgnoreCase(actualChecksum)) {
            Files.deleteIfExists(temporaryFile);
            throw new IOException("Downloaded H2 driver checksum does not match Maven Central");
        }

        Files.move(temporaryFile, library, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String sha1(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) != -1; ) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}

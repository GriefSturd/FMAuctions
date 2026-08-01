package ru.moscow.foxkiss.database;

import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HexFormat;

public final class H2LibraryLoader {

    private static final String version = "2.2.224";
    private static final String library = "h2-" + version + ".jar";
    private static final String base = "https://repo1.maven.org/maven2/com/h2database/h2/" + version + "/";

    private static URLClassLoader classLoader;
    private static final Object lock = new Object();

    private H2LibraryLoader() {
    }

    public static DataSource loadDataSource(JavaPlugin plugin, String jdbcUrl) {
        URLClassLoader loader = getClassLoader(plugin);
        try {
            Class<?> poolClass = Class.forName("org.h2.jdbcx.JdbcConnectionPool", true, loader);
            Object pool = poolClass.getMethod("create", String.class, String.class, String.class)
                    .invoke(null, jdbcUrl, "SA", "");

            DataSource dataSource = (DataSource) pool;
            try (Connection ignored = dataSource.getConnection()) {
                plugin.getLogger().info("Подключение к H2 успешно установлено.");
                return dataSource;
            } catch (SQLException e) {
                plugin.getLogger().warning("Не удалось подключиться с пользователем 'SA', пробуем пустой пользователь.");
                dispose(pool);
                Object fallbackPool = poolClass.getMethod("create", String.class, String.class, String.class)
                        .invoke(null, jdbcUrl, "", "");
                DataSource fallbackDS = (DataSource) fallbackPool;
                try (Connection ignored2 = fallbackDS.getConnection()) {
                    plugin.getLogger().warning("Используется пустой пользователь для совместимости.");
                    return fallbackDS;
                } catch (SQLException e2) {
                    dispose(fallbackPool);
                    e.addSuppressed(e2);
                    throw new IllegalStateException("Не удалось подключиться к H2", e);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Ошибка создания DataSource H2", e);
        }
    }

    private static void dispose(Object pool) {
        try {
            pool.getClass().getMethod("dispose").invoke(pool);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static URLClassLoader getClassLoader(JavaPlugin plugin) {
        if (classLoader != null) {
            return classLoader;
        }
        synchronized (lock) {
            if (classLoader != null) {
                return classLoader;
            }
            Path librariesDir = plugin.getDataFolder().toPath().resolve("libraries");
            Path libraryFile = librariesDir.resolve(library);
            try {
                Files.createDirectories(librariesDir);
                if (!Files.exists(libraryFile)) {
                    plugin.getLogger().info("Загрузка H2 драйвера версии " + version + "...");
                    downloadAndVerify(libraryFile);
                }
                classLoader = new URLClassLoader(new URL[]{libraryFile.toUri().toURL()}, H2LibraryLoader.class.getClassLoader());
                return classLoader;
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new IllegalStateException("Не удалось загрузить H2 библиотеку", e);
            }
        }
    }

    private static void downloadAndVerify(Path targetPath) throws IOException, NoSuchAlgorithmException {
        Path temp = targetPath.resolveSibling(library + ".tmp");
        try (InputStream in = URI.create(base + library).toURL().openStream()) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        String expected;
        try (InputStream in = URI.create(base + library + ".sha1").toURL().openStream()) {
            expected = new String(in.readAllBytes()).trim().split("\\s+")[0];
        }
        String actual = sha1(temp);
        if (!expected.equalsIgnoreCase(actual)) {
            Files.deleteIfExists(temp);
            throw new IOException("Контрольная сумма скачанного H2 драйвера не совпадает с Maven Central");
        }
        Files.move(temp, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String sha1(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
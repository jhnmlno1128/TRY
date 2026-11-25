package brgy.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** MySQL connection settings. Defaults match a fresh XAMPP install; override them in db.properties. */
public class DbConfig {

    public final String host;
    public final int port;
    public final String user;
    public final String password;
    public final String database;

    public DbConfig(String host, int port, String user, String password, String database) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.database = database;
    }

    /** localhost:3306, root, no password, database "bris" (XAMPP defaults). */
    public static DbConfig defaults() {
        return new DbConfig("localhost", 3306, "root", "", "bris");
    }

    /** Reads db.properties (keys: host, port, user, password, database). Missing file or keys = defaults. */
    public static DbConfig load(Path file) {
        DbConfig d = defaults();
        if (file == null || !Files.isRegularFile(file)) return d;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file + ": " + e.getMessage(), e);
        }
        int port = d.port;
        try { port = Integer.parseInt(p.getProperty("port", String.valueOf(d.port)).trim()); }
        catch (NumberFormatException e) { throw new IllegalStateException("db.properties: port must be a number."); }
        return new DbConfig(p.getProperty("host", d.host).trim(), port, p.getProperty("user", d.user).trim(),
                p.getProperty("password", d.password), p.getProperty("database", d.database).trim());
    }

    public DbConfig withDatabase(String name) {
        return new DbConfig(host, port, user, password, name);
    }

    public String serverUrl() { return url(""); }
    public String databaseUrl() { return url(database); }

    private String url(String db) {
        return "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true";
    }
}

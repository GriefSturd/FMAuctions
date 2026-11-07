package ru.moscow.foxkiss.database.h2;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class SqlRuntime {
    private final Semaphore connections = new Semaphore(8, true);
    private final Semaphore requests = new Semaphore(72, true);
    private final Map<String, Samples> metrics = new HashMap<>();
    private long revision, rejected;
    private int writers;

    public synchronized long cacheRevision() { return writers == 0 ? revision : -1; }
    public synchronized void invalidateCache() { revision++; }
    private synchronized void beginWrite() { writers++; revision++; }
    private synchronized void endWrite() { writers--; revision++; }

    public Connection open(DataSource source) throws SQLException {
        if (!requests.tryAcquire()) {
            synchronized (this) { rejected++; }
            throw new SQLTransientConnectionException("Очередь запросов к базе данных заполнена");
        }
        long start = System.nanoTime();
        boolean acquired = false;
        try {
            acquired = connections.tryAcquire(2, TimeUnit.SECONDS);
            if (!acquired) throw new SQLTransientConnectionException("Истекло время ожидания доступа к базе данных");
            Connection raw = source.getConnection();
            record("Ожидание соединения", System.nanoTime() - start, false);
            return wrap(raw);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (acquired) connections.release();
            requests.release();
            throw new SQLTransientConnectionException("Ожидание соединения прервано", e);
        } catch (SQLException | RuntimeException e) {
            if (acquired) connections.release();
            requests.release();
            record("Ожидание соединения", System.nanoTime() - start, true);
            throw e;
        }
    }

    private Connection wrap(Connection raw) {
        class State implements InvocationHandler {
            boolean closed, dirty;
            void dirty() { if (!dirty) { dirty = true; beginWrite(); } }
            void clean() { if (dirty) { dirty = false; endWrite(); } }
            @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                if (name.equals("close")) {
                    if (closed) return null;
                    closed = true;
                    try { return call(raw, method, args); }
                    finally { clean(); connections.release(); requests.release(); }
                }
                Object result = call(raw, method, args);
                if (name.equals("commit") || name.equals("rollback") || (name.equals("setAutoCommit") && Boolean.TRUE.equals(args[0]))) clean();
                if (!(result instanceof Statement statement)) return result;
                String sql = args != null && args.length > 0 && args[0] instanceof String text ? text : "";
                Class<?> api = statement instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
                return Proxy.newProxyInstance(SqlRuntime.class.getClassLoader(), new Class<?>[]{api}, (p, m, values) -> {
                    if (m.getName().equals("getConnection")) return proxy;
                    if (!m.getName().startsWith("execute")) return call(statement, m, values);
                    String query = values != null && values.length > 0 && values[0] instanceof String text ? text : sql;
                    boolean write = changesCatalog(query);
                    if (write) dirty();
                    long start = System.nanoTime();
                    boolean error = false;
                    try { return call(statement, m, values); }
                    catch (Throwable failure) { error = true; throw failure; }
                    finally {
                        record(query.isEmpty() ? m.getName() : query, System.nanoTime() - start, error);
                        if (write && raw.getAutoCommit()) clean();
                    }
                });
            }
        }
        return (Connection) Proxy.newProxyInstance(SqlRuntime.class.getClassLoader(), new Class<?>[]{Connection.class}, new State());
    }

    private static boolean changesCatalog(String sql) {
        if (sql.stripLeading().regionMatches(true, 0, "SELECT", 0, 6)) return false;
        String query = sql.stripLeading().toLowerCase(java.util.Locale.ENGLISH);
        return query.contains("auction_items") || query.contains("auction_player_profiles") || query.contains("sales_history");
    }

    private static Object call(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException e) { throw e.getCause(); }
    }

    private synchronized void record(String sql, long nanos, boolean error) {
        String key = sql;
        if (!metrics.containsKey(key) && metrics.size() >= 64) key = "Прочие запросы";
        Samples sample = metrics.computeIfAbsent(key, ignored -> new Samples());
        sample.times[(int) (sample.count++ % sample.times.length)] = nanos;
        if (error) sample.errors++;
    }

    public synchronized void clearSamples() { metrics.clear(); rejected = 0; }

    public synchronized List<String> report() {
        List<String> result = new ArrayList<>();
        int active = 8 - connections.availablePermits();
        result.add("БД: активных " + active + "/8, ожидающих " + Math.max(0, 72 - requests.availablePermits() - active) + "/64, отклонено " + rejected);
        List<String> keys = new ArrayList<>(metrics.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            Samples sample = metrics.get(key);
            long[] values = Arrays.copyOf(sample.times, (int) Math.min(sample.count, sample.times.length));
            Arrays.sort(values);
            String label = key.length() > 180 ? key.substring(0, 70) + " … " + key.substring(key.length() - 100) : key;
            String mode = key.contains("COUNT(*) OVER()") ? "[WINDOW] " : key.startsWith("SELECT COUNT(*)") ? "[COUNT] " : "";
            result.add(mode + label + " | n=" + sample.count + " ошибок=" + sample.errors + " p50=" + percentile(values, .50)
                    + " p95=" + percentile(values, .95) + " p99=" + percentile(values, .99) + " мс");
        }
        return List.copyOf(result);
    }

    private static double percentile(long[] sorted, double p) {
        return sorted.length == 0 ? 0 : Math.round(sorted[(int) Math.ceil(sorted.length * p) - 1] / 1000.0) / 1000.0;
    }
    private static final class Samples {
        final long[] times = new long[1024];
        long count, errors;
    }
}

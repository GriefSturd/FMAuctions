package ru.moscow.foxkiss.utils;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.util.*;
import java.util.function.Predicate;

public final class MaterialSearchIndex {
    private final Node root = new Node();
    private final Map<String, List<Alias>> aliases;

    private record Alias(String name, String normalized) {}

    public MaterialSearchIndex(Map<String, String> translations, Collection<String> materialIds) {
        Map<String, List<Alias>> searchable = new HashMap<>();
        for (String material : materialIds) {
            char[] alias = material.toCharArray();
            for (int i = 0; i < alias.length; i++) alias[i] = Character.toLowerCase(alias[i]);
            String englishName = new String(alias);
            add(material, englishName);
            searchable.put(material, new ArrayList<>(List.of(new Alias(englishName, normalize(material)))));
        }
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            String name = entry.getValue();
            if (name != null && !name.isBlank()) {
                String plainName = PlainTextComponentSerializer.plainText().serialize(TextUtils.component(name));
                if (!plainName.isBlank()) {
                    add(entry.getKey(), plainName);
                    searchable.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                            .add(new Alias(plainName, normalize(plainName)));
                }
            }
        }

        Map<String, List<Alias>> immutableAliases = new HashMap<>();
        for (Map.Entry<String, List<Alias>> entry : searchable.entrySet()) {
            immutableAliases.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        aliases = Map.copyOf(immutableAliases);

        ArrayDeque<Node> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            node.edges = new char[node.children.size()];
            int position = 0;
            for (Map.Entry<Character, Node> edge : node.children.entrySet()) {
                node.edges[position++] = edge.getKey();
                pending.push(edge.getValue());
            }
            Arrays.sort(node.edges);
            node.items.sort(Comparator.comparing(Match::material));
        }
    }

    public List<String> findMaterials(String query, int limit) {
        String needle = normalize(query);
        List<String> result = new ArrayList<>();
        if (needle.isEmpty() || limit <= 0) return result;

        for (Map.Entry<String, List<Alias>> entry : aliases.entrySet()) {
            for (Alias alias : entry.getValue()) {
                if (alias.normalized().contains(needle)) {
                    result.add(entry.getKey());
                    break;
                }
            }
            if (result.size() >= limit) break;
        }
        return result;
    }

    public List<String> findMaterials(String query) {
        return findMaterials(query, Integer.MAX_VALUE);
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase().replace('_', ' ');
    }

    private void add(String material, String name) {
        Node node = root;
        for (int i = 0; i < name.length(); i++) {
            node = node.children.computeIfAbsent(Character.toLowerCase(name.charAt(i)), ignored -> new Node());
        }
        for (Match existing : node.items) {
            if (existing.material().equals(material) && existing.name().equalsIgnoreCase(name)) return;
        }
        node.items.add(new Match(material, name));
    }

    public List<Match> find(String prefix, int limit) {
        return find(prefix, limit, ignored -> true);
    }

    public List<Match> find(String prefix, int limit, Predicate<String> acceptsMaterial) {
        if (limit <= 0 || prefix.isEmpty()) return List.of();
        Node node = findNode(prefix);
        if (node == null) return List.of();
        List<Match> result = new ArrayList<>();
        ArrayDeque<Node> pending = new ArrayDeque<>();
        pending.push(node);
        while (!pending.isEmpty() && result.size() < limit) {
            Node current = pending.pop();
            for (Match item : current.items) {
                if (!acceptsMaterial.test(item.material())) continue;
                result.add(item);
                if (result.size() == limit) return List.copyOf(result);
            }
            for (int i = current.edges.length - 1; i >= 0; i--) {
                pending.push(current.children.get(current.edges[i]));
            }
        }
        return List.copyOf(result);
    }

    public String resolveMaterial(String name, Set<String> allowedMaterials) {
        String id = name.replace(' ', '_').toUpperCase();
        if (allowedMaterials.contains(id)) return id;

        Node node = findNode(name.replace('_', ' '));
        if (node == null) return null;

        String result = null;
        for (Match item : node.items) {
            if (!allowedMaterials.contains(item.material())) continue;
            if (result != null && !result.equals(item.material())) return null;
            result = item.material();
        }
        return result;
    }

    public List<String> findInventoryAliases(String prefix, int consumed, Set<String> materials) {
        String needle = prefix.replace('_', ' ');
        Set<String> result = new TreeSet<>();
        for (String material : materials) {
            List<Alias> names = aliases.get(material);
            if (names == null) continue;
            for (Alias alias : names) {
                if (alias.normalized().regionMatches(true, 0, needle, 0, needle.length())) {
                    String name = alias.name();
                    if (consumed < name.length()) {
                        result.add(name.substring(consumed));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private Node findNode(String text) {
        Node node = root;
        for (int i = 0; i < text.length(); i++) {
            node = node.children.get(Character.toLowerCase(text.charAt(i)));
            if (node == null) return null;
        }
        return node;
    }

    public record Match(String material, String name) {}

    private static final class Node {
        final Map<Character, Node> children = new HashMap<>();
        final List<Match> items = new ArrayList<>();
        char[] edges;
    }
}
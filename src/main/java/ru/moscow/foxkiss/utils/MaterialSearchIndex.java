package ru.moscow.foxkiss.utils;

import java.util.*;

/** Immutable after construction; safe to publish as one snapshot to SQL workers. */
public final class MaterialSearchIndex {
    private final Node root = new Node();

    public MaterialSearchIndex(Map<String, String> translations) {
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            String name = entry.getValue();
            if (name == null || name.isBlank()) continue;
            Node node = root;
            for (int i = 0; i < name.length(); i++) {
                char key = Character.toLowerCase(name.charAt(i));
                node = node.children.computeIfAbsent(key, ignored -> new Node());
            }
            node.items.add(new Match(entry.getKey(), name));
        }
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

    public List<Match> find(String prefix, int limit) {
        if (limit <= 0 || prefix.isEmpty()) return List.of();
        Node node = root;
        for (int i = 0; i < prefix.length(); i++) {
            node = node.children.get(Character.toLowerCase(prefix.charAt(i)));
            if (node == null) return List.of();
        }
        List<Match> result = new ArrayList<>();
        ArrayDeque<Node> pending = new ArrayDeque<>();
        pending.push(node);
        while (!pending.isEmpty() && result.size() < limit) {
            Node current = pending.pop();
            for (Match item : current.items) {
                result.add(item);
                if (result.size() == limit) return List.copyOf(result);
            }
            for (int i = current.edges.length - 1; i >= 0; i--) {
                pending.push(current.children.get(current.edges[i]));
            }
        }
        return List.copyOf(result);
    }

    public record Match(String material, String name) {}

    private static final class Node {
        final Map<Character, Node> children = new HashMap<>();
        final List<Match> items = new ArrayList<>();
        char[] edges;
    }
}

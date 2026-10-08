package org.odisee.document

import java.nio.file.Files
import java.nio.file.Path

final class PathTree {

    private PathTree() {
    }

    static void delete(Path root) {
        if (root == null || !Files.exists(root)) {
            return
        }
        Files.walk(root).withCloseable { stream ->
            stream.sorted { a, b -> b.compareTo(a) }.each { Files.deleteIfExists(it) }
        }
    }

}

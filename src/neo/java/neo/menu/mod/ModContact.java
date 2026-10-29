package neo.menu.mod;

import java.util.Optional;

/** 作者・貢献者の連絡先 (email など)。 */
@FunctionalInterface
public interface ModContact {
    Optional<String> get(String key);
}

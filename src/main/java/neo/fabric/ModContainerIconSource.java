package neo.fabric;

import neo.menu.mod.IconSource;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.nio.file.Path;

/** Fabric の ModContainer をアイコンの読み出し元として扱うアダプタ。 */
final class ModContainerIconSource implements IconSource {
    private final ModContainer container;

    private ModContainerIconSource(ModContainer container) {
        this.container = container;
    }

    static ModContainerIconSource of(String modId) {
        ModContainer container = FabricLoader.getInstance()
                .getModContainer(modId)
                .orElseThrow(() -> new RuntimeException("Cannot get ModContainer for Fabric mod with id " + modId));
        return new ModContainerIconSource(container);
    }

    @Override
    public Path getPath(String relativePath) {
        return container.getPath(relativePath);
    }

    @Override
    public String getId() {
        return container.getMetadata().getId();
    }
}

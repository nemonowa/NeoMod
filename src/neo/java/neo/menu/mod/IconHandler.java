package neo.menu.mod;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import neo.util.NeoAssets;

public class IconHandler implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mod Manager | IconHandler");

    private final Map<Path, DynamicTexture> modIconCache = new HashMap<>();
    private @Nullable DynamicTexture vanillaPackIcon;

    public DynamicTexture createIcon(IconSource iconSource, String iconPath) {
        try {
            Path path = iconSource.getPath(iconPath);
            DynamicTexture cachedIcon = getCachedModIcon(path);

            if (cachedIcon != null) {
                return cachedIcon;
            }

            try (InputStream inputStream = Files.newInputStream(path)) {
                NativeImage image = NativeImage.read(Objects.requireNonNull(inputStream));
                Validate.validState(image.getHeight() == image.getWidth(), "Must be square icon");
                DynamicTexture tex = new DynamicTexture(() -> Identifier.fromNamespaceAndPath(NeoAssets.NAMESPACE, iconPath).toString(), image);
                cacheModIcon(path, tex);

                return tex;
            }
        } catch (IllegalStateException e) {
            if (e.getMessage().equals("Must be square icon")) {
                LOGGER.error("Mod icon must be a square for icon source {}: {}",
                        iconSource.getId(),
                        iconPath
                );
            }

            return null;
        } catch (Throwable t) {
            if (!iconPath.equals("assets/" + iconSource.getId() + "/icon.png")) {
                LOGGER.error("Invalid mod icon for icon source {}: {}", iconSource.getId(), iconPath);
            }

            return null;
        }
    }

    /**
     * バニラの標準リソースパックのアイコン (pack.png。標準の草のブロック) を読んで、Minecraft の Mod のアイコンにする。
     * この Mod は画像を持たず、バニラのアセットをそのまま参照する。読み方はバニラのリソースパック選択画面と同じ
     * (Pack#openMetadata → getRootResource)。読めないとき・正方形でないときは null (呼び出し側が既定のアイコンにする)。
     */
    public @Nullable DynamicTexture createVanillaPackIcon() {
        if (vanillaPackIcon != null) {
            return vanillaPackIcon;
        }

        try {
            Pack pack = Minecraft.getInstance().getResourcePackRepository().getPack("vanilla");
            if (pack == null) {
                return null;
            }

            try (PackMetadataResources resources = pack.openMetadata()) {
                IoSupplier<InputStream> icon = resources.getRootResource("pack.png");
                if (icon == null) {
                    return null;
                }

                try (InputStream inputStream = icon.get()) {
                    NativeImage image = NativeImage.read(inputStream);
                    if (image.getHeight() != image.getWidth()) {
                        image.close();
                        return null;
                    }

                    vanillaPackIcon = new DynamicTexture(() -> "Mod Manager | vanilla pack icon", image);
                    return vanillaPackIcon;
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Could not load the vanilla pack icon", t);
            return null;
        }
    }

    @Override
    public void close() {
        for (DynamicTexture tex : modIconCache.values()) {
            tex.close();
        }

        if (vanillaPackIcon != null) {
            vanillaPackIcon.close();
            vanillaPackIcon = null;
        }
    }

    DynamicTexture getCachedModIcon(Path path) {
        return modIconCache.get(path);
    }

    void cacheModIcon(Path path, DynamicTexture tex) {
        modIconCache.put(path, tex);
    }
}

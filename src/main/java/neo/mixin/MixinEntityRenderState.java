package neo.mixin;

import java.util.UUID;

import neo.entitytex.NamedRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 描画用データに、エンティティの名前と個体の ID を持たせる (neo.entitytex.RandomEntityTextures が絵を選ぶのに使う)。 */
@Mixin(EntityRenderState.class)
public abstract class MixinEntityRenderState implements NamedRenderState {
    @Unique private String neo$customName;
    @Unique private UUID neo$entityId;

    @Unique @Override public String neo$customName() { return neo$customName; }

    @Unique @Override public UUID neo$entityId() { return neo$entityId; }

    @Unique @Override public void neo$setEntity(String customName, UUID entityId) {
        this.neo$customName = customName;
        this.neo$entityId = entityId;
    }
}

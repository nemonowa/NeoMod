package neo.entitytex;

import java.util.UUID;

/**
 * 描画用データ (EntityRenderState) に足す、エンティティの名前と個体の ID。
 * バニラの描画用データは名札を出す時しか名前を持たないので、絵を選ぶために別に覚える (実装は neo.mixin.MixinEntityRenderState)。
 */
public interface NamedRenderState {
    String neo$customName();

    UUID neo$entityId();

    void neo$setEntity(String customName, UUID entityId);
}

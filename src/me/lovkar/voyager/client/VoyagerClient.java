package me.lovkar.voyager.client;

import com.minecolonies.api.client.render.modeltype.SimpleModelType;
import com.minecolonies.api.client.render.modeltype.registry.IModelTypeRegistry;
import me.lovkar.voyager.Voyager;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only wiring: the Voyager citizen model and its MineColonies model type. */
public final class VoyagerClient {

    public static final ModelLayerLocation VOYAGER_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "voyager"), "main");

    public static final ModelLayerLocation ASTRONOMER_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "astronomer"), "main");

    private VoyagerClient() {
    }

    public static void init(final IEventBus modEventBus) {
        modEventBus.addListener(VoyagerClient::registerLayers);
        modEventBus.addListener(VoyagerClient::registerModelType);
    }

    private static void registerLayers(final EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(VOYAGER_LAYER, VoyagerModel::createMesh);
        event.registerLayerDefinition(ASTRONOMER_LAYER, AstronomerModel::createMesh);
    }

    /**
     * MineColonies registers its own model types in this event too; textures resolve to
     * minecolonies:textures/entity/citizen/(style)/voyager(male|female)1(_a|_b|_d|_w).png.
     */
    private static void registerModelType(final EntityRenderersEvent.AddLayers event) {
        IModelTypeRegistry.getInstance().register(new SimpleModelType(Voyager.MODEL_ID, 1,
                new VoyagerModel(event.getEntityModels().bakeLayer(VOYAGER_LAYER)),
                new VoyagerModel(event.getEntityModels().bakeLayer(VOYAGER_LAYER))));
        IModelTypeRegistry.getInstance().register(new SimpleModelType(Voyager.ASTRONOMER_MODEL_ID, 1,
                new AstronomerModel(event.getEntityModels().bakeLayer(ASTRONOMER_LAYER)),
                new AstronomerModel(event.getEntityModels().bakeLayer(ASTRONOMER_LAYER))));
        Voyager.LOGGER.info("Voyager suit and astronomer coat models registered");
    }
}

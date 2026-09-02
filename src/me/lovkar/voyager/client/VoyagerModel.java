package me.lovkar.voyager.client;

import com.minecolonies.api.client.render.modeltype.CitizenModel;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * The Voyager's suit: a helmet with an open visor, a chest control panel, a life-support
 * pack with two tanks and an antenna. Same mesh for both genders; texture 128x64
 * (see tools/gen_skin.py for the layout).
 */
public class VoyagerModel extends CitizenModel<AbstractEntityCitizen> {

    public VoyagerModel(final ModelPart part) {
        super(part);
        this.hat.visible = false;
    }

    public static LayerDefinition createMesh() {
        final MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        final PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, CubeDeformation.NONE)
                        .texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.6F)),
                PartPose.ZERO);

        final PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, CubeDeformation.NONE),
                PartPose.ZERO);
        body.addOrReplaceChild("panel", CubeListBuilder.create()
                        .texOffs(64, 16).addBox(-3.0F, 2.0F, -3.0F, 6.0F, 4.0F, 1.0F, CubeDeformation.NONE),
                PartPose.ZERO);
        final PartDefinition pack = body.addOrReplaceChild("pack", CubeListBuilder.create()
                        .texOffs(64, 24).addBox(-4.0F, 1.0F, 2.0F, 8.0F, 10.0F, 4.0F, CubeDeformation.NONE),
                PartPose.ZERO);
        pack.addOrReplaceChild("tank_right", CubeListBuilder.create()
                        .texOffs(88, 24).addBox(-3.0F, 2.0F, 6.0F, 2.0F, 8.0F, 2.0F, CubeDeformation.NONE),
                PartPose.ZERO);
        pack.addOrReplaceChild("tank_left", CubeListBuilder.create()
                        .texOffs(88, 24).mirror().addBox(1.0F, 2.0F, 6.0F, 2.0F, 8.0F, 2.0F, CubeDeformation.NONE),
                PartPose.ZERO);
        pack.addOrReplaceChild("antenna", CubeListBuilder.create()
                        .texOffs(96, 24).addBox(2.5F, -4.0F, 4.75F, 1.0F, 6.0F, 1.0F, CubeDeformation.NONE),
                PartPose.ZERO);

        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, CubeDeformation.NONE),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, CubeDeformation.NONE),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                        .texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, CubeDeformation.NONE),
                PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                        .texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, CubeDeformation.NONE),
                PartPose.offset(1.9F, 12.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(final @NotNull AbstractEntityCitizen entity, final float limbSwing, final float limbSwingAmount,
                          final float ageInTicks, final float netHeadYaw, final float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        this.hat.visible = false;   // the helmet is part of the head; CitizenModel re-enables the vanilla hat layer
        // the life-support pack only goes on for work; at home the suit is just a suit
        this.body.getChild("pack").visible = isWorking(entity);
    }
}

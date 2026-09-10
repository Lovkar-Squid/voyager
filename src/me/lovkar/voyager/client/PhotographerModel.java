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
 * The photographer: a flat cap, a leather vest over a shirt with the sleeves rolled, a strap across
 * the chest and a camera bag on the hip.
 *
 * <p>The thing this model exists for is the <b>pose</b>. A camera item has no use duration, so the
 * game's own "using an item" arm pose never shows for it; without this the photographer would take
 * every picture with the camera dangling at their side. While the render metadata says
 * {@code camera}, both arms come up and the hands meet in front of the face - the viewfinder pose -
 * and you can tell from across the town square what they are doing.</p>
 */
public class PhotographerModel extends CitizenModel<AbstractEntityCitizen> {

    public PhotographerModel(final ModelPart part) {
        super(part);
        this.hat.visible = false;
    }

    public static LayerDefinition createMesh() {
        final MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        final PartDefinition root = mesh.getRoot();

        // head, with the cap sitting low on it
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, CubeDeformation.NONE)
                        .texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.5F)),
                PartPose.ZERO);

        final PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, CubeDeformation.NONE),
                PartPose.ZERO);
        // the cap's peak, a thin plate over the brow
        root.getChild("head").addOrReplaceChild("peak", CubeListBuilder.create()
                        .texOffs(64, 0).addBox(-4.0F, -5.0F, -6.5F, 8.0F, 1.0F, 3.0F, CubeDeformation.NONE),
                PartPose.ZERO);
        // the camera bag on the left hip, out of the way of the shutter hand
        body.addOrReplaceChild("bag", CubeListBuilder.create()
                        .texOffs(64, 26).addBox(3.0F, 5.0F, -1.5F, 6.0F, 6.0F, 3.0F, CubeDeformation.NONE),
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
        this.hat.visible = false;   // the cap is part of the head; CitizenModel re-enables the vanilla hat layer
        final String meta = entity.getRenderMetadata();
        if (meta != null && meta.contains(me.lovkar.voyager.colony.JobPhotographer.META_CAMERA)) {
            // The viewfinder pose: both hands up in front of the face, following where the head looks.
            this.rightArm.xRot = -1.55F + this.head.xRot;
            this.rightArm.yRot = -0.28F + this.head.yRot;
            this.rightArm.zRot = 0.0F;
            this.leftArm.xRot = -1.55F + this.head.xRot;
            this.leftArm.yRot = 0.28F + this.head.yRot;
            this.leftArm.zRot = 0.0F;
        }
    }
}

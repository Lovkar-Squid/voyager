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
 * The astronomer, who works outdoors at night and dresses for it: a hood, a long scarf, a heavy
 * coat and a satchel of glass plates on the hip. Deliberately nothing like the Voyager's suit -
 * the two professions share a mod, not a wardrobe, and you should be able to tell at fifty blocks
 * which one is on the roof.
 *
 * <p>The satchel only goes on for work, the way the Voyager's life-support pack does: the plates
 * come out of the darkroom when the watch starts and go back when it ends.</p>
 *
 * <p>Same mesh for both genders; texture 128x64 (see tools/gen_astronomer_skin.py for the
 * layout).</p>
 */
public class AstronomerModel extends CitizenModel<AbstractEntityCitizen> {

    public AstronomerModel(final ModelPart part) {
        super(part);
        this.hat.visible = false;
    }

    public static LayerDefinition createMesh() {
        final MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        final PartDefinition root = mesh.getRoot();

        // head, with the hood pulled over it
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, CubeDeformation.NONE)
                        .texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.55F)),
                PartPose.ZERO);

        final PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, CubeDeformation.NONE),
                PartPose.ZERO);
        // the scarf: a ring round the neck, thick enough to read as wool
        body.addOrReplaceChild("scarf", CubeListBuilder.create()
                        .texOffs(64, 16).addBox(-5.0F, -0.5F, -3.0F, 10.0F, 3.0F, 6.0F, CubeDeformation.NONE),
                PartPose.ZERO);
        // the cloak: a flat panel down the back, hung from the shoulders
        body.addOrReplaceChild("cloak", CubeListBuilder.create()
                        .texOffs(64, 0).addBox(-4.0F, 0.5F, 2.0F, 8.0F, 14.0F, 1.0F, CubeDeformation.NONE),
                PartPose.ZERO);
        // the plate satchel, on the left hip and out of the way of the sword arm
        body.addOrReplaceChild("satchel", CubeListBuilder.create()
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
        this.hat.visible = false;   // the hood is part of the head; CitizenModel re-enables the vanilla hat layer
        this.body.getChild("satchel").visible = isWorking(entity);
    }
}

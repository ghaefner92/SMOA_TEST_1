"""Create the first Android-safe IMIQ avatar proof asset.

Run with Blender in background. The result is intentionally a compact stylized
prototype: it validates the mobile glTF contract before art production replaces
its simple geometry with the approved final character.
"""

import bpy
import math
import os
from mathutils import Vector


OUTPUT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "models", "imiq_character.glb"))


def clear_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    for collection in (bpy.data.materials, bpy.data.meshes, bpy.data.armatures, bpy.data.actions):
        for item in collection:
            collection.remove(item)


def material(name, color, roughness=0.62):
    value = bpy.data.materials.new(name)
    value.diffuse_color = (*color, 1.0)
    value.use_nodes = True
    principled = value.node_tree.nodes.get("Principled BSDF")
    principled.inputs["Base Color"].default_value = (*color, 1.0)
    principled.inputs["Roughness"].default_value = roughness
    return value


SKIN = None
HAIR = None
JACKET = None
SHIRT = None
PANTS = None
SHOES = None


def smooth(obj):
    for polygon in obj.data.polygons:
        polygon.use_smooth = True


def uv_sphere(name, location, scale, mat, segments=20, rings=12):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments, ring_count=rings, location=location)
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    smooth(obj)
    obj.data.materials.append(mat)
    return obj


def capsule(name, location, radius, depth, mat, rotation=(0, 0, 0)):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=16, ring_count=10, location=location, rotation=rotation)
    obj = bpy.context.object
    obj.name = name
    obj.scale = (radius, radius, depth * 0.5)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    smooth(obj)
    obj.data.materials.append(mat)
    return obj


def bone_parent(obj, armature, bone_name):
    obj.parent = armature
    obj.parent_type = "BONE"
    obj.parent_bone = bone_name
    obj.matrix_parent_inverse = armature.matrix_world.inverted()


def make_armature():
    bpy.ops.object.armature_add(enter_editmode=True, location=(0, 0, 0))
    rig = bpy.context.object
    rig.name = "IMIQ_Humanoid_Rig"
    arm = rig.data
    arm.name = "IMIQ_Humanoid_Rig"
    root = arm.edit_bones[0]
    root.name = "root"
    root.head, root.tail = (0, 0, 0), (0, 0, 0.18)

    def add(name, head, tail, parent="root"):
        bone = arm.edit_bones.new(name)
        bone.head, bone.tail = head, tail
        bone.parent = arm.edit_bones.get(parent)
        return bone

    add("spine", (0, 0, 0.18), (0, 0, 0.92))
    add("chest", (0, 0, 0.92), (0, 0, 1.30), "spine")
    add("neck", (0, 0, 1.30), (0, 0, 1.48), "chest")
    add("head", (0, 0, 1.48), (0, 0, 1.82), "neck")
    for side, sign in (("L", 1), ("R", -1)):
        add(f"upper_arm.{side}", (0.23 * sign, 0, 1.24), (0.52 * sign, 0, 1.03), "chest")
        add(f"forearm.{side}", (0.52 * sign, 0, 1.03), (0.70 * sign, 0, 0.82), f"upper_arm.{side}")
        add(f"hand.{side}", (0.70 * sign, 0, 0.82), (0.78 * sign, 0, 0.78), f"forearm.{side}")
        add(f"thigh.{side}", (0.14 * sign, 0, 0.22), (0.16 * sign, 0, -0.42), "root")
        add(f"shin.{side}", (0.16 * sign, 0, -0.42), (0.16 * sign, 0.02, -0.96), f"thigh.{side}")
        add(f"foot.{side}", (0.16 * sign, 0.02, -0.96), (0.16 * sign, -0.18, -1.02), f"shin.{side}")
    bpy.ops.object.mode_set(mode="POSE")
    for bone in rig.pose.bones:
        bone.rotation_mode = "XYZ"
    bpy.ops.object.mode_set(mode="OBJECT")
    return rig


def add_face_shapes(head):
    head.shape_key_add(name="Basis")
    negative = head.shape_key_add(name="valence_negative")
    positive = head.shape_key_add(name="valence_positive")
    # A subtle, symmetric squash/raise is enough to validate reactive morphs.
    for basis, neg, pos in zip(head.data.shape_keys.key_blocks[0].data, negative.data, positive.data):
        z = basis.co.z
        neg.co = basis.co.copy()
        pos.co = basis.co.copy()
        if z < 0:
            neg.co.z -= 0.035
            pos.co.z += 0.012
        else:
            neg.co.z += 0.012
            pos.co.z += 0.035


def add_actions(rig):
    def pose(action_name, keyframes):
        action = bpy.data.actions.new(action_name)
        rig.animation_data_create()
        rig.animation_data.action = action
        for frame, rotations in keyframes:
            for bone_name, rotation in rotations.items():
                bone = rig.pose.bones[bone_name]
                bone.rotation_euler = rotation
                bone.keyframe_insert(data_path="rotation_euler", frame=frame)
        track = rig.animation_data.nla_tracks.new()
        track.name = action_name
        track.strips.new(action_name, 1, action)

    walk_mid = {
        "thigh.L": (0.55, 0, 0), "shin.L": (-0.34, 0, 0),
        "thigh.R": (-0.55, 0, 0), "shin.R": (0.34, 0, 0),
        "upper_arm.L": (-0.38, 0, 0), "upper_arm.R": (0.38, 0, 0),
    }
    walk_reverse = {key: tuple(-component for component in value) for key, value in walk_mid.items()}
    pose("walk", [(1, walk_mid), (16, walk_reverse), (31, walk_mid)])
    bike = {
        "thigh.L": (0.75, 0, 0), "shin.L": (-1.18, 0, 0),
        "thigh.R": (-0.48, 0, 0), "shin.R": (0.86, 0, 0),
        "upper_arm.L": (-0.82, 0, 0.16), "upper_arm.R": (-0.82, 0, -0.16),
        "forearm.L": (-0.56, 0, 0), "forearm.R": (-0.56, 0, 0),
    }
    bike_alt = dict(bike)
    bike_alt.update({"thigh.L": (-0.48, 0, 0), "shin.L": (0.86, 0, 0), "thigh.R": (0.75, 0, 0), "shin.R": (-1.18, 0, 0)})
    pose("bike_ride", [(1, bike), (16, bike_alt), (31, bike)])
    drive = {
        "thigh.L": (1.28, 0, 0), "shin.L": (-1.38, 0, 0),
        "thigh.R": (1.28, 0, 0), "shin.R": (-1.38, 0, 0),
        "upper_arm.L": (-0.66, 0, 0.30), "upper_arm.R": (-0.66, 0, -0.30),
        "forearm.L": (-0.62, 0, 0), "forearm.R": (-0.62, 0, 0),
    }
    pose("drive", [(1, drive), (31, drive)])
    transit = {
        "thigh.L": (1.38, 0, 0), "shin.L": (-1.43, 0, 0),
        "thigh.R": (1.38, 0, 0), "shin.R": (-1.43, 0, 0),
        "upper_arm.L": (-0.17, 0, 0.10), "upper_arm.R": (-0.17, 0, -0.10),
    }
    pose("transit_sit", [(1, transit), (31, transit)])
    rig.animation_data.action = None


def main():
    global SKIN, HAIR, JACKET, SHIRT, PANTS, SHOES
    clear_scene()
    SKIN = material("Skin", (0.43, 0.22, 0.13))
    HAIR = material("Hair", (0.035, 0.025, 0.020), 0.48)
    JACKET = material("Jacket", (0.08, 0.22, 0.30))
    SHIRT = material("Shirt", (0.83, 0.76, 0.63))
    PANTS = material("Pants", (0.07, 0.09, 0.14))
    SHOES = material("Shoes", (0.025, 0.028, 0.034))
    rig = make_armature()
    head = uv_sphere("AvatarFace", (0, 0, 1.62), (0.24, 0.22, 0.27), SKIN)
    add_face_shapes(head)
    hair = uv_sphere("Hair", (0, 0.018, 1.78), (0.245, 0.22, 0.15), HAIR)
    torso = capsule("Torso", (0, 0, 1.02), 0.285, 0.58, JACKET)
    shirt = capsule("Shirt", (0, -0.27, 1.03), 0.15, 0.36, SHIRT)
    neck = capsule("Neck", (0, 0, 1.38), 0.09, 0.18, SKIN)
    bone_parent(head, rig, "head"); bone_parent(hair, rig, "head"); bone_parent(neck, rig, "neck")
    bone_parent(torso, rig, "spine"); bone_parent(shirt, rig, "chest")
    for side, sign in (("L", 1), ("R", -1)):
        upper = capsule(f"UpperArm.{side}", (0.38 * sign, 0, 1.12), 0.09, 0.40, JACKET, (0, sign * 0.64, 0))
        fore = capsule(f"Forearm.{side}", (0.61 * sign, 0, 0.91), 0.075, 0.33, SKIN, (0, sign * 0.64, 0))
        hand = uv_sphere(f"Hand.{side}", (0.74 * sign, 0, 0.79), (0.09, 0.07, 0.08), SKIN)
        thigh = capsule(f"Thigh.{side}", (0.15 * sign, 0, -0.08), 0.115, 0.58, PANTS)
        shin = capsule(f"Shin.{side}", (0.16 * sign, 0, -0.68), 0.10, 0.54, PANTS)
        foot = uv_sphere(f"Foot.{side}", (0.16 * sign, -0.12, -1.00), (0.13, 0.22, 0.08), SHOES)
        bone_parent(upper, rig, f"upper_arm.{side}"); bone_parent(fore, rig, f"forearm.{side}"); bone_parent(hand, rig, f"hand.{side}")
        bone_parent(thigh, rig, f"thigh.{side}"); bone_parent(shin, rig, f"shin.{side}"); bone_parent(foot, rig, f"foot.{side}")
    add_actions(rig)
    bpy.context.scene.render.engine = "BLENDER_EEVEE"
    bpy.context.scene.unit_settings.system = "METRIC"
    bpy.context.scene.world.color = (0.04, 0.05, 0.07)
    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.export_scene.gltf(
        filepath=OUTPUT,
        export_format="GLB",
        use_selection=True,
        export_animations=True,
        export_nla_strips=True,
        export_morph=True,
        export_morph_animation=True,
        export_yup=True,
    )
    print(f"IMIQ avatar exported: {OUTPUT}")


if __name__ == "__main__":
    main()

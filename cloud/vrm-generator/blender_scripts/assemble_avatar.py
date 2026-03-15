"""
Blender headless script for VRM avatar assembly.

Usage:
  blender --background --python assemble_avatar.py -- config.json

Config JSON structure:
{
  "assets": {
    "body": "/path/to/body.glb",
    "hair": "/path/to/hair.glb",
    "outfit": "/path/to/outfit.glb",
    "rig": "/path/to/rig.glb",
    "extras": ["/path/to/extra1.glb", ...]
  },
  "vrmParams": {
    "bodyType": "AVERAGE",
    "heightCm": 170,
    "skinColor": "#C8956C",
    "hairColor": "#2C1A0E",
    "hairStyle": "SHORT_CLASSIC",
    "outfitStyle": "CASUAL_MODERN",
    "extras": ["GLASSES", "EARRINGS"]
  },
  "outputVrm": "/path/to/output.vrm",
  "outputThumbnail": "/path/to/thumbnail.png"
}

Pipeline steps:
1. Clear scene
2. Import base rig
3. Import body mesh, apply skin color
4. Import hair mesh, apply hair color
5. Import outfit mesh
6. Import extras
7. Scale to target height
8. Join meshes under armature
9. Setup VRM metadata (blend shapes, spring bones)
10. Export .vrm
11. Render thumbnail
"""

import sys
import json
import math
import os

import bpy


def clear_scene():
    """Remove all default objects."""
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    # Clear orphan data
    for block in bpy.data.meshes:
        if block.users == 0:
            bpy.data.meshes.remove(block)
    for block in bpy.data.materials:
        if block.users == 0:
            bpy.data.materials.remove(block)


def import_glb(filepath: str) -> list:
    """Import a GLB file and return newly added objects."""
    existing = set(bpy.data.objects.keys())
    bpy.ops.import_scene.gltf(filepath=filepath)
    new_objects = [bpy.data.objects[name] for name in bpy.data.objects.keys() if name not in existing]
    return new_objects


def hex_to_linear(hex_color: str) -> tuple:
    """Convert hex color string to linear RGB tuple (0-1 range)."""
    hex_color = hex_color.lstrip("#")
    r = int(hex_color[0:2], 16) / 255.0
    g = int(hex_color[2:4], 16) / 255.0
    b = int(hex_color[4:6], 16) / 255.0
    # sRGB to linear
    r = pow(r, 2.2)
    g = pow(g, 2.2)
    b = pow(b, 2.2)
    return (r, g, b, 1.0)


def apply_color_to_objects(objects: list, hex_color: str, mat_name: str):
    """Apply a solid color material to all mesh objects."""
    if not hex_color:
        return
    color = hex_to_linear(hex_color)
    mat = bpy.data.materials.new(name=mat_name)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    if bsdf:
        bsdf.inputs["Base Color"].default_value = color
    for obj in objects:
        if obj.type == "MESH":
            obj.data.materials.clear()
            obj.data.materials.append(mat)


def scale_to_height(armature, target_cm: float):
    """Scale the armature so the avatar matches target height in cm."""
    if not armature or target_cm <= 0:
        return
    # Measure current bounding box height
    bpy.context.view_layer.update()
    bbox = [armature.matrix_world @ bpy.data.objects[c.name].bound_box[i]
            for c in armature.children if c.type == "MESH"
            for i in range(8)]
    if not bbox:
        return
    # Simplified: use armature dimensions
    current_height = armature.dimensions.z
    if current_height > 0:
        scale_factor = (target_cm / 100.0) / current_height
        armature.scale *= scale_factor
        bpy.ops.object.select_all(action="DESELECT")
        armature.select_set(True)
        bpy.context.view_layer.objects.active = armature
        bpy.ops.object.transform_apply(scale=True)


def join_meshes_to_armature(armature, all_objects: list):
    """Parent all mesh objects to the armature and join them."""
    meshes = [obj for obj in all_objects if obj.type == "MESH"]
    if not armature or not meshes:
        return

    bpy.ops.object.select_all(action="DESELECT")
    for mesh in meshes:
        mesh.select_set(True)
        mesh.parent = armature
        # Add armature modifier if not present
        if not any(m.type == "ARMATURE" for m in mesh.modifiers):
            mod = mesh.modifiers.new(name="Armature", type="ARMATURE")
            mod.object = armature


def setup_vrm_metadata(armature, vrm_params: dict):
    """Setup VRM-specific metadata using the VRM addon."""
    try:
        # Enable VRM addon
        bpy.ops.preferences.addon_enable(module="VRM_Addon_for_Blender")
    except Exception:
        print("[Blender] VRM addon not found, skipping VRM metadata setup")
        return

    # VRM metadata is set via addon properties on the armature
    if hasattr(armature.data, "vrm_addon_extension"):
        vrm = armature.data.vrm_addon_extension
        # VRM 1.0 meta
        if hasattr(vrm, "vrm1"):
            meta = vrm.vrm1.meta
            meta.vrm_name = vrm_params.get("name", "Calmify Avatar")
            meta.authors.clear()
            author = meta.authors.add()
            author.value = "Calmify App"
            meta.allow_antisocial_or_hate_usage = False
            meta.allow_excessively_violent_usage = False
            meta.allow_excessively_sexual_usage = False


def render_thumbnail(output_path: str):
    """Render a simple thumbnail of the avatar."""
    scene = bpy.context.scene
    scene.render.resolution_x = 512
    scene.render.resolution_y = 512
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = "PNG"
    scene.render.filepath = output_path

    # Add camera if not present
    if not any(obj.type == "CAMERA" for obj in bpy.data.objects):
        bpy.ops.object.camera_add(location=(0, -2.5, 1.2), rotation=(math.radians(80), 0, 0))
        scene.camera = bpy.context.object

    # Add light if not present
    if not any(obj.type == "LIGHT" for obj in bpy.data.objects):
        bpy.ops.object.light_add(type="SUN", location=(2, -2, 3))
        bpy.context.object.data.energy = 3.0

    bpy.ops.render.render(write_still=True)
    print(f"[Blender] Thumbnail rendered → {output_path}")


def export_vrm(armature, output_path: str):
    """Export the avatar as VRM file."""
    bpy.ops.object.select_all(action="DESELECT")
    armature.select_set(True)
    for child in armature.children:
        child.select_set(True)
    bpy.context.view_layer.objects.active = armature

    try:
        # Try VRM addon export
        bpy.ops.export_scene.vrm(filepath=output_path)
        print(f"[Blender] VRM exported → {output_path}")
    except Exception as e:
        print(f"[Blender] VRM export failed ({e}), falling back to glTF")
        # Fallback: export as .glb (client can handle both)
        fallback_path = output_path.replace(".vrm", ".glb")
        bpy.ops.export_scene.gltf(filepath=fallback_path, export_format="GLB")
        os.rename(fallback_path, output_path)
        print(f"[Blender] glTF fallback exported → {output_path}")


def main():
    # Parse config from command line args (after --)
    argv = sys.argv
    if "--" not in argv:
        print("[Blender] ERROR: No config file provided. Usage: blender --background --python script.py -- config.json")
        sys.exit(1)

    config_path = argv[argv.index("--") + 1]
    with open(config_path, "r") as f:
        config = json.load(f)

    assets = config["assets"]
    vrm_params = config.get("vrmParams", {})
    output_vrm = config["outputVrm"]
    output_thumb = config.get("outputThumbnail", "")

    print(f"[Blender] Starting avatar assembly: {json.dumps(vrm_params, indent=2)}")

    # 1. Clear scene
    clear_scene()

    all_objects = []
    armature = None

    # 2. Import base rig
    if assets.get("rig"):
        rig_objects = import_glb(assets["rig"])
        for obj in rig_objects:
            if obj.type == "ARMATURE":
                armature = obj
                break
        all_objects.extend(rig_objects)

    # 3. Import body mesh + apply skin color
    if assets.get("body"):
        body_objects = import_glb(assets["body"])
        apply_color_to_objects(body_objects, vrm_params.get("skinColor", ""), "Skin")
        all_objects.extend(body_objects)

    # 4. Import hair mesh + apply hair color
    if assets.get("hair"):
        hair_objects = import_glb(assets["hair"])
        apply_color_to_objects(hair_objects, vrm_params.get("hairColor", ""), "Hair")
        all_objects.extend(hair_objects)

    # 5. Import outfit mesh
    if assets.get("outfit"):
        outfit_objects = import_glb(assets["outfit"])
        all_objects.extend(outfit_objects)

    # 6. Import extras
    for extra_path in assets.get("extras", []):
        extra_objects = import_glb(extra_path)
        all_objects.extend(extra_objects)

    # 7. Scale to target height
    if armature:
        height_cm = vrm_params.get("heightCm", 170)
        scale_to_height(armature, height_cm)

    # 8. Join meshes to armature
    if armature:
        join_meshes_to_armature(armature, all_objects)

    # 9. Setup VRM metadata
    if armature:
        setup_vrm_metadata(armature, vrm_params)

    # 10. Export VRM
    if armature:
        export_vrm(armature, output_vrm)
    else:
        print("[Blender] WARNING: No armature found, exporting all objects as glTF")
        bpy.ops.export_scene.gltf(filepath=output_vrm, export_format="GLB")

    # 11. Render thumbnail
    if output_thumb:
        render_thumbnail(output_thumb)

    print("[Blender] Assembly complete!")


if __name__ == "__main__":
    main()

import zipfile, os, json, shutil

def merge_hudengine():
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    hud_zip_path = os.path.join(root, 'test-servers', '26.2', 'plugins', 'HUDEngine', 'build', 'HUDEngine.zip')
    if not os.path.exists(hud_zip_path):
        print("HUDEngine.zip not found, skipping merge")
        return

    hud_z = zipfile.ZipFile(hud_zip_path)
    hud_files = {}
    for name in hud_z.namelist():
        if name != 'pack.mcmeta' and not name.endswith('/'):
            hud_files[name] = hud_z.read(name)

    if 'overlay_text/assets/minecraft/shaders/core/text.vsh' in hud_files:
        hud_files['assets/minecraft/shaders/core/text.vsh'] = hud_files['overlay_text/assets/minecraft/shaders/core/text.vsh']

    hud_meta = json.loads(hud_z.read('pack.mcmeta').decode('utf-8'))
    overlays = hud_meta.get('overlays', {})

    instances_dir = os.path.expandvars(r'%APPDATA%\ATLauncher\instances')
    desktop_dir = os.path.expandvars(r'%USERPROFILE%\Desktop')
    builds_dir = os.path.join(root, 'resourcepacks_builds')

    for lang in ['RU', 'EN', '']:
        canonical_name = f'Rounds-ResourcePack-{lang}.zip' if lang else 'Rounds-ResourcePack.zip'
        canonical_path = os.path.join(builds_dir, canonical_name)
        if not os.path.exists(canonical_path):
            continue

        can_z = zipfile.ZipFile(canonical_path)
        entries = {name: can_z.read(name) for name in can_z.namelist()}
        can_z.close()

        meta = json.loads(entries.get('pack.mcmeta', b'{}').decode('utf-8'))
        meta['overlays'] = overlays
        entries['pack.mcmeta'] = json.dumps(meta, indent=2).encode('utf-8')

        for k, v in hud_files.items():
            entries[k] = v

        cardpack_name = f'Rounds-CardPack-{lang}.zip' if lang else 'Rounds-CardPack.zip'
        for out_name in [canonical_name, cardpack_name]:
            out_desktop = os.path.join(desktop_dir, out_name)
            with zipfile.ZipFile(out_desktop, 'w', zipfile.ZIP_DEFLATED) as out_z:
                for k, v in entries.items():
                    out_z.writestr(k, v)

            if out_name == canonical_name:
                shutil.copyfile(out_desktop, canonical_path)

            if os.path.isdir(instances_dir):
                for inst in os.listdir(instances_dir):
                    rp = os.path.join(instances_dir, inst, 'resourcepacks')
                    if os.path.isdir(rp):
                        shutil.copyfile(out_desktop, os.path.join(rp, out_name))

    print("HUDEngine assets and shaders merged into all resourcepacks.")

if __name__ == '__main__':
    merge_hudengine()

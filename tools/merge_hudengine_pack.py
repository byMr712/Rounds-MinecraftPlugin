import zipfile, os, json, shutil

def merge_hudengine():
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    candidates = [
        os.path.join(root, 'tools', 'assets', 'HUDEngine.zip'),
        os.path.join(root, 'rounds-core', 'src', 'main', 'resources', 'hudengine', 'HUDEngine.zip'),
        os.path.join(root, 'test-servers', '26.2', 'plugins', 'HUDEngine', 'build', 'HUDEngine.zip'),
        os.path.expandvars(r'%APPDATA%\ATLauncher\instances\FabulouslyOptimized262\resourcepacks\HUDEngine.zip')
    ]
    hud_zip_path = None
    for cand in candidates:
        if os.path.exists(cand):
            hud_zip_path = cand
            break
    if not hud_zip_path:
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

        out_desktop = os.path.join(desktop_dir, canonical_name)
        with zipfile.ZipFile(out_desktop, 'w', zipfile.ZIP_DEFLATED) as out_z:
            for k, v in entries.items():
                out_z.writestr(k, v)

        shutil.copyfile(out_desktop, canonical_path)

        cardpack_name = f'Rounds-CardPack-{lang}.zip' if lang else 'Rounds-CardPack.zip'
        old_cardpack_desktop = os.path.join(desktop_dir, cardpack_name)
        if os.path.exists(old_cardpack_desktop):
            try:
                os.remove(old_cardpack_desktop)
            except OSError:
                pass

        if os.path.isdir(instances_dir):
            for inst in os.listdir(instances_dir):
                rp = os.path.join(instances_dir, inst, 'resourcepacks')
                if os.path.isdir(rp):
                    shutil.copyfile(out_desktop, os.path.join(rp, canonical_name))
                    old_cp = os.path.join(rp, cardpack_name)
                    if os.path.exists(old_cp):
                        try:
                            os.remove(old_cp)
                        except OSError:
                            pass

    print("HUDEngine assets and shaders merged into all resourcepacks.")

if __name__ == '__main__':
    merge_hudengine()

import zipfile, os, json, tempfile, shutil

def merge_hudengine():
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    candidates = [
        os.path.join(root, 'tools', 'assets', 'HUDEngine.zip'),
        os.path.join(root, 'rounds-core', 'src', 'main', 'resources', 'hudengine', 'HUDEngine.zip'),
        os.path.join(root, 'test-servers', '26.2', 'plugins', 'HUDEngine', 'build', 'HUDEngine.zip')
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

    builds_dir = os.path.join(root, 'resourcepacks_builds')
    if not os.path.isdir(builds_dir):
        return

    for lang in ['RU', 'EN', 'For-Developers', '']:
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

        tmp_fd, tmp_path = tempfile.mkstemp(suffix='.zip')
        os.close(tmp_fd)
        with zipfile.ZipFile(tmp_path, 'w', zipfile.ZIP_DEFLATED) as out_z:
            for k, v in entries.items():
                out_z.writestr(k, v)

        shutil.move(tmp_path, canonical_path)

    print("HUDEngine assets and shaders merged into resourcepacks_builds.")

if __name__ == '__main__':
    merge_hudengine()

# HUD text shaders

Self-contained vertex shaders for the built-in round-score HUD. Every file is the vanilla
text shader of the matching Minecraft version with two changes:

1. `#moj_import` / `#include` directives are replaced by the inlined include source
   (`fog.glsl`, `sample_lightmap.glsl`, `dynamictransforms.glsl`, `projection.glsl`);
2. the HUD positioning block is inserted into `main()` and `Position` is replaced by the
   adjusted `pos` for the final projection and fog calculations.

Inlining is required because some clients (notably NeoForge) fail to resolve
`minecraft:`-prefixed shader imports and reject the whole pack. Keeping the rest of the
shader identical to vanilla guarantees the interface (UBO layout, GLSL version, SSO
locations, `rendertype_text` vs `core/text`) matches the client exactly.

| Directory | Pack formats | Minecraft | Shader |
| :--- | :--- | :--- | :--- |
| `base` (pack root) | 22 - 31 | 1.20.4 | `rendertype_text.vsh` |
| `overlay_32_41` | 32 - 41 | 1.20.6 | `rendertype_text.vsh` |
| `overlay_42_62` | 42 - 62 | 1.21.4 | `rendertype_text.vsh` |
| `overlay_63_68` | 63 - 68 | 1.21.8 | `rendertype_text.vsh` |
| `overlay_69_74` | 69 - 74 | 1.21.10 | `rendertype_text.vsh` |
| `overlay_75_83` | 75 - 83 | 1.21.11 | `rendertype_text.vsh` |
| `overlay_84_87` | 84 - 87 | 26.1.2 | `rendertype_text.vsh` |
| `overlay_88_96` | 88 - 96 | 26.2 | `text.vsh` |
| `overlay_97_99` | 97 - 99 | 26.3 | `text.vsh` (SSO) |

The directories are copied into the ZIP by `ResourcePackBuilder.writeHudShaders`. The
`pack.mcmeta` overlay ranges live in `ResourcePackBuilder.buildPack` and must stay in sync
with this table.

Regenerate with:

```
powershell -NoProfile -ExecutionPolicy Bypass -File tools\generate_hud_shaders.ps1
```

The script downloads the needed client jars from Mojang, extracts the vanilla shaders and
includes, and rewrites this directory. Do not add shaders with `#moj_import`/`#include`
directives.

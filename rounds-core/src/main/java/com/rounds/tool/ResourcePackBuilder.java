package com.rounds.tool;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Pure Java, cross-platform resource pack builder for Rounds Minecraft Plugin.
 * Works uniformly on Windows, Linux, macOS, and CI pipelines without PowerShell or GDI+.
 */
public class ResourcePackBuilder {

    private static final Pattern ID_PATTERN = Pattern.compile("^id\\s*:\\s*(\\d+)\\s*$");
    private static final Pattern ART_PATTERN = Pattern.compile("^art\\s*:\\s*[\"']?([^\"'\n\r]+)[\"']?");
    private static final Pattern TOP_RARITY_PATTERN = Pattern.compile("^rarity\\s*:\\s*([A-Za-z]+)\\s*$");
    private static final Pattern VAR_RARITY_PATTERN = Pattern.compile("^\\s*-\\s+rarity\\s*:\\s*([A-Za-z]+)\\s*$");
    private static final Pattern VAR_ART_PATTERN = Pattern.compile("^\\s+art\\s*:\\s*[\"']?([^\"'\n\r]+)[\"']?");
    private static final int GLYPH_BASE = 0xE000;

    private static final class Variation {
        String rarity;
        String art;
    }

    private static final class FamilyCard {
        int id;
        String art;
        String rarity;
        final List<Variation> variations = new ArrayList<>();
    }

    public static void main(String[] args) {
        try {
            Path projectRoot = findProjectRoot();
            System.out.println("ResourcePackBuilder running at project root: " + projectRoot);

            Map<Integer, FamilyCard> familyArt = collectFamilyCards(projectRoot);
            System.out.println("Discovered " + familyArt.size() + " card definitions with explicit art mappings.");

            Path outDir = projectRoot.resolve("resourcepacks_builds");
            Files.createDirectories(outDir);

            // 1. Build RU
            Path ruZip = outDir.resolve("Rounds-ResourcePack-RU.zip");
            buildPack(projectRoot, familyArt, "RU", "[RU]", ruZip, false);

            // 2. Build EN
            Path enZip = outDir.resolve("Rounds-ResourcePack-EN.zip");
            buildPack(projectRoot, familyArt, "EN", "[EN]", enZip, false);


            // 3. Build Base / Clean Template Pack for developers (blank cards without text)
            Path devZip = outDir.resolve("Rounds-ResourcePack-For-Developers.zip");
            buildPack(projectRoot, familyArt, "BASE", "[For Developers]", devZip, false);

            // 4. Build EXPERIMENTAL RU pack — strips ALL minecraft: shader imports (fog, sample_lightmap,
            // dynamictransforms, projection) and replaces them with inline stubs. ONLY Russian language.
            // Use for testing on clients where the regular pack still fails after fog fix.
            Path ruExperimentalZip = outDir.resolve("Rounds-ResourcePack-RU-experimental-only-test.zip");
            buildPack(projectRoot, familyArt, "RU", "[RU Experimental - Test Only]", ruExperimentalZip, true);

            Path allBuildsDir = Paths.get(System.getProperty("user.home"), "Documents", "RoundsPlugin_all_builds");
            if (Files.isDirectory(allBuildsDir)) {
                Files.copy(ruZip, allBuildsDir.resolve("Rounds-ResourcePack-RU.zip"), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(enZip, allBuildsDir.resolve("Rounds-ResourcePack-EN.zip"), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(devZip, allBuildsDir.resolve("Rounds-ResourcePack-For-Developers.zip"), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(ruExperimentalZip, allBuildsDir.resolve("Rounds-ResourcePack-RU-experimental-only-test.zip"), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Resource packs copied to " + allBuildsDir);
            }


            System.out.println("ResourcePackBuilder completed successfully. Output in " + outDir);
        } catch (Exception e) {
            System.err.println("Error building resource packs: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static Path findProjectRoot() {
        Path cur = Paths.get(".").toAbsolutePath().normalize();
        while (cur != null) {
            if (Files.exists(cur.resolve("settings.gradle.kts"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        return Paths.get(".").toAbsolutePath().normalize();
    }

    private static Map<Integer, FamilyCard> collectFamilyCards(Path root) throws IOException {
        Map<Integer, FamilyCard> cardMap = new TreeMap<>();
        Path cardsDir = root.resolve("rounds-core").resolve("src").resolve("main").resolve("resources").resolve("cards");
        if (Files.isDirectory(cardsDir)) {
            try (var paths = Files.walk(cardsDir)) {
                paths.filter(p -> p.toString().endsWith(".yml")).forEach(p -> {
                    try {
                        FamilyCard fc = new FamilyCard();
                        Variation current = null;
                        boolean inVariations = false;
                        for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                            if (line.matches("^\\s*variations\\s*:\\s*$")) { inVariations = true; continue; }
                            Matcher mId = ID_PATTERN.matcher(line);
                            if (mId.find()) { fc.id = Integer.parseInt(mId.group(1)); continue; }
                            Matcher mArt = ART_PATTERN.matcher(line);
                            if (mArt.find()) { fc.art = mArt.group(1).trim(); continue; }
                            Matcher mRar = TOP_RARITY_PATTERN.matcher(line);
                            if (mRar.find() && fc.rarity == null) { fc.rarity = mRar.group(1).toUpperCase(); continue; }
                            Matcher mVar = VAR_RARITY_PATTERN.matcher(line);
                            if (inVariations && mVar.find()) {
                                current = new Variation();
                                current.rarity = mVar.group(1).toUpperCase();
                                fc.variations.add(current);
                                continue;
                            }
                            Matcher mVarArt = VAR_ART_PATTERN.matcher(line);
                            if (inVariations && current != null && mVarArt.find() && current.art == null) {
                                current.art = mVarArt.group(1).trim();
                            }
                        }
                        if (fc.id != 0) {
                            cardMap.put(fc.id, fc);
                        }
                    } catch (Exception ignored) {}
                });
            }
        }
        return cardMap;
    }

    private static int rarityOrdinal(String rarity) {
        if (rarity == null) return 0;
        return switch (rarity.toUpperCase()) {
            case "COMMON" -> 0;
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            default -> 0;
        };
    }

    private static void buildPack(Path root, Map<Integer, FamilyCard> familyArt, String lang, String label, Path outZip, boolean experimental) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Set<String> written = new HashSet<>();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.setLevel(java.util.zip.Deflater.BEST_COMPRESSION);
            // 1. pack.mcmeta with HUDEngine shader overlays
            String desc = label.isEmpty()
                    ? "Rounds ResourcePack by ifrit & Mr712"
                    : "Rounds ResourcePack " + label + " by ifrit & Mr712";

            String mcmeta = "{\n" +
                    "  \"pack\": {\n" +
                    "    \"pack_format\": 22,\n" +
                    "    \"supported_formats\": [22, 99],\n" +
                    "    \"min_format\": 22,\n" +
                    "    \"max_format\": 99,\n" +
                    "    \"description\": \"" + desc + "\"\n" +
                    "  },\n" +
                    "  \"overlays\": {\n" +
                    "    \"entries\": [\n" +
                    "      {\"formats\": [63, 68], \"min_format\": 63, \"max_format\": 68, \"directory\": \"overlay_ubo150\"},\n" +
                    "      {\"formats\": [69, 83], \"min_format\": 69, \"max_format\": 83, \"directory\": \"overlay_ubo330\"},\n" +
                    "      {\"formats\": [84, 87], \"min_format\": 84, \"max_format\": 87, \"directory\": \"overlay_lightmap\"},\n" +
                    "      {\"formats\": [88, 96], \"min_format\": 88, \"max_format\": 96, \"directory\": \"overlay_text\"},\n" +
                    "      {\"formats\": [97, 99], \"min_format\": 97, \"max_format\": 99, \"directory\": \"overlay_sso\"}\n" +
                    "    ]\n" +
                    "  }\n" +
                    "}\n";
            writeEntry(zos, written, "pack.mcmeta", mcmeta.getBytes(StandardCharsets.UTF_8));

            // 2. pack.png icon (resized from logo.jpg)
            Path logoPath = root.resolve("images").resolve("logo.jpg");
            if (Files.exists(logoPath)) {
                BufferedImage original = ImageIO.read(logoPath.toFile());
                if (original != null) {
                    BufferedImage scaled = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = scaled.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(original, 0, 0, 128, 128, null);
                    g.dispose();
                    ByteArrayOutputStream iconBaos = new ByteArrayOutputStream();
                    ImageIO.write(scaled, "PNG", iconBaos);
                    writeEntry(zos, written, "pack.png", iconBaos.toByteArray());
                }
            }

            // 3. Card font and textures
            Path baseCardsDir = root.resolve("test-servers").resolve("original_mod").resolve("cards");
            Path targetCardsDir = baseCardsDir;
            if (Files.isDirectory(baseCardsDir)) {
                if ("RU".equalsIgnoreCase(lang) && Files.isDirectory(baseCardsDir.resolve("ru"))) {
                    targetCardsDir = baseCardsDir.resolve("ru");
                } else if ("EN".equalsIgnoreCase(lang) && Files.isDirectory(baseCardsDir.resolve("en"))) {
                    targetCardsDir = baseCardsDir.resolve("en");
                }
            } else {
                targetCardsDir = null;
                baseCardsDir = null;
            }

            List<Path> sourceImages = new ArrayList<>();
            if (targetCardsDir != null && Files.isDirectory(targetCardsDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetCardsDir, "*.png")) {
                    for (Path p : stream) sourceImages.add(p);
                }
            }
            if (sourceImages.isEmpty() && baseCardsDir != null && Files.isDirectory(baseCardsDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseCardsDir, "*.png")) {
                    for (Path p : stream) sourceImages.add(p);
                }
            }

            sourceImages.sort(Comparator.comparingInt(p -> {
                String digits = p.getFileName().toString().replaceAll("\\D+", "");
                return digits.isEmpty() ? 0 : Integer.parseInt(digits);
            }));

            int imgCount = sourceImages.isEmpty() ? 1 : sourceImages.size();
            List<String> providers = new ArrayList<>();
            int idx = 0;
            Set<Integer> usedCodepoints = new HashSet<>();

            for (FamilyCard fc : familyArt.values()) {
                List<Variation> slots = new ArrayList<>();
                if (fc.variations.isEmpty()) {
                    Variation v = new Variation();
                    v.rarity = fc.rarity != null ? fc.rarity : "COMMON";
                    v.art = fc.art;
                    slots.add(v);
                } else {
                    slots = fc.variations;
                }
                for (Variation v : slots) {
                    // Stable glyph per variation: familyId * 10 + rarity ordinal, mirrored by
                    // Card.getGlyphCodepoint() on the server.
                    int cp = GLYPH_BASE + fc.id * 10 + rarityOrdinal(v.rarity);
                    if (!usedCodepoints.add(cp)) continue; // duplicate rarity shares the slot
                    String artName = (v.art != null && !v.art.isBlank()) ? v.art : fc.art;
                    if (artName == null || artName.isBlank()) artName = "card_" + fc.id;
                    String texName = artName.endsWith(".png")
                            ? artName.substring(0, artName.length() - 4)
                            : artName;
                    String hex = String.format("%04X", cp);
                    providers.add("    { \"type\": \"bitmap\", \"file\": \"rounds:font/" + texName + ".png\", \"ascent\": 87, \"height\": 88, \"chars\": [\"\\u" + hex + "\"] }");

                    Path cardFile = null;
                    if ("BASE".equalsIgnoreCase(lang) || "BLANK".equalsIgnoreCase(lang)) {
                        // Base / clean template pack: strictly use clean original unbaked card art
                        if (fc.art != null) cardFile = resolveCardPng(fc.art, baseCardsDir, null);
                        if (cardFile == null) cardFile = resolveCardPng("card" + fc.id, baseCardsDir, null);
                        if (cardFile == null) cardFile = resolveCardPng("card_" + fc.id, baseCardsDir, null);
                        if (cardFile == null) cardFile = resolveCardPng("card_default", baseCardsDir, null);
                        if (cardFile == null && !sourceImages.isEmpty()) cardFile = sourceImages.get(idx % imgCount);
                    } else {
                        if (fc.variations.isEmpty()) {
                            // Legacy single card: prefer the baked texture generated for the family id.
                            cardFile = resolveCardPng("card_" + fc.id, targetCardsDir, baseCardsDir);
                            if (cardFile == null) cardFile = resolveCardPng("card" + fc.id, targetCardsDir, baseCardsDir);
                        }
                        if (cardFile == null) {
                            cardFile = resolveCardPng(artName, targetCardsDir, baseCardsDir);
                        }
                        if (cardFile == null && fc.art != null && !fc.art.equals(artName)) {
                            cardFile = resolveCardPng(fc.art, targetCardsDir, baseCardsDir);
                        }
                        if (cardFile == null) {
                            cardFile = resolveCardPng("card_default", targetCardsDir, baseCardsDir);
                        }
                        if (cardFile == null) {
                            cardFile = resolveCardPng("card_missing", targetCardsDir, baseCardsDir);
                        }
                        if (cardFile == null && !sourceImages.isEmpty()) {
                            cardFile = sourceImages.get(idx % imgCount);
                        }
                    }
                    if (cardFile != null) {
                        writeEntry(zos, written, "assets/rounds/textures/font/" + texName + ".png", Files.readAllBytes(cardFile));
                    }
                    idx++;
                }
            }

            String fontJson = "{\n  \"providers\": [\n" + String.join(",\n", providers) + "\n  ]\n}\n";
            writeEntry(zos, written, "assets/rounds/font/default.json", fontJson.getBytes(StandardCharsets.UTF_8));
            // NOTE: never write assets/minecraft/font/default.json here — it would replace
            // the vanilla default font providers and break all regular text rendering.

            // 4. Sounds
            Path soundsDir = root.resolve("test-servers").resolve("original_mod").resolve("sounds");
            if (Files.isDirectory(soundsDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(soundsDir, "*.ogg")) {
                    for (Path p : stream) {
                        writeEntry(zos, written, "assets/rounds/sounds/" + p.getFileName().toString(), Files.readAllBytes(p));
                    }
                }

                String soundsJson = "{\n" +
                        "  \"music1\": { \"category\": \"music\", \"sounds\": [ { \"name\": \"rounds:music1\", \"stream\": true } ] },\n" +
                        "  \"music2\": { \"category\": \"music\", \"sounds\": [ { \"name\": \"rounds:music2\", \"stream\": true } ] },\n" +
                        "  \"music3\": { \"category\": \"music\", \"sounds\": [ { \"name\": \"rounds:music3\", \"stream\": true } ] },\n" +
                        "  \"music4\": { \"category\": \"music\", \"sounds\": [ { \"name\": \"rounds:music4\", \"stream\": true } ] },\n" +
                        "  \"block\": { \"category\": \"player\", \"sounds\": [ \"rounds:block\" ] },\n" +
                        "  \"saw\": { \"category\": \"player\", \"sounds\": [ \"rounds:saw\" ] }\n" +
                        "}\n";
                writeEntry(zos, written, "assets/rounds/sounds.json", soundsJson.getBytes(StandardCharsets.UTF_8));

                // Vanilla sound silencer
                String[] vanillaEvents = new String[]{
                        "music.game", "music.creative", "music.menu", "music.under_water",
                        "music.end", "music.dragon", "music.credits",
                        "music.nether.nether_wastes", "music.nether.crimson_forest", "music.nether.warped_forest",
                        "music.nether.soul_sand_valley", "music.nether.basalt_deltas",
                        "music.overworld.deep_dark", "music.overworld.dripstone_caves", "music.overworld.grove",
                        "music.overworld.jagged_peaks", "music.overworld.lush_caves", "music.overworld.meadow",
                        "music.overworld.cherry_grove", "music.overworld.frozen_peaks", "music.overworld.snowy_slopes",
                        "music.overworld.stony_peaks", "music.overworld.flower_forest", "music.overworld.desert",
                        "music.overworld.badlands", "music.overworld.jungle", "music.overworld.sparse_jungle",
                        "music.overworld.bamboo_jungle", "music.overworld.forest", "music.overworld.old_growth_taiga",
                        "music.overworld.swamp", "music.overworld.mangrove_swamp"
                };
                StringBuilder sb = new StringBuilder("{\n");
                for (int v = 0; v < vanillaEvents.length; v++) {
                    sb.append("  \"").append(vanillaEvents[v]).append("\": { \"replace\": true, \"sounds\": [] }");
                    if (v < vanillaEvents.length - 1) sb.append(",");
                    sb.append("\n");
                }
                sb.append("}\n");
                writeEntry(zos, written, "assets/minecraft/sounds.json", sb.toString().getBytes(StandardCharsets.UTF_8));
            }

            // 5. Weapon 3D model and textures
            Path gunDir = root.resolve("test-servers").resolve("original_mod").resolve("gun");
            Path gunModelPath = gunDir.resolve("models").resolve("item").resolve("gun.json");
            Path gunTexPath = gunDir.resolve("textures").resolve("item").resolve("piton.png");
            if (Files.exists(gunModelPath) && Files.exists(gunTexPath)) {
                writeEntry(zos, written, "assets/rounds/textures/item/piton.png", Files.readAllBytes(gunTexPath));
                writeEntry(zos, written, "assets/rounds/models/item/gun.json", ensureParticleTexture(Files.readAllBytes(gunModelPath)));

                // Legacy model override (Minecraft 1.20.4 - 1.21.3)
                String stickModelJson = "{\n" +
                        "  \"parent\": \"minecraft:item/handheld\",\n" +
                        "  \"textures\": {\n" +
                        "    \"layer0\": \"minecraft:item/stick\"\n" +
                        "  },\n" +
                        "  \"overrides\": [\n" +
                        "    {\n" +
                        "      \"predicate\": {\n" +
                        "        \"custom_model_data\": 1\n" +
                        "      },\n" +
                        "      \"model\": \"rounds:item/gun\"\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}\n";
                writeEntry(zos, written, "assets/minecraft/models/item/stick.json", stickModelJson.getBytes(StandardCharsets.UTF_8));

                // Modern item model definition (Minecraft 1.21.4+)
                String stickItemDefJson = "{\n" +
                        "  \"model\": {\n" +
                        "    \"type\": \"minecraft:range_dispatch\",\n" +
                        "    \"property\": \"minecraft:custom_model_data\",\n" +
                        "    \"fallback\": {\n" +
                        "      \"type\": \"minecraft:model\",\n" +
                        "      \"model\": \"minecraft:item/stick\"\n" +
                        "    },\n" +
                        "    \"entries\": [\n" +
                        "      {\n" +
                        "        \"threshold\": 1.0,\n" +
                        "        \"model\": {\n" +
                        "          \"type\": \"minecraft:model\",\n" +
                        "          \"model\": \"rounds:item/gun\"\n" +
                        "        }\n" +
                        "      }\n" +
                        "    ]\n" +
                        "  }\n" +
                        "}\n";
                writeEntry(zos, written, "assets/minecraft/items/stick.json", stickItemDefJson.getBytes(StandardCharsets.UTF_8));
            }

            // 6. HUDEngine HUD Overlay assets, fonts and shaders
            Path hudEngineZip = resolveHudEngineZip(root);
            if (hudEngineZip != null && Files.exists(hudEngineZip)) {
                try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(hudEngineZip))) {
                    ZipEntry ze;
                    byte[] textVshBytes = null;
                    byte[] transparentBossBarBytes = null;
                    while ((ze = zis.getNextEntry()) != null) {
                        String name = ze.getName();
                        if (!ze.isDirectory() && !"pack.mcmeta".equalsIgnoreCase(name)) {
                            if ("assets/minecraft/textures/gui/sprites/boss_bar/yellow_background.png".equals(name)
                                    || "assets/minecraft/textures/gui/sprites/boss_bar/yellow_progress.png".equals(name)) {
                                continue; // Yellow boss bar is used for the round timer and must remain visible
                            }
                            byte[] data = zis.readAllBytes();
                            // Strip ALL minecraft: shader imports (fog, sample_lightmap, dynamictransforms,
                            // projection) and replace them with inline stubs in place. Every pack needs this:
                            // clients (notably NeoForge 1.21.11+) fail to resolve these imports and reject
                            // the whole pack with an NPE in GlslPreprocessor.
                            if (name.endsWith(".vsh") || name.endsWith(".glsl")) {
                                data = stripAllShaderImports(data);
                            }
                            if ("assets/minecraft/textures/gui/sprites/boss_bar/pink_background.png".equals(name)) {
                                transparentBossBarBytes = data;
                            }
                            if ("assets/hudengine/font/hud.json".equals(name)) {
                                data = ensureDynamicHudGlyphs(data);
                            }
                            writeEntry(zos, written, name, data);
                            if ("overlay_text/assets/minecraft/shaders/core/text.vsh".equals(name)) {
                                textVshBytes = data;
                            }
                        }
                    }
                    if (textVshBytes != null) {
                        writeEntry(zos, written, "assets/minecraft/shaders/core/text.vsh", textVshBytes);
                    }
                    if (transparentBossBarBytes != null) {
                        writeEntry(zos, written, "assets/minecraft/textures/gui/sprites/boss_bar/pink_background.png", transparentBossBarBytes);
                        writeEntry(zos, written, "assets/minecraft/textures/gui/sprites/boss_bar/pink_progress.png", transparentBossBarBytes);
                    }
                }
            }
        }

        byte[] zipBytes = baos.toByteArray();
        Files.write(outZip, zipBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("Generated: " + outZip.getFileName() + " (" + (zipBytes.length / 1024) + " KB)");
    }

    private static Path resolveHudEngineZip(Path root) {
        Path[] candidates = new Path[]{
                root.resolve("tools").resolve("assets").resolve("HUDEngine.zip"),
                root.resolve("rounds-core").resolve("src").resolve("main").resolve("resources").resolve("hudengine").resolve("HUDEngine.zip"),
                root.resolve("test-servers").resolve("26.2").resolve("plugins").resolve("HUDEngine").resolve("build").resolve("HUDEngine.zip")
        };
        for (Path c : candidates) {
            if (Files.exists(c)) return c;
        }
        return null;
    }

    private static Path resolveCardPng(String artName, Path targetCardsDir, Path baseCardsDir) {
        if (artName == null || artName.isBlank()) return null;
        String base = artName.endsWith(".png") ? artName : artName + ".png";
        if (targetCardsDir != null) {
            Path p = targetCardsDir.resolve(base);
            if (Files.exists(p)) return p;
        }
        if (baseCardsDir != null) {
            Path p = baseCardsDir.resolve(base);
            if (Files.exists(p)) return p;
        }
        return null;
    }

    private static byte[] ensureDynamicHudGlyphs(byte[] rawJson) {
        String jsonStr = new String(rawJson, StandardCharsets.UTF_8);
        if (jsonStr.contains("\\udb95\\udc00") || jsonStr.contains("\uDB95\uDC00")) {
            return rawJson;
        }
        int spaceIndex = jsonStr.indexOf("{\"type\":\"space\"");
        if (spaceIndex < 0) {
            spaceIndex = jsonStr.indexOf("{\"type\": \"space\"");
        }
        if (spaceIndex < 0) {
            return rawJson;
        }

        StringBuilder dynamicProviders = new StringBuilder();
        String[] colors = {"empty", "red", "green", "yellow", "blue"};
        for (int line = 0; line < 4; line++) {
            int ascentEmpty = -8400907 - line * 12;
            int ascentWin = -8409099 - line * 12;
            for (int c = 0; c < colors.length; c++) {
                String color = colors[c];
                int ascent = ("empty".equals(color)) ? ascentEmpty : ascentWin;
                int cp = 0xF5400 + line * 5 + c;
                int high = 0xD800 + ((cp - 0x10000) >> 10);
                int low = 0xDC00 + ((cp - 0x10000) & 0x3FF);
                dynamicProviders.append(String.format(
                        "{\"type\":\"bitmap\",\"file\":\"hudengine:image_frame_%s.png\",\"ascent\":%d,\"height\":16,\"chars\":[\"\\u%04x\\u%04x\"]},",
                        color, ascent, high, low
                ));
            }
        }

        String patched = jsonStr.substring(0, spaceIndex) + dynamicProviders + jsonStr.substring(spaceIndex);
        return patched.getBytes(StandardCharsets.UTF_8);
    }

    private static void writeEntry(ZipOutputStream zos, Set<String> written, String entryName, byte[] data) throws IOException {
        String normalized = entryName.replace('\\', '/');
        if (written != null && !written.add(normalized)) {
            return;
        }
        ZipEntry entry = new ZipEntry(normalized);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    /**
     * Ensures the weapon model declares a {@code particle} texture. Minecraft 1.21.5+ item models
     * require it, and its absence triggers a "Missing texture references ... particle" warning.
     * If missing, the particle is set to the same piton texture used by the model.
     */
    private static byte[] ensureParticleTexture(byte[] modelBytes) {
        String s = new String(modelBytes, StandardCharsets.UTF_8);
        if (s.contains("\"particle\"")) {
            return modelBytes;
        }
        s = s.replace(
                "\"1\": \"rounds:item/piton\"",
                "\"1\": \"rounds:item/piton\",\n    \"particle\": \"rounds:item/piton\""
        );
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Replaces ALL {@code minecraft:} shader imports in place with self-contained inline stubs.
     *
     * Some clients (notably NeoForge 1.21.11+) fail to resolve {@code minecraft:} shader includes
     * and abort the whole pack with an NPE inside {@code GlslPreprocessor.processImports}. To make
     * every pack load on every client, each import is replaced inline (at its original position,
     * before {@code main()}) with a stub that keeps the shader self-contained:
     *   - fog.glsl           -> fog_distance / fog_spherical_distance / fog_cylindrical_distance (0.0)
     *   - sample_lightmap    -> sample_lightmap() returning full white
     *   - projection.glsl    -> local `uniform mat4 ProjMat; uniform mat4 ModelViewMat;`
     *   - dynamictransforms  -> removed (the text HUD shader does not use its symbols)
     */
    private static byte[] stripAllShaderImports(byte[] shaderData) {
        String src = new String(shaderData, StandardCharsets.UTF_8);

        String fogStubs = "float fog_distance(vec3 pos, int fogShape) { return 0.0; }\n"
                + "float fog_spherical_distance(vec3 pos) { return 0.0; }\n"
                + "float fog_cylindrical_distance(vec3 pos) { return 0.0; }\n";
        String lightmapStub = "vec4 sample_lightmap(sampler2D sampler, ivec2 uv) { return vec4(1.0, 1.0, 1.0, 1.0); }\n";
        String projectionStub = "uniform mat4 ProjMat;\nuniform mat4 ModelViewMat;\n";

        src = src.replace("#moj_import <minecraft:fog.glsl>", fogStubs);
        src = src.replace("#include <minecraft:fog.glsl>", fogStubs);
        src = src.replace("#moj_import <minecraft:sample_lightmap.glsl>", lightmapStub);
        src = src.replace("#include <minecraft:sample_lightmap.glsl>", lightmapStub);
        src = src.replace("#moj_import <minecraft:dynamictransforms.glsl>", "");
        src = src.replace("#include <minecraft:dynamictransforms.glsl>", "");
        src = src.replace("#moj_import <minecraft:projection.glsl>", projectionStub);
        src = src.replace("#include <minecraft:projection.glsl>", projectionStub);

        return src.getBytes(StandardCharsets.UTF_8);
    }
}

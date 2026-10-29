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
import java.util.zip.ZipOutputStream;

/**
 * Pure Java, cross-platform resource pack builder for Rounds Minecraft Plugin.
 * Works uniformly on Windows, Linux, macOS, and CI pipelines without PowerShell or GDI+.
 */
public class CardPackBuilder {

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
        boolean deployDesktop = false;
        boolean deployInstances = false;

        for (String arg : args) {
            if ("--deploy-desktop".equalsIgnoreCase(arg) || "-deployDesktop".equalsIgnoreCase(arg)) {
                deployDesktop = true;
            } else if ("--deploy-instances".equalsIgnoreCase(arg) || "-deployInstances".equalsIgnoreCase(arg)) {
                deployInstances = true;
            }
        }

        try {
            Path projectRoot = findProjectRoot();
            System.out.println("CardPackBuilder running at project root: " + projectRoot);

            Map<Integer, FamilyCard> familyArt = collectFamilyCards(projectRoot);
            System.out.println("Discovered " + familyArt.size() + " card definitions with explicit art mappings.");

            Path outDir = projectRoot.resolve("resourcepacks_builds");
            Files.createDirectories(outDir);

            // 1. Build RU
            Path ruZip = outDir.resolve("Rounds-ResourcePack-RU.zip");
            buildPack(projectRoot, familyArt, "RU", "[RU]", ruZip);

            // 2. Build EN
            Path enZip = outDir.resolve("Rounds-ResourcePack-EN.zip");
            buildPack(projectRoot, familyArt, "EN", "[EN]", enZip);

            // 3. Build Base / Clean Template Pack (blank cards without text)
            Path defaultZip = outDir.resolve("Rounds-ResourcePack.zip");
            buildPack(projectRoot, familyArt, "BASE", "[Base]", defaultZip);

            // Optional Deploy
            if (deployDesktop) {
                Path desktop = Paths.get(System.getProperty("user.home"), "Desktop");
                if (Files.isDirectory(desktop)) {
                    Files.copy(ruZip, desktop.resolve(ruZip.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    Files.copy(enZip, desktop.resolve(enZip.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    Files.copy(defaultZip, desktop.resolve(defaultZip.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Deployed all packs to Desktop: " + desktop);
                }
            }

            if (deployInstances) {
                Path instancesRoot = Paths.get(System.getProperty("user.home"), "AppData", "Roaming", "ATLauncher", "instances");
                if (Files.isDirectory(instancesRoot)) {
                    int count = 0;
                    int skipped = 0;
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(instancesRoot)) {
                        for (Path instance : stream) {
                            Path rp = instance.resolve("resourcepacks");
                            if (Files.isDirectory(rp)) {
                                try {
                                    Files.copy(ruZip, rp.resolve(ruZip.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                                    Files.copy(enZip, rp.resolve(enZip.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                                    Files.copy(defaultZip, rp.resolve(defaultZip.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                                    count++;
                                } catch (IOException locked) {
                                    // Instance pack file is locked (game running): skip, report.
                                    System.out.println("Skipped locked instance: " + instance.getFileName());
                                    skipped++;
                                }
                            }
                        }
                    }
                    System.out.println("Deployed all packs to " + count + " ATLauncher client instances"
                            + (skipped > 0 ? " (" + skipped + " locked/skipped - close the game and re-run)" : "") + ".");
                }
            }

            System.out.println("CardPackBuilder completed successfully.");
        } catch (Exception e) {
            System.err.println("Error building card packs: " + e.getMessage());
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

    private static void buildPack(Path root, Map<Integer, FamilyCard> familyArt, String lang, String label, Path outZip) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.setLevel(java.util.zip.Deflater.BEST_COMPRESSION);
            // 1. pack.mcmeta
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
                    "  }\n" +
                    "}\n";
            writeEntry(zos, "pack.mcmeta", mcmeta.getBytes(StandardCharsets.UTF_8));

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
                    writeEntry(zos, "pack.png", iconBaos.toByteArray());
                }
            }

            // 3. Card font and textures
            Path baseCardsDir = root.resolve("test-servers").resolve("original_mod").resolve("cards");
            Path targetCardsDir = baseCardsDir;
            if ("RU".equalsIgnoreCase(lang) && Files.isDirectory(baseCardsDir.resolve("ru"))) {
                targetCardsDir = baseCardsDir.resolve("ru");
            } else if ("EN".equalsIgnoreCase(lang) && Files.isDirectory(baseCardsDir.resolve("en"))) {
                targetCardsDir = baseCardsDir.resolve("en");
            }

            List<Path> sourceImages = new ArrayList<>();
            if (Files.isDirectory(targetCardsDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetCardsDir, "*.png")) {
                    for (Path p : stream) sourceImages.add(p);
                }
            }
            if (sourceImages.isEmpty() && Files.isDirectory(baseCardsDir)) {
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
                        writeEntry(zos, "assets/rounds/textures/font/" + texName + ".png", Files.readAllBytes(cardFile));
                    }
                    idx++;
                }
            }

            String fontJson = "{\n  \"providers\": [\n" + String.join(",\n", providers) + "\n  ]\n}\n";
            writeEntry(zos, "assets/rounds/font/default.json", fontJson.getBytes(StandardCharsets.UTF_8));
            // NOTE: never write assets/minecraft/font/default.json here — it would replace
            // the vanilla default font providers and break all regular text rendering.

            // 4. Sounds
            Path soundsDir = root.resolve("test-servers").resolve("original_mod").resolve("sounds");
            if (Files.isDirectory(soundsDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(soundsDir, "*.ogg")) {
                    for (Path p : stream) {
                        writeEntry(zos, "assets/rounds/sounds/" + p.getFileName().toString(), Files.readAllBytes(p));
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
                writeEntry(zos, "assets/rounds/sounds.json", soundsJson.getBytes(StandardCharsets.UTF_8));

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
                writeEntry(zos, "assets/minecraft/sounds.json", sb.toString().getBytes(StandardCharsets.UTF_8));
            }
        }

        byte[] zipBytes = baos.toByteArray();
        Files.write(outZip, zipBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("Generated: " + outZip.getFileName() + " (" + (zipBytes.length / 1024) + " KB)");
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

    private static void writeEntry(ZipOutputStream zos, String entryName, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(entryName.replace('\\', '/'));
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
}

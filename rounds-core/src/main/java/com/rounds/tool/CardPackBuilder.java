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

    private static final Pattern ID_PATTERN = Pattern.compile("^\\s*id\\s*:\\s*(\\d+)\\s*$");
    private static final Pattern ART_PATTERN = Pattern.compile("^\\s*art\\s*:\\s*[\"']?([^\"'\n\r]+)[\"']?");

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

            Map<Integer, String> familyArt = collectFamilyCards(projectRoot);
            System.out.println("Discovered " + familyArt.size() + " card definitions with explicit art mappings.");

            Path outDir = projectRoot.resolve("build").resolve("distributions").resolve("cardpacks");
            Files.createDirectories(outDir);

            // 1. Build RU
            Path ruZip = outDir.resolve("Rounds-CardPack-RU.zip");
            buildPack(projectRoot, familyArt, "RU", "[RU]", ruZip);

            // 2. Build EN
            Path enZip = outDir.resolve("Rounds-CardPack-EN.zip");
            buildPack(projectRoot, familyArt, "EN", "[EN]", enZip);

            // 3. Build Universal Default
            Path defaultZip = outDir.resolve("Rounds-CardPack.zip");
            buildPack(projectRoot, familyArt, "DEFAULT", "", defaultZip);

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
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(instancesRoot)) {
                        for (Path instance : stream) {
                            Path rp = instance.resolve("resourcepacks");
                            if (Files.isDirectory(rp)) {
                                Files.copy(ruZip, rp.resolve(ruZip.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                                Files.copy(enZip, rp.resolve(enZip.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                                Files.copy(defaultZip, rp.resolve(defaultZip.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                                count++;
                            }
                        }
                    }
                    System.out.println("Deployed all packs to " + count + " ATLauncher client instances.");
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

    private static Map<Integer, String> collectFamilyCards(Path root) throws IOException {
        Map<Integer, String> cardMap = new TreeMap<>();
        Path cardsDir = root.resolve("rounds-core").resolve("src").resolve("main").resolve("resources").resolve("cards");
        if (Files.isDirectory(cardsDir)) {
            try (var paths = Files.walk(cardsDir)) {
                paths.filter(p -> p.toString().endsWith(".yml")).forEach(p -> {
                    try {
                        Integer id = null;
                        String art = null;
                        for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                            Matcher mId = ID_PATTERN.matcher(line);
                            if (mId.find()) {
                                id = Integer.parseInt(mId.group(1));
                            }
                            Matcher mArt = ART_PATTERN.matcher(line);
                            if (mArt.find()) {
                                art = mArt.group(1).trim();
                            }
                        }
                        if (id != null) {
                            cardMap.put(id, art != null ? art : "");
                        }
                    } catch (Exception ignored) {}
                });
            }
        }
        return cardMap;
    }

    private static void buildPack(Path root, Map<Integer, String> familyArt, String lang, String label, Path outZip) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // 1. pack.mcmeta
            String desc = label.isEmpty()
                    ? "Rounds CardPack (1.20.4 - 26.3)"
                    : "Rounds CardPack " + label + " (1.20.4 - 26.3)";

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
            } else if (("EN".equalsIgnoreCase(lang) || "DEFAULT".equalsIgnoreCase(lang)) && Files.isDirectory(baseCardsDir.resolve("en"))) {
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

            for (Map.Entry<Integer, String> entry : familyArt.entrySet()) {
                int fid = entry.getKey();
                String customArt = entry.getValue();
                int cp = 0xE000 + fid;
                String hex = String.format("%04X", cp);

                providers.add("    { \"type\": \"bitmap\", \"file\": \"rounds:font/card_" + fid + ".png\", \"ascent\": 87, \"height\": 88, \"chars\": [\"\\u" + hex + "\"] }");

                Path cardFile = targetCardsDir.resolve("card_" + fid + ".png");
                if (!Files.exists(cardFile)) {
                    cardFile = targetCardsDir.resolve("card" + fid + ".png");
                }
                if (!Files.exists(cardFile) && customArt != null && !customArt.isEmpty()) {
                    String artFile = customArt.endsWith(".png") ? customArt : customArt + ".png";
                    Path artCandidate = targetCardsDir.resolve(artFile);
                    if (!Files.exists(artCandidate)) {
                        artCandidate = baseCardsDir.resolve(artFile);
                    }
                    if (Files.exists(artCandidate)) {
                        cardFile = artCandidate;
                    }
                }
                if (!Files.exists(cardFile)) {
                    Path defArt = targetCardsDir.resolve("card_default.png");
                    if (!Files.exists(defArt)) defArt = baseCardsDir.resolve("card_default.png");
                    if (!Files.exists(defArt)) defArt = targetCardsDir.resolve("card_missing.png");
                    if (!Files.exists(defArt)) defArt = baseCardsDir.resolve("card_missing.png");
                    if (Files.exists(defArt)) {
                        cardFile = defArt;
                    }
                }
                if (!Files.exists(cardFile) && !sourceImages.isEmpty()) {
                    cardFile = sourceImages.get(idx % imgCount);
                }
                if (cardFile != null && Files.exists(cardFile)) {
                    byte[] imgBytes = Files.readAllBytes(cardFile);
                    writeEntry(zos, "assets/rounds/textures/font/card_" + fid + ".png", imgBytes);
                }
                idx++;
            }

            String fontJson = "{\n  \"providers\": [\n" + String.join(",\n", providers) + "\n  ]\n}\n";
            writeEntry(zos, "assets/rounds/font/default.json", fontJson.getBytes(StandardCharsets.UTF_8));

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

    private static void writeEntry(ZipOutputStream zos, String entryName, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(entryName.replace('\\', '/'));
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
}

package com.example.demo.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FileUtils {

    private FileUtils() {
        // Utility class
    }

    public static String unzipFile(String zipPath) throws IOException {
        String destDir = "repo";
        try (var zis = new ZipInputStream(Files.newInputStream(Paths.get(zipPath)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    File parent = new File(newFile.getParent());
                    if (!parent.exists()) {
                        parent.mkdirs();
                    }
                    Files.copy(zis, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
        return destDir;
    }

    public static void deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File sub : files) {
                    deleteDirectory(sub);
                }
            }
        }
        file.delete();
    }

    public static void deleteDirectoryQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Ignore deletion errors
        }
    }
}

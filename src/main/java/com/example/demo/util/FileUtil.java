package com.example.demo.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileUtil {
    public static void deleteDirectory(File file) {
        if (file.isDirectory()) {
            for (File sub : file.listFiles()) {
                deleteDirectory(sub);
            }
        }
        file.delete();
    }

    public static void cleanup(String zipPath, String extractDir) {
        try {
            Files.deleteIfExists(new File(zipPath).toPath());
            deleteDirectory(new File(extractDir));
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }
    
    public static String unzip(String zipPath, String dir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(dir, entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    new File(newFile.getParent()).mkdirs();
                    Files.copy(zis, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
        return dir;
    }
}

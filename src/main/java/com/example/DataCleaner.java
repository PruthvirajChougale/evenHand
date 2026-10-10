package com.example;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DataCleaner {

    public static void cleanData(String inputFilePath, String outputFilePath) {
        System.out.println("Starting data cleaning process...");

        Path inputPath = resolveInputPath(inputFilePath);
        Path outputPath = resolveOutputPath(outputFilePath);

        if (inputPath == null) {
            System.err.println("Input file not found. Checked common project paths: " + inputFilePath);
            return;
        }

        try {
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
        } catch (IOException e) {
            System.err.println("Could not create output directory: " + e.getMessage());
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(inputPath);
             BufferedWriter writer = Files.newBufferedWriter(outputPath)) {

            String line;
            boolean isHeader = true;
            int rowsProcessed = 0;
            int rowsCleaned = 0;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    writer.write(line);
                    writer.newLine();
                    isHeader = false;
                    continue;
                }

                String cleanedLine = cleanRow(line);
                if (cleanedLine != null) {
                    writer.write(cleanedLine);
                    writer.newLine();
                    rowsCleaned++;
                }
                rowsProcessed++;
            }

            System.out.println("Data cleaning complete!");
            System.out.println("Source file: " + inputPath);
            System.out.println("Output file: " + outputPath);
            System.out.println("Total rows processed: " + rowsProcessed);
            System.out.println("Successfully saved cleaned rows: " + rowsCleaned);

        } catch (IOException e) {
            System.err.println("Error reading or writing files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Path resolveInputPath(String inputFilePath) {
        if (inputFilePath == null || inputFilePath.trim().isEmpty()) {
            return null;
        }

        Path requestedPath = Paths.get(inputFilePath);
        List<Path> candidatePaths = new ArrayList<>();

        // Step 1: Try the path exactly as provided by the caller
        candidatePaths.add(requestedPath);
        candidatePaths.add(requestedPath.toAbsolutePath().normalize());

        // Step 2: Try relative to the current working directory
        Path currentDir = Paths.get("").toAbsolutePath().normalize();
        candidatePaths.add(currentDir.resolve(requestedPath));

        // Step 3: Try relative to the project root folder
        Path projectRoot = currentDir;
        if (currentDir.getFileName() != null && currentDir.getFileName().toString().equals("src")) {
            projectRoot = currentDir.getParent();
        }
        candidatePaths.add(projectRoot.resolve(requestedPath));

        // Step 4: Try under a nested project folder such as project2/
        candidatePaths.add(projectRoot.resolve("project2").resolve(requestedPath));

        // Step 5: Try parent directories to support running from src/main/java/com/example
        Path parent = currentDir;
        for (int i = 0; i < 6; i++) {
            parent = parent.getParent();
            if (parent == null) {
                break;
            }
            candidatePaths.add(parent.resolve(requestedPath));
            candidatePaths.add(parent.resolve("project2").resolve(requestedPath));
        }

        // Step 6: Try searching by file name within the working tree
        Path fileName = requestedPath.getFileName();
        if (fileName != null) {
            for (Path directory : buildSearchDirectories(currentDir)) {
                candidatePaths.add(directory.resolve(fileName));
            }
        }

        for (Path candidate : candidatePaths) {
            if (candidate != null && Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }

        return null;
    }

    private static Path resolveOutputPath(String outputFilePath) {
        Path requestedOutput = Paths.get(outputFilePath);
        Path currentDir = Paths.get("").toAbsolutePath().normalize();

        if (requestedOutput.isAbsolute()) {
            return requestedOutput;
        }

        Path directPath = currentDir.resolve(requestedOutput);
        if (Files.exists(currentDir) || requestedOutput.getParent() == null) {
            return directPath.toAbsolutePath().normalize();
        }

        return directPath;
    }

    private static List<Path> buildSearchDirectories(Path startDir) {
        List<Path> directories = new ArrayList<>();
        Path current = startDir;

        while (current != null) {
            directories.add(current);
            current = current.getParent();
        }

        return directories;
    }

    private static String cleanRow(String row) {
        if (row == null || row.trim().isEmpty()) {
            return null;
        }

        String[] fields = row.split(",");

        for (int i = 0; i < fields.length; i++) {
            fields[i] = fields[i].trim();

            if (fields[i].isEmpty() || fields[i].equalsIgnoreCase("NA") || fields[i].equalsIgnoreCase("NULL")) {
                fields[i] = "0";
            }
        }

        return String.join(",", fields);
    }

    public static void main(String[] args) {
        cleanData("Book1.csv", "cleaned_data.csv");
    }
}
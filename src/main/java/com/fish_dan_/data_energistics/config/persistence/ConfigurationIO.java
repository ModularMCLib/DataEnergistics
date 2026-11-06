package com.fish_dan_.data_energistics.config.persistence;

import com.fish_dan_.data_energistics.Data_Energistics;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** File boundary shared by startup properties and client preferences. All writes use UTF-8 and atomic replacement. */
public final class ConfigurationIO {

    private ConfigurationIO() {}

    /** Missing files use the supplied default text; malformed UTF-8 and other read failures remain explicit. */
    public static String read(Path file, String defaultText) throws IOException {
        try {
            return decode(Files.readAllBytes(file));
        } catch (NoSuchFileException missing) {
            return defaultText;
        }
    }

    /** Bounds the bytes read before decoding, so a corrupt statistics file cannot allocate without a limit. */
    public static String read(Path file, int maximumBytes, String defaultText) throws IOException {
        if (maximumBytes < 0 || maximumBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid configuration byte limit: " + maximumBytes);
        }
        byte[] bytes;
        try (var input = Files.newInputStream(file)) {
            bytes = input.readNBytes(maximumBytes + 1);
        } catch (NoSuchFileException missing) {
            return defaultText;
        }
        if (bytes.length > maximumBytes) {
            throw new IOException("Configuration exceeds " + maximumBytes + " bytes: " + file);
        }
        return decode(bytes);
    }

    /** Preserves the previous file when writing or atomic replacement fails; always removes its own temporary file. */
    public static void write(Path file, String text) throws IOException {
        Path target = file.toAbsolutePath();
        Path directory = target.getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, target.getFileName() + "-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException failure) {
                Data_Energistics.LOGGER.warn("Cannot remove temporary configuration file {}", temporary, failure);
            }
        }
    }

    /** Moves a corrupt file aside without replacing an existing backup, or reports why preservation failed. */
    public static Path backupCorrupt(Path file, long timestamp) throws IOException {
        for (int attempt = 0; attempt < 100; attempt++) {
            String suffix = attempt == 0 ? "" : "-" + attempt;
            Path backup = file.resolveSibling(file.getFileName() + ".corrupt-" + timestamp + suffix);
            try {
                return Files.move(file, backup);
            } catch (FileAlreadyExistsException collision) {
                Data_Energistics.LOGGER.debug("Configuration backup already exists at {}", backup);
            }
        }
        throw new IOException("Cannot choose a unique corrupt configuration backup for " + file);
    }

    private static String decode(byte[] bytes) throws IOException {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xef && (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf) {
            throw new IOException("Configuration must not contain a UTF-8 BOM");
        }
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }
}

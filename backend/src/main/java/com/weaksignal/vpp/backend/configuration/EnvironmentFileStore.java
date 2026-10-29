package com.weaksignal.vpp.backend.configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 项目根目录 {@code .env} 文件读写组件。
 */
@Component
public class EnvironmentFileStore {

    private final Path environmentFile;

    /**
     * 根据应用配置创建环境文件存储。
     *
     * @param environmentFilePath 环境文件路径
     */
    @Autowired
    public EnvironmentFileStore(@Value("${app.settings.env-file:../.env}") String environmentFilePath) {
        this(Path.of(environmentFilePath));
    }

    EnvironmentFileStore(Path environmentFile) {
        this.environmentFile = environmentFile.toAbsolutePath().normalize();
    }

    /**
     * 读取环境文件中的键值。
     *
     * @return 按文件顺序排列的键值
     * @throws IOException 文件读取失败
     */
    public Map<String, String> readValues() throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(environmentFile, StandardCharsets.UTF_8)) {
            ParsedLine parsedLine = parseLine(line);
            if (parsedLine != null) {
                values.put(parsedLine.key(), parsedLine.value());
            }
        }
        return values;
    }

    /**
     * 原子更新指定环境变量，保留未修改行和注释。
     *
     * @param updates 需要更新的键值
     * @throws IOException 文件更新失败
     */
    public void updateValues(Map<String, String> updates) throws IOException {
        List<String> originalLines = Files.readAllLines(environmentFile, StandardCharsets.UTF_8);
        List<String> updatedLines = new ArrayList<>(originalLines.size() + updates.size());
        Set<String> replacedKeys = new LinkedHashSet<>();

        for (String line : originalLines) {
            ParsedLine parsedLine = parseLine(line);
            if (parsedLine != null && updates.containsKey(parsedLine.key())) {
                updatedLines.add(parsedLine.key() + "=" + updates.get(parsedLine.key()));
                replacedKeys.add(parsedLine.key());
            } else {
                updatedLines.add(line);
            }
        }

        for (Map.Entry<String, String> update : updates.entrySet()) {
            if (!replacedKeys.contains(update.getKey())) {
                updatedLines.add(update.getKey() + "=" + update.getValue());
            }
        }

        Path parentDirectory = environmentFile.getParent();
        Path temporaryFile = Files.createTempFile(parentDirectory, ".env-update-", ".tmp");
        try {
            Files.write(
                    temporaryFile,
                    updatedLines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            moveAtomically(temporaryFile);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    /**
     * 判断环境文件是否存在且可写。
     *
     * @return 可写时返回 {@code true}
     */
    public boolean isWritable() {
        return Files.isRegularFile(environmentFile) && Files.isWritable(environmentFile);
    }

    /**
     * 查询环境文件最近修改时间。
     *
     * @param zoneId 时区
     * @return 最近修改时间
     * @throws IOException 文件属性读取失败
     */
    public OffsetDateTime lastModifiedAt(ZoneId zoneId) throws IOException {
        FileTime lastModifiedTime = Files.getLastModifiedTime(environmentFile);
        return lastModifiedTime.toInstant().atZone(zoneId).toOffsetDateTime();
    }

    private void moveAtomically(Path temporaryFile) throws IOException {
        try {
            Files.move(
                    temporaryFile,
                    environmentFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, environmentFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private ParsedLine parseLine(String line) {
        String trimmedLine = line.trim();
        if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
            return null;
        }
        int separatorIndex = line.indexOf('=');
        if (separatorIndex <= 0) {
            return null;
        }
        String key = line.substring(0, separatorIndex).trim();
        if (!key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return null;
        }
        return new ParsedLine(key, line.substring(separatorIndex + 1));
    }

    private record ParsedLine(String key, String value) {
    }
}
